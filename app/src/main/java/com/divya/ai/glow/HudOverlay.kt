package com.divya.ai.glow

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * v2.22.10: Voice orb (reference video jaisa). Divya se baat karte waqt screen ke neeche-beech (dock ke upar) ek chhota gola:
 *  - shant hone par chhota, beech mein chamakta dot + patli ring.
 *  - awaaz (meri mic ki ya Divya ki) ke saath gola bada hota hai, uska kinara kaante jaisa (jagged) naachta hai, bahar ripple rings nikalti hain.
 *  - neeche live caption pill: "LISTENING...", phir jo suna/bola ja raha hai, kaam ke waqt "WORKING...".
 *  - THINKING mein ghoomte arcs + violet rang.
 * Touch bilkul pass-through (Back/Home/gesture kuch block nahi hota) aur ye Divya ko band nahi karta.
 * Permission: Accessibility ON (DivyaService) ya "Display over other apps".
 */
object HudOverlay {
    /** Purana tap-to-stop hook, ab kabhi call nahi hota (MainActivity compile ke liye rakha hai). */
    @Volatile var onTap: (() -> Unit)? = null
    private var view: HudView? = null
    private var wm: WindowManager? = null

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
        val w = (minOf(sw, sh) * 0.92f).toInt()
        val h = (w * 0.92f).toInt()
        val prefs = base.getSharedPreferences("divya", 0)
        val halo = prefs.getBoolean("hudDim", true)
        val orb = prefs.getString("orbStyle", "classic") ?: "classic"
        val l = WindowManager.LayoutParams(
            w, h, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or      // poora pass-through
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        l.gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
        // orb ka kendra screen ki ~72% unchai par (video mein dock ke thoda upar), pill uske neeche
        l.y = (sh * 0.72f - h * HudView.CY).toInt().coerceAtLeast(0)
        val v = HudView(base).also { it.state = state; it.halo = halo; it.style = orb }
        try { manager.addView(v, l) } catch (_: Exception) { return }
        wm = manager; view = v
    }

    fun setState(state: GlowState) { view?.state = state }

    fun hide() {
        val v = view ?: return
        val m = wm
        view = null; wm = null
        v.animate().alpha(0f).setDuration(220).withEndAction { try { m?.removeView(v) } catch (_: Exception) {} }.start()
    }
}

private class HudView(ctx: Context) : View(ctx) {
    companion object { const val CY = 0.40f }   // orb ka kendra window ki unchai ka 40% par

    var state: GlowState = GlowState.IDLE
    var halo = true
    /** "classic" = purana gola + kaante; "4dzine" = chaukor/tesseract style orb */
    var style = "classic"

