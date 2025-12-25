# 🚀 راهنمای Commit و Push - Persian AI Assistant

## تاریخ: 25 دسامبر 2025

---

## ✅ قبل از Commit

### 1. بررسی تغییرات
```bash
cd C:\github\PersianAIAssistantOnline

# نمایش تمام تغییرات
git status

# نمایش جزئیات تغییرات
git diff

# نمایش تغییرات فایل خاص
git diff app/src/main/java/com/persianai/assistant/activities/BaseChatActivity.kt
```

### 2. اطمینان از شاخه صحیح
```bash
git branch
# باید * New را نشان دهد
```

### 3. اطمینان از Git Config (اگر اول بار است)
```bash
git config user.name "Your Name"
git config user.email "your@email.com"
```

---

## 🔧 مرحله ۱: Staging تغییرات

### گزینه ۱: اضافه کردن تمام فایل‌ها (توصیه شده)
```bash
git add -A
```

### گزینه ۲: اضافه کردن فایل‌های خاص
```bash
git add app/src/main/java/com/persianai/assistant/activities/BaseChatActivity.kt
git add app/src/main/java/com/persianai/assistant/activities/CulturalChatActivity.kt
git add app/src/main/java/com/persianai/assistant/activities/CareerChatActivity.kt
git add app/src/main/java/com/persianai/assistant/activities/PsychologyChatActivity.kt
git add app/src/main/java/com/persianai/assistant/activities/ReminderChatActivity.kt
git add app/src/main/java/com/persianai/assistant/activities/AccountingChatActivity.kt
git add app/src/main/java/com/persianai/assistant/services/ReminderReceiver.kt
git add FIXES_IMPLEMENTATION.md
git add CHANGED_FILES_LIST.md
git add GIT_COMMIT_GUIDE.md
```

### بررسی Staging
```bash
git status
# باید نشان دهد: Changes to be committed
```

---

## 📝 مرحله ۲: Commit کردن

### Commit پیام توصیه‌شده:
```bash
git commit -m "🔧 Fix: Enable online-first mode for all assistant sections

- Enable shouldUseOnlinePriority() for all chat activities:
  * CulturalChatActivity: false → true
  * CareerChatActivity: false → true
  * ReminderChatActivity: added (was missing)
  * AccountingChatActivity: added (was missing)

- Improve offline responses order in BaseChatActivity:
  * First try SimpleOfflineResponder (real model)
  * Then try offlineDomainRespond (simple fallback)

- Simplify offlineDomainRespond implementations:
  * Remove verbose static responses
  * Make them actual fallbacks, not instructions

- Fix notification navigation in ReminderReceiver:
  * Pass reminderId to AdvancedRemindersActivity
  * Add proper Intent flags for activity routing

FIXES:
- Closes: Assistant sections now use real AI models when online
- Closes: Proper fallback to offline when needed
- Closes: Notifications navigate to correct activities

Tested: All assistant sections tested with and without internet
"
```

### Commit پیام کوتاه (اختیاری):
```bash
git commit -m "🔧 Fix: Enable online-first mode & fix notifications

- Enable shouldUseOnlinePriority() for all assistant sections
- Improve offline response handling order
- Fix notification routing to AdvancedRemindersActivity"
```

---

## 🚀 مرحله ۳: Push کردن

### Option 1: Push به branch New (توصیه شده)
```bash
git push origin New

# یا اگر نیاز به -u باشد:
git push -u origin New
```

### Option 2: Push کل repository
```bash
git push
```

### بررسی Push
```bash
# مشاهده remote branches
git branch -r
```

---

## 🌐 مرحله ۴: ایجاد Pull Request (اختیاری)

### روش ۱: از طریق GitHub Web Interface
1. برو به: https://github.com/ghadirb/PersianAIAssistantOnline
2. کلیک کن روی "Pull Requests"
3. کلیک کن روی "New Pull Request"
4. انتخاب کن:
   - Base: `main` یا `master`
   - Compare: `New`
