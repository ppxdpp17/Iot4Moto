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
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(1, notification)
        }
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
                        "THEFT" -> "Tentativa de Roubo" to "Movimento detetado com a mota estacionada."
                        "FALL"  -> "Queda Detetada" to "SOS ativado. Coordenadas enviadas."
                        "RAIN"  -> "Chuva" to "A mota está à chuva."
                        "FCW"   -> "Travagem Brusca" to "Risco de colisão."
                        else    -> "Informação" to "Evento: $alertType"
                    }

                    val newAlert = Alert(titulo, descricao, currentTime, alertType)
                    AlertManager.addAlert(newAlert)
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