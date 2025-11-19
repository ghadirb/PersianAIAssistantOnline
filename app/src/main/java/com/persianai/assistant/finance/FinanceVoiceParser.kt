package com.persianai.assistant.finance

import java.util.Locale
import kotlin.math.roundToInt

sealed interface FinanceVoiceIntent {
    data class AddCheckIntent(
        val amount: Double,
        val recipient: String?,
        val dueDays: Int?
    ) : FinanceVoiceIntent

    data class AddInstallmentIntent(
        val title: String,
        val amount: Double,
        val monthlyAmount: Double?,
        val totalMonths: Int?
    ) : FinanceVoiceIntent

    data class AddExpenseIntent(
        val amount: Double,
        val description: String?
    ) : FinanceVoiceIntent

    object UnknownIntent : FinanceVoiceIntent
}

/**
 * Extremely lightweight text parser for Persian voice/text commands.
 * Looks for key words such as "چک", "قسط", "هزینه" and extracts digits.
 */
object FinanceVoiceParser {

    private val numberRegex = Regex("[0-9]+(?:,[0-9]{3})*")

    fun parse(rawText: String): FinanceVoiceIntent {
        val text = rawText.trim().lowercase(Locale.getDefault())
        if (text.isEmpty()) return FinanceVoiceIntent.UnknownIntent

        val numbers = numberRegex.findAll(text)
            .mapNotNull { it.value.replace(",", "").toDoubleOrNull() }
            .toList()

        return when {
            text.contains("چک") -> parseCheckIntent(text, numbers)
            text.contains("قسط") || text.contains("وام") -> parseInstallmentIntent(text, numbers)
            text.contains("هزینه") || text.contains("خرج") -> parseExpenseIntent(text, numbers)
            else -> FinanceVoiceIntent.UnknownIntent
        }
    }

    private fun parseCheckIntent(text: String, numbers: List<Double>): FinanceVoiceIntent {
        val amount = numbers.firstOrNull() ?: return FinanceVoiceIntent.UnknownIntent
        val dueDays = extractDays(text)
        val recipient = extractRecipient(text)
        return FinanceVoiceIntent.AddCheckIntent(amount, recipient, dueDays)
    }

    private fun parseInstallmentIntent(text: String, numbers: List<Double>): FinanceVoiceIntent {
        val amount = numbers.firstOrNull() ?: return FinanceVoiceIntent.UnknownIntent
        val monthlyAmount = numbers.getOrNull(1)
        val totalMonths = extractMonths(text)
        val title = when {
            text.contains("ماشین") -> "قسط ماشین"
            text.contains("خانه") || text.contains("مسکن") -> "قسط خانه"
            text.contains("موبایل") -> "قسط موبایل"
            else -> "قسط جدید"
        }
        return FinanceVoiceIntent.AddInstallmentIntent(title, amount, monthlyAmount, totalMonths)
    }

    private fun parseExpenseIntent(text: String, numbers: List<Double>): FinanceVoiceIntent {
        val amount = numbers.firstOrNull() ?: return FinanceVoiceIntent.UnknownIntent
        val description = text.substringAfter("هزینه", "").trim().ifEmpty { null }
        return FinanceVoiceIntent.AddExpenseIntent(amount, description)
    }

    private fun extractDays(text: String): Int? {
        val match = Regex("(\u0631\u0648\u0632|day)\\s*(\d+)").find(text)
        return match?.groupValues?.getOrNull(2)?.toIntOrNull()
    }

    private fun extractMonths(text: String): Int? {
        val match = Regex("(\u0645\u0627\u0647|month)\\s*(\d+)").find(text)
        return match?.groupValues?.getOrNull(2)?.toIntOrNull()
    }

    private fun extractRecipient(text: String): String? {
        val keywords = listOf("برای", "به", "واسه")
        keywords.forEach { key ->
            if (text.contains(key)) {
                val part = text.substringAfter(key).trim()
                if (part.isNotEmpty()) {
                    val words = part.split(" ")
                    return words.take(2).joinToString(" ")
                }
            }
        }
        return null
    }
}
