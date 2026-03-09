package com.example.rideriot

import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

import com.example.rideriot.PermissionManager
import com.example.rideriot.utils.MqttManager

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var tvStatus: TextView
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tem de ser instanciado antes de a atividade entrar no estado STARTED
        permissionManager = PermissionManager(this)

        Configuration.getInstance().load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
        setContentView(R.layout.activity_main)

        setupUI()

        // Pede as permissões usando a classe modularizada
        permissionManager.checkAndRequest()
    }

    // Adiciona estas variáveis no topo da classe, junto ao map e tvStatus:
    private lateinit var mqttManager: MqttManager
    private val BROKER_URL = "tcp://172.20.0.203:1883" // Verifica se a porta TCP é esta
    private val CLIENT_ID = "AndroidApp_Moto"

    // Substitui a tua função setupUI atual por esta:
    private fun setupUI() {
        map = findViewById(R.id.mapView)
        tvStatus = findViewById(R.id.tvStatus)
        val btnStart: Button = findViewById(R.id.btnStart)
        val btnStop: Button = findViewById(R.id.btnStop)

        // Inicializa Mapa
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(GeoPoint(41.3000, -7.7400))

        // Inicializa e Liga MQTT
        mqttManager = MqttManager(this)
        mqttManager.connect(BROKER_URL, CLIENT_ID) {
            runOnUiThread { tvStatus.text = "Estado: Ligado à Mota \uD83D\uDFE2" } // Bola verde
        }

        // Eventos de Clique
        btnStart.setOnClickListener {
            mqttManager.publish("sim/motorcycle", "on", retain = true)
            tvStatus.text = "Estado: ON"
        }

        btnStop.setOnClickListener {
            mqttManager.publish("sim/motorcycle", "off", retain = true)
            tvStatus.text = "Estado: OFF"
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}