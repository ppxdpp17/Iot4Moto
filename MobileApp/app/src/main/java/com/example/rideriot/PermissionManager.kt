package com.example.rideriot

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionManager(private val activity: AppCompatActivity) {

    //Lista de permissões necessárias (mapa, SMS, notificações)
    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.SEND_SMS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.POST_NOTIFICATIONS
    )

    //API do Android para pedir permissões
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        //Verifica se todas as permissões do array foram concedidas pelo utilizador
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(activity, "Permissões recusadas! SOS e Mapa limitados.", Toast.LENGTH_LONG).show()
        }
    }

    //Função de ponto de entrada chamada na criação da MainActivity
    fun checkAndRequest() {
        //Filtra apenas as permissões que ainda não foram concedidas
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        //Lança o pop-up apenas para as permissões em falta
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions)
        }
    }
}