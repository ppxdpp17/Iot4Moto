package com.example.rideriot

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.rideriot.fragments.AlertsFragment
import com.example.rideriot.fragments.CameraFragment
import com.example.rideriot.fragments.MapFragment
import com.example.rideriot.fragments.SettingsFragment
import com.example.rideriot.utils.ForegroundAlertService
import com.example.rideriot.PermissionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var permissionManager: PermissionManager

    // Infraestrutura gRPC persistente para evitar desperdício de recursos
    private var grpcChannel: ManagedChannel? = null
    private var grpcStub: MotorcycleServiceGrpcKt.MotorcycleServiceCoroutineStub? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Inicializa o canal gRPC uma única vez (Reutilização de infraestrutura)
        setupGrpc()

        // 2. Gestão de permissões
        permissionManager = PermissionManager(this)
        permissionManager.checkAndRequest()

        // 3. Lançamento do serviço de vigilância em segundo plano
        val serviceIntent = Intent(this, ForegroundAlertService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // 4. Configuração da Navegação (Fragments)
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

        // 5. Tratamento de navegação vinda de Notificações
        if (savedInstanceState == null) {
            val targetFragment = intent.getStringExtra("TARGET_FRAGMENT")
            when (targetFragment) {
                "CAMERA" -> bottomNav.selectedItemId = R.id.nav_camera
                "ALERTS" -> bottomNav.selectedItemId = R.id.nav_alerts
                else -> bottomNav.selectedItemId = R.id.nav_map
            }
        }
    }

    private fun setupGrpc() {
        // O SERVER_IP é injetado aqui via local.properties através do Gradle
        grpcChannel = ManagedChannelBuilder.forAddress(BuildConfig.SERVER_IP, 50051)
            .usePlaintext() // Nota: Em ambiente real, substituir por TLS/SSL
            .build()

        grpcStub = MotorcycleServiceGrpcKt.MotorcycleServiceCoroutineStub(grpcChannel!!)
    }

    // ====================================================================
    // LÓGICA DE COMUNICAÇÃO CLOUD (gRPC OTIMIZADA)
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
                val actionCmd = if (ligar) "ign_on" else "ign_off"
                val request = CommandRequest.newBuilder()
                    .setVin("AJP1RB5SXLB123456")
                    .setAction(actionCmd)
                    .build()

                // Reutiliza o stub já existente, eliminando o delay de handshake
                val response = grpcStub?.sendCommand(request)

                if (response?.success == true) {
                    if (ligar) "Mota ligada com sucesso!" else "Mota desligada com sucesso!"
                } else {
                    "Falha no servidor: ${response?.message ?: "Desconhecida"}"
                }

            } catch (e: Exception) {
                "Erro de Rede: ${e.message}"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Encerra o canal de rede quando a atividade é destruída
        if (grpcChannel != null && !grpcChannel!!.isShutdown) {
            grpcChannel!!.shutdownNow()
        }
    }
}