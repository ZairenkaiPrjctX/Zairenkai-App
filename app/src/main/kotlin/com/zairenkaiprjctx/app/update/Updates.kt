// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.zairenkaiprjctx.app.BuildConfig
import com.zairenkaiprjctx.app.MainActivity
import com.zairenkaiprjctx.app.R
import com.zairenkaiprjctx.app.ZairenkaiApplication
import com.zairenkaiprjctx.integration.update.ApkCheck
import com.zairenkaiprjctx.integration.update.ApkFacts
import com.zairenkaiprjctx.integration.update.ApkVerdict
import com.zairenkaiprjctx.integration.update.AppRelease
import com.zairenkaiprjctx.integration.update.AppVersion
import com.zairenkaiprjctx.integration.update.HttpsTransport
import com.zairenkaiprjctx.integration.update.ReleaseClient
import com.zairenkaiprjctx.integration.update.UpdateChannel
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The update settings: channel and the daily check (on unless the user turns it off). */
data class UpdateSettings(val channel: UpdateChannel = UpdateChannel.STABLE, val autoCheck: Boolean = true, val lastCheck: Long = 0)

class UpdatePreferences(private val prefs: SharedPreferences) {
    private val state = MutableStateFlow(read())
    val settings: StateFlow<UpdateSettings> = state.asStateFlow()

    private fun read() = UpdateSettings(
        channel = runCatching { UpdateChannel.valueOf(prefs.getString(KEY_CHANNEL, null) ?: "STABLE") }.getOrDefault(UpdateChannel.STABLE),
        autoCheck = prefs.getBoolean(KEY_AUTO, true),
        lastCheck = prefs.getLong(KEY_LAST, 0),
    )

    fun update(s: UpdateSettings) {
        prefs.edit {
            putString(KEY_CHANNEL, s.channel.name)
            putBoolean(KEY_AUTO, s.autoCheck)
            putLong(KEY_LAST, s.lastCheck)
        }
        state.value = s
    }

    /**
     * The installed zairenkai module's versionCode as the System Module screen last read it
     * (through root, when the user opened it); null when not installed or never read. The daily
     * check compares module releases with it, so it never needs root itself.
     */
    var knownModuleCode: Long?
        get() = prefs.getLong(KEY_MODULE_CODE, -1).takeIf { it >= 0 }
        set(v) = prefs.edit { if (v == null) remove(KEY_MODULE_CODE) else putLong(KEY_MODULE_CODE, v) }

    /** The module release the user was last notified about. */
    var moduleNotified: String?
        get() = prefs.getString(KEY_MODULE_NOTIFIED, null)
        set(v) = prefs.edit { putString(KEY_MODULE_NOTIFIED, v) }

    /** The version the user was last notified about, so one release notifies once. */
    var notified: String?
        get() = prefs.getString(KEY_NOTIFIED, null)
        set(v) = prefs.edit { putString(KEY_NOTIFIED, v) }

    private companion object {
        const val KEY_CHANNEL = "update_channel"
        const val KEY_AUTO = "update_auto_check"
        const val KEY_LAST = "update_last_check"
        const val KEY_NOTIFIED = "update_notified"
        const val KEY_MODULE_CODE = "module_known_version_code"
        const val KEY_MODULE_NOTIFIED = "module_update_notified"
    }
}

/** Where an update stands. Every step is the user's choice except the daily check itself. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val bytes: Long, val total: Long) : UpdateState
    /** Downloaded, SHA-256 and APK checks passed. */
    data class Ready(val release: AppRelease, val file: File, val sha256: String) : UpdateState
    /** Android must allow this app to install apps first (Install unknown apps). */
    data class NeedsInstallPermission(val release: AppRelease, val file: File, val sha256: String) : UpdateState
    data class Installing(val release: AppRelease) : UpdateState
    data class Failed(val message: String, val release: AppRelease? = null) : UpdateState
}

/**
 * What the app knows about the zairenkai root module's releases: the newest published one, the
 * installed version (as last read) and the one this APK packages. The app never downloads a
 * module release: root managers update through module.prop's updateJson, and the app's own
 * installer uses only the module packaged in its signed APK.
 */
data class ModuleUpdate(
    val latest: com.zairenkaiprjctx.integration.update.ModuleRelease?,
    val installedCode: Long?,
    val packagedCode: Long?,
    val error: String? = null,
) {
    /** A published release newer than the installed module (nothing to offer when not installed). */
    val updateAvailable: Boolean get() = latest != null && installedCode != null && latest.versionCode > installedCode

    /** A release newer than the module this app version packages. */
    val newerThanPackaged: Boolean get() = latest != null && packagedCode != null && latest.versionCode > packagedCode
}

/** The running app as an [ApkFacts], and APK files read by PackageManager. */
class ApkInspector(private val context: Context) {
    private val pm = context.packageManager

    private fun PackageInfo.facts(): ApkFacts {
        // The certificates that sign the APK's contents now (after any key rotation).
        val signers = signingInfo?.apkContentsSigners
            ?.map { sig -> MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }
            ?.toSet().orEmpty()
        return ApkFacts(packageName, longVersionCode, versionName, signers)
    }

