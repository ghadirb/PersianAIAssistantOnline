package com.persianai.assistant.music

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * مدیریت پلی‌لیست‌های موسیقی و ارتباط با Music Player
 */
class MusicPlaylistManager(private val context: Context) {
    
    companion object {
        private const val TAG = "MusicPlaylistManager"
        
        // Mood Keywords for Persian songs
        val HAPPY_KEYWORDS = listOf("شاد", "خوشحال", "رقص", "جشن", "عروسی", "happy", "dance", "party", "celebration")
        val SAD_KEYWORDS = listOf("غمگین", "غم", "اندوه", "جدایی", "تنهایی", "sad", "lonely", "separation")
        val ROMANTIC_KEYWORDS = listOf("عاشقانه", "احساسی", "عشق", "دوست", "love", "romantic", "emotional")
        val TRADITIONAL_KEYWORDS = listOf("سنتی", "اصیل", "قدیمی", "کلاسیک", "traditional", "classic", "folk")
        val ENERGETIC_KEYWORDS = listOf("انرژی", "ورزش", "راک", "رپ", "energy", "workout", "rock", "rap", "hip hop")
    }
    
    data class MusicTrack(
        val id: Long,
        val title: String,
        val artist: String?,
        val album: String?,
        val path: String,
        val duration: Long,
        val size: Long
    )
    
    data class Playlist(
        val name: String,
        val mood: String,
        val tracks: List<MusicTrack>,
        val createdAt: Long = System.currentTimeMillis()
    )
    
    /**
     * اسکن کردن موسیقی‌های دستگاه
     */
    suspend fun scanDeviceMusic(): List<MusicTrack> = withContext(Dispatchers.IO) {
        val musicList = mutableListOf<MusicTrack>()
        
        try {
            val contentResolver: ContentResolver = context.contentResolver
            val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE
            )
            
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            
            val cursor: Cursor? = contentResolver.query(
                uri,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )
            
            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val pathColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                
                while (it.moveToNext()) {
                    val path = it.getString(pathColumn)
                    // فقط فایل‌های موجود را اضافه کن
                    if (File(path).exists()) {
                        musicList.add(
                            MusicTrack(
                                id = it.getLong(idColumn),
                                title = it.getString(titleColumn) ?: "Unknown",
                                artist = it.getString(artistColumn),
                                album = it.getString(albumColumn),
                                path = path,
                                duration = it.getLong(durationColumn),
                                size = it.getLong(sizeColumn)
                            )
                        )
                    }
                }
            }
            
