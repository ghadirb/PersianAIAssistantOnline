# 🔑 اطلاعات کلیدهای API نشان

## ✅ کلیدهای فعال شما:

### 1️⃣ کلید سرویس (Service Key) - فعال ✅
```
service.d81b1f9424414d4ea848931499e60dac
```

**استفاده:**
- ✅ NeshanDirectionAPI.kt → آپدیت شد
- ✅ NeshanSearchAPI.kt → آپدیت شد

**مزایا:**
- راحت برای تست و توسعه
- نیاز به تنظیمات خاصی نداره
- فوراً کار می‌کنه

**معایب:**
- محدودیت درخواست کمتر
- امنیت کمتر (هر کسی با این کلید می‌تونه API رو صدا بزنه)

---

### 2️⃣ فایل License اپلیکیشن Android - آماده ✅
```
📁 app/src/main/assets/neshan.license
```

**مشخصات:**
- Package Name: `com.persianai.assistant`
- SHA-1: `7D:D8:DA:4E:AF:A2:BA:38:14:C9:C9:48:2E:9E:DF:E3:2F:55:EC:90`

**مزایا:**
- امنیت بالا (فقط با package name و SHA-1 شما کار می‌کنه)
- محدودیت درخواست بیشتر
- مناسب برای نسخه نهایی

**معایب:**
- نیاز به پیاده‌سازی در کد
- فقط برای همین اپلیکیشن کار می‌کنه

---

## 🔧 وضعیت فعلی:

### ✅ چیزهایی که انجام شد:
1. کلید سرویس جدید در کد جایگزین شد
2. فایل `neshan.license` در پوشه `assets` کپی شد
3. هر دو سرویس Search و Direction آپدیت شدن

### 🔄 کار باقیمانده (اختیاری):
برای استفاده از فایل license به جای کلید سرویس، باید:

1. **بارگذاری فایل license در runtime:**
```kotlin
// در NeshanDirectionAPI.kt یا یک Helper class
fun loadLicenseFromAssets(context: Context): String {
    return context.assets.open("neshan.license")
        .bufferedReader()
        .use { it.readText() }
}
```

2. **استفاده در API calls:**
```kotlin
// بجای:
connection.setRequestProperty("Api-Key", apiKey)

// استفاده کن:
connection.setRequestProperty("Api-Key", licenseKey)
// یا
connection.setRequestProperty("Authorization", "Bearer $licenseKey")
```

---

## 💡 توصیه:

### برای الان (توسعه و تست):
✅ **استفاده از کلید سرویس** (همون که الان هست)
- کد کمتر
- راحت‌تر
- کافیه برای تست

### برای نسخه نهایی (Production):
🔐 **استفاده از فایل license**
- امنیت بیشتر
- محدودیت کمتر
- حرفه‌ای‌تر

---

## 🧪 تست:

### تست کلید سرویس جدید:
```bash
# Build
./gradlew assembleDebug

# Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Check logs
adb logcat | grep "NeshanDirection"
```

### باید ببینی:
```
✅ Response code: 200
✅ Route 0 polyline length: 2456
```

---

## 📝 یادداشت مهم:

کلید سرویس جدید `service.d81b1f9424414d4ea848931499e60dac` با **همه سرویس‌ها** ثبت شده:
- ✅ Map
- ✅ Search
- ✅ Direction
- ✅ Reverse Geocoding
- ✅ Routing

پس همه چیز باید کار کنه!

---

## 🔄 مراحل بعدی (اگه خواستی):

1. **تست کن:** بساز و نصب کن
2. **چک کن:** لاگ‌ها رو ببین
3. **اگه کار کرد:** الان می‌تونی از همین استفاده کنی
4. **بعداً:** اگه خواستی license file رو پیاده کن

---

**فعلاً کلید سرویس کافیه! 🚀**
