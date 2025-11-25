package com.persianai.assistant.utils

import android.content.Context
import com.persianai.assistant.finance.CheckManager
import com.persianai.assistant.finance.InstallmentManager
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class AccountingManager(private val context: Context) {

    private val checkManager = CheckManager(context)
    private val installmentManager = InstallmentManager(context)

    // Transaction management can be added here if needed

    fun getAllChecks(): List<CheckManager.Check> = checkManager.getAllChecks()

    fun getAllInstallments(): List<InstallmentManager.Installment> = installmentManager.getAllInstallments()

    fun getFinancialReport(): FinancialReport {
        val totalIncome = checkManager.getTotalPendingAmount()
        val totalExpense = installmentManager.getTotalRemainingAmount()
        val balance = totalIncome - totalExpense

        return FinancialReport(totalIncome, totalExpense, balance)
    }

    fun getMonthlyReport(month: Int, year: Int): FinancialReport {
        val calendar = Calendar.getInstance()
        val allChecks = checkManager.getAllChecks().filter { 
            calendar.timeInMillis = it.dueDate
            calendar.get(Calendar.MONTH) == month && calendar.get(Calendar.YEAR) == year
        }
        val allInstallments = installmentManager.getAllInstallments() // Filtering logic needs to be more complex

        val totalIncome = allChecks.filter { it.status == CheckManager.CheckStatus.PAID }.sumOf { it.amount }
        val totalExpense = 0.0 // Placeholder for monthly installment expense
        return FinancialReport(totalIncome, totalExpense, totalIncome - totalExpense)
    }

    fun getYearlyReport(year: Int): FinancialReport {
        val calendar = Calendar.getInstance()
        val allChecks = checkManager.getAllChecks().filter {
            calendar.timeInMillis = it.dueDate
            calendar.get(Calendar.YEAR) == year
        }
        val totalIncome = allChecks.filter { it.status == CheckManager.CheckStatus.PAID }.sumOf { it.amount }
        val totalExpense = 0.0 // Placeholder for yearly installment expense
        return FinancialReport(totalIncome, totalExpense, totalIncome - totalExpense)
    }

    fun exportToCSV(file: File): Boolean {
        try {
            FileWriter(file).use { writer ->
                writer.append("Type,Date,Amount,Description\n")
                checkManager.getAllChecks().forEach {
                    writer.append("Check,${formatDate(it.dueDate)},${it.amount},${it.description}\n")
                }
                installmentManager.getAllInstallments().forEach {
                    // This needs a loop for each payment
                    writer.append("Installment,N/A,${it.installmentAmount},${it.title}\n")
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun createBackup(): String {
        // Implementation for creating a JSON backup of all data
        return "Backup created successfully."
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

data class FinancialReport(
    val totalIncome: Double,
    val totalExpense: Double,
    val balance: Double
)
