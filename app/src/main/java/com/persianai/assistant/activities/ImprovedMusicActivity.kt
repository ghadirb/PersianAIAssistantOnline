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
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.persianai.assistant.R
import com.persianai.assistant.databinding.ActivityMusicBinding
import com.persianai.assistant.music.SimpleMusicPlayer
import com.persianai.assistant.music.MusicMood
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * اکتیویتی موزیک پلیر بهبود یافته با پخش واقعی موسیقی
 */
class ImprovedMusicActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMusicBinding
    private lateinit var musicPlayer: SimpleMusicPlayer
    private var tracksAdapter: MusicTracksAdapter? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMusicBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        initializeMusicPlayer()
        setupUI()
        checkPermissions()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "🎵 موزیک پلیر هوشمند"
    }
    
    private fun initializeMusicPlayer() {
        musicPlayer = SimpleMusicPlayer(this)
        
        // مشاهده تغییرات وضعیت پخش
        lifecycleScope.launch {
            musicPlayer.isPlaying.collect { isPlaying ->
                updatePlayPauseButton(isPlaying)
            }
        }
        
        lifecycleScope.launch {
            musicPlayer.currentTrack.collect { track ->
                updateCurrentTrackInfo(track)
            }
        }
        
        lifecycleScope.launch {
            musicPlayer.currentPosition.collect { position ->
                binding.seekBar?.progress = position
                binding.currentTimeText?.text = musicPlayer.formatTime(position.toLong())
            }
        }
        
        lifecycleScope.launch {
            musicPlayer.duration.collect { duration ->
                binding.seekBar?.max = duration
                binding.totalTimeText?.text = musicPlayer.formatTime(duration.toLong())
            }
        }
    }
    
    private fun setupUI() {
        setupClickListeners()
        setupSeekBar()
        setupMoodSelection()
    }
    
    private fun setupClickListeners() {
        // دکمه اسکن موسیقی
        binding.scanMusicButton?.setOnClickListener {
            scanAndLoadMusic()
        }
        
        // دکمه پخش/توقف
        binding.playPauseButton?.setOnClickListener {
            musicPlayer.togglePlayPause()
        }
        
        // دکمه بعدی
        binding.nextButton?.setOnClickListener {
            musicPlayer.playNext()
        }
        
        // دکمه قبلی
        binding.prevButton?.setOnClickListener {
            musicPlayer.playPrevious()
        }
        
        // دکمه shuffle
        binding.shuffleButton?.setOnClickListener {
            toggleShuffleMode()
        }
        
        // دکمه تکرار
        binding.repeatButton?.setOnClickListener {
            toggleRepeatMode()
        }
        
        // دکمه انتخاب حالت موسیقی
        binding.voiceCommandButton?.setOnClickListener {
            showMoodSelectionDialog()
        }
    }
    
    private fun setupSeekBar() {
        binding.seekBar?.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicPlayer.seekTo(progress)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                // کاربر در حال جستجو است
            }
            
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                // کاربر جستجو را تمام کرد
            }
        })
    }
    
    private fun setupMoodSelection() {
        val moods = MusicMood.values()
        
        binding.moodChipGroup?.removeAllViews()
        
        moods.forEach { mood ->
            val chip = com.google.android.material.chip.Chip(this)
            chip.text = mood.displayName
            chip.isCheckable = true
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    createMoodPlaylist(mood)
                }
            }
            binding.moodChipGroup?.addView(chip)
        }
    }
    
    private fun scanAndLoadMusic() {
        binding.progressBar?.visibility = View.VISIBLE
        binding.scanMusicButton?.isEnabled = false
        
        lifecycleScope.launch {
            try {
                val tracks = musicPlayer.loadTracksFromDevice()
                
                binding.progressBar?.visibility = View.GONE
                binding.scanMusicButton?.isEnabled = true
                
                if (tracks.isNotEmpty()) {
                    showMusicStats(tracks)
                    setupTracksList(tracks)
                    enablePlayerControls()
                    
                    Toast.makeText(
                        this@ImprovedMusicActivity,
                        "✅ ${tracks.size} آهنگ پیدا شد",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@ImprovedMusicActivity,
                        "هیچ آهنگی در دستگاه پیدا نشد",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
            } catch (e: Exception) {
                binding.progressBar?.visibility = View.GONE
                binding.scanMusicButton?.isEnabled = true
                
                Toast.makeText(
                    this@ImprovedMusicActivity,
                    "خطا در اسکن موسیقی: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun showMusicStats(tracks: List<SimpleMusicPlayer.MusicTrack>) {
        val stats = musicPlayer.getMusicStats()
        
        binding.statsCard?.visibility = View.VISIBLE
        binding.musicCountText?.text = "📊 آمار موسیقی"
        binding.statsText?.text = buildString {
            appendLine("🎵 کل آهنگ‌ها: ${stats.totalTracks}")
            appendLine("⏱️ مدت کل: ${formatDuration(stats.totalDuration)}")
            appendLine("🎼 ژانرها: ${stats.totalGenres}")
            appendLine("🎤 هنرمندان: ${stats.totalArtists}")
            if (stats.topGenres.isNotEmpty()) {
                appendLine("🔥 برترین ژانرها: ${stats.topGenres.take(3).joinToString(", ")}")
            }
            if (stats.topArtists.isNotEmpty()) {
                appendLine("⭐ برترین هنرمندان: ${stats.topArtists.take(3).joinToString(", ")}")
            }
        }
    }
    
    private fun setupTracksList(tracks: List<SimpleMusicPlayer.MusicTrack>) {
        tracksAdapter = MusicTracksAdapter(tracks) { track, index ->
            musicPlayer.playTrack(index)
        }
        
        binding.tracksRecyclerView?.layoutManager = LinearLayoutManager(this)
        binding.tracksRecyclerView?.adapter = tracksAdapter
        binding.tracksRecyclerView?.visibility = View.VISIBLE
    }
    
    private fun enablePlayerControls() {
        binding.playerControlsCard?.visibility = View.VISIBLE
        
        // نمایش دکمه‌های کنترل
        binding.playPauseButton?.visibility = View.VISIBLE
        binding.nextButton?.visibility = View.VISIBLE
        binding.prevButton?.visibility = View.VISIBLE
        binding.shuffleButton?.visibility = View.VISIBLE
        binding.repeatButton?.visibility = View.VISIBLE
        binding.seekBar?.visibility = View.VISIBLE
        binding.currentTimeText?.visibility = View.VISIBLE
        binding.totalTimeText?.visibility = View.VISIBLE
        binding.nowPlayingText?.visibility = View.VISIBLE
    }
    
    private fun updatePlayPauseButton(isPlaying: Boolean) {
        binding.playPauseButton?.text = if (isPlaying) "⏸️" else "▶️"
    }
    
    private fun updateCurrentTrackInfo(track: SimpleMusicPlayer.MusicTrack?) {
        if (track != null) {
            binding.nowPlayingText?.text = "🎵 ${track.title} - ${track.artist}"
            binding.nowPlayingText?.visibility = View.VISIBLE
            
            // آپدیت لیست آهنگ‌ها برای نمایش آهنگ در حال پخش
            tracksAdapter?.setCurrentTrackIndex(musicPlayer.currentTrackIndex.value)
        } else {
            binding.nowPlayingText?.visibility = View.GONE
        }
    }
    
    private fun toggleShuffleMode() {
        val currentShuffle = musicPlayer.isShuffleEnabled()
        musicPlayer.setShuffleMode(!currentShuffle)
        
        binding.shuffleButton?.setBackgroundColor(
            ContextCompat.getColor(
                this,
                if (!currentShuffle) R.color.purple_700 else R.color.purple_200
            )
        )
        
        Toast.makeText(
            this,
            if (!currentShuffle) "🔀 Shuffle فعال شد" else "🔀 Shuffle غیرفعال شد",
            Toast.LENGTH_SHORT
        ).show()
    }
    
    private fun toggleRepeatMode() {
        val currentMode = musicPlayer.getRepeatMode()
        val nextMode = when (currentMode) {
            SimpleMusicPlayer.RepeatMode.OFF -> SimpleMusicPlayer.RepeatMode.ALL
            SimpleMusicPlayer.RepeatMode.ALL -> SimpleMusicPlayer.RepeatMode.ONE
            SimpleMusicPlayer.RepeatMode.ONE -> SimpleMusicPlayer.RepeatMode.OFF
        }
        
        musicPlayer.setRepeatMode(nextMode)
        
        val repeatText = when (nextMode) {
            SimpleMusicPlayer.RepeatMode.OFF -> "🔁 تکرار خاموش"
            SimpleMusicPlayer.RepeatMode.ALL -> "🔁 تکرار همه"
            SimpleMusicPlayer.RepeatMode.ONE -> "🔂 تکرار یک"
        }
        
        binding.repeatButton?.text = repeatText
        
        Toast.makeText(this, repeatText, Toast.LENGTH_SHORT).show()
    }
    
    private fun showMoodSelectionDialog() {
        val moods = MusicMood.values()
        val moodNames = moods.map { it.displayName }.toTypedArray()
        
        MaterialAlertDialogBuilder(this)
            .setTitle("🎵 انتخاب حالت موسیقی")
            .setItems(moodNames) { _, which ->
                val selectedMood = moods[which]
                createMoodPlaylist(selectedMood)
            }
            .show()
    }
    
    private fun createMoodPlaylist(mood: MusicMood) {
        lifecycleScope.launch {
            try {
                val moodPlaylist = musicPlayer.createMoodPlaylist(mood)
                
                if (moodPlaylist.isNotEmpty()) {
                    musicPlayer.setPlaylist(moodPlaylist)
                    musicPlayer.playTrack(0)
                    
                    binding.selectedMoodText?.text = "🎵 حالت انتخاب شده: ${mood.displayName} (${moodPlaylist.size} آهنگ)"
                    
                    setupTracksList(moodPlaylist)
                    
                    Toast.makeText(
                        this@ImprovedMusicActivity,
                        "✅ پلی‌لیست ${mood.displayName} با ${moodPlaylist.size} آهنگ ایجاد شد",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@ImprovedMusicActivity,
                        "هیچ آهنگی برای حالت ${mood.displayName} پیدا نشد",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(
                    this@ImprovedMusicActivity,
                    "خطا در ایجاد پلی‌لیست: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun checkPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(permission),
                PERMISSION_REQUEST_CODE
            )
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
                scanAndLoadMusic()
            } else {
                Toast.makeText(
                    this,
                    "برای اسکن موسیقی نیاز به مجوز دسترسی به حافظه دارید",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun formatDuration(durationMs: Long): String {
        val hours = durationMs / (1000 * 60 * 60)
        val minutes = (durationMs % (1000 * 60 * 60)) / (1000 * 60)
        
        return if (hours > 0) {
            String.format("%d ساعت و %d دقیقه", hours, minutes)
        } else {
            String.format("%d دقیقه", minutes)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        musicPlayer.cleanup()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 101
    }
}
