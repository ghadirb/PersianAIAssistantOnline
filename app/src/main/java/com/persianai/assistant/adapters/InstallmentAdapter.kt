package com.persianai.assistant.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.persianai.assistant.R
import com.persianai.assistant.models.Installment
import com.persianai.assistant.models.InstallmentStatus
import java.text.SimpleDateFormat
import java.util.*

/**
 * آداپتور برای نمایش لیست اقساط
 */
class InstallmentAdapter(
    private val onInstallmentClick: (Installment) -> Unit
) : ListAdapter<Installment, InstallmentAdapter.ViewHolder>(DiffCallback()) {
    
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale("fa", "IR"))
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_installment, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val statusIcon: ImageView = itemView.findViewById(R.id.installmentStatusIcon)
        private val titleView: TextView = itemView.findViewById(R.id.installmentTitle)
        private val totalAmountView: TextView = itemView.findViewById(R.id.installmentTotalAmount)
        private val paidAmountView: TextView = itemView.findViewById(R.id.installmentPaidAmount)
        private val remainingAmountView: TextView = itemView.findViewById(R.id.installmentRemainingAmount)
        private val progressView: ProgressBar = itemView.findViewById(R.id.installmentProgress)
        private val progressText: TextView = itemView.findViewById(R.id.installmentProgressText)
        private val nextPaymentView: TextView = itemView.findViewById(R.id.installmentNextPayment)
        private val statusView: TextView = itemView.findViewById(R.id.installmentStatus)
        private val lenderView: TextView = itemView.findViewById(R.id.installmentLender)
        
        fun bind(installment: Installment) {
            // تنظیم آیکون وضعیت
            statusIcon.setImageResource(
                when (installment.status) {
                    InstallmentStatus.ACTIVE -> R.drawable.ic_active
                    InstallmentStatus.COMPLETED -> R.drawable.ic_completed
                    InstallmentStatus.DELAYED -> R.drawable.ic_delayed
                    InstallmentStatus.CANCELLED -> R.drawable.ic_cancel
                }
            )
            
            // تنظیم رنگ آیکون
            statusIcon.setColorFilter(
                when (installment.status) {
                    InstallmentStatus.ACTIVE -> itemView.context.getColor(R.color.primary_blue)
                    InstallmentStatus.COMPLETED -> itemView.context.getColor(R.color.success_green)
                    InstallmentStatus.DELAYED -> itemView.context.getColor(R.color.warning_orange)
                    InstallmentStatus.CANCELLED -> itemView.context.getColor(R.color.neutral_gray)
                }
            )
            
            // تنظیم عنوان
            titleView.text = installment.title
            
            // تنظیم مبالغ
            totalAmountView.text = "مبلغ کل: ${String.format("%,.0f", installment.totalAmount)} تومان"
            paidAmountView.text = "پرداخت شده: ${String.format("%,.0f", installment.paidAmount)} تومان"
            remainingAmountView.text = "مانده: ${String.format("%,.0f", installment.remainingAmount)} تومان"
            
            // تنظیم پیشرفت
            val progress = if (installment.installmentCount > 0) {
                (installment.paidInstallments.toFloat() / installment.installmentCount * 100).toInt()
            } else 0
            progressView.progress = progress
            progressText.text = "%${progress} (${installment.paidInstallments}/${installment.installmentCount})"
            
            // تنظیم تاریخ پرداخت بعدی
            nextPaymentView.text = "پرداخت بعدی: ${dateFormat.format(installment.nextPaymentDate)}"
            
            // تنظیم وضعیت
            statusView.text = getStatusName(installment.status)
            statusView.setTextColor(
                when (installment.status) {
                    InstallmentStatus.ACTIVE -> itemView.context.getColor(R.color.primary_blue)
                    InstallmentStatus.COMPLETED -> itemView.context.getColor(R.color.success_green)
                    InstallmentStatus.DELAYED -> itemView.context.getColor(R.color.warning_orange)
                    InstallmentStatus.CANCELLED -> itemView.context.getColor(R.color.neutral_gray)
                }
            )
            
            // تنظیم طلبکار
            if (installment.lender.isNotBlank()) {
                lenderView.text = "طلبکار: ${installment.lender}"
                lenderView.visibility = View.VISIBLE
            } else {
                lenderView.visibility = View.GONE
            }
            
            // تنظیم کلیک
            itemView.setOnClickListener {
                onInstallmentClick(installment)
            }
        }
        
        private fun getStatusName(status: InstallmentStatus): String {
            return when (status) {
                InstallmentStatus.ACTIVE -> "فعال"
                InstallmentStatus.COMPLETED -> "تکمیل شده"
                InstallmentStatus.DELAYED -> "معوق"
                InstallmentStatus.CANCELLED -> "لغو شده"
            }
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<Installment>() {
        override fun areItemsTheSame(oldItem: Installment, newItem: Installment): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Installment, newItem: Installment): Boolean {
            return oldItem == newItem
        }
    }
}
