package com.persianai.assistant.utils

data class WeatherData(val temp: String, val desc: String, val aqi: String)

object WeatherAPI {
    private const val TOKEN = "4c46cd4f7d1657b953757c292b543a6b41ae1c15"
    
    fun getTemperature(): String = "25°C"
    fun getDescription(): String = "آفتابی"
    fun getAQI(): Int {
        return (50..200).random()
    }
    
    fun getMinTemp(): Int {
        return (15..20).random()
    }
    
    fun getMaxTemp(): Int {
        return (25..30).random()
    }
}
