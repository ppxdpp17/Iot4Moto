package com.example.rideriot.fragments

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
import org.osmdroid.views.overlay.Marker

class MapFragment : Fragment() {

    private lateinit var map: MapView
    private lateinit var tvStatus: TextView
    private lateinit var switchMotor: SwitchMaterial
    private lateinit var mqttManager: MqttManager

    private val BROKER_URL = "tcp://172.20.0.203:1883" // Mantém o teu IP
    private val CLIENT_ID = "AndroidApp_Map_${System.currentTimeMillis()}"

    // Variáveis para o GPS
    private var motoMarker: Marker? = null
    private var currentLat: Double? = null
    private var currentLon: Double? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
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
        map.controller.setZoom(18.0) // Mais zoom para ver a rua
        map.controller.setCenter(GeoPoint(41.3000, -7.7400))
    }

    private fun setupMqtt() {
        mqttManager = MqttManager(requireContext())

        mqttManager.messageListener = { topic, payload ->
            activity?.runOnUiThread {
                when (topic) {
                    "sim/motorcycle" -> {
                        val isOn = payload.trim() == "on"
                        if (switchMotor.isChecked != isOn) {
                            switchMotor.setOnCheckedChangeListener(null)
                            switchMotor.isChecked = isOn
                            tvStatus.text = "Motor: ${payload.uppercase()}"
                            setSwitchListener()
                        }
                    }
                    "moto/gps" -> {
                        try {
                            val json = org.json.JSONObject(payload)
                            currentLat = json.getDouble("lat")
                            currentLon = json.getDouble("lon")
                            updateMapLocation()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        mqttManager.connect(BROKER_URL, CLIENT_ID) {
            activity?.runOnUiThread {
                tvStatus.text = "Estado: Ligado à mota \uD83D\uDFE2"
                mqttManager.subscribe("sim/motorcycle")
                mqttManager.subscribe("moto/gps")
            }
        }

        setSwitchListener()
    }

    private fun updateMapLocation() {
        // Só atualiza se já tiver as duas coordenadas
        val lat = currentLat ?: return
        val lon = currentLon ?: return

        val point = GeoPoint(lat, lon)

        if (motoMarker == null) {
            motoMarker = Marker(map)
            motoMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            motoMarker?.title = "A tua Mota"
            map.overlays.add(motoMarker)
        }

        motoMarker?.position = point
        map.controller.setCenter(point)
        map.invalidate() // Força o redesenho do mapa
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

    override fun onDestroyView() {
        super.onDestroyView()
        mqttManager.disconnect()
        map.onDetach()
    }
}