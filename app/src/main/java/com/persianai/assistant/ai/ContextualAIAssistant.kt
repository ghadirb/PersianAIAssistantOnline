package com.persianai.assistant.ai

import android.content.Context
import android.util.Log
import com.persianai.assistant.data.AccountingDB
import com.persianai.assistant.data.Transaction
import com.persianai.assistant.data.TransactionType
import com.persianai.assistant.api.AIModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.*

/**
 * دستیار هوشمند که با مدل AI کار می‌کند
 */
class ContextualAIAssistant(private val context: Context) {
    
    private val TAG = "ContextualAIAssistant"
    private val nlp = PersianNLP()
    private val aiModelManager = AIModelManager(context)
    
    suspend fun processAccountingCommand(userMessage: String, db: AccountingDB): AIResponse = withContext(Dispatchers.IO) {
        // اول سعی می‌کنیم با مدل واقعی پردازش کنیم
        if (aiModelManager.hasApiKey()) {
            try {
                val prompt = """تو یک دستیار مالی هستی. کاربر می‌گوید: "$userMessage"
اگر درخواست ثبت هزینه یا درآمد است، به فرمت JSON پاسخ بده:
{"type": "expense/income", "amount": مبلغ, "description": "توضیحات"}
اگر سوال است، مستقیم پاسخ بده."""
                
                val response = aiModelManager.generateText(prompt)
                if (response.contains("{") && response.contains("}")) {
                    val json = JSONObject(response.substring(response.indexOf("{"), response.lastIndexOf("}") + 1))
                    val type = json.optString("type")
                    val amount = json.optDouble("amount", 0.0)
                    val desc = json.optString("description", "")
                    
                    if (amount > 0) {
                        val transType = if (type == "income") TransactionType.INCOME else TransactionType.EXPENSE
                        val t = Transaction(0, transType, amount, "", desc, System.currentTimeMillis())
                        db.addTransaction(t)
                        return@withContext AIResponse(true, "✅ ${if (type == "income") "درآمد" else "هزینه"} ${formatMoney(amount)} تومان ثبت شد", "add_transaction")
                    }
                }
                return@withContext AIResponse(true, response, "chat")
            } catch (e: Exception) {
                Log.e(TAG, "Error using AI model", e)
            }
        }
        
        // اگر مدل در دسترس نبود، از NLP ساده استفاده می‌کنیم
        val cmd = nlp.parse(userMessage)
        return@withContext when(cmd.type) {
            PersianNLP.Type.EXPENSE -> {
                if (cmd.amount != null && cmd.amount > 0) {
                    val t = Transaction(0, TransactionType.EXPENSE, cmd.amount, "", cmd.text ?: "", System.currentTimeMillis())
                    db.addTransaction(t)
                    AIResponse(true, "✅ هزینه ${formatMoney(cmd.amount)} تومان ثبت شد", "add_transaction")
                } else {
                    AIResponse(false, "مبلغ نامعتبر است", "error")
                }
            }
            PersianNLP.Type.INCOME -> {
                if (cmd.amount != null && cmd.amount > 0) {
                    val t = Transaction(0, TransactionType.INCOME, cmd.amount, "", cmd.text ?: "", System.currentTimeMillis())
                    db.addTransaction(t)
                    AIResponse(true, "✅ درآمد ${formatMoney(cmd.amount)} تومان ثبت شد", "add_transaction")
                } else {
                    AIResponse(false, "مبلغ نامعتبر است", "error")
                }
            }
            else -> extractAccountingCommandManually(userMessage, db)
        }
    }
    
