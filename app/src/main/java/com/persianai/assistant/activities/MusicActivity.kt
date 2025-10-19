package com.persianai.assistant.activities

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import android.widget.SeekBar
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.C
import com.persianai.assistant.R
import com.persianai.assistant.databinding.ActivityMusicBinding
import com.persianai.assistant.music.RealMusicService
import com.persianai.assistant.utils.MusicPlaylistManager
import com.persianai.assistant.utils.MusicRepository
import com.persianai.assistant.ai.ContextualAIAssistant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class MusicActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMusicBinding
    private lateinit var musicManager: MusicPlaylistManager
    private lateinit var musicRepository: MusicRepository
    private lateinit var aiAssistant: ContextualAIAssistant
    private var musicService: RealMusicService? = null
    private var isBound = false
    private var exoPlayer: ExoPlayer? = null
    private var currentPlaylist: MusicPlaylistManager.Playlist? = null
    private var currentTrackIndex = 0
    private var isUserSeeking = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RealMusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            updateUIFromService()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            musicService = null
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMusicBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "🎵 پلی‌لیست هوشمند"
        
        musicManager = MusicPlaylistManager(this)
        musicRepository = MusicRepository(this)
        aiAssistant = ContextualAIAssistant(this)
        
        setupExoPlayer()
        setupUI()
        loadPlaylists()
        checkPermissions()
    }
    
    private fun setupExoPlayer() {
        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .build()
    }
    
    private fun setupUI() {
        // Play/Pause button
        binding.playPauseButton?.setOnClickListener {
            exoPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    binding.playPauseButton?.setIconResource(R.drawable.ic_play)
                } else {
                    player.play()
                    binding.playPauseButton?.setIconResource(R.drawable.ic_pause)
                }
            }
        }
        
        // Next button
        binding.nextButton?.setOnClickListener {
            playNextTrack()
        }
        
        // Previous button
        // TODO: Add previousButton to layout
        // binding.previousButton?.setOnClickListener {
        //     playPreviousTrack()
        // }
        
        // Seek bar
        binding.seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    exoPlayer?.seekTo(progress.toLong())
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
            }
        })
        
        // AI Chat button
        // TODO: Add aiChatButton to layout
        // binding.aiChatButton?.setOnClickListener {
        //     showMusicAIChat()
        // }
        
        // Setup player listener
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        updateDuration()
                    }
                    Player.STATE_ENDED -> {
                        playNextTrack()
                    }
                }
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                binding.playPauseButton?.setIconResource(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                )
            }
        })
    }
    
    private fun loadPlaylists() {
        lifecycleScope.launch {
            try {
                // TODO: Add playlistChipGroup to layout
                // val playlists = musicManager.getAllPlaylists()
                // setupPlaylistChips(playlists)
            } catch (e: Exception) {
                Toast.makeText(this@MusicActivity, "خطا در بارگذاری پلی‌لیست‌ها", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun setupPlaylistChips(playlists: List<MusicPlaylistManager.Playlist>) {
        // TODO: Add playlistChipGroup to layout
        // binding.playlistChipGroup?.removeAllViews()
        // 
        // playlists.forEach { playlist ->
        //     val chip = Chip(this)
        //     chip.text = playlist.name
        //     chip.isCheckable = true
        //     chip.setOnCheckedChangeListener { _, isChecked ->
        //         if (isChecked) {
        //             loadPlaylist(playlist)
        //         }
        //     }
        //     binding.playlistChipGroup?.addView(chip)
        // }
    }
    
    private fun loadPlaylist(playlist: MusicPlaylistManager.Playlist) {
        currentPlaylist = playlist
        currentTrackIndex = 0
        
        if (playlist.tracks.isNotEmpty()) {
            val track = playlist.tracks[0]
            playTrack(track)
        }
    }
    
    private fun playTrack(track: MusicPlaylistManager.Track) {
        exoPlayer?.let { player ->
            val mediaItem = MediaItem.fromUri(track.uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            
            // Update UI
            // TODO: Add trackTitleText and artistText to layout
            // binding.trackTitleText?.text = track.title
            // binding.artistText?.text = track.artist
        }
    }
    
    private fun playNextTrack() {
        currentPlaylist?.let { playlist ->
            currentTrackIndex = (currentTrackIndex + 1) % playlist.tracks.size
            val track = playlist.tracks[currentTrackIndex]
            playTrack(track)
        }
    }
    
    private fun playPreviousTrack() {
        currentPlaylist?.let { playlist ->
            currentTrackIndex = if (currentTrackIndex > 0) currentTrackIndex - 1 else playlist.tracks.size - 1
            val track = playlist.tracks[currentTrackIndex]
            playTrack(track)
        }
    }
    
    private fun updateDuration() {
        exoPlayer?.let { player ->
            val duration = player.duration
            binding.seekBar?.max = duration.toInt()
            
            // Update progress periodically
            lifecycleScope.launch {
                while (player.isPlaying) {
                    if (!isUserSeeking) {
                        binding.seekBar?.progress = player.currentPosition.toInt()
                        // TODO: Add currentTimeText and totalTimeText to layout
                        // binding.currentTimeText?.text = formatTime(player.currentPosition)
                        // binding.totalTimeText?.text = formatTime(player.duration)
                    }
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
    }
    
    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return String.format("%d:%02d", minutes, seconds)
    }
    
    private fun showMusicAIChat() {
        val options = arrayOf("پخش آهنگ شاد", "پخش آهنگ آرامش‌بخش", "پخش موسیقی سنتی", "پخش موسیقی پاپ")
        
        AlertDialog.Builder(this)
            .setTitle("انتخاب نوع موسیقی")
            .setItems(options) { _, which ->
                val selectedType = options[which]
                processMusicCommand(selectedType)
            }
            .show()
    }
    
    private fun processMusicCommand(command: String) {
        lifecycleScope.launch {
            try {
                val response = aiAssistant.processMusicCommand(command)
                Toast.makeText(this@MusicActivity, response.message, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MusicActivity, "خطا در پردازش دستور", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_MEDIA_AUDIO),
                    100
                )
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    100
                )
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadPlaylists()
        }
    }
    
    private fun updateUIFromService() {
        musicService?.let { service ->
            updatePlayPauseButton(service.isCurrentlyPlaying())
        }
    }
    
    private fun updatePlayPauseButton(isPlaying: Boolean) {
        binding.playPauseButton?.setIconResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }
    
    override fun onStart() {
        super.onStart()
        Intent(this, RealMusicService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
    
    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}
