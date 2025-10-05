package com.persianai.assistant.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object BackupManager {
    
    fun createBackup(context: Context): File {
        val backup = JSONObject()
        
        // Settings
        val prefs = context.getSharedPreferences("ai_assistant_prefs", Context.MODE_PRIVATE)
        val settings = JSONObject()
        prefs.all.forEach { (key, value) ->
            settings.put(key, value)
        }
        backup.put("settings", settings)
        
        // Conversations
        val convPrefs = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
        val conversations = JSONObject()
        convPrefs.all.forEach { (key, value) ->
            conversations.put(key, value)
        }
        backup.put("conversations", conversations)
        
        // Expenses
        val expensePrefs = context.getSharedPreferences("expenses", Context.MODE_PRIVATE)
        val expenses = JSONObject()
        expensePrefs.all.forEach { (key, value) ->
            expenses.put(key, value)
        }
        backup.put("expenses", expenses)
        
        // Incomes
        val incomePrefs = context.getSharedPreferences("incomes", Context.MODE_PRIVATE)
        val incomes = JSONObject()
        incomePrefs.all.forEach { (key, value) ->
            incomes.put(key, value)
        }
        backup.put("incomes", incomes)
        
        // Reminders
        val reminderPrefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val reminders = JSONObject()
        reminderPrefs.all.forEach { (key, value) ->
            reminders.put(key, value)
        }
        backup.put("reminders", reminders)
        
        // Save to file
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        val fileName = "backup_${dateFormat.format(Date())}.json"
        val file = File(context.getExternalFilesDir(null), fileName)
        file.writeText(backup.toString(2))
        
        return file
    }
    
    fun restoreBackup(context: Context, fileContent: String): Boolean {
        return try {
            val backup = JSONObject(fileContent)
            
            // Restore settings
            if (backup.has("settings")) {
                val prefs = context.getSharedPreferences("ai_assistant_prefs", Context.MODE_PRIVATE).edit()
                val settings = backup.getJSONObject("settings")
                settings.keys().forEach { key ->
                    val value = settings.get(key)
                    when (value) {
                        is String -> prefs.putString(key, value)
                        is Boolean -> prefs.putBoolean(key, value)
                        is Int -> prefs.putInt(key, value)
                        is Long -> prefs.putLong(key, value)
                        is Float -> prefs.putFloat(key, value)
                    }
                }
                prefs.apply()
            }
            
            // Restore conversations
            if (backup.has("conversations")) {
                val prefs = context.getSharedPreferences("conversations", Context.MODE_PRIVATE).edit()
                val conversations = backup.getJSONObject("conversations")
                conversations.keys().forEach { key ->
                    prefs.putString(key, conversations.getString(key))
                }
                prefs.apply()
            }
            
            // Restore expenses
            if (backup.has("expenses")) {
                val prefs = context.getSharedPreferences("expenses", Context.MODE_PRIVATE).edit()
                val expenses = backup.getJSONObject("expenses")
                expenses.keys().forEach { key ->
                    prefs.putString(key, expenses.getString(key))
                }
                prefs.apply()
            }
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun shareBackup(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری بک‌آپ"))
    }
}
