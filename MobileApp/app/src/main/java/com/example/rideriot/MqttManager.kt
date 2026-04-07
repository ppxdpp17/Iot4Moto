package com.example.rideriot.utils

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttManager(private val context: Context) {

    private var mqttClient: MqttAsyncClient? = null

    //Permite que o ForegroundService ou um Fragment injete a sua própria lógica de processamento quando chega uma mensagem
    var messageListener: ((String, String) -> Unit)? = null

    fun connect(brokerUri: String, clientId: String, onConnected: () -> Unit) {
        try {
            //MemoryPersistence: Os dados em trânsito não são guardados na flash do telemóvel
            mqttClient = MqttAsyncClient(brokerUri, clientId, MemoryPersistence())

            //Ouve tudo o que chega
            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {}
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    //Desempacota o payload e envia para quem estiver a escutar
                    messageListener?.invoke(topic!!, String(message!!.payload))
                }
                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 10
            }

            mqttClient?.connect(options, context, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    onConnected()
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {}
            })
        } catch (e: Exception) { e.printStackTrace() }
    }

    //Subscreve o tópico MQTT para receber info do Simulador
    fun subscribe(topic: String) {
        try {
            mqttClient?.subscribe(topic, 1)
        } catch (e: Exception) { e.printStackTrace() }
    }

    //Função para publicar mensagens (ON/OFF)
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
                Log.w("MQTT", "Cliente desligado.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun disconnect() {
        try {
            if (mqttClient?.isConnected == true) {
                mqttClient?.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}