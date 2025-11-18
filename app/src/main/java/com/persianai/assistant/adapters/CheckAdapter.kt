package com.persianai.assistant.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.persianai.assistant.R
import com.persianai.assistant.models.Check
import com.persianai.assistant.models.CheckStatus
import java.text.SimpleDateFormat
import java.util.*

/**
 * آداپتور برای نمایش لیست چک‌ها
 */
class CheckAdapter(
    private val onCheckClick: (Check) -> Unit
) : ListAdapter<Check, CheckAdapter.ViewHolder>(DiffCallback()) {
    
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale("fa", "IR"))
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_check, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkNumberView: TextView = itemView.findViewById(R.id.checkNumberText)
        private val amountView: TextView = itemView.findViewById(R.id.amountText)
        private val holderNameView: TextView = itemView.findViewById(R.id.holderNameText)
        private val dueDateView: TextView = itemView.findViewById(R.id.dueDateText)
        private val typeView: TextView = itemView.findViewById(R.id.typeText)
        private val alertView: TextView = itemView.findViewById(R.id.alertText)
        private val statusChip: Chip = itemView.findViewById(R.id.statusChip)
        
        fun bind(check: Check) {
            // شماره چک
            checkNumberView.text = "💳 چک #${check.checkNumber}"

            // مبلغ
            amountView.text = "💰 ${String.format("%,.0f", check.amount)} تومان"

            // گیرنده
            holderNameView.text = "در وجه: ${check.recipient}"

            // تاریخ سررسید
            dueDateView.text = "📅 سررسید: ${dateFormat.format(check.dueDate)}"

            // نوع/بانک
            typeView.text = if (check.bankName.isNotBlank()) {
                "بانک: ${check.bankName}"
            } else {
                ""
            }

            // وضعیت روی چیپ
            statusChip.text = getStatusName(check.status)
            when (check.status) {
                CheckStatus.PENDING -> statusChip.setChipBackgroundColorResource(R.color.warning_orange)
                CheckStatus.DEPOSITED -> statusChip.setChipBackgroundColorResource(R.color.success_green)
                CheckStatus.BOUNCED -> statusChip.setChipBackgroundColorResource(R.color.error_red)
                CheckStatus.CANCELLED -> statusChip.setChipBackgroundColorResource(R.color.neutral_gray)
            }

            // هشدار فاصله تا سررسید
            val daysRemaining = ((check.dueDate.time - Date().time) / (24 * 60 * 60 * 1000L)).toInt()
            alertView.text = when {
                daysRemaining > 0 -> "🔔 ${daysRemaining} روز تا سررسید"
                daysRemaining == 0 -> "⚠️ امروز سررسید است"
                else -> "⏰ ${-daysRemaining} روز از سررسید گذشته"
            }

            // کلیک روی آیتم
            itemView.setOnClickListener {
                onCheckClick(check)
            }
        }
        
        private fun getStatusName(status: CheckStatus): String {
            return when (status) {
                CheckStatus.PENDING -> "در انتظار وصول"
                CheckStatus.DEPOSITED -> "وصول شده"
                CheckStatus.BOUNCED -> "برگشت خورده"
                CheckStatus.CANCELLED -> "لغو شده"
            }
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<Check>() {
        override fun areItemsTheSame(oldItem: Check, newItem: Check): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Check, newItem: Check): Boolean {
            return oldItem == newItem
        }
    }
}
