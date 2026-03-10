package com.example.rideriot

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.rideriot.fragments.* // Importa os teus fragments
import com.example.rideriot.utils.ForegroundAlertService

class MainActivity : AppCompatActivity() {

    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Inicia e pede permissões
        permissionManager = PermissionManager(this)
        permissionManager.checkAndRequest()

        //Arranca o serviço de segundo plano (alerta) em background
        val serviceIntent = Intent(this, ForegroundAlertService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Começa sempre no separador do Mapa
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.fragment_container, MapFragment()).commit()
        }

        // Lógica de troca de separadores
        bottomNav.setOnItemSelectedListener { item ->
            val selectedFragment = when (item.itemId) {
                R.id.nav_map -> MapFragment()
                R.id.nav_alerts -> AlertsFragment()
                R.id.nav_camera -> CameraFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> return@setOnItemSelectedListener false
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, selectedFragment)
                .commit()

            true
        }
    }
}