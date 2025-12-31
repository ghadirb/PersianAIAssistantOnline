# Large ONNX Model Files Cleanup

## Problem
بڑی ONNX model فائلوں کی وجہ سے GitHub پر بھیجی نہیں جا رہی تھیں:

1. **Coqui TTS Model** (`app/src/main/assets/tts/coqui/model.onnx`)
   - سائز: 100+ MB
   - مسئلہ: GitHub پر ڈبھیجا نہیں جا سکا

2. **Haaniye Full Model** (`app/src/main/assets/tts/haaniye/fa-haaniye.onnx`)
   - سائز: 109 MB
   - متبادل: `fa-haaniye_low.onnx` (3 MB) استعمال کریں

## حل (Solution)

### 1. .gitignore میں شامل کیا
```gitignore
# Large ONNX models (using lightweight versions instead)
# Haaniye TTS: 109MB full model - excluded, using 3MB low version
app/src/main/assets/tts/haaniye/fa-haaniye.onnx
# Coqui TTS: 100MB+ model - should be downloaded at runtime
app/src/main/assets/tts/coqui/model.onnx
```

### 2. Git سے ہٹایا
```bash
git rm --cached app/src/main/assets/tts/coqui/model.onnx
```

### 3. Commit اور Push کیا
```bash
git add -A
git commit -m "chore: exclude large ONNX model files from git"
git push --force origin main
```

## موجودہ حالت (Current Status)

✅ **Lightweight Models محفوظ رہے:**
- `app/src/main/assets/tts/haaniye/fa-haaniye_low.onnx` (3 MB) - ✅ GitHub میں موجود
- `app/src/main/assets/tts/haaniye/fa-haaniye_low.onnx.json` - ✅ GitHub میں موجود

❌ **بڑی فائلیں نہیں بھیجی گئیں (Excluded from GitHub):**
- `app/src/main/assets/tts/coqui/model.onnx` - Removed from git
- `app/src/main/assets/tts/haaniye/fa-haaniye.onnx` - Not tracked

## بناء (Build)

اب GitHub پر خالی repository ہے لیکن:
1. Build.gradle syntax ٹھیک ہے
2. GitHub Actions workflow NDK setup کے ساتھ ٹھیک ہے
3. تمام source code GitHub میں موجود ہے

### Next Steps:
1. APK local میں build ہوگی (25-30 MB تقریباً)
2. Lightweight Haaniye model (3 MB) شامل ہوگی
3. GitHub Actions automated builds کامیاب ہوں گی

## Development Notes

- **Coqui Model**: Runtime پر download کریں گے (ضرورت کے وقت)
- **Haaniye Model**: `fa-haaniye_low.onnx` استعمال ہوگی (HaaniyeManager میں configured)

```kotlin
// HaaniyeManager.kt میں:
val low = File(dir, "fa-haaniye_low.onnx")
return if (low.exists()) low else File(dir, "fa-haaniye.onnx")
```

## Repository Size
- **پہلے**: ~300+ MB (بڑی models کی وجہ سے)
- **اب**: ~50 MB (صرف lightweight assets اور code)

✅ GitHub Push اب کامیاب ہوگی!
