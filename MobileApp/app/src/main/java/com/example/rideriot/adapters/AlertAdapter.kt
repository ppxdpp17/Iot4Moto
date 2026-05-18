package com.example.rideriot.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.rideriot.R
import com.example.rideriot.models.Alert

class AlertAdapter(private val alerts: MutableList<Alert>, private val onItemClick: (Alert) -> Unit) : RecyclerView.Adapter<AlertAdapter.AlertViewHolder>() {

    class AlertViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvAlertTitle)
        val tvTime: TextView = view.findViewById(R.id.tvAlertTime)
        val tvMessage: TextView = view.findViewById(R.id.tvAlertMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alert, parent, false)
        return AlertViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        val alert = alerts[position]
        holder.tvTitle.text = alert.title
        holder.tvTime.text = alert.timestamp
        holder.tvMessage.text = alert.message

        holder.itemView.setOnClickListener {
            onItemClick(alert)
        }
    }

    override fun getItemCount() = alerts.size

    fun notifyNewAlertAtTop() {
        notifyItemInserted(0)
    }
}