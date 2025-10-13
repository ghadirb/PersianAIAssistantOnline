# Persian AI Assistant - نسخه آنلاین

یک دستیار هوش مصنوعی قدرتمند و چندمنظوره برای اندروید

## ✨ ویژگی‌های اصلی

- 🌐 **حالت آنلاین**: استفاده از API های OpenAI, Claude, OpenRouter
- 🔐 **امنیت بالا**: رمزگذاری کلیدهای API با AES-256
- 🎤 **تشخیص صوت**: تبدیل گفتار به متن و تحلیل فایل‌های صوتی
- 💾 **حافظه بلندمدت**: ذخیره تاریخچه گفتگو و یادآوری‌ها
- ☁️ **پشتیبان‌گیری**: بک‌آپ خودکار و دستی در Google Drive
- 🎨 **رابط کاربری مدرن**: طراحی زیبا با Material Design 3
- 🔄 **سرویس پس‌زمینه**: اجرای برنامه حتی در حالت بسته
- 🤖 **مدل‌های قدرتمند**: GPT-4o, GPT-4o-mini, Claude Sonnet

## 🏗️ معماری

```
app/
├── activities/       # صفحات برنامه
├── services/         # سرویس‌های پس‌زمینه
├── models/           # مدل‌های داده
├── utils/            # توابع کمکی
├── encryption/       # رمزگذاری/رمزگشایی
└── ai/               # یکپارچه‌سازی AI
```

## 🔧 نصب و راه‌اندازی

1. Clone کردن پروژه:
```bash
git clone https://github.com/ghadirb/PersianAIAssistantOnline.git
cd PersianAIAssistantOnline
```

2. باز کردن در Android Studio

3. Sync کردن Gradle

4. اجرا روی دستگاه یا شبیه‌ساز

## 📱 نیازمندی‌ها

- Android 8.0 (API 26) یا بالاتر
- اتصال به اینترنت برای حالت آنلاین
- مجوزهای: RECORD_AUDIO, INTERNET, FOREGROUND_SERVICE, ACCESS_NETWORK_STATE

## 🔑 تنظیم کلیدهای API

1. فایل رمزگذاری شده از Google Drive دانلود می‌شود
2. رمز عبور را در صفحه اول وارد کنید
3. برنامه به صورت خودکار کلیدها را رمزگشایی می‌کند

## 📦 بیلد با CodeMagic

فایل `codemagic.yaml` برای بیلد خودکار پیکربندی شده است.

## 📄 لایسنس

MIT License

## 👨‍💻 توسعه‌دهنده

Ghadir - [GitHub](https://github.com/ghadirb)
