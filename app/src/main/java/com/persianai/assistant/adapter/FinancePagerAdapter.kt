package com.persianai.assistant.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.persianai.assistant.fragment.finance.ChecksFragment
import com.persianai.assistant.fragment.finance.InstallmentsFragment

class FinancePagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    private val fragments = listOf(
        ChecksFragment(),
        InstallmentsFragment()
        // TODO: Add IncomeFragment and ExpenseFragment when created
    )

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment = fragments[position]

    fun getPageTitle(position: Int): CharSequence {
        return when (position) {
            0 -> "چک‌ها"
            1 -> "اقساط"
            // TODO: Add titles for Income and Expense
            else -> ""
        }
    }
}
