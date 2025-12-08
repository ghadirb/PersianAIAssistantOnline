package com.persianai.assistant.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.persianai.assistant.databinding.ActivityCommandLibraryBinding
import com.persianai.assistant.models.CommandItem
import com.persianai.assistant.ui.CommandLibraryAdapter

/**
 * کتابخانه دستورها - نمایش فرمان‌های پشتیبانی‌شده با مثال و دسته‌بندی
 */
class CommandLibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommandLibraryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommandLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "کتابخانه دستورها"

        setupList()
    }

    private fun setupList() {
        val items = buildCommands()
        val adapter = CommandLibraryAdapter(items)
        binding.commandsRecycler.apply {
            layoutManager = LinearLayoutManager(this@CommandLibraryActivity)
            this.adapter = adapter
        }
    }

    private fun buildCommands(): List<CommandItem> {
        return listOf(
            CommandItem("هواشناسی", "هوا", "هوای فردا مشهد چطوره؟"),
            CommandItem("مسیر و نقشه", "مسیر", "تا شرکت مسیر سریع‌تر چیه؟"),
            CommandItem("یادآوری", "یادآوری", "یادآوری کن فردا ۸ صبح قبض رو پرداخت کنم"),
            CommandItem("تقویم شمسی", "یادآوری", "جمعه‌ها ساعت ۷ صبح بیدارم کن"),
            CommandItem("مالی", "تحلیل مالی", "هزینه‌های این ماه را خلاصه کن و رشد غیرعادی را بگو"),
            CommandItem("یادداشت/تسک", "تسک‌ها", "یک لیست کار روزانه بساز: خرید، تماس با بانک"),
            CommandItem("چت هوشمند", "دستیار", "با من در مورد برنامه هفتگی مطالعه صحبت کن"),
            CommandItem("آب‌وهوا سریع", "هوا", "هوا تهران الان چطوره؟"),
            CommandItem("جستجوی صوتی", "صوت", "ضبط و تبدیل گفتار به متن برای ارسال پیام"),
            CommandItem("تحلیل مسیر", "مسیر", "مسیر بدون ترافیک تا فرودگاه را بده")
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
