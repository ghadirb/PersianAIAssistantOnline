# معماری برنامه

## نمای کلی

Persian AI Assistant یک برنامه اندروید native است که با Kotlin نوشته شده و از معماری MVVM و Clean Architecture استفاده می‌کند.

## ساختار پروژه

```
PersianAIAssistantOnline/
│
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/persianai/assistant/
│   │       │   ├── activities/           # صفحات برنامه
│   │       │   │   ├── MainActivity.kt   # صفحه اصلی چت
│   │       │   │   ├── SplashActivity.kt # صفحه شروع
│   │       │   │   └── SettingsActivity.kt # تنظیمات
│   │       │   │
│   │       │   ├── adapters/             # RecyclerView Adapters
│   │       │   │   └── ChatAdapter.kt    # نمایش پیام‌ها
│   │       │   │
│   │       │   ├── ai/                   # یکپارچه‌سازی AI
│   │       │   │   └── AIClient.kt       # کلاینت اصلی API
│   │       │   │
│   │       │   ├── database/             # لایه دیتابیس
│   │       │   │   └── ChatDatabase.kt   # Room Database
│   │       │   │
│   │       │   ├── models/               # مدل‌های داده
│   │       │   │   └── AIModel.kt        # Data classes
│   │       │   │
│   │       │   ├── services/             # سرویس‌های پس‌زمینه
│   │       │   │   └── AIAssistantService.kt
│   │       │   │
│   │       │   ├── utils/                # توابع کمکی
│   │       │   │   ├── EncryptionHelper.kt
│   │       │   │   ├── DriveHelper.kt
│   │       │   │   └── PreferencesManager.kt
│   │       │   │
│   │       │   └── AIAssistantApplication.kt
│   │       │
│   │       └── res/
│   │           ├── drawable/             # آیکون‌ها و تصاویر
│   │           ├── layout/               # فایل‌های XML صفحات
│   │           ├── values/               # رنگ‌ها، استرینگ‌ها، تم‌ها
│   │           ├── menu/                 # منوها
│   │           ├── mipmap/               # launcher icons
│   │           └── raw/                  # فایل‌های خام (animations)
│   │
│   ├── build.gradle                      # تنظیمات Gradle ماژول
│   └── proguard-rules.pro                # قوانین ProGuard
│
├── build.gradle                          # تنظیمات Gradle پروژه
├── settings.gradle                       # تنظیمات پروژه
├── gradle.properties                     # پراپرتی‌های Gradle
├── codemagic.yaml                        # تنظیمات CI/CD
├── .gitignore
├── README.md
└── LICENSE
```

## کامپوننت‌های اصلی

### 1. Activities (صفحات)

#### SplashActivity
- **مسئولیت**: صفحه شروع، دریافت رمز عبور، دانلود و رمزگشایی کلیدها
- **جریان کار**:
  1. بررسی وجود کلیدهای API
  2. نمایش دیالوگ توضیحات
  3. دریافت رمز عبور از کاربر
  4. دانلود فایل رمزشده از Google Drive
  5. رمزگشایی با `EncryptionHelper`
  6. ذخیره کلیدها در `PreferencesManager`
  7. انتقال به `MainActivity`

#### MainActivity
- **مسئولیت**: صفحه اصلی چت، ارسال و دریافت پیام‌ها
- **ویژگی‌ها**:
  - نمایش تاریخچه چت با RecyclerView
  - ارسال پیام متنی
  - ضبط صوت با Google Speech Recognition
  - انتخاب مدل AI
  - منوی تنظیمات
- **جریان پیام**:
  1. کاربر پیام می‌فرستد
  2. ذخیره در Room Database
  3. ارسال به `AIClient`
  4. دریافت پاسخ
  5. نمایش و ذخیره پاسخ

#### SettingsActivity
- **مسئولیت**: تنظیمات برنامه
- **قابلیت‌ها**:
  - مدیریت کلیدهای API
  - تنظیم System Prompt
  - فعال/غیرفعال سرویس پس‌زمینه
  - بک‌آپ و بازیابی
  - درباره برنامه

### 2. AI Integration

#### AIClient
- **مسئولیت**: ارتباط با APIهای هوش مصنوعی
- **پشتیبانی از**:
  - OpenAI (GPT-4o, GPT-4o-mini)
  - Anthropic (Claude 3.5 Sonnet, Haiku)
  - OpenRouter
- **متدهای اصلی**:
  - `sendMessage()`: ارسال پیام به مدل
  - `sendToOpenAI()`: ارسال به OpenAI API
  - `sendToClaude()`: ارسال به Claude API

### 3. Database Layer

#### ChatDatabase (Room)
- **مسئولیت**: ذخیره‌سازی محلی پیام‌ها
- **Entities**:
  - `ChatMessageEntity`: پیام‌های چت
- **DAOs**:
  - `ChatDao`: عملیات CRUD روی پیام‌ها
- **قابلیت‌ها**:
  - ذخیره تاریخچه کامل
  - جستجو در پیام‌ها
  - حذف پیام‌های قدیمی

