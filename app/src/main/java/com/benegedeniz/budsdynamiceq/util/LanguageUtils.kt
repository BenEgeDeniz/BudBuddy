package com.benegedeniz.budsdynamiceq.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import java.util.Locale

object LanguageUtils {
    fun setLocale(context: Context): Context {
        val prefs = context.getSharedPreferences("BudsPrefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("AppLanguage", "system") ?: "system"

        val locale = if (lang == "system") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Resources.getSystem().configuration.locales.get(0)
            } else {
                @Suppress("DEPRECATION")
                Resources.getSystem().configuration.locale
            }
        } else {
            Locale(lang)
        }

        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        return context.createConfigurationContext(config)
    }
}
