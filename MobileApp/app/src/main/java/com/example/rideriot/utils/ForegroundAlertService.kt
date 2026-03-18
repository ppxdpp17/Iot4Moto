package com.example.rideriot.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.rideriot.MainActivity
import com.example.rideriot.models.Alert
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ForegroundAlertService : Service() {

    private lateinit var mqttManager: MqttManager
    private val BROKER_URL = "tcp://172.20.0.203:1883"
    private val CLIENT_ID = "AndroidApp_Watchdog_${System.currentTimeMillis()}"

    private var lastLat: Double = 0.0
    private var lastLon: Double = 0.0

    override fun onCreate() {
        super.onCreate()
        createAlertNotificationChannel()
        startForegroundServiceNotification()
        setupMqttWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundServiceNotification() {
        val channelId = "rideriot_watchdog_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Monitorização de Alertas",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("RiderIoT Ativo")
            .setContentText("A monitorizar mota em tempo real...")
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun createAlertNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "rideriot_alerts_channel",
                "Alertas de Emergência",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                // Padrão: [Espera, Vibra, Espera, Vibra, Espera, Vibra] em milissegundos
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 1000)
                enableVibration(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun showEmergencyNotification(title: String, message: String, alertType: String) {

        // Cria a intenção de abrir a app
        val intent = android.content.Intent(this, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Define o destino consoante o alerta
            val target = if (alertType == "THEFT") "CAMERA" else "ALERTS"
            putExtra("TARGET_FRAGMENT", target)
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(this, "rideriot_alerts_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // Já sabes, muda para o logo depois
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent) // ANEXA A AÇÃO AQUI
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager?.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun setupMqttWatchdog() {
        mqttManager = MqttManager(this)

        mqttManager.messageListener = { topic, payload ->
            when (topic) {
                "moto/gps" -> {
                    try {
                        val json = JSONObject(payload)
                        lastLat = json.getDouble("lat")
                        lastLon = json.getDouble("lon")
                    } catch (e: Exception) {
                        Log.e("WATCHDOG", "Erro a ler GPS: ${e.message}")
                    }
                }
                "moto/alarm" -> {
                    val alertType = payload.trim().uppercase()

                    if (alertType == "FALL") {
                        val coordenadasGPS = "$lastLat,$lastLon"
                        try {
                            val smsHelper = SmsHelper(this@ForegroundAlertService)
                            smsHelper.triggerEmergencySms(coordenadasGPS)
                        } catch (e: Exception) {
                            Log.e("WATCHDOG", "Erro a enviar SMS: ${e.message}")
                        }
                    }

                    //Gravar o alerta no histórico (AlertManager)
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
        }

        mqttManager.connect(BROKER_URL, CLIENT_ID) {
            mqttManager.subscribe("moto/alarm")
            mqttManager.subscribe("moto/gps")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mqttManager.disconnect()
    }
}