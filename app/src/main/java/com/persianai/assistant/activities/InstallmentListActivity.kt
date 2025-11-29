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
            binding.recyclerView.adapter = InstallmentAdapter(
                onInstallmentClick = { installment ->
                    // Handle installment click
                },
                onDeleteClick = { installment ->
                    // Handle delete click
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this@InstallmentListActivity)
                        .setTitle("❌ حذف قسط")
                        .setMessage("آیا از حذف این قسط مطمئن هستید؟")
                        .setPositiveButton("حذف") { _, _ ->
                            lifecycleScope.launch {
                                db.deleteInstallment(installment.id)
                                loadInstallments()
                                android.widget.Toast.makeText(this@InstallmentListActivity, "✅ قسط حذف شد", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("لغو", null)
                        .show()
                }
            ).apply {
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
