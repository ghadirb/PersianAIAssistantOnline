package com.persianai.assistant.models

/**
 * مدل برنامه متصل
 */
data class ConnectedApp(
    val id: String,
    var name: String,           // نام فارسی برنامه
    var packageName: String,    // Package name اندروید
    var keywords: List<String>, // کلمات کلیدی برای تشخیص
    var isEnabled: Boolean = true
) {
    companion object {
        /**
         * لیست پیش‌فرض برنامه‌های متصل
         */
        fun getDefaultApps(): List<ConnectedApp> {
            return listOf(
                ConnectedApp(
                    id = "telegram",
                    name = "تلگرام",
                    packageName = "org.telegram.messenger",
                    keywords = listOf("تلگرام", "telegram")
                ),
                ConnectedApp(
                    id = "whatsapp",
                    name = "واتساپ",
                    packageName = "com.whatsapp",
                    keywords = listOf("واتساپ", "whatsapp")
                ),
                ConnectedApp(
                    id = "rubika",
                    name = "روبیکا",
                    packageName = "ir.resaneh1.iptv",
                    keywords = listOf("روبیکا", "rubika")
                ),
                ConnectedApp(
                    id = "eitaa",
                    name = "ایتا",
                    packageName = "ir.eitaa.messenger",
                    keywords = listOf("ایتا", "eitaa")
                ),
                ConnectedApp(
                    id = "neshan",
                    name = "نشان",
                    packageName = "com.neshantadbir.neshan",
                    keywords = listOf("نشان", "neshan")
                ),
                ConnectedApp(
                    id = "instagram",
                    name = "اینستاگرام",
                    packageName = "com.instagram.android",
                    keywords = listOf("اینستاگرام", "instagram", "اینستا")
                ),
                ConnectedApp(
                    id = "twitter",
                    name = "توییتر / X",
                    packageName = "com.twitter.android",
                    keywords = listOf("توییتر", "twitter", "x")
                ),
                ConnectedApp(
                    id = "maps",
                    name = "Google Maps",
                    packageName = "com.google.android.apps.maps",
                    keywords = listOf("مپ", "maps", "نقشه")
                ),
                ConnectedApp(
                    id = "gmail",
                    name = "Gmail",
                    packageName = "com.google.android.gm",
                    keywords = listOf("ایمیل", "gmail", "جیمیل")
                ),
                ConnectedApp(
                    id = "chrome",
                    name = "Chrome",
                    packageName = "com.android.chrome",
                    keywords = listOf("کروم", "chrome", "مرورگر")
                )
            )
        }
    }
}
