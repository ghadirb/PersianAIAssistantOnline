# راهنمای ساخت نسخه Release

## 🔐 نسخه فعلی: Debug Build

نسخه فعلی برنامه به صورت **Debug** بیلد می‌شود که:
- ✅ نیاز به keystore ندارد
- ✅ سریع‌تر بیلد می‌شود
- ✅ برای تست و توسعه مناسب است
- ⚠️ برای انتشار در Google Play مناسب نیست

## 🚀 برای ساخت نسخه Release (آینده)

### مرحله 1: ایجاد Keystore

```bash
keytool -genkey -v -keystore persian-ai-assistant.keystore \
  -alias persian-ai-key \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

# اطلاعات مورد نیاز:
# - Password keystore: [یک رمز قوی]
# - Password key: [یک رمز قوی]
# - نام شما
# - نام سازمان
# - شهر، استان، کشور
```

### مرحله 2: آپلود Keystore به CodeMagic

1. وارد CodeMagic Dashboard شوید
2. Settings → Code signing identities
3. Android keystores → Upload keystore
4. فایل `.keystore` را آپلود کنید
5. Reference name: `persian_ai_keystore`

### مرحله 3: تنظیم Environment Variables

در CodeMagic:
```
CM_KEYSTORE_PASSWORD = [رمز keystore]
CM_KEY_ALIAS = persian-ai-key
CM_KEY_PASSWORD = [رمز key]
```

### مرحله 4: به‌روزرسانی build.gradle

در `app/build.gradle`:

```gradle
android {
    signingConfigs {
        release {
            if (System.getenv("CM_KEYSTORE_PATH")) {
                storeFile file(System.getenv("CM_KEYSTORE_PATH"))
                storePassword System.getenv("CM_KEYSTORE_PASSWORD")
                keyAlias System.getenv("CM_KEY_ALIAS")
                keyPassword System.getenv("CM_KEY_PASSWORD")
            }
        }
    }
    
    buildTypes {
        release {
            minifyEnabled true
            signingConfig signingConfigs.release
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

### مرحله 5: به‌روزرسانی codemagic.yaml

```yaml
workflows:
  android-release:
    name: Android Build (Release)
    instance_type: mac_mini_m1
    max_build_duration: 60
    environment:
      android_signing:
        - persian_ai_keystore
      vars:
        PACKAGE_NAME: "com.persianai.assistant"
      java: 17
    scripts:
      - name: Set up local.properties
        script: |
          echo "sdk.dir=$ANDROID_SDK_ROOT" > "$CM_BUILD_DIR/local.properties"
      - name: Build Release APK
        script: |
          ./gradlew assembleRelease
    artifacts:
      - app/build/outputs/**/*.apk
      - app/build/outputs/**/*.aab
```

## 📦 تفاوت Debug و Release

| ویژگی | Debug | Release |
|-------|-------|---------|
| **Signing** | خودکار | نیاز به keystore |
| **سرعت بیلد** | سریع | کندتر |
| **اندازه APK** | بزرگ‌تر | کوچک‌تر (با ProGuard) |
| **قابل Debug** | بله | خیر |
| **Google Play** | خیر | بله |
| **امنیت** | کم | بالا |

## 🎯 برای الان (نسخه اول)

از **Debug Build** استفاده کنید:
```bash
./gradlew assembleDebug
```

APK خروجی در:
```
app/build/outputs/apk/debug/app-debug.apk
```

این APK را می‌توانید:
- ✅ روی گوشی خودتان نصب کنید
- ✅ برای تست به دوستان بدهید
- ✅ از طریق Firebase App Distribution منتشر کنید
- ❌ در Google Play منتشر نکنید

## 🔮 برای نسخه بعدی

وقتی برنامه آماده انتشار عمومی شد:
1. Keystore بسازید
2. در CodeMagic تنظیم کنید
3. Release build بگیرید
4. در Google Play منتشر کنید

## ⚠️ نکات امنیتی

- 🔒 **هرگز keystore را commit نکنید**
- 🔒 رمزهای keystore را در جای امن نگه دارید
- 🔒 از keystore بک‌آپ بگیرید
- 🔒 اگر keystore را گم کنید، نمی‌توانید برنامه را به‌روزرسانی کنید!

## 📞 سوالات

اگر سوالی دارید:
- GitHub Issues
- ghadirb@example.com

---

**نسخه فعلی (Debug) کاملاً کاربردی است و برای تست و استفاده شخصی مناسب است!** ✅