    suspend fun processReminderCommand(userMessage: String): AIResponse = withContext(Dispatchers.IO) {
        val cmd = nlp.parse(userMessage)
        return@withContext when(cmd.type) {
            PersianNLP.Type.REMINDER -> {
                if (cmd.time != null) {
                    val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
                    prefs.edit().putString("reminder_${System.currentTimeMillis()}", "${cmd.time}|${cmd.text}").apply()
                    val timeStr = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(cmd.time))
                    AIResponse(true, "✅ یادآوری ساعت $timeStr ثبت شد", "add_reminder")
                } else {
                    AIResponse(false, "زمان نامعتبر است", "error")
                }
            }
            else -> extractReminderCommandManually(userMessage)
        }
    }
    
    suspend fun processMusicCommand(userMessage: String): AIResponse = withContext(Dispatchers.IO) {
        return@withContext extractMusicCommandManually(userMessage)
    }
    
    suspend fun processNavigationCommand(userMessage: String): AIResponse = withContext(Dispatchers.IO) {
        return@withContext extractNavigationCommandManually(userMessage)
    }
    
    private fun extractAccountingCommandManually(message: String, db: AccountingDB): AIResponse {
        val lowerMessage = message.lowercase()
        
        // استخراج اعداد
        val numbers = Regex("\\d+(?:\\.\\d+)?").findAll(message).map { it.value.toDoubleOrNull() ?: 0.0 }.toList()
        
        if (numbers.isNotEmpty()) {
            val amount = numbers.first()
            val isIncome = lowerMessage.contains("درآمد") || lowerMessage.contains("دریافت") || lowerMessage.contains("سود")
            
            val type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE
            val description = message.replace(Regex("\\d+(?:\\.\\d+)?"), "").trim()
            
            val transaction = Transaction(0, type, amount, "", description, System.currentTimeMillis())
            db.addTransaction(transaction)
            
            val typeText = if (isIncome) "درآمد" else "هزینه"
            return AIResponse(true, "✅ $typeText ${formatMoney(amount)} تومان ثبت شد", "add_transaction")
        }
        
        return AIResponse(false, "متاسفانه متوجه منظور شما نشدم. لطفا مبلغ را مشخص کنید.", "error")
    }
    
    private fun extractReminderCommandManually(message: String): AIResponse {
        return AIResponse(false, "برای ثبت یادآوری، لطفا زمان و موضوع را مشخص کنید.", "error")
    }
    
    private fun extractMusicCommandManually(message: String): AIResponse {
        return AIResponse(false, "برای کنترل موسیقی، از دستورات پخش، توقف، یا بعدی استفاده کنید.", "error")
    }
    
    private fun extractNavigationCommandManually(message: String): AIResponse {
        return AIResponse(false, "برای مسیریابی، لطفا مبدأ و مقصد را مشخص کنید.", "error")
    }
    
    private fun formatMoney(amount: Double): String {
        return String.format("%,.0f", amount)
    }
}

/**
 * کلاس پاسخ هوش مصنوعی
 */
data class AIResponse(
    val success: Boolean,
    val message: String,
    val action: String
)

/**
 * کلاس NLP ساده برای پردازش فارسی
 */
class PersianNLP {
    data class Command(
        val type: Type,
        val amount: Double? = null,
        val text: String? = null,
        val time: Long? = null
    )
    
    enum class Type {
        EXPENSE, INCOME, REMINDER, MUSIC, NAVIGATION, UNKNOWN
    }
    
    fun parse(text: String): Command {
        val lower = text.lowercase()
        
        return when {
            lower.contains("هزینه") || lower.contains("خرج") -> {
                val amount = extractAmount(text)
                Command(Type.EXPENSE, amount, text)
            }
            lower.contains("درآمد") || lower.contains("دریافت") -> {
                val amount = extractAmount(text)
                Command(Type.INCOME, amount, text)
            }
            lower.contains("یادآوری") || lower.contains("یادآور") -> {
                Command(Type.REMINDER, text = text)
            }
            lower.contains("موسیقی") || lower.contains("آهنگ") || lower.contains("موزیک") -> {
                Command(Type.MUSIC, text = text)
            }
            lower.contains("مسیر") || lower.contains("نقشه") || lower.contains("آدرس") -> {
                Command(Type.NAVIGATION, text = text)
            }
            else -> Command(Type.UNKNOWN, text = text)
        }
    }
    
    private fun extractAmount(text: String): Double? {
        val numbers = Regex("\\d+(?:\\.\\d+)?").findAll(text)
        for (match in numbers) {
            val amount = match.value.toDoubleOrNull()
            if (amount != null && amount > 0) {
                return amount
            }
        }
        return null
    }
}
