# تکمیل Liara Integration - 30 دسامبر 2025

## ✅ تکمیل شده

### 1️⃣ **فعال‌کردن خودکار کلید Liara در Home**

**فایل:** `AutoProvisioningManager.kt` → `HomeActivity.kt`

```
Flow:
Home onCreate 
  → AutoProvisioningManager.autoProvision()
  → دانلود کلیدها از gist.github.com
  → رمزگشایی با رمز 12345 (PBKDF2 + AES-GCM)
  → تمام کلیدها فعال می‌شوند
  → syncApiPrefsToShared() ذخیره می‌کند
```

✅ **کلیدها بلافاصله بعد از ورود به Home فعال می‌شوند**

---

### 2️⃣ **مدل‌های Liara برای تحلیل**

**فایل:** `AIModelManager.kt`

| اولویت | مدل | استفاده |
|-------|------|--------|
| 1 | `openai/gpt-5-mini` | تحلیل پیشرفته (اگر در دسترس باشد) |
| 2 | `openai/gpt-5-nano` | تحلیل سریع |
| 3 | `openai/gpt-4o-mini` | تحلیل عمومی |
| 4 | `anthropic/claude-3.7-sonnet` | تحلیل نوشتاری |

**Base URL:** `https://ai.liara.ir/api/69467b6ba99a2016cac892e1/v1`

✅ **تمام مدل‌ها بر اساس دسترسی استفاده می‌شوند**

---

### 3️⃣ **Gemini 2.0 Flash برای ضبط صدا**

**فایل:** `AIModelManager.kt` + `NewHybridVoiceRecorder.kt`

```
Voice Recording Flow:
User presses voice button
  → Liara key فعال است
  → AIClient.transcribeAudio() 
  → Gemini 2.0 Flash model
  → Base URL: https://ai.liara.ir/api/69467b6ba99a2016cac892e1/v1
  → /audio/transcriptions endpoint
```

✅ **Gemini 2.0 Flash اولویت برای ضبط صدا است**

---

### 4️⃣ **Fallback به آفلاین (Haaniye)**

**فایل:** `SpeechToTextPipeline.kt` + `NewHybridVoiceRecorder.kt`

```
اگر Liara/Gemini شکست بخورد:
  → analyzeOffline() → Haaniye model
  → TinyLlama اگر Haaniye موجود نباشد
```

✅ **اگر آنلاین کار نکند، خودکار به آفلاین می‌رود**

---

### 5️⃣ **رمزگشایی خودکار کلیدها**

**فایل:** `EncryptionHelper.kt` + `AutoProvisioningManager.kt`

```
Encryption Algorithm:
- PBKDF2WithHmacSHA256 (20,000 iterations)
- AES/GCM/NoPadding
- Password: 12345
- Salt: 16 bytes
- IV: 12 bytes

Python Script: encrypt_keys.py
$ python encrypt_keys.py keys.txt encrypted.b64 12345
```

✅ **کلیدها به صورت محفوظ رمزگذاری و رمزگشایی می‌شوند**

---

## 📊 تشریح کامل Flow

### **1. صفحه Home:**
```
Home.onCreate()
  ↓
AutoProvisioningManager.autoProvision()
  ↓
دانلود از: https://gist.githubusercontent.com/...
  ↓
رمزگشایی (password: 12345)
  ↓
Parse کلیدها (Liara اول)
  ↓
تمام کلیدها فعال می‌شوند
  ↓
⭐ بلافاصله استفاده آماده است
```

### **2. ارسال پیام (تحلیل):**
```
BaseChatActivity.chooseBestModel()
  ↓
Liara موجود است؟ → YES
  ↓
استفاده از بهترین مدل Liara
  ↓
Priority: gpt-5-mini > gpt-5-nano > gpt-4o-mini > claude
  ↓
AIClient.sendMessage()
  ↓
✅ استفاده از Liara پایه URL + مدل
```

### **3. ضبط صدا:**
```
User presses voice button
  ↓
NewHybridVoiceRecorder.startRecording()
  ↓
ضبط صدا در فایل m4a
  ↓
SpeechToTextPipeline.transcribe()
  ↓
Liara key فعال است؟ → YES
  ↓
AIClient.transcribeAudio()
  ↓
Gemini 2.0 Flash (Priority 1)
  ↓
اگر شکست: Haaniye (Offline)
  ↓
✅ متن نمایش داده می‌شود
```

---

## 🔐 کلیدهای Liara

### **فرمت ذخیره در Gist:**
```txt
liara:YOUR_LIARA_KEY_1:https://ai.liara.ir/api/69467b6ba99a2016cac892e1/v1
liara:YOUR_LIARA_KEY_2:https://ai.liara.ir/api/69467b6ba99a2016cac892e1/v1
openai:YOUR_OPENAI_KEY
```

### **رمزگذاری:**
```bash
python encrypt_keys.py keys.txt encrypted_keys.b64.txt 12345
```

### **موقعیت Gist:**
```
https://gist.githubusercontent.com/ghadirb/626a804df3009e49045a2948dad89fe5/raw/.../keys.txt
```

✅ **کلیدها خودکار دانلود و رمزگشایی می‌شوند**

---

## 📝 لاگ‌های مهم

### **Home Activity:**
```
✅ Provisioning successful: 2 keys
✅ Active keys: 2
```

### **Voice Recording:**
```
✔ Found active Liara key for Gemini 2.0 Flash
📤 Calling transcribeAudio: /path/to/audio.m4a
🎤 Using: Liara (Gemini 2.0 Flash) for voice transcription
✅ Online transcription (Gemini 2.0 Flash): [text]
```

### **اگر شکست بخورد:**
```
⚠️ Online failed: [error]
📱 Attempting offline transcription (Haaniye model)...
✅ Offline transcription (Haaniye): [text]
```

---

## ✨ خلاصه

| بخش | وضعیت | توضیح |
|-----|--------|--------|
| **Liara در Home** | ✅ | خودکار فعال |
| **مدل‌های تحلیل** | ✅ | GPT-5 > GPT-4o > Claude |
| **ضبط صدا** | ✅ | Gemini 2.0 Flash (Liara) |
| **Fallback** | ✅ | Haaniye → TinyLlama |
| **رمزگذاری** | ✅ | AES-GCM + PBKDF2 |
| **لاگ‌ها** | ✅ | تفصیلی و مفید |

---

## 🚀 برای GitHub Build

```bash
git add .
git commit -m "Fix: Complete Liara integration with GPT-5, Gemini 2.0, and offline fallback"
git push origin New
```

✅ **تمام ویژگی‌ها پیاده‌سازی شده است - آماده برای build!**
