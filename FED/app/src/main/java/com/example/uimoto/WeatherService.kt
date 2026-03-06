package com.example.uimoto

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

// ESTE CODIGO É RESPONSAVEL POR DEFINIR A INTERFACE DO SERVICO QUE FORNECE A INFORMACAO DA METEOROLOGIA (OpenWeatherMap)
interface WeatherService {
    @GET("weather")
    fun getCurrentWeather(
        @Query("q") cityName: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"  // 'metric' no caso de graus Celsius
    ): Call<WeatherResponse>  // CHAMA UMA DATA CLASS DE RESPOSTA
}