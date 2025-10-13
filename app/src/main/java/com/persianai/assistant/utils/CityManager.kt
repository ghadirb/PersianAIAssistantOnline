package com.persianai.assistant.utils

data class City(val name: String, val lat: Double, val lon: Double)

object CityManager {
    
    val cities = listOf(
        City("تهران", 35.6892, 51.3890),
        City("مشهد", 36.2974, 59.6067),
        City("اصفهان", 32.6546, 51.6680),
        City("شیراز", 29.5918, 52.5836),
        City("تبریز", 38.0800, 46.2919),
        City("کرج", 35.8327, 50.9916),
        City("قم", 34.6416, 50.8746),
        City("اهواز", 31.3183, 48.6706),
        City("کرمانشاه", 34.3142, 47.0650),
        City("رشت", 37.2808, 49.5832)
    )
    
    fun getCityByName(name: String): City? {
        return cities.find { it.name == name }
    }
}
