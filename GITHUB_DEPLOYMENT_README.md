# 🚀 Persian AI Assistant - GitHub Deployment Guide

## 📱 Project Overview

**Persian AI Assistant Online** - A hybrid AI assistant for Android that works both online and offline with complete voice support and automatic action execution.

## ✨ Key Features

### 🧠 AI Models
- **Online Models**: 
  - 🌐 Gemini 2.0 Flash (via Liara API)
  - 🤖 GPT-4o-mini (via OpenRouter - FREE)
  - 📡 OpenAI GPT-3.5/GPT-4
  - 💬 AIML chatbot

- **Offline Models**:
  - ⚡ TinyLlama 1.1B (0.6GB) - Pre-configured
  - 🧠 Mistral 7B (4.1GB) - Optional download
  - 🦙 Llama 2 7B (3.8GB) - Optional download

### 🎤 Voice Support
- **Speech-to-Text**: 
  - 🎧 Haaniye ONNX (Persian, offline)
  - ☁️ Gemini 2.0 Flash (online fallback)
- **Text-to-Speech**: TTS integration for responses

### 🎯 Action Execution
Automatic pattern-based execution:
- 📌 **Reminders** - Set with natural language (`یادآوری ساعت 3`)
- ⏰ **Alarms** - Schedule with voice (`زنگ الرم ساعت 5 صبح`)
- 📝 **Notes** - Create and store (`یادداشت بنام مهم`)

### 🔌 Hybrid Architecture
- **Online-First**: Prioritizes cloud models for quality
- **Offline-Ready**: Instant fallback to local models
- **Action-First**: Executes patterns before queries
- **Centralized Routing**: QueryRouter ensures all queries reach models

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  User Query                             │
└───────────────────┬─────────────────────────────────────┘
                    │
        ┌───────────▼─────────────┐
        │  QueryRouter.routeQuery │
        └───────────┬─────────────┘
                    │
        ┌───────────▼────────────────────────┐
        │ 1. ActionExecutor                  │
        │    (Pattern match: Reminder/Alarm) │
        └───────────┬────────────────────────┘
                    │ (if no match)
        ┌───────────▼────────────────────────┐
        │ 2. Online Model                    │
        │    (Liara/OpenRouter)              │
        └───────────┬────────────────────────┘
                    │ (if offline)
        ┌───────────▼────────────────────────┐
        │ 3. Offline Model                   │
        │    (TinyLlama via JNI)             │
        └───────────┬────────────────────────┘
                    │
        ┌───────────▼──────────────────────┐
        │   Return Response to User        │
        └────────────────────────────────┘
```

## 📦 Files Structure

### Core Architecture
- `QueryRouter.kt` - Centralized query routing system
- `ActionExecutor.kt` - Pattern-based action execution
- `OfflineModelManager.kt` - Offline model management
- `AutoProvisioningManager.kt` - API key provisioning with free fallback

### Model Managers
- `HaaniyeManager.kt` - Haaniye ONNX speech recognition
- `LocalLlamaRunner.kt` - JNI interface for TinyLlama/Mistral/Llama2
- `SpeechToTextPipeline.kt` - Voice input pipeline
- `NewHybridVoiceRecorder.kt` - Hybrid voice recording

### UI & Activities
- `BaseChatActivity.kt` - Main chat UI with QueryRouter integration
- Other activities using centralized routing

### APIs & Services
- `LiaraAPIManager.kt` - Gemini 2.0 Flash integration
- `OpenRouterAPIManager.kt` - OpenRouter free API
- `OpenAIAPIManager.kt` - OpenAI integration

### Assets
- `assets/tts/haaniye/fa-haaniye.onnx` - Persian speech recognition model (109MB)
- `assets/models/` - Offline model references (optional)

## 🔧 Build Requirements

- **Java**: 17+
- **Android SDK**: 34 (min: 26)
- **NDK**: 25.1+ (for JNI)
- **Gradle**: 8.2+
- **Gradle Plugin**: 8.1+

## 🏗️ GitHub Actions Build

This project includes automated GitHub Actions CI/CD:

### Build Pipeline
```yaml
✅ Checkout code
✅ Setup Java 17
✅ Setup Android SDK 34 + NDK
✅ Build Debug APK
✅ Generate build report
✅ Upload artifacts
```

### Automatic Triggers
- Push to `main`, `master`, `develop`
- Tag creation (`v*`)
- Pull requests

### Generated Artifacts
- `app-debug-apk` - Built APK file
- `build-logs` - Build report and error logs

## 🚀 Deployment Steps

### 1. Prerequisites
```bash
git clone https://github.com/YOUR_USERNAME/PersianAIAssistantOnline
cd PersianAIAssistantOnline
```

### 2. Local Build
```bash
# Setup
chmod +x gradlew
./gradlew --version

# Build
./gradlew assembleDebug

