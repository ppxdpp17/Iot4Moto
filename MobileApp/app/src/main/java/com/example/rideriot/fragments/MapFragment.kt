package com.example.rideriot.fragments

import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.rideriot.MainActivity // Importa a tua MainActivity
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

    private val BROKER_URL = "tcp://85.234.145.56:1883"
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
        setSwitchListener() // Inicia o listener do gRPC
    }

    private fun setupMap() {
        map.setMultiTouchControls(true)
        map.controller.setZoom(20.0)
        map.controller.setCenter(GeoPoint(41.3000, -7.7400))
    }

    private fun setupMqtt() {
        mqttManager = MqttManager(requireContext())

        mqttManager.messageListener = { topic, payload ->
            activity?.runOnUiThread {
                when (topic) {
                    // (Opcional) Podemos ouvir a telemetria completa aqui se precisares de atualizar a UI
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
                // A App agora só subscreve telemetria. Os comandos descem via gRPC!
                mqttManager.subscribe("motos/AJP1RB5SXLB123456/telemetria")
            }
        }
    }

    private fun updateMapLocation() {
        val lat = currentLat ?: return
        val lon = currentLon ?: return
        val point = GeoPoint(lat, lon)

        if (motoMarker == null) {
            motoMarker = Marker(map)
            val customIcon = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.moto_icon)
            motoMarker?.icon = customIcon
            motoMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            motoMarker?.title = "Mota"
            map.overlays.add(motoMarker)
        }

        motoMarker?.position = point
        map.controller.setCenter(point)
        map.invalidate()
    }

    // =======================================================
    // A MAGIA DO gRPC ACONTECE AQUI
    // =======================================================
    private fun setSwitchListener() {
        switchMotor.setOnCheckedChangeListener { _, isChecked ->
            // Chama a função da MainActivity que vai disparar o pedido gRPC para a porta 50051
            if (isChecked) {
                (requireActivity() as MainActivity).enviarComandoMota(true)
                tvStatus.text = "Motor: ON"
            } else {
                (requireActivity() as MainActivity).enviarComandoMota(false)
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