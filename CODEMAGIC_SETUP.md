# راهنمای تنظیم CodeMagic

## مرحله 1: ثبت‌نام و اتصال

### 1.1. ایجاد حساب CodeMagic
1. به https://codemagic.io بروید
2. با GitHub خود وارد شوید
3. مجوزهای لازم را بدهید

### 1.2. اضافه کردن توکن API
توکن CodeMagic شما:
```
sC89KeWx9DqYyg1gGFHXF0IIxLwJV4PdM-0L1urk4nQ
```

**نحوه استفاده:**
1. Settings → Integrations → API tokens
2. Add new token
3. توکن بالا را وارد کنید

## مرحله 2: اضافه کردن پروژه

### 2.1. انتخاب Repository
1. Applications → Add application
2. GitHub را انتخاب کنید
3. `ghadirb/PersianAIAssistantOnline` را انتخاب کنید

### 2.2. تنظیم Workflow
فایل `codemagic.yaml` از قبل در پروژه موجود است:
```yaml
workflows:
  android-workflow:
    name: Android Build
    instance_type: mac_mini_m1
    ...
```

## مرحله 3: تنظیمات پیشرفته

### 3.1. Environment Variables
در صورت نیاز، متغیرهای محیطی اضافه کنید:

```bash
# در CodeMagic UI → Environment variables
ANDROID_SDK_ROOT=/Users/builder/android-sdk
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
```

### 3.2. Signing Configuration (اختیاری)
برای release build نیاز به keystore دارید:

1. **ایجاد Keystore:**
```bash
keytool -genkey -v -keystore persian-ai-assistant.keystore -alias release -keyalg RSA -keysize 2048 -validity 10000
```

2. **آپلود به CodeMagic:**
- Settings → Code signing identities
- Android keystores
- Upload keystore file

3. **تنظیم در build.gradle:**
```gradle
android {
    signingConfigs {
        release {
            storeFile file(System.getenv("CM_KEYSTORE_PATH"))
            storePassword System.getenv("CM_KEYSTORE_PASSWORD")
            keyAlias System.getenv("CM_KEY_ALIAS")
            keyPassword System.getenv("CM_KEY_PASSWORD")
        }
    }
}
```

## مرحله 4: شروع Build

### 4.1. Build خودکار
هر بار که به branch `main` push می‌کنید، بیلد خودکار شروع می‌شود.

### 4.2. Build دستی
1. به CodeMagic Dashboard بروید
2. پروژه را انتخاب کنید
3. Start new build را بزنید

## مرحله 5: دریافت APK

### 5.1. دانلود از Dashboard
1. پس از اتمام بیلد، به Builds بروید
2. روی build مورد نظر کلیک کنید
3. در قسمت Artifacts، APK را دانلود کنید

### 5.2. دریافت از ایمیل
اگر notifications را فعال کرده باشید، لینک دانلود به ایمیل ارسال می‌شود.

## عیب‌یابی

### خطای "SDK not found"
**راه‌حل:**
در `codemagic.yaml` مطمئن شوید که:
```yaml
scripts:
  - name: Set up local.properties
    script: |
      echo "sdk.dir=$ANDROID_SDK_ROOT" > "$CM_BUILD_DIR/local.properties"
```

### خطای "Java version mismatch"
**راه‌حل:**
در `codemagic.yaml`:
```yaml
environment:
  java: 17
```

### خطای "Gradle build failed"
**راه‌حل:**
1. بررسی logs در CodeMagic
2. اجرای دستور زیر محلی:
```bash
./gradlew clean assembleDebug --stacktrace
```

## بهینه‌سازی Build Time

### 1. استفاده از Cache
```yaml
cache:
  cache_paths:
    - $HOME/.gradle/caches
    - $HOME/.gradle/wrapper
```

### 2. Parallel Execution
در `gradle.properties`:
```properties
org.gradle.parallel=true
org.gradle.caching=true
```

### 3. انتخاب Instance مناسب
برای بیلدهای سریع‌تر:
```yaml
instance_type: mac_mini_m2  # سریع‌تر از m1
```

## انتشار در Google Play (مراحل آینده)

### 1. تنظیم Service Account
1. Google Play Console → Settings
2. API access → Create service account
3. دانلود JSON key

### 2. آپلود به CodeMagic
```yaml
publishing:
  google_play:
    credentials: $GCLOUD_SERVICE_ACCOUNT_CREDENTIALS
    track: internal  # or: alpha, beta, production
```

## Webhooks (پیشرفته)

برای اعلان به سرویس‌های دیگر:
```yaml
publishing:
  scripts:
    - name: Notify Discord
      script: |
        curl -X POST $DISCORD_WEBHOOK_URL \
          -H "Content-Type: application/json" \
          -d '{"content":"✅ Build completed!"}'
```

## مانیتورینگ

### دیدن وضعیت Build
```bash
# استفاده از API CodeMagic
curl -H "x-auth-token: YOUR_TOKEN" \
  https://api.codemagic.io/builds
```

### Badge در README
```markdown
![Build Status](https://api.codemagic.io/apps/APP_ID/status_badge.svg)
```

## هزینه‌ها

### Free Tier
- 500 دقیقه build در ماه
- مناسب برای پروژه‌های شخصی

### Paid Plans
- Unlimited builds
- سرعت بالاتر
- پشتیبانی بهتر

## لینک‌های مفید

- 📖 مستندات: https://docs.codemagic.io/
- 💬 Community: https://github.com/codemagic-ci-cd/codemagic-docs
- 🎓 Tutorials: https://blog.codemagic.io/

## پشتیبانی

در صورت مشکل:
1. بررسی build logs در CodeMagic
2. مراجعه به documentation
3. سوال در GitHub Issues پروژه
4. تماس با support@codemagic.io

---

**نکته امنیتی**: ⚠️
- هرگز توکن‌ها را commit نکنید
- از Environment Variables استفاده کنید
- API keys را رمزگذاری کنید
