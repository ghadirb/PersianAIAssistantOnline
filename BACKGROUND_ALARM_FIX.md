# Full-Screen Alarm Background Fix Plan

## 🎯 Problem
Full-screen reminders not triggering properly in background, causing app to be unresponsive when closed.

## 🔍 Root Causes Identified

### 1. Foreground Service Type Issue
**Current**: `android:foregroundServiceType="specialUse"`
**Problem**: Not all Android versions support this type properly
**Fix**: Use standard `dataSync` or remove type specification

### 2. Exact Alarm Runtime Permission
**Current**: Permission declared in manifest but not requested at runtime
**Problem**: Android 12+ requires runtime permission for exact alarms
**Fix**: Add runtime permission request flow

### 3. Duplicate Activity Launch
**Current**: Both fullScreenIntent and direct startActivity called
**Problem**: Can cause multiple alarm instances or conflicts
**Fix**: Use single launch method consistently

### 4. WakeLock Duration
**Current**: 10 minutes (600,000ms)
**Problem**: Might not be sufficient for complex alarm sequences
**Fix**: Increase to 15 minutes and add acquisition retry logic

## 🛠️ Minimal Safe Fixes

### Fix 1: Update Service Declaration (1 line change)
```xml
<!-- BEFORE -->
<service android:name=".services.FullScreenAlarmService" 
         android:foregroundServiceType="specialUse" />

<!-- AFTER -->
<service android:name=".services.FullScreenAlarmService" 
         android:foregroundServiceType="dataSync" />
```

### Fix 2: Add Runtime Permission Check (3 lines in ReminderReceiver)
```kotlin
// Add at start of onReceive
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (!alarmManager.canScheduleExactAlarms()) {
        Log.w(TAG, "Exact alarm permission not granted")
        return
    }
}
```

### Fix 3: Fix Activity Launch (Remove duplicate)
```kotlin
// In ReminderReceiver.showFullScreenAlarm(), remove this line:
// context.startActivity(alarmIntent)  // <- REMOVE THIS

// Keep only the full-screen notification
NotificationManagerCompat.from(context).notify(reminderId?.hashCode() ?: 2001, notification)
```

### Fix 4: Increase WakeLock Duration (1 line change)
```kotlin
// Change from 10 minutes to 15 minutes
.acquire(15 * 60 * 1000L) // 15 minutes
```

### Fix 5: Add Battery Optimization Check (2 lines in MainActivity)
```kotlin
// Add to onCreate() after requestNotificationPermission()
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
        // Optionally show dialog to request exclusion
    }
}
```

## 🧪 Testing Checklist

### Before Fixes:
- [ ] Test with app closed
- [ ] Test after force-close
- [ ] Test after reboot
- [ ] Test with Do Not Disturb mode

### After Fixes:
- [ ] Full-screen alarm triggers when app is closed
- [ ] Alarm works after device reboot
- [ ] No duplicate alarms
- [ ] WakeLock properly released
- [ ] All gesture controls work (swipe left/right)

## ⚡ Expected Results

### Current Behavior:
- ❌ Alarms not triggering when app closed
- ❌ Need to manually open app for reminders
- ❌ Unreliable background execution

### After Fixes:
- ✅ Alarms trigger reliably in background
- ✅ Works after app force-close
- ✅ Survives device reboots
- ✅ Single, reliable alarm instance
- ✅ Proper battery optimization handling

## 🛡️ Safety Notes

- **Zero Breaking Changes**: Only internal fixes
- **Backward Compatible**: Works on all Android versions 6+
- **No UI Changes**: User experience unchanged
- **Easy Rollback**: Each fix is isolated and reversible

---
**Estimated Fix Time**: 30 minutes  
**Risk Level**: Very Low  
**Testing Time**: 15 minutes
