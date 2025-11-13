package com.persianai.assistant.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * مدیر دستیار بانکی و مالی هوشمند
 */
class BankingAssistantManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("banking_assistant", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    companion object {
        private const val TRANSACTIONS_KEY = "transactions"
        private const val ACCOUNTS_KEY = "accounts"
        private const val BUDGETS_KEY = "budgets"
        private const val BILLS_KEY = "bills"
    }
    
    @Serializable
    data class Transaction(
        val id: String,
        val amount: Double,
        val type: TransactionType,
        val category: TransactionCategory,
        val description: String,
        val date: String,
        val accountId: String,
        val tags: List<String> = emptyList(),
        val isRecurring: Boolean = false,
        val recurringPeriod: RecurringPeriod? = null
    )
    
    @Serializable
    data class Account(
        val id: String,
        val name: String,
        val type: AccountType,
        val balance: Double,
        val currency: String = "IRR",
        val bankName: String = "",
        val cardNumber: String = "",
        val isActive: Boolean = true
    )
    
    @Serializable
    data class Budget(
        val id: String,
        val category: TransactionCategory,
        val limit: Double,
        val spent: Double = 0.0,
        val period: BudgetPeriod,
        val startDate: String,
        val endDate: String,
        val isActive: Boolean = true
    )
    
    @Serializable
    data class Bill(
        val id: String,
        val title: String,
        val amount: Double,
        val dueDate: String,
        val category: BillCategory,
        val isPaid: Boolean = false,
        val isRecurring: Boolean = false,
        val recurringPeriod: RecurringPeriod? = null,
        val reminderDays: Int = 3
    )
    
    @Serializable
    enum class TransactionType {
        INCOME, // درآمد
        EXPENSE, // هزینه
        TRANSFER // انتقال
    }
    
    @Serializable
    enum class TransactionCategory {
        FOOD, // خوراک
        TRANSPORT, // حمل و نقل
        SHOPPING, // خرید
        ENTERTAINMENT, // سرگرمی
        HEALTH, // سلامتی
        EDUCATION, // آموزش
        BILLS, // قبوض
        SALARY, // حقوق
        INVESTMENT, // سرمایه‌گذاری
        OTHER // سایر
    }
    
    @Serializable
    enum class AccountType {
        CHECKING, // حساب جاری
        SAVINGS, // حساب پس‌انداز
        CREDIT_CARD, // کارت اعتباری
        CASH, // نقدی
        INVESTMENT // سرمایه‌گذاری
    }
    
    @Serializable
    enum class BudgetPeriod {
        WEEKLY, // هفتگی
        MONTHLY, // ماهانه
        YEARLY // سالانه
    }
    
    @Serializable
    enum class BillCategory {
        ELECTRICITY, // برق
        WATER, // آب
        GAS, // گاز
        PHONE, // تلفن
        INTERNET, // اینترنت
        RENT, // اجاره
        INSURANCE, // بیمه
        LOAN, // وام
        OTHER // سایر
    }
    
    @Serializable
    enum class RecurringPeriod {
        DAILY, // روزانه
        WEEKLY, // هفتگی
        MONTHLY, // ماهانه
        YEARLY // سالانه
    }
    
    /**
     * افزودن تراکنش جدید
     */
    fun addTransaction(transaction: Transaction) {
        try {
            val transactions = getTransactions().toMutableList()
            transactions.add(transaction)
            saveTransactions(transactions)
            
            // به‌روزرسانی حساب
            updateAccountBalance(transaction.accountId, transaction)
            
            // به‌روزرسانی بودجه
            updateBudgetSpending(transaction)
            
            Log.i("BankingAssistantManager", "✅ تراکنش جدید اضافه شد: ${transaction.description}")
            
        } catch (e: Exception) {
            Log.e("BankingAssistantManager", "❌ خطا در افزودن تراکنش: ${e.message}")
        }
    }
    
    /**
     * دریافت تمام تراکنش‌ها
     */
    fun getTransactions(): List<Transaction> {
        return try {
            val transactionsJson = prefs.getString(TRANSACTIONS_KEY, null)
            if (transactionsJson != null) {
                json.decodeFromString<List<Transaction>>(transactionsJson)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("BankingAssistantManager", "❌ خطا در دریافت تراکنش‌ها: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * دریافت تراکنش‌های بر اساس نوع
     */
    fun getTransactionsByType(type: TransactionType): List<Transaction> {
        return getTransactions().filter { it.type == type }
    }
    
    /**
     * دریافت تراکنش‌های بر اساس دسته‌بندی
     */
    fun getTransactionsByCategory(category: TransactionCategory): List<Transaction> {
        return getTransactions().filter { it.category == category }
    }
    
    /**
     * دریافت تراکنش‌های ماه جاری
     */
    fun getCurrentMonthTransactions(): List<Transaction> {
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        
        return getTransactions().filter { transaction ->
            val calendar = Calendar.getInstance()
            calendar.time = dateFormat.parse(transaction.date) ?: Date()
            calendar.get(Calendar.MONTH) == currentMonth && calendar.get(Calendar.YEAR) == currentYear
        }
    }
    
    /**
     * افزودن حساب جدید
     */
    fun addAccount(account: Account) {
        try {
            val accounts = getAccounts().toMutableList()
            accounts.add(account)
            saveAccounts(accounts)
            Log.i("BankingAssistantManager", "✅ حساب جدید اضافه شد: ${account.name}")
        } catch (e: Exception) {
            Log.e("BankingAssistantManager", "❌ خطا در افزودن حساب: ${e.message}")
        }
    }
    
    /**
     * دریافت تمام حساب‌ها
     */
    fun getAccounts(): List<Account> {
        return try {
            val accountsJson = prefs.getString(ACCOUNTS_KEY, null)
            if (accountsJson != null) {
                json.decodeFromString<List<Account>>(accountsJson)
            } else {
                createDefaultAccounts()
            }
        } catch (e: Exception) {
            Log.e("BankingAssistantManager", "❌ خطا در دریافت حساب‌ها: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * دریافت حساب‌های فعال
     */
    fun getActiveAccounts(): List<Account> {
        return getAccounts().filter { it.isActive }
    }
    
    /**
     * به‌روزرسانی موجودی حساب
     */
    private fun updateAccountBalance(accountId: String, transaction: Transaction) {
        try {
            val accounts = getAccounts().toMutableList()
            val index = accounts.indexOfFirst { it.id == accountId }
            if (index != -1) {
                val currentBalance = accounts[index].balance
                val newBalance = when (transaction.type) {
                    TransactionType.INCOME -> currentBalance + transaction.amount
                    TransactionType.EXPENSE -> currentBalance - transaction.amount
                    TransactionType.TRANSFER -> currentBalance // انتقال بین حساب‌ها نیاز به منطق جداگانه دارد
                }
                accounts[index] = accounts[index].copy(balance = newBalance)
                saveAccounts(accounts)
            }
        } catch (e: Exception) {
            Log.e("BankingAssistantManager", "❌ خطا در به‌روزرسانی موجودی حساب: ${e.message}")
        }
    }
    
    /**
     * افزودن بودجه
     */
    fun addBudget(budget: Budget) {
        try {
            val budgets = getBudgets().toMutableList()
            budgets.add(budget)
            saveBudgets(budgets)
            Log.i("BankingAssistantManager", "✅ بودجه جدید اضافه شد: ${budget.category}")
        } catch (e: Exception) {
            Log.e("BankingAssistantManager", "❌ خطا در افزودن بودجه: ${e.message}")
        }
    }
    
    /**
     * دریافت تمام بودجه‌ها
     */
    fun getBudgets(): List<Budget> {
        return try {
            val budgetsJson = prefs.getString(BUDGETS_KEY, null)
            if (budgetsJson != null) {
                json.decodeFromString<List<Budget>>(budgetsJson)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("BankingAssistantManager", "❌ خطا در دریافت بودجه‌ها: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * افزودن قبض
     */
    fun addBill(bill: Bill) {
        try {
            val bills = getBills().toMutableList()
            bills.add(bill)
            saveBills(bills)
            
            // تنظیم یادآور پرداخت قبض
            scheduleBillReminder(bill)
            
            Log.i("BankingAssistantManager", "✅ قبض جدید اضافه شد: ${bill.title}")
        } catch (e: Exception) {
            Log.e("BankingAssistantManager", "❌ خطا در افزودن قبض: ${e.message}")
        }
    }
    
    /**
     * دریافت تمام قبوض
     */
    fun getBills(): List<Bill> {
        return try {
            val billsJson = prefs.getString(BILLS_KEY, null)
            if (billsJson != null) {
                json.decodeFromString<List<Bill>>(billsJson)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("BankingAssistantManager", "❌ خطا در دریافت قبوض: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * دریافت قبوض پرداخت نشده
     */
    fun getUnpaidBills(): List<Bill> {
        return getBills().filter { !it.isPaid }
            .sortedBy { it.dueDate }
    }
    
    /**
     * پرداخت قبض
     */
    fun payBill(billId: String) {
        try {
            val bills = getBills().toMutableList()
            val index = bills.indexOfFirst { it.id == billId }
            if (index != -1) {
                bills[index] = bills[index].copy(isPaid = true)
                saveBills(bills)
                
                // افزودن تراکنش پرداخت قبض
                val bill = bills[index]
                val transaction = Transaction(
                    id = "bill_${billId}_${System.currentTimeMillis()}",
                    amount = bill.amount,
                    type = TransactionType.EXPENSE,
                    category = TransactionCategory.BILLS,
                    description = "پرداخت قبض ${bill.title}",
                    date = dateFormat.format(Date()),
                    accountId = getActiveAccounts().firstOrNull()?.id ?: "default"
                )
                addTransaction(transaction)
                
                Log.i("BankingAssistantManager", "✅ قبض پرداخت شد: ${bill.title}")
            }
        } catch (e: Exception) {
            Log.e("BankingAssistantManager", "❌ خطا در پرداخت قبض: ${e.message}")
        }
    }
    
    /**
     * محاسبه خلاصه مالی
     */
    fun getFinancialSummary(): FinancialSummary {
        val currentMonthTransactions = getCurrentMonthTransactions()
        val income = currentMonthTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expenses = currentMonthTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val totalBalance = getActiveAccounts().sumOf { it.balance }
        val unpaidBills = getUnpaidBills().sumOf { it.amount }
        
        return FinancialSummary(
            totalIncome = income,
            totalExpenses = expenses,
            netIncome = income - expenses,
            totalBalance = totalBalance,
            unpaidBills = unpaidBills,
            savingsRate = if (income > 0) ((income - expenses) / income) * 100 else 0.0
        )
    }
    
    @Serializable
    data class FinancialSummary(
        val totalIncome: Double,
        val totalExpenses: Double,
        val netIncome: Double,
        val totalBalance: Double,
        val unpaidBills: Double,
        val savingsRate: Double
    )
    
    /**
     * دریافت تحلیل هزینه‌ها
     */
    fun getExpenseAnalysis(): Map<TransactionCategory, Double> {
        val currentMonthTransactions = getCurrentMonthTransactions()
            .filter { it.type == TransactionType.EXPENSE }
        
        return currentMonthTransactions
            .groupBy { it.category }
            .mapValues { it.value.sumOf { transaction -> transaction.amount } }
    }
    
    /**
     * دریافت هشدارهای مالی
     */
    fun getFinancialAlerts(): List<FinancialAlert> {
        val alerts = mutableListOf<FinancialAlert>()
        
        // بررسی بودجه‌ها
        getBudgets().forEach { budget ->
            if (budget.spent > budget.limit * 0.8) {
                alerts.add(
                    FinancialAlert(
                        type = AlertType.BUDGET_WARNING,
                        message = "شما ${String.format("%.1f", (budget.spent / budget.limit) * 100)}% از بودجه ${getCategoryName(budget.category)} را مصرف کرده‌اید",
                        severity = AlertSeverity.WARNING
                    )
                )
            }
        }
        
        // بررسی قبوض نزدیک به سررسید
        val today = dateFormat.format(Date())
        getUnpaidBills().forEach { bill ->
            val daysUntilDue = getDaysBetween(today, bill.dueDate)
            if (daysUntilDue <= bill.reminderDays && daysUntilDue > 0) {
                alerts.add(
                    FinancialAlert(
                        type = AlertType.BILL_DUE,
                        message = "قبض ${bill.title} تا ${daysUntilDue} روز دیگر سررسید می‌شود",
                        severity = if (daysUntilDue <= 1) AlertSeverity.URGENT else AlertSeverity.WARNING
                    )
                )
            }
        }
        
        // بررسی موجودی پایین حساب
        getActiveAccounts().forEach { account ->
            if (account.balance < 100000) { // کمتر از ۱۰۰ هزار تومان
                alerts.add(
                    FinancialAlert(
                        type = AlertType.LOW_BALANCE,
                        message = "موجودی حساب ${account.name} کم است: ${String.format("%,.0f", account.balance)} تومان",
                        severity = AlertSeverity.WARNING
                    )
                )
            }
        }
        
        return alerts
    }
    
    @Serializable
    data class FinancialAlert(
        val type: AlertType,
        val message: String,
        val severity: AlertSeverity
    )
    
    @Serializable
    enum class AlertType {
        BUDGET_WARNING,
        BILL_DUE,
        LOW_BALANCE,
        OVERDRAFT
    }
    
    @Serializable
    enum class AlertSeverity {
        INFO,
        WARNING,
        URGENT
    }
    
    /**
     * به‌روزرسانی هزینه بودجه
     */
    private fun updateBudgetSpending(transaction: Transaction) {
        if (transaction.type == TransactionType.EXPENSE) {
            try {
                val budgets = getBudgets().toMutableList()
                val budgetIndex = budgets.indexOfFirst { 
                    it.category == transaction.category && it.isActive 
                }
                if (budgetIndex != -1) {
                    budgets[budgetIndex] = budgets[budgetIndex].copy(
                        spent = budgets[budgetIndex].spent + transaction.amount
                    )
                    saveBudgets(budgets)
                }
            } catch (e: Exception) {
                Log.e("BankingAssistantManager", "❌ خطا در به‌روزرسانی هزینه بودجه: ${e.message}")
            }
        }
    }
    
    /**
     * تنظیم یادآور قبض
     */
    private fun scheduleBillReminder(bill: Bill) {
        try {
            val notificationHelper = NotificationHelper(context)
            val reminderDate = getReminderDate(bill.dueDate, bill.reminderDays)
            
            notificationHelper.scheduleNotification(
                title = "یادآور پرداخت قبض",
                message = "قبض ${bill.title} تا ${bill.reminderDays} روز دیگر سررسید می‌شود",
                time = reminderDate,
                channelId = "bill_reminders"
            )
            
            Log.i("BankingAssistantManager", "✅ یادآور قبض تنظیم شد: ${bill.title}")
        } catch (e: Exception) {
            Log.e("BankingAssistantManager", "❌ خطا در تنظیم یادآور قبض: ${e.message}")
        }
    }
    
    /**
     * دریافت تاریخ یادآور
     */
    private fun getReminderDate(dueDate: String, daysBefore: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.time = dateFormat.parse(dueDate) ?: Date()
        calendar.add(Calendar.DAY_OF_MONTH, -daysBefore)
        return calendar.timeInMillis
    }
    
    /**
     * محاسبه تعداد روز بین دو تاریخ
     */
    private fun getDaysBetween(startDate: String, endDate: String): Int {
        return try {
            val start = dateFormat.parse(startDate) ?: Date()
            val end = dateFormat.parse(endDate) ?: Date()
            val diff = end.time - start.time
            (diff / (1000 * 60 * 60 * 24)).toInt()
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * دریافت نام دسته‌بندی
     */
    private fun getCategoryName(category: TransactionCategory): String {
        return when (category) {
            TransactionCategory.FOOD -> "خوراک"
            TransactionCategory.TRANSPORT -> "حمل و نقل"
            TransactionCategory.SHOPPING -> "خرید"
            TransactionCategory.ENTERTAINMENT -> "سرگرمی"
            TransactionCategory.HEALTH -> "سلامتی"
            TransactionCategory.EDUCATION -> "آموزشی"
            TransactionCategory.BILLS -> "قبوض"
            TransactionCategory.SALARY -> "حقوق"
            TransactionCategory.INVESTMENT -> "سرمایه‌گذاری"
            TransactionCategory.OTHER -> "سایر"
        }
    }
    
    /**
     * ایجاد حساب‌های پیش‌فرض
     */
    private fun createDefaultAccounts(): List<Account> {
        val defaultAccounts = listOf(
            Account(
                id = "cash",
                name = "نقدی",
                type = AccountType.CASH,
                balance = 0.0,
                currency = "IRR"
            ),
            Account(
                id = "main_checking",
                name = "حساب جاری اصلی",
                type = AccountType.CHECKING,
                balance = 0.0,
                currency = "IRR"
            )
        )
        
        saveAccounts(defaultAccounts)
        return defaultAccounts
    }
    
    /**
     * ذخیره تراکنش‌ها
     */
    private fun saveTransactions(transactions: List<Transaction>) {
        try {
            val transactionsJson = json.encodeToString(transactions)
            prefs.edit()
                .putString(TRANSACTIONS_KEY, transactionsJson)
                .apply()
        } catch (e: Exception) {
            Log.e("BankingAssistantManager", "❌ خطا در ذخیره تراکنش‌ها: ${e.message}")
        }
    }
    
    /**
     * ذخیره حساب‌ها
     */
    private fun saveAccounts(accounts: List<Account>) {
        try {
            val accountsJson = json.encodeToString(accounts)
            prefs.edit()
                .putString(ACCOUNTS_KEY, accountsJson)
                .apply()
        } catch (e: Exception) {
            Log.e("BankingAssistantManager", "❌ خطا در ذخیره حساب‌ها: ${e.message}")
        }
    }
    
    /**
     * ذخیره بودجه‌ها
     */
    private fun saveBudgets(budgets: List<Budget>) {
        try {
            val budgetsJson = json.encodeToString(budgets)
            prefs.edit()
                .putString(BUDGETS_KEY, budgetsJson)
                .apply()
        } catch (e: Exception) {
            Log.e("BankingAssistantManager", "❌ خطا در ذخیره بودجه‌ها: ${e.message}")
        }
    }
    
    /**
     * ذخیره قبوض
     */
    private fun saveBills(bills: List<Bill>) {
        try {
            val billsJson = json.encodeToString(bills)
            prefs.edit()
                .putString(BILLS_KEY, billsJson)
                .apply()
        } catch (e: Exception) {
            Log.e("BankingAssistantManager", "❌ خطا در ذخیره قبوض: ${e.message}")
        }
    }
    
    /**
     * پاک‌سازی منابع
     */
    fun cleanup() {
        scope.cancel()
        Log.i("BankingAssistantManager", "🧹 منابع BankingAssistantManager پاک‌سازی شد")
    }
}
