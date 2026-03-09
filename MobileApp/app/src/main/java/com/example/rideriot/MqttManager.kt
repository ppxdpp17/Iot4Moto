package com.example.rideriot.utils

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttManager(private val context: Context) {
    private var mqttClient: MqttAsyncClient? = null

    fun connect(brokerUri: String, clientId: String, onConnected: () -> Unit) {
        try {
            mqttClient = MqttAsyncClient(brokerUri, clientId, MemoryPersistence())
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 10
                // Se o teu broker tiver password, descomenta isto:
                // userName = "teu_user"
                // password = "tua_password".toCharArray()
            }

            mqttClient?.connect(options, context, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("MQTT", "Ligado ao broker com sucesso!")
                    onConnected()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("MQTT", "Falha na ligação: ${exception?.message}")
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun publish(topic: String, payload: String, retain: Boolean = false) {
        try {
            if (mqttClient?.isConnected == true) {
                val message = MqttMessage(payload.toByteArray()).apply {
                    qos = 1
                    isRetained = retain
                }
                mqttClient?.publish(topic, message)
                Log.d("MQTT", "Publicado: $payload no tópico $topic")
            } else {
                Log.w("MQTT", "Cliente desligado. Impossível publicar.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}