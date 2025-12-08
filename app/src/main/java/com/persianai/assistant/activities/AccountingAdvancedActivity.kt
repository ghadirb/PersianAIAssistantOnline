package com.persianai.assistant.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.persianai.assistant.databinding.ActivityAccountingAdvancedBinding
import com.persianai.assistant.finance.FinanceManager
import java.util.Calendar
import kotlin.math.abs

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
        
        binding.btnExpenseInsights.setOnClickListener {
            showExpenseInsights()
        }
        
        binding.btnMonthCompare.setOnClickListener {
            showMonthCompare()
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
    
    private fun showExpenseInsights() {
        val financeManager = FinanceManager(this)
        val transactions = financeManager.getAllTransactions().filter { it.type == "expense" }
        if (transactions.isEmpty()) {
            android.widget.Toast.makeText(this, "هزینه‌ای ثبت نشده است", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        val now = Calendar.getInstance()
        val currentMonth = now.get(Calendar.MONTH)
        val currentYear = now.get(Calendar.YEAR)
        
        var monthExpense = 0.0
        val categoryMap = mutableMapOf<String, Double>()
        val cal = Calendar.getInstance()
        
        transactions.forEach { t ->
            cal.timeInMillis = t.date
            if (cal.get(Calendar.MONTH) == currentMonth && cal.get(Calendar.YEAR) == currentYear) {
                monthExpense += t.amount
                categoryMap[t.category] = (categoryMap[t.category] ?: 0.0) + t.amount
            }
        }
        
        val daysPassed = now.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val dailyAvg = monthExpense / daysPassed
        val topCategory = categoryMap.maxByOrNull { it.value }
        
        // هفته جاری و هفته قبل برای هشدار مصرف
        val millisInDay = 24 * 60 * 60 * 1000L
        val currentWeekStart = now.timeInMillis - (6 * millisInDay)
        val prevWeekStart = currentWeekStart - (7 * millisInDay)
        val prevWeekEnd = currentWeekStart - 1
        
        var currentWeekExpense = 0.0
        var prevWeekExpense = 0.0
        transactions.forEach { t ->
            if (t.date >= currentWeekStart) {
                currentWeekExpense += t.amount
            } else if (t.date in prevWeekStart..prevWeekEnd) {
                prevWeekExpense += t.amount
            }
        }
        
        val builder = StringBuilder()
        builder.appendLine("مجموع هزینه‌های ماه جاری: ${formatAmount(monthExpense)}")
        builder.appendLine("میانگین روزانه ماه جاری: ${formatAmount(dailyAvg)}")
        if (topCategory != null) {
            val share = if (monthExpense > 0) (topCategory.value / monthExpense * 100).toInt() else 0
            builder.appendLine("بیشترین دسته: ${topCategory.key} (${formatAmount(topCategory.value)}، ${share}٪)")
        }
        if (prevWeekExpense > 0) {
            val change = ((currentWeekExpense - prevWeekExpense) / prevWeekExpense) * 100
            val sign = if (change >= 0) "⬆️" else "⬇️"
            builder.appendLine("هفته جاری نسبت به هفته قبل: $sign ${change.toInt()}٪")
        } else {
            builder.appendLine("برای هشدار هفتگی، داده‌ی هفته قبل کافی نیست.")
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("📈 نمودار و هشدار هزینه")
            .setMessage(builder.toString())
            .setPositiveButton("باشه", null)
            .show()
    }
    
    private fun showMonthCompare() {
        val financeManager = FinanceManager(this)
        val now = Calendar.getInstance()
        val currentMonthIndex = now.get(Calendar.MONTH) + 1 // 1-12
        val currentYear = now.get(Calendar.YEAR)
        
        val prevCalendar = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        val prevMonthIndex = prevCalendar.get(Calendar.MONTH) + 1
        val prevYear = prevCalendar.get(Calendar.YEAR)
        
        val current = financeManager.getMonthlyReport(currentYear, currentMonthIndex)
        val prev = financeManager.getMonthlyReport(prevYear, prevMonthIndex)
        
        val incomeDiff = current.first - prev.first
        val expenseDiff = current.second - prev.second
        val balanceCurrent = current.first - current.second
        val balancePrev = prev.first - prev.second
        
        val builder = StringBuilder()
        builder.appendLine("ماه جاری: درآمد ${formatAmount(current.first)} | هزینه ${formatAmount(current.second)} | تراز ${formatAmount(balanceCurrent)}")
        builder.appendLine("ماه قبل: درآمد ${formatAmount(prev.first)} | هزینه ${formatAmount(prev.second)} | تراز ${formatAmount(balancePrev)}")
        
        if (prev.first > 0) {
            val incChange = ((current.first - prev.first) / prev.first) * 100
            builder.appendLine("تغییر درآمد: ${formatPercent(incChange)}")
        } else {
            builder.appendLine("تغییر درآمد: داده کافی برای ماه قبل نیست.")
        }
        
        if (prev.second > 0) {
            val expChange = ((current.second - prev.second) / prev.second) * 100
            builder.appendLine("تغییر هزینه: ${formatPercent(expChange)}")
        } else {
            builder.appendLine("تغییر هزینه: داده کافی برای ماه قبل نیست.")
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("📊 مقایسه ماه جاری و قبل")
            .setMessage(builder.toString())
            .setPositiveButton("باشه", null)
            .show()
    }
    
    private fun formatAmount(amount: Double): String {
        return "${String.format("%,.0f", amount)} تومان"
    }
    
    private fun formatPercent(value: Double): String {
        val sign = if (value >= 0) "⬆️" else "⬇️"
        return "$sign ${abs(value).toInt()}٪"
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
