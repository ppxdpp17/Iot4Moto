package com.example.rideriot.utils

import com.example.rideriot.models.Alert
import java.util.concurrent.CopyOnWriteArrayList

object AlertManager {
    //Lista Thread-Safe - impede o crash ConcurrentModificationException
    val alerts = CopyOnWriteArrayList<Alert>()

    //Callback para alerta NOVO vs alerta ATUALIZADO
    var onAlertAdded: (() -> Unit)? = null
    var onAlertUpdated: (() -> Unit)? = null

    var connectionStatus: String = "A aguardar ligação..."
    var lastLat: Double = 0.0
    var lastLon: Double = 0.0

    var onStatusChanged: ((String) -> Unit)? = null
    var onLocationChanged: ((Double, Double) -> Unit)? = null
    var ignitionState: Boolean = false
    var onIgnitionChanged: ((Boolean) -> Unit)? = null

    fun updateStatus(newStatus: String) {
        connectionStatus = newStatus
        onStatusChanged?.invoke(newStatus)
    }

    fun updateLocation(lat: Double, lon: Double) {
        lastLat = lat
        lastLon = lon
        onLocationChanged?.invoke(lat, lon)
    }

    fun addAlert(alert: Alert) {
        alerts.add(0, alert) //Adiciona fisicamente à lista
        if (alerts.size > 50) alerts.removeAt(alerts.lastIndex)
        onAlertAdded?.invoke() //Avisa o ecrã que há uma linha NOVA
    }

    fun resolveLastAlertOfType(type: String) {
        val alertToUpdate = alerts.find { it.type == type && !it.title.contains("(Resolvido)") }
        alertToUpdate?.let {
            it.title = "${it.title} (Resolvido)"
            onAlertUpdated?.invoke() //Avisa o ecrã que apenas mudou o título
        }
    }

    fun updateIgnition(isLigada: Boolean) {
        ignitionState = isLigada
        onIgnitionChanged?.invoke(isLigada)
    }
}