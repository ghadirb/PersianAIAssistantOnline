package com.persianai.assistant.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues

class AccountingDB(context: Context) : SQLiteOpenHelper(context, "accounting.db", null, 1) {
    
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE transactions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            type TEXT, amount REAL, category TEXT, description TEXT,
            date INTEGER, checkNumber TEXT, checkStatus TEXT,
            installmentId INTEGER, installmentNumber INTEGER, totalInstallments INTEGER)""")
    }
    
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        db.execSQL("DROP TABLE IF EXISTS transactions")
        onCreate(db)
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
}
