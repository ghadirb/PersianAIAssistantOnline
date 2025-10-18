package com.persianai.assistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.persianai.assistant.R
import com.persianai.assistant.activities.MusicActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.File
import java.net.URL

/**
 * سرویس پخش موزیک واقعی با قابلیت‌های کامل
 */
class MusicPlayerService : Service() {
    
    companion object {
        private const val TAG = "MusicPlayerService"
        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREVIOUS = "action_previous"
        const val ACTION_SEEK = "action_seek"
        const val ACTION_STOP = "action_stop"
        
        private const val NOTIFICATION_CHANNEL_ID = "music_player_channel"
        private const val NOTIFICATION_ID = 1001
    }
    
    private val binder = MusicBinder()
    private var mediaPlayer: MediaPlayer? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    // Media session for notification controls
    private var mediaSession: MediaSessionCompat? = null
    private var notificationManager: NotificationManager? = null
    private var isForegroundService = false
    
    // لیست پخش
    private val _playlist = MutableStateFlow<List<Song>>(emptyList())
    val playlist: StateFlow<List<Song>> = _playlist
    
    private val _currentSongIndex = MutableStateFlow(-1)
    val currentSongIndex: StateFlow<Int> = _currentSongIndex
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition
    
    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration
    
    // مدل‌های داده
    data class Song(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val duration: Long,
        val path: String,
        val albumArtUri: Uri?
    )
    
    enum class RepeatMode {
        OFF, ONE, ALL
    }
    
    private var repeatMode = RepeatMode.OFF
    private var shuffleMode = false
    private var shuffledIndices = listOf<Int>()
    private var positionUpdateJob: Job? = null
    
