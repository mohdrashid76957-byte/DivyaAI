package com.divya.ai.glow

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.divya.ai.AvatarBus
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * v2.22.8: Jarvis jaisa HUD - Divya se baat karte waqt screen ke beech mein ghoomte neon gole (rings) + "DIVYA".
 * GlowOverlay.show/setState/hide ke saath apne aap chalta hai (Settings > "Jarvis ring").
 * - Divya bolti hai to rings uski awaaz ke saath dhadakte hain (AvatarBus.level).
 * - Divya koi kaam karti hai (tool chalte waqt, state THINKING) to ring chhota hoke upar chala jata hai aur andhera hat jata hai, taaki kaam dikhe.
 * - Andhere wale mode mein ring par tap = Divya band. "Andhera" band ho to poora pass-through (touch neeche ke app ko jaata hai).
 * Permission: Accessibility ON (DivyaService) ya "Display over other apps".
 */
object HudOverlay {
    @Volatile var onTap: (() -> Unit)? = null
    private var view: HudView? = null
    private var wm: WindowManager? = null
    private var lp: WindowManager.LayoutParams? = null
    private var dimFull = true

    fun show(context: Context, state: GlowState = GlowState.IDLE) {
        if (view != null) { setState(state); return }
        val isA11y = context is AccessibilityService
        if (!isA11y && !Settings.canDrawOverlays(context)) return
        val base = if (isA11y) context else context.applicationContext
        val type = if (isA11y) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val manager = base.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val sw: Int; val sh: Int
        if (Build.VERSION.SDK_INT >= 30) { val b = manager.maximumWindowMetrics.bounds; sw = b.width(); sh = b.height() }
        else { val dm = android.util.DisplayMetrics(); @Suppress("DEPRECATION") manager.defaultDisplay.getRealMetrics(dm); sw = dm.widthPixels; sh = dm.heightPixels }
        dimFull = base.getSharedPreferences("divya", 0).getBoolean("hudDim", true)
        val l = WindowManager.LayoutParams(
            sw, sh, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        l.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        l.x = 0; l.y = 0
        if (Build.VERSION.SDK_INT >= 30) l.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        else if (Build.VERSION.SDK_INT >= 28) l.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        val v = HudView(base).also { it.state = state; it.dimFull = dimFull }
        try { manager.addView(v, l) } catch (_: Exception) { return }
        wm = manager; lp = l; view = v
        touch(state)
    }

    fun setState(state: GlowState) { view?.state = state; touch(state) }

    /** Andhere wale mode mein touchable (tap = band), par kaam chalte waqt (THINKING) pass-through taaki Divya ke gesture/tap block na hon. */
    private fun touch(state: GlowState) {
        val l = lp ?: return; val v = view ?: return
        val touchable = dimFull && state != GlowState.THINKING
        val f = if (touchable) l.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv() else l.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (f != l.flags) { l.flags = f; try { wm?.updateViewLayout(v, l) } catch (_: Exception) {} }
    }

    fun hide() {
        val v = view ?: return
        val m = wm
        view = null; wm = null; lp = null
        v.animate().alpha(0f).setDuration(220).withEndAction { try { m?.removeView(v) } catch (_: Exception) {} }.start()
    }
}

private class HudView(ctx: Context) : View(ctx) {
    var state: GlowState = GlowState.IDLE
    var dimFull = true

    private class Ring(val r: Float, val sw: Float, val n: Int, val fill: Float, val speed: Float, val a: Float, val glow: Boolean)
    // video jaisa: patli dashed ring, moti segmented band, beech ki dashes, patli arcs, chamakdar chhote arcs, sabse bahar halki arcs
    private val rings = listOf(
        Ring(0.46f, 1.6f, 90, 0.55f, 10f, 0.55f, false),
        Ring(0.55f, 13f, 4, 0.80f, -22f, 0.95f, true),
        Ring(0.66f, 5f, 18, 0.62f, 34f, 0.85f, true),
        Ring(0.76f, 2.5f, 3, 0.62f, -14f, 0.90f, false),
        Ring(0.86f, 6f, 2, 0.30f, 48f, 1.00f, true),
        Ring(0.95f, 1.6f, 6, 0.50f, -8f, 0.60f, false)
    )

