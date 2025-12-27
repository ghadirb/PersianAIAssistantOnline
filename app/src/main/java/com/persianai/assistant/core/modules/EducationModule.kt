package com.persianai.assistant.core.modules

import android.content.Context
import android.util.Log
import com.persianai.assistant.ai.AdvancedPersianAssistant
import com.persianai.assistant.core.AIIntentRequest
import com.persianai.assistant.core.AIIntentResult
import com.persianai.assistant.core.intent.AIIntent
import com.persianai.assistant.core.intent.EducationAskIntent
import com.persianai.assistant.core.intent.EducationGenerateQuestionIntent

class EducationModule(context: Context) : BaseModule(context) {
    override val moduleName: String = "Education"
    
    private val assistant = AdvancedPersianAssistant(context)

    override suspend fun canHandle(intent: AIIntent): Boolean {
        return intent is EducationAskIntent || intent is EducationGenerateQuestionIntent
    }

    override suspend fun execute(request: AIIntentRequest, intent: AIIntent): AIIntentResult {
        return when (intent) {
            is EducationAskIntent -> handleAsk(request, intent)
            is EducationGenerateQuestionIntent -> handleGenerateQuestion(request, intent)
            else -> createResult("نوع Intent نشناخته‌شده", intent.name, false)
        }
    }

    private suspend fun handleAsk(request: AIIntentRequest, intent: EducationAskIntent): AIIntentResult {
        val topic = intent.topic ?: intent.rawText
        
        logAction("ASK", "topic=$topic")
        
        val prompt = """
            شما یک معلم خصوصی بسیار دانشمند و صبور هستید.
            موضوع: $topic
            
            لطفاً:
            1. پاسخ واضح و ساده بدهید
            2. مثال‌های واقعی از زندگی روزمره استفاده کنید
            3. در صورت امکان، مراحل حل مسئله را شرح دهید
            4. در انتهای پاسخ، یک سوال فکری مطرح کنید
        """.trimIndent()
        
        return try {
            val response = assistant.processRequest(prompt)
            
            createResult(
                text = "📚 پاسخ آموزشی:\n\n${response.text}",
                intentName = intent.name,
                actionType = "education_answer"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling education ask", e)
            createResult(
                text = "❌ خطا در دریافت پاسخ آموزشی",
                intentName = intent.name,
                success = false
            )
        }
    }

    private suspend fun handleGenerateQuestion(request: AIIntentRequest, intent: EducationGenerateQuestionIntent): AIIntentResult {
        val topic = intent.topic ?: intent.rawText
        val level = intent.level ?: "متوسط"
        
        logAction("GENERATE_QUESTION", "topic=$topic level=$level")
        
        val prompt = """
            لطفاً یک سوال آموزشی در حد $level درباره "$topic" بسازید.
            
            فرمت پاسخ:
            📌 سوال: [متن سوال]
            
            💡 راهنمایی: [راهنمای حل]
            
            ✅ پاسخ: [پاسخ صحیح]
        """.trimIndent()
        
        return try {
            val response = assistant.processRequest(prompt)
            
            createResult(
                text = "❓ سوال تولیدشده:\n\n${response.text}",
                intentName = intent.name,
                actionType = "education_question"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating question", e)
            createResult(
                text = "❌ خطا در تولید سوال",
                intentName = intent.name,
                success = false
            )
        }
    }
}