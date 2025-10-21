package com.persianai.assistant.activities

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.persianai.assistant.R

class ChatAdapter(
    private val messages: List<AIChatActivity.ChatMessage>
) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {
    
    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userCard: MaterialCardView = view.findViewById(R.id.userMessageCard)
        val aiCard: MaterialCardView = view.findViewById(R.id.aiMessageCard)
        val userText: TextView = view.findViewById(R.id.userMessageText)
        val aiText: TextView = view.findViewById(R.id.aiMessageText)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        
        if (message.isUser) {
            holder.userCard.visibility = View.VISIBLE
            holder.aiCard.visibility = View.GONE
            holder.userText.text = message.text
        } else {
            holder.userCard.visibility = View.GONE
            holder.aiCard.visibility = View.VISIBLE
            holder.aiText.text = message.text
        }
    }
    
    override fun getItemCount() = messages.size
}