### 4. Utilities

#### EncryptionHelper
- **مسئولیت**: رمزگشایی کلیدهای API
- **الگوریتم**: AES-256-GCM با PBKDF2
- **مراحل**:
  1. Decode Base64
  2. استخراج Salt و IV
  3. Derive کلید با PBKDF2 (200,000 iterations)
  4. رمزگشایی با AES-GCM

#### DriveHelper
- **مسئولیت**: ارتباط با Google Drive
- **قابلیت‌ها**:
  - دانلود فایل رمزشده کلیدها
  - آپلود بک‌آپ (در نسخه‌های بعدی)

#### PreferencesManager
- **مسئولیت**: مدیریت تنظیمات SharedPreferences
- **داده‌های ذخیره شده**:
  - کلیدهای API (رمزشده)
  - مدل انتخابی
  - System Prompt
  - وضعیت سرویس پس‌زمینه

### 5. Background Service

#### AIAssistantService
- **مسئولیت**: اجرای برنامه در پس‌زمینه
- **نوع**: Foreground Service
- **قابلیت‌ها**:
  - نمایش نوتیفیکیشن دائمی
  - پاسخ به دستورات صوتی (در نسخه‌های بعدی)
  - یادآوری‌ها (در نسخه‌های بعدی)

## جریان داده (Data Flow)

```
User Input → MainActivity → AIClient → API (OpenAI/Claude)
                ↓                              ↓
         ChatDatabase                      Response
                ↓                              ↓
           RecyclerView ← ChatAdapter ← MainActivity
```

## امنیت

### رمزگذاری کلیدها
1. کلیدهای API در سرور با Python رمزگذاری می‌شوند
2. فایل رمزشده در Google Drive قرار می‌گیرد
3. برنامه با رمز عبور کاربر آن را رمزگشایی می‌کند
4. کلیدها در SharedPreferences ذخیره می‌شوند (Android Keystore)

### Best Practices
- هیچ کلیدی hardcode نمی‌شود
- استفاده از HTTPS برای تمام درخواست‌ها
- ProGuard برای obfuscation در Release
- مجوزهای حداقلی

## وابستگی‌های اصلی

```gradle
- AndroidX Core, AppCompat, Material Design
- Kotlin Coroutines (async operations)
- Room Database (local storage)
- Retrofit + OkHttp (networking)
- Gson (JSON parsing)
- Google Play Services (Speech, Drive)
- Lottie (animations)
```

## الگوهای طراحی

### 1. Singleton
- `AIAssistantApplication`
- `ChatDatabase`

### 2. Factory
- ViewModel Factories (در نسخه‌های بعدی)

### 3. Observer
- LiveData/Flow برای reactivity

### 4. Repository Pattern
- جدا کردن منبع داده از UI

## مسیر توسعه آینده

### فاز 1 (فعلی) ✅
- ساختار پایه
- یکپارچه‌سازی AI
- چت متنی
- رمزگذاری کلیدها

### فاز 2 (بعدی) 🔄
- پیاده‌سازی کامل Whisper API
- آپلود فایل صوتی
- Text-to-Speech
- بهبود UI/UX

### فاز 3 (آینده) 📅
- یکپارچه‌سازی با اپ‌های دیگر
- Intent Handlers
- Accessibility Services
- Smart Reminders

### فاز 4 (پیشرفته) 🚀
- مدل‌های آفلاین
- RAG با حافظه بلندمدت
- Multi-modal inputs
- Plugin system

## تست

### Unit Tests
```kotlin
// در نسخه‌های بعدی
@Test
fun testEncryption() {
    val encrypted = EncryptionHelper.encrypt("test", "password")
    val decrypted = EncryptionHelper.decrypt(encrypted, "password")
    assertEquals("test", decrypted)
}
```

### UI Tests
```kotlin
// در نسخه‌های بعدی
@Test
fun testSendMessage() {
    onView(withId(R.id.messageInput)).perform(typeText("Hello"))
    onView(withId(R.id.sendButton)).perform(click())
    onView(withId(R.id.recyclerView)).check(matches(hasDescendant(withText("Hello"))))
}
```

## مستندات API

برای جزئیات بیشتر هر کلاس، به KDoc در فایل‌های سورس مراجعه کنید.

## مجوزها

برنامه نیاز به مجوزهای زیر دارد:
- `INTERNET`: ارتباط با API
- `RECORD_AUDIO`: ضبط صوت
- `FOREGROUND_SERVICE`: سرویس پس‌زمینه
- `ACCESS_NETWORK_STATE`: بررسی اتصال

## Performance

### بهینه‌سازی‌ها
- استفاده از Coroutines برای async operations
- پیاده‌سازی pagination در RecyclerView (در صورت نیاز)
- Cache کردن پاسخ‌ها
- LazyLoading برای تصاویر

### Memory Management
- استفاده از ViewModel برای lifecycle-aware data
- پاک کردن پیام‌های قدیمی خودکار
- مدیریت صحیح context leaks

---

**توجه**: این معماری در حال توسعه است و ممکن است تغییرات داشته باشد.
