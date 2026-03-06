package com.example.uimoto

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    // Function to apply the saved color theme
    private fun applySavedColorTheme() {
        val prefs = getSharedPreferences("SettingsPrefs", MODE_PRIVATE)
        val selectedColor = prefs.getString("selectedColor", "#FFFFFF") // Default to white if not found
    }

    override fun attachBaseContext(newBase: Context?) {
        val prefs = newBase?.getSharedPreferences("SettingsPrefs", MODE_PRIVATE)
        val selectedLanguage = prefs?.getString("selectedLanguage", "en") ?: "en"

        val locale = Locale(selectedLanguage)
        Locale.setDefault(locale)

        val config = Configuration()
        config.setLocale(locale)

        val newContext = newBase?.createConfigurationContext(config)
        super.attachBaseContext(newContext)

        // Apply saved color theme when an activity starts
        applySavedColorTheme()
    }

    // Override onConfigurationChanged to handle language changes and preserve theme color
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Apply the saved color theme again when the configuration is changed
        applySavedColorTheme()
    }
}
