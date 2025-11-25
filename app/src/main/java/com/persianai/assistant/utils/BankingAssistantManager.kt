package com.persianai.assistant.utils

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.persianai.assistant.data.Check
import com.persianai.assistant.data.Installment
import com.persianai.assistant.finance.CheckManager
import com.persianai.assistant.finance.InstallmentManager

class BankingAssistantManager(private val context: Context) {

    private val checkManager = CheckManager(context)
    private val installmentManager = InstallmentManager(context)

    fun getFinancialReport(): FinancialReport {
        val upcomingChecks = checkManager.getUpcomingChecks()
        val overdueChecks = checkManager.getOverdueChecks()
        val upcomingInstallments = installmentManager.getUpcomingInstallments()

        return FinancialReport(
            totalCheckAmount = upcomingChecks.sumOf { it.amount }.toLong(),
            totalInstallmentAmount = upcomingInstallments.sumOf { it.totalAmount }.toLong(),
            upcomingChecksCount = upcomingChecks.size,
            overdueChecksCount = overdueChecks.size
        )
    }

    fun getUpcomingChecks(): List<Check> = checkManager.getUpcomingChecks()
    fun getUpcomingInstallments(): List<Installment> = installmentManager.getUpcomingInstallments()

    fun checkAndNotify() {
        // Notify for upcoming checks
        checkManager.getUpcomingChecks(1).forEach { check ->
            NotificationHelper.showReminderNotification(
                context,
                "check_${check.id}",
                "سررسید چک",
                "چک به مبلغ ${check.amount} برای ${check.recipient} امروز سررسید می‌شود."
            )
        }

        // Notify for upcoming installments
        installmentManager.getUpcomingInstallments(1).forEach { installment ->
            NotificationHelper.showReminderNotification(
                context,
                "installment_${installment.id}",
                "سررسید قسط",
                "قسط ${installment.title} به مبلغ ${installment.installmentAmount} امروز سررسید می‌شود."
            )
        }
    }
}

data class FinancialReport(
    val totalCheckAmount: Long,
    val totalInstallmentAmount: Long,
    val upcomingChecksCount: Int,
    val overdueChecksCount: Int
)

class CheckReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val checkId = inputData.getString("checkId") ?: return Result.failure()
        val message = inputData.getString("message") ?: ""
        NotificationHelper.showReminderNotification(applicationContext, "check_$checkId", "سررسید چک", message)
        return Result.success()
    }
}

class InstallmentReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val installmentId = inputData.getString("installmentId") ?: return Result.failure()
        val message = inputData.getString("message") ?: ""
        NotificationHelper.showReminderNotification(applicationContext, "installment_$installmentId", "سررسید قسط", message)
        return Result.success()
    }
}
