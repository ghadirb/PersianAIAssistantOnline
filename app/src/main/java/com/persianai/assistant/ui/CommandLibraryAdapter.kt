package com.persianai.assistant.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.persianai.assistant.databinding.ItemCommandBinding
import com.persianai.assistant.models.CommandItem

class CommandLibraryAdapter(
    private val items: List<CommandItem>
) : RecyclerView.Adapter<CommandLibraryAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemCommandBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCommandBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.commandTitle.text = item.title
        holder.binding.commandCategory.text = item.category
        holder.binding.commandExample.text = item.example
    }

    override fun getItemCount(): Int = items.size
}
