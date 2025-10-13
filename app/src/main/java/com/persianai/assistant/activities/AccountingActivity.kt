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
import com.persianai.assistant.data.*
import kotlinx.coroutines.launch

class AccountingActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAccountingBinding
    private lateinit var db: AccountingDB
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "💰 حسابداری"
        
        db = AccountingDB(this)
        
        setupUI()
        updateBalance()
    }
    
    private fun setupUI() {
        // دکمه اضافه کردن تراکنش
        binding.addTransactionFab?.setOnClickListener {
            showAddTransactionDialog()
        }
        
        // دکمه چت AI
        binding.chatFab?.setOnClickListener {
            showAIChat()
        }
        binding.addIncomeButton.setOnClickListener { showAddDialog(TransactionType.INCOME) }
        binding.addExpenseButton.setOnClickListener { showAddDialog(TransactionType.EXPENSE) }
        binding.addCheckButton.setOnClickListener { showCheckDialog() }
        binding.addInstallmentButton.setOnClickListener { showInstallmentDialog() }
        binding.aiChatButton.setOnClickListener { showAIChat() }
    }
    
    private fun showAddDialog(type: TransactionType) {
        val view = layoutInflater.inflate(R.layout.dialog_add_transaction, null)
        val amountField = view.findViewById<TextInputEditText>(R.id.amountField)
        val categoryField = view.findViewById<TextInputEditText>(R.id.categoryField)
        val descField = view.findViewById<TextInputEditText>(R.id.descriptionField)
        
        MaterialAlertDialogBuilder(this)
            .setTitle(if (type == TransactionType.INCOME) "➕ درآمد جدید" else "➖ هزینه جدید")
            .setView(view)
            .setPositiveButton("ثبت") { _, _ ->
                val amount = amountField.text.toString().toDoubleOrNull() ?: 0.0
                val category = categoryField.text.toString()
                val desc = descField.text.toString()
                
                lifecycleScope.launch {
                    db.addTransaction(Transaction(type = type, amount = amount, category = category, description = desc))
                    updateBalance()
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
            hint = "سوال خود را بپرسید (مثلا: خرج این ماه چقدر شد؟)"
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("💰 دستیار مالی AI")
            .setView(input)
            .setPositiveButton("ارسال") { _, _ ->
                val question = input.text.toString()
                val expenses = db.getMonthlyExpenses()
                val income = db.getMonthlyIncome()
                val response = "💸 هزینه این ماه: ${expenses}\n💰 درآمد: ${income}"
                
                MaterialAlertDialogBuilder(this)
                    .setTitle("پاسخ AI")
                    .setMessage(response)
                    .setPositiveButton("باشه", null)
                    .show()
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun updateBalance() {
        lifecycleScope.launch {
            val balance = db.getBalance()
            binding.totalBalanceText.text = String.format("%,.0f تومان", balance)
        }
    }
}
