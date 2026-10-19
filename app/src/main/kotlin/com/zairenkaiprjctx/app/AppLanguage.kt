package com.zairenkaiprjctx.app

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.core.content.edit
import java.util.Locale

/**
 * The app's languages. English (`values/`) is the default whatever the system language is;
 * Indonesian (`values-in/`) applies only when the user chooses it.
 *
 * Where the choice lives:
 * - API 33+: the per-app language ([LocaleManager]), so the system setting and the app agree.
 *   No per-app language ("System default") means English.
 * - API 29–32: the app's preferences.
 */
enum class AppLanguage(val tag: String, @StringRes val label: Int) {
    ENGLISH("en", R.string.language_english),
    INDONESIAN("id", R.string.language_indonesian);

    companion object {
        val DEFAULT = ENGLISH

        private const val PREFS = "zairenkai"
        private const val KEY = "app_language"

        /** Android still reports Indonesian as the legacy code "in" in some places. */
        fun fromLanguage(language: String?): AppLanguage? = when (language?.lowercase(Locale.ROOT)) {
            "en" -> ENGLISH
            "id", "in" -> INDONESIAN
            else -> null
        }

        fun current(context: Context): AppLanguage {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
                return locales?.takeUnless { it.isEmpty }?.let { fromLanguage(it[0].language) } ?: DEFAULT
            }
            val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            return fromLanguage(stored) ?: DEFAULT
        }

        /** Records [language]; the activity is recreated in it (by the system on API 33+). */
        fun set(activity: Activity, language: AppLanguage) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language.tag)
            } else {
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, language.tag) }
                activity.recreate()
            }
        }

        /** [base] with its locale replaced by the chosen language. For `attachBaseContext`. */
        fun wrap(base: Context): Context {
            val config = Configuration(base.resources.configuration)
            config.setLocales(LocaleList(Locale.forLanguageTag(current(base).tag)))
            return base.createConfigurationContext(config)
        }
    }
}
