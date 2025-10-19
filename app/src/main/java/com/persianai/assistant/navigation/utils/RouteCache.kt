package com.persianai.assistant.navigation.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.persianai.assistant.navigation.models.*
import org.osmdroid.util.GeoPoint
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * سیستم کش مسیر برای بهینه‌سازی و کاهش مصرف API
 */
class RouteCache(private val context: Context) {
    
    companion object {
        private const val TAG = "RouteCache"
        private const val CACHE_FILE = "route_cache.json"
        private const val MAX_CACHE_SIZE = 100
        private const val CACHE_EXPIRY_TIME = 24 * 60 * 60 * 1000L // 24 ساعت
    }
    
    private val gson = Gson()
    private val cacheFile = File(context.filesDir, CACHE_FILE)
    private val memoryCache = ConcurrentHashMap<String, CachedRoute>()
    
    init {
        loadCache()
    }
    
    /**
     * دریافت مسیر از کش
     */
    fun getRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        routeType: RouteType
    ): NavigationRoute? {
        
        val key = generateCacheKey(origin, destination, routeType)
        val cached = memoryCache[key]
        
        if (cached != null && !isExpired(cached)) {
            Log.d(TAG, "Route found in cache: $key")
            return cached.route
        }
        
        // حذف مسیر منقضی شده
        if (cached != null && isExpired(cached)) {
            memoryCache.remove(key)
        }
        
        return null
    }
    
    /**
     * ذخیره مسیر در کش
     */
    fun saveRoute(route: NavigationRoute) {
        val key = generateCacheKey(route.origin, route.destination, route.routeType)
        
        val cachedRoute = CachedRoute(
            route = route,
            timestamp = System.currentTimeMillis()
        )
        
        memoryCache[key] = cachedRoute
        
        // بررسی اندازه کش
        if (memoryCache.size > MAX_CACHE_SIZE) {
            cleanupOldEntries()
        }
        
        // ذخیره در فایل
        saveCacheToFile()
        
        Log.d(TAG, "Route saved to cache: $key")
    }
    
    /**
     * تولید کلید کش
     */
    private fun generateCacheKey(
        origin: GeoPoint,
        destination: GeoPoint,
        routeType: RouteType
    ): String {
        val originStr = "${origin.latitude},${origin.longitude}"
        val destStr = "${destination.latitude},${destination.longitude}"
        return "${originStr}_${destStr}_${routeType.name}"
    }
    
    /**
     * بررسی انقضای کش
     */
    private fun isExpired(cachedRoute: CachedRoute): Boolean {
        return System.currentTimeMillis() - cachedRoute.timestamp > CACHE_EXPIRY_TIME
    }
    
    /**
     * پاکسازی ورودی‌های قدیمی
     */
    private fun cleanupOldEntries() {
        val sortedEntries = memoryCache.entries.sortedBy { it.value.timestamp }
        val entriesToRemove = sortedEntries.take(memoryCache.size - MAX_CACHE_SIZE + 10)
        
        entriesToRemove.forEach { entry ->
            memoryCache.remove(entry.key)
        }
        
        Log.d(TAG, "Cleaned up ${entriesToRemove.size} old cache entries")
    }
    
    /**
     * بارگذاری کش از فایل
     */
    private fun loadCache() {
        try {
            if (cacheFile.exists()) {
                val json = cacheFile.readText()
                val type = object : com.google.gson.reflect.TypeToken<Map<String, CachedRoute>>() {}.type
                val loadedCache = gson.fromJson<Map<String, CachedRoute>>(json, type)
                
                if (loadedCache != null) {
                    memoryCache.putAll(loadedCache)
                    
                    // حذف ورودی‌های منقضی شده
                    val expiredKeys = memoryCache.filter { isExpired(it.value) }.keys
                    expiredKeys.forEach { memoryCache.remove(it) }
                    
                    Log.d(TAG, "Loaded ${memoryCache.size} routes from cache")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cache", e)
            memoryCache.clear()
        }
    }
    
    /**
     * ذخیره کش در فایل
     */
    private fun saveCacheToFile() {
        try {
            val json = gson.toJson(memoryCache.toMap())
            cacheFile.writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cache to file", e)
        }
    }
    
    /**
     * پاک کردن کش
     */
    fun clearCache() {
        memoryCache.clear()
        cacheFile.delete()
        Log.d(TAG, "Cache cleared")
    }
    
    /**
     * دریافت آمار کش
     */
    fun getCacheStats(): CacheStats {
        val totalEntries = memoryCache.size
        val expiredEntries = memoryCache.count { isExpired(it.value) }
        val validEntries = totalEntries - expiredEntries
        
        return CacheStats(
            totalEntries = totalEntries,
            validEntries = validEntries,
            expiredEntries = expiredEntries,
            cacheSize = cacheFile.length()
        )
    }
}

/**
 * مسیر کش شده
 */
data class CachedRoute(
    val route: NavigationRoute,
    val timestamp: Long
)

/**
 * آمار کش
 */
data class CacheStats(
    val totalEntries: Int,
    val validEntries: Int,
    val expiredEntries: Int,
    val cacheSize: Long
)
