package com.example.uimoto

import android.app.Application
import android.content.res.Configuration
import java.util.Locale

class DisplayApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Apply the saved language globally
        applySavedLanguage()
    }

    private fun applySavedLanguage() {
        val prefs = getSharedPreferences("SettingsPrefs", MODE_PRIVATE)
        val savedLanguage = prefs.getString("selectedLanguage", "en") ?: "en"
        val locale = Locale(savedLanguage)
        Locale.setDefault(locale)

        val config = Configuration()
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}
