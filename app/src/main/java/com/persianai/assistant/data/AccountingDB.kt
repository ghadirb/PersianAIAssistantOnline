package com.persianai.assistant.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues

class AccountingDB(context: Context) : SQLiteOpenHelper(context, "accounting.db", null, 2) {
    
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE transactions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            type TEXT, amount REAL, category TEXT, description TEXT,
            date INTEGER, checkNumber TEXT, checkStatus TEXT,
            installmentId INTEGER, installmentNumber INTEGER, totalInstallments INTEGER)""")
        
        db.execSQL("""CREATE TABLE checks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            amount REAL, checkNumber TEXT, issuer TEXT, dueDate INTEGER,
            status TEXT, type TEXT, description TEXT, createdDate INTEGER)""")
        
        db.execSQL("""CREATE TABLE installments (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            totalAmount REAL, monthlyAmount REAL, totalMonths INTEGER, paidMonths INTEGER,
            startDate INTEGER, description TEXT, category TEXT, reminderEnabled INTEGER, createdDate INTEGER)""")
    }
    
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        if (old < 2) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS checks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                amount REAL, checkNumber TEXT, issuer TEXT, dueDate INTEGER,
                status TEXT, type TEXT, description TEXT, createdDate INTEGER)""")
            
            db.execSQL("""CREATE TABLE IF NOT EXISTS installments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                totalAmount REAL, monthlyAmount REAL, totalMonths INTEGER, paidMonths INTEGER,
                startDate INTEGER, description TEXT, category TEXT, reminderEnabled INTEGER, createdDate INTEGER)""")
        }
    }
    
    fun addTransaction(t: Transaction): Long {
        val values = ContentValues().apply {
            put("type", t.type.name)
            put("amount", t.amount)
            put("category", t.category)
            put("description", t.description)
            put("date", t.date)
            put("checkNumber", t.checkNumber)
            put("checkStatus", t.checkStatus?.name)
            put("installmentId", t.installmentId)
            put("installmentNumber", t.installmentNumber)
            put("totalInstallments", t.totalInstallments)
        }
        return writableDatabase.insert("transactions", null, values)
    }
    
    fun getBalance(): Double {
        val cursor = readableDatabase.rawQuery(
            "SELECT type, SUM(amount) FROM transactions GROUP BY type", null)
        var balance = 0.0
        while (cursor.moveToNext()) {
            val type = cursor.getString(0)
            val sum = cursor.getDouble(1)
            balance += if (type == "INCOME") sum else -sum
        }
        cursor.close()
        return balance
    }
    
    fun getMonthlyExpenses(): Double {
        val cursor = readableDatabase.rawQuery(
            "SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE' AND date >= strftime('%s', 'now', 'start of month') * 1000", null)
        var expenses = 0.0
        if (cursor.moveToFirst()) {
            expenses = cursor.getDouble(0)
        }
        cursor.close()
        return expenses
    }
    
    fun getMonthlyIncome(): Double {
        val cursor = readableDatabase.rawQuery(
            "SELECT SUM(amount) FROM transactions WHERE type = 'INCOME' AND date >= strftime('%s', 'now', 'start of month') * 1000", null)
        var income = 0.0
        if (cursor.moveToFirst()) {
            income = cursor.getDouble(0)
        }
        cursor.close()
        return income
    }
    
    fun getAllTransactions(): List<Transaction> {
        val transactions = mutableListOf<Transaction>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM transactions ORDER BY date DESC LIMIT 50", null)
        
        while (cursor.moveToNext()) {
            val transaction = Transaction(
                id = cursor.getLong(0),
                type = TransactionType.valueOf(cursor.getString(1)),
                amount = cursor.getDouble(2),
                category = cursor.getString(3) ?: "",
                description = cursor.getString(4) ?: "",
                date = cursor.getLong(5),
                checkNumber = cursor.getString(6),
                checkStatus = cursor.getString(7)?.let { CheckStatus.valueOf(it) },
                installmentId = if (cursor.isNull(8)) null else cursor.getLong(8),
                installmentNumber = if (cursor.isNull(9)) null else cursor.getInt(9),
                totalInstallments = if (cursor.isNull(10)) null else cursor.getInt(10)
            )
            transactions.add(transaction)
        }
        cursor.close()
        return transactions
    }
    
    fun deleteTransaction(id: Long): Int {
        return writableDatabase.delete("transactions", "id = ?", arrayOf(id.toString()))
    }
    
    // ==================== متدهای چک ====================
    
    fun addCheck(check: Check): Long {
        val values = ContentValues().apply {
            put("amount", check.amount)
            put("checkNumber", check.checkNumber)
            put("issuer", check.issuer)
            put("dueDate", check.dueDate)
            put("status", check.status.name)
            put("type", check.type.name)
            put("description", check.description)
            put("createdDate", check.createdDate)
        }
        return writableDatabase.insert("checks", null, values)
    }
    
    fun getAllChecks(): List<Check> {
        val checks = mutableListOf<Check>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM checks ORDER BY dueDate ASC", null)
        
        while (cursor.moveToNext()) {
            checks.add(Check(
                id = cursor.getLong(0),
                amount = cursor.getDouble(1),
                checkNumber = cursor.getString(2),
                issuer = cursor.getString(3),
                dueDate = cursor.getLong(4),
                status = CheckStatus.valueOf(cursor.getString(5)),
                type = CheckType.valueOf(cursor.getString(6)),
                description = cursor.getString(7) ?: "",
                createdDate = cursor.getLong(8)
            ))
        }
        cursor.close()
        return checks
    }
    
    fun updateCheckStatus(id: Long, status: CheckStatus): Int {
        val values = ContentValues().apply {
            put("status", status.name)
        }
        return writableDatabase.update("checks", values, "id = ?", arrayOf(id.toString()))
    }
    
    fun deleteCheck(id: Long): Int {
        return writableDatabase.delete("checks", "id = ?", arrayOf(id.toString()))
    }
    
    // ==================== متدهای اقساط ====================
    
    fun addInstallment(installment: Installment): Long {
        val values = ContentValues().apply {
            put("totalAmount", installment.totalAmount)
            put("monthlyAmount", installment.monthlyAmount)
            put("totalMonths", installment.totalMonths)
            put("paidMonths", installment.paidMonths)
            put("startDate", installment.startDate)
            put("description", installment.description)
            put("category", installment.category)
            put("reminderEnabled", if (installment.reminderEnabled) 1 else 0)
            put("createdDate", installment.createdDate)
        }
        return writableDatabase.insert("installments", null, values)
    }
    
    fun getAllInstallments(): List<Installment> {
        val installments = mutableListOf<Installment>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM installments ORDER BY startDate DESC", null)
        
        while (cursor.moveToNext()) {
            installments.add(Installment(
                id = cursor.getLong(0),
                totalAmount = cursor.getDouble(1),
                monthlyAmount = cursor.getDouble(2),
                totalMonths = cursor.getInt(3),
                paidMonths = cursor.getInt(4),
                startDate = cursor.getLong(5),
                description = cursor.getString(6) ?: "",
                category = cursor.getString(7) ?: "",
                reminderEnabled = cursor.getInt(8) == 1,
                createdDate = cursor.getLong(9)
            ))
        }
        cursor.close()
        return installments
    }
    
    fun payInstallment(id: Long): Boolean {
        val cursor = readableDatabase.rawQuery(
            "SELECT paidMonths, totalMonths FROM installments WHERE id = ?", arrayOf(id.toString()))
        
        if (cursor.moveToFirst()) {
            val paidMonths = cursor.getInt(0)
            val totalMonths = cursor.getInt(1)
            cursor.close()
            
            if (paidMonths < totalMonths) {
                val values = ContentValues().apply {
                    put("paidMonths", paidMonths + 1)
                }
                writableDatabase.update("installments", values, "id = ?", arrayOf(id.toString()))
                return true
            }
        } else {
            cursor.close()
        }
        return false
    }
    
    fun deleteInstallment(id: Long): Int {
        return writableDatabase.delete("installments", "id = ?", arrayOf(id.toString()))
    }
    
    // ==================== گزارشات ====================
    
    fun getMonthlyReport(year: Int, month: Int): Map<String, Double> {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(year, month - 1, 1, 0, 0, 0)
        val startTime = calendar.timeInMillis
        
        calendar.add(java.util.Calendar.MONTH, 1)
        val endTime = calendar.timeInMillis
        
        val cursor = readableDatabase.rawQuery(
            "SELECT type, SUM(amount) FROM transactions WHERE date >= ? AND date < ? GROUP BY type",
            arrayOf(startTime.toString(), endTime.toString()))
        
        val report = mutableMapOf<String, Double>()
        while (cursor.moveToNext()) {
            report[cursor.getString(0)] = cursor.getDouble(1)
        }
        cursor.close()
        return report
    }
    
    fun getYearlyReport(year: Int): Map<String, Double> {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(year, 0, 1, 0, 0, 0)
        val startTime = calendar.timeInMillis
        
        calendar.add(java.util.Calendar.YEAR, 1)
        val endTime = calendar.timeInMillis
        
        val cursor = readableDatabase.rawQuery(
            "SELECT type, SUM(amount) FROM transactions WHERE date >= ? AND date < ? GROUP BY type",
            arrayOf(startTime.toString(), endTime.toString()))
        
        val report = mutableMapOf<String, Double>()
        while (cursor.moveToNext()) {
            report[cursor.getString(0)] = cursor.getDouble(1)
        }
        cursor.close()
        return report
    }
}
