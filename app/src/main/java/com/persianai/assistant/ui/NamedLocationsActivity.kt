package com.persianai.assistant.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.persianai.assistant.navigation.SavedLocationsManager
import com.google.android.gms.maps.model.LatLng

/**
 * Minimal activity to list/add/delete named locations.
 * Uses the existing `SavedLocationsManager` so locations integrate with navigation flows.
 */
class NamedLocationsActivity : AppCompatActivity() {

    private lateinit var repo: SavedLocationsManager
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = SavedLocationsManager(this)

        listView = ListView(this)
        listView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setContentView(listView)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter

        refresh()

        listView.setOnItemClickListener { _, _, position, _ ->
            val item = adapter.getItem(position) ?: return@setOnItemClickListener
            // Offer delete or navigate choice
            AlertDialog.Builder(this)
                .setTitle(item)
                .setItems(arrayOf("ویرایش نام", "حذف", "استفاده برای ناوبری")) { dialog, which ->
                    val all = repo.getAllLocations()
                    val target = all.firstOrNull { it.name == item }
                    when (which) {
                        0 -> {
                            // Edit name
                            target?.let { t ->
                                val input = EditText(this)
                                input.setText(t.name)
                                AlertDialog.Builder(this)
                                    .setTitle("ویرایش نام")
                                    .setView(input)
                                    .setPositiveButton("ذخیره") { _, _ ->
                                        val newName = input.text.toString().trim().ifEmpty { t.name }
                                        repo.updateLocationName(t.id, newName)
                                        refresh()
                                    }
                                    .setNegativeButton("لغو", null)
                                    .show()
                            }
                        }
                        1 -> {
                            target?.let { repo.deleteLocation(it.id) }
                            refresh()
                        }
                        2 -> {
                            target?.let { l ->
                                val uri = "geo:${l.latitude},${l.longitude}?q=${l.latitude},${l.longitude}(${java.net.URLEncoder.encode(l.name, "utf-8")})"
                                val intent = Intent(Intent.ACTION_VIEW).apply { data = android.net.Uri.parse(uri) }
                                startActivity(intent)
                            }
                        }
                    }
                }
                .show()
        }

        // Floating action: add new
        val addBtn = Button(this).apply { text = "افزودن مکان" }
        addBtn.setOnClickListener { showAddDialog() }
        addBtn.setOnLongClickListener {
            // Quick import from share intent
            handleShareIntent()
            true
        }
        addBtn.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addBtn.isAllCaps = false
        addBtn.alpha = 0.95f
        // Add to header
        listView.addHeaderView(addBtn)
    }

    private fun refresh() {
        val items = repo.getAllLocations().map { it.name }
        adapter.clear()
        adapter.addAll(items)
        adapter.notifyDataSetChanged()
    }

    private fun showAddDialog() {
        val nameInput = EditText(this)
        val latInput = EditText(this)
        latInput.hint = "lat"
        val lngInput = EditText(this)
        lngInput.hint = "lng"

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(nameInput)
            addView(latInput)
            addView(lngInput)
        }

        AlertDialog.Builder(this)
            .setTitle("افزودن مکان جدید")
            .setView(layout)
            .setPositiveButton("افزودن") { _, _ ->
                val name = nameInput.text.toString().trim()
                val lat = latInput.text.toString().toDoubleOrNull() ?: 0.0
                val lng = lngInput.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotEmpty()) {
                    repo.saveLocation(name, "", LatLng(lat, lng), "favorite")
                    refresh()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun handleShareIntent() {
        val it = intent
        if (it?.action == Intent.ACTION_SEND) {
            val text = it.getStringExtra(Intent.EXTRA_TEXT) ?: return
            // Try to parse coordinates from text
            val regex = Regex("(-?\\d+\\.\\d+),\\s*(-?\\d+\\.\\d+)")
            val match = regex.find(text)
            match?.let {
                val lat = it.groupValues[1].toDouble()
                val lng = it.groupValues[2].toDouble()
                // Prompt for name
                val input = EditText(this)
                AlertDialog.Builder(this)
                    .setTitle("نام مکان را وارد کنید")
                    .setView(input)
                    .setPositiveButton("ذخیره") { _, _ ->
                        val name = input.text.toString().ifEmpty { "مکان ${System.currentTimeMillis()}" }
                        repo.saveLocation(name, "", LatLng(lat, lng), "favorite")
                        refresh()
                    }
                    .setNegativeButton("لغو", null)
                    .show()
            }
        }
    }
}
