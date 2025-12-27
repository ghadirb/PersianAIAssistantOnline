package com.persianai.assistant.core

import android.content.Context
import android.util.Log
import com.persianai.assistant.ai.OfflineIntentParser
import com.persianai.assistant.core.intent.*
import com.persianai.assistant.core.modules.*
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AIIntentController(private val context: Context) {

    // ماژول‌ها
    private val assistantModule = AssistantModule(context)
    private val reminderModule = ReminderModule(context)
    private val navigationModule = NavigationModule(context)
    private val financeModule = FinanceModule(context)
    private val educationModule = EducationModule(context)
    private val callModule = CallModule(context)
    private val weatherModule = WeatherModule(context)
    private val musicModule = MusicModule(context)
    
    // تشخیص‌دهنده Intent پیشرفته
    private val detector = EnhancedIntentDetector(context)

    suspend fun handle(request: AIIntentRequest): AIIntentResult = withContext(Dispatchers.Default) {
        logIntent(request)
        
        val result = when (val i = request.intent) {
            // Assistant & Chat
            is AssistantChatIntent -> assistantModule.execute(request, i)
            
            // Reminders
            is ReminderCreateIntent -> reminderModule.execute(request, i)
            is ReminderListIntent -> reminderModule.execute(request, i)
            is ReminderDeleteIntent -> reminderModule.execute(request, i)
            is ReminderUpdateIntent -> reminderModule.execute(request, i)
            
            // Navigation
            is NavigationSearchIntent -> navigationModule.execute(request, i)
            is NavigationStartIntent -> navigationModule.execute(request, i)
            
            // Finance
            is FinanceTrackIntent -> financeModule.execute(request, i)
            is FinanceReportIntent -> financeModule.execute(request, i)
            
            // Education
            is EducationAskIntent -> educationModule.execute(request, i)
            is EducationGenerateQuestionIntent -> educationModule.execute(request, i)
            
            // Call
            is CallSmartIntent -> callModule.execute(request, i)
            
            // Weather
            is WeatherCheckIntent -> weatherModule.execute(request, i)
            
            // Music
            is MusicPlayIntent -> musicModule.execute(request, i)
            
            UnknownIntent -> AIIntentResult(
                text = "متوجه منظورت نشدم. لطفاً واضح‌تر بگو.",
                intentName = i.name
            )
        }
        
        result
    }

    fun detectIntentFromText(text: String, context: String? = null): AIIntent {
        val t = text.trim()
        if (t.isBlank()) return UnknownIntent

        return try {
            detector.detectIntent(text)
        } catch (e: Exception) {
            Log.e("AIIntentController", "Error detecting intent", e)
            AssistantChatIntent(rawText = t)
        }
    }

    private fun logIntent(request: AIIntentRequest) {
        try {
            Log.d(
                "AIIntentController",
                "Intent=${request.intent.name} | " +
                "Source=${request.source.name} | " +
                "Mode=${request.workingModeName ?: "N/A"}"
            )
        } catch (_: Exception) {}
    }
}