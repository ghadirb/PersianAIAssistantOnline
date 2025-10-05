package com.persianai.assistant.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.persianai.assistant.databinding.ActivityCalendarBinding

class CalendarActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityCalendarBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "📅 تقویم فارسی"
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
