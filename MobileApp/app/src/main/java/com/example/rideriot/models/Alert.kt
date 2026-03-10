package com.example.rideriot.models

data class Alert(
    val title: String,
    val message: String,
    val timestamp: String,
    val type: String // Ex:"THEFT", "FALL", "FCW", "RAIN" para depois mudarmos as cores ou ícones
)