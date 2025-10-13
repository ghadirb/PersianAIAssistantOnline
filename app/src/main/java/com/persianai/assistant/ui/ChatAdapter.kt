package com.persianai.assistant.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.persianai.assistant.R

class ChatAdapter(private val messages: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {
    
    data class ChatMessage(val text: String, val isUser: Boolean)
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.messageText)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = if (viewType == 1) R.layout.item_user_message else R.layout.item_ai_message
        return ViewHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = messages[position].text
    }
    
    override fun getItemCount() = messages.size
    override fun getItemViewType(position: Int) = if (messages[position].isUser) 1 else 0
}
