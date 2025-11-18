package com.persianai.assistant.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.persianai.assistant.databinding.ItemCheckBinding
import com.persianai.assistant.finance.CheckManager
import java.text.SimpleDateFormat
import java.util.*

class ChecksAdapter(
    private val checks: List<CheckManager.Check>,
    private val onItemClick: (CheckManager.Check) -> Unit
) : RecyclerView.Adapter<ChecksAdapter.ViewHolder>() {
    
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale("fa"))
    
    inner class ViewHolder(val binding: ItemCheckBinding) : RecyclerView.ViewHolder(binding.root)
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCheckBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val check = checks[position]
        
        with(holder.binding) {
            // شماره چک و مبلغ
            checkNumberText.text = "💳 چک ${check.checkNumber}"
            amountText.text = "${formatMoney(check.amount)} تومان"
            
            // دارنده/گیرنده
            holderNameText.text = "در وجه: ${check.recipient}"
            
            // تاریخ سررسید
            dueDateText.text = "📅 سررسید: ${dateFormat.format(Date(check.dueDate))}"

            // نوع/بانک روی typeText
            typeText.text = if (check.bankName.isNotBlank()) {
                "بانک: ${check.bankName}"
            } else {
                "چک"
            }

            // وضعیت روی چیپ وضعیت
            statusChip.text = when (check.status) {
                CheckManager.CheckStatus.PENDING -> "⏳ در انتظار"
                CheckManager.CheckStatus.PAID -> "✅ پرداخت شده"
                CheckManager.CheckStatus.BOUNCED -> "❌ برگشتی"
                CheckManager.CheckStatus.CANCELLED -> "🚫 لغو شده"
            }

            // رنگ پس‌زمینه کارت و متن هشدار بر اساس وضعیت و فاصله تا سررسید
            val daysRemaining = ((check.dueDate - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt()
            when (check.status) {
                CheckManager.CheckStatus.PENDING -> {
                    if (daysRemaining <= check.alertDays) {
                        root.setCardBackgroundColor(Color.parseColor("#FFEBEE")) // قرمز روشن
                        alertText.text = "⚠️ $daysRemaining روز تا سررسید"
                    } else {
                        root.setCardBackgroundColor(Color.WHITE)
                        alertText.text = ""
                    }
                }
                CheckManager.CheckStatus.PAID -> {
                    root.setCardBackgroundColor(Color.parseColor("#E8F5E9"))
                    alertText.text = "✅ پرداخت شده"
                }
                CheckManager.CheckStatus.BOUNCED -> {
                    root.setCardBackgroundColor(Color.parseColor("#FFCDD2"))
                    alertText.text = "❌ برگشتی"
                }
                CheckManager.CheckStatus.CANCELLED -> {
                    root.setCardBackgroundColor(Color.parseColor("#ECEFF1"))
                    alertText.text = "🚫 لغو شده"
                }
            }

            root.setOnClickListener {
                onItemClick(check)
            }
        }
    }
    
    override fun getItemCount() = checks.size
    
    private fun formatMoney(amount: Double): String {
        return String.format("%,.0f", amount)
    }
}
