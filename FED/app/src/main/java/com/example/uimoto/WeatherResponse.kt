package com.example.uimoto


// ESTE CODIGO É RESPONSAVEL POR MAPEAR A RESPOSTA DA API

data class WeatherResponse(
    val main: Main,
    val weather: List<Weather>
)

data class Main(
    val temp: Float
)

data class Weather(
    val icon: String
)