# Result: app/build/outputs/apk/debug/app-debug.apk
```

### 3. GitHub Actions Build
- Push to main branch
- Actions automatically trigger
- Check Actions tab for progress
- Download APK from artifacts

### 4. Installation
```bash
# Enable USB debugging on device
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 🔑 API Keys Configuration

### Free API Fallback
The app automatically falls back to free APIs:
- **OpenRouter**: `sk-or-free` (primary)
- **OpenAI**: Trial key fallback
- **AIML**: Free tier

### Provided API Keys
Place in `assets/api_keys.json` or use Auto-Provisioning:
```json
{
  "liara": "YOUR_LIARA_KEY",
  "openrouter": "sk-or-...",
  "openai": "sk-..."
}
```

## 🧪 Testing

### Voice Testing
1. Open app
2. Click voice button
3. Speak a command (Persian or English)
4. App records and transcribes
5. Model processes and responds

### Offline Testing
1. Disable network
2. Send a text query
3. TinyLlama processes locally
4. Response appears instantly

### Action Testing
```
Try these commands:
- "یادآوری ساعت 3 بعد از ظهر - کار مهم"
- "زنگ الرم ساعت 7 صبح"
- "یادداشت - تماس با علی"
```

## 📊 Build Information

### APK Details
- **Size**: ~50-55MB (includes Haaniye ONNX)
- **Min API**: 26 (Android 8.0)
- **Target API**: 34 (Android 14)
- **Architectures**: armeabi-v7a, arm64-v8a

### Included Components
- ✅ Haaniye speech recognition (109MB extracted)
- ✅ TinyLlama model support
- ✅ All required JNI libraries
- ✅ Voice recording permissions
- ✅ Online/offline fallback

## 🐛 Troubleshooting

### Build Fails
```bash
# Clean build
./gradlew clean
./gradlew assembleDebug

# Check Java version
java -version  # Should be 17+

# Check NDK
$ANDROID_HOME/ndk-list
```

### Voice Not Working
- Check microphone permission
- Verify Haaniye model in `assets/tts/haaniye/`
- Check logs for "HaaniyeManager" errors

### Models Not Loading
- Verify `OfflineModelManager.kt` model paths
- Check file permissions
- Ensure sufficient storage (TinyLlama needs 0.6GB)

### API Key Issues
- Check internet connection for online models
- Verify API key in `AutoProvisioningManager.kt`
- Free fallback activates automatically

## 📚 Documentation

- `COMPLETE_ARCHITECTURE.md` - Full system design
- `OFFLINE_MODE_COMPLETE.md` - Offline setup details
- `VOICE_TESTING_GUIDE.md` - Voice feature testing
- `INTENT_ACTION_ARCHITECTURE.md` - Action execution system
- `FINAL_DEPLOYMENT_STATUS.md` - Current status

## 🔄 Workflow

### For Developers
1. Fork repository
2. Create feature branch
3. Make changes
4. GitHub Actions auto-builds
5. Submit pull request
6. Merge after review

### For Users
1. Download APK from releases
2. Enable installation from unknown sources
3. Install APK
4. Grant required permissions
5. Start chatting!

## ⚙️ Configuration

### Online Models Priority
```kotlin
// QueryRouter.kt
// Try in order:
1. Liara (Gemini 2.0 Flash)
2. OpenRouter (Free)
3. OpenAI (Paid)
4. AIML (Fallback)
```

### Offline Models Priority
```kotlin
// OfflineModelManager.kt
// Try in order:
1. TinyLlama (pre-configured)
2. Mistral (if downloaded)
3. Llama2 (if downloaded)
```

## 🎯 Performance

### Response Times
- **Actions**: < 1 second (local execution)
- **Online**: 2-5 seconds (network dependent)
- **Offline**: 3-10 seconds (device dependent)

### Memory Usage
- **Idle**: ~150MB
- **Recording**: ~250MB
- **Inference**: ~500MB (with TinyLlama)

### Storage
- **APK**: 50-55MB
- **Haaniye**: 109MB (extracted)
- **TinyLlama**: 600MB (optional)

## 🔐 Security

- API keys encrypted with AES-GCM
- Key derivation using PBKDF2
- Secure SharedPreferences for sensitive data
- No personal data collection beyond chat history

## 📞 Support

### Error Reporting
Include in bug report:
- Logcat output: `adb logcat | grep PersianAI`
- Android version
- Device model
- Steps to reproduce
- Screenshot/video

### Common Issues
See Troubleshooting section above

## 📄 License

This project is open source. See LICENSE file for details.

## 🤝 Contributing

Contributions welcome! Please:
1. Follow code style (Kotlin conventions)
2. Add tests for new features
3. Document changes
4. Submit PR with description

## 📅 Version History

- **v1.0.0** - Initial release with hybrid AI
  - ✅ Online models (Liara/OpenRouter)
  - ✅ Offline models (TinyLlama)
  - ✅ Voice support (Haaniye)
  - ✅ Action execution
  - ✅ Centralized routing

---

**Built with ❤️ for Persian users**

🚀 **Ready for Production Deployment**
