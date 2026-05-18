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
import com.example.rideriot.BuildConfig
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ForegroundAlertService : Service() {

    private var lastLat: Double = 0.0
    private var lastLon: Double = 0.0

    //Controlo de Anti-Spam
    private var ultimoAlertaRecebido = "NONE"
    private var ultimoTempoAlerta: Long = 0

    //Scope para correr a Stream fora da Main Thread
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createAlertNotificationChannel()
        startForegroundServiceNotification()

        serviceScope.launch {
            TimeSyncManager.sync()
        }

        //Arranca a torneira gRPC
        iniciarStreamGrpc()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun iniciarStreamGrpc() {
        serviceScope.launch {
            val channel = ManagedChannelBuilder.forAddress(BuildConfig.SERVER_IP, 50051)
                .usePlaintext()
                .build()

            val stub = MotorcycleServiceGrpcKt.MotorcycleServiceCoroutineStub(channel)

            try {
                while (isActive) {
                    try {
                        AlertManager.updateStatus("A aguardar ligação... \uD83D\uDD34")
                        val request = StreamRequest.newBuilder().setVin("AJP1RB5SXLB123456").build()

                        stub.streamTelemetria(request).collect { data ->
                            AlertManager.updateStatus("Online \uD83D\uDFE2")
                            AlertManager.updateIgnition(data.ign)
                            AlertManager.updateLocation(data.lat, data.lon)
                            if (data.lat != 0.0 && data.lon != 0.0) {
                                lastLat = data.lat
                                lastLon = data.lon
                            }
                            processarAlerta(data.alert)
                        }
                    } catch (e: Exception) {
                        AlertManager.updateStatus("A aguardar ligação... \uD83D\uDD34")
                        delay(5000)
                    }
                }
            } finally {
                if (!channel.isShutdown) {
                    channel.shutdownNow()
                }
            }
        }
    }

    private fun processarAlerta(alertType: String) {
        //Deteção de "Chuva parou"
        if (alertType == "RAIN_CLEARED") {
            showResolvedRainNotification()
            AlertManager.resolveLastAlertOfType("RAIN")
            ultimoAlertaRecebido = "NONE"
            Log.d("SENTINEL", "Chuva parou.")
            return
        }

        //Limpeza de estado vinda da CSVU
        if (alertType == "NONE") {
            ultimoAlertaRecebido = "NONE"
            return
        }

        //REGISTO DE LATÊNCIA EXATO - para testes de stress
        val tsRececao = TimeSyncManager.getTrueTime()
        Log.e("ALERTA_$alertType", "$tsRececao")

        //Bloqueio Anti-Spam: 2 segundos
        val tempoAtual = System.currentTimeMillis()
        if (alertType == ultimoAlertaRecebido && (tempoAtual - ultimoTempoAlerta) < 2000) {
            return
        }

        ultimoAlertaRecebido = alertType
        ultimoTempoAlerta = tempoAtual

        //Verifica definições do utilizador
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
            .setContentText("A monitorizar sensores do motociclo.")
            .setSmallIcon(R.drawable.ic_moto_notif)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)
    }

    private fun createAlertNotificationChannel() {
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
        //ID 9999 fixo para a chuva para permitir sobrescrever no futuro
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

    //Substitui a notificação da Chuva em vez de criar uma nova
    private fun showResolvedRainNotification() {
        val channelId = "rideriot_env_channel"
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("TARGET_FRAGMENT", "ALERTS")
        }
        val pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Alerta de Chuva - RESOLVIDO")
            .setContentText("O sensor deixou de detetar precipitação.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        //Usar o ID 9999 substitui a caixa de alerta vermelha anterior sem fazer spam
        getSystemService(NotificationManager::class.java)?.notify(9999, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}