# مشکلات و راه حل

## 1. خط مسیر صاف
- علت: polyline خالی یا نادرست
- چک: `adb logcat | grep polyline`
- راه حل: باید API نشان درست جواب بده

## 2. صدا کار نمی‌کنه
- علت: TTS فارسی نصب نیست
- راه حل: نصب Google TTS از Play Store

## 3. حالت رانندگی
- TODO: نیاز به WebView جدید با حالت 3D

## تست:
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat | grep -E "RealNav|polyline|TTS"
```
