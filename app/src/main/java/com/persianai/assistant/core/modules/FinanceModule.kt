package com.persianai.assistant.core.modules

import android.content.Context
import android.content.Intent
import android.util.Log
import com.persianai.assistant.activities.ChecksManagementActivity
import com.persianai.assistant.activities.InstallmentsManagementActivity
import com.persianai.assistant.core.AIIntentRequest
import com.persianai.assistant.core.AIIntentResult
import com.persianai.assistant.core.intent.AIIntent
import com.persianai.assistant.core.intent.FinanceReportIntent
import com.persianai.assistant.core.intent.FinanceTrackIntent
import com.persianai.assistant.finance.FinanceManager

class FinanceModule(private val context: Context) : BaseModule(context) {
    override val moduleName: String = "Finance"
    
    private val financeManager = FinanceManager(context)

    override suspend fun canHandle(intent: AIIntent): Boolean {
        return intent is FinanceTrackIntent || intent is FinanceReportIntent
    }

    override suspend fun execute(request: AIIntentRequest, intent: AIIntent): AIIntentResult {
        return when (intent) {
            is FinanceTrackIntent -> handleTrack(request, intent)
            is FinanceReportIntent -> handleReport(request, intent)
            else -> createResult("نوع Intent نشناخته‌شده", intent.name, false)
        }
    }

    private suspend fun handleTrack(request: AIIntentRequest, intent: FinanceTrackIntent): AIIntentResult {
        val type = intent.type ?: "all"
        
        logAction("TRACK", "type=$type")
        
        return try {
            val summary = financeManager.getSummary()
            
            val text = when (type.lowercase()) {
                "income", "درآمد" -> {
                    "💰 درآمدهای شما:\n${summary["income"] ?: "بدون درآمد ثبت‌شده"}"
                }
                "expense", "هزینه", "خرج" -> {
                    "💸 هزینه‌های شما:\n${summary["expense"] ?: "بدون هزینه ثبت‌شده"}"
                }
                else -> {
                    "📊 خلاصه مالی:\n${summary["total"] ?: "اطلاعات موجود نیست"}"
                }
            }
            
            createResult(
                text = text,
                intentName = intent.name,
                actionType = "show_finance_summary"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error tracking finance", e)
            createResult(
                text = "❌ خطا در دریافت اطلاعات مالی",
                intentName = intent.name,
                success = false
            )
        }
    }

    private suspend fun handleReport(request: AIIntentRequest, intent: FinanceReportIntent): AIIntentResult {
        val timeRange = intent.timeRange ?: "month"
        
        logAction("REPORT", "timeRange=$timeRange")
        
        try {
            val report = financeManager.generateReport(timeRange)
            
            return createResult(
                text = "📋 گزارش مالی $timeRange:\n$report",
                intentName = intent.name,
                actionType = "show_finance_report",
                actionData = timeRange
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating report", e)
            return createResult(
                text = "❌ خطا در تولید گزارش",
                intentName = intent.name,
                success = false
            )
        }
    }
}