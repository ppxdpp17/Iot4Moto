package com.example.uimoto

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttMessage

// Data class to represent a Call
data class Call(
    val callerName: String,
    val callerNumber: String,
    val callDuration: String
)

// Adapter for the RecyclerView
class CallAdapter(
    val callList: List<Call>,
    private val onCallClick: (Call) -> Unit // Click listener as a lambda function
) : RecyclerView.Adapter<CallAdapter.CallViewHolder>() {

    inner class CallViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val callerName: TextView = itemView.findViewById(R.id.callerName)
        val callerNumber: TextView = itemView.findViewById(R.id.callerNumber)
        val callDuration: TextView = itemView.findViewById(R.id.callDuration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.call_item, parent, false)
        return CallViewHolder(view)
    }

    override fun onBindViewHolder(holder: CallViewHolder, position: Int) {
        val call = callList[position]
        holder.callerName.text = call.callerName
        holder.callerNumber.text = call.callerNumber
        holder.callDuration.text = call.callDuration

        // Set the click listener
        holder.itemView.setOnClickListener {
            onCallClick(call) // Pass the clicked song to the listener
        }
    }

    override fun getItemCount(): Int = callList.size
}

class CallsActivity : BaseActivity() {

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

    // chamadas
    private lateinit var makeCallNumber: TextView
    private lateinit var backspaceIcon: AppCompatButton
    private lateinit var startCall: ImageButton
    private lateinit var endCall: ImageButton
    private lateinit var callRecyclerView: RecyclerView
    private val callList = mutableListOf<Call>()
    private lateinit var callAdapter: CallAdapter
    private lateinit var currentCall: TextView
    private var callStartTime: Long = 0

    // piscas
    private lateinit var leftTurnSignal: ImageView
    private lateinit var rightTurnSignal: ImageView
    private lateinit var indicatorsContainer: LinearLayout

