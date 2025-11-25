package com.persianai.assistant.activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.persianai.assistant.R
import com.persianai.assistant.adapters.CheckAdapter
import com.persianai.assistant.adapters.InstallmentAdapter
import com.persianai.assistant.databinding.ActivityProfessionalAccountingBinding
import com.persianai.assistant.finance.CheckManager
import com.persianai.assistant.finance.InstallmentManager
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
        // Note: The models used by adapters (Check, Installment) might need to be adjusted
        // if they are different from the ones in CheckManager/InstallmentManager.
        // Assuming they are compatible for now.
        checkAdapter = CheckAdapter { /* Handle check click */ }
        installmentAdapter = InstallmentAdapter { /* Handle installment click */ }

        binding.recyclerViewChecks.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewInstallments.layoutManager = LinearLayoutManager(this)
        // Setup for recyclerViewTransactions if needed
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                handleTabSelection(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun handleTabSelection(position: Int) {
        binding.recyclerViewTransactions.visibility = if (position == 0) View.VISIBLE else View.GONE
        binding.recyclerViewChecks.visibility = if (position == 1) View.VISIBLE else View.GONE
        binding.recyclerViewInstallments.visibility = if (position == 2) View.VISIBLE else View.GONE
        binding.reportsLayout.visibility = if (position == 3) View.VISIBLE else View.GONE

        when (position) {
            0 -> { /* Load Transactions */ }
            1 -> loadChecks()
            2 -> loadInstallments()
            3 -> { /* Load Reports */ }
        }
    }

    private fun updateAllData() {
        updateStatistics()
        handleTabSelection(0) // Load default tab
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
                // The Check model from CheckManager must be compatible with the Check model expected by CheckAdapter
                // This might require a mapping function if they are different.
                // checkAdapter.submitList(checks)
                binding.recyclerViewChecks.adapter = checkAdapter
            } catch (e: Exception) {
                Toast.makeText(this@ProfessionalAccountingActivity, "خطا در بارگذاری چک‌ها", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadInstallments() {
        lifecycleScope.launch {
            try {
                val installments = accountingManager.getAllInstallments()
                // Similar to checks, a mapping might be needed here.
                // installmentAdapter.submitList(installments)
                binding.recyclerViewInstallments.adapter = installmentAdapter
            } catch (e: Exception) {
                Toast.makeText(this@ProfessionalAccountingActivity, "خطا در بارگذاری اقساط", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
