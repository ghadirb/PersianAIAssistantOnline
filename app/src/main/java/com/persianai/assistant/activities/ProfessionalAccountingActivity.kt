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
        setupFabs()
    }

    private fun setupRecyclerViews() {
        // The adapters must be updated to use the correct models from Manager classes
        checkAdapter = CheckAdapter { /* TODO: Open check details/edit dialog */ }
        installmentAdapter = InstallmentAdapter { /* TODO: Open installment details/edit dialog */ }

        binding.recyclerViewChecks.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewChecks.adapter = checkAdapter

        binding.recyclerViewInstallments.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewInstallments.adapter = installmentAdapter
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

    private fun setupFabs() {
        binding.fabAddCheck.setOnClickListener {
            // TODO: Open dialog to add a new check
            Toast.makeText(this, "Add Check Clicked", Toast.LENGTH_SHORT).show()
        }
        binding.fabAddInstallment.setOnClickListener {
            // TODO: Open dialog to add a new installment
            Toast.makeText(this, "Add Installment Clicked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleTabSelection(position: Int) {
        // Hide all RecyclerViews and FABs first
        binding.recyclerViewTransactions.visibility = View.GONE
        binding.recyclerViewChecks.visibility = View.GONE
        binding.recyclerViewInstallments.visibility = View.GONE
        binding.reportsLayout.visibility = View.GONE
        binding.fabAddTransaction.visibility = View.GONE
        binding.fabAddCheck.visibility = View.GONE
        binding.fabAddInstallment.visibility = View.GONE

        when (position) {
            0 -> { // Transactions
                binding.recyclerViewTransactions.visibility = View.VISIBLE
                binding.fabAddTransaction.visibility = View.VISIBLE
            }
            1 -> { // Checks
                binding.recyclerViewChecks.visibility = View.VISIBLE
                binding.fabAddCheck.visibility = View.VISIBLE
                loadChecks()
            }
            2 -> { // Installments
                binding.recyclerViewInstallments.visibility = View.VISIBLE
                binding.fabAddInstallment.visibility = View.VISIBLE
                loadInstallments()
            }
            3 -> { // Reports
                binding.reportsLayout.visibility = View.VISIBLE
            }
        }
    }

    private fun updateAllData() {
        updateStatistics()
        handleTabSelection(binding.tabLayout.selectedTabPosition) // Load initial tab
    }

    private fun updateStatistics() {
        lifecycleScope.launch {
            val report = accountingManager.getFinancialReport()
            binding.totalIncomeText.text = String.format("%,.0f", report.totalIncome)
            binding.totalExpenseText.text = String.format("%,.0f", report.totalExpense)
            binding.balanceText.text = String.format("%,.0f", report.balance)
            binding.balanceText.setTextColor(
                when {
                    report.balance > 0 -> getColor(R.color.income_green)
                    report.balance < 0 -> getColor(R.color.expense_red)
                    else -> getColor(R.color.neutral_gray)
                }
            )
        }
    }

    private fun loadChecks() {
        lifecycleScope.launch {
            val checks = accountingManager.getAllChecks()
            // This will cause a type mismatch. The adapter needs to be fixed.
            // checkAdapter.submitList(checks)
        }
    }

    private fun loadInstallments() {
        lifecycleScope.launch {
            val installments = accountingManager.getAllInstallments()
            // This will cause a type mismatch. The adapter needs to be fixed.
            // installmentAdapter.submitList(installments)
        }
    }

    override fun onResume() {
        super.onResume()
        updateAllData()
    }
}
