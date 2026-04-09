package com.example.rideriot

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView

// Os teus Fragments e Serviços
import com.example.rideriot.fragments.AlertsFragment
import com.example.rideriot.fragments.CameraFragment
import com.example.rideriot.fragments.MapFragment
import com.example.rideriot.fragments.SettingsFragment
import com.example.rideriot.utils.ForegroundAlertService

// Bibliotecas do gRPC e Coroutines
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Classes geradas pelo teu ficheiro .proto
import com.example.rideriot.CommandRequest
import com.example.rideriot.MotorcycleServiceGrpcKt

class MainActivity : AppCompatActivity() {

    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionManager = PermissionManager(this)
        permissionManager.checkAndRequest()

        val serviceIntent = Intent(this, ForegroundAlertService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNav.setOnItemSelectedListener { item ->
            val selectedFragment = when (item.itemId) {
                R.id.nav_map -> MapFragment()
                R.id.nav_alerts -> AlertsFragment()
                R.id.nav_camera -> CameraFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> return@setOnItemSelectedListener false
            }

            supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                .replace(R.id.fragment_container, selectedFragment)
                .commit()

            true
        }

        if (savedInstanceState == null) {
            val targetFragment = intent.getStringExtra("TARGET_FRAGMENT")
            when (targetFragment) {
                "CAMERA" -> bottomNav.selectedItemId = R.id.nav_camera
                "ALERTS" -> bottomNav.selectedItemId = R.id.nav_alerts
                else -> bottomNav.selectedItemId = R.id.nav_map
            }
        }
    }

    // ====================================================================
    // LÓGICA DE COMUNICAÇÃO CLOUD (gRPC)
    // ====================================================================
    fun enviarComandoMota(ligar: Boolean) {
        lifecycleScope.launch {
            val resultado = executarChamadaGrpc(ligar)
            Toast.makeText(this@MainActivity, resultado, Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun executarChamadaGrpc(ligar: Boolean): String {
        return withContext(Dispatchers.IO) {
            try {
                // ATENÇÃO: SUBSTITUI PELO TEU IP UBUNTU
                val channel = ManagedChannelBuilder.forAddress("85.234.145.56", 50051)
                    .usePlaintext()
                    .build()

                val stub = MotorcycleServiceGrpcKt.MotorcycleServiceCoroutineStub(channel)

                val actionCmd = if (ligar) "ign_on" else "ign_off"
                val request = CommandRequest.newBuilder()
                    .setVin("AJP1RB5SXLB123456")
                    .setAction(actionCmd)
                    .build()

                val response = stub.sendCommand(request)

                channel.shutdown()

                if (response.success) "Servidor: ${response.message}" else "Falha no servidor"

            } catch (e: Exception) {
                "Erro de Rede: ${e.message}"
            }
        }
    }
}