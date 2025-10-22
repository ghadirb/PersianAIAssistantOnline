# ⚠️ TODO: جایگزینی API Key نشان

## 📍 فایل‌هایی که باید تغییر بدی:

### 1️⃣ NeshanDirectionAPI.kt
**مسیر:**
```
app/src/main/java/com/persianai/assistant/utils/NeshanDirectionAPI.kt
```

**خط 12:**
```kotlin
private val apiKey = "service.649ba7521ba04da595c5ab56413b3c84"
```

**تغییر به:**
```kotlin
private val apiKey = "کلید_جدید_که_از_پنل_گرفتی"
```

---

### 2️⃣ NeshanSearchAPI.kt
**مسیر:**
```
app/src/main/java/com/persianai/assistant/search/NeshanSearchAPI.kt
```

**خط مربوطه:**
```kotlin
private val apiKey = "service.649ba7521ba04da595c5ab56413b3c84"
```

**تغییر به:**
```kotlin
private val apiKey = "کلید_جدید_که_از_پنل_گرفتی"
```

---

## ✅ مراحل کامل:

1. **برو پنل نشان:**
   - https://platform.neshan.org/

2. **ثبت کلید اپلیکیشن اندروید:**
   - Package Name: `com.persianai.assistant`
   - SHA-1: `7D:D8:DA:4E:AF:A2:BA:38:14:C9:C9:48:2E:9E:DF:E3:2F:55:EC:90`

3. **کپی کلید جدید**

4. **جایگزینی در 2 فایل بالا**

5. **Build برنامه:**
   ```bash
   ./gradlew assembleDebug
   ```

6. **تست:**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb logcat | grep "NeshanDirection"
   ```

7. **چک کن که ببینی:**
   ```
   ✅ Response code: 200
   ✅ Route 0 polyline length: 2456
   ```

---

## 🔍 چک کردن مشکلات:

### اگه Response code: 401
- کلید اشتباهه
- یا SHA-1 اشتباه ثبت شده

### اگه Response code: 403
- سرویس Direction فعال نیست
- یا محدودیت درخواست تموم شده

### اگه polyline length: 0
- API داده نمیده
- یا پاسخ JSON مشکل داره

---

**برای جزئیات بیشتر: `NESHAN_API_SETUP.md` رو بخون!**
