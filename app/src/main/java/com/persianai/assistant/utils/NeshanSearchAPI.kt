package com.persianai.assistant.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class NeshanSearchAPI(private val context: Context) {
    
    private val apiKey = "service.649ba7521ba04da595c5ab56413b3c84"
    
    data class SearchResult(
        val title: String,
        val address: String,
        val latitude: Double,
        val longitude: Double,
        val category: String = ""
    )
    
    suspend fun searchGlobal(query: String): List<SearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                // جستجوی global - مرکز تهران برای نتایج بهتر
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val urlString = "https://api.neshan.org/v1/search?term=$encodedQuery&lat=35.699739&lng=51.338097"
                android.util.Log.d("NeshanSearch", "Searching for: $query")
                android.util.Log.d("NeshanSearch", "URL: $urlString")
                
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Api-Key", apiKey)
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                android.util.Log.d("NeshanSearch", "Response code: $responseCode")
                
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    android.util.Log.d("NeshanSearch", "Response: ${response.take(200)}")
                    val results = parseSearchResults(response)
                    android.util.Log.d("NeshanSearch", "Found ${results.size} results")
                    results
                } else {
                    val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                    android.util.Log.e("NeshanSearch", "Error response: $error")
                    emptyList()
                }
            } catch (e: Exception) {
                android.util.Log.e("NeshanSearch", "Exception: ${e.message}", e)
                emptyList()
            }
        }
    }
    
    suspend fun search(query: String, city: String = "تهران"): List<SearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode("$query $city", "UTF-8")
                val url = URL("https://api.neshan.org/v1/search?term=$encodedQuery&lat=35.699739&lng=51.338097")
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Api-Key", apiKey)
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val response = connection.inputStream.bufferedReader().readText()
                parseSearchResults(response)
            } catch (e: Exception) {
                android.util.Log.e("NeshanSearch", "Error: ${e.message}", e)
                emptyList()
            }
        }
    }
    
    private fun parseSearchResults(json: String): List<SearchResult> {
        return try {
            val jsonObj = JSONObject(json)
            val items = jsonObj.optJSONArray("items") ?: return emptyList()
            
            (0 until items.length()).mapNotNull { i ->
                val item = items.getJSONObject(i)
                val location = item.optJSONObject("location") ?: return@mapNotNull null
                
                SearchResult(
                    title = item.optString("title", "بدون نام"),
                    address = item.optString("address", ""),
                    latitude = location.optDouble("y", 0.0),
                    longitude = location.optDouble("x", 0.0),
                    category = item.optString("category", "")
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("NeshanSearch", "Parse error", e)
            emptyList()
        }
    }
}
