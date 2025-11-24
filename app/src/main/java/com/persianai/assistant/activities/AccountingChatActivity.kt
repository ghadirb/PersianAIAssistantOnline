package com.persianai.assistant.activities

import android.os.Bundle
import android.view.View
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.persianai.assistant.data.AccountingDB
import com.persianai.assistant.databinding.ActivityChatBinding
import com.persianai.assistant.models.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AccountingChatActivity : BaseChatActivity() {

    private lateinit var db: AccountingDB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar((binding as ActivityChatBinding).toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "چت با دستیار حسابداری"

        db = AccountingDB(this)

        setupChatUI()

        addMessage(com.persianai.assistant.models.ChatMessage(role = MessageRole.ASSISTANT, content = "سلام! می‌تونم درآمد، هزینه، چک یا قسط جدید برات ثبت کنم. فقط کافیه بگی."))
    }

    override fun getRecyclerView(): androidx.recyclerview.widget.RecyclerView = (binding as ActivityChatBinding).recyclerView
    override fun getMessageInput(): com.google.android.material.textfield.TextInputEditText = (binding as ActivityChatBinding).messageInput
    override fun getSendButton(): View = (binding as ActivityChatBinding).sendButton
    override fun getVoiceButton(): View = (binding as ActivityChatBinding).voiceButton

    override fun getSystemPrompt(): String {
        return """
        شما یک دستیار هوشمند متخصص در زمینه حسابداری شخصی هستید.
        وظیفه شما تحلیل درخواست‌های کاربر و تبدیل آن‌ها به ساختار JSON برای ثبت تراکنش‌های مالی است.
        اکشن‌های پشتیبانی‌شده:
        1) ثبت درآمد: {"action":"add_income", "amount":مبلغ, "description":"توضیح"}
        2) ثبت هزینه: {"action":"add_expense", "amount":مبلغ, "description":"توضیح"}
        """
    }

    override suspend fun handleRequest(text: String): String {
        val responseJson = super.handleRequest(text)
        return withContext(Dispatchers.Main) {
            try {
                val json = Gson().fromJson(responseJson, JsonObject::class.java)
                val action = json.get("action").asString
                val amount = json.get("amount").asLong
                val description = json.get("description").asString

                when (action) {
                    "add_income" -> {
                        db.addTransaction(com.persianai.assistant.data.Transaction(type = com.persianai.assistant.data.TransactionType.INCOME, amount = amount.toDouble(), category = "", description = description))
                        "✅ درآمد «$description» به مبلغ $amount تومان ثبت شد."
                    }
                    "add_expense" -> {
                        db.addTransaction(com.persianai.assistant.data.Transaction(type = com.persianai.assistant.data.TransactionType.EXPENSE, amount = amount.toDouble(), category = "", description = description))
                        "✅ هزینه «$description» به مبلغ $amount تومان ثبت شد."
                    }
                    else -> responseJson
                }
            } catch (e: Exception) {
                responseJson
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
