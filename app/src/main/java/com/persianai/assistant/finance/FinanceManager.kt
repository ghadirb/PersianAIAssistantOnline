package com.persianai.assistant.finance

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * سیستم حسابداری ساده
 */
class FinanceManager(private val context: Context) {
    
    data class Transaction(
        val id: String,
        val amount: Double,
        val type: String, // "income" or "expense"
        val category: String,
        val description: String,
        val date: Long
    )
    
    private val prefs = context.getSharedPreferences("finance", Context.MODE_PRIVATE)
    
    fun addTransaction(amount: Double, type: String, category: String, desc: String): String {
        val id = UUID.randomUUID().toString()
        val transaction = Transaction(id, amount, type, category, desc, System.currentTimeMillis())
        
        // ذخیره
        val transactions = getAllTransactions().toMutableList()
        transactions.add(transaction)
        saveTransactions(transactions)
        
        return id
    }
    
    fun getAllTransactions(): List<Transaction> {
        val json = prefs.getString("transactions", "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<Transaction>()
        
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(Transaction(
                obj.getString("id"),
                obj.getDouble("amount"),
                obj.getString("type"),
                obj.getString("category"),
                obj.getString("description"),
                obj.getLong("date")
            ))
        }
        
        return list.sortedByDescending { it.date }
    }
    
    private fun saveTransactions(transactions: List<Transaction>) {
        val array = JSONArray()
        transactions.forEach { t ->
            array.put(JSONObject().apply {
                put("id", t.id)
                put("amount", t.amount)
                put("type", t.type)
                put("category", t.category)
                put("description", t.description)
                put("date", t.date)
            })
        }
        prefs.edit().putString("transactions", array.toString()).apply()
    }
    
    fun getBalance(): Double {
        val transactions = getAllTransactions()
        var balance = 0.0
        transactions.forEach {
            if (it.type == "income") {
                balance += it.amount
            } else {
                balance -= it.amount
            }
        }
        return balance
    }
    
    fun getMonthlyReport(year: Int, month: Int): Pair<Double, Double> {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1, 0, 0, 0)
        val startTime = calendar.timeInMillis
        
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        val endTime = calendar.timeInMillis
        
        val transactions = getAllTransactions().filter { it.date in startTime..endTime }
        
        var income = 0.0
        var expense = 0.0
        
        transactions.forEach {
            if (it.type == "income") income += it.amount
            else expense += it.amount
        }
        
        return Pair(income, expense)
    }
    
    fun deleteTransaction(id: String) {
        val transactions = getAllTransactions().filter { it.id != id }
        saveTransactions(transactions)
    }
}
