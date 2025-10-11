# راهنمای نصب Google Maps API Key

## مشکل فعلی:
نقشه در مسیریاب نمایش داده نمی‌شود چون Google Maps API Key موجود نیست.

## مراحل دریافت API Key:

### 1. رفتن به Google Cloud Console
https://console.cloud.google.com/

### 2. ایجاد پروژه جدید یا انتخاب پروژه موجود

### 3. فعال کردن Maps SDK
- Maps SDK for Android
- Directions API
- Places API (اختیاری)

### 4. ایجاد API Key
- Credentials → Create Credentials → API Key

### 5. محدود کردن API Key (امنیت)
- Application restrictions → Android apps
- اضافه کردن Package name: `com.persianai.assistant`
- SHA-1 fingerprint از keystoore

### 6. اضافه کردن به AndroidManifest.xml
```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_API_KEY_HERE" />
```

مسیر فایل:
`app/src/main/AndroidManifest.xml`

داخل تگ `<application>` اضافه کنید.

## توجه:
- API رایگان تا $200 اعتبار ماهانه
- برای استفاده بیشتر نیاز به فعال‌سازی billing
