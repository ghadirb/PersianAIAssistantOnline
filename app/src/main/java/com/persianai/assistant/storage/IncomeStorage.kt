package com.persianai.assistant.storage

import android.content.Context
import com.persianai.assistant.models.Income
import org.json.JSONArray
import org.json.JSONObject

class IncomeStorage(context: Context) {
    
    private val prefs = context.getSharedPreferences("incomes", Context.MODE_PRIVATE)
    
    fun saveIncome(income: Income) {
        val incomes = getAllIncomes().toMutableList()
        incomes.add(income)
        saveAll(incomes)
    }
    
    fun getAllIncomes(): List<Income> {
        val json = prefs.getString("all_incomes", "[]") ?: "[]"
        val jsonArray = JSONArray(json)
        val incomes = mutableListOf<Income>()
        
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            incomes.add(
                Income(
                    id = obj.getLong("id"),
                    amount = obj.getLong("amount"),
                    source = obj.getString("source"),
                    persianDate = obj.getString("persianDate"),
                    timestamp = obj.getLong("timestamp")
                )
            )
        }
        
        return incomes.sortedByDescending { it.timestamp }
    }
    
    fun getMonthlyTotal(persianMonth: String): Long {
        return getAllIncomes()
            .filter { it.persianDate.startsWith(persianMonth) }
            .sumOf { it.amount }
    }
    
    private fun saveAll(incomes: List<Income>) {
        val jsonArray = JSONArray()
        incomes.forEach { income ->
            val obj = JSONObject().apply {
                put("id", income.id)
                put("amount", income.amount)
                put("source", income.source)
                put("persianDate", income.persianDate)
                put("timestamp", income.timestamp)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("all_incomes", jsonArray.toString()).apply()
    }
}
