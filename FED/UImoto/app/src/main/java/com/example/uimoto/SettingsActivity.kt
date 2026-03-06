package com.example.uimoto

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.util.Locale

class SettingsActivity : BaseActivity() {

    // hora e temperatura
    private lateinit var temperatureDisplay: TextView
    private lateinit var weatherIcon: ImageView
    private lateinit var timeDisplay: TextView

    // barra inferior
    private lateinit var speedDisplay: TextView
    private lateinit var speedBar: ImageView
    private lateinit var batteryDisplay: TextView
    private lateinit var batteryBar: ImageView
    private lateinit var gearDisplay: TextView

    // piscas
    private lateinit var leftTurnSignal: ImageView
    private lateinit var rightTurnSignal: ImageView

    // auxiliares
    private lateinit var helperFunction: HelperFunction

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the saved language before calling super.onCreate()
        applySavedLanguage()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.settingsmenu)             // INTERFACE QUE ESTÁ A SER EXIBIDA

        // FORÇA O FULLSCREEN COMPLETO
        // Get the WindowInsetsController
        val windowInsetsController = window.insetsController

        // Hide both the status bar and the navigation bar
        windowInsetsController?.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())

        // Enable edge-to-edge content and immersive mode
        windowInsetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // BRIGHTNESS CONTROL
        val seekBarBrightness = findViewById<SeekBar>(R.id.seekBarBrightness)
        seekBarBrightness.max = 255 // Max screen brightness

        // Set initial brightness value based on current window brightness
        val layoutParams = window.attributes
        seekBarBrightness.progress = (layoutParams.screenBrightness * 255).toInt()

        // Brightness control for the app only
        seekBarBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val brightnessValue = progress / 255.0f
                    val window = window
                    val layoutParams = window.attributes
                    layoutParams.screenBrightness = brightnessValue
                    window.attributes = layoutParams
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // COLOR SELECTION
        val colorGroup = findViewById<android.widget.RadioGroup>(R.id.colorGroup)

        colorGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedColor = when (checkedId) {
                R.id.colorPink -> "#FF00FF"
                R.id.colorRed -> "#FF0000"
                R.id.colorOrange -> "#FFA500"
                R.id.colorYellow -> "#FFFF00"
                else -> "#FFFFFF"
            }
            saveColorPreference(selectedColor)
            applyColorTheme(selectedColor)
        }

        // LANGUAGE CHANGE
        // List of available languages
        val spinnerLanguage = findViewById<Spinner>(R.id.spinnerLanguage)
        val languages = listOf("English", "Português")

        // Create an ArrayAdapter to display the language options
        val adapter = ArrayAdapter(this, R.layout.spinner_item, languages)
        spinnerLanguage.adapter = adapter

        // Select the currently saved language in the spinner
        val savedLanguage = getSavedLanguage()
        spinnerLanguage.setSelection(
            when (savedLanguage) {
                "en" -> 0
                "pt" -> 1
                else -> 0
            }
        )

        // Handle language selection
        spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLanguage = when (position) {
                    0 -> "en"  // English
                    1 -> "pt"  // Portuguese
                    else -> "en"
                }
                // If the selected language is different from the current one, change it
                if (getSavedLanguage() != selectedLanguage) {
                    saveLanguage(selectedLanguage)
                    recreate() // Restart the activity to apply the new language
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        leftTurnSignal = findViewById(R.id.leftTurnSignal)
        rightTurnSignal = findViewById(R.id.rightTurnSignal)
        temperatureDisplay = findViewById(R.id.temperatureDisplay)
        weatherIcon = findViewById(R.id.weatherIcon)
        gearDisplay = findViewById(R.id.gearValue)
        speedBar = findViewById(R.id.speedometerBar)
        speedDisplay = findViewById(R.id.speedValue)
        batteryBar = findViewById(R.id.batteryBar)
        batteryDisplay = findViewById(R.id.batteryValue)
        timeDisplay = findViewById(R.id.time)

        // Mete as cores default
        leftTurnSignal.imageTintList =
            ContextCompat.getColorStateList(this, R.color.defaultLightGray)
        rightTurnSignal.imageTintList =
            ContextCompat.getColorStateList(this, R.color.defaultLightGray)

        // Initialize helperFunction after the views are ready
        helperFunction = HelperFunction(
            leftTurnSignal,
            rightTurnSignal,
            headlightsMedium = null,
            highBeam = null,
            abs = null,
            context = this
        )

        setupMqttClient()

        WeatherAPICaller.fetchWeather("Vila Real") { temperature, iconCode ->
            updateTemperature(temperature)

            // Constroi o URL para o icon
            val iconUrl = "https://openweathermap.org/img/wn/$iconCode.png"

            // Use Glide to load the icon into an ImageView
            if (!isDestroyed && !isFinishing) {
                Glide.with(this)
                    .load(iconUrl)
                    .into(weatherIcon) // Replace with your actual ImageView
            }
        }
    }

    private fun updateTemperature(temperature: Int) {
        temperatureDisplay.text = "$temperature°C"    // altera o valor da temperatura
    }

    // FUNCAO QUE CONFIGURA O MQTT CLIENT
    private fun setupMqttClient() {

        // Conecta-se ao broker usando a função Helper
        MqttHelper.connectToBroker(this)

        // Define o callback para gerir as mensagens
        MqttHelper.setCallback(object : MqttCallback {

            // CODIGO RESPONSAVEL PELA PERDA DE CONEXAO
            override fun connectionLost(cause: Throwable?) {
                Log.e("CONNECTION_STATUS", "Connection lost: ${cause?.message ?: "Unknown cause"}")
                // Implementar codigo responsavel pela connection loss
            }

            // CODIGO QUE CORRE QUANDO RECEBE UMA MENSAGEM
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                var speed = ""
                var battery = ""
                var gear = ""
                var indicatorLeft = ""
                var indicatorRight = ""
                var timeValue = ""
                var dangerLights = ""


                if (topic != null && message != null) {
                    val payload = String(message.payload)
                    runOnUiThread {
                        when (topic) {
                            // quando o topico da mensagem mqtt é igual a "speed", muda o valor da velocidade da mota
                            "moto/speed" -> speed = (payload)
                            // quando o topico da mensagem mqtt é igual a "battery", muda o valor da bateria da mota
                            "moto/battery" -> battery = (payload)
                            // quando o topico da mensagem mqtt é igual a "temperature", muda o valor da temperatura da bateria da mota
                            "moto/gear" -> gear = (payload)
                            // quando o topico da mensagem mqtt é igual a "indicator_left", muda o boolean do pisca esquerdo da mota
                            "moto/indicator_left" -> indicatorLeft = (payload)
                            // quando o topico da mensagem mqtt é igual a "indicator_right", muda o boolean do pisca direito da mota
                            "moto/indicator_right" -> indicatorRight = (payload)
                            // quando o topico da mensagem mqtt é igual a "danger-lights", muda o boolean das luzes de perigo da mota
                            "moto/danger-lights" -> dangerLights = (payload)
                            // quando o topico da mensagem mqtt é igual a "time", muda o tempo visível no display da mota
                            "moto/time" -> timeValue = (payload)
                        }

                        if (speed.isNotEmpty()) {
                            MqttHelper.updateBarMenus(
                                this@SettingsActivity,
                                speed.toInt(),
                                speedBar,
                                speedDisplay,
                                120,
                                ContextCompat.getColor(this@SettingsActivity, R.color.lightBlue),
                                "km/h",
                                "speed"
                            )
                        }

                        if (battery.isNotEmpty()) {
                            MqttHelper.updateBarMenus(
                                this@SettingsActivity,
                                battery.toInt(),
                                batteryBar,
                                batteryDisplay,
                                100,
                                ContextCompat.getColor(this@SettingsActivity, R.color.green),
                                "%",
                                "battery"
                            )
                        }

                        if (gear.isNotEmpty()) {
                            MqttHelper.updateGear(
                                this@SettingsActivity,
                                gear,
                                gearDisplay
                            )
                        }

                        if (timeValue.isNotEmpty()) {
                            MqttHelper.updateTime(
                                this@SettingsActivity,
                                timeValue,
                                timeDisplay
                            )
                        }

                        if (indicatorLeft.isNotEmpty()) {
                            MqttHelper.updateIndicatorLeft(
                                this@SettingsActivity,
                                indicatorLeft.toBoolean(),
                                helperFunction
                            )
                        }

                        if (indicatorRight.isNotEmpty()) {
                            MqttHelper.updateIndicatorRight(
                                this@SettingsActivity,
                                indicatorRight.toBoolean(),
                                helperFunction
                            )
                        }

                        if (dangerLights.isNotEmpty()) {
                            MqttHelper.updateDangerLights(
                                this@SettingsActivity,
                                dangerLights.toBoolean(),
                                helperFunction
                            )
                        }
                    }
                }
            }

            // FUNCAO QUE CORRE QUANDO UMA MENSAGEM É ENTREGUE
            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // Implementar codigo responsavel pela mensagem ser entregue
            }
        })
    }

    // CODIGO QUE CORRE QUANDO O PROGRAMA É TERMINADO
    override fun onDestroy() {
        super.onDestroy()
        if (MqttHelper.isConnected()) {
            MqttHelper.disconnect()  // Quando a atividade é destruída, desconecta-se do broker
        }
        // Cancel any ongoing asynchronous operations or callbacks
        WeatherAPICaller.cancelRequests()
    }

    // SAVE COLOR PREFERENCE
    private fun saveColorPreference(color: String) {
        val prefs = getSharedPreferences("SettingsPrefs", MODE_PRIVATE)
        prefs.edit().putString("selectedColor", color).apply()
    }

    // APPLY COLOR THEME
    private fun applyColorTheme(color: String) {
        val parsedColor = android.graphics.Color.parseColor(color)
        findViewById<View>(R.id.line1)?.background?.setTint(parsedColor)
        findViewById<View>(R.id.line2)?.background?.setTint(parsedColor)
        findViewById<View>(R.id.line3)?.background?.setTint(parsedColor)
        findViewById<TextView>(R.id.settingsLabel)?.setTextColor(parsedColor)
    }

    // Apply the saved language to the configuration
    private fun applySavedLanguage() {
        val savedLanguage = getSavedLanguage()
        if (savedLanguage.isNotEmpty()) {
            val locale = Locale(savedLanguage)
            Locale.setDefault(locale)

            val config = Configuration()
            config.setLocale(locale)
            resources.updateConfiguration(config, resources.displayMetrics)
        }
    }

    // Save the selected language to SharedPreferences
    private fun saveLanguage(languageCode: String) {
        val prefs = getSharedPreferences("SettingsPrefs", MODE_PRIVATE)
        prefs.edit().putString("selectedLanguage", languageCode).apply()
    }

    // Retrieve the saved language from SharedPreferences
    private fun getSavedLanguage(): String {
        val prefs = getSharedPreferences("SettingsPrefs", MODE_PRIVATE)
        return prefs.getString("selectedLanguage", "en") ?: "en" // Default to English
    }
}