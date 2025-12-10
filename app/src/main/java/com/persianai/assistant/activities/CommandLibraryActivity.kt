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
            // هواشناسی
            CommandItem("هواشناسی فعلی", "هوا", "هوای تهران الان چطوره؟"),
            CommandItem("پیش‌بینی فردا", "هوا", "هوای فردا مشهد چطوره؟"),
            CommandItem("پیش‌بینی ۷ روزه", "هوا", "پیش‌بینی هفته آینده اصفهان را بده"),
            // مسیریابی و صوت
            CommandItem("مسیریابی سریع", "مسیر", "تا شرکت مسیر سریع‌تر چیه؟"),
            CommandItem("مسیریابی صوتی", "صوت/مسیر", "الان از راست برم یا مستقیم؟"),
            CommandItem("بازمسیر در اشتباه", "مسیر", "مسیر جدید بده اشتباه پیچیدم"),
            CommandItem("مقصد اشتراکی", "مسیر", "این لینک گوگل‌مپ/نشان رو باز کن و راه بیفت"),
            CommandItem("محل‌های ذخیره‌شده", "مسیر", "به خانه یا محل کار برو"),
            // یادآوری و تقویم هوشمند
            CommandItem("یادآوری ساده", "یادآوری", "یادآوری کن فردا ۸ صبح قبض رو پرداخت کنم"),
            CommandItem("تکرارشونده شمسی", "یادآوری", "جمعه‌ها ساعت ۷ صبح بیدارم کن"),
            CommandItem("یادآوری متنی طبیعی", "یادآوری", "یک ساعت قبل از پرواز یادم بنداز چک‌این کنم"),
            // مالی و تحلیل
            CommandItem("خلاصه هزینه ماه", "تحلیل مالی", "هزینه‌های این ماه را خلاصه کن"),
            CommandItem("هشدار مصرف غیرعادی", "تحلیل مالی", "مصرف غیرعادی موبایل یا خریدها را بگو"),
            CommandItem("نمودار دسته‌بندی", "تحلیل مالی", "چقدر برای غذا و رفت‌وآمد خرج شده؟"),
            // تسک و نوت
            CommandItem("لیست کار روزانه", "تسک/نوت", "یک لیست کار روزانه بساز: خرید، تماس با بانک"),
            CommandItem("یادداشت سریع", "تسک/نوت", "یادداشت کن ایده جلسه سه‌شنبه را مرور کنم"),
            // صوت و تبدیل
            CommandItem("ضبط و تبدیل گفتار", "صوت", "با صدای من پیام را به متن تبدیل کن"),
            CommandItem("پاسخ صوتی", "صوت", "پاسخ را برایم با صدا بخوان"),
            // دستیار هوشمند
            CommandItem("چت آزاد", "دستیار", "با من در مورد برنامه هفتگی مطالعه صحبت کن"),
            CommandItem("فرمان عملیاتی", "دستیار", "تلگرام را باز کن و به علی پیام بده سلام"),
            // فعالیت‌های اخیر
            CommandItem("فعالیت‌های اخیر", "History", "آخرین جستجوی هوا و مسیرهای اخیر را بگو")
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
