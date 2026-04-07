package com.example.rideriot.utils

import com.example.rideriot.models.Alert

object AlertManager {
    //Lista que armazena os alertas ativos
    val alerts = mutableListOf<Alert>()
    //Avisa a UI que existem novos alertas
    var onAlertAdded: (() -> Unit)? = null

    //Insere no topo da lista da UI o alerta mais recente, limitando-a a 50 itens
    fun addAlert(alert: Alert) {
        alerts.add(0, alert)
        if (alerts.size > 50) alerts.removeAt(alerts.lastIndex)
        onAlertAdded?.invoke()
    }

    fun resolveLastAlertOfType(type: String) {
        //'find' procura a partir do topo (index 0), apanhando o alerta mais recente
        val alertToUpdate = alerts.find { it.type == type && !it.title.contains("(Resolvido)") }
        alertToUpdate?.let {
            it.title = "${it.title} (Resolvido)"

            //Força o fragmento a redesenhar a lista para mostrar a alteração
            onAlertAdded?.invoke()
        }
    }
}