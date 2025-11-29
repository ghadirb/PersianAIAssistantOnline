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
            binding.recyclerView.adapter = CheckAdapter(
                onCheckClick = { check ->
                    // Handle check click
                },
                onDeleteClick = { check ->
                    // Handle delete click
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this@CheckListActivity)
                        .setTitle("❌ حذف چک")
                        .setMessage("آیا از حذف این چک مطمئن هستید؟")
                        .setPositiveButton("حذف") { _, _ ->
                            lifecycleScope.launch {
                                db.deleteCheck(check.id)
                                loadChecks()
                                android.widget.Toast.makeText(this@CheckListActivity, "✅ چک حذف شد", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("لغو", null)
                        .show()
                }
            ).apply {
                submitList(checks)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadChecks()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
