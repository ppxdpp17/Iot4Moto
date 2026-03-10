package com.example.rideriot.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class ForegroundAlertService : Service() {

    private lateinit var mqttManager: MqttManager
    private val BROKER_URL = "tcp://172.20.0.203:1883" //IP da CSVU
    private val CLIENT_ID = "AndroidApp_Watchdog_${System.currentTimeMillis()}"

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        setupMqttWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //START_STICKY diz ao Android para recriar o serviço se ele for morto por falta de RAM
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null //Não precisamos de dar bind à UI
    }

    private fun startForegroundServiceNotification() {
        val channelId = "rideriot_watchdog_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Monitorização de Alertas",
                NotificationManager.IMPORTANCE_LOW //Low para não estar sempre a apitar
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("RiderIoT Ativo")
            .setContentText("A monitorizar mota em tempo real...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) //TODO: Mudar este ícone
            .build()

        startForeground(1, notification)
    }

    private fun setupMqttWatchdog() {
        mqttManager = MqttManager(this)

        mqttManager.messageListener = { topic, payload ->
            if (topic == "moto/alarm") {
                val alertType = payload.trim().uppercase()

                if (alertType == "FALL") {
                    Log.d("WATCHDOG", "Queda detetada em background! A iniciar SOS...")
                    try {
                        val smsHelper = SmsHelper(this)
                        smsHelper.triggerEmergencySms(payload)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        mqttManager.connect(BROKER_URL, CLIENT_ID) {
            mqttManager.subscribe("moto/alarm")
            Log.d("WATCHDOG", "Vigilância MQTT ativada em background.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}