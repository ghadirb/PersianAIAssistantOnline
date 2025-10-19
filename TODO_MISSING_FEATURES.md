# TODO: قابلیت‌های ناقص و نیازمند پیاده‌سازی

## ⚠️ UI Elements گمشده در Layouts

### DashboardActivity (activity_main_dashboard.xml)
- [ ] `weatherCard` - کارت آب و هوا
- [ ] `weatherTempText` - متن دمای هوا
- [ ] `weatherIcon` - آیکون آب و هوا
- [ ] `hourlyBtn` - دکمه پیش‌بینی ساعتی
- [ ] `weeklyBtn` - دکمه پیش‌بینی هفتگی

### MusicActivity (activity_music.xml)
- [ ] `previousButton` - دکمه قطعه قبلی
- [ ] `aiChatButton` - دکمه چت با AI
- [ ] `playlistChipGroup` - گروه چیپ‌های پلی‌لیست
- [ ] `trackTitleText` - عنوان آهنگ
- [ ] `artistText` - نام هنرمند
- [ ] `currentTimeText` - زمان فعلی
- [ ] `totalTimeText` - زمان کل

### NavigationActivity (activity_navigation.xml)
- [ ] `alertSettingsButton` - دکمه تنظیمات هشدارها
- [ ] `syncButton` - دکمه همگام‌سازی

### ProfessionalNavigationActivity
- [ ] `voiceNavigationButton` - دکمه ناوبری صوتی
- [ ] `distanceText` - متن مسافت
- [ ] `durationText` - متن مدت زمان
- [ ] `trafficText` - متن وضعیت ترافیک
- [ ] `instructionText` - متن دستورالعمل
- [ ] `instructionDistance` - مسافت تا دستورالعمل بعدی

## 🔧 متدها و کلاس‌های ناقص

### NavigationRoute Model
- [ ] `steps` property - لیست مراحل مسیر (NavigationStep)
- [ ] `NavigationStep` data class - کلاس داده برای هر مرحله از مسیر

### Navigation Detectors/Analyzers
- [ ] `SpeedCameraDetector.enable()` - فعال‌سازی تشخیص دوربین
- [ ] `SpeedCameraDetector.disable()` - غیرفعال‌سازی تشخیص دوربین
- [ ] `TrafficAnalyzer.enable()` - فعال‌سازی تحلیل ترافیک
- [ ] `TrafficAnalyzer.disable()` - غیرفعال‌سازی تحلیل ترافیک
- [ ] `RoadConditionAnalyzer.enable()` - فعال‌سازی تحلیل جاده
- [ ] `RoadConditionAnalyzer.disable()` - غیرفعال‌سازی جاده
- [ ] `SpeedCameraDetector.setSpeedBumpAlertsEnabled()`
- [ ] `SpeedCameraDetector.setCameraAlertsEnabled()`
- [ ] `TrafficAnalyzer.setEnabled()`
- [ ] `RoadConditionAnalyzer.setEnabled()`
- [ ] `SpeedCameraDetector.setVoiceAlertsEnabled()`
- [ ] `TrafficAnalyzer.setVoiceAlertsEnabled()`
- [ ] `RoadConditionAnalyzer.setVoiceAlertsEnabled()`

### NavigationManager
- [ ] `getCurrentStep()` - دریافت مرحله فعلی مسیر
- [ ] `hasReachedDestination()` - بررسی رسیدن به مقصد
- [ ] Google Directions API integration - یکپارچه‌سازی کامل با Google Maps
- [ ] DirectionsResult to NavigationRoute conversion
- [ ] Polyline decoding
- [ ] Route settings (avoidTolls, avoidHighways, avoidFerries) implementation

### MusicActivity
- [ ] `MusicPlaylistManager.getAllPlaylists()` - دریافت تمام پلی‌لیست‌ها
- [ ] `Track` data class definition or proper import

## 📝 نکات مهم

### تغییرات انجام شده برای Compile شدن پروژه:
1. تمام UI elements گمشده با safe call (`?.`) و یا comment out شده‌اند
2. متدهای ناموجود با TODO comment out شده‌اند
3. property های `steps` در NavigationRoute موقتاً حذف یا comment شده‌اند
4. integration های Google Maps API به صورت stub پیاده‌سازی شده‌اند

### اولویت پیاده‌سازی:
1. **فوری**: اضافه کردن UI elements به layout XMLها
2. **مهم**: پیاده‌سازی NavigationStep و steps property
3. **معمولی**: متدهای enable/disable در detectors
4. **اختیاری**: Google Maps API integration کامل

## 🚀 راهنمای پیاده‌سازی

### برای اضافه کردن UI Elements:
```xml
<!-- مثال برای activity_main_dashboard.xml -->
<com.google.android.material.card.MaterialCardView
    android:id="@+id/weatherCard"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">
    
    <TextView
        android:id="@+id/weatherTempText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content" />
        
    <TextView
        android:id="@+id/weatherIcon"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content" />
</com.google.android.material.card.MaterialCardView>
```

### برای پیاده‌سازی NavigationStep:
```kotlin
data class NavigationStep(
    val instruction: String,
    val distance: Double,
    val duration: Long,
    val maneuver: String,
    val location: GeoPoint
)

// سپس در NavigationRoute:
data class NavigationRoute(
    // ... سایر property ها
    val steps: List<NavigationStep> = emptyList()
)
```

### برای متدهای enable/disable:
```kotlin
// در SpeedCameraDetector, TrafficAnalyzer, و RoadConditionAnalyzer
private var isEnabled: Boolean = true

fun enable() {
    isEnabled = true
}

fun disable() {
    isEnabled = false
}

fun setEnabled(enabled: Boolean) {
    isEnabled = enabled
}
```

## 📊 وضعیت فعلی پروژه
- ✅ **Compile می‌شود**: تمام خطاهای کامپایل حل شده
- ⚠️ **Runtime ناقص**: برخی قابلیت‌ها به دلیل UI elements یا متدهای گمشده کار نمی‌کنند
- 📝 **نیازمند توسعه**: قابلیت‌های لیست شده در این فایل

## 📅 تاریخ ایجاد
2025-10-19

## 👤 توسعه‌دهنده
Cascade AI Assistant

---
**نکته**: این فایل را به‌روز نگه دارید و پس از پیاده‌سازی هر قابلیت، checkbox مربوطه را علامت بزنید.
