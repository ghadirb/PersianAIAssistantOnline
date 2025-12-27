AI-First Architecture
====================

## 🧠 معماری جدید: AI-Driven Intent-Based System

### مرحله به مرحله

#### 1️⃣ ورودی → Intent Detection
```
Text/Voice/Notification
        ↓
   EnhancedIntentDetector
        ↓
   AIIntent (typed)
```

#### 2️⃣ Intent Processing
```
AIIntent
   ↓
AIIntentController.handle()
   ↓
Matches to Module
   ↓
Module.execute()
   ↓
AIIntentResult
```

---

## 📦 Core Components

### 1. Intent Definition (`core/intent/AIIntent.kt`)
تمام Intent‌های ممکن به صورت sealed class تعریف شده‌اند:

```kotlin
// Reminders
ReminderCreateIntent
ReminderListIntent  
ReminderDeleteIntent
ReminderUpdateIntent

// Navigation
NavigationSearchIntent
NavigationStartIntent

// Finance
FinanceTrackIntent
FinanceReportIntent

// Education
EducationAskIntent
EducationGenerateQuestionIntent

// Call
CallSmartIntent

// Weather
WeatherCheckIntent

// Music
MusicPlayIntent

// Assistant
AssistantChatIntent
```

### 2. Intent Detection (`core/EnhancedIntentDetector.kt`)
- تشخیص خودکار Intent از متن فارسی
- الگوهای regex برای هر دسته
- استخراج پارامترها (مثلاً مقصد، نام مخاطب، وغیره)

### 3. Controller (`core/AIIntentController.kt`)
- دریافت AIIntentRequest
- تطابق Intent با ماژول صحیح
- اجرای ماژول و برگرداندن نتیجه
- سیاسی logging و error handling

### 4. Modules (`core/modules/`)
هر ماژول مستقل و مسئول‌پذیر:

```
BaseModule (abstract)
├── AssistantModule
├── ReminderModule
├── NavigationModule
├── FinanceModule
├── EducationModule
├── CallModule
├── WeatherModule
└── MusicModule
```

---

## 🔄 Data Flow

### Example: "یادآوری برای فردا ساعت ۹"

1. **Input Source**:
   - UI Text Field
   - Voice Transcription
   - Notification Action

2. **Intent Detection**:
   ```kotlin
   val detector = EnhancedIntentDetector(context)
   val intent = detector.detectIntent(text)
   // Result: ReminderCreateIntent(rawText="...", type="reminder")
   ```

3. **Request Creation**:
   ```kotlin
   val request = AIIntentRequest(
       intent = intent,
       source = AIIntentRequest.Source.UI,  // or VOICE, NOTIFICATION
       workingModeName = "HYBRID"
   )
   ```

4. **Handling**:
   ```kotlin
   val controller = AIIntentController(context)
   val result = controller.handle(request)
   // Routed to ReminderModule.execute()
   ```

5. **Result**:
   ```kotlin
   AIIntentResult(
       text = "✅ یادآوری تنظیم شد",
       intentName = "reminder.create",
       actionType = "reminder_created",
       actionData = "09:00"
   )
   ```

---

## 🎯 Benefits

✅ **منطق جدا از UI**
- UI فقط واسط است
- هیچ منطق تجاری در Activity نیست

✅ **Scalability**
- اضافه کردن قابلیت جدید = یک Intent جدید + یک Module جدید
- بدون تغییر Core Controller

✅ **Testability**
- هر ماژول مستقل قابل تست
- Intent Detection قابل تست

✅ **Voice/Notification/UI Unified**
- تمام sources یک path واحد می‌روند
- منطق یکسان برای همه

---

## 🔧 Usage Examples

### From UI
```kotlin
// MainActivity.kt
val controller = AIIntentController(this)
val intent = controller.detectIntentFromText(userText)
val result = controller.handle(
    AIIntentRequest(
        intent = intent,
        source = AIIntentRequest.Source.UI,
        workingModeName = mode.name
    )
)
```

### From Voice (Notification)
```kotlin
// VoiceCommandService.kt
val intent = controller.detectIntentFromText(transcribedText)
val result = controller.handle(
    AIIntentRequest(
        intent = intent,
        source = AIIntentRequest.Source.NOTIFICATION,
        workingModeName = PreferencesManager(this).getWorkingMode().name
    )
)
```

### From Direct Module
```kotlin
// If you need to call a specific module directly
val module = ReminderModule(context)
val result = module.execute(request, reminderIntent)
```

---

## 📝 Adding New Features

### Step 1: Define Intent
```kotlin
// core/intent/AIIntent.kt
data class MyNewIntent(
    override val rawText: String,
    val param1: String? = null
) : AIIntent(rawText) {
    override val name: String = "mynew.action"
}
```

### Step 2: Add Detection Pattern
```kotlin
// core/EnhancedIntentDetector.kt
private fun matchesMyNew(t: String) = 
    t.contains("کلمه کلیدی")

private fun detectIntent(text: String): AIIntent {
    return when {
        matchesMyNew(t) -> MyNewIntent(rawText = text)
        // ...
    }
}
```

### Step 3: Create Module
```kotlin
// core/modules/MyNewModule.kt
class MyNewModule(context: Context) : BaseModule(context) {
    override val moduleName = "MyNew"
    
    override suspend fun canHandle(intent: AIIntent) =
        intent is MyNewIntent
    
    override suspend fun execute(req: AIIntentRequest, intent: AIIntent): AIIntentResult {
        // Your logic here
        return createResult(...)
    }
}
```

### Step 4: Register in Controller
```kotlin
// core/AIIntentController.kt
private val myNewModule = MyNewModule(context)

suspend fun handle(request: AIIntentRequest): AIIntentResult {
    return when (val i = request.intent) {
        is MyNewIntent -> myNewModule.execute(request, i)
        // ...
    }
}
```

---

## 🔐 Security

### API Keys Management
```kotlin
// config/APIKeysConfig.kt
val config = APIKeysConfig(context)
config.saveEncryptedKeys(data)
config.hasKeys()
config.clearKeys()
```

### Working Modes
- **OFFLINE**: محلی، بدون اتصال
- **HYBRID**: ابتدا آنلاین، بعد محلی
- **ONLINE**: فقط آنلاین

---

## 📊 Current Status

✅ Implemented:
- Core Intent Architecture
- 8 Main Modules (Assistant, Reminder, Navigation, Finance, Education, Call, Weather, Music)
- Enhanced Intent Detection
- Request/Response Pipeline
- Voice Command Service Integration
- Notification Service Integration

🔄 Integration Points:
- MainActivity (UI Entry)
- VoiceCommandService (Voice Entry)
- AIAssistantService (Foreground Notification)
- NotificationActions (Quick Actions)

---

## 🚀 Next Steps

1. ✅ Compile and test
2. ✅ Voice recognition integration
3. ✅ Notification quick actions
4. ✅ More sophisticated Intent patterns
5. ✅ Analytics and logging
