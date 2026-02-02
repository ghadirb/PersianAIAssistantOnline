## فعال‌سازی STT آفلاین با Whisper در PersianAIAssistantOnline

این پروژه برای استفاده از مدل‌های STT آفلاین (مانند `whisper-tiny-q5_1.gguf`) به دو مؤلفه نیاز دارد:

1) **کتابخانه‌های بومی (JNI + Core)**  
   برای هر ABI (arm64-v8a کافی است)، باید این فایل‌ها در `app/src/main/jniLibs/ABI/` قرار بگیرند:
   - `libwhisper.so`
   - `libwhisper_jni.so`

   اگر نسخه آماده ندارید، از `whisper-android-lib` یا `whisper.cpp` با NDK بسازید:
   - `build_android.sh` (whisper.cpp) → تولید `libwhisper.so`
   - `build_jni.sh` (whisper-android-lib) → تولید `libwhisper_jni.so`

2) **مدل GGUF**  
   فایل مدل (مثلاً `whisper-tiny-q5_1.gguf`) باید در یکی از این مسیرها باشد:
   - هنگام ساخت: `app/src/main/assets/whisper_models/whisper-tiny-q5_1.gguf`
   - یا پس از نصب: `filesDir/whisper_models/whisper-tiny-q5_1.gguf`

### نیازمندی‌های فعلی
در پوشه `C:\Users\Admin\Downloads\Compressed\so` فقط `libwhisper_arm64-v8a.so` و چند `libggml*` وجود دارد و **فایل `libwhisper_jni_arm64-v8a.so` موجود نیست**. بدون آن Whisper فعال نمی‌شود.

### اقدام پیشنهادی سریع
1) تأمین `libwhisper_jni_arm64-v8a.so` (از ریلیز آماده‌ی `whisper-android-lib` یا با اجرای `build_jni.sh` پس از ساخت libwhisper).  
2) کپی فایل‌ها:
   ```
   app/src/main/jniLibs/arm64-v8a/libwhisper.so
   app/src/main/jniLibs/arm64-v8a/libwhisper_jni.so
   ```
3) کپی مدل:
   ```
   app/src/main/assets/whisper_models/whisper-tiny-q5_1.gguf
   ```

### اتصال در کد
- کلاس `WhisperSttEngine` از `filesDir/whisper_models/whisper-tiny-q5_1.gguf` و `filesDir/whisper_native/arm64-v8a/libwhisper*.so` استفاده می‌کند.  
- اگر در `jniLibs` باندل شود، در زمان اجرا به صورت خودکار در دستگاه load می‌شود. برای assets، می‌توان در Splash/Settings کپی به `filesDir` را اضافه کرد تا `WhisperSttEngine.isAvailable()` موفق شود.

### نکته درباره LLM آفلاین
Whisper فقط STT است. برای پاسخ متنی آفلاین باید یک موتور GGUF LLM (مثل `llama.cpp`) و مدل LLM جداگانه اضافه شود. این README صرفاً درباره STT است.
