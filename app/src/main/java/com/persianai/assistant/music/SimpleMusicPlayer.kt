package com.persianai.assistant.music

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * موزیک پلیر ساده و کاربردی با قابلیت پخش واقعی موسیقی
 */
class SimpleMusicPlayer(private val context: Context) {
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaPlayer: MediaPlayer? = null
    private var positionUpdateJob: Job? = null
    
    // State flows
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    private val _currentTrack = MutableStateFlow<MusicTrack?>(null)
    val currentTrack: StateFlow<MusicTrack?> = _currentTrack
    
    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition
    
    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration
    
    private val _playlist = MutableStateFlow<List<MusicTrack>>(emptyList())
    val playlist: StateFlow<List<MusicTrack>> = _playlist
    
    private val _currentTrackIndex = MutableStateFlow(-1)
    val currentTrackIndex: StateFlow<Int> = _currentTrackIndex
    
    private var repeatMode = RepeatMode.OFF
    private var shuffleMode = false
    private var shuffledIndices = mutableListOf<Int>()
    
    companion object {
        private const val TAG = "SimpleMusicPlayer"
    }
    
    data class MusicTrack(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        val duration: Long,
        val path: String,
        val albumArt: String = "",
        val genre: String = "",
        val year: Int = 0
    )
    
    enum class RepeatMode {
        OFF, ONE, ALL
    }
    
    init {
        initializeMediaPlayer()
    }
    
    private fun initializeMediaPlayer() {
        try {
            mediaPlayer = MediaPlayer().apply {
                setOnCompletionListener {
                    Log.d(TAG, "Track completed")
                    onTrackCompleted()
                }
                setOnPreparedListener {
                    Log.d(TAG, "Track prepared, duration: ${it.duration}")
                    _duration.value = it.duration
                    startPlayback()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaPlayer", e)
        }
    }
    
    /**
     * بارگذاری آهنگ‌ها از دستگاه
     */
    fun loadTracksFromDevice(): List<MusicTrack> {
        val tracks = mutableListOf<MusicTrack>()
        
        try {
            // Only use columns that are guaranteed to exist on all devices
            // Some OEMs / Android versions don't expose GENRE/YEAR directly on MediaStore.Audio.Media
            val projection = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.TITLE,
                android.provider.MediaStore.Audio.Media.ARTIST,
                android.provider.MediaStore.Audio.Media.ALBUM,
                android.provider.MediaStore.Audio.Media.DURATION,
                android.provider.MediaStore.Audio.Media.DATA
            )
            
            val selection = "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${android.provider.MediaStore.Audio.Media.TITLE} ASC"
            
            context.contentResolver.query(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn).toString()
                    val title = cursor.getString(titleColumn) ?: "Unknown Title"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val album = cursor.getString(albumColumn) ?: "Unknown Album"
                    val duration = cursor.getLong(durationColumn)
                    val path = cursor.getString(dataColumn)
                    // Fallbacks for fields that may not be available on all devices
                    val genre = ""
                    val year = 0
                    
                    // فقط فایل‌های موجود را اضافه کن
                    if (File(path).exists()) {
                        tracks.add(
                            MusicTrack(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                duration = duration,
                                path = path,
                                genre = genre,
                                year = year
                            )
                        )
                    }
                }
            }
            
            _playlist.value = tracks
            Log.i(TAG, "✅ ${tracks.size} آهنگ از دستگاه بارگذاری شد")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطا در بارگذاری آهنگ‌ها از دستگاه", e)
        }
        
