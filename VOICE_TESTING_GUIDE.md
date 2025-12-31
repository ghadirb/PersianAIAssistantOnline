# 🎙️ Voice Recording & Offline Conversation - Testing Guide

## تاریخ: 31 Dec 2025

---

## **مسائل حل شدے:**

### ❌ **پہلے:**
1. "Offline STT not available" error
2. Voice recording button crash
3. Haaniye model not found
4. No offline chat working
5. No fallback to OpenRouter

### ✅ **اب:**
1. ✔ Haaniye model (`fa-haaniye.onnx`) assets میں موجود ہے
2. ✔ Voice recording pipeline complete
3. ✔ SpeechToTextPipeline proper fallback کرتا ہے
4. ✔ TinyLlama offline chat available
5. ✔ OpenRouter.ai free keys fallback active

---

## **1. اگر Voice Recording Button کام نہ کرے:**

### Debugging Steps:
```
1. Logcat میں search کریں: "NewHybridVoice"
2. دیکھیں: 
   ✓ "🎤 Starting new hybrid recording..."
   ✓ "✅ Recording started"
   ✓ "📊 Stopped recording. File size=..."
3. اگر errors دیکھیں:
   - "Missing required permissions" → Grant microphone permission
   - "Failed to start recorder" → Check storage permissions
```

### Haaniye Availability:
```
Logcat search: "HaaniyeManager"
دیکھیں:
✓ "✅ Model available in filesDir"
✓ "✅ Model available from assets"
✗ "⚠️ Model not found" → Copy model.onnx manually
```

---

## **2. اگر Voice Transcription نہ ہو:**

### Online (Liara Gemini 2.0):
```
Logcat: "SpeechToTextPipeline" + "Gemini"
✓ "🌐 Attempting online transcription"
✓ "✅ Online transcription (Gemini 2.0 Flash)"
✗ "⚠️ Online failed" → Check API key
```

### Offline (Haaniye):
```
Logcat: "SpeechToTextPipeline" + "Haaniye"
✓ "📱 Attempting offline transcription"
✓ "✅ Offline transcription (Haaniye)"
✗ "⚠️ Haaniye failed" → See below

If Haaniye fails:
- Model file مسائل → Copy manually to /files/haaniye/
- ONNX runtime مسائل → Check libonnxruntime.so available
- Audio file corrupt → Check file size > 0
```

---

## **3. Manual Haaniye Model Setup (اگر دوبارہ ضرورت ہو):**

```
Command:
adb push C:\Users\Admin\Downloads\Compressed\model.onnx /data/data/com.persianai.assistant/files/haaniye/fa-haaniye.onnx

یا manually via file explorer:
Storage → Android → data → com.persianai.assistant → files → haaniye/
→ Copy fa-haaniye.onnx here
```

---

## **4. اگر Chat Offline نہ ہو:**

### Check TinyLlama Download:
```
Settings → Offline Models → TinyLlama 1.1B
- اگر "Download" button دیکھیں → Download کریں (یا manually copy)
- اگر "Delete" button دیکھیں → Model already downloaded ✓
```

### Check LocalLllama:
```
Logcat: "LocalLlamaRunner"
✓ "Loading model from /path/to/tinyllama.gguf (size=...)"
✓ "infer done. handle=123, path=/path/to/model, resultLen=..."
✗ "Failed to load model" → Check file exists
✗ "UnsatisfiedLinkError" → Check liblocal_llama.so in APK
```

### Offline Chat Flow:
```
BaseChatActivity.sendMessage()
→ handleRequest()
→ tryOffline()
→ offlineRespond()
  → SimpleOfflineResponder (quick responses)
  → LocalLlamaRunner.infer() (TinyLlama)
  → fallback message
```

---

## **5. اگر Free Keys fallback نہ ہو:**

### AutoProvisioning Check:
```
Logcat: "AutoProvisioning"
✓ "📥 دانلود فایل رمزشده از gist..."
✓ "✅ ${processedKeys.size} کلید بارگذاری شد"
✗ "❌ خطا در دانلود از gist"
  → استفاده کریں: "📡 استفاده از free keys fallback"
```

