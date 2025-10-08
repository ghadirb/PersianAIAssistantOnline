package com.persianai.assistant.activities

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.persianai.assistant.R
import com.persianai.assistant.databinding.ActivityOfflineModelsBinding
import com.persianai.assistant.models.OfflineModelManager

class OfflineModelsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityOfflineModelsBinding
    private lateinit var modelManager: OfflineModelManager
    private lateinit var adapter: ModelsAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOfflineModelsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "مدل‌های آفلاین"
        
        modelManager = OfflineModelManager(this)
        
        setupViews()
        observeDownloadProgress()
        loadModels()
    }
    
    private fun setupViews() {
        adapter = ModelsAdapter()
        binding.recyclerModels.layoutManager = LinearLayoutManager(this)
        binding.recyclerModels.adapter = adapter
    }
    
    private fun loadModels() {
        val downloadedModels = modelManager.getDownloadedModels()
        adapter.setModels(modelManager.availableModels, downloadedModels.map { it.first.name })
    }
    
    private fun observeDownloadProgress() {
        modelManager.downloadProgress.observe(this, Observer { progress ->
            binding.downloadProgress.progress = progress.toInt()
        })
        
        modelManager.downloadStatus.observe(this, Observer { status ->
            binding.downloadStatus.text = status
        })
        
        modelManager.isDownloading.observe(this, Observer { isDownloading ->
            binding.downloadProgress.visibility = if (isDownloading) View.VISIBLE else View.GONE
            binding.downloadStatus.visibility = if (isDownloading) View.VISIBLE else View.GONE
        })
    }
    
    inner class ModelsAdapter : RecyclerView.Adapter<ModelsAdapter.ModelViewHolder>() {
        
        private var models = listOf<OfflineModelManager.ModelInfo>()
        private var downloadedModelNames = listOf<String>()
        
        fun setModels(models: List<OfflineModelManager.ModelInfo>, downloaded: List<String>) {
            this.models = models
            this.downloadedModelNames = downloaded
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_offline_model, parent, false)
            return ModelViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
            holder.bind(models[position])
        }
        
        override fun getItemCount() = models.size
        
        inner class ModelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val cardView: CardView = itemView.findViewById(R.id.cardModel)
            private val modelName: TextView = itemView.findViewById(R.id.modelName)
            private val modelDescription: TextView = itemView.findViewById(R.id.modelDescription)
            private val modelSize: TextView = itemView.findViewById(R.id.modelSize)
            private val modelFeatures: TextView = itemView.findViewById(R.id.modelFeatures)
            private val downloadButton: Button = itemView.findViewById(R.id.downloadButton)
            private val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
            private val statusIcon: ImageView = itemView.findViewById(R.id.statusIcon)
            
            fun bind(model: OfflineModelManager.ModelInfo) {
                modelName.text = model.name
                modelDescription.text = model.description
                modelSize.text = "حجم: ${model.size} GB"
                modelFeatures.text = model.features.joinToString("\n• ", "• ")
                
                val isDownloaded = downloadedModelNames.contains(model.name)
                
                if (isDownloaded) {
                    downloadButton.visibility = View.GONE
                    deleteButton.visibility = View.VISIBLE
                    statusIcon.setImageResource(R.drawable.ic_check_circle)
                    cardView.setCardBackgroundColor(getColor(R.color.success_light))
                } else {
                    downloadButton.visibility = View.VISIBLE
                    deleteButton.visibility = View.GONE
                    statusIcon.setImageResource(R.drawable.ic_cloud_download)
                    cardView.setCardBackgroundColor(getColor(R.color.card_background))
                }
                
                downloadButton.setOnClickListener {
                    showDownloadDialog(model)
                }
                
                deleteButton.setOnClickListener {
                    showDeleteDialog(model)
                }
            }
        }
    }
    
    private fun showDownloadDialog(model: OfflineModelManager.ModelInfo) {
        // بررسی فضای خالی
        if (!modelManager.hasEnoughSpace(model.size)) {
            AlertDialog.Builder(this)
                .setTitle("فضای ناکافی")
                .setMessage("برای دانلود این مدل حداقل ${(model.size * 1.2).toInt()} GB فضای خالی نیاز دارید.")
                .setPositiveButton("باشه", null)
                .show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("راهنمای دانلود ${model.name}")
            .setMessage("""
                حجم دانلود: ${model.size} GB
                
                لینک دانلود:
                ${model.url}
                
                روش‌های دانلود:
                1️⃣ لینک را کپی کنید و در مرورگر باز کنید
                2️⃣ از دانلود منیجر استفاده کنید (ADM یا IDM)
                3️⃣ پس از دانلود، فایل را در این مسیر کپی کنید:
                /Android/data/com.persianai.assistant/files/Download/models/
                
                ⚠️ نکات مهم:
                • از اتصال Wi-Fi استفاده کنید
                • دانلود ممکن است چند ساعت طول بکشد
            """.trimIndent())
            .setPositiveButton("کپی لینک") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Model URL", model.url)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "✅ لینک کپی شد", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("دانلود در برنامه") { _, _ ->
                // شروع دانلود با OkHttp
                modelManager.downloadModel(model) { success ->
                    runOnUiThread {
                        if (success) {
                            Toast.makeText(this, "✅ دانلود کامل شد!", Toast.LENGTH_LONG).show()
                            loadModels()
                        } else {
                            Toast.makeText(this, "❌ خطا در دانلود", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("بستن", null)
            .show()
    }
    
    private fun showDeleteDialog(model: OfflineModelManager.ModelInfo) {
        AlertDialog.Builder(this)
            .setTitle("حذف ${model.name}")
            .setPositiveButton("حذف") { _, _ ->
                if (modelManager.deleteModel(model.name)) {
                    Toast.makeText(this, "✅ مدل حذف شد", Toast.LENGTH_SHORT).show()
                    loadModels()
                } else {
                    Toast.makeText(this, "❌ خطا در حذف مدل", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