        return tracks
    }
    
    /**
     * تنظیم لیست پخش
     */
    fun setPlaylist(tracks: List<MusicTrack>) {
        _playlist.value = tracks
        if (shuffleMode) {
            generateShuffledIndices()
        }
        Log.d(TAG, "Playlist set with ${tracks.size} tracks")
    }
    
    /**
     * پخش آهنگ با ایندکس مشخص
     */
    fun playTrack(index: Int) {
        val tracks = _playlist.value
        if (index !in tracks.indices) {
            Log.e(TAG, "Invalid track index: $index")
            return
        }
        
        _currentTrackIndex.value = index
        val track = tracks[index]
        _currentTrack.value = track
        
        Log.d(TAG, "Playing track: ${track.title}")
        
        try {
            mediaPlayer?.apply {
                reset()
                setDataSource(context, Uri.parse(track.path))
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting data source", e)
        }
    }
    
    /**
     * شروع پخش
     */
    private fun startPlayback() {
        try {
            mediaPlayer?.start()
            _isPlaying.value = true
            startPositionUpdater()
            Log.d(TAG, "Playback started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting playback", e)
        }
    }
    
    /**
     * پخش/توقف
     */
    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            resume()
        }
    }
    
    /**
     * توقف موقت
     */
    fun pause() {
        try {
            mediaPlayer?.pause()
            _isPlaying.value = false
            positionUpdateJob?.cancel()
            Log.d(TAG, "Playback paused")
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing", e)
        }
    }
    
    /**
     * ادامه پخش
     */
    fun resume() {
        try {
            mediaPlayer?.start()
            _isPlaying.value = true
            startPositionUpdater()
            Log.d(TAG, "Playback resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming", e)
        }
    }
    
    /**
     * توقف کامل
     */
    fun stop() {
        try {
            mediaPlayer?.stop()
            _isPlaying.value = false
            _currentPosition.value = 0
            positionUpdateJob?.cancel()
            Log.d(TAG, "Playback stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping", e)
        }
    }
    
    /**
     * پخش آهنگ بعدی
     */
    fun playNext() {
        val nextIndex = getNextTrackIndex()
        if (nextIndex != -1) {
            playTrack(nextIndex)
        } else {
            Log.d(TAG, "No next track available")
        }
    }
    
    /**
     * پخش آهنگ قبلی
     */
    fun playPrevious() {
        // اگر بیشتر از 3 ثانیه پخش شده، از اول همین آهنگ
        if (_currentPosition.value > 3000) {
            seekTo(0)
            return
        }
        
        val previousIndex = getPreviousTrackIndex()
        if (previousIndex != -1) {
            playTrack(previousIndex)
        } else {
            Log.d(TAG, "No previous track available")
        }
    }
    
    /**
     * جستجو در آهنگ
     */
    fun seekTo(position: Int) {
        try {
            mediaPlayer?.seekTo(position)
            _currentPosition.value = position
            Log.d(TAG, "Seeked to position: $position")
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking", e)
        }
    }
    
    /**
     * تنظیم حالت تکرار
     */
    fun setRepeatMode(mode: RepeatMode) {
        repeatMode = mode
        Log.d(TAG, "Repeat mode set to: $mode")
    }
    
    /**
     * دریافت حالت تکرار فعلی
     */
    fun getRepeatMode(): RepeatMode {
        return repeatMode
    }
    
    /**
     * بررسی وضعیت پخش تصادفی
     */
    fun isShuffleEnabled(): Boolean {
        return shuffleMode
    }
    
    /**
     * تنظیم حالت shuffle
     */
    fun setShuffleMode(enabled: Boolean) {
        shuffleMode = enabled
        if (enabled) {
            generateShuffledIndices()
        }
        Log.d(TAG, "Shuffle mode: $enabled")
    }
    
    /**
     * دریافت ایندکس آهنگ بعدی
     */
    private fun getNextTrackIndex(): Int {
        val currentIndex = _currentTrackIndex.value
        val playlistSize = _playlist.value.size
        
        if (playlistSize == 0) return -1
        
        return when {
            repeatMode == RepeatMode.ONE -> currentIndex
            shuffleMode -> {
                val currentShuffledPosition = shuffledIndices.indexOf(currentIndex)
                val nextShuffledPosition = (currentShuffledPosition + 1) % shuffledIndices.size
                shuffledIndices[nextShuffledPosition]
            }
            repeatMode == RepeatMode.ALL -> (currentIndex + 1) % playlistSize
            else -> {
                val nextIndex = currentIndex + 1
                if (nextIndex < playlistSize) nextIndex else -1
            }
        }
    }
    
    /**
     * دریافت ایندکس آهنگ قبلی
     */
    private fun getPreviousTrackIndex(): Int {
        val currentIndex = _currentTrackIndex.value
        val playlistSize = _playlist.value.size
        
        if (playlistSize == 0) return -1
        
        return when {
            shuffleMode -> {
                val currentShuffledPosition = shuffledIndices.indexOf(currentIndex)
                val prevShuffledPosition = if (currentShuffledPosition > 0) {
                    currentShuffledPosition - 1
                } else {
                    shuffledIndices.size - 1
                }
                shuffledIndices[prevShuffledPosition]
            }
            repeatMode == RepeatMode.ALL -> {
                if (currentIndex > 0) currentIndex - 1 else playlistSize - 1
            }
            else -> {
                if (currentIndex > 0) currentIndex - 1 else -1
            }
        }
    }
    
    /**
     * تولید ایندکس‌های shuffle
     */
    private fun generateShuffledIndices() {
        val indices = _playlist.value.indices.toMutableList()
        indices.shuffle()
        shuffledIndices = indices
        Log.d(TAG, "Shuffled indices generated")
    }
    
    /**
     * وقتی آهنگ تمام شد
     */
    private fun onTrackCompleted() {
        val nextIndex = getNextTrackIndex()
        if (nextIndex != -1) {
            playTrack(nextIndex)
        } else {
            stop()
            Log.d(TAG, "Playlist ended")
        }
    }
    
    /**
     * شروع آپدیت موقعیت
     */
    private fun startPositionUpdater() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (_isPlaying.value) {
                try {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) {
                            _currentPosition.value = player.currentPosition
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating position", e)
                }
                delay(500)
            }
        }
    }
    
    /**
     * جستجوی آهنگ‌ها بر اساس متن
     */
    fun searchTracks(query: String): List<MusicTrack> {
        val tracks = _playlist.value
        return tracks.filter { track ->
            track.title.contains(query, ignoreCase = true) ||
            track.artist.contains(query, ignoreCase = true) ||
            track.album.contains(query, ignoreCase = true) ||
            track.genre.contains(query, ignoreCase = true)
        }
    }
    
    /**
     * دریافت آهنگ‌ها بر اساس ژانر
     */
    fun getTracksByGenre(genre: String): List<MusicTrack> {
        val tracks = _playlist.value
        return tracks.filter { it.genre.equals(genre, ignoreCase = true) }
    }
    
    /**
     * دریافت آهنگ‌ها بر اساس هنرمند
     */
    fun getTracksByArtist(artist: String): List<MusicTrack> {
        val tracks = _playlist.value
        return tracks.filter { it.artist.equals(artist, ignoreCase = true) }
    }
    
    /**
     * ایجاد پلی‌لیست هوشمند بر اساس حالت (مود)
     */
    fun createMoodPlaylist(mood: MusicMood): List<MusicTrack> {
        val tracks = _playlist.value
        val keywords = mood.keywords
        
        return tracks.filter { track ->
            keywords.any { keyword ->
                track.title.contains(keyword, ignoreCase = true) ||
                track.artist.contains(keyword, ignoreCase = true) ||
                track.genre.contains(keyword, ignoreCase = true)
            }
        }.shuffled()
    }
    
    /**
     * دریافت آمار موسیقی
     */
    fun getMusicStats(): MusicStats {
        val tracks = _playlist.value
        val genres = tracks.groupBy { it.genre }
        val artists = tracks.groupBy { it.artist }
        
        return MusicStats(
            totalTracks = tracks.size,
            totalDuration = tracks.sumOf { it.duration },
            totalGenres = genres.size,
            totalArtists = artists.size,
            topGenres = genres.entries.sortedByDescending { it.value.size }.take(5).map { it.key },
            topArtists = artists.entries.sortedByDescending { it.value.size }.take(5).map { it.key }
        )
    }
    
    data class MusicStats(
        val totalTracks: Int,
        val totalDuration: Long,
        val totalGenres: Int,
        val totalArtists: Int,
        val topGenres: List<String>,
        val topArtists: List<String>
    )
    
    /**
     * فرمت زمان
     */
    fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = ms / (1000 * 60 * 60)
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
    
    /**
     * پاک‌سازی منابع
     */
    fun cleanup() {
        scope.cancel()
        positionUpdateJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        Log.i(TAG, "🧹 منابع SimpleMusicPlayer پاک‌سازی شد")
    }
}

/**
 * حالت‌های موسیقی برای ایجاد پلی‌لیست هوشمند
 */
enum class MusicMood(val displayName: String, val keywords: List<String>) {
    HAPPY("شاد", listOf("happy", "joy", "شاد", "خوشحال", "dance", "party", "جشن", "رقص")),
    SAD("غمگین", listOf("sad", "blue", "غمگین", "غمناک", "slow", "آرام")),
    RELAXED("آرامش‌بخش", listOf("relax", "calm", "آرام", "peaceful", "soft", "نرم")),
    ENERGETIC("انرژی‌بخش", listOf("energy", "power", "انرژی", "rock", "metal", "strong")),
    ROMANTIC("عاشقانه", listOf("love", "romantic", "عشق", "عاشقانه", "romance", "قلب")),
    FOCUS("تمرکز", listOf("focus", "study", "تمرکز", "درسی", "instrumental", "ساز")),
    WORKOUT("ورزشی", listOf("workout", "gym", "sport", "ورزش", "fitness", "قدرتی")),
    TRADITIONAL("سنتی", listOf("سنتی", "ایرانی", "فارسی", "traditional", "folk", "محلی"))
}
