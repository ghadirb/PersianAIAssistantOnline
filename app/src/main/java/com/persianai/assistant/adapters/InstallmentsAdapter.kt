package com.persianai.assistant.adapters

import android.graphics.Color
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
            // عنوان و مبالغ مطابق layout فعلی
            titleText.text = installment.title
            totalAmountText.text = formatMoney(installment.totalAmount)
            monthlyAmountText.text = formatMoney(installment.installmentAmount)

            // پیشرفت اقساط
            val progressPercent = (installment.paidInstallments.toFloat() / installment.totalInstallments * 100).toInt()
            progressText.text = "پرداخت: ${installment.paidInstallments} از ${installment.totalInstallments}"
            progressPercentText.text = "$progressPercent%"
            progressBar.progress = progressPercent

            // مبلغ و تعداد اقساط باقی‌مانده
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

            // برچسب و رنگ پس‌زمینه بر اساس نزدیک بودن قسط بعدی
            val nextPaymentLabel: String = if (remaining > 0) {
                if (daysRemaining <= installment.alertDaysBefore) {
                    root.setCardBackgroundColor(Color.parseColor("#FFF3E0")) // نارنجی روشن
                    "⚠️ قسط بعدی: $daysRemaining روز دیگر"
                } else {
                    root.setCardBackgroundColor(Color.WHITE)
                    "📅 قسط بعدی: $daysRemaining روز دیگر"
                }
            } else {
                root.setCardBackgroundColor(Color.parseColor("#E8F5E9")) // سبز روشن
                "✅ تکمیل شده"
            }

            // افزودن وضعیت قسط بعدی به متن باقیمانده
            remainingText.text = "${remainingText.text}\n$nextPaymentLabel"

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
