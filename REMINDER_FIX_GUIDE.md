# راهنمای رفع مشکل هشدارهای تمام‌صفحه یادآوری

## 🔧 تغییرات اعمال شده

### 1. **FullScreenAlarmActivity.kt** 
تغییرات کلیدی:
- ✅ افزودن لاگ‌های کامل برای ردیابی مشکلات
- ✅ استفاده صحیح از `setShowWhenLocked()` و `setTurnScreenOn()` برای اندروید 8+
- ✅ پشتیبانی کامل از نمایش بر روی lock screen
- ✅ مدیریت صحیح MediaPlayer و Vibrator با try-catch
- ✅ جلوگیری از بسته شدن با دکمه Back
- ✅ استفاده از FULL_WAKE_LOCK برای روشن کردن صفحه نمایش
- ✅ تنظیم حجم صدا به حداکثر برای ALARM stream

### 2. **ReminderReceiver.kt**
تغییرات کلیدی:
- ✅ استفاده از `FULL_WAKE_LOCK` با `ACQUIRE_CAUSES_WAKEUP` برای بیدار کردن گوشی
- ✅ افزودن لاگ‌های دقیق برای ردیابی مسیر اجرا
- ✅ پشتیبانی از fullScreenIntent برای اندروید 10+
- ✅ مدیریت صحیح خطاها با fallback به notification
- ✅ استفاده از `Intent.FLAG_ACTIVITY_CLEAR_TASK` برای اطمینان از نمایش Activity

### 3. **AndroidManifest.xml**
تغییرات کلیدی:
- ✅ تغییر `exported` به `true` برای FullScreenAlarmActivity
- ✅ افزودن `launchMode="singleInstance"` برای جلوگیری از multiple instance
- ✅ افزودن `excludeFromRecents="true"` 
- ✅ افزودن `configChanges` برای جلوگیری از restart در تغییرات configuration

## 📋 Permissions مورد نیاز

تمام permission‌های زیر در AndroidManifest موجود هستند:
```xml
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

## 🎯 نحوه کار سیستم

### حالت 1: برنامه باز است
1. ReminderReceiver دریافت می‌شود
2. FullScreenAlarmActivity مستقیماً شروع می‌شود
3. صفحه نمایش روشن می‌شود
4. صدا و لرزش شروع می‌شود

### حالت 2: برنامه بسته است (Android 10+)
1. ReminderReceiver دریافت می‌شود
2. یک Notification با fullScreenIntent ایجاد می‌شود
3. سیستم خودکار FullScreenAlarmActivity را نمایش می‌دهد
4. صفحه نمایش روشن می‌شود
5. صدا و لرزش شروع می‌شود

### حالت 3: برنامه بسته است (Android 9 و پایین‌تر)
1. ReminderReceiver دریافت می‌شود
2. Activity مستقیماً با FLAG_ACTIVITY_NEW_TASK شروع می‌شود
3. صفحه نمایش روشن می‌شود
4. صدا و لرزش شروع می‌شود

## 🔍 نحوه تست

### تست 1: حالت Foreground
```kotlin
// در هر Activity
val intent = Intent(this, FullScreenAlarmActivity::class.java).apply {
    putExtra("title", "تست یادآوری")
    putExtra("description", "این یک تست است")
}
startActivity(intent)
```

### تست 2: حالت Background
```kotlin
// ایجاد یک یادآوری 1 دقیقه‌ای
val reminderManager = SmartReminderManager(context)
val reminder = SmartReminderManager.SmartReminder(
    id = UUID.randomUUID().toString(),
    title = "تست Background",
    description = "تست نمایش در background",
    triggerTime = System.currentTimeMillis() + 60000,
    alertType = SmartReminderManager.AlertType.FULL_SCREEN
)
reminderManager.addReminder(reminder)

// برنامه را ببندید و صبر کنید...
```

### تست 3: حالت Lock Screen
1. یک یادآوری با alertType = FULL_SCREEN ایجاد کنید
2. گوشی را قفل کنید
3. صبر کنید تا زمان یادآوری برسد
4. صفحه باید روشن شود و Activity نمایش داده شود

## 🐛 رفع مشکلات

### مشکل: Activity نمایش داده نمی‌شود
**راه حل:**
1. بررسی کنید که permission `USE_FULL_SCREEN_INTENT` در Manifest است
2. بررسی کنید که در تنظیمات گوشی، اجازه نمایش بر روی برنامه‌های دیگر داده شده
3. لاگ‌ها را بررسی کنید: `adb logcat | grep "ReminderReceiver\|FullScreenAlarm"`

### مشکل: صدا پخش نمی‌شود
**راه حل:**
1. بررسی کنید که volume گوشی روی silent نیست
2. بررسی کنید که Do Not Disturb غیرفعال است
3. volume ALARM stream را چک کنید

### مشکل: در حالت Battery Optimization کار نمی‌کند
**راه حل:**
```kotlin
// در یک Activity
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    if (!pm.isIgnoringBatteryOptimizations(packageName)) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }
}
```

## 📱 تنظیمات گوشی مورد نیاز

### برای تمام گوشی‌ها:
1. Settings > Apps > Your App > Permissions
   - ✅ Display over other apps
   - ✅ Alarms & reminders

2. Settings > Apps > Your App > Battery
   - ✅ Unrestricted (یا Optimized را خاموش کنید)

### برای Samsung:
3. Settings > Apps > Your App > Battery > Optimize battery usage
   - ✅ All apps را انتخاب کنید
   - ✅ برنامه را پیدا کنید و غیرفعال کنید

## 📊 لاگ‌های مهم

برای دیدن لاگ‌ها:
```bash
adb logcat | grep -E "ReminderReceiver|FullScreenAlarm|SmartReminder"
```

لاگ‌های موفق:
```
D/ReminderReceiver: onReceive called with action: com.persianai.assistant.REMINDER_ALARM
D/ReminderReceiver: Processing reminder: ID=123, SmartID=abc, Message=Test
D/ReminderReceiver: Triggering reminder: Test (useAlarm: true)
D/ReminderReceiver: showFullScreenAlarm called for: Test
D/FullScreenAlarm: onCreate called
D/FullScreenAlarm: Starting alarm sound
D/FullScreenAlarm: Alarm sound started successfully
D/FullScreenAlarm: Starting vibration
D/FullScreenAlarm: Vibration started successfully
```

## ✅ چک‌لیست نهایی

- [ ] تمام فایل‌ها ذخیره شده‌اند
- [ ] AndroidManifest به‌روز شده
- [ ] Permissions اضافه شده‌اند
- [ ] Build بدون خطا انجام شده
- [ ] تست در حالت Foreground موفق بوده
- [ ] تست در حالت Background موفق بوده
- [ ] تست در حالت Lock Screen موفق بوده

## 🎓 نکات مهم

1. **WAKE_LOCK**: حتماً در `finally` block آزاد شود
2. **MediaPlayer**: حتماً `release()` شود تا memory leak نداشته باشید
3. **Vibrator**: حتماً `cancel()` شود
4. **Permission Runtime**: در Android 13+، permission POST_NOTIFICATIONS باید runtime درخواست شود

## 🔗 منابع مفید

- [Android AlarmClock Documentation](https://developer.android.com/reference/android/provider/AlarmClock)
- [Full Screen Intent Documentation](https://developer.android.com/reference/androidx/core/app/NotificationCompat.Builder#setFullScreenIntent)
- [WakeLock Best Practices](https://developer.android.com/training/scheduling/wakelock)
