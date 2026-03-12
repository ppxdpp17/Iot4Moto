package com.example.rideriot.utils

import com.example.rideriot.models.Alert

object AlertManager {
    val alerts = mutableListOf<Alert>()
    var onAlertAdded: (() -> Unit)? = null //Callback para avisar o Fragmento

    fun addAlert(alert: Alert) {
        alerts.add(0, alert) //Adiciona no topo
        if (alerts.size > 50) alerts.removeAt(alerts.lastIndex) //Limpa os mais antigos (limite de 50)
        onAlertAdded?.invoke() //Dispara o aviso para atualizar a UI
    }
}