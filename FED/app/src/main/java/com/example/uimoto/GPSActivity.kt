package com.example.uimoto

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttMessage


class GPSActivity : AppCompatActivity() {

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
    private lateinit var batteryTemperatureDisplay : TextView

    // piscas
    private lateinit var leftTurnSignal: ImageView
    private lateinit var rightTurnSignal: ImageView
    private lateinit var indicatorsContainer: ImageView

    // auxiliares
    private lateinit var helperFunction: HelperFunction

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.gpsmenu)             // INTERFACE QUE ESTÁ A SER EXIBIDA

        // FORÇA O FULLSCREEN COMPLETO
        // Get the WindowInsetsController
        val windowInsetsController = window.insetsController

        // Hide both the status bar and the navigation bar
        windowInsetsController?.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())

        // Enable edge-to-edge content and immersive mode
        windowInsetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE


        leftTurnSignal = findViewById(R.id.leftTurnSignalGPS)
        rightTurnSignal = findViewById(R.id.rightTurnSignalGPS)
        temperatureDisplay = findViewById(R.id.temperatureDisplay)
        weatherIcon = findViewById(R.id.weatherIcon)
        gearDisplay = findViewById(R.id.gearValue)
        speedBar = findViewById(R.id.speedometerBar)
        speedDisplay = findViewById(R.id.speedValue)
        batteryBar = findViewById(R.id.batteryBar)
        batteryDisplay = findViewById(R.id.batteryValue)
        timeDisplay = findViewById(R.id.time)
        indicatorsContainer = findViewById(R.id.turnContainerGPS)
        batteryTemperatureDisplay = findViewById(R.id.batteryTempValue)

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
            Glide.with(this)
                .load(iconUrl)
                .into(weatherIcon)  // Replace with your actual ImageView
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
                var batTemperature = ""
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
                            "moto/temperature" -> batTemperature = (payload)
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
                                this@GPSActivity,
                                speed.toInt(),
                                speedBar,
                                speedDisplay,
                                120,
                                ContextCompat.getColor(this@GPSActivity, R.color.lightBlue),
                                "km/h",
                                "speed"
                            )
                        }

                        if (battery.isNotEmpty()) {
                            MqttHelper.updateBarMenus(
                                this@GPSActivity,
                                battery.toInt(),
                                batteryBar,
                                batteryDisplay,
                                100,
                                ContextCompat.getColor(this@GPSActivity, R.color.green),
                                "%",
                                "battery"
                            )
                        }

                        if (batTemperature.isNotEmpty()) {
                            batteryTemperatureDisplay.text = "$batTemperature º"
                        }

                        if (gear.isNotEmpty()) {
                            MqttHelper.updateGear(
                                this@GPSActivity,
                                gear,
                                gearDisplay
                            )
                        }

                        if (timeValue.isNotEmpty()) {
                            MqttHelper.updateTime(
                                this@GPSActivity,
                                timeValue,
                                timeDisplay
                            )
                        }

                        if (indicatorLeft.isNotEmpty()) {
                            MqttHelper.updateIndicatorLeft(
                                this@GPSActivity,
                                indicatorLeft.toBoolean(),
                                helperFunction
                            )
                        }

                        if (indicatorRight.isNotEmpty()) {
                            MqttHelper.updateIndicatorRight(
                                this@GPSActivity,
                                indicatorRight.toBoolean(),
                                helperFunction
                            )
                        }

                        if (dangerLights.isNotEmpty()) {
                            MqttHelper.updateDangerLights(
                                this@GPSActivity,
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
    }
}

