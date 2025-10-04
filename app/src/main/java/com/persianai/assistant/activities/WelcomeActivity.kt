package com.persianai.assistant.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.persianai.assistant.R
import com.persianai.assistant.databinding.ActivityWelcomeBinding
import com.persianai.assistant.utils.PreferencesManager

/**
 * صفحه خوش‌آمدگویی و انتخاب حالت کار
 */
class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private lateinit var prefsManager: PreferencesManager
    private var selectedMode = PreferencesManager.WorkingMode.HYBRID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefsManager = PreferencesManager(this)

        // چک کردن اینکه آیا فقط برای نمایش راهنما اومده
        val isShowingHelp = intent.getBooleanExtra("SHOW_HELP", false)

        // اگر قبلاً انتخاب شده و برای راهنما نیومده، بپر به MainActivity
        if (prefsManager.hasCompletedWelcome() && !isShowingHelp) {
            goToMain()
            return
        }

        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupContinueButton()
    }

    private fun setupViewPager() {
        val layouts = listOf(
            R.layout.page_mode_online,
            R.layout.page_mode_offline,
            R.layout.page_mode_hybrid
        )

        val titles = listOf("آنلاین", "آفلاین", "ترکیبی")

        binding.viewPager.adapter = ModePagerAdapter(layouts)
        binding.viewPager.setCurrentItem(2, false) // شروع از ترکیبی

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = titles[position]
        }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                selectedMode = when (position) {
                    0 -> PreferencesManager.WorkingMode.ONLINE
                    1 -> PreferencesManager.WorkingMode.OFFLINE
                    else -> PreferencesManager.WorkingMode.HYBRID
                }
            }
        })
    }

    private fun setupContinueButton() {
        val isShowingHelp = intent.getBooleanExtra("SHOW_HELP", false)
        
        if (isShowingHelp) {
            // اگر فقط برای راهنما اومده، دکمه "بستن" باشه
            binding.continueButton.text = "بستن"
        }
        
        binding.continueButton.setOnClickListener {
            if (isShowingHelp) {
                // فقط بسته شدن
                finish()
            } else {
                // اولین بار - ذخیره تنظیمات
                prefsManager.setWorkingMode(selectedMode)
                prefsManager.setWelcomeCompleted(true)
                goToMain()
            }
        }
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        // غیرفعال کردن دکمه بازگشت در صفحه Welcome
    }

    // Adapter برای ViewPager2
    private class ModePagerAdapter(private val layouts: List<Int>) :
        RecyclerView.Adapter<ModePagerAdapter.PageViewHolder>() {

        class PageViewHolder(val view: View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            // هیچ بایندینگی نیاز نیست
        }

        override fun getItemCount() = layouts.size

        override fun getItemViewType(position: Int) = layouts[position]
    }
}
