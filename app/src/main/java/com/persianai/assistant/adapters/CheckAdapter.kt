package com.persianai.assistant.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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
        private val statusIcon: ImageView = itemView.findViewById(R.id.checkStatusIcon)
        private val checkNumberView: TextView = itemView.findViewById(R.id.checkNumber)
        private val amountView: TextView = itemView.findViewById(R.id.checkAmount)
        private val recipientView: TextView = itemView.findViewById(R.id.checkRecipient)
        private val dueDateView: TextView = itemView.findViewById(R.id.checkDueDate)
        private val statusView: TextView = itemView.findViewById(R.id.checkStatus)
        private val bankView: TextView = itemView.findViewById(R.id.checkBank)
        
        fun bind(check: Check) {
            // تنظیم آیکون وضعیت
            statusIcon.setImageResource(
                when (check.status) {
                    CheckStatus.PENDING -> R.drawable.ic_pending
                    CheckStatus.DEPOSITED -> R.drawable.ic_check_circle
                    CheckStatus.BOUNCED -> R.drawable.ic_error
                    CheckStatus.CANCELLED -> R.drawable.ic_cancel
                }
            )
            
            // تنظیم رنگ آیکون
            statusIcon.setColorFilter(
                when (check.status) {
                    CheckStatus.PENDING -> itemView.context.getColor(R.color.warning_orange)
                    CheckStatus.DEPOSITED -> itemView.context.getColor(R.color.success_green)
                    CheckStatus.BOUNCED -> itemView.context.getColor(R.color.error_red)
                    CheckStatus.CANCELLED -> itemView.context.getColor(R.color.neutral_gray)
                }
            )
            
            // تنظیم شماره چک
            checkNumberView.text = "چک شماره: ${check.checkNumber}"
            
            // تنظیم مبلغ
            amountView.text = "${String.format("%,.0f", check.amount)} تومان"
            
            // تنظیم گیرنده
            recipientView.text = "در وجه: ${check.recipient}"
            
            // تنظیم تاریخ سررسید
            dueDateView.text = "سررسید: ${dateFormat.format(check.dueDate)}"
            
            // تنظیم وضعیت
            statusView.text = getStatusName(check.status)
            statusView.setTextColor(
                when (check.status) {
                    CheckStatus.PENDING -> itemView.context.getColor(R.color.warning_orange)
                    CheckStatus.DEPOSITED -> itemView.context.getColor(R.color.success_green)
                    CheckStatus.BOUNCED -> itemView.context.getColor(R.color.error_red)
                    CheckStatus.CANCELLED -> itemView.context.getColor(R.color.neutral_gray)
                }
            )
            
            // تنظیم بانک
            if (check.bankName.isNotBlank()) {
                bankView.text = "بانک: ${check.bankName}"
                bankView.visibility = View.VISIBLE
            } else {
                bankView.visibility = View.GONE
            }
            
            // تنظیم کلیک
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
