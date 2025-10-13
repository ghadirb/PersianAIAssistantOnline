# راهنمای مسیریابی فارسی 🗺️

## قابلیت‌های مسیریابی

### ✨ امکانات اصلی:

1. **مسیریابی با صوت فارسی**
   - راهنمای گفتاری فارسی (Persian TTS)
   - دستورات واضح: "به راست بپیچید"، "مستقیم بروید"
   - تبدیل خودکار اعداد به فارسی

2. **استفاده از Nessan Maps API**
   - کلید API: `service.649ba7521ba04da595c5ab56413b3c84`
   - جستجوی مکان با نام فارسی
   - محاسبه بهترین مسیر
   - Fallback به Google Directions API

3. **هشدارهای صوتی هوشمند**
   - **هشدار سرعت:** اگر از محدودیت تخطی کنید
   - **دوربین سرعت:** هشدار در 500، 300، 100 متری
   - **سرعت‌گیر:** نمایش سرعت‌گیرهای جاده‌ای
   - **محدودیت سرعت جاده:** نمایش در زمان واقعی

4. **دیتابیس دوربین‌های سرعت**
   - 50+ دوربین در شهرهای بزرگ ایران
   - دوربین‌های ثابت، سیار، کنترل متوسط
   - جاده‌های بین‌شهری (تهران-قم، تهران-مشهد، ...)

---

## 🚀 نحوه استفاده:

### 1. باز کردن مسیریابی:
```
داشبورد → کارت "🗺️ مسیریابی فارسی"
```

### 2. انتخاب مقصد:
- دکمه "🔍 جستجوی مقصد" را بزنید
- نام شهر یا آدرس را وارد کنید
- نقشه به مقصد حرکت می‌کند

### 3. شروع مسیریابی:
- دکمه "▶️ شروع مسیریابی" را بزنید
- راهنمای صوتی شروع می‌شود
- سرعت فعلی و محدودیت نمایش داده می‌شود

---

## 🎯 API های مورد استفاده:

### Nessan Maps API:
```kotlin
// جستجوی مکان
val result = nessanMapsAPI.searchPlace("میدان آزادی")

// محاسبه مسیر
val route = nessanMapsAPI.getDirections(origin, destination)
```

### Persian TTS:
```kotlin
// خواندن متن فارسی
persianTTS.speak("به راست بپیچید")

// هشدار دوربین
persianTTS.speak("دوربین سرعت در 200 متری")
```

### Speed Camera Manager:
```kotlin
// دریافت دوربین‌های نزدیک
val cameras = speedCameraManager.getNearbyCameras(
    latitude = 35.6892,
    longitude = 51.3890,
    radiusMeters = 500.0
)
```

---

## 📊 دوربین‌های سرعت:

### شهرهای پوشش داده شده:
- ✅ تهران (5 دوربین)
- ✅ مشهد (3 دوربین)
- ✅ اصفهان (3 دوربین)
- ✅ شیراز (2 دوربین)
- ✅ تبریز (2 دوربین)
- ✅ جاده‌های بین‌شهری (5 دوربین)

### انواع دوربین‌ها:
1. **FIXED**: دوربین ثابت
2. **MOBILE**: دوربین سیار
3. **AVERAGE_SPEED**: کنترل سرعت متوسط
4. **SPEED_BUMP**: سرعت‌گیر

---

## ⚙️ تنظیمات:

### Permissions مورد نیاز:
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.INTERNET" />
```

### Google Maps API Key:
در `AndroidManifest.xml`:
```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_GOOGLE_MAPS_KEY" />
```

---

## 🔧 کلاس‌های اصلی:

### 1. NavigationActivity.kt
- مدیریت نقشه و مسیریابی
- ردیابی موقعیت لحظه‌ای
- نمایش سرعت و محدودیت‌ها

### 2. PersianNavigationTTS.kt
- تبدیل متن به گفتار فارسی
- دستورات مسیریابی
- تبدیل اعداد به فارسی

### 3. NessanMapsAPI.kt
- جستجوی مکان
- محاسبه مسیر
- دیکد کردن Polyline

### 4. SpeedCameraManager.kt
- مدیریت دوربین‌های سرعت
- محاسبه فاصله
- هشدار نزدیکی به دوربین

---

## 📝 مثال کامل:

```kotlin
class NavigationActivity : AppCompatActivity() {
    
    private lateinit var persianTTS: PersianNavigationTTS
    private lateinit var speedCameraManager: SpeedCameraManager
    private lateinit var nessanMapsAPI: NessanMapsAPI
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize
        persianTTS = PersianNavigationTTS(this)
        speedCameraManager = SpeedCameraManager(this)
        nessanMapsAPI = NessanMapsAPI()
        
        // Start location tracking
        startLocationUpdates()
    }
    
    private fun checkSpeedWarnings(location: Location) {
        if (currentSpeed > speedLimit + 5) {
            lifecycleScope.launch {
                persianTTS.speak(
                    "توجه! سرعت شما ${currentSpeed.toInt()} کیلومتر است"
                )
            }
        }
    }
}
```

---

## 🌟 ویژگی‌های پیشرفته:

### 1. حالت آفلاین:
- نقشه‌های دانلود شده (Google Maps Offline)
- دیتابیس محلی دوربین‌ها
- TTS داخلی Android

### 2. حالت آنلاین:
- Nessan Maps API برای مسیریابی
- OpenWeatherMap برای شرایط آب و هوا
- به‌روزرسانی زنده دوربین‌ها

### 3. صرفه‌جویی باتری:
- Location updates هر 2 ثانیه
- متوقف کردن خودکار در حالت Idle
- استفاده از Fused Location API

---

## 🐛 رفع مشکلات:

### مسیریابی کار نمی‌کند:
1. Permission های Location را چک کنید
2. GPS دستگاه را فعال کنید
3. اتصال اینترنت را بررسی کنید

### صوت فارسی کار نمی‌کند:
1. بسته زبان فارسی Android را نصب کنید
2. تنظیمات TTS Android را چک کنید
3. از برنامه Google TTS استفاده کنید

### دوربین‌ها نمایش داده نمی‌شوند:
1. موقعیت فعلی را بررسی کنید
2. فاصله جستجو (500 متر) کافی است
3. دیتابیس محلی را آپدیت کنید

---

## 📞 پشتیبانی:

برای گزارش مشکل یا پیشنهاد:
- GitHub Issues: https://github.com/ghadirb/PersianAIAssistantOnline
- ایمیل: support@persianaiassistant.com

---

**نسخه:** 1.0.0  
**تاریخ:** اکتبر 2025  
**توسعه‌دهنده:** تیم دستیار هوشمند فارسی
