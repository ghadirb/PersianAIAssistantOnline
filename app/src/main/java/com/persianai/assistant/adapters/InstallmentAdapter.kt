package com.persianai.assistant.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.persianai.assistant.R
import com.persianai.assistant.models.Installment
import com.persianai.assistant.models.InstallmentStatus
import com.persianai.assistant.utils.JalaliCalendar
import java.util.*

/**
 * آداپتور برای نمایش لیست اقساط
 */
class InstallmentAdapter(
    private val onInstallmentClick: (Installment) -> Unit,
    private val onDeleteClick: ((Installment) -> Unit)? = null
) : ListAdapter<Installment, InstallmentAdapter.ViewHolder>(DiffCallback()) {
    
    private fun toJalali(timestamp: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        val jCal = JalaliCalendar(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        return jCal.toString()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_installment, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.titleText)
        private val typeChip: Chip = itemView.findViewById(R.id.typeChip)
        private val totalAmountView: TextView = itemView.findViewById(R.id.totalAmountText)
        private val monthlyAmountView: TextView = itemView.findViewById(R.id.monthlyAmountText)
        private val progressView: ProgressBar = itemView.findViewById(R.id.progressBar)
        private val progressTextView: TextView = itemView.findViewById(R.id.progressText)
        private val progressPercentView: TextView = itemView.findViewById(R.id.progressPercentText)
        private val remainingView: TextView = itemView.findViewById(R.id.remainingText)
        private val deleteButton: ImageButton? = itemView.findViewById(R.id.deleteButton)
        
        fun bind(installment: Installment) {
            // عنوان قسط
            titleView.text = installment.title
            
            // مبلغ کل و مبلغ ماهانه
            totalAmountView.text = String.format("%,.0f", installment.totalAmount) + " تومان"
            monthlyAmountView.text = String.format("%,.0f", installment.monthlyAmount) + " تومان"
            
            // پیشرفت اقساط
            val progress = if (installment.installmentCount > 0) {
                (installment.paidInstallments.toFloat() / installment.installmentCount * 100).toInt()
            } else 0
            progressView.progress = progress
            progressTextView.text = "پرداخت: ${installment.paidInstallments} از ${installment.installmentCount}"
            progressPercentView.text = "$progress%"
            
            // مانده مبلغ
            remainingView.text = "باقیمانده: ${String.format("%,.0f", installment.remainingAmount)} تومان"
            
            // وضعیت روی چیپ نوع
            typeChip.text = getStatusName(installment.status)
            
            // تنظیم کلیک
            itemView.setOnClickListener {
                onInstallmentClick(installment)
            }
            
            // دکمه حذف
            deleteButton?.setOnClickListener {
                onDeleteClick?.invoke(installment)
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
