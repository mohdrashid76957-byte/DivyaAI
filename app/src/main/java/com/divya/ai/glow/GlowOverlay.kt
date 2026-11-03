package com.divya.ai.glow

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator

/**
 * SYSTEM-WIDE glow: dusre apps ke upar bhi (reel wala effect).
 * Touch pass-through hai, to neeche ka app normal chalta hai.
 *
 *   GlowOverlay.show(this)                       // DivyaService.onServiceConnected me
 *   GlowOverlay.setState(GlowState.LISTENING)    // mic on hone par
 *   GlowOverlay.hide()
 *
 * DivyaService (AccessibilityService) se call karo to permission ki zarurat nahi
 * (TYPE_ACCESSIBILITY_OVERLAY use hota hai). Warna SYSTEM_ALERT_WINDOW chahiye.
 */
object GlowOverlay {
    private var view: GlowView? = null
    private var wm: WindowManager? = null

    fun show(context: Context, state: GlowState = GlowState.IDLE) {
        if (view != null) { setState(state); return }
        val isA11y = context is android.accessibilityservice.AccessibilityService
        if (!isA11y && !Settings.canDrawOverlays(context)) return
        val type = if (isA11y && Build.VERSION.SDK_INT >= 22)
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // Asli poora screen size (status bar + navigation bar + cutout sab milakar), MATCH_PARENT kabhi-kabhi bars chhod deta hai
        val sw: Int; val sh: Int
        if (Build.VERSION.SDK_INT >= 30) { val b = manager.maximumWindowMetrics.bounds; sw = b.width(); sh = b.height() }
        else { val dm = android.util.DisplayMetrics(); @Suppress("DEPRECATION") manager.defaultDisplay.getRealMetrics(dm); sw = dm.widthPixels; sh = dm.heightPixels }
        val lp = WindowManager.LayoutParams(
            sw, sh,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        lp.x = 0; lp.y = 0
        // Notch / punch-hole ke neeche bhi border dikhe (top edge kata hua na ho)
        if (Build.VERSION.SDK_INT >= 30) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        else if (Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        val v = GlowView(context).also { it.state = state }
        manager.addView(v, lp)
        wm = manager; view = v
    }

    fun setState(state: GlowState) { view?.state = state }

    fun hide() {
        view?.let { runCatching { wm?.removeView(it) } }
        view = null; wm = null
    }
}

private class GlowView(ctx: Context) : View(ctx) {
    var state: GlowState = GlowState.IDLE
        set(v) {
            field = v; anim.duration = v.periodMs.toLong()
            hueAnim.duration = when (v) { GlowState.IDLE -> 6000L; GlowState.LISTENING -> 3000L; GlowState.THINKING -> 1500L; GlowState.SPEAKING -> 2500L }
            invalidate()
        }

    private var phase = 1f
    private var angle = 0f
    private val dp = ctx.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val matrix = Matrix()
    private var shader: SweepGradient? = null
    private var lw = 0
    private var lh = 0
    private val rainbow = intArrayOf(
        Color.parseColor("#FF1744"), Color.parseColor("#FFEA00"), Color.parseColor("#00E676"),
        Color.parseColor("#00E5FF"), Color.parseColor("#2979FF"), Color.parseColor("#D500F9"), Color.parseColor("#FF1744"))
    private val anim = ValueAnimator.ofFloat(0.6f, 1f).apply {
        duration = state.periodMs.toLong()
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }
    private val hueAnim = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 6000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { angle = it.animatedValue as Float }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start(); hueAnim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); hueAnim.cancel(); super.onDetachedFromWindow() }

    private fun cornerRadius(): Float {
        if (Build.VERSION.SDK_INT >= 31) {
            rootWindowInsets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)
                ?.let { return it.radius.toFloat() }
        }
        return 40f * dp
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        if (shader == null || lw != width || lh != height) {
            shader = SweepGradient(cx, cy, rainbow, null); lw = width; lh = height
        }
        matrix.setRotate(angle, cx, cy)
        shader?.setLocalMatrix(matrix)
        paint.shader = shader
        val r = cornerRadius()
        fun layer(width: Float, alpha: Float) {
            paint.strokeWidth = width
            paint.alpha = (255 * (alpha * phase).coerceIn(0f, 1f)).toInt()
            val inset = width / 2f
            rect.set(inset, inset, this.width - inset, this.height - inset)
            c.drawRoundRect(rect, r, r, paint)
        }
        layer(34f * dp, 0.10f)
        layer(22f * dp, 0.20f)
        layer(12f * dp, 0.40f)
        layer(5f * dp, 0.80f)
        layer(2f * dp, 1.00f)
    }
}
