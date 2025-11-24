package com.persianai.assistant.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.persianai.assistant.adapters.TransactionAdapter
import com.persianai.assistant.data.AccountingDB
import com.persianai.assistant.data.TransactionType
import com.persianai.assistant.databinding.ActivityIncomeListBinding
import kotlinx.coroutines.launch

class IncomeListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncomeListBinding
    private lateinit var db: AccountingDB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIncomeListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "درآمدها"

        db = AccountingDB(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        loadIncomes()
    }

    private fun loadIncomes() {
        lifecycleScope.launch {
            val incomes = db.getAllTransactions(TransactionType.INCOME)
            binding.recyclerView.adapter = TransactionAdapter(incomes.toMutableList()) { transaction ->
                // Handle delete click
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
