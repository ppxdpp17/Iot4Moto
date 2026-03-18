package com.example.uimoto

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttMessage

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val selectedInterface = sharedPreferences.getString("SELECTED_INTERFACE", null)

        if (selectedInterface != null) {
            // Route to the saved interface
            when (selectedInterface) {
                "interface1" -> startActivity(Intent(this, MainInterface1Activity::class.java))
                "interface2" -> startActivity(Intent(this, MainInterface2Activity::class.java))
            }
            finish()
        } else {
            // Show selection screen
            startActivity(Intent(this, InterfaceSelectionActivity::class.java))
            finish()
        }
    }
}

class MainInterface1Activity : AppCompatActivity() {

    // hora e temperatura
    private lateinit var timeDisplay: TextView
    private lateinit var temperatureDisplay: TextView    // mostra a temperatura
    private lateinit var weatherIcon: ImageView          // mostra o icone do estado do tempo (Nublado, limpo, etc...)

    // velocimetro
    private lateinit var speedometerFilling: ImageView // preenchimento do velocimetro
    private lateinit var speedBox: TextView              // mostra a velocidade atual da mota
    private val maxSpeed = 120                            // velocidade maxima da moto

    // painel de instrumentos
    private lateinit var leftTurnSignal: ImageView
    private lateinit var rightTurnSignal: ImageView
    private lateinit var headlightsMedium: ImageView   // médios
    private lateinit var highBeam: ImageView // máximos
    private lateinit var gearBox: TextView
    private lateinit var abs: ImageView
    private lateinit var driveMode: TextView

    // quilometragem
    private lateinit var totalKmDisplay: TextView // mostra a quilometragem da mota
    private lateinit var autonomyDisplay: TextView  // mostra a autonomia

    // bateria
    private lateinit var batteryDisplay: TextView    // mostra percentagem da bateria
    private lateinit var batteryFilling: ImageView  // preenchimento da bateria

    private lateinit var batteryTemperatureFilling: ImageView // preenchimendo da barra da temperatura da bateria

    // alarme
    private lateinit var alarmOverlay: FrameLayout

    // auxiliares
    private lateinit var helperFunction: HelperFunction


