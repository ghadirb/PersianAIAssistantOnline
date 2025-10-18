package com.persianai.assistant.data

data class Check(
    val id: Long = 0,
    val amount: Double,
    val checkNumber: String,
    val issuer: String,
    val dueDate: Long,
    val status: CheckStatus = CheckStatus.PENDING,
    val type: CheckType,
    val description: String = "",
    val createdDate: Long = System.currentTimeMillis()
)

enum class CheckType {
    RECEIVED,  // چک دریافتی
    ISSUED     // چک صادره
}

data class Installment(
    val id: Long = 0,
    val totalAmount: Double,
    val monthlyAmount: Double,
    val totalMonths: Int,
    val paidMonths: Int = 0,
    val startDate: Long,
    val description: String,
    val category: String = "",
    val reminderEnabled: Boolean = true,
    val createdDate: Long = System.currentTimeMillis()
) {
    val remainingAmount: Double
        get() = totalAmount - (monthlyAmount * paidMonths)
    
    val remainingMonths: Int
        get() = totalMonths - paidMonths
    
    val nextPaymentDate: Long
        get() {
            val calendar = java.util.Calendar.getInstance()
            calendar.timeInMillis = startDate
            calendar.add(java.util.Calendar.MONTH, paidMonths)
            return calendar.timeInMillis
        }
}
