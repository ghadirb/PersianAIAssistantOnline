package com.persianai.assistant.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object NeshanAPI {
    private const val KEY = "service.649ba7521ba04da595c5ab56413b3c84"
    
    data class Route(val distance: Int, val duration: Int, val polyline: String)
    data class SearchResult(val name: String, val latitude: Double, val longitude: Double)
    
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
    
    suspend fun searchLocation(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.neshan.org/v1/search?term=$query&lat=35.7&lng=51.4"
            val conn = URL(url).openConnection()
            conn.setRequestProperty("Api-Key", KEY)
            val json = JSONObject(conn.getInputStream().bufferedReader().readText())
            val results = mutableListOf<SearchResult>()
            val items = json.optJSONArray("items") ?: return@withContext results
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val loc = item.getJSONObject("location")
                results.add(SearchResult(
                    item.optString("title", query),
                    loc.getDouble("latitude"),
                    loc.getDouble("longitude")
                ))
            }
            results
        } catch (e: Exception) { emptyList() }
    }
}
