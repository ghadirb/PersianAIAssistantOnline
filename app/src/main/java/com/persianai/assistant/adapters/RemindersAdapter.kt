package com.persianai.assistant.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.persianai.assistant.databinding.ItemReminderBinding
import com.persianai.assistant.models.Reminder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RemindersAdapter(
    private var reminders: List<Reminder>,
    private val onReminderAction: (Reminder, String) -> Unit
) : RecyclerView.Adapter<RemindersAdapter.ReminderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val binding = ItemReminderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReminderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        holder.bind(reminders[position])
    }

    override fun getItemCount(): Int = reminders.size

    fun updateData(newReminders: List<Reminder>) {
        this.reminders = newReminders
        notifyDataSetChanged()
    }

    inner class ReminderViewHolder(private val binding: ItemReminderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(reminder: Reminder) {
            binding.titleText.text = reminder.message
            binding.descriptionText.text = reminder.locationName ?: ""
            binding.descriptionText.visibility = if (reminder.locationName.isNullOrEmpty()) View.GONE else View.VISIBLE

            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
            binding.timeText.text = sdf.format(Date(reminder.timestamp))

            binding.recurringIcon.visibility = if (reminder.isRepeating) View.VISIBLE else View.GONE
            binding.categoryChip.text = reminder.tags.firstOrNull() ?: "عمومی"

            if (reminder.isCompleted) {
                binding.statusText.text = "انجام شده"
                binding.titleText.paint.isStrikeThruText = true
            } else {
                binding.statusText.text = if (reminder.timestamp < System.currentTimeMillis()) "معوق" else "فعال"
                binding.titleText.paint.isStrikeThruText = false
            }

            binding.completeButton.setOnClickListener {
                onReminderAction(reminder, "complete")
            }
            binding.root.setOnClickListener {
                onReminderAction(reminder, "view")
            }
        }
    }
}
