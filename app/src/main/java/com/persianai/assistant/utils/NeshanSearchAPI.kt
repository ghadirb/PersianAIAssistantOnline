package com.persianai.assistant.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class NeshanSearchAPI(private val context: Context) {
    
    private val apiKey = NeshanKeyProvider.getApiKey()
    private val osmSearch = OSMSearchAPI()
    
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
                android.util.Log.d("NeshanSearch", "🔍 Nationwide search for: $query")
                
                // جستجو در هر دو API به صورت موازی
                val neshanResults = try {
                    searchWithNeshan(query)
                } catch (e: Exception) {
                    android.util.Log.w("NeshanSearch", "Neshan failed: ${e.message}")
                    emptyList()
                }
                
                val osmResults = try {
                    osmSearch.searchInIran(query).map { 
                        SearchResult(it.title, it.address, it.latitude, it.longitude, "OSM")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("NeshanSearch", "OSM failed: ${e.message}")
                    emptyList()
                }
                
                // ترکیب نتایج و حذف تکراری
                val allResults = (neshanResults + osmResults)
                    .distinctBy { "${it.latitude.format(4)},${it.longitude.format(4)}" }
                    .take(15)
                
                android.util.Log.d("NeshanSearch", "✅ Total: ${allResults.size} (Neshan: ${neshanResults.size}, OSM: ${osmResults.size})")
                allResults
            } catch (e: Exception) {
                android.util.Log.e("NeshanSearch", "Exception: ${e.message}", e)
                emptyList()
            }
        }
    }
    
    private fun Double.format(decimals: Int) = "%.${decimals}f".format(this)
    
    private suspend fun searchWithNeshan(query: String): List<SearchResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val urlString = "https://api.neshan.org/v1/search?term=$encodedQuery&lat=35.699739&lng=51.338097"
        
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("Api-Key", apiKey)
        connection.requestMethod = "GET"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        
        if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().readText()
            return parseSearchResults(response)
        }
        return emptyList()
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
