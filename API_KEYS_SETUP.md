# 🔑 API Keys Setup Guide

## ⚠️ اہم: اپنے API Keys یہاں update کریں!

**فائل:** `app/src/main/java/com/persianai/assistant/config/APIKeyConfig.kt`

---

## 🔴 مسائل سے بچیں

### ❌ OpenRouter (موجودہ keys invalid ہیں)
```
Status: 402 Insufficient Credits
Action: ہٹا دیں یا نئی key شامل کریں
```

### ❌ Gladia
```
Status: 403 Forbidden
Action: ہٹایا جا چکا ہے
```

### ⚠️ AIML
```
Status: SSL issues (fixed)
Action: اگر valid ہے تو استعمال کریں
```

---

## ✅ صحیح Keys

### 1. OpenAI (REQUIRED)
```
کہاں سے: https://platform.openai.com/api-keys
Format: sk-proj-...
استعمال: Text Chat + Voice STT
Status: ✅ MUST HAVE
```

**Update کریں:**
```kotlin
APIKey(
    provider = AIProvider.OPENAI,
    key = "sk-proj-YOUR_ACTUAL_OPENAI_KEY",  // ⚠️ REPLACE THIS
    baseUrl = "https://api.openai.com/v1",
    isActive = true
)
```

### 2. Liara (OPTIONAL)
```
کہاں سے: https://app.liara.ir/
Format: Custom key
استعمال: Voice features improvement
Status: ✅ اختیاری
```

### 3. AIML (OPTIONAL)
```
کہاں سے: https://www.aimlapi.com/
Format: Custom API key
استعمال: Fallback text API
Status: ✅ اختیاری
```

---

## 📝 API Keys کو کہاں add کریں

### Option 1: Code میں (Direct)
**فائل:** `APIKeyConfig.kt`

```kotlin
fun getInitialAPIKeys(): List<APIKey> {
    return listOf(
        APIKey(
            provider = AIProvider.OPENAI,
            key = "sk-proj-YOUR_KEY",  // ⚠️ Replace
            baseUrl = "https://api.openai.com/v1",
            isActive = true
        )
    )
}
```

### Option 2: App میں (Dashboard)
```
1. HomeActivity >> Settings
2. "API Keys" منتخب کریں
3. نئی key add کریں
4. بند کریں
```

### Option 3: Preferences میں (SharedPreferences)
```kotlin
// آپ کے PreferencesManager میں براہ راست call کریں:
val key = APIKey(
    provider = AIProvider.OPENAI,
    key = "sk-proj-...",
    baseUrl = "https://api.openai.com/v1",
    isActive = true
)
preferencesManager.saveAPIKeys(listOf(key))
```

---

## 🧪 Keys کو Test کریں

### مرحلہ 1: Verify کریں
```bash
# OpenAI API test کریں:
curl https://api.openai.com/v1/models \
  -H "Authorization: Bearer sk-proj-YOUR_KEY"
```

### مرحلہ 2: App میں Test کریں
```
1. HomeActivity میں "سلام" بھیجیں
2. 30-60 سیکنڈ انتظار کریں
3. جواب آنا چاہیے
```

### مرحلہ 3: Voice Test کریں
```
1. AIChatActivity میں Voice button دبائیں
2. کچھ بولیں
3. Stop دبائیں
4. ترجمہ + جواب ہونا چاہیے
```

---

## 🔍 Debugging

### اگر Text کام نہیں کر رہی:
```
1. Logcat میں "AIClient" search کریں
2. Error دیکھیں:
   ❌ "402" → API balance ختم
   ❌ "403" → Key invalid
   ❌ "401" → Key غلط
   ❌ "timeout" → Network/server
```

### اگر Voice کام نہیں کر رہی:
```
1. Logcat میں "SpeechToTextPipeline" search کریں
2. "No active API keys" → کوئی key set نہیں
3. "Online STT returned blank" → STT failed
```

---

## 💡 Tips

1. **OpenAI key ہمیشہ رکھیں**
   - یہ سب سے قابل اعتماد ہے
   - Fallback کے لیے دوسری keys add کریں

2. **Credits check کریں**
   - OpenRouter: https://openrouter.ai/settings/credits
   - OpenAI: https://platform.openai.com/account/billing

3. **Git میں commit نہ کریں**
   - اگر APIKeyConfig میں کچھ update کریں تو احتیاط سے
   - Real keys کو git میں commit نہ کریں!

4. **Safe Keys**
   - ہمیشہ environment variables استعمال کریں (production میں)
   - Development میں test keys استعمال کریں

---

## 🎯 Checklist

- [ ] OpenAI key حاصل کی
- [ ] OpenAI key APIKeyConfig میں ڈالی
- [ ] App rebuild کیا
- [ ] Text message test کیا
- [ ] Voice test کیا
- [ ] دونوں کام کر رہے ہیں ✅
- [ ] GitHub پر push کیا

---

## 📞 اگر مسائل ہوں

```
1. OpenAI key verify کریں
2. Logcat میں errors دیکھیں
3. keys کی status check کریں
4. Network connection verify کریں
5. دوبارہ try کریں
```

---

**تمام تیاری مکمل! اب build اور test کریں۔ 🚀**