### Free Keys:
```
✓ OpenRouter: sk-or-free (recommended)
✓ OpenAI: sk-proj-free (if active)
✓ AIML: free-aiml-fallback
```

---

## **6. Voice Conversation - مکمل Flow:**

```
┌─────────────────────────────────────┐
│  User taps Microphone Button        │
└─────────────────────┬───────────────┘
                      │
        ┌─────────────▼──────────────┐
        │  NewHybridVoiceRecorder    │
        │  .startRecording()         │
        │  (3 second recording)      │
        └─────────────┬──────────────┘
                      │
        ┌─────────────▼──────────────┐
        │  SpeechToTextPipeline      │
        │  .transcribe()             │
        │  1. Online (Gemini 2.0)    │
        │  2. Offline (Haaniye)      │
        └─────────────┬──────────────┘
                      │
        ┌─────────────▼──────────────┐
        │  Text Transcription        │
        │  "میں آپ کا نام کیا ہے؟" │
        └─────────────┬──────────────┘
                      │
        ┌─────────────▼──────────────┐
        │  BaseChatActivity          │
        │  .handleRequest()           │
        │  1. Try Liara (online)     │
        │  2. Try LocalLlama (offline)│
        └─────────────┬──────────────┘
                      │
        ┌─────────────▼──────────────┐
        │  Response Generated        │
        │  "میں ایک AI ہوں"         │
        └─────────────┬──────────────┘
                      │
        ┌─────────────▼──────────────┐
        │  TTS (Optional)            │
        │  Play voice response       │
        └────────────────────────────┘
```

---

## **7. Logcat Debugging Commands:**

```bash
# Clear logcat
adb logcat -c

# Monitor specific tags:
adb logcat | grep -E "HaaniyeManager|LocalLlamaRunner|SpeechToTextPipeline|AutoProvisioning|NewHybridVoice"

# Save to file:
adb logcat > logcat.txt

# Watch in real-time:
adb logcat -s "HaaniyeManager" "LocalLlamaRunner" "SpeechToTextPipeline"
```

---

## **8. Expected Logcat Output (Successful Run):**

```
D/NewHybridVoice: 🎤 Starting new hybrid recording...
D/NewHybridVoice: ✅ Recording started
...recording...
D/NewHybridVoice: 📊 Stopped recording. File size=...
D/SpeechToTextPipeline: 🎤 Starting transcription
D/SpeechToTextPipeline: 🌐 Attempting online transcription (Priority: Liara Gemini 2.0 Flash)...
W/SpeechToTextPipeline: ⚠️ Online failed (no internet or key issue)
D/SpeechToTextPipeline: 📱 Attempting offline transcription (Haaniye model)...
D/HaaniyeManager: 🎯 analyzeOffline: running Haaniye inference...
D/HaaniyeManager: ✅ analyzeOffline: result length=15 chars
D/SpeechToTextPipeline: ✅ Offline transcription (Haaniye): "میں آپ کا نام ہوں"
D/BaseChatActivity: 📡 tryOnline model=gpt-4o-mini
W/BaseChatActivity: ⚠️ tryOnline failed (no internet)
D/BaseChatActivity: 📱 tryOffline: using LocalLlama
D/LocalLlamaRunner: Loading model from /path/to/tinyllama.gguf
D/LocalLlamaRunner: infer done. resultLen=45
D/BaseChatActivity: ✅ Response: "سلام! میں کیسے مدد کر سکتا ہوں"
```

---

## **9. اگر ابھی بھی مسائل ہوں:**

### Check Files:
```
✓ /assets/tts/haaniye/fa-haaniye.onnx (exists)
✓ /assets/tts/haaniye/fa-haaniye_low.onnx (exists)
✓ libonnxruntime.so (in APK)
✓ liblocal_llama.so (in APK)
```

### Check Permissions:
```
✓ RECORD_AUDIO
✓ WRITE_EXTERNAL_STORAGE
✓ READ_EXTERNAL_STORAGE
```

### Check Settings:
```
✓ Working Mode: OFFLINE یا HYBRID
✓ API Keys: active or fallback enabled
✓ Voice Input: enabled
```

---

**Ready for Testing! 🚀**
