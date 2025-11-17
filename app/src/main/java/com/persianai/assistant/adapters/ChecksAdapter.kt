package com.persianai.assistant.adapters

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
            checkNumberText.text = "چک ${check.checkNumber}"
            amountText.text = "${formatMoney(check.amount)} تومان"
            dueDateText.text = "سررسید: ${dateFormat.format(Date(check.dueDate))}"
            bankText.text = check.bankName
            issuerText.text = "صادرکننده: ${check.issuer}"
            
            statusText.text = when (check.status) {
                CheckManager.CheckStatus.PENDING -> "⏳ در انتظار"
                CheckManager.CheckStatus.PAID -> "✅ پرداخت شده"
                CheckManager.CheckStatus.BOUNCED -> "❌ برگشتی"
                CheckManager.CheckStatus.CANCELLED -> "🚫 لغو شده"
            }
            
            // رنگ‌بندی بر اساس وضعیت
            when (check.status) {
                CheckManager.CheckStatus.PENDING -> {
                    val daysRemaining = ((check.dueDate - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt()
                    if (daysRemaining <= check.alertDays) {
                        cardView.setCardBackgroundColor(0xFFFFEBEE.toInt()) // قرمز روشن
                        alertIcon.text = "⚠️"
                        alertText.text = "$daysRemaining روز تا سررسید"
                    } else {
                        cardView.setCardBackgroundColor(0xFFFFFFFF.toInt())
                    }
                }
                CheckManager.CheckStatus.PAID -> cardView.setCardBackgroundColor(0xFFE8F5E9.toInt())
                CheckManager.CheckStatus.BOUNCED -> cardView.setCardBackgroundColor(0xFFFFCDD2.toInt())
                CheckManager.CheckStatus.CANCELLED -> cardView.setCardBackgroundColor(0xFFECEFF1.toInt())
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