    private val dp = ctx.resources.displayMetrics.density
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tpFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.12f
    }
    private val tpGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.12f
    }
    private val oval = RectF()
    private val born = SystemClock.uptimeMillis()
    private var last = born
    private var spin = 0f
    private var compact = 0f
    private var dim = 0f
    private var lvl = 0f
    private var col = 0xFF00C8FF.toInt()

    private fun mix(a: Int, b: Int, t: Float): Int = Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP) HudOverlay.onTap?.invoke()
        return true
    }

    private fun arcs(c: Canvas, cx: Float, cy: Float, rr: Float, sw: Float, n: Int, fill: Float, rot: Float, a: Float, glow: Boolean) {
        oval.set(cx - rr, cy - rr, cx + rr, cy + rr)
        val step = 360f / n
        val passes = if (glow) 3 else 1
        for (ps in 0 until passes) {
            val top = ps == passes - 1
            sp.strokeWidth = if (glow) (if (ps == 0) sw * 2.8f else if (ps == 1) sw * 1.8f else sw) else sw
            sp.color = col
            sp.alpha = (255f * (a * (if (!glow || top) 1f else if (ps == 0) 0.10f else 0.20f)).coerceIn(0f, 1f)).toInt()
            for (k in 0 until n) c.drawArc(oval, rot + k * step, step * fill, false, sp)
        }
    }

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = (now - last).coerceIn(1L, 64L) / 1000f
        last = now
        val target = when (state) { GlowState.IDLE -> 0xFF2F8CFF; GlowState.LISTENING -> 0xFF00C8FF; GlowState.SPEAKING -> 0xFF18E0FF; GlowState.THINKING -> 0xFF8A7CFF }.toInt()
        col = mix(col, target, 1f - exp(-5f * dt))
        val tc = if (state == GlowState.THINKING) 1f else 0f
        compact += (tc - compact) * (1f - exp(-5f * dt))
        val td = if (dimFull) 0.86f * (1f - compact) else 0f
        dim += (td - dim) * (1f - exp(-5f * dt))
        lvl += (AvatarBus.level - lvl) * (1f - exp(-14f * dt))
        val mult = when (state) { GlowState.THINKING -> 2.2f; GlowState.SPEAKING -> 1.35f; GlowState.LISTENING -> 1f; GlowState.IDLE -> 0.6f } + lvl * 1.2f
        spin += dt * mult
        val fade = ((now - born) / 300f).coerceIn(0f, 1f)
        val ga = fade * (1f - 0.45f * compact)

        val w = width.toFloat(); val h = height.toFloat()
        if (dim > 0.01f) c.drawColor(Color.argb((dim * 255f).toInt().coerceIn(0, 255), 0, 5, 14))
        val breathe = 1f + 0.015f * sin((now % 2400L) / 2400f * 2f * PI.toFloat())
        val r0 = minOf(w, h) / 2f * 0.96f
        val rr = r0 * (1f - 0.66f * compact) * breathe * (1f + 0.05f * lvl)
        val cx = w / 2f
        val cy = h / 2f + (h * 0.14f - h / 2f) * compact
        val k = rr / r0

        // beech ki halki chamak (awaaz ke saath badhti hai)
        for (i in 0..4) {
            fp.color = col
            fp.alpha = ((10f + 22f * lvl) * ga).toInt().coerceIn(0, 255)
            c.drawCircle(cx, cy, rr * (0.52f - i * 0.08f), fp)
        }
        sp.strokeWidth = 1.5f * dp * k; sp.color = col; sp.alpha = (130f * ga).toInt()
        c.drawCircle(cx, cy, rr * 0.38f, sp)

        for ((i, rg) in rings.withIndex()) {
            val rot = rg.speed * spin + i * 37f
            arcs(c, cx, cy, rr * rg.r, rg.sw * dp * k, rg.n, rg.fill, rot, rg.a * ga, rg.glow)
        }

        // "DIVYA"
        val ts = rr * 0.23f
        tpGlow.textSize = ts; tpFill.textSize = ts
        tpGlow.strokeWidth = ts * 0.14f; tpGlow.color = col; tpGlow.alpha = (90f * ga).toInt()
        tpFill.alpha = (255f * ga).toInt()
        val ty = cy - (tpFill.ascent() + tpFill.descent()) / 2f
        c.drawText("DIVYA", cx, ty, tpGlow)
        c.drawText("DIVYA", cx, ty, tpFill)

        postInvalidateOnAnimation()
    }
}
