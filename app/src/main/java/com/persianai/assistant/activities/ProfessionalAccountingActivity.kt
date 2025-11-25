package com.persianai.assistant.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.persianai.assistant.R
import com.persianai.assistant.adapters.CheckAdapter
import com.persianai.assistant.adapters.InstallmentAdapter
import com.persianai.assistant.databinding.ActivityProfessionalAccountingBinding
import com.persianai.assistant.utils.AccountingManager
import kotlinx.coroutines.launch

class ProfessionalAccountingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfessionalAccountingBinding
    private lateinit var accountingManager: AccountingManager
    private lateinit var checkAdapter: CheckAdapter
    private lateinit var installmentAdapter: InstallmentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfessionalAccountingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        accountingManager = AccountingManager(this)

        setupRecyclerViews()
        setupTabs()
        updateAllData()
    }

    private fun setupRecyclerViews() {
        checkAdapter = CheckAdapter(emptyList()) { /* Handle check click */ }
        installmentAdapter = InstallmentAdapter(emptyList()) { /* Handle installment click */ }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> loadChecks()
                    1 -> loadInstallments()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateAllData() {
        updateStatistics()
        loadChecks() // Load default tab
    }

    private fun updateStatistics() {
        lifecycleScope.launch {
            try {
                val report = accountingManager.getFinancialReport()
                binding.totalIncomeText.text = String.format("%,.0f تومان", report.totalIncome)
                binding.totalExpenseText.text = String.format("%,.0f تومان", report.totalExpense)
                binding.balanceText.text = String.format("%,.0f تومان", report.balance)

                binding.balanceText.setTextColor(
                    when {
                        report.balance > 0 -> getColor(R.color.income_green)
                        report.balance < 0 -> getColor(R.color.expense_red)
                        else -> getColor(R.color.neutral_gray)
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(this@ProfessionalAccountingActivity, "خطا در به‌روزرسانی آمار", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadChecks() {
        lifecycleScope.launch {
            try {
                val checks = accountingManager.getAllChecks()
                checkAdapter.updateData(checks)
                binding.recyclerView.adapter = checkAdapter
            } catch (e: Exception) {
                Toast.makeText(this@ProfessionalAccountingActivity, "خطا در بارگذاری چک‌ها", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadInstallments() {
        lifecycleScope.launch {
            try {
                val installments = accountingManager.getAllInstallments()
                installmentAdapter.updateData(installments)
                binding.recyclerView.adapter = installmentAdapter
            } catch (e: Exception) {
                Toast.makeText(this@ProfessionalAccountingActivity, "خطا در بارگذاری اقساط", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
