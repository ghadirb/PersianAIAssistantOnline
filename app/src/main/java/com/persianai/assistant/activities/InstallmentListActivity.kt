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
                },
                onEditClick = { installment ->
                    // Handle edit click
                    showEditDialog(installment)
                }
            ).apply {
                submitList(installments)
            }
        }
    }
    
    private fun showEditDialog(installment: com.persianai.assistant.models.Installment) {
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        
        val titleInput = android.widget.EditText(this).apply {
            hint = "عنوان"
            setText(installment.title)
        }
        val amountInput = android.widget.EditText(this).apply {
            hint = "مبلغ کل"
            setText(installment.totalAmount.toString())
            inputType = android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        
        container.addView(titleInput)
        container.addView(amountInput)
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("✏️ ویرایش قسط")
            .setView(container)
            .setPositiveButton("ذخیره") { _, _ ->
                val newTitle = titleInput.text.toString()
                val newAmount = amountInput.text.toString().toDoubleOrNull() ?: installment.totalAmount
                
                lifecycleScope.launch {
                    val updated = installment.copy(
                        title = newTitle,
                        totalAmount = newAmount
                    )
                    db.updateInstallment(updated)
                    loadInstallments()
                    android.widget.Toast.makeText(this@InstallmentListActivity, "✅ قسط ویرایش شد", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
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