    // auxiliares
    private lateinit var helperFunction: HelperFunction

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.callsmenu)             // INTERFACE QUE ESTÁ A SER EXIBIDA

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

        // Bind the TextView where the number will be displayed
        makeCallNumber = findViewById(R.id.makeCallNumber)

        // Bind the backspace icon and start and end call buttons
        backspaceIcon = findViewById(R.id.backspaceIcon)
        startCall = findViewById(R.id.startCall)
        endCall = findViewById(R.id.endCall)

        // Listener for backspace icon
        backspaceIcon.setOnClickListener {
            val currentText = makeCallNumber.text.toString()
            if (currentText.isNotEmpty()) {
                makeCallNumber.text = currentText.substring(0, currentText.length - 1)
            }
        }

        // Listener for number buttons
        val numberButtons = arrayOf(
            findViewById<AppCompatButton>(R.id.button1),
            findViewById<AppCompatButton>(R.id.button2),
            findViewById<AppCompatButton>(R.id.button3),
            findViewById<AppCompatButton>(R.id.button4),
            findViewById<AppCompatButton>(R.id.button5),
            findViewById<AppCompatButton>(R.id.button6),
            findViewById<AppCompatButton>(R.id.button7),
            findViewById<AppCompatButton>(R.id.button8),
            findViewById<AppCompatButton>(R.id.button9),
            findViewById<AppCompatButton>(R.id.button0)
        )

        val maxDigits = 9
        for (button in numberButtons) {
            button.setOnClickListener {
                val currentText = makeCallNumber.text.toString()
                if (currentText.length < maxDigits) {
                    makeCallNumber.append(button.text)
                }
            }
        }

        makeCallNumber.text = "" // limpa o campo de chamada quando a app é iniciada

        currentCall = findViewById(R.id.currentCall)
        currentCall.text = ""    // limpa o campo de chamada atual

        // Apply the saved color theme
        val savedColor = getSavedColorPreference()
        applyColorTheme(savedColor)

        // Initialize callList with data
        callList.addAll(
            listOf(
                Call("Cristina Ferreira", "123456789", "3:36"),
                Call("Gouchinha", "987654321", "3:26"),
                Call("Fátima Lopes", "123789456", "3:16"),
                Call("Teresa Guilherme", "987321654", "3:36")
            )
        )

        // Find RecyclerView and set up
        callRecyclerView = findViewById(R.id.callsRecyclerView)
        callRecyclerView.layoutManager = LinearLayoutManager(this)

        // Set adapter with a click listener
        callAdapter = CallAdapter(callList) { selectedCall ->
            updateRightPanel(selectedCall) // Update the right-side panel
        }
        callRecyclerView.adapter = callAdapter

        startCall.setOnClickListener {
            val number = makeCallNumber.text.toString()
            if (number.isNotEmpty()) {
                callStartTime = System.currentTimeMillis()
                currentCall.text = "Chamada: $number  00:00"

                startCallTimer()
            }
        }

        endCall.setOnClickListener {
            endCall()
        }


        leftTurnSignal = findViewById(R.id.leftTurnSignalMusic)
        rightTurnSignal = findViewById(R.id.rightTurnSignalMusic)
        temperatureDisplay = findViewById(R.id.temperatureDisplay)
        weatherIcon = findViewById(R.id.weatherIcon)
        gearDisplay = findViewById(R.id.gearValue)
        speedBar = findViewById(R.id.speedometerBar)
        speedDisplay = findViewById(R.id.speedValue)
        batteryBar = findViewById(R.id.batteryBar)
        batteryDisplay = findViewById(R.id.batteryValue)
        timeDisplay = findViewById(R.id.time)
        indicatorsContainer = findViewById(R.id.indicatorContainer)

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

    // Function to update the right-side panel
    private fun updateRightPanel(call: Call) {
        val callerNumber: TextView = findViewById(R.id.makeCallNumber)

        callerNumber.text = call.callerNumber
    }

    private fun updateTemperature(temperature: Int) {
        temperatureDisplay.text = "$temperature°C"    // altera o valor da temperatura
    }

    private var handler = Handler(Looper.getMainLooper())
    private var callDuration = 0 // seconds

    private fun startCallTimer() {
        callDuration = 0
        handler.post(object : Runnable {
            override fun run() {
                val minutes = callDuration / 60
                val seconds = callDuration % 60
                currentCall.text = "Chamada: ${makeCallNumber.text}  %02d:%02d".format(minutes, seconds)

                callDuration++
                handler.postDelayed(this, 1000) // Update every second
            }
        })
    }

    private fun endCall() {
        handler.removeCallbacksAndMessages(null) // Stop timer

        if (callStartTime > 0) {
            val number = makeCallNumber.text.toString()
            val minutes = callDuration / 60
            val seconds = callDuration % 60
            val formattedDuration = "%02d:%02d".format(minutes, seconds)

            // Add new call to the top of the list
            callList.add(0, Call("Desconhecido", number, formattedDuration))

            // Notify adapter about item inserted instead of full dataset change
            callAdapter.notifyItemInserted(0)

            // Scroll to the top
            callRecyclerView.scrollToPosition(0)

            // Clear UI
            currentCall.text = ""
            makeCallNumber.text = ""
            callStartTime = 0L
        }
    }

    // CODIGO QUE CORRE QUANDO O PROGRAMA É TERMINADO
    override fun onDestroy() {
        super.onDestroy()
        if (MqttHelper.isConnected()) {
            MqttHelper.disconnect()  // Quando a atividade é destruída, desconecta-se do broker
        }
    }

    private fun getSavedColorPreference(): String {
        val prefs = getSharedPreferences("SettingsPrefs", MODE_PRIVATE)
        return prefs.getString("selectedColor", "#FFFFFF") ?: "#FFFFFF"  // Default to white
    }

    private fun applyColorTheme(color: String) {
        val parsedColor = android.graphics.Color.parseColor(color)
        findViewById<TextView>(R.id.callsLabel)?.setTextColor(parsedColor)
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
                                this@CallsActivity,
                                speed.toInt(),
                                speedBar,
                                speedDisplay,
                                120,
                                ContextCompat.getColor(this@CallsActivity, R.color.lightBlue),
                                "km/h",
                                "speed"
                            )
                        }

                        if (battery.isNotEmpty()) {
                            MqttHelper.updateBarMenus(
                                this@CallsActivity,
                                battery.toInt(),
                                batteryBar,
                                batteryDisplay,
                                100,
                                ContextCompat.getColor(this@CallsActivity, R.color.green),
                                "%",
                                "battery"
                            )
                        }

                        if (gear.isNotEmpty()) {
                            MqttHelper.updateGear(
                                this@CallsActivity,
                                gear,
                                gearDisplay
                            )
                        }

                        if (timeValue.isNotEmpty()) {
                            MqttHelper.updateTime(
                                this@CallsActivity,
                                timeValue,
                                timeDisplay
                            )
                        }

                        if (indicatorLeft.isNotEmpty()) {
                            MqttHelper.updateIndicatorLeft(
                                this@CallsActivity,
                                indicatorLeft.toBoolean(),
                                helperFunction
                            )
                        }

                        if (indicatorRight.isNotEmpty()) {
                            MqttHelper.updateIndicatorRight(
                                this@CallsActivity,
                                indicatorRight.toBoolean(),
                                helperFunction
                            )
                        }

                        if (dangerLights.isNotEmpty()) {
                            MqttHelper.updateDangerLights(
                                this@CallsActivity,
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
}