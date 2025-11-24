package com.persianai.assistant.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.persianai.assistant.adapters.CheckAdapter
import com.persianai.assistant.data.AccountingDB
import com.persianai.assistant.databinding.ActivityCheckListBinding
import kotlinx.coroutines.launch

class CheckListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCheckListBinding
    private lateinit var db: AccountingDB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "چک‌ها"

        db = AccountingDB(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        loadChecks()
    }

    private fun loadChecks() {
        lifecycleScope.launch {
            val checks = db.getAllChecks()
            binding.recyclerView.adapter = CheckAdapter { check ->
                // Handle check click
            }.apply {
                submitList(checks)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
