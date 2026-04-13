package com.example.rideriot.utils

import com.example.rideriot.models.Alert

object AlertManager {
    val alerts = mutableListOf<Alert>()
    var onAlertAdded: (() -> Unit)? = null

    // --- NOVOS CAMPOS PARA O ESTADO REAL-TIME ---
    var connectionStatus: String = "A aguardar ligação..."
    var lastLat: Double = 0.0
    var lastLon: Double = 0.0

    // Callbacks para avisar a UI
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
        alerts.add(0, alert)
        if (alerts.size > 50) alerts.removeAt(alerts.lastIndex)
        onAlertAdded?.invoke()
    }

    fun resolveLastAlertOfType(type: String) {
        val alertToUpdate = alerts.find { it.type == type && !it.title.contains("(Resolvido)") }
        alertToUpdate?.let {
            it.title = "${it.title} (Resolvido)"
            onAlertAdded?.invoke()
        }
    }

    fun updateIgnition(isLigada: Boolean) {
        ignitionState = isLigada
        onIgnitionChanged?.invoke(isLigada)
    }
}