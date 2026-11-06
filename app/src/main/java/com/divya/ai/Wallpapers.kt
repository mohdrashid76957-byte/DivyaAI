package com.divya.ai

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import java.util.Locale

/**
 * v2.22.8: Divya voice se phone ka wallpaper badal sakti hai (Jarvis wala "wallpaper bhi change kar sakta hai").
 * Wallpaper phone par hi bante hain (neon rings, koi internet / AI nahi): reactor (neela), matrix (hara), crimson (laal),
 * violet (jamuni), gold (sunehra), aurora, amoled (kala). "wallpaper badlo" = agla style, "random wallpaper", "lock screen wallpaper red".
 * Offline voice, typed chat aur Gemini Live (phone_brain tool) teeno se chalta hai.
 */
object Wallpapers {
    private class Pal(val name: String, val hindi: String, val main: Int, val acc: Int, val bg: Int, val words: List<String>)

    private val PALS = listOf(
        Pal("reactor", "neela", 0xFF00C8FF.toInt(), 0xFF9BF3FF.toInt(), 0xFF01060F.toInt(), listOf("reactor", "arc", "blue", "neela", "neeli", "नीला", "नीली", "cyan", "jarvis", "ice")),
        Pal("matrix", "hara", 0xFF00FF7F.toInt(), 0xFFC4FFE0.toInt(), 0xFF010A05.toInt(), listOf("matrix", "green", "hara", "hari", "हरा", "हरी", "iron", "hulk")),
        Pal("crimson", "laal", 0xFFFF3B3B.toInt(), 0xFFFFB9A6.toInt(), 0xFF0F0203.toInt(), listOf("crimson", "red", "laal", "lal", "लाल", "blood")),
        Pal("violet", "jamuni", 0xFFB26BFF.toInt(), 0xFFE6D0FF.toInt(), 0xFF08020F.toInt(), listOf("violet", "purple", "jamuni", "बैंगनी", "जामुनी", "galaxy")),
        Pal("gold", "sunehra", 0xFFFFC107.toInt(), 0xFFFFF0A8.toInt(), 0xFF0F0A01.toInt(), listOf("gold", "golden", "sunehra", "sunahri", "सुनहरा", "yellow", "peela", "पीला")),
        Pal("aurora", "aurora", 0xFF00E5B0.toInt(), 0xFF7FB0FF.toInt(), 0xFF010C0C.toInt(), listOf("aurora", "teal")),
        Pal("amoled", "kala", 0xFFFFFFFF.toInt(), 0xFF9AA0A6.toInt(), 0xFF000000.toInt(), listOf("amoled", "black", "kala", "kaala", "काला", "dark"))
    )

