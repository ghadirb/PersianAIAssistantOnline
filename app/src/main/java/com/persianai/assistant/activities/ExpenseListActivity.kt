package com.persianai.assistant.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.persianai.assistant.adapters.TransactionAdapter
import com.persianai.assistant.data.AccountingDB
import com.persianai.assistant.data.TransactionType
import com.persianai.assistant.databinding.ActivityExpenseListBinding
import kotlinx.coroutines.launch

class ExpenseListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExpenseListBinding
    private lateinit var db: AccountingDB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpenseListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "هزینه‌ها"

        db = AccountingDB(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        loadExpenses()
    }

    private fun loadExpenses() {
        lifecycleScope.launch {
            val expenses = db.getAllTransactions(TransactionType.EXPENSE)
            binding.recyclerView.adapter = TransactionAdapter(expenses.toMutableList()) { transaction ->
                // Handle delete click
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@ExpenseListActivity)
                    .setTitle("❌ حذف هزینه")
                    .setMessage("آیا از حذف این هزینه مطمئن هستید؟")
                    .setPositiveButton("حذف") { _, _ ->
                        lifecycleScope.launch {
                            db.deleteTransaction(transaction.id)
                            loadExpenses()
                            android.widget.Toast.makeText(this@ExpenseListActivity, "✅ هزینه حذف شد", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("لغو", null)
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadExpenses()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
