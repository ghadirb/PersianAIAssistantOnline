package com.persianai.assistant.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.persianai.assistant.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * مدیر حسابداری حرفه‌ای برای مدیریت تراکنش‌ها، چک‌ها و اقساط
 */
class AccountingManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("accounting_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    
    // کلیدهای SharedPreferences
    companion object {
        private const val TRANSACTIONS_KEY = "financial_transactions"
        private const val CHECKS_KEY = "checks"
        private const val INSTALLMENTS_KEY = "installments"
        private const val BUDGETS_KEY = "budgets"
        private const val ACCOUNTS_KEY = "bank_accounts"
        private const val REMINDERS_KEY = "financial_reminders"
    }
    
    // مدیریت تراکنش‌ها
    suspend fun addTransaction(transaction: FinancialTransaction): Boolean = withContext(Dispatchers.IO) {
        try {
            val transactions = getAllTransactions().toMutableList()
            val newTransaction = transaction.copy(id = generateTransactionId())
            transactions.add(newTransaction)
            saveTransactions(transactions)
            
            // ایجاد یادآوری اگر تراکنش تکراری باشد
            if (newTransaction.isRecurring) {
                createRecurringReminder(newTransaction)
            }
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun updateTransaction(transaction: FinancialTransaction): Boolean = withContext(Dispatchers.IO) {
        try {
            val transactions = getAllTransactions().toMutableList()
            val index = transactions.indexOfFirst { it.id == transaction.id }
            if (index != -1) {
                transactions[index] = transaction
                saveTransactions(transactions)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun deleteTransaction(transactionId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val transactions = getAllTransactions().toMutableList()
            transactions.removeAll { it.id == transactionId }
            saveTransactions(transactions)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun getAllTransactions(): List<FinancialTransaction> {
        val json = prefs.getString(TRANSACTIONS_KEY, null)
        return if (json != null) {
            val type = object : TypeToken<List<FinancialTransaction>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    fun getTransactionsByCategory(category: TransactionCategory): List<FinancialTransaction> {
        return getAllTransactions().filter { it.category == category }
    }
    
    fun getTransactionsByDateRange(startDate: Date, endDate: Date): List<FinancialTransaction> {
        return getAllTransactions().filter { 
            it.date >= startDate && it.date <= endDate 
        }
    }
    
    // مدیریت چک‌ها
    suspend fun addCheck(check: Check): Boolean = withContext(Dispatchers.IO) {
        try {
            val checks = getAllChecks().toMutableList()
            val newCheck = check.copy(id = generateCheckId())
            checks.add(newCheck)
            saveChecks(checks)
            
            // ایجاد یادآوری برای سررسید چک
            createCheckReminder(newCheck)
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun updateCheck(check: Check): Boolean = withContext(Dispatchers.IO) {
        try {
            val checks = getAllChecks().toMutableList()
            val index = checks.indexOfFirst { it.id == check.id }
            if (index != -1) {
                checks[index] = check
                saveChecks(checks)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun deleteCheck(checkId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val checks = getAllChecks().toMutableList()
            checks.removeAll { it.id == checkId }
            saveChecks(checks)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun getAllChecks(): List<Check> {
        val json = prefs.getString(CHECKS_KEY, null)
        return if (json != null) {
            val type = object : TypeToken<List<Check>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    fun getPendingChecks(): List<Check> {
        return getAllChecks().filter { it.status == CheckStatus.PENDING }
    }
    
    // مدیریت اقساط
    suspend fun addInstallment(installment: Installment): Boolean = withContext(Dispatchers.IO) {
        try {
            val installments = getAllInstallments().toMutableList()
            val newInstallment = installment.copy(id = generateInstallmentId())
            installments.add(newInstallment)
            saveInstallments(installments)
            
            // ایجاد یادآوری برای اقساط
            createInstallmentReminder(newInstallment)
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun updateInstallment(installment: Installment): Boolean = withContext(Dispatchers.IO) {
        try {
            val installments = getAllInstallments().toMutableList()
            val index = installments.indexOfFirst { it.id == installment.id }
            if (index != -1) {
                installments[index] = installment
                saveInstallments(installments)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun payInstallment(installmentId: Long, amount: Double): Boolean = withContext(Dispatchers.IO) {
        try {
            val installments = getAllInstallments().toMutableList()
            val index = installments.indexOfFirst { it.id == installmentId }
            if (index != -1) {
                val installment = installments[index]
                val updatedInstallment = installment.copy(
                    paidAmount = installment.paidAmount + amount,
                    remainingAmount = installment.remainingAmount - amount,
                    paidInstallments = installment.paidInstallments + 1,
                    status = if (installment.remainingAmount - amount <= 0) InstallmentStatus.COMPLETED else installment.status
                )
                installments[index] = updatedInstallment
                saveInstallments(installments)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun deleteInstallment(installmentId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val installments = getAllInstallments().toMutableList()
            installments.removeAll { it.id == installmentId }
            saveInstallments(installments)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun getAllInstallments(): List<Installment> {
        val json = prefs.getString(INSTALLMENTS_KEY, null)
        return if (json != null) {
            val type = object : TypeToken<List<Installment>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    fun getActiveInstallments(): List<Installment> {
        return getAllInstallments().filter { it.status == InstallmentStatus.ACTIVE }
    }
    
    fun getDelayedInstallments(): List<Installment> {
        val today = Date()
        return getAllInstallments().filter { 
            it.status == InstallmentStatus.ACTIVE && it.nextPaymentDate < today 
        }
    }
    
    // آمار و گزارش‌ها
    suspend fun getFinancialStatistics(): FinancialStatistics = withContext(Dispatchers.IO) {
        val transactions = getAllTransactions()
        val currentMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.time
        
        val monthlyTransactions = transactions.filter { it.date >= currentMonth }
        
        val totalIncome = monthlyTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = monthlyTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val balance = totalIncome - totalExpense
        
        FinancialStatistics(
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            balance = balance,
            transactionCount = monthlyTransactions.size,
            averageExpense = if (monthlyTransactions.isNotEmpty()) totalExpense / monthlyTransactions.size else 0.0
        )
    }
    
    suspend fun getMonthlyReport(): FinancialReport = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val startDate = calendar.time
        
        calendar.add(Calendar.MONTH, 1)
        calendar.add(Calendar.DAY_OF_MONTH, -1)
        val endDate = calendar.time
        
        generateReport(startDate, endDate, ReportPeriod.MONTHLY)
    }
    
    suspend fun getYearlyReport(): FinancialReport = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_YEAR, 1)
        val startDate = calendar.time
        
        calendar.add(Calendar.YEAR, 1)
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val endDate = calendar.time
        
        generateReport(startDate, endDate, ReportPeriod.YEARLY)
    }
    
    // export و پشتیبان
    suspend fun exportToCSV(): String = withContext(Dispatchers.IO) {
        val transactions = getAllTransactions()
        val file = File(context.getExternalFilesDir(null), "financial_export.csv")
        
        FileWriter(file).use { writer ->
            // نوشتن هدر
            writer.appendLine("تاریخ,نوع,دسته‌بندی,مبلغ,توضیحات")
            
            // نوشتن تراکنش‌ها
            transactions.forEach { transaction ->
                val line = "${dateFormat.format(transaction.date)}," +
                        "${transaction.type}," +
                        "${transaction.category}," +
                        "${transaction.amount}," +
                        "\"${transaction.description}\""
                writer.appendLine(line)
            }
        }
        
        file.absolutePath
    }
    
    suspend fun createBackup(): String = withContext(Dispatchers.IO) {
        val backupData = AccountingBackup(
            transactions = getAllTransactions(),
            checks = getAllChecks(),
            installments = getAllInstallments(),
            exportDate = Date()
        )
        
        val json = gson.toJson(backupData)
        val file = File(context.getExternalFilesDir(null), "accounting_backup.json")
        
        file.writeText(json)
        file.absolutePath
    }
    
    // توابع کمکی
    private fun saveTransactions(transactions: List<FinancialTransaction>) {
        val json = gson.toJson(transactions)
        prefs.edit().putString(TRANSACTIONS_KEY, json).apply()
    }
    
    private fun saveChecks(checks: List<Check>) {
        val json = gson.toJson(checks)
        prefs.edit().putString(CHECKS_KEY, json).apply()
    }
    
    private fun saveInstallments(installments: List<Installment>) {
        val json = gson.toJson(installments)
        prefs.edit().putString(INSTALLMENTS_KEY, json).apply()
    }
    
    private fun generateTransactionId(): Long {
        return System.currentTimeMillis()
    }
    
    private fun generateCheckId(): Long {
        return System.currentTimeMillis() + 1
    }
    
    private fun generateInstallmentId(): Long {
        return System.currentTimeMillis() + 2
    }
    
    private fun generateReport(startDate: Date, endDate: Date, period: ReportPeriod): FinancialReport {
        val transactions = getTransactionsByDateRange(startDate, endDate)
        
        val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val netIncome = totalIncome - totalExpense
        
        val categoryBreakdown = transactions.groupBy { it.category }
            .mapValues { it.value.sumOf { transaction -> transaction.amount } }
        
        val topExpenses = transactions.filter { it.type == TransactionType.EXPENSE }
            .sortedByDescending { it.amount }.take(10)
        
        val topIncome = transactions.filter { it.type == TransactionType.INCOME }
            .sortedByDescending { it.amount }.take(10)
        
        return FinancialReport(
            period = period,
            startDate = startDate,
            endDate = endDate,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            netIncome = netIncome,
            categoryBreakdown = categoryBreakdown,
            monthlyTrend = emptyList(), // TODO: پیاده‌سازی ترند ماهانه
            topExpenses = topExpenses,
            topIncome = topIncome
        )
    }
    
    private fun createRecurringReminder(transaction: FinancialTransaction) {
        // TODO: پیاده‌سازی یادآوری تراکنش تکراری
    }
    
    private fun createCheckReminder(check: Check) {
        // TODO: پیاده‌سازی یادآوری چک
    }
    
    private fun createInstallmentReminder(installment: Installment) {
        // TODO: پیاده‌سازی یادآوری قسط
    }
}

// مدل‌های کمکی
data class FinancialStatistics(
    val totalIncome: Double,
    val totalExpense: Double,
    val balance: Double,
    val transactionCount: Int,
    val averageExpense: Double
)

data class AccountingBackup(
    val transactions: List<FinancialTransaction>,
    val checks: List<Check>,
    val installments: List<Installment>,
    val exportDate: Date
)
