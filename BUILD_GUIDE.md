# راهنمای بیلد و نصب

## پیش‌نیازها

### نصب JDK 17
```bash
# دانلود و نصب JDK 17 از:
https://adoptium.net/temurin/releases/?version=17
```

### نصب Android SDK
1. دانلود Android Studio از https://developer.android.com/studio
2. باز کردن SDK Manager
3. نصب:
   - Android SDK Platform 34
   - Android SDK Build-Tools 34.0.0
   - Android SDK Command-line Tools

## بیلد محلی (Local Build)

### 1. Clone کردن پروژه
```bash
git clone https://github.com/ghadirb/PersianAIAssistantOnline.git
cd PersianAIAssistantOnline
```

### 2. تنظیم متغیرهای محیطی
```bash
# Windows
set ANDROID_HOME=C:\Users\YourUsername\AppData\Local\Android\Sdk
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.x-hotspot

# Linux/Mac
export ANDROID_HOME=$HOME/Android/Sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
```

### 3. بیلد APK
```bash
# Debug build
gradlew.bat assembleDebug

# Release build (نیاز به keystore دارد)
gradlew.bat assembleRelease
```

### 4. نصب روی دستگاه
```bash
# نصب Debug APK
gradlew.bat installDebug

# یا دستی:
adb install app/build/outputs/apk/debug/app-debug.apk
```

## بیلد با CodeMagic (CI/CD)

### 1. اتصال ریپازیتوری
- وارد https://codemagic.io شوید
- توکن API را اضافه کنید: `sC89KeWx9DqYyg1gGFHXF0IIxLwJV4PdM-0L1urk4nQ`
- ریپازیتوری GitHub را متصل کنید

### 2. تنظیم Workflow
فایل `codemagic.yaml` از قبل پیکربندی شده است و شامل:
- Build خودکار
- تست‌ها
- تولید APK

### 3. شروع Build
- Push به branch `main`
- یا دستی از داشبورد CodeMagic

### 4. دانلود APK
- پس از اتمام بیلد، APK در artifacts موجود است
- لینک دانلود در ایمیل ارسال می‌شود

## بیلد در GitHub Actions (اختیاری)

می‌توانید GitHub Actions را نیز تنظیم کنید:

```yaml
# .github/workflows/android.yml
name: Android CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
    - name: Build with Gradle
      run: ./gradlew assembleDebug
    - name: Upload APK
      uses: actions/upload-artifact@v3
      with:
        name: app-debug
        path: app/build/outputs/apk/debug/app-debug.apk
```

## عیب‌یابی مشکلات رایج

### خطای "SDK location not found"
```bash
# ایجاد فایل local.properties
echo "sdk.dir=C:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk" > local.properties
```

### خطای "Java version"
```bash
# بررسی نسخه Java
java -version  # باید 17 باشد
```

### خطای "Dependency resolution"
```bash
# پاک کردن cache
gradlew.bat clean
gradlew.bat --refresh-dependencies
```

### خطای "Execution failed for task"
```bash
# حذف build folder
rmdir /s /q build
rmdir /s /q app\build
gradlew.bat clean build
```

## راه‌اندازی اولیه برنامه

پس از نصب APK:
1. برنامه را باز کنید
2. در صفحه خوش‌آمدگویی، "ورود رمز" را انتخاب کنید
3. رمز عبور کلیدهای API را وارد کنید
4. برنامه کلیدها را دانلود و رمزگشایی می‌کند
5. شروع به استفاده کنید!

## پشتیبانی

برای گزارش باگ یا سوال:
- GitHub Issues: https://github.com/ghadirb/PersianAIAssistantOnline/issues
- Email: ghadirb@example.com

## نکات مهم

⚠️ **هشدار امنیتی**: 
- هرگز فایل `google-services.json` یا `keystore` را commit نکنید
- کلیدهای API را رمزگذاری کنید
- از `.gitignore` استفاده کنید

🎯 **بهینه‌سازی**:
- برای Release build، ProGuard فعال است
- اندازه APK حدود 15-20 MB است
- حداقل Android 8.0 (API 26) نیاز دارد