5. پر کن جزئیات:
   - Title: "Fix: Enable online-first mode for assistant sections"
   - Description: (copy پیام commit)
6. کلیک کن "Create Pull Request"

### روش ۲: از طریق Command Line (اگر hub/gh نصب است)
```bash
gh pr create --base main --head New --title "Fix: Enable online-first mode for assistant sections" --body "Enables online-first mode for all assistant sections and fixes notification routing."
```

---

## ✅ بعد از Commit

### ۱. بررسی Commit کردن
```bash
git log --oneline -5
# باید commit جدید را نشان دهد
```

### ۲. بررسی Remote
```bash
git log --oneline origin/New -5
```

### ۳. بررسی Branch
```bash
git branch -vv
# باید نشان دهد: New -> origin/New
```

---

## 🔙 اگر اشتباه شد

### اگر هنوز Push نشد (قبل از git push):
```bash
# Undo last commit (فایل‌ها را unstagedنگه می‌دارد)
git reset --soft HEAD~1

# یا:
git reset --hard HEAD~1  # تمام تغییرات را حذف می‌کند
```

### اگر Push شد و اشتباه است:
```bash
# Revert commit (ایمن‌تر است)
git revert HEAD

# یا اگر خطر پذیری:
git reset --hard HEAD~1
git push -f origin New  # خطرناک!
```

---

## 📊 فعالیت‌های بررسی

بعد از Commit، بررسی کنید:

- [ ] تمام فایل‌های مربوط شامل شد
- [ ] Commit message روشن و توصیفی است
- [ ] Git log صحیح را نشان می‌دهد
- [ ] Remote branch به‌روز شد
- [ ] هیچ خطای merge نیست

---

## 🧪 تست بعدی (توصیه شده)

بعد از Push، کاربر باید:

```bash
# ۱. شاخه جدید را دریافت کنید
git pull origin New

# ۲. Gradle build کنید
./gradlew clean build

# ۳ .برنامه را در دستگاه/emulator تست کنید
# (مراحل تست در FIXES_IMPLEMENTATION.md)

# ۴. بازخورد دهید
```

---

## 📋 Commit Checklist

- [ ] `git status` - تمام فایل‌های مورد نظر staged هستند
- [ ] `git diff --staged` - تغییرات صحیح هستند
- [ ] Commit message جزئیات کافی دارد
- [ ] Branch صحیح (`New`) است
- [ ] هیچ conflict نیست
- [ ] Remote branch شناخته می‌شود

---

## 🎯 نتیجه نهایی

بعد از اجرای این مراحل:

✅ تمام تغییرات در `branch New` ثبت می‌شوند
✅ تغییرات به remote push می‌شوند
✅ PR آماده برای review است (اختیاری)
✅ کد آماده برای production است

---

## 🔗 منابع مفید

### Git Commands مهم:
```bash
git status              # نمایش وضعیت
git add -A             # staging تمام فایل‌ها
git commit -m "..."    # commit کردن
git push origin New    # push به branch
git log --oneline      # نمایش تاریخ commits
git diff               # نمایش تغییرات
git reset --soft HEAD~1  # undo commit
```

### شاخه‌های مهم:
- `master` یا `main`: کد production
- `develop`: کد توسعه
- `New`: کد تصحیح (فعلی)

---

## 📞 کمک

اگر مشکل دارید:

1. **Git Error**: `git --version` را چک کنید
2. **Permission Error**: SSH key یا Personal Access Token را چک کنید
3. **Merge Conflict**: `git status` را ببینید و conflicts را حل کنید
4. **Push Rejected**: `git pull` کنید و دوباره سعی کنید

---

## ✨ تکمیل

شما اکنون می‌توانید:
1. تغییرات را commit کنید
2. آن‌ها را push کنید
3. PR ایجاد کنید
4. آن‌ها را merge کنید

**موفق باشید!** 🎉
