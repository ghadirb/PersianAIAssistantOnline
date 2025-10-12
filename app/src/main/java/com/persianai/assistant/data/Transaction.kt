package com.persianai.assistant.data

data class Transaction(
    val id: Long = 0,
    val type: TransactionType,
    val amount: Double,
    val category: String,
    val description: String,
    val date: Long = System.currentTimeMillis(),
    val checkNumber: String? = null,
    val checkStatus: CheckStatus? = null,
    val installmentId: Long? = null,
    val installmentNumber: Int? = null,
    val totalInstallments: Int? = null
)

enum class TransactionType {
    INCOME, EXPENSE, CHECK_IN, CHECK_OUT, INSTALLMENT
}

enum class CheckStatus {
    PENDING, CASHED, BOUNCED
}