    private fun al(c: Int, a: Int) = Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))
    private fun mix(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(), (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())

    /** Phone par neon rings wala wallpaper bitmap. */
    private fun render(c: Context, pal: Pal, seed: Long): Bitmap {
        val dm = c.resources.displayMetrics
        var w = dm.widthPixels; var h = dm.heightPixels
        if (w.toLong() * h > 9_000_000L) { w = w * 2 / 3; h = h * 2 / 3 }   // bahut bade screen par memory bachao
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val rnd = java.util.Random(seed)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val wf = w.toFloat(); val hf = h.toFloat()
        p.shader = LinearGradient(0f, 0f, 0f, hf, intArrayOf(pal.bg, mix(pal.bg, pal.main, 0.12f), pal.bg), null, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, wf, hf, p); p.shader = null
        val cx = wf / 2f; val cy = hf * 0.40f; val r = wf * 0.46f
        p.shader = RadialGradient(cx, cy, r * 1.1f, intArrayOf(al(pal.main, 95), al(pal.main, 0)), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r * 1.1f, p); p.shader = null
        // rings: (radius, stroke, segments, fill)
        val specs = listOf(
            floatArrayOf(0.30f, 3f, 60f, 0.5f), floatArrayOf(0.42f, 22f, 6f, 0.78f), floatArrayOf(0.58f, 5f, 24f, 0.6f),
            floatArrayOf(0.74f, 3f, 3f, 0.6f), floatArrayOf(0.90f, 9f, 2f, 0.3f), floatArrayOf(1.04f, 2f, 8f, 0.5f)
        )
        p.style = Paint.Style.STROKE
        val oval = RectF()
        for (sp in specs) {
            val rr = r * sp[0]; val sw = sp[1] * dm.density * (w / 1080f).coerceAtLeast(0.6f); val n = sp[2].toInt(); val step = 360f / n
            val rot = rnd.nextFloat() * 360f
            oval.set(cx - rr, cy - rr, cx + rr, cy + rr)
            val glow = sw > 4f * dm.density
            val passes = if (glow) 3 else 1
            for (ps in 0 until passes) {
                p.strokeWidth = if (!glow) sw else if (ps == 0) sw * 2.8f else if (ps == 1) sw * 1.8f else sw
                p.color = pal.main
                p.alpha = if (!glow || ps == passes - 1) 235 else if (ps == 0) 28 else 55
                for (k in 0 until n) cv.drawArc(oval, rot + k * step, step * sp[3], false, p)
            }
        }
        // core + chhote chamakte bindu
        p.style = Paint.Style.FILL
        p.color = pal.main; p.alpha = 70; cv.drawCircle(cx, cy, r * 0.20f, p)
        p.color = pal.acc; p.alpha = 220; cv.drawCircle(cx, cy, r * 0.09f, p)
        repeat(70) {
            p.color = if (rnd.nextBoolean()) pal.main else pal.acc
            p.alpha = 40 + rnd.nextInt(150)
            cv.drawCircle(rnd.nextFloat() * wf, rnd.nextFloat() * hf, (1f + rnd.nextFloat() * 2.5f) * dm.density, p)
        }
        return bmp
    }

    /** Divya Kali wallpaper (drawable) ko home + lock par lagao. Background thread se chalao. */
    fun setKali(c: Context, home: Boolean = true, lock: Boolean = true): String = try {
        val o = android.graphics.BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = android.graphics.BitmapFactory.decodeResource(c.resources, R.drawable.divya_kali_wall, o)
        val wm = WallpaperManager.getInstance(c)
        if (Build.VERSION.SDK_INT >= 24) {
            val flag = (if (home) WallpaperManager.FLAG_SYSTEM else 0) or (if (lock) WallpaperManager.FLAG_LOCK else 0)
            wm.setBitmap(bmp, null, true, flag)
        } else wm.setBitmap(bmp)
        bmp.recycle()
        "Divya Kali wallpaper lag gaya. 🐉"
    } catch (e: Throwable) { "Kali wallpaper nahi lag paya: " + (e.message ?: e.javaClass.simpleName) }

    private fun has(t: String, vararg w: String) = w.any { t.contains(it) }

    /** t = lowercase command. Background thread se chalao (bitmap banta aur set hota hai). */
    fun handle(c: Context, t: String): String {
        val prefs = c.getSharedPreferences("divya", 0)
        if (has(t, "list", "kaun kaun", "kaun se", "kitne", "styles", "options", "कौन")) {
            return "Wallpaper styles: " + PALS.joinToString(", ") { it.name + " (" + it.hindi + ")" } + ". Bolo: green wallpaper lagao, ya random wallpaper."
        }
        if (has(t, "kali", "kaali linux", "dragon")) return setKali(c, !has(t, "lock") || has(t, "home"), !has(t, "home") || has(t, "lock"))
        val words = t.split(Regex("[^\\p{L}\\p{M}]+")).filter { it.isNotEmpty() }.toSet()
        var idx = PALS.indexOfFirst { pl -> pl.words.any { it in words } }
        var randomPick = false
        if (idx < 0) {
            val cur = prefs.getInt("wpIdx", -1)
            idx = if (has(t, "random", "koi bhi", "kuch bhi", "any", "रैंडम")) { randomPick = true; (0 until PALS.size).filter { it != cur }.random() } else (cur + 1).mod(PALS.size)
        }
        val pal = PALS[idx]
        val lockOnly = has(t, "lock", "लॉक") && !has(t, "home", "होम")
        val homeOnly = has(t, "home", "होम") && !has(t, "lock", "लॉक")
        return try {
            val bmp = render(c, pal, System.nanoTime())
            val wm = WallpaperManager.getInstance(c)
            if (Build.VERSION.SDK_INT >= 24) {
                val flag = if (lockOnly) WallpaperManager.FLAG_LOCK else if (homeOnly) WallpaperManager.FLAG_SYSTEM else WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                wm.setBitmap(bmp, null, true, flag)
            } else wm.setBitmap(bmp)
            bmp.recycle()
            prefs.edit().putInt("wpIdx", idx).apply()
            val where = if (lockOnly) "lock screen ka" else if (homeOnly) "home screen ka" else "wallpaper"
            (if (randomPick) "Random chuna: " else "") + "${pal.name} (${pal.hindi}) $where lag gaya." + (if (lockOnly || homeOnly) "" else " Aur chahiye to 'wallpaper badlo' bolo.")
        } catch (e: SecurityException) { "Wallpaper badalne ki permission nahi mili."
        } catch (e: Throwable) { "Wallpaper nahi badal paayi: " + (e.message ?: e.javaClass.simpleName) }
    }
}
