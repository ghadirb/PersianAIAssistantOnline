package com.persianai.assistant.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.persianai.assistant.R
import com.persianai.assistant.activities.MusicActivity
import com.persianai.assistant.models.Song
import com.persianai.assistant.utils.MusicRepository

/**
 * سرویس موزیک پلیر واقعی با ExoPlayer
 */
class RealMusicService : Service() {
    
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var musicRepository: MusicRepository
    private lateinit var notificationManager: PlayerNotificationManager
    
    private var currentSong: Song? = null
    private var isPlaying = false
    private val binder = MusicBinder()
    
    inner class MusicBinder : Binder() {
        fun getService(): RealMusicService = this@RealMusicService
    }
    
    override fun onCreate() {
        super.onCreate()
        
        initializePlayer()
        initializeMediaSession()
        initializeRepository()
        createNotificationChannel()
    }
    
    private fun initializePlayer() {
        exoPlayer = ExoPlayer.Builder(this).build()
        
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        isPlaying = exoPlayer.isPlaying
                        updateNotification()
                    }
                    Player.STATE_ENDED -> {
                        playNext()
                    }
                }
            }
            
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                updateMediaSessionState()
                updateNotification()
            }
        })
    }
    
    private fun initializeMediaSession() {
        mediaSession = MediaSessionCompat(this, "MusicService").apply {
            setCallback(MediaSessionCallback())
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        }
    }
    
    private fun initializeRepository() {
        musicRepository = MusicRepository(this)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "music_channel",
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_STOP -> stopSelf()
        }
        
        return START_STICKY
    }
    
    fun playSong(song: Song) {
        currentSong = song
        
        val mediaItem = MediaItem.fromUri(song.url)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
        
        updateMediaSessionMetadata()
        updateNotification()
    }
    
    fun play() {
        if (currentSong != null) {
            exoPlayer.play()
        }
    }
    
    fun pause() {
        exoPlayer.pause()
    }
    
    fun playNext() {
        val songs = musicRepository.getSongsByMood(getCurrentMood())
        val currentIndex = songs.indexOfFirst { it.id == currentSong?.id }
        val nextIndex = (currentIndex + 1) % songs.size
        
        if (songs.isNotEmpty()) {
            playSong(songs[nextIndex])
        }
    }
    
    fun playPrevious() {
        val songs = musicRepository.getSongsByMood(getCurrentMood())
        val currentIndex = songs.indexOfFirst { it.id == currentSong?.id }
        val previousIndex = if (currentIndex - 1 < 0) songs.size - 1 else currentIndex - 1
        
        if (songs.isNotEmpty()) {
            playSong(songs[previousIndex])
        }
    }
    
    fun seekTo(position: Long) {
        exoPlayer.seekTo(position)
    }
    
    fun getCurrentPosition(): Long = exoPlayer.currentPosition
    fun getDuration(): Long = exoPlayer.duration
    fun isCurrentlyPlaying(): Boolean = exoPlayer.isPlaying
    fun getCurrentSong(): Song? = currentSong
    
    private fun getCurrentMood(): String {
        return currentSong?.mood ?: "شاد"
    }
    
    private fun updateMediaSessionMetadata() {
        currentSong?.let { song ->
            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
                .build()
            
            mediaSession.setMetadata(metadata)
        }
    }
    
    private fun updateMediaSessionState() {
        val state = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                exoPlayer.currentPosition,
                if (isPlaying) 1f else 0f
            )
            .build()
        
        mediaSession.setPlaybackState(state)
    }
    
    private fun updateNotification() {
        currentSong?.let { song ->
            val notification = createNotification(song)
            startForeground(1, notification)
        }
    }
    
    private fun createNotification(song: Song): Notification {
        val intent = Intent(this, MusicActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, "music_channel")
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setSmallIcon(R.drawable.ic_music_note)
            .setLargeIcon(getAlbumArt(song.albumArt))
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_previous, "Previous", createPendingIntent(ACTION_PREVIOUS))
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play",
                createPendingIntent(if (isPlaying) ACTION_PAUSE else ACTION_PLAY)
            )
            .addAction(R.drawable.ic_next, "Next", createPendingIntent(ACTION_NEXT))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
    
    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, RealMusicService::class.java).setAction(action)
        return PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun getAlbumArt(albumArtUrl: String?): Bitmap? {
        return try {
            // TODO: بارگذاری تصویر آلبوم با Glide
            BitmapFactory.decodeResource(resources, R.drawable.ic_music_note)
        } catch (e: Exception) {
            BitmapFactory.decodeResource(resources, R.drawable.ic_music_note)
        }
    }
    
    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            play()
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
        
        override fun onSeekTo(pos: Long) {
            seekTo(pos)
        }
        
        override fun onStop() {
            stopSelf()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
        mediaSession.release()
    }
    
    companion object {
        const val ACTION_PLAY = "com.persianai.assistant.music.PLAY"
        const val ACTION_PAUSE = "com.persianai.assistant.music.PAUSE"
        const val ACTION_NEXT = "com.persianai.assistant.music.NEXT"
        const val ACTION_PREVIOUS = "com.persianai.assistant.music.PREVIOUS"
        const val ACTION_STOP = "com.persianai.assistant.music.STOP"
    }
}
