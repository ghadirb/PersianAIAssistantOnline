package com.persianai.assistant.utils

import android.content.Context
import com.persianai.assistant.models.Song
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * ریپازیتوری برای مدیریت موزیک‌ها و دسته‌بندی‌ها
 */
class MusicRepository(private val context: Context) {
    
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("music_prefs", Context.MODE_PRIVATE)
    
    // لیست موزیک‌های پیش‌فرض بر اساس حالت روحی
    private val happySongs = listOf(
        Song(
            id = 1,
            title = "بهار و من",
            artist = "سیاوش قمیشی",
            album = "بهار و من",
            url = "https://dl.naslemusic.com/1399/08/27/Siavash%20Ghomayshi%20-%20Bahar%20O%20Man%20(128).mp3",
            mood = "شاد",
            duration = 240000L,
            albumArt = "https://example.com/album1.jpg"
        ),
        Song(
            id = 2,
            title = "دوچرخه",
            artist = "محسن چاوشی",
            album = "دوچرخه",
            url = "https://dl.naslemusic.com/1401/06/24/Mohsen%20Chavoshi%20-%20Docharkheh%20(128).mp3",
            mood = "شاد",
            duration = 210000L,
            albumArt = "https://example.com/album2.jpg"
        ),
        Song(
            id = 3,
            title = "عاشقتم",
            artist = "بنیامین بهادری",
            album = "عاشقتم",
            url = "https://dl.naslemusic.com/1401/02/17/Benjamin%20Bahadori%20-%20Asheghetam%20(128).mp3",
            mood = "شاد",
            duration = 200000L,
            albumArt = "https://example.com/album3.jpg"
        )
    )
    
    private val sadSongs = listOf(
        Song(
            id = 4,
            title = "سفر",
            artist = "محسن چاوشی",
            album = "سفر",
            url = "https://dl.naslemusic.com/1401/08/24/Mohsen%20Chavoshi%20-%20Safar%20(128).mp3",
            mood = "غمگین",
            duration = 260000L,
            albumArt = "https://example.com/album4.jpg"
        ),
        Song(
            id = 5,
            title = "آبادی",
            artist = "محسن چاوشی",
            album = "آبادی",
            url = "https://dl.naslemusic.com/1401/09/15/Mohsen%20Chavoshi%20-%20Abadi%20(128).mp3",
            mood = "غمگین",
            duration = 280000L,
            albumArt = "https://example.com/album5.jpg"
        )
    )
    
    private val romanticSongs = listOf(
        Song(
            id = 6,
            title = "عاشقانه‌ها",
            artist = "شهاب مظفری",
            album = "عاشقانه‌ها",
            url = "https://dl.naslemusic.com/1401/06/08/Shahab%20Mozaffari%20-%20Asheghaneha%20(128).mp3",
            mood = "عاشقانه",
            duration = 230000L,
            albumArt = "https://example.com/album6.jpg"
        ),
        Song(
            id = 7,
            title = "گل یخ",
            artist = "شهاب مظفری",
            album = "گل یخ",
            url = "https://dl.naslemusic.com/1401/03/23/Shahab%20Mozaffari%20-%20Gol%20Yakh%20(128).mp3",
            mood = "عاشقانه",
            duration = 250000L,
            albumArt = "https://example.com/album7.jpg"
        )
    )
    
    private val traditionalSongs = listOf(
        Song(
            id = 8,
            title = "مرغ سحر",
            artist = "محدرضا شجریان",
            album = "مرغ سحر",
            url = "https://dl.naslemusic.com/1399/08/27/Mohammadreza%20Shajarian%20-%20Morgh%20Sahar%20(128).mp3",
            mood = "سنتی",
            duration = 300000L,
            albumArt = "https://example.com/album8.jpg"
        ),
        Song(
            id = 9,
            title = "ساقی‌نامه",
            artist = "محدرضا شجریان",
            album = "ساقی‌نامه",
            url = "https://dl.naslemusic.com/1399/08/27/Mohammadreza%20Shajarian%20-%20Saghinameh%20(128).mp3",
            mood = "سنتی",
            duration = 320000L,
            albumArt = "https://example.com/album9.jpg"
        )
    )
    
    private val energeticSongs = listOf(
        Song(
            id = 10,
            title = "تکنو",
            artist = "یاس",
            album = "تکنو",
            url = "https://dl.naslemusic.com/1401/06/08/Yas%20-%20Tekno%20(128).mp3",
            mood = "انرژی",
            duration = 190000L,
            albumArt = "https://example.com/album10.jpg"
        ),
        Song(
            id = 11,
            title = "پرنده",
            artist = "یاس",
            album = "پرنده",
            url = "https://dl.naslemusic.com/1401/08/24/Yas%20-%20Parandeh%20(128).mp3",
            mood = "انرژی",
            duration = 200000L,
            albumArt = "https://example.com/album11.jpg"
        )
    )
    
