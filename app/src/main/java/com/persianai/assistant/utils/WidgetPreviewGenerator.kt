package com.persianai.assistant.utils

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import androidx.core.content.ContextCompat
import com.persianai.assistant.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Generator برای ایجاد تصاویر پیش‌نمایش ویجت‌ها
 */
class WidgetPreviewGenerator(private val context: Context) {
    
    companion object {
        const val PREVIEW_SMALL_WIDTH = 220
        const val PREVIEW_SMALL_HEIGHT = 80
        const val PREVIEW_MEDIUM_WIDTH = 320
        const val PREVIEW_MEDIUM_HEIGHT = 160
        const val PREVIEW_LARGE_WIDTH = 440
        const val PREVIEW_LARGE_HEIGHT = 220
    }
    
    /**
     * ایجاد پیش‌نمایش برای ویجت کوچک
     */
    fun generateSmallWidgetPreview(): Bitmap {
        val bitmap = Bitmap.createBitmap(PREVIEW_SMALL_WIDTH, PREVIEW_SMALL_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // پس‌زمینه
        drawBackground(canvas, PREVIEW_SMALL_WIDTH, PREVIEW_SMALL_HEIGHT, 0x80006064.toInt(), 0x80004D40.toInt())
        
        // ساعت
        val clockPaint = Paint().apply {
            color = Color.WHITE
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        canvas.drawText("14:30", 20f, 45f, clockPaint)
        
        // تاریخ
        val datePaint = Paint().apply {
            color = Color.parseColor("#F0F0F0")
            textSize = 16f
            isAntiAlias = true
        }
        canvas.drawText("15 آذر", 20f, 65f, datePaint)
        
        // آب و هوا
        val weatherPaint = Paint().apply {
            color = Color.parseColor("#F0F0F0")
            textSize = 14f
            isAntiAlias = true
        }
        canvas.drawText("☀️ 23°", 140f, 45f, weatherPaint)
        
        // دکمه refresh
        drawRefreshButton(canvas, PREVIEW_SMALL_WIDTH - 35f, 15f, 20f)
        
        return bitmap
    }
    
    /**
     * ایجاد پیش‌نمایش برای ویجت متوسط
     */
    fun generateMediumWidgetPreview(): Bitmap {
        val bitmap = Bitmap.createBitmap(PREVIEW_MEDIUM_WIDTH, PREVIEW_MEDIUM_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // پس‌زمینه
        drawBackground(canvas, PREVIEW_MEDIUM_WIDTH, PREVIEW_MEDIUM_HEIGHT, 0x99006064.toInt(), 0x99004D40.toInt())
        
        // ساعت بزرگ
        val clockPaint = Paint().apply {
            color = Color.WHITE
            textSize = 56f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 2f, 2f, Color.parseColor("#80000000"))
        }
        canvas.drawText("14:30", PREVIEW_MEDIUM_WIDTH / 2f, 70f, clockPaint)
        
        // تاریخ فارسی
        val datePaint = Paint().apply {
            color = Color.parseColor("#F0F0F0")
            textSize = 20f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("دوشنبه، 15 آذر 1402", PREVIEW_MEDIUM_WIDTH / 2f, 100f, datePaint)
        
        // آب و هوا
        val weatherPaint = Paint().apply {
            color = Color.parseColor("#F0F0F0")
            textSize = 18f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("🌤️ تهران: 23°", PREVIEW_MEDIUM_WIDTH / 2f, 130f, weatherPaint)
        
        // دکمه refresh
        drawRefreshButton(canvas, PREVIEW_MEDIUM_WIDTH - 40f, 20f, 24f)
        
        return bitmap
    }
    
    /**
     * ایجاد پیش‌نمایش برای ویجت بزرگ
     */
    fun generateLargeWidgetPreview(): Bitmap {
        val bitmap = Bitmap.createBitmap(PREVIEW_LARGE_WIDTH, PREVIEW_LARGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // پس‌زمینه
        drawBackground(canvas, PREVIEW_LARGE_WIDTH, PREVIEW_LARGE_HEIGHT, 0xB0006064.toInt(), 0xB0004D40.toInt())
        
        // Header
        val headerPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        canvas.drawText("دستیار هوشمند", 30f, 35f, headerPaint)
        
        // دکمه refresh
        drawRefreshButton(canvas, PREVIEW_LARGE_WIDTH - 45f, 20f, 28f)
        
        // خط جدا کننده
        val linePaint = Paint().apply {
            color = Color.parseColor("#40FFFFFF")
            strokeWidth = 1f
        }
        canvas.drawLine(30f, 50f, PREVIEW_LARGE_WIDTH - 30f, 50f, linePaint)
        
        // ساعت بزرگ
        val clockPaint = Paint().apply {
            color = Color.WHITE
            textSize = 52f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            setShadowLayer(4f, 2f, 2f, Color.parseColor("#80000000"))
        }
        canvas.drawText("14:30", 40f, 100f, clockPaint)
        
        // ثانیه
        val secondsPaint = Paint().apply {
            color = Color.parseColor("#B0B0B0")
            textSize = 18f
            isAntiAlias = true
        }
        canvas.drawText("45", 155f, 100f, secondsPaint)
        
        // خط عمودی جدا کننده
        canvas.drawLine(200f, 60f, 200f, 150f, linePaint)
        
        // تاریخ فارسی
        val persianDatePaint = Paint().apply {
            color = Color.WHITE
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        canvas.drawText("دوشنبه، 15 آذر 1402", 220f, 85f, persianDatePaint)
        
        // تاریخ میلادی
        val gregorianPaint = Paint().apply {
            color = Color.parseColor("#C0C0C0")
            textSize = 15f
            isAntiAlias = true
        }
        canvas.drawText("6 December 2023", 220f, 105f, gregorianPaint)
        
        // آب و هوا
        val weatherPaint = Paint().apply {
            color = Color.parseColor("#F0F0F0")
            textSize = 18f
            isAntiAlias = true
        }
        canvas.drawText("🌤️ 23° تهران", 220f, 130f, weatherPaint)
        
        val weatherDescPaint = Paint().apply {
            color = Color.parseColor("#B0B0B0")
            textSize = 14f
            isAntiAlias = true
        }
        canvas.drawText("آفتابی", 330f, 130f, weatherDescPaint)
        
        // Quick Actions
        drawQuickActions(canvas, 30f, 170f, PREVIEW_LARGE_WIDTH - 60f, 35f)
        
        return bitmap
    }
    
    /**
     * رسم پس‌زمینه گرادیانت
     */
    private fun drawBackground(canvas: Canvas, width: Int, height: Int, startColor: Int, endColor: Int) {
        val gradient = LinearGradient(
            0f, 0f,
            width.toFloat(), height.toFloat(),
            startColor, endColor,
            Shader.TileMode.CLAMP
        )
        
        val paint = Paint().apply {
            shader = gradient
            isAntiAlias = true
        }
        
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, 24f, 24f, paint)
        
        // Border
        val borderPaint = Paint().apply {
            color = Color.parseColor("#40FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, 24f, 24f, borderPaint)
    }
    
    /**
     * رسم دکمه refresh
     */
    private fun drawRefreshButton(canvas: Canvas, x: Float, y: Float, size: Float) {
        val paint = Paint().apply {
            color = Color.parseColor("#CCFFFFFF")
            isAntiAlias = true
        }
        
        // رسم دایره پس‌زمینه
        val bgPaint = Paint().apply {
            color = Color.parseColor("#20FFFFFF")
            isAntiAlias = true
        }
        canvas.drawCircle(x + size/2, y + size/2, size/2 + 4, bgPaint)
        
        // رسم آیکون refresh (ساده شده)
        val iconPaint = Paint().apply {
            color = Color.WHITE
            strokeWidth = 2f
            style = Paint.Style.STROKE
            isAntiAlias = true
            alpha = 200
        }
        
        val path = Path()
        val centerX = x + size/2
        val centerY = y + size/2
        val radius = size/3
        
        val rect = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        path.addArc(rect, 30f, 300f)
        
        // رسم فلش
        val arrowPath = Path()
        arrowPath.moveTo(centerX + radius * 0.7f, centerY - radius * 0.5f)
        arrowPath.lineTo(centerX + radius, centerY - radius * 0.2f)
        arrowPath.lineTo(centerX + radius * 0.5f, centerY - radius * 0.2f)
        
        canvas.drawPath(path, iconPaint)
        canvas.drawPath(arrowPath, iconPaint)
    }
    
    /**
     * رسم Quick Actions برای ویجت بزرگ
     */
    private fun drawQuickActions(canvas: Canvas, x: Float, y: Float, width: Float, height: Float) {
        val buttonWidth = width / 3 - 10
        val buttonPaint = Paint().apply {
            color = Color.parseColor("#30FFFFFF")
            isAntiAlias = true
        }
        
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 14f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        
        // دکمه چت
        var rect = RectF(x, y, x + buttonWidth, y + height)
        canvas.drawRoundRect(rect, 12f, 12f, buttonPaint)
        canvas.drawText("💬 چت", x + buttonWidth/2, y + height/2 + 5, textPaint)
        
        // دکمه تقویم
        rect = RectF(x + buttonWidth + 10, y, x + 2*buttonWidth + 10, y + height)
        canvas.drawRoundRect(rect, 12f, 12f, buttonPaint)
        canvas.drawText("📅 تقویم", x + buttonWidth + 10 + buttonWidth/2, y + height/2 + 5, textPaint)
        
        // دکمه آب و هوا
        rect = RectF(x + 2*(buttonWidth + 10), y, x + width, y + height)
        canvas.drawRoundRect(rect, 12f, 12f, buttonPaint)
        canvas.drawText("🌡️ آب‌وهوا", x + 2*(buttonWidth + 10) + buttonWidth/2, y + height/2 + 5, textPaint)
    }
    
    /**
     * ذخیره bitmap به فایل
     */
    fun saveBitmapToFile(bitmap: Bitmap, fileName: String): File? {
        return try {
            val file = File(context.cacheDir, fileName)
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * ایجاد همه preview ها
     */
    fun generateAllPreviews() {
        val smallPreview = generateSmallWidgetPreview()
        val mediumPreview = generateMediumWidgetPreview()
        val largePreview = generateLargeWidgetPreview()
        
        saveBitmapToFile(smallPreview, "widget_preview_small.png")
        saveBitmapToFile(mediumPreview, "widget_preview_medium.png")
        saveBitmapToFile(largePreview, "widget_preview_large.png")
        
        // آزاد کردن حافظه
        smallPreview.recycle()
        mediumPreview.recycle()
        largePreview.recycle()
    }
}
