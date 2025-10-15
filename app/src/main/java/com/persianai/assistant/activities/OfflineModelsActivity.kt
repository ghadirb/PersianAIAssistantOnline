package com.persianai.assistant.activities

import android.content.Context
import android.content.Intent
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
        
        // نمایش مسیر پوشه
        val modelDir = java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "models")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
        
        binding.pathInfoText?.text = "📁 مسیر پوشه:\n${modelDir.absolutePath}"
        binding.pathInfoText?.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Model Path", modelDir.absolutePath)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "✅ مسیر کپی شد", Toast.LENGTH_SHORT).show()
        }
        
        // دکمه اسکن مجدد
        binding.rescanButton?.setOnClickListener {
            Toast.makeText(this, "🔄 در حال اسکن...", Toast.LENGTH_SHORT).show()
            loadModels()
            Toast.makeText(this, "✅ اسکن کامل شد", Toast.LENGTH_SHORT).show()
        }
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
        
        val modelDir = java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "models")
        val expectedFileName = "${model.name.replace(" ", "_")}.gguf"
        
        AlertDialog.Builder(this)
            .setTitle("📥 ${model.name}")
            .setMessage("""
                💾 حجم: ${model.size} GB
                
                📥 روش دانلود:
                
                1️⃣ روی "🔗 باز کردن لینک" کلیک کنید
                
                2️⃣ صبر کنید تا دانلود در مرورگر شروع شود
                   (توصیه: از دانلود منیجر مثل ADM استفاده کنید)
                
                3️⃣ پس از دانلود کامل، فایل .gguf را پیدا کنید
                
                4️⃣ فایل را به این پوشه منتقل کنید:
                   ${modelDir.absolutePath}
                
                5️⃣ نام فایل را دقیقاً به این صورت تغییر دهید:
                   $expectedFileName
                
                6️⃣ روی دکمه "🔄 اسکن مجدد" کلیک کنید
                
                ⚠️ نکات مهم:
                • حتماً از Wi-Fi استفاده کنید
                • دانلود ${model.size}GB ممکن است 2-6 ساعت طول بکشد
                • نام فایل باید دقیقاً مطابق باشد
                • بعد از کپی، دکمه اسکن مجدد را بزنید
            """.trimIndent())
            .setPositiveButton("🔗 باز کردن لینک") { _, _ ->
                // باز کردن لینک در مرورگر
                try {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(model.url))
                    startActivity(intent)
                    Toast.makeText(this, "🌐 مرورگر باز شد - صبر کنید...", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "❌ خطا در باز کردن لینک", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("📋 کپی لینک") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Model URL", model.url)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "✅ لینک کپی شد - در دانلود منیجر Paste کنید", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("❌ بستن", null)
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
