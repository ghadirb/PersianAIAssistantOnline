package com.persianai.assistant.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.persianai.assistant.R
import com.persianai.assistant.databinding.ActivityConnectedAppsBinding
import com.persianai.assistant.models.ConnectedApp

/**
 * صفحه مدیریت برنامه‌های متصل
 */
class ConnectedAppsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityConnectedAppsBinding
    private lateinit var adapter: ConnectedAppsAdapter
    private val apps = mutableListOf<ConnectedApp>()
    private val prefs by lazy { getSharedPreferences("connected_apps", MODE_PRIVATE) }
    private val gson = Gson()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            android.util.Log.d("ConnectedAppsActivity", "onCreate started")
            
            binding = ActivityConnectedAppsBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            android.util.Log.d("ConnectedAppsActivity", "Binding inflated")
            
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "برنامه‌های متصل"
            
            android.util.Log.d("ConnectedAppsActivity", "Toolbar set")
            
            setupRecyclerView()
            android.util.Log.d("ConnectedAppsActivity", "RecyclerView setup")
            
            loadApps()
            android.util.Log.d("ConnectedAppsActivity", "Apps loaded: ${apps.size}")
            
            setupFab()
            android.util.Log.d("ConnectedAppsActivity", "FAB setup complete")
            
        } catch (e: Exception) {
            android.util.Log.e("ConnectedAppsActivity", "Error in onCreate", e)
            android.widget.Toast.makeText(this, "خطا: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    override fun onResume() {
        super.onResume()
        android.util.Log.d("ConnectedAppsActivity", "onResume - apps count: ${apps.size}")
    }
    
    private fun setupRecyclerView() {
        adapter = ConnectedAppsAdapter(
            apps,
            onToggle = { app, isEnabled ->
                app.isEnabled = isEnabled
                saveApps()
            },
            onEdit = { app ->
                showEditDialog(app)
            },
            onDelete = { app ->
                showDeleteDialog(app)
            }
        )
        
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }
    
    private fun setupFab() {
        binding.fabAddApp.setOnClickListener {
            showAddDialog()
        }
    }
    
    private fun loadApps() {
        val savedJson = prefs.getString("apps_list", null)
        
        if (savedJson != null) {
            // بارگذاری از SharedPreferences
            val type = object : TypeToken<List<ConnectedApp>>() {}.type
            val savedApps: List<ConnectedApp> = gson.fromJson(savedJson, type)
            apps.clear()
            apps.addAll(savedApps)
        } else {
            // اولین بار - بارگذاری لیست پیش‌فرض
            apps.clear()
            apps.addAll(ConnectedApp.getDefaultApps())
            saveApps()
        }
        
        adapter.notifyDataSetChanged()
    }
    
    private fun saveApps() {
        val json = gson.toJson(apps)
        prefs.edit().putString("apps_list", json).apply()
    }
    
    private fun showAddDialog() {
        // دریافت لیست برنامه‌های نصب شده
        val pm = packageManager
        val installedApps = pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null } // فقط برنامه‌های قابل اجرا
            .sortedBy { it.loadLabel(pm).toString() }
        
        val appNames = installedApps.map { it.loadLabel(pm).toString() }.toTypedArray()
        
        MaterialAlertDialogBuilder(this)
            .setTitle("انتخاب برنامه")
            .setItems(appNames) { _, which ->
                val selectedApp = installedApps[which]
                val appLabel = selectedApp.loadLabel(pm).toString()
                val packageName = selectedApp.packageName
                
                // دیالوگ برای وارد کردن کلمات کلیدی
                showKeywordsDialog(appLabel, packageName)
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun showKeywordsDialog(appName: String, packageName: String) {
        val input = TextInputEditText(this).apply {
            hint = "کلمات کلیدی (با کاما جدا کنید)"
            setText(appName)
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("کلمات کلیدی برای $appName")
            .setView(input)
            .setPositiveButton("افزودن") { _, _ ->
                val keywords = input.text.toString()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                
                val newApp = ConnectedApp(
                    id = packageName,
                    name = appName,
                    packageName = packageName,
                    keywords = if (keywords.isEmpty()) listOf(appName) else keywords
                )
                apps.add(newApp)
                saveApps()
                adapter.notifyItemInserted(apps.size - 1)
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun showEditDialog(app: ConnectedApp) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_app, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.nameInput)
        val packageInput = dialogView.findViewById<TextInputEditText>(R.id.packageInput)
        val keywordsInput = dialogView.findViewById<TextInputEditText>(R.id.keywordsInput)
        
        nameInput.setText(app.name)
        packageInput.setText(app.packageName)
        keywordsInput.setText(app.keywords.joinToString(", "))
        
        MaterialAlertDialogBuilder(this)
            .setTitle("ویرایش برنامه")
            .setView(dialogView)
            .setPositiveButton("ذخیره") { _, _ ->
                app.name = nameInput.text.toString().trim()
                app.packageName = packageInput.text.toString().trim()
                app.keywords = keywordsInput.text.toString()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                
                saveApps()
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun showDeleteDialog(app: ConnectedApp) {
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف برنامه")
            .setMessage("آیا مطمئن هستید که می‌خواهید ${app.name} را حذف کنید؟")
            .setPositiveButton("حذف") { _, _ ->
                val position = apps.indexOf(app)
                apps.remove(app)
                saveApps()
                adapter.notifyItemRemoved(position)
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

/**
 * آداپتور برای نمایش برنامه‌های متصل
 */
class ConnectedAppsAdapter(
    private val apps: List<ConnectedApp>,
    private val onToggle: (ConnectedApp, Boolean) -> Unit,
    private val onEdit: (ConnectedApp) -> Unit,
    private val onDelete: (ConnectedApp) -> Unit
) : RecyclerView.Adapter<ConnectedAppsAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconView: ImageView = view.findViewById(R.id.appIcon)
        val nameText: TextView = view.findViewById(R.id.appName)
        val packageText: TextView = view.findViewById(R.id.packageName)
        val keywordsText: TextView = view.findViewById(R.id.keywords)
        val enabledSwitch: CheckBox = view.findViewById(R.id.enabledSwitch)
        val editButton: View = view.findViewById(R.id.editButton)
        val deleteButton: View = view.findViewById(R.id.deleteButton)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_connected_app, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        val context = holder.itemView.context
        
        holder.nameText.text = app.name
        holder.packageText.text = app.packageName
        holder.keywordsText.text = "کلمات کلیدی: ${app.keywords.joinToString(", ")}"
        holder.enabledSwitch.isChecked = app.isEnabled
        
        // بارگذاری آیکون برنامه
        try {
            val pm = context.packageManager
            val icon = pm.getApplicationIcon(app.packageName)
            holder.iconView.setImageDrawable(icon)
        } catch (e: PackageManager.NameNotFoundException) {
            holder.iconView.setImageResource(R.drawable.ic_notification)
        }
        
        holder.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            onToggle(app, isChecked)
        }
        
        holder.editButton.setOnClickListener {
            onEdit(app)
        }
        
        holder.deleteButton.setOnClickListener {
            onDelete(app)
        }
    }
    
    override fun getItemCount() = apps.size
}
