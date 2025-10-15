package com.persianai.assistant.activities

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.persianai.assistant.R
import com.persianai.assistant.databinding.ActivityAccountingBinding
import com.persianai.assistant.data.AccountingDB
import com.persianai.assistant.data.Transaction
import com.persianai.assistant.data.TransactionType
import com.persianai.assistant.utils.SharedDataManager
import com.persianai.assistant.ai.ContextualAIAssistant
import com.persianai.assistant.adapters.TransactionAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import android.widget.Toast

class AccountingActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAccountingBinding
    private lateinit var db: AccountingDB
    private lateinit var aiAssistant: ContextualAIAssistant
    private lateinit var transactionAdapter: TransactionAdapter
    private val transactions = mutableListOf<Transaction>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "💰 حسابداری"
        
        db = AccountingDB(this)
        aiAssistant = ContextualAIAssistant(this)
        
        setupUI()
        setupRecyclerView()
        updateBalance()
        loadTransactions()
    }
    
    private fun setupUI() {
        binding.addIncomeButton.setOnClickListener { showAddDialog(TransactionType.INCOME) }
        binding.addExpenseButton.setOnClickListener { showAddDialog(TransactionType.EXPENSE) }
        binding.addCheckButton.setOnClickListener { showCheckDialog() }
        binding.addInstallmentButton.setOnClickListener { showInstallmentDialog() }
        binding.aiChatButton.setOnClickListener { showAIChat() }
    }
    
    private fun showAddTransactionDialog() {
        // Default to expense
        showAddDialog(TransactionType.EXPENSE)
    }
    
    private fun showAddDialog(type: TransactionType) {
        val view = layoutInflater.inflate(R.layout.dialog_add_transaction, null)
        val amountField = view.findViewById<TextInputEditText>(R.id.amountField)
        val categoryField = view.findViewById<TextInputEditText>(R.id.categoryField)
        val descField = view.findViewById<TextInputEditText>(R.id.descriptionField)
        
        MaterialAlertDialogBuilder(this)
            .setTitle(if (type == TransactionType.INCOME) "➥ درآمد جدید" else "➖ هزینه جدید")
            .setView(view)
            .setPositiveButton("ثبت") { _, _ ->
                val amount = amountField.text.toString().toDoubleOrNull() ?: 0.0
                val category = categoryField.text.toString()
                val desc = descField.text.toString()
                
                if (amount > 0) {
                    lifecycleScope.launch {
                        val transaction = Transaction(
                            id = 0,
                            type = type,
                            amount = amount,
                            category = category,
                            description = desc,
                            date = System.currentTimeMillis()
                        )
                        db.addTransaction(transaction)
                        updateBalance()
                    }
                } else {
                    Toast.makeText(this, "مبلغ نامعتبر است", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun showCheckDialog() {
        // Similar dialog for checks
    }
    
    private fun showInstallmentDialog() {
        // Dialog for installments
    }
    
    private fun showAIChat() {
        val input = android.widget.EditText(this).apply {
            hint = "دستور خود را بنویسید (مثل: درآمد 500000 تومان ثبت کن)"
            setPadding(32, 32, 32, 32)
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("🤖 دستیار مالی هوشمند")
            .setView(input)
            .setPositiveButton("اجرا") { _, _ ->
                val userMessage = input.text.toString()
                if (userMessage.isNotEmpty()) {
                    lifecycleScope.launch {
                        try {
                            val response = aiAssistant.processAccountingCommand(userMessage, db)
                            
                            runOnUiThread {
                                MaterialAlertDialogBuilder(this@AccountingActivity)
                                    .setTitle(if (response.success) "✅ انجام شد" else "⚠️ خطا")
                                    .setMessage(response.message)
                                    .setPositiveButton("باشه") { _, _ ->
                                        if (response.success && response.action == "add_transaction") {
                                            updateBalance()
                                        }
                                    }
                                    .show()
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                Toast.makeText(this@AccountingActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter(transactions) { transaction ->
            // حذف تراکنش
            MaterialAlertDialogBuilder(this)
                .setTitle("❌ حذف تراکنش")
                .setMessage("آیا از حذف این تراکنش مطمئن هستید؟")
                .setPositiveButton("حذف") { _, _ ->
                    lifecycleScope.launch {
                        db.deleteTransaction(transaction.id)
                        transactionAdapter.removeItem(transaction)
                        updateBalance()
                        loadTransactions()
                        Toast.makeText(this@AccountingActivity, "✅ تراکنش حذف شد", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("لغو", null)
                .show()
        }
        
        binding.transactionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AccountingActivity)
            adapter = transactionAdapter
        }
    }
    
    private fun loadTransactions() {
        lifecycleScope.launch {
            val allTransactions = db.getAllTransactions()
            transactions.clear()
            transactions.addAll(allTransactions)
            transactionAdapter.notifyDataSetChanged()
        }
    }
    
    private fun updateBalance() {
        lifecycleScope.launch {
            val balance = db.getBalance()
            binding.totalBalanceText.text = String.format("%,.0f تومان", balance)
            
            // ذخیره در SharedDataManager
            SharedDataManager.saveTotalBalance(this@AccountingActivity, balance)
            
            // ذخیره هزینه و درآمد ماهانه
            val expenses = db.getMonthlyExpenses()
            val income = db.getMonthlyIncome()
            SharedDataManager.saveMonthlyExpenses(this@AccountingActivity, expenses)
            SharedDataManager.saveMonthlyIncome(this@AccountingActivity, income)
            
            android.util.Log.d("AccountingActivity", "💾 داده‌ها به SharedDataManager sync شد: Balance=$balance, Expenses=$expenses, Income=$income")
        }
    }
}
