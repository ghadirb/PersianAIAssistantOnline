# راهنمای تنظیم API Keys 🔑

## ⚠️ توجه مهم
برای امنیت، کلیدهای API واقعی در کد قرار نگرفته‌اند. شما باید کلیدهای خود را اضافه کنید.

## 📋 مراحل تنظیم کلیدها

### 1. دریافت کلیدهای API
- **OpenAI (Whisper):** از [platform.openai.com](https://platform.openai.com/api-keys) دریافت کنید
- **AIML API:** از [aimlapi.com](https://aimlapi.com) دریافت کنید
- **OpenRouter:** از [openrouter.ai](https://openrouter.ai) دریافت کنید

### 2. اضافه کردن کلیدها در برنامه

#### روش اول: از طریق تنظیمات برنامه (توصیه شده)
1. برنامه را اجرا کنید
2. به بخش **تنظیمات API** بروید
3. کلیدهای خود را وارد کنید
4. دکمه ذخیره را بزنید

#### روش دوم: در کد (برای توسعه‌دهندگان)
در فایل `DefaultApiKeys.kt`:
```kotlin
private val ENCRYPTED_KEYS = mapOf(
    "openai_1" to encryptKey("sk-proj-YOUR_ACTUAL_KEY"),
    "aimlapi_1" to encryptKey("YOUR_ACTUAL_AIML_KEY"),
    "openrouter_1" to encryptKey("sk-or-YOUR_ACTUAL_KEY")
)
```

### 3. کلیدهای ارائه شده توسط کاربر
اگر کلیدهای زیر را دارید، می‌توانید از آنها استفاده کنید:
- **OpenAI:** کلید خود را از پنل OpenAI کپی کنید
- **AIML:** `1dcaf5e5b0b84c7db893e8e2493deb72`
- **OpenRouter:** کلید خود را از پنل OpenRouter کپی کنید

## 🛡️ نکات امنیتی
1. **هرگز** کلیدهای API را در GitHub قرار ندهید
2. از فایل `.env` یا `local.properties` برای ذخیره محلی استفاده کنید
3. کلیدها را در SharedPreferences رمزگذاری شده ذخیره کنید

## 📱 قابلیت‌های فعال شده با API Keys

### با OpenAI Key:
- ✅ تبدیل صوت به متن (Whisper)
- ✅ چت با GPT-3.5/GPT-4
- ✅ تولید کد و Debug

### با AIML Key:
- ✅ مدل‌های متنوع AI
- ✅ پردازش سریع‌تر
- ✅ هزینه کمتر

### با OpenRouter Key:
- ✅ دسترسی به مدل‌های مختلف
- ✅ Claude, Llama, Mistral
- ✅ مقایسه مدل‌ها

## 🚀 نحوه استفاده در برنامه

```kotlin
// مثال استفاده از Whisper API
val openAIKey = DefaultApiKeys.getOpenAIKey() 
    ?: prefs.getString("openai_api_key", null)
    
if (!openAIKey.isNullOrEmpty()) {
    // استفاده از Whisper API
    transcribeAudio(audioFile, openAIKey)
}
```

## ❓ سوالات متداول

**س: آیا می‌توانم بدون API Key از برنامه استفاده کنم؟**
ج: بله، بسیاری از ویژگی‌ها بدون API Key کار می‌کنند، اما برخی قابلیت‌ها مثل Whisper نیاز به کلید دارند.

**س: کلیدهای من کجا ذخیره می‌شوند؟**
ج: در SharedPreferences رمزگذاری شده و فقط در دستگاه شما.

**س: چگونه کلیدها را تغییر دهم؟**
ج: از منوی تنظیمات API در برنامه استفاده کنید.
