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
import com.example.rideriot.utils.AlertManager

class AlertsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AlertAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_alerts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerViewAlerts)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Aponta diretamente para o armazém global
        adapter = AlertAdapter(AlertManager.alerts)
        recyclerView.adapter = adapter

        // Ouve ativamente adições à lista para fazer scroll automático
        AlertManager.onAlertAdded = {
            activity?.runOnUiThread {
                adapter.notifyDataSetChanged()
                recyclerView.scrollToPosition(0)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged() // Força refresh ao reabrir a aba
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Previne memory leaks quando fechas a aba
        AlertManager.onAlertAdded = null
    }
}