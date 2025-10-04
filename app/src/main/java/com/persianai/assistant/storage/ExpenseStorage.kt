package com.persianai.assistant.storage

import android.content.Context
import com.persianai.assistant.models.Expense
import com.persianai.assistant.models.ExpenseCategory
import org.json.JSONArray
import org.json.JSONObject

class ExpenseStorage(context: Context) {
    
    private val prefs = context.getSharedPreferences("expenses", Context.MODE_PRIVATE)
    
    fun saveExpense(expense: Expense) {
        val expenses = getAllExpenses().toMutableList()
        expenses.add(expense)
        saveAll(expenses)
    }
    
    fun getAllExpenses(): List<Expense> {
        val json = prefs.getString("all_expenses", "[]") ?: "[]"
        val jsonArray = JSONArray(json)
        val expenses = mutableListOf<Expense>()
        
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            expenses.add(
                Expense(
                    id = obj.getLong("id"),
                    amount = obj.getLong("amount"),
                    category = ExpenseCategory.valueOf(obj.getString("category")),
                    description = obj.getString("description"),
                    persianDate = obj.getString("persianDate"),
                    timestamp = obj.getLong("timestamp")
                )
            )
        }
        
        return expenses.sortedByDescending { it.timestamp }
    }
    
    fun deleteExpense(id: Long) {
        val expenses = getAllExpenses().filter { it.id != id }
        saveAll(expenses)
    }
    
    fun getMonthlyTotal(persianMonth: String): Long {
        return getAllExpenses()
            .filter { it.persianDate.startsWith(persianMonth) }
            .sumOf { it.amount }
    }
    
    fun getCategoryTotal(category: ExpenseCategory): Long {
        return getAllExpenses()
            .filter { it.category == category }
            .sumOf { it.amount }
    }
    
    private fun saveAll(expenses: List<Expense>) {
        val jsonArray = JSONArray()
        expenses.forEach { expense ->
            val obj = JSONObject().apply {
                put("id", expense.id)
                put("amount", expense.amount)
                put("category", expense.category.name)
                put("description", expense.description)
                put("persianDate", expense.persianDate)
                put("timestamp", expense.timestamp)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("all_expenses", jsonArray.toString()).apply()
    }
}
