# Persian AI Assistant - "New" Branch Fixes

## تبدیلیاں مکمل ہو گئیں ✅

### 1. بڑی ONNX Model فائلوں کو Exclude کیا
```
✅ .gitignore میں شامل:
   - app/src/main/assets/tts/haaniye/fa-haaniye.onnx (109 MB)
   - app/src/main/assets/tts/coqui/model.onnx (100+ MB)

✅ Lightweight models موجود ہیں:
   - fa-haaniye_low.onnx (3 MB) ✓
   - fa-haaniye_low.onnx.json ✓
```

### 2. GitHub Actions Workflows ٹھیک کیے
```
✅ android-build.yml - "New" branch کے لیے
   - NDK r25b setup شامل کیا
   - Build command: ./gradlew :app:assembleDebug -PskipNativeBuild=true

✅ build.yml - "main" اور دیگر branches کے لیے
   - "New" branch شامل کیا
```

### 3. Commit Information
```
Branch: New
Latest Commit: 572b1df
Message: fix: exclude large ONNX models and fix GitHub Actions workflows

Changes:
- .gitignore (updated)
- .github/workflows/android-build.yml (NDK added)
- .github/workflows/build.yml (New branch added)
```

## ٹیسٹ کریں

### GitHub Actions بھیجنے کے لیے:
1. GitHub پر "New" branch کو دیکھیں
2. Actions tab میں "Android CI Build" رن ہوگی
3. APK artifact download ہو سکے گی

### Local Test:
```bash
cd c:\github\PersianAIAssistantOnline
.\gradlew.bat assembleDebug -PskipNativeBuild=true --no-daemon
```

## Configuration Details

### HaaniyeManager (TTS کے لیے)
```kotlin
// یہ code استعمال ہے:
val low = File(dir, "fa-haaniye_low.onnx")
return if (low.exists()) low else File(dir, "fa-haaniye.onnx")
// پہلے 3MB lightweight version استعمال کرے، اگر نہیں تو 109MB version
```

### Expected APK Size
- With fa-haaniye_low.onnx: ~25-30 MB
- Original with fa-haaniye.onnx: ~130+ MB

## اگلے قدم

1. ✅ GitHub Actions trigger ہوگا جب کوئی بھی commit "New" branch پر آئے
2. ✅ بڑی فائلوں کے بغیر APK بنے گی
3. ✅ Lightweight Haaniye model (3MB) شامل ہوگی
4. ✅ Coqui model کو صارف runtime میں download کرسکے گا

## Status: READY FOR TESTING ✅
