package com.persianai.assistant.activities

import android.os.Bundle
import android.view.View
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.persianai.assistant.databinding.ActivityChatBinding
import com.persianai.assistant.finance.CheckManager
import com.persianai.assistant.finance.FinanceManager
import com.persianai.assistant.finance.InstallmentManager
import com.persianai.assistant.models.MessageRole
import com.persianai.assistant.utils.PersianDateConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class AccountingChatActivity : BaseChatActivity() {

    private lateinit var financeManager: FinanceManager
    private lateinit var checkManager: CheckManager
    private lateinit var installmentManager: InstallmentManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar((binding as ActivityChatBinding).toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "چت با دستیار حسابداری"

        financeManager = FinanceManager(this)
        checkManager = CheckManager(this)
        installmentManager = InstallmentManager(this)

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
        3) ثبت چک: {"action":"add_check", "checkNumber":"شماره", "amount":مبلغ, "issuer":"صادرکننده", "recipient":"دریافت‌کننده", "dueDate":"YYYY/MM/DD", "bankName":"نام بانک", "description":"توضیح"}
        4) ثبت قسط: {"action":"add_installment", "title":"عنوان", "totalAmount":کل, "installmentAmount":هر‌قسط, "totalInstallments":تعداد, "startDate":"YYYY/MM/DD", "paymentDay":روز, "recipient":"دریافت‌کننده", "description":"توضیح"}
        """
    }

    override suspend fun handleRequest(text: String): String {
        val responseJson = super.handleRequest(text)
        return withContext(Dispatchers.Main) {
            try {
                // استخراج JSON از پاسخ
                val jsonStr = extractJsonFromResponse(responseJson)
                val json = Gson().fromJson(jsonStr, JsonObject::class.java)
                val action = json.get("action").asString

                when (action) {
                    "add_income" -> {
                        val amount = json.get("amount").asDouble
                        val description = json.get("description").asString
                        financeManager.addTransaction(amount, "income", "درآمد", description)
                        "✅ درآمد «$description» به مبلغ ${String.format("%,.0f", amount)} تومان ثبت شد."
                    }
                    "add_expense" -> {
                        val amount = json.get("amount").asDouble
                        val description = json.get("description").asString
                        financeManager.addTransaction(amount, "expense", "هزینه", description)
                        "✅ هزینه «$description» به مبلغ ${String.format("%,.0f", amount)} تومان ثبت شد."
                    }
                    "add_check" -> {
                        val checkNumber = json.get("checkNumber").asString
                        val amount = json.get("amount").asDouble
                        val issuer = json.get("issuer").asString
                        val recipient = json.get("recipient").asString
                        val dueDateStr = json.get("dueDate").asString
                        val bankName = json.get("bankName").asString
                        val description = json.get("description").asString
                        
                        val dueDate = parseDateStringToMillis(dueDateStr)
                        checkManager.addCheck(
                            checkNumber = checkNumber,
                            amount = amount,
                            issuer = issuer,
                            recipient = recipient,
                            issueDate = System.currentTimeMillis(),
                            dueDate = dueDate,
                            bankName = bankName,
                            accountNumber = "",
                            description = description
                        )
                        "✅ چک شماره $checkNumber به مبلغ ${String.format("%,.0f", amount)} تومان ثبت شد."
                    }
                    "add_installment" -> {
                        val title = json.get("title").asString
                        val totalAmount = json.get("totalAmount").asDouble
                        val installmentAmount = json.get("installmentAmount").asDouble
                        val totalInstallments = json.get("totalInstallments").asInt
                        val startDateStr = json.get("startDate").asString
                        val paymentDay = json.get("paymentDay").asInt
                        val recipient = json.get("recipient").asString
                        val description = json.get("description").asString
                        
                        val startDate = parseDateStringToMillis(startDateStr)
                        installmentManager.addInstallment(
                            title = title,
                            totalAmount = totalAmount,
                            installmentAmount = installmentAmount,
                            totalInstallments = totalInstallments,
                            startDate = startDate,
                            paymentDay = paymentDay,
                            recipient = recipient,
                            description = description
                        )
                        "✅ قسط «$title» با $totalInstallments قسط ثبت شد."
                    }
                    else -> responseJson
                }
            } catch (e: Exception) {
                responseJson
            }
        }
    }
    
    private fun extractJsonFromResponse(response: String): String {
        val startIdx = response.indexOf('{')
        val endIdx = response.lastIndexOf('}')
        
        return if (startIdx >= 0 && endIdx > startIdx) {
            response.substring(startIdx, endIdx + 1)
        } else {
            response
        }
    }
    
    private fun parseDateStringToMillis(dateStr: String): Long {
        return try {
            val parts = dateStr.split("/")
            if (parts.size == 3) {
                val year = parts[0].toInt()
                val month = parts[1].toInt()
                val day = parts[2].toInt()
                
                val calendar = Calendar.getInstance()
                calendar.set(year, month - 1, day, 0, 0, 0)
                calendar.timeInMillis
            } else {
                System.currentTimeMillis()
            }
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
