package com.persianai.assistant.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.persianai.assistant.R
import com.persianai.assistant.databinding.ActivityConversationsBinding
import com.persianai.assistant.models.Conversation
import com.persianai.assistant.storage.ConversationStorage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * صفحه تاریخچه چت‌ها
 */
class ConversationsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityConversationsBinding
    private lateinit var storage: ConversationStorage
    private lateinit var adapter: ConversationsAdapter
    private val conversations = mutableListOf<Conversation>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "تاریخچه چت‌ها"
        
        storage = ConversationStorage(this)
        
        setupRecyclerView()
        loadConversations()
    }
    
    private fun setupRecyclerView() {
        adapter = ConversationsAdapter(
            conversations,
            onItemClick = { conversation ->
                // باز کردن چت
                storage.setCurrentConversationId(conversation.id)
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
                finish()
            },
            onRename = { conversation ->
                showRenameDialog(conversation)
            },
            onDelete = { conversation ->
                showDeleteDialog(conversation)
            }
        )
        
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }
    
    private fun loadConversations() {
        lifecycleScope.launch {
            val loaded = storage.getAllConversations()
            conversations.clear()
            conversations.addAll(loaded)
            adapter.notifyDataSetChanged()
            
            updateEmptyState()
        }
    }
    
    private fun showRenameDialog(conversation: Conversation) {
        val input = TextInputEditText(this).apply {
            setText(conversation.title)
            hint = "عنوان چت"
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("تغییر عنوان")
            .setView(input)
            .setPositiveButton("ذخیره") { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    lifecycleScope.launch {
                        storage.updateConversationTitle(conversation.id, newTitle)
                        loadConversations()
                    }
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun showDeleteDialog(conversation: Conversation) {
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف چت")
            .setMessage("آیا مطمئن هستید که می‌خواهید این چت را حذف کنید؟")
            .setPositiveButton("حذف") { _, _ ->
                lifecycleScope.launch {
                    storage.deleteConversation(conversation.id)
                    loadConversations()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun updateEmptyState() {
        if (conversations.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

/**
 * آداپتور برای نمایش چت‌ها
 */
class ConversationsAdapter(
    private val conversations: List<Conversation>,
    private val onItemClick: (Conversation) -> Unit,
    private val onRename: (Conversation) -> Unit,
    private val onDelete: (Conversation) -> Unit
) : RecyclerView.Adapter<ConversationsAdapter.ViewHolder>() {
    
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale("fa", "IR"))
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.titleText)
        val dateText: TextView = view.findViewById(R.id.dateText)
        val messageCountText: TextView = view.findViewById(R.id.messageCountText)
        val renameButton: ImageButton = view.findViewById(R.id.renameButton)
        val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = conversations[position]
        
        holder.titleText.text = conversation.title
        holder.dateText.text = dateFormat.format(Date(conversation.updatedAt))
        holder.messageCountText.text = "${conversation.messages.size} پیام"
        
        holder.itemView.setOnClickListener {
            onItemClick(conversation)
        }
        
        holder.renameButton.setOnClickListener {
            onRename(conversation)
        }
        
        holder.deleteButton.setOnClickListener {
            onDelete(conversation)
        }
    }
    
    override fun getItemCount() = conversations.size
}
