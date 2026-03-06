package com.example.uimoto

import android.util.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object  WeatherAPICaller {

    private val apiWeatherKey = "4110624eb1fb4e97cc1250082d059c2f" // chave pessoal da API da meteorologia

     fun fetchWeather(city: String, callback: (Int, String) -> Unit) {
        // Instanciar o Retrofit
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(WeatherService::class.java)
        val weatherCall = service.getCurrentWeather(city, apiWeatherKey)

        // CHAMA A API DE FORMA ASSINCRONA
        weatherCall.enqueue(object : Callback<WeatherResponse> {
            override fun onResponse(
                call: Call<WeatherResponse>,
                response: Response<WeatherResponse>
            ) {
                if (response.isSuccessful) {
                    val weatherResponse = response.body()
                    weatherResponse?.let {
                        val temperature = it.main.temp
                        val iconCode = it.weather[0].icon   // extrai o icone do tempo da resposta da api
                        callback(temperature.toInt(), iconCode)
                    }
                } else {
                    Log.e("Weather API", "Response failed with code: ${response.code()}")
                    Log.e("Weather API", "Error body: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                t.printStackTrace()
            }
        })
    }
}