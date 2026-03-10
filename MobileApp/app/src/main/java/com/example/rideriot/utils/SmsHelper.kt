package com.example.rideriot.utils

import android.content.Context
import android.telephony.SmsManager
import android.util.Log

class SmsHelper(private val context: Context) {

    fun triggerEmergencySms(gpsData: String) {
        val prefs = context.getSharedPreferences("RiderIotSettings", Context.MODE_PRIVATE)

        //Verifica se o toggle do SOS está ativo
        if (!prefs.getBoolean("alert_fall", true)) {
            Log.d("SMS", "Alerta de queda desativado nas definições. SMS cancelado.")
            return
        }

        val msgBase = prefs.getString("sos_message", "Emergência! Queda de mota.")
        val finalMessage = "$msgBase Localização: $gpsData"

        val phones = listOf(
            prefs.getString("phone1", ""),
            prefs.getString("phone2", ""),
            prefs.getString("phone3", "")
        ).filter { !it.isNullOrBlank() } //Ignora os que estiverem vazios

        if (phones.isEmpty()) {
            Log.e("SMS", "Nenhum número configurado para SOS!")
            return
        }

        try {
            val smsManager = SmsManager.getDefault()
            for (phone in phones) {
                smsManager.sendTextMessage(phone, null, finalMessage, null, null)
                Log.d("SMS", "SMS enviado para $phone")
            }
        } catch (e: SecurityException) {
            Log.e("SMS", "Erro: Permissões de SMS necessárias.")
        } catch (e: Exception) {
            Log.e("SMS", "Falha ao enviar SMS: ${e.message}")
        }
    }
}