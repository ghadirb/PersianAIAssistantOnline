package com.persianai.assistant.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.persianai.assistant.R

data class SearchResultItem(
    val title: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val icon: String = "📍"
)

class SearchResultAdapter(
    private val results: List<SearchResultItem>,
    private val onItemClick: (SearchResultItem) -> Unit,
    private val onShowOnMap: (SearchResultItem) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconText: TextView = view.findViewById(R.id.iconText)
        val titleText: TextView = view.findViewById(R.id.titleText)
        val addressText: TextView = view.findViewById(R.id.addressText)
        val showOnMapButton: ImageButton = view.findViewById(R.id.showOnMapButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.search_result_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = results[position]
        holder.iconText.text = item.icon
        holder.titleText.text = item.title
        holder.addressText.text = item.address
        
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.showOnMapButton.setOnClickListener { onShowOnMap(item) }
    }

    override fun getItemCount() = results.size
}
