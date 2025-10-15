package com.persianai.assistant.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.persianai.assistant.R
import com.persianai.assistant.data.Transaction
import com.persianai.assistant.data.TransactionType
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(
    private val transactions: MutableList<Transaction>,
    private val onDeleteClick: (Transaction) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    inner class TransactionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val typeIcon: TextView = view.findViewById(R.id.typeIcon)
        val description: TextView = view.findViewById(R.id.transactionDescription)
        val amount: TextView = view.findViewById(R.id.transactionAmount)
        val date: TextView = view.findViewById(R.id.transactionDate)
        val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]
        
        // آیکون و رنگ بر اساس نوع
        when (transaction.type) {
            TransactionType.INCOME -> {
                holder.typeIcon.text = "💰"
                holder.amount.setTextColor(holder.itemView.context.getColor(android.R.color.holo_green_dark))
                holder.amount.text = "+${formatMoney(transaction.amount)} تومان"
            }
            TransactionType.EXPENSE -> {
                holder.typeIcon.text = "💸"
                holder.amount.setTextColor(holder.itemView.context.getColor(android.R.color.holo_red_dark))
                holder.amount.text = "-${formatMoney(transaction.amount)} تومان"
            }
            TransactionType.CHECK_IN -> {
                holder.typeIcon.text = "📝"
                holder.amount.setTextColor(holder.itemView.context.getColor(android.R.color.holo_green_light))
                holder.amount.text = "+${formatMoney(transaction.amount)} تومان"
            }
            TransactionType.CHECK_OUT -> {
                holder.typeIcon.text = "📄"
                holder.amount.setTextColor(holder.itemView.context.getColor(android.R.color.holo_orange_dark))
                holder.amount.text = "-${formatMoney(transaction.amount)} تومان"
            }
            TransactionType.INSTALLMENT -> {
                holder.typeIcon.text = "📊"
                holder.amount.setTextColor(holder.itemView.context.getColor(android.R.color.holo_blue_dark))
                holder.amount.text = "-${formatMoney(transaction.amount)} تومان"
            }
        }
        
        // توضیحات
        holder.description.text = if (transaction.description.isNotEmpty()) {
            transaction.description
        } else {
            transaction.category
        }
        
        // تاریخ
        val dateFormat = SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale("fa"))
        holder.date.text = dateFormat.format(Date(transaction.date))
        
        // دکمه حذف
        holder.deleteButton.setOnClickListener {
            onDeleteClick(transaction)
        }
    }

    override fun getItemCount() = transactions.size

    fun removeItem(transaction: Transaction) {
        val position = transactions.indexOf(transaction)
        if (position != -1) {
            transactions.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    private fun formatMoney(amount: Double): String {
        return String.format("%,.0f", amount)
    }
}
