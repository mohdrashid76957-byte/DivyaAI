package com.divya.ai

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import kotlin.math.sin

/** Animated neon live wallpaper (chalte hue particles). Ye simple animation hai, AI nahi. */
class DivyaWallpaper : WallpaperService() {
    override fun onCreateEngine(): Engine = E()

    private inner class E : Engine() {
        private val h = Handler(Looper.getMainLooper())
        private var vis = false
        private var t = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val pts = Array(40) { floatArrayOf(Math.random().toFloat(), Math.random().toFloat(), (Math.random() * 0.002 + 0.0005).toFloat()) }
        private val tick = object : Runnable { override fun run() { draw(); if (vis) h.postDelayed(this, 33) } }

        override fun onVisibilityChanged(visible: Boolean) { vis = visible; h.removeCallbacks(tick); if (visible) h.post(tick) }
        override fun onDestroy() { vis = false; h.removeCallbacks(tick); super.onDestroy() }

        private fun draw() {
            val sh = surfaceHolder
            val c: Canvas = (try { sh.lockCanvas() } catch (_: Exception) { null }) ?: return
            try {
                val w = c.width.toFloat(); val hh = c.height.toFloat()
                paint.shader = LinearGradient(0f, 0f, 0f, hh, intArrayOf(0xFF05051A.toInt(), 0xFF1B0B4A.toInt(), 0xFF04204A.toInt()), null, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w, hh, paint)
                paint.shader = null
                t += 0.02f
                for (p in pts) {
                    p[1] -= p[2]
                    if (p[1] < 0f) { p[1] = 1f; p[0] = Math.random().toFloat() }
                    paint.color = if (p[0] > 0.5f) 0xAA00E5FF.toInt() else 0xAA8E5BFF.toInt()
                    c.drawCircle(p[0] * w + sin((t + p[1] * 6f).toDouble()).toFloat() * 12f, p[1] * hh, 3f + p[2] * 2000f, paint)
                }
            } finally { try { sh.unlockCanvasAndPost(c) } catch (_: Exception) {} }
        }
    }
}
