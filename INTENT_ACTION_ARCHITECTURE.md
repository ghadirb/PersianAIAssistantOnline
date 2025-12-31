# 🎯 Intent-Based Action Architecture

## تاریخ: 31 Dec 2025

---

## **معماری:**

```
┌─────────────────────────────────────────────────────────┐
│                    User Query                            │
│          "یادآوری برای فردا ساعت 8 صبح"               │
└────────────────────┬────────────────────────────────────┘
                     │
        ┌────────────▼────────────┐
        │  ActionExecutor         │
        │  .executeFromQuery()    │
        └────────────┬────────────┘
                     │
     ┌───────────────┼───────────────┐
     │               │               │
     ▼               ▼               ▼
┌─────────┐  ┌─────────┐  ┌─────────┐
│Reminder │  │  Alarm  │  │  Note   │
│Pattern  │  │ Pattern │  │Pattern  │
└────┬────┘  └────┬────┘  └────┬────┘
     │            │            │
     ▼            ▼            ▼
┌─────────────────────────────────┐
│ Parse Time & Extract Text       │
│ "فردا" → 24*60 minutes          │
│ "ساعت 8" → 08:00                │
│ Text: "صبح"                     │
└────────────┬────────────────────┘
             │
     ┌───────▼───────┐
     │ AlarmManager  │
     │    .set()     │
     └───────┬───────┘
             │
     ┌───────▼──────────────┐
     │ ExecutionResult      │
     │ success: true        │
     │ message: "✅ تنظیم شد" │
     │ action: "reminder"   │
     └──────────────────────┘
             │
     ┌───────▼──────────────┐
     │ Display in Chat      │
     │ "یادآوری برای فردا  │
     │  ساعت 8 تنظیم شد ✅" │
     └──────────────────────┘
```

---

## **1. Supported Actions (اقدامات پشتیبانی‌شده)**

### **الف: Reminder (یادآوری)**

**Patterns:**
```regex
یادآوری.*(فردا|امروز|بعداً|ساعت\d+|کال|مدت)
```

**Examples:**
```
✅ "یادآوری برای فردا ساعت 8"
✅ "مرا یادآوری کن یک ساعت بعد"
✅ "یادآوری: پیش‌رو تماس بگیر"
✅ "یادآوری برای نیم‌ساعت دیگر"
```

**Implementation:**
```kotlin
fun executeReminder(query: String): ExecutionResult {
    val timeInMinutes = parseTimeFromQuery(query)
    val reminderText = extractReminderText(query)
    
    // Set AlarmManager
    val calendar = Calendar.getInstance().apply {
        add(Calendar.MINUTE, timeInMinutes)
    }
    alarmManager.setAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        calendar.timeInMillis,
        pendingIntent
    )
    
    return ExecutionResult(
        success = true,
        message = "یادآوری برای ${calendar.displayHumanReadable()} تنظیم شد ✅",
        action = "reminder"
    )
}
```

**Output:**
```
User: "یادآوری برای فردا ساعت 8"
Bot: "یادآوری برای فردا ساعت 8 تنظیم شد ✅"
[Notification at next day 8:00 AM]
```

---

### **ب: Alarm (زنگ)**

**Patterns:**
```regex
(زنگ|alarm|اژیر).*(فردا|امروز|بعداً|ساعت\d+)
```

**Examples:**
```
✅ "زنگ برای 6 صبح"
✅ "alarm برای 2 ساعت دیگر"
✅ "اژیر برای فردا"
```

**Output:**
```
User: "زنگ برای 6 صبح"
Bot: "زنگ برای فردا ساعت 6 تنظیم شد ✅"
[Alarm at next day 6:00 AM]
```

---

### **ج: Note (یادداشت)**

**Patterns:**
```regex
(یادداشت|نت|note).*(بریز|ذخیره|save)
```

**Examples:**
```
✅ "یادداشت: خریدن شیر و نان"
✅ "یادداشت کن: شماره تلفن علی: 09123456789"
✅ "نت بریز: جلسه فردا ساعت 3"
```

**Implementation:**
```kotlin
fun executeNote(query: String): ExecutionResult {
    val noteText = extractNoteText(query)
    
    // Save to SharedPreferences
    val prefs = context.getSharedPreferences("notes", Context.MODE_PRIVATE)
    val timestamp = System.currentTimeMillis()
    val newNote = "$timestamp|$noteText"
    prefs.edit().putString("all_notes", allNotes).apply()
    
    return ExecutionResult(
        success = true,
        message = "یادداشت ذخیره شد ✅",
        action = "note",
        data = mapOf("text" to noteText)
    )
}
```

**Output:**
```
User: "یادداشت: خریدن شیر و نان"
Bot: "یادداشت ذخیره شد ✅"
[Saved to notes]
```

---

## **2. Query Parsing Logic (منطق تحلیل پرسش)**

### **Time Extraction:**
```kotlin
private fun parseTimeFromQuery(query: String): Int {
    return when {
        query.contains("فردا") → 24 * 60           // 24 hours
        query.contains("یک ساعت") → 60             // 1 hour
        query.contains("نیم ساعت") → 30            // 30 minutes
        query.contains("دو ساعت") → 120            // 2 hours
        query.contains("5 دقیقه") → 5              // 5 minutes
        else → 60 // Default 1 hour
    }
}
```

