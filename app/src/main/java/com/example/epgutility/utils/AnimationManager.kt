package com.example.epgutility.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.widget.TextView
import kotlin.random.Random

object AnimationManager {

    fun startGlowFlicker(textView: TextView) {
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Random.nextLong(300, 700)

            addUpdateListener {
                val radius = Random.nextInt(8, 75).toFloat()
                val alpha = Random.nextInt(50, 192)
                val glowColor = Color.argb(alpha, 180, 180, 255)
                textView.setShadowLayer(radius, 0f, 0f, glowColor)
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    textView.postDelayed({
                        startGlowFlicker(textView)
                    }, Random.nextLong(50, 200))
                }
            })
        }
        animator.start()
    }

    fun startTextFlicker(textView: TextView, baseColorHex: String = "#2196F3") {
        val baseColor = Color.parseColor(baseColorHex)
        val flickerAnimator = ValueAnimator.ofFloat(1f, 0.8f).apply {
            duration = Random.nextLong(200, 600)

            addUpdateListener {
                val fraction = it.animatedValue as Float
                val flickerColor = adjustAlpha(baseColor, fraction)
                textView.setTextColor(flickerColor)
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    textView.postDelayed({
                        startTextFlicker(textView, baseColorHex)
                    }, Random.nextLong(50, 300))
                }
            })
        }
        flickerAnimator.start()
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = Color.alpha(color)
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)

        return Color.argb(
            alpha,
            (red * factor).toInt().coerceAtLeast(0),
            (green * factor).toInt().coerceAtLeast(0),
            (blue * factor).toInt().coerceAtLeast(0)
        )
    }
}
