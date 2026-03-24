package com.example.rideriot.models

data class Alert(
    var title: String,
    val message: String,
    val timestamp: String,
    val type: String
)