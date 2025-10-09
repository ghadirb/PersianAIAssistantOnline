package com.persianai.assistant.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.persianai.assistant.R
import com.persianai.assistant.databinding.ActivityMusicBinding
import com.persianai.assistant.music.MusicPlaylistManager
import kotlinx.coroutines.launch
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import androidx.core.net.toUri

class MusicActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMusicBinding
    private lateinit var musicManager: MusicPlaylistManager
    private var selectedMood: String = ""
    private var selectedPlayerPackage: String? = null
    private var exoPlayer: ExoPlayer? = null
    private var currentPlaylist: MusicPlaylistManager.Playlist? = null
    private var currentTrackIndex = 0
    
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
            
            setupUI()
            checkPermissions()
        } catch (e: Exception) {
            android.util.Log.e("MusicActivity", "Error in onCreate", e)
            Toast.makeText(this, "خطا در بارگذاری صفحه موزیک", Toast.LENGTH_LONG).show()
            finish()
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
            
            moods.forEach { (label, mood) ->
                val chip = Chip(this)
                chip.text = label
                chip.isCheckable = true
                chip.setOnClickListener {
                    selectedMood = mood
                    binding.selectedMoodText?.text = "حالت انتخاب شده: $label"
                    binding.createPlaylistButton?.isEnabled = true
                }
                binding.moodChipGroup?.addView(chip)
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicActivity", "Error in setupUI", e)
        
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
                val playlist = musicManager.createMoodPlaylist(selectedMood)
                
                runOnUiThread {
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
        
        val playerNames = players.map { it.appName }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("انتخاب Music Player")
            .setItems(playerNames) { _, which ->
                selectedPlayerPackage = players[which].packageName
                binding.selectedPlayerText?.text = "Player: ${players[which].appName}"
                
                // ذخیره انتخاب کاربر
                val prefs = getSharedPreferences("music_settings", MODE_PRIVATE)
                prefs.edit().putString("selected_player", selectedPlayerPackage).apply()
            }
            .show()
    }
    
    private fun playInternalPlayer(playlist: MusicPlaylistManager.Playlist) {
        try {
            currentPlaylist = playlist
            currentTrackIndex = 0
            
            // ایجاد ExoPlayer
            if (exoPlayer == null) {
                exoPlayer = ExoPlayer.Builder(this).build()
                
                // افزودن listener برای پخش خودکار آهنگ بعدی
                exoPlayer?.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_ENDED -> {
                                playNextTrack()
                            }
                        }
                    }
                })
            }
            
            // پاک کردن صف فعلی و اضافه کردن آهنگ‌ها
            exoPlayer?.clearMediaItems()
            playlist.tracks.forEach { track ->
                val mediaItem = MediaItem.fromUri(track.uri.toUri())
                exoPlayer?.addMediaItem(mediaItem)
            }
            
            // آماده کردن و شروع پخش
            exoPlayer?.prepare()
            exoPlayer?.play()
            
            // نمایش کنترل‌های پخش
            showPlaybackControls(playlist.tracks[0])
            
            Toast.makeText(this, "▶️ پخش ${playlist.tracks.size} آهنگ", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            android.util.Log.e("MusicActivity", "Error playing music", e)
            Toast.makeText(this, "خطا در پخش موزیک: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun playNextTrack() {
        currentPlaylist?.let { playlist ->
            currentTrackIndex++
            if (currentTrackIndex < playlist.tracks.size) {
                showPlaybackControls(playlist.tracks[currentTrackIndex])
            } else {
                // پخش تمام شد
                currentTrackIndex = 0
                Toast.makeText(this, "✅ پخش پلی‌لیست تمام شد", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showPlaybackControls(track: MusicPlaylistManager.MusicTrack) {
        // نمایش اطلاعات آهنگ در حال پخش
        binding.nowPlayingText?.text = "▶️ ${track.title}"
        binding.nowPlayingText?.visibility = View.VISIBLE
        
        // می‌توانید یک Player UI ساده اضافه کنید
        binding.playPauseButton?.visibility = View.VISIBLE
        binding.nextButton?.visibility = View.VISIBLE
        binding.prevButton?.visibility = View.VISIBLE
        
        binding.playPauseButton?.setOnClickListener {
            exoPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    binding.playPauseButton?.text = "▶️"
                } else {
                    it.play()
                    binding.playPauseButton?.text = "⏸️"
                }
            }
        }
        
        binding.nextButton?.setOnClickListener {
            exoPlayer?.seekToNext()
        }
        
        binding.prevButton?.setOnClickListener {
            exoPlayer?.seekToPrevious()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
