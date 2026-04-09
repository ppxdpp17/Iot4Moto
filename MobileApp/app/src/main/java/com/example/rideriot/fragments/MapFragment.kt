package com.example.rideriot.fragments

import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.rideriot.MainActivity
import com.example.rideriot.R
import com.google.android.material.switchmaterial.SwitchMaterial
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

// Imports das classes gRPC geradas
import com.example.rideriot.StreamRequest
import com.example.rideriot.TelemetriaData
import com.example.rideriot.MotorcycleServiceGrpcKt
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MapFragment : Fragment() {

    private lateinit var map: MapView
    private lateinit var tvStatus: TextView
    private lateinit var switchMotor: SwitchMaterial

    private var motoMarker: Marker? = null

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
        setSwitchListener()

        // Inicia a escuta de dados via gRPC Stream
        observarTelemetria()
    }

    private fun setupMap() {
        map.setMultiTouchControls(true)
        map.controller.setZoom(18.0)
        map.controller.setCenter(GeoPoint(41.3000, -7.7400))
    }

    private fun observarTelemetria() {
        // O lifecycleScope garante que a stream fecha quando sais do Fragment
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val channel = ManagedChannelBuilder.forAddress("85.234.145.56", 50051)
                    .usePlaintext()
                    .build()

                val stub = MotorcycleServiceGrpcKt.MotorcycleServiceCoroutineStub(channel)
                val request = StreamRequest.newBuilder().setVin("AJP1RB5SXLB123456").build()

                // O 'collect' fica bloqueado à espera de dados do servidor (Stream)
                stub.streamTelemetria(request).collect { data ->
                    // Volta para a UI thread para atualizar o mapa
                    launch(Dispatchers.Main) {
                        atualizarInterface(data.lat, data.lon, data.alert)
                    }
                }
            } catch (e: Exception) {
                Log.e("gRPC_Stream", "Erro na stream: ${e.message}")
            }
        }
    }

    private fun atualizarInterface(lat: Double, lon: Double, alerta: String) {
        val point = GeoPoint(lat, lon)

        // Atualiza Marcador
        if (motoMarker == null) {
            motoMarker = Marker(map)
            motoMarker?.icon = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.moto_icon)
            map.overlays.add(motoMarker)
        }
        motoMarker?.position = point
        map.controller.animateTo(point)

        // Atualiza Status/Alertas
        if (alerta.isNotEmpty() && alerta != "NONE") {
            tvStatus.text = "ALERTA: $alerta ⚠️"
        } else {
            tvStatus.text = "Mota em segurança ✅"
        }

        map.invalidate()
    }

    private fun setSwitchListener() {
        switchMotor.setOnCheckedChangeListener { _, isChecked ->
            // Continua a usar o método que já criámos na MainActivity (gRPC Unário)
            (requireActivity() as MainActivity).enviarComandoMota(isChecked)
        }
    }

    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { super.onPause(); map.onPause() }
}