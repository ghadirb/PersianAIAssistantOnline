package com.persianai.assistant.activities

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
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
