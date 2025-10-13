package com.persianai.assistant.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicPlaylistManager(private val context: Context) {
    
    data class MusicTrack(
        val id: Long,
        val title: String,
        val artist: String,
        val path: String,
        val duration: Long,
        val album: String = ""
    )
    
    data class Playlist(
        val name: String,
        val tracks: List<MusicTrack>,
        val mood: String = "",
        val createdAt: Long = System.currentTimeMillis()
    )
    
    suspend fun scanDeviceMusic(): List<MusicTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<MusicTrack>()
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION
            )
            
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                MediaStore.Audio.Media.IS_MUSIC + " != 0",
                null,
                MediaStore.Audio.Media.TITLE + " ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                
                while (cursor.moveToNext()) {
                    tracks.add(MusicTrack(
                        cursor.getLong(idCol),
                        cursor.getString(titleCol) ?: "Unknown",
                        cursor.getString(artistCol) ?: "Unknown",
                        cursor.getString(pathCol) ?: "",
                        cursor.getLong(durCol)
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext tracks
    }
    
    fun createPlaylistByMood(mood: String, tracks: List<MusicTrack>): Playlist {
        val filteredTracks = when (mood) {
            "happy", "شاد" -> tracks.take(20)
            "sad", "غمگین" -> tracks.takeLast(20)
            "energetic", "انرژی" -> tracks.shuffled().take(25)
            "calm", "آرام" -> tracks.filter { it.duration > 180000 }.take(15)
            "عاشقانه" -> tracks.shuffled().take(20)
            "سنتی" -> tracks.shuffled().take(15)
            else -> tracks.shuffled().take(30)
        }
        return Playlist(mood, filteredTracks, mood)
    }
    
    data class MusicPlayer(
        val appName: String,
        val packageName: String
    )
    
    fun getInstalledMusicPlayers(): List<MusicPlayer> {
        val players = mutableListOf<MusicPlayer>()
        val intent = Intent(Intent.ACTION_VIEW)
        intent.type = "audio/*"
        
        val pm = context.packageManager
        val activities = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        
        for (info in activities) {
            players.add(MusicPlayer(
                info.loadLabel(pm).toString(),
                info.activityInfo.packageName
            ))
        }
        
        return players
    }
}
