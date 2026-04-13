package com.example.rideriot.fragments

import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.rideriot.MainActivity
import com.example.rideriot.R
import com.example.rideriot.utils.AlertManager
import com.google.android.material.switchmaterial.SwitchMaterial
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

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

        // 1. Sincronização Imediata ao abrir o ecrã
        atualizarTextoStatus(AlertManager.connectionStatus)
        atualizarSwitchIgnicao(AlertManager.ignitionState)
        AlertManager.onIgnitionChanged = { isLigada ->
            activity?.runOnUiThread { atualizarSwitchIgnicao(isLigada) }
        }

        if (AlertManager.lastLat != 0.0) {
            atualizarPosicaoMapa(AlertManager.lastLat, AlertManager.lastLon)
        }

        // 2. Listeners para atualizações enquanto o ecrã estiver aberto
        AlertManager.onStatusChanged = { novoStatus ->
            activity?.runOnUiThread { atualizarTextoStatus(novoStatus) }
        }

        AlertManager.onLocationChanged = { lat, lon ->
            activity?.runOnUiThread { atualizarPosicaoMapa(lat, lon) }
        }

        switchMotor.setOnCheckedChangeListener { _, isChecked ->
            (requireActivity() as MainActivity).enviarComandoMota(isChecked)
        }

        setSwitchListener()
    }

    private fun setupMap() {
        map.setMultiTouchControls(true)
        map.controller.setZoom(18.0)
        map.controller.setCenter(GeoPoint(41.3000, -7.7400))
    }

    private fun atualizarTextoStatus(status: String) {
        tvStatus.text = "Estado: $status"
        tvStatus.setTextColor(if (status.contains("Ligado")) Color.GREEN else Color.WHITE)
    }

    private fun atualizarPosicaoMapa(lat: Double, lon: Double) {
        // Agora mostramos a mota mesmo que esteja em 0.0 para saberes que os dados estão a chegar
        val point = GeoPoint(lat, lon)

        if (motoMarker == null) {
            motoMarker = Marker(map)
            motoMarker?.icon = ContextCompat.getDrawable(requireContext(), R.drawable.moto_icon)
            motoMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            map.overlays.add(motoMarker)
        }

        motoMarker?.position = point
        map.controller.animateTo(point)
        map.invalidate()
    }

    private fun atualizarSwitchIgnicao(isLigada: Boolean) {
        if (switchMotor.isChecked != isLigada) {
            switchMotor.setOnCheckedChangeListener(null) // 1. Fica surdo a cliques
            switchMotor.isChecked = isLigada             // 2. Muda o botão visualmente
            setSwitchListener()                          // 3. Volta a ouvir os teus cliques
        }
    }

    private fun setSwitchListener() {
        switchMotor.setOnCheckedChangeListener { _, isChecked ->
            (requireActivity() as MainActivity).enviarComandoMota(isChecked)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        AlertManager.onStatusChanged = null
        AlertManager.onLocationChanged = null
    }

    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { super.onPause(); map.onPause() }
}