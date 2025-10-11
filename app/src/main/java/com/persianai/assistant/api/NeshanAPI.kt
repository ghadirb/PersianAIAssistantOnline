package com.persianai.assistant.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object NeshanAPI {
    private const val KEY = "service.649ba7521ba04da595c5ab56413b3c84"
    
    data class Route(val distance: Int, val duration: Int, val polyline: String)
    
    suspend fun getRoute(oLat: Double, oLng: Double, dLat: Double, dLng: Double): Route? = 
        withContext(Dispatchers.IO) {
        try {
            val url = "https://api.neshan.org/v4/direction?type=car&origin=$oLat,$oLng&destination=$dLat,$dLng"
            val conn = URL(url).openConnection()
            conn.setRequestProperty("Api-Key", KEY)
            val json = JSONObject(conn.getInputStream().bufferedReader().readText())
            val leg = json.getJSONArray("routes").getJSONObject(0).getJSONArray("legs").getJSONObject(0)
            Route(
                leg.getJSONObject("distance").getInt("value"),
                leg.getJSONObject("duration").getInt("value"),
                json.getJSONArray("routes").getJSONObject(0).getJSONObject("overview_polyline").getString("points")
            )
        } catch (e: Exception) { null }
    }
}