    fun installed(): ApkFacts = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).facts()

    fun archive(file: File): ApkFacts? = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)?.facts()
}

/**
 * Checks, downloads and hands an update to Android's installer. The APK must match the
 * release's SHA-256 file, be this package, carry exactly the version the release names, be newer,
 * and be signed by the same key as the installed app; Android's installer then asks the user.
 */
class UpdateController(private val context: Context, val prefs: UpdatePreferences, private val packagedModuleCode: () -> Long? = { null }) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = ReleaseClient(HttpsTransport("Zairenkai-App/${BuildConfig.VERSION_NAME} (+https://github.com/ZairenkaiPrjctX/Zairenkai-App)"))
    private val inspector = ApkInspector(context)
    private val dir = File(context.cacheDir, "updates")
    private val flow = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = flow.asStateFlow()

    val current: AppVersion? = AppVersion.parse(BuildConfig.VERSION_NAME)

    private val moduleFlow = MutableStateFlow<ModuleUpdate?>(null)
    val module: StateFlow<ModuleUpdate?> = moduleFlow.asStateFlow()

    /** Recorded by the System Module screen whenever it reads the installed module. */
    fun recordInstalledModule(versionCode: Long?) {
        prefs.knownModuleCode = versionCode
        moduleFlow.value = moduleFlow.value?.copy(installedCode = versionCode)
    }

    /** Reads the module release list; a failure here never fails the app's own check. */
    private fun checkModule(): ModuleUpdate = try {
        ModuleUpdate(client.moduleReleases().firstOrNull(), prefs.knownModuleCode, packagedModuleCode())
    } catch (e: Exception) {
        ModuleUpdate(null, prefs.knownModuleCode, packagedModuleCode(), e.message ?: e.javaClass.simpleName)
    }.also { moduleFlow.value = it }

    /**
     * Checks now; also used by the daily job, which then notifies once per release. Returns false
     * (and never calls [onResult]) when a check, download or install is already under way.
     */
    fun check(onResult: (UpdateState) -> Unit = {}): Boolean {
        if (flow.value is UpdateState.Checking || flow.value is UpdateState.Downloading || flow.value is UpdateState.Installing) return false
        val v = current ?: run {
            flow.value = UpdateState.Failed(context.getString(R.string.update_err_version, BuildConfig.VERSION_NAME))
            return false
        }
        flow.value = UpdateState.Checking
        scope.launch {
            val s = prefs.settings.value
            checkModule()
            val result = try {
                val r = client.check(s.channel, v)
                prefs.update(s.copy(lastCheck = System.currentTimeMillis()))
                if (r == null) UpdateState.UpToDate(System.currentTimeMillis()) else UpdateState.Available(r)
            } catch (e: Exception) {
                UpdateState.Failed(context.getString(R.string.update_err_check, e.message ?: e.javaClass.simpleName))
            }
            flow.value = result
            onResult(result)
        }
        return true
    }

    fun download() {
        val release = (flow.value as? UpdateState.Available)?.release ?: (flow.value as? UpdateState.Failed)?.release ?: return
        flow.value = UpdateState.Downloading(release, 0, release.apk.size)
        scope.launch {
            flow.value = try {
                dir.listFiles()?.forEach { it.delete() } // one update at a time; old files go
                val d = client.download(release, dir) { bytes, total -> flow.value = UpdateState.Downloading(release, bytes, total) }
                when (val verdict = ApkCheck.decide(inspector.installed(), inspector.archive(d.file), release.version)) {
                    ApkVerdict.Accept -> readyOrPermission(release, d.file, d.sha256)
                    is ApkVerdict.Reject -> {
                        d.file.delete()
                        UpdateState.Failed(context.getString(R.string.update_err_apk, verdict.detail), release)
                    }
                }
            } catch (e: Exception) {
                UpdateState.Failed(context.getString(R.string.update_err_download, e.message ?: e.javaClass.simpleName), release)
            }
        }
    }

    private fun readyOrPermission(release: AppRelease, file: File, sha: String): UpdateState =
        if (context.packageManager.canRequestPackageInstalls()) UpdateState.Ready(release, file, sha)
        else UpdateState.NeedsInstallPermission(release, file, sha)

    /** Opens Android's "Install unknown apps" page for this app. */
    fun openInstallPermission() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** After the user returns from the permission page. */
    fun recheckPermission() {
        val s = flow.value as? UpdateState.NeedsInstallPermission ?: return
        flow.value = readyOrPermission(s.release, s.file, s.sha256)
    }

    /** Streams the verified APK into an installer session; Android asks the user to confirm. */
    fun install() {
        val s = flow.value as? UpdateState.Ready ?: return
        flow.value = UpdateState.Installing(s.release)
        scope.launch {
            try {
                // The file was verified when downloaded; check it again right before installing.
                val now = s.file.inputStream().use { input ->
                    val md = MessageDigest.getInstance("SHA-256")
                    val buf = ByteArray(64 * 1024)
                    while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
                    md.digest().joinToString("") { "%02x".format(it) }
                }
                check(now == s.sha256) { "the downloaded file changed" }
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    setSize(s.file.length())
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("zairenkai.apk", 0, s.file.length()).use { out -> s.file.inputStream().use { it.copyTo(out) }; session.fsync(out) }
                    val intent = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
                }
            } catch (e: Exception) {
                flow.value = UpdateState.Failed(context.getString(R.string.update_err_install, e.message ?: e.javaClass.simpleName), s.release)
            }
        }
    }

    /**
     * The installer's answer (from [InstallResultReceiver]). On success Android replaces and stops
     * this process, so the new version starts fresh; a cancelled or failed install can be retried.
     */
    internal fun installResult(status: Int, message: String?) {
        val release = (flow.value as? UpdateState.Installing)?.release
        flow.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateState.Failed(context.getString(R.string.update_err_cancelled), release)
            else -> UpdateState.Failed(context.getString(R.string.update_err_install, message ?: "status $status"), release)
        }
    }

    fun dismiss() {
        flow.value = UpdateState.Idle
    }

    fun setChannel(c: UpdateChannel) {
        prefs.update(prefs.settings.value.copy(channel = c))
        flow.value = UpdateState.Idle
    }

    fun setAutoCheck(on: Boolean) {
        prefs.update(prefs.settings.value.copy(autoCheck = on))
        UpdateCheckJob.schedule(context, on)
    }
}

