// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app

import android.app.NotificationManager
import android.app.job.JobScheduler
import android.content.pm.PackageManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.zairenkaiprjctx.app.update.UpdateNotifier
import com.zairenkaiprjctx.integration.update.AppRelease
import com.zairenkaiprjctx.integration.update.AppVersion
import com.zairenkaiprjctx.integration.update.ReleaseAsset
import com.zairenkaiprjctx.integration.update.UpdateChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Settings › Updates and Settings › About, the daily check job, the notification and the manifest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ZairenkaiApplication::class)
class UpdateUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<ZairenkaiApplication>()
    private val page = hasScrollAction()

    private fun openSettings() {
        compose.setContent { ZairenkaiRoot(app.container, activity = null) }
        compose.waitUntil(10_000) { app.container.registry.snapshot.value.records.isNotEmpty() }
        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.waitForIdle()
    }

    private fun openRow(text: String) {
        compose.onNode(page).performScrollToNode(hasText(text, substring = true))
        compose.onNodeWithText(text, substring = true).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    fun theBuildCarriesAReleaseVersionAndItsCode() {
        val v = AppVersion.parse(BuildConfig.VERSION_NAME)
        assertNotNull("versionName ${BuildConfig.VERSION_NAME} must be a release version", v)
        assertEquals(v!!.code, BuildConfig.VERSION_CODE.toLong())
        assertEquals(v, app.container.updates.current)
        assertTrue(BuildConfig.GIT_COMMIT.matches(Regex("[0-9a-f]{40}|unknown")))
    }

    @Test
    fun theDailyCheckIsScheduledAndCanBeTurnedOff() {
        val js = app.getSystemService(JobScheduler::class.java)
        val job = js.allPendingJobs.single()
        assertTrue(job.isPeriodic)
        assertTrue(job.isPersisted)
        assertEquals(24L * 60 * 60 * 1000, job.intervalMillis)
        app.container.updates.setAutoCheck(false)
        assertTrue(js.allPendingJobs.isEmpty())
        assertFalse(app.container.updates.prefs.settings.value.autoCheck)
        app.container.updates.setAutoCheck(true)
        assertEquals(1, js.allPendingJobs.size)
    }

    @Test
    fun updatesPageShowsTheInstalledVersionAndSwitchesChannel() {
        openSettings()
        openRow("Updates")
        compose.onNodeWithText("Installed ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})").assertExists()
        compose.onNodeWithText("Check now").assertExists()
        compose.onNode(page).performScrollToNode(hasText("Beta"))
        compose.onNodeWithText("Beta").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(UpdateChannel.BETA, app.container.updates.prefs.settings.value.channel)
        // Stored, so the next start keeps it.
        assertEquals("BETA", app.getSharedPreferences("zairenkai", 0).getString("update_channel", null))
    }

    @Test
    fun aboutPageShowsVersionBuildAndThisVersionsChanges() {
        openSettings()
        openRow("Version, build, components")
        compose.onNodeWithText("About Zairenkai").assertExists()
        compose.onNode(page).performScrollToNode(hasText("What's new in ${BuildConfig.VERSION_NAME}"))
        compose.onNode(page).performScrollToNode(hasText("Source commit"))
        compose.onNode(page).performScrollToNode(hasText("Open-source notices"))
    }

    @Test
    fun settingsSearchFindsUpdatesInBothLanguages() {
        openSettings()
        compose.onNodeWithText("Search settings…").performTextInput("pembaruan")
        compose.waitForIdle()
        compose.onNodeWithText("Updates").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("Check now").assertExists()
    }

    @Test
    fun theNotificationOpensUpdates() {
        compose.setContent { ZairenkaiRoot(app.container, activity = null) }
        compose.waitUntil(10_000) { app.container.registry.snapshot.value.records.isNotEmpty() }
        app.container.openRequest.value = "UPDATES"
        compose.waitForIdle()
        compose.onNodeWithText("Check now").assertExists()
        assertNull(app.container.openRequest.value)
    }

    @Test
    fun aReleaseIsNotifiedOnceAndOnlyWithPermission() {
        val v = AppVersion.parse("9.0.0")!!
        val r = AppRelease(v, "v9.0.0", "Zairenkai 9.0.0", "### Added\n- Something new", "2026-10-30T00:00:00Z", "https://github.com/x", false,
            ReleaseAsset("zairenkai-9.0.0.apk", "https://github.com/x", 1), ReleaseAsset("zairenkai-9.0.0.apk.sha256", "https://github.com/x", 1))
        val nm = shadowOf(app.getSystemService(NotificationManager::class.java))
        val prefs = app.container.updates.prefs
        shadowOf(app).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        UpdateNotifier.notifyOnce(app, prefs, r)
        assertEquals(0, nm.allNotifications.size)
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        UpdateNotifier.notifyOnce(app, prefs, r)
        UpdateNotifier.notifyOnce(app, prefs, r)
        assertEquals(1, nm.allNotifications.size)
        val n = nm.allNotifications.single()
        assertEquals("Zairenkai 9.0.0 is available", shadowOf(n).contentTitle)
        assertEquals("Something new", shadowOf(n).contentText)
        assertEquals("9.0.0", prefs.notified)
    }

    @Test
    fun aModuleReleaseIsNotifiedOnlyWhenNewerThanTheInstalledModule() {
        val rel = com.zairenkaiprjctx.integration.update.ModuleRelease(
            AppVersion.parse("0.4.0")!!, "v0.4.0", "### Added\n- Faster boot", "2026-10-30T00:00:00Z", "https://github.com/x",
            ReleaseAsset("zairenkai-v0.4.0.zip", "https://github.com/x", 1),
        )
        val nm = shadowOf(app.getSystemService(NotificationManager::class.java))
        val prefs = app.container.updates.prefs
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val m = com.zairenkaiprjctx.app.update.ModuleUpdate(rel, installedCode = null, packagedCode = 300)
        assertTrue(m.newerThanPackaged)
        // Not installed (or never read): nothing to update, no notification.
        UpdateNotifier.notifyModuleOnce(app, prefs, m)
        assertEquals(0, nm.allNotifications.size)
        // Installed and current: nothing either.
        UpdateNotifier.notifyModuleOnce(app, prefs, m.copy(installedCode = 400))
        assertEquals(0, nm.allNotifications.size)
        // Installed and older: one notification, once.
        UpdateNotifier.notifyModuleOnce(app, prefs, m.copy(installedCode = 300))
        UpdateNotifier.notifyModuleOnce(app, prefs, m.copy(installedCode = 300))
        assertEquals(1, nm.allNotifications.size)
        assertEquals("Zairenkai module v0.4.0 is available", shadowOf(nm.allNotifications.single()).contentTitle)
        assertEquals("v0.4.0", prefs.moduleNotified)
    }

    @Test
    fun theInstalledModuleVersionIsRemembered() {
        val u = app.container.updates
        u.recordInstalledModule(300)
        assertEquals(300L, u.prefs.knownModuleCode)
        u.recordInstalledModule(null)
        assertNull(u.prefs.knownModuleCode)
    }

    @Test
    fun manifestDeclaresOnlyWhatUpdatesNeed() {
        val pm = app.packageManager
        val info = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS)
        assertEquals(
            setOf(
                "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.POST_NOTIFICATIONS", "android.permission.REQUEST_INSTALL_PACKAGES",
            ),
            info.requestedPermissions!!.filter { it.startsWith("android.permission.") }.toSet(),
        )
        val job = info.services!!.single { it.name.endsWith("UpdateCheckJob") }
        assertEquals("android.permission.BIND_JOB_SERVICE", job.permission)
        assertFalse(job.exported)
        assertFalse(info.receivers!!.single { it.name.endsWith("InstallResultReceiver") }.exported)
    }
}
