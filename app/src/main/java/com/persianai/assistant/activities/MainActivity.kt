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

        // Setup chat UI and logic here...
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reminders -> {
                startActivity(Intent(this, AdvancedRemindersActivity::class.java))
                true
            }
            R.id.action_finance -> {
                startActivity(Intent(this, FinanceAdvancedActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

