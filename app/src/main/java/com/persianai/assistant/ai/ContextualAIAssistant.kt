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
        val cmd = nlp.parse(userMessage)
        
        return@withContext when {
            userMessage.contains("پمپ بنزین") || userMessage.contains("بنزین") -> {
                AIResponse(true, "🔍 در حال جستجوی پمپ بنزین‌های نزدیک...", "search_poi", mapOf("type" to "gas_station"))
            }
            userMessage.contains("رستوران") || userMessage.contains("غذا") -> {
                AIResponse(true, "🔍 در حال جستجوی رستوران‌های نزدیک...", "search_poi", mapOf("type" to "restaurant"))
            }
            userMessage.contains("بیمارستان") || userMessage.contains("درمانگاه") -> {
                AIResponse(true, "🔍 در حال جستجوی بیمارستان‌های نزدیک...", "search_poi", mapOf("type" to "hospital"))
            }
            userMessage.contains("خانه") || userMessage.contains("منزل") -> {
                AIResponse(true, "🏠 مسیر به خانه محاسبه می‌شود", "navigate_home")
            }
            userMessage.contains("کار") || userMessage.contains("محل کار") -> {
                AIResponse(true, "💼 مسیر به محل کار محاسبه می‌شود", "navigate_work")
            }
            userMessage.contains("ذخیره") || userMessage.contains("save") -> {
                AIResponse(true, "💾 مکان فعلی ذخیره می‌شود", "save_location")
            }
            userMessage.contains("ترافیک") || userMessage.contains("traffic") -> {
                AIResponse(true, "🚦 اطلاعات ترافیک در حال بارگذاری...", "show_traffic")
            }
            userMessage.contains("دوربین") || userMessage.contains("سرعت") -> {
                AIResponse(true, "📷 دوربین‌های سرعت در حال نمایش...", "show_cameras")
            }
            userMessage.contains("مسیر") || userMessage.contains("route") -> {
                AIResponse(true, "🛣️ پیشنهاد مسیرهای مختلف...", "suggest_routes")
            }
            else -> extractNavigationCommandManually(userMessage)
        }
    }
    
    private suspend fun parseAccountingResponse(aiResponse: String, db: AccountingDB, userMessage: String): AIResponse {
        return try {
            val json = JSONObject(aiResponse)
            val action = json.optString("action", "chat")
            
            when (action) {
                "add_transaction" -> {
                    val type = json.optString("transaction_type", "EXPENSE")
                    val amount = json.optDouble("amount", 0.0)
                    val desc = json.optString("description", userMessage)
                    
                    if (amount > 0) {
                        val transaction = Transaction(0, TransactionType.valueOf(type), amount, "", desc, System.currentTimeMillis())
                        db.addTransaction(transaction)
                        AIResponse(true, "✅ ثبت شد: ${formatMoney(amount)} تومان", "add_transaction", mapOf("transaction" to transaction))
                    } else {
                        AIResponse(false, "مبلغ نامعتبر", "error")
                    }
                }
                "show_balance" -> {
                    val balance = db.getBalance()
                    AIResponse(true, "موجودی: ${formatMoney(balance)} تومان", "show_balance", mapOf("balance" to balance))
                }
                else -> AIResponse(true, json.optString("response", aiResponse), "chat")
            }
        } catch (e: Exception) {
            extractAccountingCommandManually(userMessage, db)
        }
    }
    
    private suspend fun parseReminderResponse(aiResponse: String, userMessage: String): AIResponse {
        return try {
            val json = JSONObject(aiResponse)
            val action = json.optString("action", "chat")
            
            if (action == "add_reminder") {
                val time = json.optString("time", "")
                val message = json.optString("message", userMessage)
                
                if (time.isNotEmpty()) {
                    val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
                    prefs.edit().putString("reminder_${System.currentTimeMillis()}", "$time|$message").apply()
                    AIResponse(true, "✅ یادآوری ساعت $time ثبت شد", "add_reminder", mapOf("time" to time))
                } else {
                    AIResponse(false, "زمان نامعتبر", "error")
                }
            } else {
                AIResponse(true, json.optString("response", aiResponse), "chat")
            }
        } catch (e: Exception) {
            extractReminderCommandManually(userMessage)
        }
    }
    
    private fun parseMusicResponse(aiResponse: String, userMessage: String): AIResponse {
        return try {
            val json = JSONObject(aiResponse)
            AIResponse(true, json.optString("response", aiResponse), json.optString("action", "chat"), 
                mapOf("mood" to json.optString("mood", "")))
        } catch (e: Exception) {
            extractMusicCommandManually(userMessage)
        }
    }
    
    private fun parseNavigationResponse(aiResponse: String, userMessage: String): AIResponse {
        return try {
            val json = JSONObject(aiResponse)
            AIResponse(true, json.optString("response", aiResponse), json.optString("action", "chat"),
                mapOf("poi_type" to json.optString("poi_type", "")))
        } catch (e: Exception) {
            extractNavigationCommandManually(userMessage)
        }
    }
    
    private suspend fun extractAccountingCommandManually(userMessage: String, db: AccountingDB): AIResponse {
        val msg = userMessage.lowercase()
        
        // استخراج مبلغ
        val amountRegex = """(\d+(?:,\d{3})*(?:\.\d+)?)""".toRegex()
        val amountMatch = amountRegex.find(msg)
        val amount = amountMatch?.value?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        
        return when {
            msg.contains("درآمد") && amount > 0 -> {
                val t = Transaction(0, TransactionType.INCOME, amount, "", userMessage, System.currentTimeMillis())
                db.addTransaction(t)
                AIResponse(true, "✅ درآمد ${formatMoney(amount)} تومان ثبت شد", "add_transaction")
            }
            msg.contains("هزینه") && amount > 0 -> {
                val t = Transaction(0, TransactionType.EXPENSE, amount, "", userMessage, System.currentTimeMillis())
                db.addTransaction(t)
                AIResponse(true, "✅ هزینه ${formatMoney(amount)} تومان ثبت شد", "add_transaction")
            }
            msg.contains("موجودی") || msg.contains("مانده") -> {
                val balance = db.getBalance()
                AIResponse(true, "💰 موجودی: ${formatMoney(balance)} تومان", "show_balance")
            }
            else -> AIResponse(true, "لطفاً دستور خود را واضح‌تر بیان کنید", "chat")
        }
    }
    
    private fun extractReminderCommandManually(userMessage: String): AIResponse {
        val msg = userMessage.lowercase()
        val timeRegex = """(\d{1,2})\s*(صبح|ظهر|عصر|شب)""".toRegex()
        val match = timeRegex.find(msg)
        
        return if (match != null) {
            val hour = match.groupValues[1].toInt()
            val period = match.groupValues[2]
            val time24 = when (period) {
                "صبح" -> String.format("%02d:00", hour)
                "ظهر" -> "12:00"
                "عصر" -> String.format("%02d:00", hour + 12)
                "شب" -> String.format("%02d:00", if (hour < 12) hour + 12 else hour)
                else -> "09:00"
            }
            
            val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
            prefs.edit().putString("reminder_${System.currentTimeMillis()}", "$time24|$userMessage").apply()
            AIResponse(true, "✅ یادآوری ساعت $time24 ثبت شد", "add_reminder")
        } else {
            AIResponse(true, "لطفاً زمان را مشخص کنید (مثال: ساعت 9 صبح)", "chat")
        }
    }
    
    private fun extractMusicCommandManually(userMessage: String): AIResponse {
        val msg = userMessage.lowercase()
        val mood = when {
            msg.contains("شاد") -> "شاد"
            msg.contains("غمگین") -> "غمگین"
            msg.contains("آرام") -> "آرام"
            msg.contains("انرژی") || msg.contains("پرانرژی") -> "انرژی"
            msg.contains("عاشقانه") -> "عاشقانه"
            msg.contains("سنتی") -> "سنتی"
            else -> ""
        }
        
        return if (mood.isNotEmpty()) {
            AIResponse(true, "🎵 پلی‌لیست $mood ایجاد می‌شود", "create_playlist", mapOf("mood" to mood))
        } else {
            AIResponse(true, "چه نوع موسیقی می‌خواهید؟ (شاد، غمگین، آرام، ...)", "chat")
        }
    }
    
    private fun extractNavigationCommandManually(userMessage: String): AIResponse {
        val msg = userMessage.lowercase()
        val poiType = when {
            msg.contains("پمپ") || msg.contains("بنزین") -> "gas"
            msg.contains("رستوران") || msg.contains("غذا") -> "food"
            msg.contains("بیمارستان") || msg.contains("درمانگاه") -> "hospital"
            msg.contains("عابر بانک") || msg.contains("atm") -> "atm"
            msg.contains("پارکینگ") -> "parking"
            else -> ""
        }
        
        return if (poiType.isNotEmpty()) {
            AIResponse(true, "🗺️ جستجوی نزدیک‌ترین مکان...", "find_poi", mapOf("poi_type" to poiType))
        } else {
            AIResponse(true, "چه مکانی می‌خواهید پیدا کنم؟", "chat")
        }
    }
    
    private fun formatMoney(amount: Double): String {
        return String.format("%,.0f", amount)
    }
    
    data class AIResponse(
        val success: Boolean,
        val message: String,
        val action: String,
        val data: Map<String, Any> = emptyMap()
    )
}
