package com.persianai.assistant.utils

import android.content.Context
import com.persianai.assistant.finance.CheckManager
import com.persianai.assistant.finance.InstallmentManager

class AccountingManager(context: Context) {

    private val checkManager = CheckManager(context)
    private val installmentManager = InstallmentManager(context)

    fun getFinancialReport(): FinancialReport {
        val upcomingChecks = checkManager.getUpcomingChecks()
        val overdueChecks = checkManager.getOverdueChecks()
        val upcomingInstallments = installmentManager.getUpcomingInstallments()

        val totalCheckAmount = upcomingChecks.sumOf { it.amount }.toLong()
        val totalInstallmentAmount = upcomingInstallments.sumOf { it.totalAmount }.toLong()

        return FinancialReport(
            totalCheckAmount = totalCheckAmount,
            totalInstallmentAmount = totalInstallmentAmount,
            upcomingChecksCount = upcomingChecks.size,
            overdueChecksCount = overdueChecks.size
        )
    }
}