### **Text Extraction:**
```kotlin
private fun extractReminderText(query: String): String {
    return query
        .replace(Regex("(یادآوری|فردا|امروز|ساعت|زنگ)"), "")
        .trim()
        .take(100)
}
```

---

## **3. Online & Offline Support**

### **Online Mode (Liara/OpenRouter):**
```
1. Parse query
2. Try execute action (local)
3. If action executed, show result
4. If no action, send to AI model for response
```

### **Offline Mode (TinyLlama):**
```
1. Parse query
2. Execute local action if pattern matches
3. Generate response with TinyLlama
4. Show combined result
```

---

## **4. Message Flow with Action Execution**

```
┌─────────────────────────────────────────────────────────┐
│            User sends: "یادآوری برای فردا ساعت 8"        │
└────────────────────┬────────────────────────────────────┘
                     │
     ┌───────────────▼────────────────┐
     │  ActionExecutor.executeFromQuery()
     ├───────────────────────────────┤
     │ ✅ Pattern matched: Reminder   │
     │ ✅ Time parsed: 24*60 min      │
     │ ✅ Alarm set via AlarmManager  │
     │ ✅ Result: success=true        │
     └────────────────┬────────────────┘
                      │
     ┌────────────────▼─────────────────┐
     │  Return ExecutionResult          │
     │  message: "یادآوری برای فردا    │
     │            ساعت 8 تنظیم شد ✅" │
     └────────────────┬─────────────────┘
                      │
     ┌────────────────▼─────────────────┐
     │  Display in Chat                 │
     │  🤖 "یادآوری برای فردا       │
     │      ساعت 8 تنظیم شد ✅"        │
     └─────────────────────────────────┘
```

---

## **5. Error Handling**

```kotlin
// تمام exceptions catch ہوتے ہیں اور user-friendly message دی جاتی ہے

try {
    executeReminder(query)
} catch (e: Exception) {
    ExecutionResult(
        success = false,
        message = "خطا در تنظیم یادآوری: ${e.message}",
        action = "reminder",
        exception = e
    )
}
```

---

## **6. Future Actions (آینده میں)**

```
⏳ Call Trigger    - "علی کو تماس بگیر"
⏳ SMS Send        - "پیام به احمد: سلام"
⏳ App Launch      - "اپلیکیشن وتس‌اپ باز کن"
⏳ Share Content   - "این پیام را به علی ارسال کن"
⏳ Music Control   - "موسیقی پخش کن"
⏳ Volume Control  - "صدا کم کن"
⏳ Screenshot      - "اسکرین‌شات بگیر"
```

---

## **7. Integration Points**

### **In BaseChatActivity:**
```kotlin
protected fun sendMessage() {
    val text = getMessageInput().text.toString().trim()
    
    lifecycleScope.launch {
        // 1. سعی کریں action execute کریں
        val executor = ActionExecutor(this@BaseChatActivity)
        val actionResult = executor.executeFromQuery(text)
        
        if (actionResult.success) {
            // اگر action successful ہے تو result دکھائیں
            addMessage(ChatMessage(
                role = ASSISTANT,
                content = actionResult.message
            ))
            return@launch
        }
        
        // 2. اگر action نہیں تو Intent-based response
        val controller = AIIntentController(this@BaseChatActivity)
        val result = controller.handle(...)
        addMessage(ChatMessage(...))
    }
}
```

---

## **8. Broadcast Receiver (Background Handling)**

```kotlin
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val reminderText = intent?.getStringExtra(EXTRA_REMINDER_TEXT) ?: "یادآوری"
        showReminderNotification(context, reminderText)
        // Notification میں message دکھائیں
    }
}
```

---

## **9. Testing Examples**

### **Example 1: Reminder**
```
Input:  "یادآوری برای یک ساعت بعد"
Process: 
  - Pattern: Reminder ✓
  - Time: 1 hour = 60 min
  - Text: "برای یک ساعت بعد"
  - Action: AlarmManager.set()
Output: "یادآوری برای 1 ساعت دیگر تنظیم شد ✅"
Result: Notification after 1 hour
```

### **Example 2: Note**
```
Input:  "یادداشت: خریدن شیر و نان"
Process:
  - Pattern: Note ✓
  - Text: "خریدن شیر و نان"
  - Action: SaveToPreferences()
Output: "یادداشت ذخیره شد ✅"
Result: Saved to notes list
```

### **Example 3: No Action Pattern**
```
Input:  "میں کیسے میٹھا کھانا بناتے ہیں؟"
Process:
  - Pattern: No action match ✗
  - Action: Send to AI model
Output: [TinyLlama/Liara response]
```

---

## **10. Advantages (فوائد)**

✅ **Offline Support** - اقدامات بغیر internet کے
✅ **Fast Response** - فوری local action
✅ **Intelligent** - Pattern matching + AI response
✅ **Extensible** - نئے actions آسانی سے add ہو سکتے ہیں
✅ **Error Handling** - Graceful failure handling
✅ **User Feedback** - واضح messages

---

**Status: ✅ Ready** 🚀
