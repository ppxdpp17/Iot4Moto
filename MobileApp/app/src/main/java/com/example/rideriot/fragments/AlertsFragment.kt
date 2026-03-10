package com.example.rideriot.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.rideriot.R
import com.example.rideriot.adapters.AlertAdapter
import com.example.rideriot.models.Alert
import com.example.rideriot.utils.MqttManager
import com.example.rideriot.utils.SmsHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AlertAdapter
    private val alertList = mutableListOf<Alert>()

    private lateinit var mqttManager: MqttManager
    private val BROKER_URL = "tcp://172.20.0.203:1883" //IP da CSVU
    private val CLIENT_ID = "AndroidApp_Alerts_${System.currentTimeMillis()}" //ID único dinâmico para evitar conflitos MQTT

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_alerts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerViewAlerts)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = AlertAdapter(alertList)
        recyclerView.adapter = adapter

        setupMqtt()
    }

    private fun setupMqtt() {
        mqttManager = MqttManager(requireContext())

        //Define o que fazer quando chega um alerta do C#
        mqttManager.messageListener = { topic, payload ->
            //Exemplo de tópico esperado: "sim/alerts/theft"
            if (topic == "moto/alarm") {

                //Extrair o conteúdo
                val alertType = payload.trim().uppercase()

                //Vai buscar a hora atual do telemóvel
                val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

                //Atualiza a UI no ecrã
                activity?.runOnUiThread {
                    // Traduz o código da máquina para algo útil
                    val (titulo, descricao) = when (alertType) {
                        "THEFT" -> "Tentativa de Roubo" to "Movimento detetado com a mota estacionada."
                        "FALL"  -> "Queda Detetada" to "A iniciar protocolo de SOS..."
                        "RAIN"  -> "Chuva" to "A mota está estacionada à chuva."
                        "FCW"   -> "Travagem Brusca" to "Risco de colisão frontal detetado."
                        else    -> "Informação" to "Evento registado: $alertType"
                    }

                    //Alerta de Queda
                    if(alertType == "FALL") {
                        //Para já, envia o payload como GPS.
                        //TODO: Ajustar para extrair a lat/long real do MQTT.
                        val smsHelper = SmsHelper(requireContext())
                        smsHelper.triggerEmergencySms(payload)
                    }

                    val newAlert = Alert(
                        title = titulo,
                        message = descricao,
                        timestamp = currentTime,
                        type = alertType
                    )
                    adapter.addAlert(newAlert)
                    recyclerView.scrollToPosition(0)

                }
            }
        }

        //Liga e subscreve a todos os tópicos de alerta
        mqttManager.connect(BROKER_URL, CLIENT_ID) {
            mqttManager.subscribe("moto/alarm")
        }
    }
}