package com.example.rideriot.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.rideriot.MainActivity
import com.example.rideriot.models.Alert

// Imports gRPC e Coroutines
import com.example.rideriot.StreamRequest
import com.example.rideriot.MotorcycleServiceGrpcKt
import com.example.rideriot.R
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ForegroundAlertService : Service() {

    private var lastLat: Double = 0.0
    private var lastLon: Double = 0.0

    // Controlo de Anti-Spam
    private var ultimoAlertaRecebido = "NONE"
    private var ultimoTempoAlerta: Long = 0

    // Scope para correr a Stream fora da Main Thread
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createAlertNotificationChannel()
        startForegroundServiceNotification()

        // Arranca a torneira gRPC
        iniciarStreamGrpc()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun iniciarStreamGrpc() {
        serviceScope.launch {
            while (isActive) {
                try {
                    AlertManager.updateStatus("A aguardar ligação... 🔴")

                    val channel = ManagedChannelBuilder.forAddress("85.234.145.56", 50051)
                        .usePlaintext()
                        .build()

                    val stub = MotorcycleServiceGrpcKt.MotorcycleServiceCoroutineStub(channel)
                    val request = StreamRequest.newBuilder().setVin("AJP1RB5SXLB123456").build()

                    // O collect bloqueia aqui e fica à espera de dados
                    stub.streamTelemetria(request).collect { data ->
                        // Mal recebe o PRIMEIRO pacote, assume que está ligado
                        AlertManager.updateStatus("Ligado 🟢")

                        AlertManager.updateLocation(data.lat, data.lon)
                        processarAlerta(data.alert)
                    }
                } catch (e: Exception) {
                    AlertManager.updateStatus("Erro: A aguardar ligação... 🔴")
                    delay(5000)
                }
            }
        }
    }

    private fun processarAlerta(alertType: String) {
        // Lógica de limpeza: Se antes chovia e agora diz "NONE", resolve o alerta
        if (ultimoAlertaRecebido == "RAIN" && alertType == "NONE") {
            getSystemService(NotificationManager::class.java)?.cancel(9999)
            AlertManager.resolveLastAlertOfType("RAIN")
            ultimoAlertaRecebido = "NONE"
            Log.d("SENTINEL", "Chuva parou. Alerta resolvido no histórico.")
            return
        }

        if (alertType == "NONE") {
            ultimoAlertaRecebido = "NONE"
            return
        }

        // Bloqueio Anti-Spam: Ignora alertas repetidos num espaço de 10 segundos
        val tempoAtual = System.currentTimeMillis()
        if (alertType == ultimoAlertaRecebido && (tempoAtual - ultimoTempoAlerta) < 10000) {
            return
        }

        ultimoAlertaRecebido = alertType
        ultimoTempoAlerta = tempoAtual

        // Verifica definições do utilizador
        val prefs = getSharedPreferences("RiderIotSettings", Context.MODE_PRIVATE)
        val isEnabled = when (alertType) {
            "FALL"  -> prefs.getBoolean("alert_fall", true)
            "THEFT" -> prefs.getBoolean("alert_theft", true)
            "RAIN"  -> prefs.getBoolean("alert_rain", true)
            "FCW"   -> prefs.getBoolean("alert_fcw", true)
            else    -> true
        }

        if (isEnabled) {
            if (alertType == "FALL") {
                try {
                    val smsHelper = SmsHelper(this@ForegroundAlertService)
                    smsHelper.triggerEmergencySms("$lastLat,$lastLon")
                } catch (e: Exception) {
                    Log.e("SENTINEL", "Erro a enviar SMS: ${e.message}")
                }
            }

            val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val (titulo, descricao) = when (alertType) {
                "THEFT" -> "Alerta de Roubo!" to "Movimento detetado com a mota estacionada."
                "FALL"  -> "Queda Detetada!" to "SOS ativado. Coordenadas enviadas."
                "RAIN"  -> "Alerta de Chuva!" to "A mota está à chuva."
                "FCW"   -> "Aviso FCW!" to "Risco de colisão frontal."
                else    -> "Informação" to "Evento: $alertType"
            }

            // Grava no histórico (UI atualiza automaticamente)
            val newAlert = Alert(titulo, descricao, currentTime, alertType)
            AlertManager.addAlert(newAlert)
            showEmergencyNotification(titulo, descricao, alertType)
        }
    }

    private fun startForegroundServiceNotification() {
        val channelId = "rideriot_sentinel_channel"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("TARGET_FRAGMENT", "MAP")
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Monitorização", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("RiderIoT em Execução")
            .setContentText("Toque para obter mais informações ou para parar a app.")
            .setSmallIcon(R.drawable.ic_moto_notif)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)
    }

    private fun createAlertNotificationChannel() {
        // (O TEU CÓDIGO INTACTO AQUI)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val safetyChannel = NotificationChannel("rideriot_safety_channel", "Segurança", NotificationManager.IMPORTANCE_HIGH).apply {
                vibrationPattern = longArrayOf(0, 1000, 200, 1000)
                enableVibration(true)
            }
            val envChannel = NotificationChannel("rideriot_env_channel", "Ambiente", NotificationManager.IMPORTANCE_DEFAULT).apply {
                vibrationPattern = longArrayOf(0, 200, 100, 200)
                enableVibration(true)
            }
            manager?.createNotificationChannel(safetyChannel)
            manager?.createNotificationChannel(envChannel)
        }
    }

    private fun showEmergencyNotification(title: String, message: String, alertType: String) {
        // (O TEU CÓDIGO INTACTO AQUI)
        val notificationId = if (alertType == "RAIN") 9999 else System.currentTimeMillis().toInt()
        val channelId = if (alertType in listOf("FCW", "FALL", "THEFT")) "rideriot_safety_channel" else "rideriot_env_channel"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("TARGET_FRAGMENT", if (alertType == "THEFT") "CAMERA" else "ALERTS")
        }
        val pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(notificationId, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel() // Mata as coroutines quando o serviço for destruído
    }
}