# مراحل بعدی پروژه

## ✅ انجام شده:
1. رفع flickering دما
2. Music Player: SeekBar + Shuffle + Repeat
3. Navigation: مسیرهای جایگزین
4. WorldWeatherAPI.kt ساخته شد با کلید: db4236ef33c64dab8ce194001251110

## 🔄 در حال انجام:

### 1. چت AI در هر قسمت
**فایل:** ContextualChatDialog.kt (نیمه کامل)
- باید layout: dialog_contextual_chat.xml ساخته شود
- ChatAdapter.kt برای RecyclerView

### 2. حسابداری حرفه‌ای
**فایل جدید:** AccountingActivity.kt
**قابلیت‌ها:**
- جدول درآمد/هزینه
- چک و اقساط
- تراز ماهانه/سالانه
- اتصال به AI برای ثبت خودکار

### 3. اتصال AI به همه features
**فایل:** AIContextManager.kt
- Calendar: ثبت رویداد با AI
- Weather: پرسش آب و هوا
- Navigation: مسیریابی صوتی
- Music: انتخاب موزیک
- Accounting: ثبت تراکنش

## 📝 کد نمونه استفاده:
```kotlin
// در هر Activity:
val chat = ContextualChatDialog(this, "تقویم", "امروز: 1403/07/20")
chat.show("یک رویداد برای فردا بساز")
```

## نیاز به:
1. layout XML ها
2. AccountingActivity کامل
3. Database برای حسابداری
4. اتصال AIModelManager
