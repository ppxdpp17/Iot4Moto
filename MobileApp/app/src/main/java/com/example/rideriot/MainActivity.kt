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

        //Arranca o serviço de segundo plano em background
        val serviceIntent = Intent(this, ForegroundAlertService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        //Interface
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNav.setOnItemSelectedListener { item ->
            val selectedFragment = when (item.itemId) {
                R.id.nav_map -> MapFragment()   //GPS
                R.id.nav_alerts -> AlertsFragment() //Lista de alarmes
                R.id.nav_camera -> CameraFragment() //Visualizador de feed da câmara
                R.id.nav_settings -> SettingsFragment() //Definições/Preferências
                else -> return@setOnItemSelectedListener false
            }

            //"Animação" de transição entre páginas
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                .replace(R.id.fragment_container, selectedFragment)
                .commit()

            true
        }

        //Tratamento de notificações clicadas
        if (savedInstanceState == null) {
            val targetFragment = intent.getStringExtra("TARGET_FRAGMENT")

            when (targetFragment) {
                "CAMERA" -> bottomNav.selectedItemId = R.id.nav_camera
                "ALERTS" -> bottomNav.selectedItemId = R.id.nav_alerts
                else -> bottomNav.selectedItemId = R.id.nav_map
            }
        }
    }
}