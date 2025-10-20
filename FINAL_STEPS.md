# مراحل نهایی

## 1. دکمه‌های قدیمی را nullable کن:

در NavigationActivity.kt خط 273:
```kotlin
binding.searchDestinationButton?.setOnClickListener {
binding.savedLocationsButton?.setOnClickListener {
binding.poiButton?.setOnClickListener {
binding.saveCurrentLocationButton?.setOnClickListener {
binding.startNavigationButton?.setOnClickListener {
binding.stopNavigationButton?.setOnClickListener {
binding.addWaypointButton?.setOnClickListener {
```

## 2. Build بگیر

## 3. تست کن:
- جستجو با AI
- Long press روی نقشه
- دکمه ترافیک
- تب‌ها

✅ همه چیز آماده است!