            Log.d(TAG, "Found ${musicList.size} music tracks on device")
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning music", e)
        }
        
        return@withContext musicList
    }
    
    /**
     * ایجاد پلی‌لیست بر اساس mood
     */
    suspend fun createMoodPlaylist(mood: String): Playlist = withContext(Dispatchers.IO) {
        val allTracks = scanDeviceMusic()
        val filteredTracks = filterTracksByMood(allTracks, mood)
        
        Playlist(
            name = "پلی‌لیست $mood",
            mood = mood,
            tracks = filteredTracks
        )
    }
    
    /**
     * فیلتر کردن آهنگ‌ها بر اساس mood
     */
    private fun filterTracksByMood(tracks: List<MusicTrack>, mood: String): List<MusicTrack> {
        val keywords = when (mood.lowercase()) {
            "شاد", "happy" -> HAPPY_KEYWORDS
            "غمگین", "sad" -> SAD_KEYWORDS
            "عاشقانه", "احساسی", "romantic", "emotional" -> ROMANTIC_KEYWORDS
            "سنتی", "traditional" -> TRADITIONAL_KEYWORDS
            "انرژی", "energetic", "ورزشی" -> ENERGETIC_KEYWORDS
            else -> return tracks.shuffled().take(30) // اگر mood شناخته نشد، 30 آهنگ رندوم
        }
        
        // فیلتر بر اساس عنوان، آرتیست و آلبوم
        val filteredTracks = tracks.filter { track ->
            val searchText = "${track.title} ${track.artist ?: ""} ${track.album ?: ""}".lowercase()
            keywords.any { keyword -> searchText.contains(keyword.lowercase()) }
        }
        
        // اگر تعداد کم بود، آهنگ‌های رندوم اضافه کن
        return if (filteredTracks.size < 10) {
            val randomTracks = tracks.shuffled().take(20 - filteredTracks.size)
            (filteredTracks + randomTracks).distinctBy { it.id }
        } else {
            filteredTracks.take(50) // حداکثر 50 آهنگ
        }
    }
    
    /**
     * باز کردن پلی‌لیست در Music Player
     */
    fun openPlaylistInMusicPlayer(playlist: Playlist, playerPackage: String? = null) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            
            // اگر package مشخص شده، از آن استفاده کن
            if (!playerPackage.isNullOrEmpty()) {
                intent.setPackage(playerPackage)
            }
            
            // M3U playlist file ایجاد کن
            val playlistFile = createM3UPlaylistFile(playlist)
            
            // Intent برای باز کردن playlist
            intent.setDataAndType(Uri.fromFile(playlistFile), "audio/x-mpegurl")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            context.startActivity(intent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error opening playlist in music player", e)
            // اگر player مشخص شده کار نکرد، با chooser امتحان کن
            openWithChooser(playlist)
        }
    }
    
    /**
     * باز کردن با انتخاب کاربر
     */
    private fun openWithChooser(playlist: Playlist) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val playlistFile = createM3UPlaylistFile(playlist)
            
            intent.setDataAndType(Uri.fromFile(playlistFile), "audio/x-mpegurl")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            val chooser = Intent.createChooser(intent, "انتخاب Music Player")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening chooser", e)
            // در صورت خطا، اولین آهنگ را پخش کن
            playFirstTrack(playlist)
        }
    }
    
    /**
     * ایجاد فایل M3U playlist
     */
    private fun createM3UPlaylistFile(playlist: Playlist): File {
        val playlistDir = File(context.getExternalFilesDir(null), "playlists")
        if (!playlistDir.exists()) {
            playlistDir.mkdirs()
        }
        
        val fileName = "${playlist.mood}_${System.currentTimeMillis()}.m3u"
        val playlistFile = File(playlistDir, fileName)
        
        val content = buildString {
            appendLine("#EXTM3U")
            playlist.tracks.forEach { track ->
                val seconds = track.duration / 1000
                appendLine("#EXTINF:$seconds,${track.artist ?: "Unknown"} - ${track.title}")
                appendLine(track.path)
            }
        }
        
        playlistFile.writeText(content)
        return playlistFile
    }
    
    /**
     * پخش اولین آهنگ
     */
    private fun playFirstTrack(playlist: Playlist) {
        if (playlist.tracks.isNotEmpty()) {
            val firstTrack = playlist.tracks.first()
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.parse("file://${firstTrack.path}"), "audio/*")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error playing first track", e)
            }
        }
    }
    
    /**
     * لیست Music Player های نصب شده
     */
    fun getInstalledMusicPlayers(): List<MusicPlayerInfo> {
        val players = mutableListOf<MusicPlayerInfo>()
        
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(Uri.parse("file:///dummy.mp3"), "audio/*")
        
        val packageManager = context.packageManager
        val activities = packageManager.queryIntentActivities(intent, 0)
        
        activities.forEach { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val appName = resolveInfo.loadLabel(packageManager).toString()
            
            players.add(
                MusicPlayerInfo(
                    packageName = packageName,
                    appName = appName,
                    icon = resolveInfo.loadIcon(packageManager)
                )
            )
        }
        
        return players
    }
    
    data class MusicPlayerInfo(
        val packageName: String,
        val appName: String,
        val icon: android.graphics.drawable.Drawable
    )
}
