package com.persianai.assistant.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.persianai.assistant.R
import com.persianai.assistant.databinding.ActivityExpensesBinding
import com.persianai.assistant.models.Expense
import com.persianai.assistant.models.ExpenseCategory
import com.persianai.assistant.storage.ExpenseStorage
import com.persianai.assistant.utils.PersianDateConverter

class ExpensesActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityExpensesBinding
    private lateinit var expenseStorage: ExpenseStorage
    private lateinit var incomeStorage: com.persianai.assistant.storage.IncomeStorage
    private lateinit var adapter: ExpenseAdapter
    private val expenses = mutableListOf<Expense>()
    private var showingExpenses = true
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpensesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "💰 حسابداری شخصی"
        
        expenseStorage = ExpenseStorage(this)
        incomeStorage = com.persianai.assistant.storage.IncomeStorage(this)
        setupRecyclerView()
        loadExpenses()
        
        binding.addExpenseFab.setOnClickListener {
            showAddDialog()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = ExpenseAdapter(expenses)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }
    
    private fun loadExpenses() {
        expenses.clear()
        expenses.addAll(expenseStorage.getAllExpenses())
        adapter.notifyDataSetChanged()
        updateSummary()
    }
    
    private fun updateSummary() {
        val persianDate = PersianDateConverter.getCurrentPersianDate()
        val monthKey = "${persianDate.year}/${persianDate.month}"
        val totalExpense = expenseStorage.getMonthlyTotal(monthKey)
        val totalIncome = incomeStorage.getMonthlyTotal(monthKey)
        val balance = totalIncome - totalExpense
        supportActionBar?.subtitle = "درآمد: ${formatMoney(totalIncome)} | هزینه: ${formatMoney(totalExpense)} | مانده: ${formatMoney(balance)}"
    }
    
    private fun formatMoney(amount: Long): String {
        return amount.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")
    }
    
    private fun showAddDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("انتخاب کنید")
            .setItems(arrayOf("💸 ثبت هزینه", "💰 ثبت درآمد")) { _, which ->
                if (which == 0) showAddExpenseDialog() else showAddIncomeDialog()
            }
            .show()
    }
    
    private fun showAddExpenseDialog() {
        val categories = ExpenseCategory.values()
        val items = categories.map { "${it.emoji} ${it.displayName}" }.toTypedArray()
        
        MaterialAlertDialogBuilder(this)
            .setTitle("انتخاب دسته")
            .setItems(items) { _, which ->
                showAmountDialog(categories[which])
            }
            .show()
    }
    
    private fun showAmountDialog(category: ExpenseCategory) {
        val input = android.widget.EditText(this).apply {
            hint = "مبلغ (تومان)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("${category.emoji} ${category.displayName}")
            .setView(input)
            .setPositiveButton("ثبت") { _, _ ->
                val amount = input.text.toString().toLongOrNull() ?: 0
                if (amount > 0) {
                    val persianDate = PersianDateConverter.getCurrentPersianDate()
                    val expense = Expense(
                        amount = amount,
                        category = category,
                        description = "",
                        persianDate = persianDate.toString()
                    )
                    expenseStorage.saveExpense(expense)
                    loadExpenses()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
    
    private fun showAddIncomeDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "مبلغ (تومان)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("💰 ثبت درآمد")
            .setView(input)
            .setPositiveButton("ثبت") { _, _ ->
                val amount = input.text.toString().toLongOrNull() ?: 0
                if (amount > 0) {
                    val persianDate = PersianDateConverter.getCurrentPersianDate()
                    val income = com.persianai.assistant.models.Income(
                        amount = amount,
                        source = "درآمد",
                        persianDate = persianDate.toString()
                    )
                    incomeStorage.saveIncome(income)
                    loadExpenses()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

class ExpenseAdapter(private val expenses: List<Expense>) : 
    RecyclerView.Adapter<ExpenseAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val categoryText: TextView = view.findViewById(android.R.id.text1)
        val amountText: TextView = view.findViewById(android.R.id.text2)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val expense = expenses[position]
        holder.categoryText.text = "${expense.category.emoji} ${expense.category.displayName}"
        holder.amountText.text = "${expense.amount.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")} تومان - ${expense.persianDate}"
    }
    
    override fun getItemCount() = expenses.size
}
