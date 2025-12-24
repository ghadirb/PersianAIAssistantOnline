package com.persianai.assistant.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.persianai.assistant.databinding.ActivitySharedLocationBinding
import com.google.android.gms.maps.model.LatLng
import com.persianai.assistant.navigation.SavedLocationsManager
import com.persianai.assistant.utils.LocationShareParser
import kotlinx.coroutines.launch
import com.persianai.assistant.R

/**
 * فعالیت اشتراک گذاری مکان
 * این صفحه برای ذخیره مکان‌های مشترک‌شده از Google Maps و Neshan استفاده می‌شود
 */
class SharedLocationActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySharedLocationBinding
    private lateinit var savedLocationsManager: SavedLocationsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivitySharedLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        
        supportActionBar?.apply {
            title = "ذخیره مکان"
            setDisplayHomeAsUpEnabled(true)
        }

        savedLocationsManager = SavedLocationsManager(this)

        // Handle incoming share/view intent from maps apps
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        // 1) Try extras (legacy path)
        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)
        val locationName = intent.getStringExtra("location_name") ?: "مکان ذخیره‌شده"
        if (latitude != 0.0 && longitude != 0.0) {
            showLocationSaveDialog(latitude, longitude, locationName)
            return
        }

        // 2) Parse from URI/text (Google Maps / Neshan / geo:)
        val candidates = mutableListOf<String>()
        intent.data?.toString()?.let { candidates.add(it) }
        intent.dataString?.let { candidates.add(it) }
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { candidates.add(it) }

        // ClipData is common for shares from maps apps
        try {
            val clip = intent.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) {
                    val item = clip.getItemAt(i)
                    item.text?.toString()?.let { candidates.add(it) }
                    item.uri?.toString()?.let { candidates.add(it) }
                }
            }
        } catch (_: Exception) {
        }

        val parsed = candidates
            .asSequence()
            .mapNotNull { LocationShareParser.parseFromString(it) }
            .firstOrNull()
        if (parsed != null) {
            val suggested = parsed.label?.takeIf { it.isNotBlank() } ?: "مکان ذخیره‌شده"
            showLocationSaveDialog(parsed.latitude, parsed.longitude, suggested)
            return
        }

        Toast.makeText(this, "مکان قابل تشخیص نبود", Toast.LENGTH_SHORT).show()
        finish()
    }
    
    private fun showLocationSaveDialog(latitude: Double, longitude: Double, defaultName: String) {
        val input = TextInputEditText(this)
        input.setText(defaultName)
        input.hint = "نام مکان را وارد کنید"
        
        MaterialAlertDialogBuilder(this)
            .setTitle("ذخیره مکان جدید")
            .setMessage("لطفاً برای این مکان یک نام متمایز انتخاب کنید:")
            .setView(input)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    saveLocation(name, latitude, longitude)
                } else {
                    Toast.makeText(this, "نام نمی‌تواند خالی باشد", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("انصراف") { _, _ ->
                finish()
            }
            .show()
    }
    
    private fun saveLocation(name: String, latitude: Double, longitude: Double) {
        lifecycleScope.launch {
            try {
                val saved = savedLocationsManager.saveLocation(
                    name,
                    "",
                    LatLng(latitude, longitude),
                    "favorite"
                )
                if (!saved) throw IllegalStateException("saveLocation failed")
                
                Toast.makeText(
                    this@SharedLocationActivity,
                    "✅ مکان ذخیره شد: $name",
                    Toast.LENGTH_SHORT
                ).show()
                
                finish()
            } catch (e: Exception) {
                Toast.makeText(
                    this@SharedLocationActivity,
                    "❌ خطا در ذخیره: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
