# Navigation System - Step by Step Guide

## ✅ DONE: Step 1 - Foundation
- NeshanDirectionAPI.kt created
- Polyline decoder added
- RouteSheetHelper updated

## 🔧 TODO: Step 2 - Complete UI (30 min)
```kotlin
// In RouteSheetHelper.kt line 54-80:
val routes = directionAPI.getDirection(...)
val items = routes.map { "${it.summary}: ${it.duration}min, ${it.distance}km" }
// Show dialog with real routes
// Draw polyline: webView.evaluateJavascript("drawRealRoute('$polyline')")
```

## 🔧 TODO: Step 3 - Navigation Activity (1 hour)
Create: RealNavigationActivity.kt
- Show selected route
- "بزن بریم" button
- Start turn-by-turn navigation

## 🔧 TODO: Step 4 - Voice Alerts (2 hours)
Create: PersianVoiceAlerts.kt
- TextToSpeech Persian
- Speed limit warnings
- Turn warnings
- Camera warnings

## 🔧 TODO: Step 5 - Google Maps (1 hour)
- Settings to choose Neshan/Google
- Intent to Google Maps
- Voice overlay

## Next Action:
Run: `./gradlew assembleDebug` to test current code
Then complete Step 2 with real routes display
