package com.persianai.assistant.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.persianai.assistant.databinding.ItemNoteBinding
import com.persianai.assistant.models.NoteItem

class NoteAdapter(
    private val items: MutableList<NoteItem>,
    private val listener: NoteClickListener
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    interface NoteClickListener {
        fun onToggleDone(id: String)
        fun onDelete(id: String)
    }

    inner class NoteViewHolder(val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val item = items[position]
        with(holder.binding) {
            checkDone.setOnCheckedChangeListener(null)
            checkDone.isChecked = item.done
            noteText.text = item.text

            noteText.paintFlags = if (item.done) {
                noteText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                noteText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            checkDone.setOnCheckedChangeListener { _, _ -> listener.onToggleDone(item.id) }
            btnDelete.setOnClickListener { listener.onDelete(item.id) }
        }
    }

    override fun getItemCount(): Int = items.size

    fun update(newItems: List<NoteItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
