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
import com.persianai.assistant.models.FinancialTransaction
import com.persianai.assistant.models.TransactionType
import com.persianai.assistant.models.TransactionCategory
import java.text.SimpleDateFormat
import java.util.*

/**
 * آداپتور برای نمایش لیست تراکنش‌های مالی
 */
class FinancialTransactionAdapter(
    private val onTransactionClick: (FinancialTransaction) -> Unit
) : ListAdapter<FinancialTransaction, FinancialTransactionAdapter.ViewHolder>(DiffCallback()) {
    
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale("fa", "IR"))
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_financial_transaction, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.transactionIcon)
        private val titleView: TextView = itemView.findViewById(R.id.transactionTitle)
        private val categoryView: TextView = itemView.findViewById(R.id.transactionCategory)
        private val amountView: TextView = itemView.findViewById(R.id.transactionAmount)
        private val dateView: TextView = itemView.findViewById(R.id.transactionDate)
        private val descriptionView: TextView = itemView.findViewById(R.id.transactionDescription)
        
        fun bind(transaction: FinancialTransaction) {
            // تنظیم آیکون بر اساس نوع تراکنش
            iconView.setImageResource(
                when (transaction.type) {
                    TransactionType.INCOME -> R.drawable.ic_income
                    TransactionType.EXPENSE -> R.drawable.ic_expense
                    TransactionType.TRANSFER -> R.drawable.ic_transfer
                }
            )
            
            // تنظیم رنگ آیکون
            iconView.setColorFilter(
                when (transaction.type) {
                    TransactionType.INCOME -> itemView.context.getColor(R.color.income_green)
                    TransactionType.EXPENSE -> itemView.context.getColor(R.color.expense_red)
                    TransactionType.TRANSFER -> itemView.context.getColor(R.color.neutral_gray)
                }
            )
            
            // تنظیم عنوان
            titleView.text = transaction.description
            
            // تنظیم دسته‌بندی
            categoryView.text = getCategoryName(transaction.category)
            
            // تنظیم مبلغ
            val amountText = when (transaction.type) {
                TransactionType.INCOME -> "+${String.format("%,.0f", transaction.amount)}"
                TransactionType.EXPENSE -> "-${String.format("%,.0f", transaction.amount)}"
                TransactionType.TRANSFER -> String.format("%,.0f", transaction.amount)
            }
            amountView.text = "$amountText تومان"
            
            // تنظیم رنگ مبلغ
            amountView.setTextColor(
                when (transaction.type) {
                    TransactionType.INCOME -> itemView.context.getColor(R.color.income_green)
                    TransactionType.EXPENSE -> itemView.context.getColor(R.color.expense_red)
                    TransactionType.TRANSFER -> itemView.context.getColor(R.color.neutral_gray)
                }
            )
            
            // تنظیم تاریخ
            dateView.text = dateFormat.format(transaction.date)
            
            // تنظیم توضیحات
            descriptionView.text = transaction.description
            if (transaction.description.isBlank()) {
                descriptionView.visibility = View.GONE
            } else {
                descriptionView.visibility = View.VISIBLE
            }
            
            // تنظیم کلیک
            itemView.setOnClickListener {
                onTransactionClick(transaction)
            }
        }
        
        private fun getCategoryName(category: TransactionCategory): String {
            return when (category) {
                // درآمدها
                TransactionCategory.SALARY -> "حقوق"
                TransactionCategory.BUSINESS -> "کسب و کار"
                TransactionCategory.INVESTMENT -> "سرمایه‌گذاری"
                TransactionCategory.RENTAL -> "اجاره"
                TransactionCategory.GIFT -> "هدیه"
                TransactionCategory.OTHER_INCOME -> "سایر درآمدها"
                
                // هزینه‌ها
                TransactionCategory.FOOD -> "غذا و رستوران"
                TransactionCategory.TRANSPORT -> "حمل و نقل"
                TransactionCategory.SHOPPING -> "خرید"
                TransactionCategory.BILLS -> "قبوض"
                TransactionCategory.ENTERTAINMENT -> "سرگرمی"
                TransactionCategory.HEALTH -> "بهداشت و درمان"
                TransactionCategory.EDUCATION -> "آموزش"
                TransactionCategory.HOUSING -> "مسکن"
                TransactionCategory.INSURANCE -> "بیمه"
                TransactionCategory.TAX -> "مالیات"
                TransactionCategory.OTHER_EXPENSE -> "سایر هزینه‌ها"
            }
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<FinancialTransaction>() {
        override fun areItemsTheSame(
            oldItem: FinancialTransaction,
            newItem: FinancialTransaction
        ): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(
            oldItem: FinancialTransaction,
            newItem: FinancialTransaction
        ): Boolean {
            return oldItem == newItem
        }
    }
}