    fun getSongsByMood(mood: String): List<Song> {
        return when (mood) {
            "شاد" -> happySongs
            "غمگین" -> sadSongs
            "عاشقانه" -> romanticSongs
            "سنتی" -> traditionalSongs
            "انرژی", "پرانرژی" -> energeticSongs
            else -> happySongs // حالت پیش‌فرض
        }
    }
    
    fun getAllSongs(): List<Song> {
        return happySongs + sadSongs + romanticSongs + traditionalSongs + energeticSongs
    }
    
    fun getSongById(id: Long): Song? {
        return getAllSongs().find { it.id == id }
    }
    
    fun searchSongs(query: String): List<Song> {
        val allSongs = getAllSongs()
        return allSongs.filter { song ->
            song.title.contains(query, ignoreCase = true) ||
            song.artist.contains(query, ignoreCase = true) ||
            song.album.contains(query, ignoreCase = true)
        }
    }
    
    fun getFavoriteSongs(): List<Song> {
        val favoriteIds = getFavoriteSongIds()
        return getAllSongs().filter { it.id in favoriteIds }
    }
    
    fun addToFavorites(songId: Long) {
        val favoriteIds = getFavoriteSongIds().toMutableSet()
        favoriteIds.add(songId)
        saveFavoriteSongIds(favoriteIds)
    }
    
    fun removeFromFavorites(songId: Long) {
        val favoriteIds = getFavoriteSongIds().toMutableSet()
        favoriteIds.remove(songId)
        saveFavoriteSongIds(favoriteIds)
    }
    
    fun isFavorite(songId: Long): Boolean {
        return songId in getFavoriteSongIds()
    }
    
    fun getRecentlyPlayed(): List<Song> {
        val recentlyPlayedIds = getRecentlyPlayedIds()
        return getAllSongs().filter { it.id in recentlyPlayedIds }
            .sortedBy { song -> recentlyPlayedIds.indexOf(song.id) }
    }
    
    fun addToRecentlyPlayed(songId: Long) {
        val recentlyPlayedIds = getRecentlyPlayedIds().toMutableList()
        recentlyPlayedIds.remove(songId) // حذف از موقعیت قبلی
        recentlyPlayedIds.add(0, songId) // اضافه به ابتدا
        
        // نگه داشتن فقط ۲۰ آخری
        if (recentlyPlayedIds.size > 20) {
            recentlyPlayedIds.removeAt(recentlyPlayedIds.size - 1)
        }
        
        saveRecentlyPlayedIds(recentlyPlayedIds)
    }
    
    fun createPlaylist(name: String, songIds: List<Long>): Boolean {
        try {
            val playlists = getPlaylists().toMutableList()
            val newPlaylist = MusicPlaylist(
                id = System.currentTimeMillis(),
                name = name,
                songIds = songIds,
                createdDate = System.currentTimeMillis()
            )
            playlists.add(newPlaylist)
            savePlaylists(playlists)
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    fun getPlaylists(): List<MusicPlaylist> {
        val json = prefs.getString("playlists", null)
        return if (json != null) {
            val type = object : TypeToken<List<MusicPlaylist>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    fun getPlaylistSongs(playlistId: Long): List<Song> {
        val playlist = getPlaylists().find { it.id == playlistId }
        return playlist?.songIds?.mapNotNull { getSongById(it) } ?: emptyList()
    }
    
    // توابع کمکی
    private fun getFavoriteSongIds(): Set<Long> {
        val json = prefs.getString("favorite_songs", null)
        return if (json != null) {
            val type = object : TypeToken<Set<Long>>() {}.type
            gson.fromJson(json, type) ?: emptySet()
        } else {
            emptySet()
        }
    }
    
    private fun saveFavoriteSongIds(ids: Set<Long>) {
        val json = gson.toJson(ids)
        prefs.edit().putString("favorite_songs", json).apply()
    }
    
    private fun getRecentlyPlayedIds(): List<Long> {
        val json = prefs.getString("recently_played", null)
        return if (json != null) {
            val type = object : TypeToken<List<Long>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    private fun saveRecentlyPlayedIds(ids: List<Long>) {
        val json = gson.toJson(ids)
        prefs.edit().putString("recently_played", json).apply()
    }
    
    private fun savePlaylists(playlists: List<MusicPlaylist>) {
        val json = gson.toJson(playlists)
        prefs.edit().putString("playlists", json).apply()
    }
}

/**
 * مدل پلی‌لیست موزیک
 */
data class MusicPlaylist(
    val id: Long,
    val name: String,
    val songIds: List<Long>,
    val createdDate: Long
)
