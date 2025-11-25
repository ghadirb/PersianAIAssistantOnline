package com.persianai.assistant.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.persianai.assistant.models.AIModel
import com.persianai.assistant.models.ChatMessage
import com.persianai.assistant.models.MessageRole
import com.persianai.assistant.utils.CheckManager
import com.persianai.assistant.utils.InstallmentManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AIFinanceProcessor(
    private val aiClient: AIClient,
    private val checkManager: CheckManager,
    private val installmentManager: InstallmentManager
) {

    private val gson = Gson()

    suspend fun processCommand(command: String): String {
        val systemPrompt = getSystemPrompt()
        val messages = listOf(ChatMessage(role = MessageRole.USER, content = command))

        return try {
            val response = aiClient.sendMessage(AIModel.GPT_4O_MINI, messages, systemPrompt)
            val jsonResponse = gson.fromJson(response.content, JsonObject::class.java)
            executeAction(jsonResponse)
        } catch (e: Exception) {
            "خطا در پردازش دستور: ${e.message}"
        }
    }

    private fun executeAction(json: JsonObject): String {
        return when (json.get("action").asString) {
            "add_check" -> {
                val dueDate = parseDate(json.get("due_date").asString)
                val check = checkManager.addCheck(
                    amount = json.get("amount").asLong,
                    dueDate = dueDate,
                    recipient = json.get("recipient").asString
                )
                "✅ چک برای ${check.recipient} به مبلغ ${check.amount} در تاریخ ${SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(check.dueDate))} با موفقیت ثبت شد."
            }
            "add_installment" -> {
                val startDate = parseDate(json.get("start_date").asString)
                val installment = installmentManager.addInstallment(
                    title = json.get("title").asString,
                    totalAmount = json.get("total_amount").asDouble,
                    installmentAmount = json.get("installment_amount").asDouble,
                    totalInstallments = json.get("total_installments").asInt,
                    startDate = startDate,
                    paymentDay = json.get("payment_day").asInt
                )
                "✅ قسط '${installment.title}' با موفقیت ثبت شد."
            }
            else -> "دستور شناسایی نشد."
        }
    }

    private fun parseDate(dateString: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
            format.parse(dateString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun getSystemPrompt(): String {
        return """
        You are an intelligent financial assistant. Your task is to analyze user requests and convert them into a specific JSON structure to perform financial actions. 
        Supported actions:
        1) Add a check: {"action":"add_check", "amount":<long>, "due_date":"YYYY/MM/DD", "recipient":"<string>"}
        2) Add an installment: {"action":"add_installment", "title":"<string>", "total_amount":<double>, "installment_amount":<double>, "total_installments":<int>, "start_date":"YYYY/MM/DD", "payment_day":<int>}
        If information is missing, ask the user for clarification. Always return a valid JSON object.
        """
    }
}
