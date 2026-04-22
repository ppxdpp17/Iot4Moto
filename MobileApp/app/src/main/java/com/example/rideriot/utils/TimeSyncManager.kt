package com.example.rideriot.utils

import android.util.Log
import org.apache.commons.net.ntp.NTPUDPClient
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TimeSyncManager {
    // Offset em milissegundos a aplicar ao relógio do telemóvel
    var timeOffset: Long = 0L
    private var isSynced = false

    // O IP do Raspberry Pi na sua VPN. Confirme se é mesmo o .1
    private const val RPI_VPN_IP = "172.20.0.154"

    suspend fun sync() {
        withContext(Dispatchers.IO) {
            val client = NTPUDPClient()
            client.defaultTimeout = 5000 // 5 segundos de timeout para evitar bloquear

            try {
                client.open()
                val hostAddr = InetAddress.getByName(RPI_VPN_IP)
                val info = client.getTime(hostAddr)

                info.computeDetails() // Calcula o offset

                timeOffset = info.offset ?: 0L
                isSynced = true

                Log.i("TIME_SYNC", "Sucesso! Sincronizado com RPi. Offset: $timeOffset ms")
            } catch (e: Exception) {
                Log.e("TIME_SYNC", "Falha na sincronização NTP via VPN: ${e.message}")
            } finally {
                client.close()
            }
        }
    }

    // Função para chamar quando quiser o tempo exato e sincronizado
    fun getTrueTime(): Long {
        if (!isSynced) {
            Log.w("TIME_SYNC", "Aviso: Usando tempo não sincronizado!")
        }
        return System.currentTimeMillis() + timeOffset
    }
}