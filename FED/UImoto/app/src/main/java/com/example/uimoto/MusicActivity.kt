package com.example.uimoto

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttMessage

// Data class to represent a Song
data class Song(
    val number: String,
    val title: String,
    val artist: String,
    val duration: String
)

// Adapter for the RecyclerView
class SongAdapter(
    private val songList: List<Song>,
    private val onSongClick: (Song) -> Unit // Click listener as a lambda function
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val songNumber: TextView = itemView.findViewById(R.id.songNumber)
        val songTitle: TextView = itemView.findViewById(R.id.songTitle)
        val songArtist: TextView = itemView.findViewById(R.id.songArtist)
        val songDuration: TextView = itemView.findViewById(R.id.songDuration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.song_item, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songList[position]
        holder.songNumber.text = song.number
        holder.songTitle.text = song.title
        holder.songArtist.text = song.artist
        holder.songDuration.text = song.duration

        // Set the click listener
        holder.itemView.setOnClickListener {
            onSongClick(song) // Pass the clicked song to the listener
        }
    }

    override fun getItemCount(): Int = songList.size
}

// Main Activity for displaying the RecyclerView
class MusicActivity : BaseActivity() {

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
    private lateinit var indicatorsContainer: LinearLayout

    // auxiliares
    private lateinit var helperFunction: HelperFunction

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.musicmenu)             // INTERFACE QUE ESTÁ A SER EXIBIDA

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

        // Apply the saved color theme
        val savedColor = getSavedColorPreference()
        applyColorTheme(savedColor)

        // Sample song data
        val songList = listOf(
            Song("01", "In The End", "Linkin Park", "3:36"),
            Song("02", "Numb", "Linkin Park", "3:07"),
            Song("03", "Crawling", "Linkin Park", "3:29"),
            Song("04", "Bleed It Out", "Linkin Park", "2:45"),
            Song("05", "From the Inside", "Linkin Park", "2:56")
        )

        // Find RecyclerView and set up
        val songRecyclerView: RecyclerView = findViewById(R.id.songRecyclerView)
        songRecyclerView.layoutManager = LinearLayoutManager(this)

        // Set adapter with a click listener
        val songAdapter = SongAdapter(songList) { selectedSong ->
            updateRightPanel(selectedSong) // Update the right-side panel
        }
        songRecyclerView.adapter = songAdapter

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
    private fun updateRightPanel(song: Song) {
        val songTitle: TextView = findViewById(R.id.songTitleRight)
        val songArtist: TextView = findViewById(R.id.songArtistRight)
        val songDuration: TextView = findViewById(R.id.songDurationRight)

        // Update the views with the selected song's details
        songTitle.text = song.title
        songArtist.text = song.artist
        songDuration.text = song.duration
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
                                this@MusicActivity,
                                speed.toInt(),
                                speedBar,
                                speedDisplay,
                                120,
                                ContextCompat.getColor(this@MusicActivity, R.color.lightBlue),
                                "km/h",
                                "speed"
                            )
                        }

                        if (battery.isNotEmpty()) {
                            MqttHelper.updateBarMenus(
                                this@MusicActivity,
                                battery.toInt(),
                                batteryBar,
                                batteryDisplay,
                                100,
                                ContextCompat.getColor(this@MusicActivity, R.color.green),
                                "%",
                                "battery"
                            )
                        }

                        if (gear.isNotEmpty()) {
                            MqttHelper.updateGear(
                                this@MusicActivity,
                                gear,
                                gearDisplay
                            )
                        }

                        if (timeValue.isNotEmpty()) {
                            MqttHelper.updateTime(
                                this@MusicActivity,
                                timeValue,
                                timeDisplay
                            )
                        }

                        if (indicatorLeft.isNotEmpty()) {
                            MqttHelper.updateIndicatorLeft(
                                this@MusicActivity,
                                indicatorLeft.toBoolean(),
                                helperFunction
                            )
                        }

                        if (indicatorRight.isNotEmpty()) {
                            MqttHelper.updateIndicatorRight(
                                this@MusicActivity,
                                indicatorRight.toBoolean(),
                                helperFunction
                            )
                        }

                        if (dangerLights.isNotEmpty()) {
                            MqttHelper.updateDangerLights(
                                this@MusicActivity,
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

    private fun getSavedColorPreference(): String {
        val prefs = getSharedPreferences("SettingsPrefs", MODE_PRIVATE)
        return prefs.getString("selectedColor", "#FFFFFF") ?: "#FFFFFF"  // Default to white
    }

    private fun applyColorTheme(color: String) {
        val parsedColor = android.graphics.Color.parseColor(color)
        findViewById<TextView>(R.id.musicLabel)?.setTextColor(parsedColor)
    }
}
