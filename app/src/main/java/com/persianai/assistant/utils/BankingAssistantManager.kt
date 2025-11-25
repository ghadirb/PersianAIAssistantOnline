package com.persianai.assistant.utils

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.persianai.assistant.finance.CheckManager
import com.persianai.assistant.finance.InstallmentManager

class BankingAssistantManager(private val context: Context) {

    private val checkManager = CheckManager(context)
    private val installmentManager = InstallmentManager(context)

    fun getFinancialReport(): FinancialReport {
        val totalIncome = checkManager.getTotalPendingAmount()
        val totalExpense = installmentManager.getTotalRemainingAmount()
        val balance = totalIncome - totalExpense
        return FinancialReport(totalIncome, totalExpense, balance)
    }

    fun checkAndNotify() {
        checkManager.getChecksNeedingAlert().forEach { check ->
            NotificationHelper.showReminderNotification(
                context,
                "check_${check.id}",
                "یادآوری سررسید چک",
                "چک شماره ${check.checkNumber} به مبلغ ${check.amount} به زودی سررسید می‌شود."
            )
        }

        installmentManager.getInstallmentsNeedingAlert().forEach { (installment, nextPaymentDate) ->
            NotificationHelper.showReminderNotification(
                context,
                "installment_${installment.id}",
                "یادآوری پرداخت قسط",
                "قسط '${installment.title}' به مبلغ ${installment.installmentAmount} نزدیک است."
            )
        }
    }
}

class FinancialReportWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val manager = BankingAssistantManager(applicationContext)
        manager.checkAndNotify()
        return Result.success()
    }
}
