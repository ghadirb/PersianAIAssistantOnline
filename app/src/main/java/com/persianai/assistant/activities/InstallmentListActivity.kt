package com.persianai.assistant.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.persianai.assistant.adapters.InstallmentAdapter
import com.persianai.assistant.data.AccountingDB
import com.persianai.assistant.databinding.ActivityInstallmentListBinding
import kotlinx.coroutines.launch

class InstallmentListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInstallmentListBinding
    private lateinit var db: AccountingDB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInstallmentListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "اقساط"

        db = AccountingDB(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        loadInstallments()
    }

    private fun loadInstallments() {
        lifecycleScope.launch {
            val installments = db.getAllInstallments()
            binding.recyclerView.adapter = InstallmentAdapter { installment ->
                // Handle installment click
            }.apply {
                submitList(installments)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadInstallments()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