    private val N = 72
    private val dp = ctx.resources.displayMetrics.density
    private val cur = FloatArray(N)
    private val tgt = FloatArray(N)
    private val cur2 = FloatArray(N)
    private val tgt2 = FloatArray(N)
    private val rnd = java.util.Random()
    private var nextJit = 0L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); letterSpacing = 0.06f
    }
    private val path = Path()
    private val oval = RectF()
    private val pill = RectF()

    private val born = SystemClock.uptimeMillis()
    private var last = born
    private var spin = 0f
    private var think = 0f
    private var lvl = 0f
    private var col = 0xFF1EB4FF.toInt()
    private var pillW = 0f
    private var shownText = ""
    private var shownSrc = ""
    private var lastRipple = 0L
    private val ripple = FloatArray(3) { -1f }

    private fun mix(a: Int, b: Int, t: Float): Int = Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )

    override fun onTouchEvent(e: MotionEvent): Boolean = false

    private fun circle(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, a: Float) {
        fill.color = color; fill.alpha = (255f * a).toInt().coerceIn(0, 255); c.drawCircle(cx, cy, r, fill)
    }

    private fun ringLine(c: Canvas, cx: Float, cy: Float, r: Float, sw: Float, a: Float) {
        line.strokeWidth = sw; line.color = col; line.alpha = (255f * a).toInt().coerceIn(0, 255); c.drawCircle(cx, cy, r, line)
    }

    /** Kaante jaisa naachta kinara: har point ka radius random target ki taraf smooth hota hai, awaaz se amp badhta hai. */
    private fun jag(c: Canvas, cx: Float, cy: Float, r: Float, arr: FloatArray, amp: Float, sw: Float, a: Float, rot: Float = 0f) {
        path.reset()
        for (i in 0 until N) {
            val ang = i * 2f * PI.toFloat() / N + rot
            val rr = r * (1f + amp * arr[i])
            val x = cx + rr * cos(ang); val y = cy + rr * sin(ang)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        line.color = col
        line.strokeWidth = sw * 3.2f; line.alpha = (255f * a * 0.18f).toInt(); c.drawPath(path, line)
        line.strokeWidth = sw; line.alpha = (255f * a).toInt().coerceIn(0, 255); c.drawPath(path, line)
    }

    private fun newTargets(act: Float) {
        for (i in 0 until N) {
            val spike = if (rnd.nextFloat() < 0.16f) 1f else 0.30f
            tgt[i] = (rnd.nextFloat() * 2f - 1f) * spike * act
            tgt2[i] = (rnd.nextFloat() * 2f - 1f) * (spike * 0.8f) * act
        }
        // thoda smooth taaki "aas-paas ke point" milkar uthen (organic), par spikes bache rahein
        for (i in 0 until N) { val a = tgt[(i + 1) % N]; val b = tgt[(i + N - 1) % N]; tgt[i] = tgt[i] * 0.65f + (a + b) * 0.175f }
    }

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = (now - last).coerceIn(1L, 64L) / 1000f
        last = now

        val target = when (state) { GlowState.IDLE -> 0xFF2F8CFF; GlowState.LISTENING -> 0xFF1EB4FF; GlowState.SPEAKING -> 0xFF2AE0FF; GlowState.THINKING -> 0xFF8A7CFF }.toInt()
        col = mix(col, target, 1f - exp(-5f * dt))
        think += ((if (state == GlowState.THINKING) 1f else 0f) - think) * (1f - exp(-5f * dt))

        // awaaz: Divya bol rahi ho to uski, sunte waqt meri mic ki (halka shor hata ke)
        val mic = if (state == GlowState.LISTENING) ((AvatarBus.micLevel - 0.07f) / 0.93f).coerceIn(0f, 1f) else 0f
        val raw = max(AvatarBus.level, mic)
        lvl += (raw - lvl) * (1f - exp(-(if (raw > lvl) 30f else 9f) * dt))
        spin += dt * (0.5f + 1.6f * think + 0.8f * lvl)

        val fade = ((now - born) / 300f).coerceIn(0f, 1f)
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h * CY
        val breathe = 1f + 0.025f * sin((now % 2600L) / 2600f * 2f * PI.toFloat())
        val base = w * 0.075f
        val r = base * (1f + 1.05f * lvl) * breathe
        val ga = fade

        // naachte kinare ke targets
        if (now >= nextJit) {
            nextJit = now + 70L
            newTargets(if (lvl > 0.04f) 1f else 0.15f)
        }
        val k = 1f - exp(-20f * dt)
        for (i in 0 until N) { cur[i] += (tgt[i] - cur[i]) * k; cur2[i] += (tgt2[i] - cur2[i]) * k }
        val amp = 0.04f + 0.46f * lvl + 0.10f * think

        // ripple: awaaz tez ho to naya ring bahar nikalta hai
        if (lvl > 0.33f && now - lastRipple > 420L) {
            val slot = ripple.indexOfFirst { it < 0f }
            if (slot >= 0) { ripple[slot] = 0f; lastRipple = now }
        }
        for (i in ripple.indices) if (ripple[i] >= 0f) { ripple[i] += dt * 0.9f; if (ripple[i] >= 1f) ripple[i] = -1f }

        // peeche halki chamak (halo)
        if (halo) for (i in 0..5) circle(c, cx, cy, r * (2.3f - i * 0.28f), col, (0.012f + 0.02f * lvl) * ga)
        // bahar ki patli rings
        ringLine(c, cx, cy, base * (1.62f + 0.35f * lvl + 0.04f * sin(spin * 2f)), 1.0f * dp, 0.22f * ga)
        ringLine(c, cx, cy, base * (2.15f + 0.75f * lvl), 1.0f * dp, 0.12f * ga)
        for (i in ripple.indices) {
            val p = ripple[i]; if (p < 0f) continue
            ringLine(c, cx, cy, r * (1.25f + 1.7f * p), 1.4f * dp, (1f - p) * 0.45f * ga)
        }
        // ghoomte arcs (THINKING mein saaf, baaki mein halke)
        oval.set(cx - r * 1.30f, cy - r * 1.30f, cx + r * 1.30f, cy + r * 1.30f)
        line.color = col; line.strokeWidth = 1.6f * dp; line.alpha = (255f * (0.22f + 0.70f * think) * ga).toInt()
        for (kk in 0 until 3) c.drawArc(oval, spin * 55f + kk * 120f, 38f, false, line)

        if (style == "4dzine") {
            draw4dzine(c, cx, cy, r, ga)
        } else {
        // gola (dark teal, shishe jaisa)
        circle(c, cx, cy, r, mix(col, Color.BLACK, 0.80f), 0.88f * ga)
        circle(c, cx, cy, r * 0.72f, mix(col, Color.BLACK, 0.62f), 0.40f * ga)
        circle(c, cx, cy, r * 0.40f, mix(col, Color.BLACK, 0.45f), 0.30f * ga)

        // kaante jaisa naachta kinara (do layer)
        // video jaisa: kaanton wala ghera lagataar ghoomta hai (bahar ek taraf, andar ulta)
        jag(c, cx, cy, r, cur, amp, 1.6f * dp, 0.95f * ga, spin * 3.0f)
        jag(c, cx, cy, r * 0.93f, cur2, amp * 0.8f, 1.0f * dp, 0.50f * ga, -spin * 2.2f)

        // beech ka chamakta dot
        val dr = (3.2f + 4.5f * lvl) * dp
        circle(c, cx, cy, dr * 3.2f, col, 0.10f * ga)
        circle(c, cx, cy, dr * 1.9f, col, 0.22f * ga)
        circle(c, cx, cy, dr, mix(col, Color.WHITE, 0.30f), 1f * ga)
        }

        drawCaption(c, w, h, dt, now, ga)
        postInvalidateOnAnimation()
    }

    /**
     * 4DZINE orb: do chaukor (bahar wala ghoomta hai, andar wala ulta) jinke kone jude hue hain,
     * jaise ek 4D (tesseract) ka projection. Chaar quadrant arcs bahar ghoomte hain.
     * Awaaz ke saath chaukor bada/chamakta hai.
     */
    private fun draw4dzine(c: Canvas, cx: Float, cy: Float, r: Float, ga: Float) {
        val ro = r * 1.02f
        val ri = r * 0.50f
        val a = spin * 60f
        val outer = Path()
        val inner = Path()
        val ox = FloatArray(4); val oy = FloatArray(4)
        val ix = FloatArray(4); val iy = FloatArray(4)
        for (k in 0 until 4) {
            val ang = Math.toRadians((a + k * 90.0)).toFloat()
            ox[k] = cx + ro * cos(ang); oy[k] = cy + ro * sin(ang)
            val ang2 = Math.toRadians((-a * 1.6 + 45.0 + k * 90.0)).toFloat()
            ix[k] = cx + ri * cos(ang2); iy[k] = cy + ri * sin(ang2)
            if (k == 0) { outer.moveTo(ox[k], oy[k]); inner.moveTo(ix[k], iy[k]) }
            else { outer.lineTo(ox[k], oy[k]); inner.lineTo(ix[k], iy[k]) }
        }
        outer.close(); inner.close()

        // andar ka chaukor: halka bharav (fill)
        fill.color = mix(col, Color.BLACK, 0.78f); fill.alpha = (255f * 0.85f * ga).toInt().coerceIn(0, 255)
        c.drawPath(inner, fill)
        fill.color = mix(col, Color.BLACK, 0.72f); fill.alpha = (255f * 0.80f * ga).toInt().coerceIn(0, 255)
        c.drawPath(outer, fill)

        // kone jodne wali lines (tesseract ki depth)
        line.color = col; line.strokeWidth = 1.1f * dp; line.alpha = (255f * 0.55f * ga).toInt().coerceIn(0, 255)
        for (k in 0 until 4) c.drawLine(ox[k], oy[k], ix[k], iy[k], line)

        // chaukor ki neon outline
        line.strokeWidth = 1.6f * dp; line.alpha = (255f * 0.95f * ga).toInt().coerceIn(0, 255); c.drawPath(outer, line)
        line.strokeWidth = 1.2f * dp; line.alpha = (255f * 0.7f * ga).toInt().coerceIn(0, 255); c.drawPath(inner, line)

        // chaar quadrant arcs, bahar ki taraf ghoomte hue
        oval.set(cx - r * 1.45f, cy - r * 1.45f, cx + r * 1.45f, cy + r * 1.45f)
        line.strokeWidth = 2.2f * dp
        for (k in 0 until 4) {
            line.alpha = (255f * (0.55f + 0.35f * think) * ga).toInt().coerceIn(0, 255)
            c.drawArc(oval, -a * 0.7f + k * 90f + 8f, 66f, false, line)
        }
    }

    private fun captionText(now: Long): String {
        val cap = AvatarBus.caption.trim()
        val age = now - AvatarBus.captionAt
        val dots = ".".repeat(1 + ((now / 380L) % 3L).toInt())
        return when {
            state == GlowState.THINKING && (cap.isEmpty() || age > 2500L) -> "WORKING$dots"
            cap.isNotEmpty() && age < 4500L -> cap.uppercase().replace('\n', ' ')
            state == GlowState.LISTENING -> "LISTENING$dots"
            state == GlowState.SPEAKING -> "SPEAKING$dots"
            else -> "READY"
        }
    }

    private fun drawCaption(c: Canvas, w: Float, h: Float, dt: Float, now: Long, ga: Float) {
        txt.textSize = 11f * dp
        val maxW = w * 0.94f - 28f * dp
        var s = captionText(now)
        if (s != shownSrc) {
            shownSrc = s
            if (txt.measureText(s) > maxW) {
                // lamba ho to shuru ka hissa kaato, naye shabd dikhte rahein
                var t = s
                while (t.length > 4 && txt.measureText("…$t") > maxW) t = t.substring(1)
                s = "…" + t.trimStart()
            }
            shownText = s
        }
        val tw = txt.measureText(shownText)
        val targetW = tw + 28f * dp
        pillW += (targetW - pillW) * (1f - exp(-14f * dt))
        if (pillW < 40f * dp) pillW = targetW
        val cx = w / 2f
        val ph = 26f * dp
        val top = h * 0.80f - ph / 2f
        pill.set(cx - pillW / 2f, top, cx + pillW / 2f, top + ph)
        fill.color = Color.rgb(3, 14, 28); fill.alpha = (225f * ga).toInt(); c.drawRoundRect(pill, 9f * dp, 9f * dp, fill)
        line.color = col
        line.strokeWidth = 4f * dp; line.alpha = (255f * 0.15f * ga).toInt(); c.drawRoundRect(pill, 9f * dp, 9f * dp, line)
        line.strokeWidth = 1.2f * dp; line.alpha = (255f * 0.85f * ga).toInt(); c.drawRoundRect(pill, 9f * dp, 9f * dp, line)
        txt.color = mix(col, Color.WHITE, 0.55f); txt.alpha = (255f * ga).toInt()
        c.drawText(shownText, cx, top + ph / 2f - (txt.ascent() + txt.descent()) / 2f, txt)
    }
}