/** Receives the installer's status: asks the user when Android needs a confirmation. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val controller = (context.applicationContext as ZairenkaiApplication).container.updates
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT))
            if (confirm != null) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) else controller.installResult(PackageInstaller.STATUS_FAILURE, null)
            return
        }
        controller.installResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}

/** The daily update check: network required, survives reboots, off when the user turns it off. */
class UpdateCheckJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val c = (applicationContext as ZairenkaiApplication).container
        if (!c.updates.prefs.settings.value.autoCheck) return false
        // false: something is already running (the user is updating), so this day's check is done.
        return c.updates.check { result ->
            if (result is UpdateState.Available) UpdateNotifier.notifyOnce(this, c.updates.prefs, result.release)
            c.updates.module.value?.let { UpdateNotifier.notifyModuleOnce(this, c.updates.prefs, it) }
            jobFinished(params, result is UpdateState.Failed)
        }
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val JOB_ID = 7001
        private const val DAY_MS = 24L * 60 * 60 * 1000

        fun schedule(context: Context, enabled: Boolean) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            if (!enabled) {
                js.cancel(JOB_ID)
                return
            }
            if (js.getPendingJob(JOB_ID) != null) return
            js.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, UpdateCheckJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(DAY_MS)
                    .setPersisted(true)
                    .build(),
            )
        }
    }
}

object UpdateNotifier {
    private const val CHANNEL = "updates"
    private const val ID = 7002
    const val EXTRA_OPEN = "com.zairenkaiprjctx.app.OPEN"
    const val OPEN_UPDATES = "updates"

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private const val MODULE_ID = 7003

    /** One notification per module release newer than the installed module. */
    fun notifyModuleOnce(context: Context, prefs: UpdatePreferences, m: ModuleUpdate) {
        val latest = m.latest ?: return
        if (!m.updateAvailable || prefs.moduleNotified == latest.tag || !canNotify(context)) return
        val summary = com.zairenkaiprjctx.integration.update.Notes.parse(latest.notes)
            .firstNotNullOfOrNull { (it as? com.zairenkaiprjctx.integration.update.NoteBlock.Bullet)?.text ?: (it as? com.zairenkaiprjctx.integration.update.NoteBlock.Paragraph)?.text }
            ?: context.getString(R.string.module_update_notify_body)
        post(context, MODULE_ID, context.getString(R.string.module_update_notify_title, latest.tag), summary)?.let { prefs.moduleNotified = latest.tag }
    }

    /** Posts on the updates channel; null when the system refused. */
    private fun post(context: Context, id: Int, title: String, text: String): Unit? {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.update_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.update_channel_description)
            },
        )
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN, OPEN_UPDATES).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
            null // permission withdrawn between the check and the call: nothing is shown
        }
    }

    /** One notification per release: the version, its channel and the first line of its notes. */
    fun notifyOnce(context: Context, prefs: UpdatePreferences, release: AppRelease) {
        if (prefs.notified == release.version.toString() || !canNotify(context)) return
        val title = context.getString(
            if (release.version.isBeta) R.string.update_notify_title_beta else R.string.update_notify_title,
            release.version.toString(),
        )
        val summary = com.zairenkaiprjctx.integration.update.Notes.parse(release.notes)
            .firstNotNullOfOrNull { (it as? com.zairenkaiprjctx.integration.update.NoteBlock.Bullet)?.text ?: (it as? com.zairenkaiprjctx.integration.update.NoteBlock.Paragraph)?.text }
            ?: context.getString(R.string.update_notify_body)
        post(context, ID, title, summary)?.let { prefs.notified = release.version.toString() }
    }
}
