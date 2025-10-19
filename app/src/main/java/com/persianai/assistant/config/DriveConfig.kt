package com.persianai.assistant.config

/**
 * پیکربندی Google Drive برای همگام‌سازی داده‌های یادگیری مسیر
 * 
 * راهنمای تنظیم:
 * 1. یک پوشه در Google Drive بسازید
 * 2. پوشه را به صورت عمومی (Anyone with the link can edit) تنظیم کنید
 * 3. ID پوشه را از لینک استخراج کرده و در اینجا قرار دهید
 * 
 * مثال لینک: https://drive.google.com/drive/folders/1ABC123XYZ_FOLDER_ID
 * ID پوشه: 1ABC123XYZ_FOLDER_ID
 */
object DriveConfig {
    
    /**
     * ID پوشه عمومی Google Drive
     * لینک کامل: https://drive.google.com/drive/folders/1bp1Ay9kmK_bjWq_PznRfkPvhhjdhSye1?usp=drive_link
     */
    const val PUBLIC_FOLDER_ID = "1bp1Ay9kmK_bjWq_PznRfkPvhhjdhSye1"
    
    /**
     * لینک کامل پوشه عمومی
     */
    val PUBLIC_FOLDER_LINK: String
        get() = "https://drive.google.com/drive/folders/$PUBLIC_FOLDER_ID"
    
    /**
     * فاصله زمانی همگام‌سازی (میلی‌ثانیه)
     * پیش‌فرض: 30 دقیقه
     */
    const val SYNC_INTERVAL = 30 * 60 * 1000L
    
    /**
     * نام فایل JSON داده‌های یادگیری مسیر
     */
    const val ROUTES_FILE_NAME = "persian_ai_learned_routes.json"
    
    /**
     * حداکثر تعداد مسیرهای ذخیره شده
     */
    const val MAX_ROUTES = 1000
    
    /**
     * فعال/غیرفعال کردن همگام‌سازی خودکار
     */
    var autoSyncEnabled = true
    
    /**
     * بررسی اینکه آیا پیکربندی به درستی انجام شده است
     */
    fun isConfigured(): Boolean {
        return PUBLIC_FOLDER_ID != "REPLACE_WITH_YOUR_FOLDER_ID" && PUBLIC_FOLDER_ID.isNotEmpty()
    }
    
    /**
     * دریافت پیام راهنما برای تنظیم
     */
    fun getSetupInstructions(): String {
        return """
            برای فعال‌سازی همگام‌سازی با Google Drive:
            
            1. به Google Drive بروید و یک پوشه جدید بسازید
            2. روی پوشه راست کلیک کرده و 'Share' را انتخاب کنید
            3. 'Anyone with the link' را انتخاب کرده و دسترسی را به 'Editor' تغییر دهید
            4. لینک پوشه را کپی کنید
            5. ID پوشه را از لینک استخراج کرده و در DriveConfig.kt قرار دهید
            
            مثال لینک: https://drive.google.com/drive/folders/1ABC123XYZ_FOLDER_ID
            ID مورد نیاز: 1ABC123XYZ_FOLDER_ID
        """.trimIndent()
    }
}
