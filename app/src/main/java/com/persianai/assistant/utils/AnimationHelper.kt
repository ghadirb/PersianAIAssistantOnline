package com.persianai.assistant.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.persianai.assistant.R

/**
 * کلاس کمکی برای انیمیشن‌های UI
 */
object AnimationHelper {
    
    /**
     * انیمیشن Fade In
     */
    fun fadeIn(view: View, duration: Long = 300) {
        view.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(duration)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        }
    }
    
    /**
     * انیمیشن Fade Out
     */
    fun fadeOut(view: View, duration: Long = 300, hideAfter: Boolean = true) {
        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(FastOutSlowInInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (hideAfter) {
                        view.visibility = View.GONE
                    }
                }
            })
            .start()
    }
    
    /**
     * انیمیشن Scale
     */
    fun scaleAnimation(view: View, fromScale: Float = 0.8f, toScale: Float = 1f, duration: Long = 200) {
        view.apply {
            scaleX = fromScale
            scaleY = fromScale
            animate()
                .scaleX(toScale)
                .scaleY(toScale)
                .setDuration(duration)
                .setInterpolator(OvershootInterpolator())
                .start()
        }
    }
    
    /**
     * انیمیشن Slide In از راست
     */
    fun slideInFromRight(view: View, duration: Long = 400) {
        view.apply {
            translationX = width.toFloat()
            visibility = View.VISIBLE
            animate()
                .translationX(0f)
                .setDuration(duration)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        }
    }
    
    /**
     * انیمیشن Slide In از چپ
     */
    fun slideInFromLeft(view: View, duration: Long = 400) {
        view.apply {
            translationX = -width.toFloat()
            visibility = View.VISIBLE
            animate()
                .translationX(0f)
                .setDuration(duration)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        }
    }
    
    /**
     * انیمیشن Slide In از پایین
     */
    fun slideInFromBottom(view: View, duration: Long = 400) {
        view.apply {
            translationY = height.toFloat()
            visibility = View.VISIBLE
            animate()
                .translationY(0f)
                .setDuration(duration)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        }
    }
    
    /**
     * انیمیشن Rotate
     */
    fun rotate(view: View, fromDegrees: Float = 0f, toDegrees: Float = 360f, duration: Long = 500) {
        ObjectAnimator.ofFloat(view, "rotation", fromDegrees, toDegrees).apply {
            this.duration = duration
            interpolator = FastOutSlowInInterpolator()
            start()
        }
    }
    
    /**
     * انیمیشن Pulse (ضربان)
     */
    fun pulseAnimation(view: View, scaleFactor: Float = 1.1f, duration: Long = 1000) {
        val scaleAnimator = ObjectAnimator.ofFloat(view, "scaleX", 1f, scaleFactor, 1f)
        val scaleYAnimator = ObjectAnimator.ofFloat(view, "scaleY", 1f, scaleFactor, 1f)
        
        scaleAnimator.apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }
        
        scaleYAnimator.apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }
    }
    
    /**
     * انیمیشن Shake (تکان دادن)
     */
    fun shake(view: View, duration: Long = 500) {
        val animator = ObjectAnimator.ofFloat(view, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f)
        animator.duration = duration
        animator.start()
    }
    
    /**
     * انیمیشن Bounce
     */
    fun bounce(view: View, duration: Long = 500) {
        val animator = ObjectAnimator.ofFloat(view, "translationY", 0f, -30f, 0f, -15f, 0f, -5f, 0f)
        animator.duration = duration
        animator.interpolator = OvershootInterpolator()
        animator.start()
    }
    
    /**
     * انیمیشن کلیک (کوچک و بزرگ شدن سریع)
     */
    fun clickAnimation(view: View) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .start()
            }
            .start()
    }
    
    /**
     * اعمال انیمیشن از فایل XML
     */
    fun applyAnimation(context: Context, view: View, animResId: Int) {
        val animation = AnimationUtils.loadAnimation(context, animResId)
        view.startAnimation(animation)
    }
    
    /**
     * انیمیشن نمایش موج (Ripple)
     */
    fun rippleEffect(view: View) {
        // این انیمیشن معمولاً با استفاده از RippleDrawable در XML انجام می‌شود
        // اینجا فقط یک انیمیشن ساده scale
        view.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .alpha(0.8f)
            .setDuration(150)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }
    
    /**
     * انیمیشن Card Flip
     */
    fun cardFlip(view: View, duration: Long = 400) {
        view.animate()
            .rotationY(90f)
            .setDuration(duration / 2)
            .withEndAction {
                // تغییر محتوا در اینجا
                view.rotationY = -90f
                view.animate()
                    .rotationY(0f)
                    .setDuration(duration / 2)
                    .start()
            }
            .start()
    }
    
    /**
     * انیمیشن گروهی برای لیست آیتم‌ها
     */
    fun animateListItems(views: List<View>, delayBetween: Long = 50) {
        views.forEachIndexed { index, view ->
            view.apply {
                alpha = 0f
                translationY = 50f
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(index * delayBetween)
                    .setDuration(300)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .start()
            }
        }
    }
}
