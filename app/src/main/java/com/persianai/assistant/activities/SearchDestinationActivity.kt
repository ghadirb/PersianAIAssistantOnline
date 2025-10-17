package com.persianai.assistant.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.persianai.assistant.R
import com.persianai.assistant.databinding.ActivitySearchDestinationBinding
import com.persianai.assistant.utils.NeshanSearchAPI
import kotlinx.coroutines.launch

class SearchDestinationActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySearchDestinationBinding
    private lateinit var searchAPI: NeshanSearchAPI
    private val results = mutableListOf<NeshanSearchAPI.SearchResult>()
    private lateinit var adapter: SearchResultsAdapter
    
    private val cities = listOf("تهران", "مشهد", "اصفهان", "شیراز", "تبریز", "کرج", "اهواز", "قم")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchDestinationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        
        searchAPI = NeshanSearchAPI(this)
        
        setupCitySpinner()
        setupSearch()
        setupRecyclerView()
    }
    
    private fun setupCitySpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, cities)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.citySpinner.adapter = adapter
    }
    
    private fun setupSearch() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                if (query.length >= 2) {
                    performSearch(query)
                } else {
                    results.clear()
                    adapter.notifyDataSetChanged()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }
    
    private fun setupRecyclerView() {
        adapter = SearchResultsAdapter(results) { result ->
            val intent = Intent()
            intent.putExtra("latitude", result.latitude)
            intent.putExtra("longitude", result.longitude)
            intent.putExtra("title", result.title)
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
        
        binding.resultsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.resultsRecyclerView.adapter = adapter
    }
    
    private fun performSearch(query: String) {
        binding.progressBar.visibility = View.VISIBLE
        
        val city = binding.citySpinner.selectedItem.toString()
        
        lifecycleScope.launch {
            try {
                val searchResults = searchAPI.search(query, city)
                results.clear()
                results.addAll(searchResults)
                adapter.notifyDataSetChanged()
                binding.progressBar.visibility = View.GONE
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@SearchDestinationActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class SearchResultsAdapter(
    private val results: List<NeshanSearchAPI.SearchResult>,
    private val onItemClick: (NeshanSearchAPI.SearchResult) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<SearchResultsAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val titleText: android.widget.TextView = view.findViewById(com.persianai.assistant.R.id.titleText)
        val addressText: android.widget.TextView = view.findViewById(com.persianai.assistant.R.id.addressText)
    }
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(com.persianai.assistant.R.layout.item_search_result, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = results[position]
        holder.titleText.text = result.title
        holder.addressText.text = result.address
        holder.itemView.setOnClickListener { onItemClick(result) }
    }
    
    override fun getItemCount() = results.size
}
