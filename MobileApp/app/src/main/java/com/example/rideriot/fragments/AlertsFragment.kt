package com.example.rideriot.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.rideriot.R
import com.example.rideriot.adapters.AlertAdapter
import com.example.rideriot.models.Alert
import com.example.rideriot.utils.AlertManager

class AlertsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var adapter: AlertAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_alerts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerViewAlerts)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Passa a função de clique para o Adapter
        adapter = AlertAdapter(AlertManager.alerts) { alertClicado ->
            showAlertDialog(alertClicado)
        }
        recyclerView.adapter = adapter

        // Ouve ativamente adições à lista
        AlertManager.onAlertAdded = {
            activity?.runOnUiThread {
                adapter.notifyDataSetChanged()
                recyclerView.scrollToPosition(0)
                checkEmptyState()
            }
        }

        checkEmptyState()
    }

    private fun checkEmptyState() {
        if (AlertManager.alerts.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
        }
    }

    private fun showAlertDialog(alert: Alert) {
        // Gera contexto extra mediante o tipo de alerta
        val conselho = when (alert.type) {
            "FALL" -> "ALERTA DE ACIDENTE:\nFoi enviada uma SMS com as coordenadas para os contactos de emergência. Caso possível, ligar para o 112"
            "THEFT" -> "AÇÃO RECOMENDADA:\nA mota foi movida enquanto estavionada. É recomendado verificar a localização GPS."
            "RAIN" -> "AÇÃO RECOMENDADA:\nRecomenda-se recolher ou cobrir a mota e o ecrã para evitar danos causados pela água."
            "FCW" -> "AVISO DE CONDUÇÃO:\nTravagem de emergência registada. É recomendado analisar as distâncias de segurança."
            else -> "Registo informativo do sistema."
        }

        AlertDialog.Builder(requireContext())
            .setTitle(alert.title)
            .setMessage("Hora de Registo: ${alert.timestamp}\n\nDetalhes:\n${alert.message}\n\n$conselho")
            .setPositiveButton("Entendido", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
        checkEmptyState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        AlertManager.onAlertAdded = null
    }
}