package com.example.rideriot.utils

import com.example.rideriot.models.Alert

object AlertManager {
    val alerts = mutableListOf<Alert>()
    var onAlertAdded: (() -> Unit)? = null

    fun addAlert(alert: Alert) {
        alerts.add(0, alert)
        if (alerts.size > 50) alerts.removeAt(alerts.lastIndex)
        onAlertAdded?.invoke()
    }

    fun resolveLastAlertOfType(type: String) {
        // 'find' procura a partir do topo (index 0), apanhando o alerta mais recente
        val alertToUpdate = alerts.find { it.type == type && !it.title.contains("(Resolvido)") }
        alertToUpdate?.let {
            it.title = "${it.title} (Resolvido)"

            // CRÍTICO: Força o fragmento a redesenhar a lista para mostrar a alteração
            onAlertAdded?.invoke()
        }
    }
}