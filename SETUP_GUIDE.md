# 📚 راهنمای کامل نصب و اجرای دستیار هوشمند فارسی

## 🚀 راه‌اندازی سریع

### 1️⃣ نصب Android Studio
1. دانلود از: https://developer.android.com/studio
2. نصب با تنظیمات پیش‌فرض
3. دانلود Android SDK 34

### 2️⃣ کلون پروژه
```bash
git clone https://github.com/ghadirb/PersianAIAssistantOnline.git
cd PersianAIAssistantOnline
```

### 3️⃣ باز کردن در Android Studio
1. Open Project → انتخاب پوشه پروژه
2. صبر کنید تا Gradle Sync کامل شود
3. اگر خطای SDK داد: File → Project Structure → SDK Location

## 🔑 تنظیم API Keys

### OpenAI (برای GPT و Whisper)
1. به https://platform.openai.com بروید
2. ثبت نام/ورود کنید
3. API Keys → Create new secret key
4. کلید با `sk-` شروع می‌شود - کپی کنید

### Claude (Anthropic)
1. به https://console.anthropic.com بروید
2. ثبت نام/ورود کنید
3. API Keys → Create Key
4. کلید را کپی کنید

### OpenRouter (رایگان 5$)
1. به https://openrouter.ai بروید
2. ثبت نام کنید
3. Keys → Create Key
4. 5$ اعتبار رایگان دریافت می‌کنید

### AIML API (رایگان)
1. به https://aimlapi.com بروید
2. ثبت نام رایگان کنید
3. در Dashboard کلید را کپی کنید

## 🎙️ رفع مشکل ضبط صدا

### مجوزهای لازم
در اولین اجرا این مجوزها را تایید کنید:
- ✅ Microphone (برای ضبط صدا)
- ✅ Storage (برای ذخیره فایل‌های صوتی)
- ✅ Internet (برای ارسال به Whisper API)

### نحوه استفاده از ضبط صدا
1. دکمه میکروفون را **نگه دارید** (مثل تلگرام)
2. صحبت کنید
3. برای ارسال: انگشت را بردارید
4. برای لغو: به سمت چپ بکشید

### رفع خطای Whisper
اگر پیام خطا می‌دهد:
1. مطمئن شوید کلید OpenAI وارد کرده‌اید
2. فایل صوتی نباید بیش از 25MB باشد
3. فرمت صدا باید MP3 یا M4A باشد

## 📱 اجرای برنامه

### روی گوشی واقعی
1. Developer Options را فعال کنید:
   - Settings → About Phone → Build Number (7 بار بزنید)
2. USB Debugging را روشن کنید:
   - Settings → Developer Options → USB Debugging ✓
3. گوشی را با کابل وصل کنید
4. در Android Studio دکمه ▶️ Run را بزنید

### روی Emulator
1. در Android Studio: Tools → AVD Manager
2. Create Virtual Device
3. انتخاب Pixel 4 → Next
4. انتخاب Android 13 (API 33) → Download → Next
5. Finish
6. دکمه ▶️ برای اجرا

## 🐛 رفع مشکلات رایج

### خطای Gradle
```
Could not resolve dependencies
```
**حل:** 
- File → Invalidate Caches → Invalidate and Restart
- VPN روشن کنید

### خطای Build
```
Execution failed for task ':app:processDebugResources'
```
**حل:**
- Build → Clean Project
- Build → Rebuild Project

### ویجت نمایش داده نمی‌شود
1. برنامه را یکبار اجرا کنید
2. به Home Screen بروید
3. Long Press → Widgets
4. دستیار فارسی را پیدا کنید
5. ویجت را بکشید و رها کنید

### مدل‌ها نمایش داده نمی‌شوند
1. به Settings → API Settings بروید
2. حداقل یک API Key وارد کنید
3. دکمه "تست" را بزنید
4. اگر سبز شد، مدل‌ها ظاهر می‌شوند

## 🎯 ویژگی‌های جدید اضافه شده

### ✅ سیستم API واقعی
- پشتیبانی از OpenAI (GPT-3.5, GPT-4)
- پشتیبانی از Claude (Opus, Sonnet)
- پشتیبانی از OpenRouter (Llama, Mixtral)
- پشتیبانی از AIML API (رایگان)

### ✅ ضبط صدا حرفه‌ای
- ضبط با نگه داشتن (مثل تلگرام)
- نمایش موج صوتی زنده
- لغو با کشیدن به چپ
- پشتیبانی از Whisper API

### ✅ ویجت‌های زیبا (3 سایز)
- کوچک (2×1): ساعت و تاریخ
- متوسط (3×2): با آب‌وهوا
- بزرگ (4×2): با دکمه‌های دسترسی سریع

### ✅ مدیریت مدل‌های آفلاین
- دانلود مدل‌های GGUF واقعی
- نمایش پیشرفت دانلود
- مدیریت فضای ذخیره‌سازی

## 💻 Build APK

### Debug APK
```bash
./gradlew assembleDebug
```
فایل در: `app/build/outputs/apk/debug/app-debug.apk`

### Release APK
1. Generate Signed Bundle/APK
2. انتخاب APK
3. ایجاد یا انتخاب Keystore
4. Build

## 📊 CodeMagic CI/CD

### تنظیم اولیه
1. به https://codemagic.io بروید
2. Add Application → GitHub
3. انتخاب repository
4. تنظیمات Build:
   - Build for: Android
   - Mode: Debug APK
   - Branch: main

### Environment Variables
```
CODEMAGIC_TOKEN=sC89KeWx9DqYyg1gGFHXF0IIxLwJV4PdM-0L1urk4nQ
```

### شروع Build
1. Start New Build
2. انتخاب branch
3. Start Build
4. دانلود APK بعد از اتمام

## 📞 پشتیبانی

در صورت مشکل:
1. Issue در GitHub ایجاد کنید
2. لاگ خطا را ضمیمه کنید
3. مراحل انجام شده را توضیح دهید

## 🎉 نکات نهایی

1. **برای تست اولیه:** از AIML API استفاده کنید (رایگان)
2. **برای کیفیت بالا:** OpenAI یا Claude توصیه می‌شود
3. **برای صدا:** حتماً کلید OpenAI نیاز است
4. **برای ویجت:** Service باید در Background اجرا شود

---
موفق باشید! 🚀
