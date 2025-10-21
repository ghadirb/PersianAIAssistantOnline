package com.persianai.assistant.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class OSMSearchAPI {
    
    data class SearchResult(
        val title: String,
        val address: String,
        val latitude: Double,
        val longitude: Double
    )
    
    suspend fun searchInIran(query: String): List<SearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val urlString = "https://nominatim.openstreetmap.org/search?" +
                    "q=$encodedQuery&" +
                    "countrycodes=ir&" +
                    "format=json&" +
                    "addressdetails=1&" +
                    "limit=10"
                
                android.util.Log.d("OSMSearch", "Searching: $query")
                
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "PersianAIAssistant/2.6")
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val results = parseResults(response)
                    android.util.Log.d("OSMSearch", "Found ${results.size} results")
                    results
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                android.util.Log.e("OSMSearch", "Error: ${e.message}")
                emptyList()
            }
        }
    }
    
    private fun parseResults(json: String): List<SearchResult> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val item = arr.getJSONObject(i)
                SearchResult(
                    title = item.optString("display_name", "").split(",").firstOrNull() ?: "نامشخص",
                    address = item.optString("display_name", ""),
                    latitude = item.optDouble("lat", 0.0),
                    longitude = item.optDouble("lon", 0.0)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