    inner class MusicBinder : Binder() {
        fun getService(): MusicPlayerService = this@MusicPlayerService
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> resume()
            ACTION_PAUSE -> pause()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_STOP -> stop()
        }
        return START_STICKY
    }
    
    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "MusicPlayerService").apply {
            setCallback(MediaSessionCallback())
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music player controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            resume()
        }
        
        override fun onPause() {
            pause()
        }
        
        override fun onSkipToNext() {
            playNext()
        }
        
        override fun onSkipToPrevious() {
            playPrevious()
        }
        
        override fun onStop() {
            stop()
        }
        
        override fun onSeekTo(pos: Long) {
            seekTo(pos.toInt())
        }
    }
    
    private fun updateNotification() {
        val currentSong = getCurrentSong()
        if (currentSong == null) {
            stopForeground(true)
            isForegroundService = false
            return
        }
        
        val notification = createNotification(currentSong)
        
        if (_isPlaying.value && !isForegroundService) {
            startForeground(NOTIFICATION_ID, notification)
            isForegroundService = true
        } else if (_isPlaying.value) {
            notificationManager?.notify(NOTIFICATION_ID, notification)
        } else {
            stopForeground(false)
            notificationManager?.notify(NOTIFICATION_ID, notification)
        }
    }
    
    private fun createNotification(song: Song): Notification {
        val intent = Intent(this, MusicActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val playPauseAction = if (_isPlaying.value) {
            NotificationCompat.Action(
                R.drawable.ic_pause,
                "Pause",
                buildPendingIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_play,
                "Play",
                buildPendingIntent(ACTION_PLAY)
            )
        }
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(pendingIntent)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_previous,
                    "Previous",
                    buildPendingIntent(ACTION_PREVIOUS)
                )
            )
            .addAction(playPauseAction)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_next,
                    "Next",
                    buildPendingIntent(ACTION_NEXT)
                )
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(_isPlaying.value)
            .build()
    }
    
    private fun buildPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun updateMediaMetadata(song: Song) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, song.path)
            .build()
        
        mediaSession?.setMetadata(metadata)
    }
    
    private fun updatePlaybackState() {
        val state = if (_isPlaying.value) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }
        
        val playbackState = PlaybackStateCompat.Builder()
            .setState(state, _currentPosition.value.toLong(), 1f)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .build()
        
        mediaSession?.setPlaybackState(playbackState)
    }
    
    override fun onCreate() {
        super.onCreate()
        initMediaPlayer()
        initMediaSession()
        createNotificationChannel()
        Log.d(TAG, "Music service created")
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer().apply {
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnCompletionListener {
                Log.d(TAG, "Song completed")
                onSongCompleted()
            }
            setOnPreparedListener {
                Log.d(TAG, "Song prepared, duration: ${it.duration}ms")
                _duration.value = it.duration
                startPlaying()
            }
            setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                handleError(what, extra)
                true
            }
        }
    }
    
    // مدیریت لیست پخش
    fun setPlaylist(songs: List<Song>) {
        _playlist.value = songs
        if (shuffleMode) {
            generateShuffledIndices()
        }
        Log.d(TAG, "Playlist set with ${songs.size} songs")
    }
    
    fun addToPlaylist(song: Song) {
        val currentList = _playlist.value.toMutableList()
        currentList.add(song)
        _playlist.value = currentList
        if (shuffleMode) {
            generateShuffledIndices()
        }
        Log.d(TAG, "Song added to playlist: ${song.title}")
    }
    
    fun removeFromPlaylist(index: Int) {
        if (index in _playlist.value.indices) {
            val currentList = _playlist.value.toMutableList()
            val removedSong = currentList.removeAt(index)
            _playlist.value = currentList
            
            // اگر آهنگ جاری حذف شد
            if (index == _currentSongIndex.value) {
                stop()
            } else if (index < _currentSongIndex.value) {
                _currentSongIndex.value--
            }
            
            if (shuffleMode) {
                generateShuffledIndices()
            }
            
            Log.d(TAG, "Song removed from playlist: ${removedSong.title}")
        }
    }
    
    // کنترل پخش
    fun playSong(index: Int) {
        if (index !in _playlist.value.indices) {
            Log.e(TAG, "Invalid song index: $index")
            return
        }
        
        _currentSongIndex.value = index
        val song = _playlist.value[index]
        
        Log.d(TAG, "Playing song: ${song.title} at index $index")
        
        try {
            mediaPlayer?.apply {
                reset()
                setDataSource(applicationContext, Uri.parse(song.path))
                prepareAsync()
            }
            updateMediaMetadata(song)
        } catch (e: IOException) {
            Log.e(TAG, "Error setting data source", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException", e)
        }
    }
    
    private fun startPlaying() {
        try {
            mediaPlayer?.start()
            _isPlaying.value = true
            startPositionUpdater()
            updateNotification()
            updatePlaybackState()
            Log.d(TAG, "Playback started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting playback", e)
        }
    }
    
    fun pause() {
        try {
            mediaPlayer?.pause()
            _isPlaying.value = false
            positionUpdateJob?.cancel()
            updateNotification()
            updatePlaybackState()
            Log.d(TAG, "Playback paused")
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing", e)
        }
    }
    
    fun resume() {
        try {
            mediaPlayer?.start()
            _isPlaying.value = true
            startPositionUpdater()
            updateNotification()
            updatePlaybackState()
            Log.d(TAG, "Playback resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming", e)
        }
    }
    
    fun stop() {
        try {
            mediaPlayer?.stop()
            _isPlaying.value = false
            _currentPosition.value = 0
            positionUpdateJob?.cancel()
            updateNotification()
            updatePlaybackState()
            Log.d(TAG, "Playback stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping", e)
        }
    }
    
    fun playNext() {
        val nextIndex = getNextSongIndex()
        if (nextIndex != -1) {
            playSong(nextIndex)
            Log.d(TAG, "Playing next song at index: $nextIndex")
        } else {
            Log.d(TAG, "No next song available")
        }
    }
    
    fun playPrevious() {
        // اگر بیشتر از 3 ثانیه پخش شده، از اول همین آهنگ
        if (_currentPosition.value > 3000) {
            seekTo(0)
            Log.d(TAG, "Restarting current song")
            return
        }
        
        val previousIndex = getPreviousSongIndex()
        if (previousIndex != -1) {
            playSong(previousIndex)
            Log.d(TAG, "Playing previous song at index: $previousIndex")
        } else {
            Log.d(TAG, "No previous song available")
        }
    }
    
    fun seekTo(position: Int) {
        try {
            mediaPlayer?.seekTo(position)
            _currentPosition.value = position
            Log.d(TAG, "Seeked to position: $position")
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking", e)
        }
    }
    
    // حالت تکرار و shuffle
    fun setRepeatMode(mode: RepeatMode) {
        repeatMode = mode
        Log.d(TAG, "Repeat mode set to: $mode")
    }
    
    fun getRepeatMode(): RepeatMode = repeatMode
    
    fun setShuffleMode(enabled: Boolean) {
        shuffleMode = enabled
        if (enabled) {
            generateShuffledIndices()
        }
        Log.d(TAG, "Shuffle mode: $enabled")
    }
    
    fun isShuffleEnabled(): Boolean = shuffleMode
    
    private fun generateShuffledIndices() {
        val indices = _playlist.value.indices.toMutableList()
        indices.shuffle()
        shuffledIndices = indices
        Log.d(TAG, "Shuffled indices generated")
    }
    
    private fun getNextSongIndex(): Int {
        val currentIndex = _currentSongIndex.value
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
    
    private fun getPreviousSongIndex(): Int {
        val currentIndex = _currentSongIndex.value
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
    
    private fun onSongCompleted() {
        val nextIndex = getNextSongIndex()
        if (nextIndex != -1) {
            playSong(nextIndex)
        } else {
            stop()
            Log.d(TAG, "Playlist ended")
        }
    }
    
    private fun startPositionUpdater() {
        positionUpdateJob?.cancel()
        positionUpdateJob = serviceScope.launch {
            while (_isPlaying.value) {
                try {
                    mediaPlayer?.let {
                        if (it.isPlaying) {
                            _currentPosition.value = it.currentPosition
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating position", e)
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }
    
    private fun handleError(what: Int, extra: Int): Boolean {
        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
        stop()
        return true
    }
    
    fun getCurrentSong(): Song? {
        val index = _currentSongIndex.value
        return if (index in _playlist.value.indices) {
            _playlist.value[index]
        } else null
    }
    
    // دریافت لیست آهنگ‌ها از دستگاه
    fun loadSongsFromDevice(context: Context): List<Song> {
        val songs = mutableListOf<Song>()
        
        try {
            val projection = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.TITLE,
                android.provider.MediaStore.Audio.Media.ARTIST,
                android.provider.MediaStore.Audio.Media.ALBUM,
                android.provider.MediaStore.Audio.Media.DURATION,
                android.provider.MediaStore.Audio.Media.DATA,
                android.provider.MediaStore.Audio.Media.ALBUM_ID
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
                val albumIdColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM_ID)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn)
                    val artist = cursor.getString(artistColumn)
                    val album = cursor.getString(albumColumn)
                    val duration = cursor.getLong(durationColumn)
                    val path = cursor.getString(dataColumn)
                    val albumId = cursor.getLong(albumIdColumn)
                    
                    val albumArtUri = Uri.parse("content://media/external/audio/albumart/$albumId")
                    
                    songs.add(
                        Song(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            path = path,
                            albumArtUri = albumArtUri
                        )
                    )
                }
            }
            
            Log.d(TAG, "Loaded ${songs.size} songs from device")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading songs", e)
        }
        
        return songs
    }
    
    override fun onDestroy() {
        super.onDestroy()
        positionUpdateJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession?.release()
        mediaSession = null
        Log.d(TAG, "Music service destroyed")
    }
}
