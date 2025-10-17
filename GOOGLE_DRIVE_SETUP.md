# راهنمای راه‌اندازی Google Drive برای اشتراک‌گذاری نقشه

## مراحل راه‌اندازی:

### 1. ایجاد فایل JSON در Google Drive:
1. به Google Drive بروید
2. یک فایل جدید با نام `map_corrections.json` بسازید
3. محتوای اولیه:
```json
[]
```

### 2. عمومی کردن فایل:
1. روی فایل راست‌کلیک کنید
2. "Share" → "Get link"
3. تغییر به "Anyone with the link can view"
4. کپی لینک (مثال):
```
https://drive.google.com/file/d/1ABC123XYZ456/view?usp=sharing
```

### 3. استخراج File ID:
از لینک بالا، `1ABC123XYZ456` همان File ID است.

### 4. قرار دادن در کد:
در فایل `GoogleDriveMapSync.kt` خط 15:
```kotlin
private val DRIVE_FILE_ID = "1ABC123XYZ456" // File ID شما
```

---

## نحوه کار:

### کاربر A (شناسایی مسیر جدید):
```kotlin
val driveSync = GoogleDriveMapSync(context)

// حین رانندگی
locationHistoryManager.recordLocation(location)

// پایان مسیر
val corrections = driveSync.detectNewRoute(allLocations)
// ذخیره محلی ✅

// صادر کردن برای آپلود
val json = driveSync.exportCorrectionsForUpload()
// کپی json و آپلود دستی به Google Drive
```

### کاربر B (دریافت تغییرات):
```kotlin
val driveSync = GoogleDriveMapSync(context)

// دانلود و ادغام
driveSync.syncCorrections()
// تغییرات دانلود شد ✅
```

---

## مزایا:
✅ رایگان (15GB فضای Google Drive)
✅ بدون نیاز به سرور
✅ دسترسی عمومی
✅ سریع و آسان

## محدودیت‌ها:
⚠️ آپلود دستی (نه خودکار)
⚠️ محدودیت 750 درخواست در روز
⚠️ فقط خواندن (نه نوشتن مستقیم)

---

## آپلود دستی:
1. در برنامه: دکمه "صادر کردن اصلاحات"
2. کپی JSON
3. ویرایش `map_corrections.json` در Google Drive
4. Paste و ذخیره
5. کاربران دیگر دانلود می‌کنند

---

## بهبود آینده (اختیاری):
- استفاده از Google Drive API برای آپلود خودکار
- نیاز به OAuth 2.0
- پیچیده‌تر اما کاملاً خودکار