    // FUNCAO QUE CORRE QUANDO O PROGRAMA É INICIALIZADO
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.interface1)             // INTERFACE QUE ESTÁ A SER EXIBIDA

        // FORÇA O FULLSCREEN COMPLETO
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        // ASSOCIAR VARIAVEIS AOS ELEMENTOS DA VIEW
        speedometerFilling = findViewById(R.id.speedometerFilling)
        speedBox = findViewById(R.id.speedBox)
        batteryFilling = findViewById(R.id.batteryFilling)
        batteryDisplay = findViewById(R.id.batteryDisplay)
        batteryTemperatureFilling = findViewById(R.id.batteryTempFilling)

        timeDisplay = findViewById(R.id.time)
        temperatureDisplay = findViewById(R.id.temperatureDisplay)
        weatherIcon = findViewById(R.id.weatherIcon)

        leftTurnSignal = findViewById(R.id.leftTurnSignal)
        rightTurnSignal = findViewById(R.id.rightTurnSignal)
        headlightsMedium = findViewById(R.id.mediums)
        highBeam = findViewById(R.id.highBeam)
        abs = findViewById(R.id.abs)

        gearBox = findViewById(R.id.gearBox)
        totalKmDisplay = findViewById(R.id.totalKm)
        autonomyDisplay = findViewById(R.id.autonomyValue)
        driveMode = findViewById(R.id.driveModeValue)

        alarmOverlay = findViewById(R.id.alarmOverlay)


        // Mete as cores default
        leftTurnSignal.imageTintList =
            ContextCompat.getColorStateList(this, R.color.defaultLightGray)
        rightTurnSignal.imageTintList =
            ContextCompat.getColorStateList(this, R.color.defaultLightGray)
        highBeam.imageTintList = ContextCompat.getColorStateList(this, R.color.lightGray)
        headlightsMedium.imageTintList = ContextCompat.getColorStateList(this, R.color.lightGray)

        // Initialize helperFunction after the views are ready
        helperFunction = HelperFunction(
            leftTurnSignal,
            rightTurnSignal,
            headlightsMedium,
            highBeam,
            abs,
            this
        )

        WeatherAPICaller.fetchWeather("Vila Real") { temperature, iconCode ->
            updateTemperature(temperature)

            // Constroi o URL para o icon
            val iconUrl = "https://openweathermap.org/img/wn/$iconCode.png"

            // Use Glide to load the icon into an ImageView
            Glide.with(this)
                .load(iconUrl)
                .into(weatherIcon)  // Replace with your actual ImageView
        }

        //CLIENTE MQTT
        setupMqttClient()                                       // funcao que configura o mqttClient
    }

    private fun showAlarm(type: String) {
        // Limpa o overlay antigo
        alarmOverlay.removeAllViews()

        // Contentor centralizado para ter Imagem e Texto juntos
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val alarmText = TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val alarmIcon = ImageView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(400, 400).apply {
                bottomMargin = 40
            }
        }

        // Lógica de cores e mensagens
        when (type.trim()) {
            "FALL" -> {
                alarmOverlay.setBackgroundColor(android.graphics.Color.BLUE)
                alarmText.text = "SOS"
                alarmText.textSize = 120f
                container.addView(alarmText)
            }
            "COLLISION_WARNING" -> {
                // Laranja Alerta (#FF8C00)
                alarmOverlay.setBackgroundColor(android.graphics.Color.parseColor("#FF8C00"))
                alarmText.text = "COLISÃO IMINENTE!"
                alarmText.textSize = 60f

                // ADICIONA O ÍCONE DO CARRO AQUI
                alarmIcon.setImageResource(R.drawable.car)

                container.addView(alarmIcon)
                container.addView(alarmText)
            }
            else -> {
                // Assume THEFT por defeito
                alarmOverlay.setBackgroundColor(android.graphics.Color.RED)
                alarmText.text = "!"
                alarmText.textSize = 150f
                container.addView(alarmText)
            }
        }

        alarmOverlay.addView(container)
        alarmOverlay.visibility = View.VISIBLE

        // Oculta ao fim de 4 segundos (reduzido para não tapar o GPS muito tempo em andamento)
        Handler(Looper.getMainLooper()).postDelayed({
            alarmOverlay.visibility = View.GONE
        }, 4000)
    }

    // FUNCAO QUE CONFIGURA O SERVIDOR MQTT
    private fun setupMqttClient() {

        // Conecta-se ao broker usando a função Helper
        MqttHelper.connectToBroker(this)

        // Define o callback para gerir as mensagens
        MqttHelper.setCallback(object : MqttCallback {

            // CODIGO RESPONSAVEL PELA PERDA DE CONEXAO
            override fun connectionLost(cause: Throwable?) {
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
                var dangerLights = ""
                var mediumLights = ""
                var highBeam = ""
                var abs = ""
                var autonomy = ""
                var driveModeValue = ""
                var timeValue = ""
                var totalKilometers = ""
                var alarmType = ""


                if (topic != null && message != null) {
                    val payload = String(message.payload)
                    runOnUiThread {
                        when (topic) {
                            // quando o topico da mensagem mqtt é igual a "time", muda o tempo visível no display da mota
                            "moto/time" -> timeValue = (payload)
                            // quando o topico da mensagem mqtt é igual a "speed", muda o valor da velocidade da mota
                            "moto/speed" -> speed = (payload)
                            // quando o topico da mensagem mqtt é igual a "battery", muda o valor da bateria da mota
                            "moto/battery" -> battery = (payload)
                            // quando o topico da mensagem mqtt é igual a "temperature", muda o valor da temperatura da bateria da mota
                            "moto/temperature" -> batTemperature = (payload)
                            // quando o topico da mensagem mqtt é igual a "gear", muda o valor da marcha da mota
                            "moto/gear" -> gear = (payload)
                            // quando o topico da mensagem mqtt é igual a "indicator_left", muda o boolean do pisca esquerdo da mota
                            "moto/indicator_left" -> indicatorLeft = (payload)
                            // quando o topico da mensagem mqtt é igual a "indicator_right", muda o boolean do pisca direito da mota
                            "moto/indicator_right" -> indicatorRight = (payload)
                            // quando o topico da mensagem mqtt é igual a "danger-lights", muda o boolean das luzes de perigo da mota
                            "moto/danger-lights" -> dangerLights = (payload)
                            // quando o topico da mensagem mqtt é igual a "lights", muda o boolean das luzes médias da mota
                            "moto/lights" -> mediumLights = (payload)
                            // quando o topico da mensagem mqtt é igual a "maxlights", muda o boolean dos máximos da mota
                            "moto/maxlights" -> highBeam = (payload)
                            // quando o topico da mensagem mqtt é igual a "abs", muda o boolean do ABS da mota
                            "moto/abs" -> abs = (payload)
                            // quando o topico da mensagem mqtt é igual a "autonomy", muda o valor da autonomia da mota
                            "moto/autonomy" -> autonomy = (payload)
                            // quando o topico da mensagem mqtt é igual a "drive_mode", muda o modo de conducao da mota
                            "moto/drive_mode" -> driveModeValue = (payload)
                            // quando o topico da mensagem mqtt é igual a "total_kilometers", muda a quilometragem da mota
                            "moto/total_kilometers" -> totalKilometers = (payload)
                            // quando o topico da mensagem mqtt é igual a "alarm", ativa o alarme no ecrã
                            "moto/alarm" -> alarmType = payload
                        }

                        if (alarmType.isNotEmpty()) {
                            showAlarm(alarmType)
                        }

                        if (speed.isNotEmpty()) {
                            MqttHelper.updateSpeedometerInterface1(
                                this@MainInterface1Activity,
                                speed.toInt(),
                                speedometerFilling,
                                speedBox,
                                maxSpeed
                            )
                        }

                        if (battery.isNotEmpty()) {
                            MqttHelper.updateBattery(
                                this@MainInterface1Activity,
                                battery.toInt(),
                                batteryFilling,
                                batteryDisplay
                            )
                        }

                        if (batTemperature.isNotEmpty()) {
                            MqttHelper.updateBatteryTemperature(
                                this@MainInterface1Activity,
                                batTemperature.toInt(),
                                batteryTemperatureFilling
                            )
                        }

                        if (gear.isNotEmpty()) {
                            MqttHelper.updateGear(
                                this@MainInterface1Activity,
                                gear,
                                gearBox
                            )
                        }

                        if (autonomy.isNotEmpty()) {
                            MqttHelper.updateAutonomy(
                                this@MainInterface1Activity,
                                autonomy,
                                autonomyDisplay
                            )
                        }

                        if (totalKilometers.isNotEmpty()) {
                            MqttHelper.updateTotalKilometers(
                                this@MainInterface1Activity,
                                totalKilometers,
                                totalKmDisplay
                            )
                        }

                        if (driveModeValue.isNotEmpty()) {
                            MqttHelper.updateDriveMode(
                                this@MainInterface1Activity,
                                driveModeValue,
                                driveMode
                            )
                        }

                        if (timeValue.isNotEmpty()) {
                            MqttHelper.updateTime(
                                this@MainInterface1Activity,
                                timeValue,
                                timeDisplay
                            )
                        }

                        if (indicatorLeft.isNotEmpty()) {
                            MqttHelper.updateIndicatorLeft(
                                this@MainInterface1Activity,
                                indicatorLeft.toBoolean(),
                                helperFunction
                            )
                        }

                        if (indicatorRight.isNotEmpty()) {
                            MqttHelper.updateIndicatorRight(
                                this@MainInterface1Activity,
                                indicatorRight.toBoolean(),
                                helperFunction
                            )
                        }

                        if (dangerLights.isNotEmpty()) {
                            MqttHelper.updateDangerLights(
                                this@MainInterface1Activity,
                                dangerLights.toBoolean(),
                                helperFunction
                            )
                        }

                        if (mediumLights.isNotEmpty()) {
                            MqttHelper.updateMediumLights(
                                this@MainInterface1Activity,
                                mediumLights.toBoolean(),
                                helperFunction
                            )
                        }

                        if (highBeam.isNotEmpty()) {
                            MqttHelper.updateHighBeam(
                                this@MainInterface1Activity,
                                highBeam.toBoolean(),
                                helperFunction
                            )
                        }

                        if (abs.isNotEmpty()) {
                            MqttHelper.updateABS(
                                this@MainInterface1Activity,
                                abs.toBoolean(),
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

    // FUNCAO QUE ATUALIZA OS VALORES RELACIONADOS COM A TEMPERATURA
    private fun updateTemperature(temperature: Int) {
        Log.d("TEMPO", "ENTROU")
        temperatureDisplay.text = "$temperature°C"    // altera o valor da temperatura
    }

    // ---------------------------------------------------------------------------------------------------------------------------------

    // ESTA FUNCAO DETETA SE DETERMINADA TECLA FOI PRESSIONADA NO TECLADO
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {

            KeyEvent.KEYCODE_P -> {
                helperFunction.toggleDangerLightsBlinking()
                return true
            }

            KeyEvent.KEYCODE_H -> {
                helperFunction.toggleHeadlights()
                return true
            }

            KeyEvent.KEYCODE_M -> {
                helperFunction.toggleHighBeam()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }


    // ------------------------------------------------------------------------------------------
}


// ============================================================================ INTERFACE 2 =================================================================================================


class MainInterface2Activity : AppCompatActivity() {

    // Hora e temperatura
    private lateinit var timeDisplay: TextView                 // mostra a hora (HH:MM) AM/PM
    private lateinit var temperatureDisplay: TextView    // mostra a temperatura
    private lateinit var weatherIcon: ImageView          // mostra o ícone do estado do tempo (Nublado, limpo, etc...)

    // velocimetro
    private lateinit var speedometerFilling: ImageView // preenchimento do velocimetro
    private lateinit var speedBox: TextView              // mostra a velocidade atual da mota
    private val maxSpeed = 120                            // velocidade maxima da moto

    // painel de instrumentos
    private lateinit var leftTurnSignal: ImageView
    private lateinit var rightTurnSignal: ImageView
    private lateinit var headlightsMedium: ImageView   // médios
    private lateinit var highBeam: ImageView // máximos
    private lateinit var gearBox: TextView
    private lateinit var abs: ImageView
    private lateinit var driveMode: TextView

    // autonomia e quilometragem
    private lateinit var totalKmDisplay: TextView // mostra a quilometragem da mota
    private lateinit var autonomyDisplay: TextView  // mostra a autonomia

    // botões da interface
    private lateinit var gpsButton: ImageButton // botão que abre o menu da navegação

    // bateria
    private lateinit var batteryDisplay: TextView    // mostra percentagem da bateria
    private lateinit var batteryFilling: ImageView  // preenchimento da bateria
    private lateinit var batteryTemperatureFilling: ImageView // preenchimendo da barra da temperatura da bateria

    // alarme
    private lateinit var alarmOverlay: FrameLayout

    // auxiliares
    private lateinit var helperFunction: HelperFunction        // inicializa a helperFunction para tratar das luzes etc


    // FUNCAO QUE CORRE QUANDO O PROGRAMA É INICIALIZADO
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.interface2)             // INTERFACE QUE ESTÁ A SER EXIBIDA

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // ASSOCIAR VARIAVEIS AOS ELEMENTOS DA VIEW
        speedBox = findViewById(R.id.speedBox)
        batteryFilling = findViewById(R.id.batteryFilling)
        temperatureDisplay = findViewById(R.id.temperatureDisplay)
        weatherIcon = findViewById(R.id.weatherIcon)
        speedometerFilling = findViewById(R.id.speedometerFilling)
        batteryDisplay = findViewById(R.id.batteryDisplay)
        leftTurnSignal = findViewById(R.id.leftTurnSignal)
        rightTurnSignal = findViewById(R.id.rightTurnSignal)
        headlightsMedium = findViewById(R.id.mediums)
        highBeam = findViewById(R.id.highBeam)
        batteryTemperatureFilling = findViewById(R.id.batteryTempFilling)
        gpsButton = findViewById(R.id.gpsMenu)
        gearBox = findViewById(R.id.gearBox)
        abs = findViewById(R.id.abs)
        autonomyDisplay = findViewById(R.id.autonomyValue)
        totalKmDisplay = findViewById(R.id.totalKm)
        driveMode = findViewById(R.id.driveModeValue)
        timeDisplay = findViewById(R.id.time)

        alarmOverlay = findViewById(R.id.alarmOverlay)

        // Mete as cores default
        leftTurnSignal.imageTintList =
            ContextCompat.getColorStateList(this, R.color.defaultLightGray)
        rightTurnSignal.imageTintList =
            ContextCompat.getColorStateList(this, R.color.defaultLightGray)
        highBeam.imageTintList = ContextCompat.getColorStateList(this, R.color.lightGray)
        headlightsMedium.imageTintList =
            ContextCompat.getColorStateList(this, R.color.lightGray)

        // Initialize helperFunction after the views are ready
        helperFunction = HelperFunction(
            leftTurnSignal,
            rightTurnSignal,
            headlightsMedium,
            highBeam,
            abs,
            this
        )

        // BOTOES DA INTERFACE
        gpsButtonClick()                                       // funcao que verifica se o botao do GPS é "clickado"

        WeatherAPICaller.fetchWeather("Vila Real") { temperature, iconCode ->
            updateTemperature(temperature)

            // Constroi o URL para o icon
            val iconUrl = "https://openweathermap.org/img/wn/$iconCode.png"

            // Use Glide to load the icon into an ImageView
            Glide.with(this)
                .load(iconUrl)
                .into(weatherIcon)  // Replace with your actual ImageView
        }

        //CLIENTE MQTT
        setupMqttClient()                                       // funcao que configura o mqttClient
    }

    private fun showAlarm(type: String) {
        // Limpa o que estiver no overlay (para não sobrepor ao ecrã vermelho original do XML)
        alarmOverlay.removeAllViews()

        // Cria um texto dinâmico para preencher o ecrã
        val alarmText = TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        if (type.trim() == "FALL") {
            alarmOverlay.setBackgroundColor(android.graphics.Color.BLUE)
            alarmText.text = "SOS"
            alarmText.textSize = 120f
        } else {
            // Assume THEFT por defeito
            alarmOverlay.setBackgroundColor(android.graphics.Color.RED)
            alarmText.text = "!"
            alarmText.textSize = 150f
        }

        // Adiciona o texto ao ecrã e mostra-o
        alarmOverlay.addView(alarmText)
        alarmOverlay.visibility = View.VISIBLE

        // Oculta ao fim de 5 segundos
        Handler(Looper.getMainLooper()).postDelayed({
            alarmOverlay.visibility = View.GONE
        }, 5000)
    }

    // FUNCAO QUE CONFIGURA O MQTT CLIENT
    private fun setupMqttClient() {

        // Conecta-se ao broker usando a função Helper
        MqttHelper.connectToBroker(this)

        // Define o callback para gerir as mensagens
        MqttHelper.setCallback(object : MqttCallback {

            // CODIGO RESPONSAVEL PELA PERDA DE CONEXAO
            override fun connectionLost(cause: Throwable?) {
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
                var dangerLights = ""
                var mediumLights = ""
                var highBeam = ""
                var abs = ""
                var autonomy = ""
                var driveModeValue = ""
                var timeValue = ""
                var totalKmValue = ""
                var alarmType = ""

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
                            // quando o topico da mensagem mqtt é igual a "lights", muda o boolean das luzes médias da mota
                            "moto/lights" -> mediumLights = (payload)
                            // quando o topico da mensagem mqtt é igual a "maxlights", muda o boolean dos máximos da mota
                            "moto/maxlights" -> highBeam = (payload)
                            // quando o topico da mensagem mqtt é igual a "abs", muda o boolean do ABS da mota
                            "moto/abs" -> abs = (payload)
                            // quando o topico da mensagem mqtt é igual a "autonomy", muda o valor da autonomia da mota
                            "moto/autonomy" -> autonomy = (payload)
                            // quando o topico da mensagem mqtt é igual a "drive_mode", muda o modo de conducao da mota
                            "moto/drive_mode" -> driveModeValue = (payload)
                            // quando o topico da mensagem mqtt é igual a "time", muda o tempo visível no display da mota
                            "moto/time" -> timeValue = (payload)
                            // quando o topico da mensagem mqtt é igual a "total_kilometers", muda a quilometragem da mota
                            "moto/total_kilometers" -> totalKmValue = (payload)
                            // quando o topico da mensagem mqtt é igual a "alarm", ativa o alarme no ecrã
                            "moto/alarm" -> alarmType = payload
                        }

                        if (alarmType.isNotEmpty()) {
                            showAlarm(alarmType)
                        }

                        if (speed.isNotEmpty()) {
                            MqttHelper.updateSpeedometer(
                                this@MainInterface2Activity,
                                speed.toInt(),
                                speedometerFilling,
                                speedBox,
                                maxSpeed
                            )
                        }

                        if (battery.isNotEmpty()) {
                            MqttHelper.updateBattery(
                                this@MainInterface2Activity,
                                battery.toInt(),
                                batteryFilling,
                                batteryDisplay
                            )
                        }

                        if (batTemperature.isNotEmpty()) {
                            MqttHelper.updateBatteryTemperature(
                                this@MainInterface2Activity,
                                batTemperature.toInt(),
                                batteryTemperatureFilling
                            )
                        }

                        if (gear.isNotEmpty()) {
                            MqttHelper.updateGear(
                                this@MainInterface2Activity,
                                gear,
                                gearBox
                            )
                        }

                        if (autonomy.isNotEmpty()) {
                            MqttHelper.updateAutonomy(
                                this@MainInterface2Activity,
                                autonomy,
                                autonomyDisplay
                            )
                        }

                        if (totalKmValue.isNotEmpty()) {
                            MqttHelper.updateTotalKilometers(
                                this@MainInterface2Activity,
                                totalKmValue,
                                totalKmDisplay
                            )
                        }

                        if (timeValue.isNotEmpty()) {
                            MqttHelper.updateTime(
                                this@MainInterface2Activity,
                                timeValue,
                                timeDisplay
                            )
                        }

                        if (driveModeValue.isNotEmpty()) {
                            MqttHelper.updateDriveMode(
                                this@MainInterface2Activity,
                                driveModeValue,
                                driveMode
                            )
                        }

                        if (indicatorLeft.isNotEmpty()) {
                            MqttHelper.updateIndicatorLeft(
                                this@MainInterface2Activity,
                                indicatorLeft.toBoolean(),
                                helperFunction
                            )
                        }

                        if (indicatorRight.isNotEmpty()) {
                            MqttHelper.updateIndicatorRight(
                                this@MainInterface2Activity,
                                indicatorRight.toBoolean(),
                                helperFunction
                            )
                        }

                        if (dangerLights.isNotEmpty()) {
                            MqttHelper.updateDangerLights(
                                this@MainInterface2Activity,
                                dangerLights.toBoolean(),
                                helperFunction
                            )
                        }

                        if (mediumLights.isNotEmpty()) {
                            MqttHelper.updateMediumLights(
                                this@MainInterface2Activity,
                                mediumLights.toBoolean(),
                                helperFunction
                            )
                        }

                        if (highBeam.isNotEmpty()) {
                            MqttHelper.updateHighBeam(
                                this@MainInterface2Activity,
                                highBeam.toBoolean(),
                                helperFunction
                            )
                        }

                        if (abs.isNotEmpty()) {
                            MqttHelper.updateABS(
                                this@MainInterface2Activity,
                                abs.toBoolean(),
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

    // FUNCAO QUE ATUALIZA OS VALORES RELACIONADOS COM A TEMPERATURA
    private fun updateTemperature(temperature: Int) {
        temperatureDisplay.text = "$temperature°C"    // altera o valor da temperatura
    }

    // ESTA FUNCAO DETETA SE DETERMINADA TECLA FOI PRESSIONADA NO TECLADO
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_P -> {
                helperFunction.toggleDangerLightsBlinking()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // BOTOES DA INTERFACE ------------------------------------------------------------------------------------------------------------------------------------------
    private fun gpsButtonClick() {
        gpsButton.setOnClickListener {
            val intent = Intent(this, GPSActivity::class.java)
            startActivity(intent)
        }
    }

    // ------------------------------------------------------------------------------------------
}
