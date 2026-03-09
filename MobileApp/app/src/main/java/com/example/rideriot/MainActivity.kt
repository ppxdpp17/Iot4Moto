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

    private fun setupUI() {
        map = findViewById(R.id.mapView)
        tvStatus = findViewById(R.id.tvStatus)
        val btnStart: Button = findViewById(R.id.btnStart)
        val btnStop: Button = findViewById(R.id.btnStop)

        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(GeoPoint(41.3000, -7.7400)) // Centrado em Vila Real

        btnStart.setOnClickListener { tvStatus.text = "Estado: ON" }
        btnStop.setOnClickListener { tvStatus.text = "Estado: OFF" }
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