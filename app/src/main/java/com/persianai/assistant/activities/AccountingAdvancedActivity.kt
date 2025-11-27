package com.persianai.assistant.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.persianai.assistant.databinding.ActivityAccountingAdvancedBinding

class AccountingAdvancedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountingAdvancedBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountingAdvancedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "💼 حسابداری پیشرفته"

        binding.btnIncomes.setOnClickListener {
            startActivity(Intent(this, IncomeListActivity::class.java))
        }

        binding.btnExpenses.setOnClickListener {
            startActivity(Intent(this, ExpenseListActivity::class.java))
        }

        binding.btnChecks.setOnClickListener {
            startActivity(Intent(this, CheckListActivity::class.java))
        }

        binding.btnInstallments.setOnClickListener {
            startActivity(Intent(this, InstallmentListActivity::class.java))
        }

        binding.chatFab.setOnClickListener {
            startActivity(Intent(this, AccountingChatActivity::class.java))
        }
        
        binding.btnMonthlyBalance.setOnClickListener {
            showMonthlyBalance()
        }
        
        binding.btnYearlyBalance.setOnClickListener {
            showYearlyBalance()
        }
        
        binding.btnAddIncomeManual.setOnClickListener {
            showManualInputDialog("درآمد", "income")
        }
        
        binding.btnAddExpenseManual.setOnClickListener {
            showManualInputDialog("هزینه", "expense")
        }
        
        binding.btnAddCheckManual.setOnClickListener {
            showManualInputDialog("چک", "check")
        }
        
        binding.btnAddInstallmentManual.setOnClickListener {
            showManualInputDialog("قسط", "installment")
        }
    }
    
    private fun showMonthlyBalance() {
        android.widget.Toast.makeText(this, "📅 نمایش تراز ماهانه - به‌زودی", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    private fun showYearlyBalance() {
        android.widget.Toast.makeText(this, "📊 نمایش تراز سالانه - به‌زودی", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    private fun showManualInputDialog(type: String, action: String) {
        android.widget.Toast.makeText(this, "✏️ ورود دستی $type - به‌زودی", android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
