package com.persianai.assistant.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.persianai.assistant.databinding.ItemInstallmentBinding
import com.persianai.assistant.finance.InstallmentManager
import java.text.SimpleDateFormat
import java.util.*

class InstallmentsAdapter(
    private val installments: List<InstallmentManager.Installment>,
    private val onItemClick: (InstallmentManager.Installment) -> Unit
) : RecyclerView.Adapter<InstallmentsAdapter.ViewHolder>() {
    
    inner class ViewHolder(val binding: ItemInstallmentBinding) : RecyclerView.ViewHolder(binding.root)
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInstallmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val installment = installments[position]
        
        with(holder.binding) {
            titleText.text = installment.title
            amountText.text = "${formatMoney(installment.installmentAmount)} تومان / ماه"
            recipientText.text = "دریافت‌کننده: ${installment.recipient}"
            
            val progress = (installment.paidInstallments.toFloat() / installment.totalInstallments * 100).toInt()
            progressText.text = "$progress% (${installment.paidInstallments}/${installment.totalInstallments})"
            progressBar.progress = progress
            
            val remaining = installment.totalInstallments - installment.paidInstallments
            val remainingAmount = remaining * installment.installmentAmount
            remainingText.text = "باقیمانده: ${formatMoney(remainingAmount)} تومان در $remaining قسط"
            
            // محاسبه تاریخ قسط بعدی
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = installment.startDate
            calendar.add(Calendar.MONTH, installment.paidInstallments)
            calendar.set(Calendar.DAY_OF_MONTH, installment.paymentDay)
            
            val nextPayment = calendar.timeInMillis
            val daysRemaining = ((nextPayment - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt()
            
            if (remaining > 0) {
                if (daysRemaining <= installment.alertDaysBefore) {
                    nextPaymentText.text = "⚠️ قسط بعدی: $daysRemaining روز دیگر"
                    cardView.setCardBackgroundColor(0xFFFFF3E0.toInt()) // نارنجی روشن
                } else {
                    nextPaymentText.text = "📅 قسط بعدی: $daysRemaining روز دیگر"
                    cardView.setCardBackgroundColor(0xFFFFFFFF.toInt())
                }
            } else {
                nextPaymentText.text = "✅ تکمیل شده"
                cardView.setCardBackgroundColor(0xFFE8F5E9.toInt()) // سبز روشن
            }
            
            root.setOnClickListener {
                onItemClick(installment)
            }
        }
    }
    
    override fun getItemCount() = installments.size
    
    private fun formatMoney(amount: Double): String {
        return String.format("%,.0f", amount)
    }
}
