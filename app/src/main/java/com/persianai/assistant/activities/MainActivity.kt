package com.persianai.assistant.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.persianai.assistant.R
import com.persianai.assistant.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        setupDashboardCards()
    }

    private fun setupDashboardCards() {
        binding.cardReminders.setOnClickListener {
            startActivity(Intent(this, AdvancedRemindersActivity::class.java))
        }

        binding.cardFinance.setOnClickListener {
            startActivity(Intent(this, ProfessionalAccountingActivity::class.java))
        }

        binding.cardAssistant.setOnClickListener {
             // This should open the main chat activity, let's assume it's ChatActivity for now
             startActivity(Intent(this, ChatActivity::class.java))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                // startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_chat_history -> {
                // startActivity(Intent(this, ChatHistoryActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
