package com.persianai.assistant.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.persianai.assistant.databinding.ActivityAccountingAdvancedBinding

class AccountingAdvancedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountingAdvancedBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountingAdvancedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "💼 حسابداری پیشرفته"

        binding.btnIncomes.setOnClickListener {
            startActivity(Intent(this, IncomeListActivity::class.java))
        }

        binding.btnExpenses.setOnClickListener {
            startActivity(Intent(this, ExpenseListActivity::class.java))
        }

        binding.btnChecks.setOnClickListener {
            startActivity(Intent(this, CheckListActivity::class.java))
        }

        binding.btnInstallments.setOnClickListener {
            startActivity(Intent(this, InstallmentListActivity::class.java))
        }

        binding.chatFab.setOnClickListener {
            startActivity(Intent(this, AccountingChatActivity::class.java))
        }
        
        binding.btnMonthlyBalance.setOnClickListener {
            showMonthlyBalance()
        }
        
        binding.btnYearlyBalance.setOnClickListener {
            showYearlyBalance()
        }
        
        binding.btnAddIncomeManual.setOnClickListener {
            showManualInputDialog("درآمد", "income")
        }
        
        binding.btnAddExpenseManual.setOnClickListener {
            showManualInputDialog("هزینه", "expense")
        }
        
        binding.btnAddCheckManual.setOnClickListener {
            showManualInputDialog("چک", "check")
        }
        
        binding.btnAddInstallmentManual.setOnClickListener {
            showManualInputDialog("قسط", "installment")
        }
        
        updateStats()
    }
    
    override fun onResume() {
        super.onResume()
        updateStats()
    }
    
    private fun updateStats() {
        val financeManager = com.persianai.assistant.finance.FinanceManager(this)
        val checkManager = com.persianai.assistant.finance.CheckManager(this)
        val installmentManager = com.persianai.assistant.finance.InstallmentManager(this)
        
        // درآمد و هزینه
        val transactions = financeManager.getAllTransactions()
        var totalIncome = 0.0
        var totalExpense = 0.0
        for (transaction in transactions) {
            if (transaction.type == "income") totalIncome += transaction.amount
            else if (transaction.type == "expense") totalExpense += transaction.amount
        }
        
        // چک‌ها
        val checks = checkManager.getAllChecks()
        var totalChecks = 0.0
        for (check in checks) {
            totalChecks += check.amount
        }
        
        // اقساط
        val installments = installmentManager.getAllInstallments()
        var totalInstallments = 0.0
        for (installment in installments) {
            totalInstallments += installment.totalAmount
        }
        
        // نمایش در UI
        binding.incomeAmount.text = "💰 ${String.format("%,.0f", totalIncome)} تومان"
        binding.expenseAmount.text = "💸 ${String.format("%,.0f", totalExpense)} تومان"
        binding.checksAmount.text = "📋 ${String.format("%,.0f", totalChecks)} تومان"
        binding.installmentsAmount.text = "💳 ${String.format("%,.0f", totalInstallments)} تومان"
    }
    
    private fun showMonthlyBalance() {
        val financeManager = com.persianai.assistant.finance.FinanceManager(this)
        val transactions = financeManager.getAllTransactions()
        val currentMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        
        var income = 0.0
        var expense = 0.0
        
        for (transaction in transactions) {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = transaction.date
            if (cal.get(java.util.Calendar.MONTH) == currentMonth && cal.get(java.util.Calendar.YEAR) == currentYear) {
                if (transaction.type == "income") income += transaction.amount
                else if (transaction.type == "expense") expense += transaction.amount
            }
        }
        
        val balance = income - expense
        val message = "📅 تراز ماهانه:\n💰 درآمد: ${String.format("%,.0f", income)} تومان\n💸 هزینه: ${String.format("%,.0f", expense)} تومان\n📊 تراز: ${String.format("%,.0f", balance)} تومان"
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }
    
    private fun showYearlyBalance() {
        val financeManager = com.persianai.assistant.finance.FinanceManager(this)
        val transactions = financeManager.getAllTransactions()
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        
        var income = 0.0
        var expense = 0.0
        
        for (transaction in transactions) {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = transaction.date
            if (cal.get(java.util.Calendar.YEAR) == currentYear) {
                if (transaction.type == "income") income += transaction.amount
                else if (transaction.type == "expense") expense += transaction.amount
            }
        }
        
        val balance = income - expense
        val message = "📊 تراز سالانه:\n💰 درآمد: ${String.format("%,.0f", income)} تومان\n💸 هزینه: ${String.format("%,.0f", expense)} تومان\n📊 تراز: ${String.format("%,.0f", balance)} تومان"
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }
    
    private fun showManualInputDialog(type: String, action: String) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("ورود دستی $type")
        
        val input = android.widget.EditText(this)
        input.hint = "مبلغ را وارد کنید"
        input.inputType = android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        builder.setView(input)
        
        builder.setPositiveButton("ثبت") { _, _ ->
            val amount = input.text.toString().toDoubleOrNull() ?: 0.0
            if (amount > 0) {
                val financeManager = com.persianai.assistant.finance.FinanceManager(this)
                when (action) {
                    "income" -> {
                        financeManager.addTransaction(amount, "income", "درآمد", "ورود دستی")
                        android.widget.Toast.makeText(this, "✅ درآمد ثبت شد", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    "expense" -> {
                        financeManager.addTransaction(amount, "expense", "هزینه", "ورود دستی")
                        android.widget.Toast.makeText(this, "✅ هزینه ثبت شد", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        builder.setNegativeButton("لغو", null)
        builder.show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
