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
import com.persianai.assistant.R
import com.persianai.assistant.databinding.ActivityMusicBinding
import com.persianai.assistant.music.RealMusicService
import com.persianai.assistant.utils.MusicPlaylistManager
import com.persianai.assistant.utils.MusicRepository
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
    private var selectedMood: String = ""
    private var selectedPlayerPackage: String? = null
    private var currentPlaylist: MusicPlaylistManager.Playlist? = null
    private var currentTrackIndex = 0
    private var isShuffleEnabled = false
    // TODO: Implement repeat mode in RealMusicService
    private val seekBarHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isUserSeeking = false
    
    // Service connection
    private var musicService: RealMusicService? = null
    private var isServiceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RealMusicService.MusicBinder
            musicService = binder.getService()
            isServiceBound = true
            
            // Update UI based on service state
            updateUIFromService()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            isServiceBound = false
            musicService = null
        }
    }
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 101
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMusicBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "🎵 پلی‌لیست هوشمند"
            
            musicManager = MusicPlaylistManager(this)
            musicRepository = MusicRepository(this)
            
            setupUI()
            checkPermissions()
            
            // Bind to music service
            bindToMusicService()
            
            // چک دستورات AI
            handleAIIntent()
            
        } catch (e: Exception) {
            android.util.Log.e("MusicActivity", "Error in onCreate", e)
            Toast.makeText(this, "خطا در بارگذاری صفحه موزیک", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun handleAIIntent() {
        val aiMood = intent.getStringExtra("AI_MOOD")
        val autoStart = intent.getBooleanExtra("AUTO_START", false)
        
        if (aiMood != null && autoStart) {
            selectedMood = aiMood
            binding.selectedMoodText?.text = "حالت انتخاب شده: 🎵 $aiMood"
            
            // ایجاد خودکار پلی‌لیست
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                createPlaylist()
            }, 500)
        }
    }
    
    private fun setupUI() {
        try {
            // Mood chips
            val moods = listOf(
                "🎉 شاد" to "شاد",
                "😢 غمگین" to "غمگین",
                "❤️ عاشقانه" to "عاشقانه",
                "🎭 سنتی" to "سنتی",
                "⚡ پرانرژی" to "انرژی",
                "🎲 تصادفی" to "random"
            )
            
            moods.forEach { mood ->
                val moodChip = com.google.android.material.chip.Chip(this)
                moodChip.text = mood.first
                moodChip.isCheckable = true
                moodChip.setOnClickListener {
                    selectedMood = mood.second
                    binding.selectedMoodText?.text = "حالت انتخاب شده: ${mood.first}"
                    binding.createPlaylistButton?.isEnabled = true
                }
                binding.moodChipGroup?.addView(moodChip)
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicActivity", "Error in setupUI", e)
        }
        
        // Create playlist button
        binding.createPlaylistButton?.setOnClickListener {
            createPlaylist()
        }
        
        // Music Player Selection Button - حذف شد (پخش داخلی)
        binding.selectPlayerButton?.visibility = View.GONE
        
        // Scan music button
        binding.scanMusicButton?.setOnClickListener {
            scanMusic()
        }
        // Chat AI button - تبدیل از دستور صوتی
        binding.voiceCommandButton?.setOnClickListener {
            try {
                val intent = android.content.Intent(this, MainActivity::class.java)
                intent.putExtra("SUGGEST_TEXT", "یک پلی‌لیست موزیک شاد برای من بساز")
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            } catch (e: Exception) {
                android.util.Log.e("MusicActivity", "Error opening chat", e)
                Toast.makeText(this, "💬 برای استفاده از چت AI، به بخش چت بروید", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun checkPermissions() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(permission),
                PERMISSION_REQUEST_CODE
            )
        } else {
            scanMusic()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanMusic()
            } else {
                Toast.makeText(
                    this,
                    "⚠️ برای اسکن موسیقی‌ها نیاز به دسترسی به حافظه است",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun scanMusic() {
        binding.progressBar?.visibility = View.VISIBLE
        binding.musicCountText?.text = "در حال اسکن..."
        
        lifecycleScope.launch {
            try {
                val tracks = musicManager.scanDeviceMusic()
                
                runOnUiThread {
                    binding.progressBar?.visibility = View.GONE
                    binding.musicCountText?.text = "تعداد آهنگ‌های یافت شده: ${tracks.size}"
                    
                    if (tracks.isEmpty()) {
                        Snackbar.make(
                            binding.root,
                            "⚠️ هیچ آهنگی در دستگاه یافت نشد",
                            Snackbar.LENGTH_LONG
                        ).show()
                    } else {
                        // نمایش آمار
                        showMusicStats(tracks)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar?.visibility = View.GONE
                    Toast.makeText(this@MusicActivity, "خطا در اسکن موسیقی‌ها", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showMusicStats(tracks: List<MusicPlaylistManager.MusicTrack>) {
        val artists = tracks.mapNotNull { it.artist }.distinct().size
        val albums = tracks.mapNotNull { it.album }.distinct().size
        val totalDuration = tracks.sumOf { it.duration } / 1000 / 60 // به دقیقه
        
        binding.statsText?.text = """
            📊 آمار موسیقی‌های شما:
            • تعداد آهنگ: ${tracks.size}
            • تعداد خواننده: $artists
            • تعداد آلبوم: $albums
            • مجموع زمان: $totalDuration دقیقه
        """.trimIndent()
        
        binding.statsCard?.visibility = View.VISIBLE
    }
    
    private fun createPlaylist() {
        if (selectedMood.isEmpty()) {
            Toast.makeText(this, "لطفاً یک حالت انتخاب کنید", Toast.LENGTH_SHORT).show()
            return
        }
        
        binding.progressBar?.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val allTracks = musicManager.scanDeviceMusic()
                val playlist = musicManager.createPlaylistByMood(selectedMood, allTracks)
                
                binding.progressBar?.visibility = View.GONE
                
                if (playlist.tracks.isEmpty()) {
                    Snackbar.make(
                        binding.root,
                        "⚠️ هیچ آهنگی برای این mood یافت نشد",
                        Snackbar.LENGTH_LONG
                    ).show()
                } else {
                    showPlaylistDialog(playlist)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar?.visibility = View.GONE
                    Toast.makeText(this@MusicActivity, "خطا در ایجاد پلی‌لیست", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showPlaylistDialog(playlist: MusicPlaylistManager.Playlist) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("✅ پلی‌لیست ${playlist.mood} آماده شد")
            .setMessage("""
                تعداد آهنگ: ${playlist.tracks.size}
                
                نمونه آهنگ‌ها:
                ${playlist.tracks.take(5).joinToString("\n") { "• ${it.title}" }}
                ${if (playlist.tracks.size > 5) "\n..." else ""}
            """.trimIndent())
            .setPositiveButton("پخش در برنامه") { _, _ ->
                playInternalPlayer(playlist)
            }
            .setNeutralButton("ذخیره پلی‌لیست") { _, _ ->
                savePlaylist(playlist)
            }
            .setNegativeButton("بستن", null)
            .create()
        
        dialog.show()
    }
    
    private fun savePlaylist(playlist: MusicPlaylistManager.Playlist) {
        // ذخیره در SharedPreferences یا Database
        val prefs = getSharedPreferences("playlists", MODE_PRIVATE)
        val editor = prefs.edit()
        
        val playlistJson = """
            {
                "name": "${playlist.name}",
                "mood": "${playlist.mood}",
                "count": ${playlist.tracks.size},
                "created": ${playlist.createdAt}
            }
        """.trimIndent()
        
        editor.putString("playlist_${playlist.createdAt}", playlistJson)
        editor.apply()
        
        Snackbar.make(binding.root, "✅ پلی‌لیست ذخیره شد", Snackbar.LENGTH_SHORT).show()
    }
    
    private fun selectMusicPlayer() {
        val players = musicManager.getInstalledMusicPlayers()
        
        if (players.isEmpty()) {
            Toast.makeText(this, "هیچ Music Player یافت نشد", Toast.LENGTH_SHORT).show()
            return
        }
        
        val builder = AlertDialog.Builder(this)
        builder.setTitle("انتخاب Music Player")
        val playerNames = players.map { it.appName }.toTypedArray()
        builder.setItems(playerNames) { _, which ->
            selectedPlayerPackage = players[which].packageName
            binding.selectedPlayerText?.text = "Player: ${players[which].appName}"
            
            // ذخیره انتخاب کاربر
            val prefs = getSharedPreferences("music_settings", MODE_PRIVATE)
            prefs.edit().putString("selected_player", selectedPlayerPackage).apply()
        }
        builder.show()
    }
    
    private fun playInternalPlayer(playlist: MusicPlaylistManager.Playlist) {
        try {
            if (!isServiceBound) {
                Toast.makeText(this, "در حال اتصال به سرویس موزیک...", Toast.LENGTH_SHORT).show()
                return
            }
            
            currentPlaylist = playlist
            currentTrackIndex = 0
            
            // Convert MusicPlaylistManager tracks to Song model
            val song = com.persianai.assistant.models.Song(
                id = playlist.tracks[0].id,
                title = playlist.tracks[0].title,
                artist = playlist.tracks[0].artist ?: "Unknown",
                album = playlist.tracks[0].album ?: "Unknown",
                url = playlist.tracks[0].path,
                mood = selectedMood,
                duration = playlist.tracks[0].duration,
                albumArt = ""
            )
            
            // Play song through service
            musicService?.playSong(song)
            
            // Show playback controls
            showPlaybackControls(playlist.tracks[0])
            
            Toast.makeText(this, "▶️ پخش ${playlist.tracks.size} آهنگ", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            android.util.Log.e("MusicActivity", "Error playing music", e)
            Toast.makeText(this, "خطا در پخش موزیک: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun playNextTrack() {
        currentPlaylist?.let { playlist ->
            musicService?.playNext()
        }
    }
    
    private fun showPlaybackControls(track: MusicPlaylistManager.MusicTrack) {
        // نمایش اطلاعات آهنگ در حال پخش
        binding.nowPlayingText?.text = "▶️ ${track.title}"
        binding.nowPlayingText?.visibility = View.VISIBLE
        
        // نمایش کارت کنترل‌ها
        binding.playerControlsCard?.visibility = View.VISIBLE
        binding.playPauseButton?.visibility = View.VISIBLE
        binding.nextButton?.visibility = View.VISIBLE
        binding.prevButton?.visibility = View.VISIBLE
        binding.shuffleButton?.visibility = View.VISIBLE
        binding.repeatButton?.visibility = View.VISIBLE
        binding.seekBar?.visibility = View.VISIBLE
        binding.timeText?.visibility = View.VISIBLE
        
        // Setup SeekBar
        setupSeekBar()
        
        binding.playPauseButton?.setOnClickListener {
            musicService?.let { service ->
                if (service.isCurrentlyPlaying()) {
                    service.pause()
                } else {
                    service.play()
                }
            }
        }
        
        binding.nextButton?.setOnClickListener {
            musicService?.playNext()
        }
        
        binding.prevButton?.setOnClickListener {
            musicService?.playPrevious()
        }
        
        // Shuffle Button - TODO: Implement in RealMusicService
        binding.shuffleButton?.setOnClickListener {
            isShuffleEnabled = !isShuffleEnabled
            val color = if (isShuffleEnabled) "#FF4081" else "#6A1B9A"
            binding.shuffleButton?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor(color)
            )
            val msg = if (isShuffleEnabled) "🔀 Shuffle فعال" else "🔀 Shuffle غیرفعال"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        
        // Repeat Button - TODO: Implement in RealMusicService
        binding.repeatButton?.setOnClickListener {
            Toast.makeText(this, "تکرار به زودی اضافه می‌شود", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupSeekBar() {
        binding.seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicService?.seekTo(progress.toLong())
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
            }
        })
    }
    
    
    private fun formatTime(millis: Long): String {
        val minutes = (millis / 1000) / 60
        val seconds = (millis / 1000) % 60
        return String.format("%d:%02d", minutes, seconds)
    }
    
    private fun updateUIFromService() {
        musicService?.let { service ->
            updatePlayPauseButton(service.isCurrentlyPlaying())
            
            // Update seekbar
            binding.seekBar?.max = service.getDuration().toInt()
            
            // Start position updates
            startPositionUpdater()
        }
    }
    
    private fun startPositionUpdater() {
        seekBarHandler.post(object : Runnable {
            override fun run() {
                musicService?.let { service ->
                    if (!isUserSeeking && service.isCurrentlyPlaying()) {
                        val current = service.getCurrentPosition()
                        val total = service.getDuration()
                        binding.seekBar?.progress = current.toInt()
                        binding.timeText?.text = "${formatTime(current)} / ${formatTime(total)}"
                    }
                }
                seekBarHandler.postDelayed(this, 1000)
            }
        })
    }
    
    private fun bindToMusicService() {
        val intent = Intent(this, RealMusicService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        startService(intent) // Start service in foreground
    }
    
    private fun unbindFromMusicService() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }
    
    private fun updatePlayPauseButton(isPlaying: Boolean) {
        binding.playPauseButton?.text = if (isPlaying) "⏸️" else "▶️"
    }
    
    private fun updateTimeDisplay() {
        musicService?.let { service ->
            val current = service.getCurrentPosition()
            val total = service.getDuration()
            binding.timeText?.text = "${formatTime(current)} / ${formatTime(total)}"
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        seekBarHandler.removeCallbacksAndMessages(null)
        unbindFromMusicService()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
