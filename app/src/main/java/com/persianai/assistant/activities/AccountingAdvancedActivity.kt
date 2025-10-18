package com.persianai.assistant.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.persianai.assistant.databinding.ActivityAccountingAdvancedBinding
import com.persianai.assistant.data.*
import kotlinx.coroutines.launch
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.EditText
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.persianai.assistant.adapters.*
import java.text.SimpleDateFormat
import java.util.*

class AccountingAdvancedActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAccountingAdvancedBinding
    private lateinit var db: AccountingDB
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountingAdvancedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "💼 حسابداری پیشرفته"
        
        db = AccountingDB(this)
        
        setupTabs()
        setupButtons()
    }
    
    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("چک‌ها"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("اقساط"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("گزارشات"))
        
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> loadChecks()
                    1 -> loadInstallments()
                    2 -> loadReports()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        
        loadChecks()
    }
    
    private fun setupButtons() {
        binding.addButton.setOnClickListener {
            when (binding.tabLayout.selectedTabPosition) {
                0 -> showAddCheckDialog()
                1 -> showAddInstallmentDialog()
                2 -> showReportOptionsDialog()
            }
        }
    }
    
    private fun loadChecks() {
        lifecycleScope.launch {
            val checks = db.getAllChecks()
            // نمایش در RecyclerView
            Toast.makeText(this@AccountingAdvancedActivity, "${checks.size} چک", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadInstallments() {
        lifecycleScope.launch {
            val installments = db.getAllInstallments()
            Toast.makeText(this@AccountingAdvancedActivity, "${installments.size} قسط", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadReports() {
        Toast.makeText(this, "گزارشات", Toast.LENGTH_SHORT).show()
    }
    
    private fun showAddCheckDialog() {
        Toast.makeText(this, "افزودن چک", Toast.LENGTH_SHORT).show()
    }
    
    private fun showAddInstallmentDialog() {
        Toast.makeText(this, "افزودن قسط", Toast.LENGTH_SHORT).show()
    }
    
    private fun showReportOptionsDialog() {
        Toast.makeText(this, "گزینه‌های گزارش", Toast.LENGTH_SHORT).show()
    }
}
