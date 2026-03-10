package com.example.rideriot.fragments // Confirma o teu package!

import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.rideriot.R
import com.example.rideriot.utils.MqttManager
import com.google.android.material.switchmaterial.SwitchMaterial
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

class MapFragment : Fragment() {

    private lateinit var map: MapView
    private lateinit var tvStatus: TextView
    private lateinit var switchMotor: SwitchMaterial
    private lateinit var mqttManager: MqttManager

    private val BROKER_URL = "tcp://172.20.0.203:1883" //IP da CSVU
    private val CLIENT_ID = "AndroidApp_Map"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        //Carregar as prefs do OSMDroid com requireContext() no Fragment
        Configuration.getInstance().load(requireContext(), PreferenceManager.getDefaultSharedPreferences(requireContext()))
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        map = view.findViewById(R.id.mapView)
        tvStatus = view.findViewById(R.id.tvStatus)
        switchMotor = view.findViewById(R.id.switchMotor)

        setupMap()
        setupMqtt()
    }

    private fun setupMap() {
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(GeoPoint(41.3000, -7.7400)) //Forçar estas coordenadas (VR)
    }

    private fun setupMqtt() {
        mqttManager = MqttManager(requireContext())

        // Define o que fazer quando recebe mensagens
        mqttManager.messageListener = { topic, payload ->
            if (topic == "sim/motorcycle") {
                activity?.runOnUiThread {
                    val isOn = payload.trim() == "on"
                    // Só atualiza se o estado do switch for diferente do recebido
                    if (switchMotor.isChecked != isOn) {
                        switchMotor.setOnCheckedChangeListener(null) // Pausa o clique
                        switchMotor.isChecked = isOn
                        tvStatus.text = "Motor: ${payload.uppercase()}"
                        setSwitchListener() // Reativa o clique
                    }
                }
            }
        }

        mqttManager.connect(BROKER_URL, CLIENT_ID) {
            activity?.runOnUiThread {
                tvStatus.text = "Estado: Ligado à mota \uD83D\uDFE2"
                mqttManager.subscribe("sim/motorcycle") // Subscreve assim que liga
            }
        }

        setSwitchListener()
    }

    private fun setSwitchListener() {
        switchMotor.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                mqttManager.publish("sim/motorcycle", "on", retain = true)
                tvStatus.text = "Motor: ON"
            } else {
                mqttManager.publish("sim/motorcycle", "off", retain = true)
                tvStatus.text = "Motor: OFF"
            }
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