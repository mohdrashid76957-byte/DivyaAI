package com.divya.ai

import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Photo par "lip-sync" (procedural, rigged avatar nahi):
 *  1. Divya ki asli awaaz ki loudness se jabda (jaw) neeche khinchta hai - drawBitmapMesh se image ka mooh wala hissa warp hota hai.
 *  2. Mooh ke andar naram andhera + daant + zaban (sirf cavity=true wale avatars par; baaki par sirf jabda warp).
 *  3. Awaaz ke zero-crossing se mooh thoda gol (aa/oo) ya chaudha (ee/s) hota hai.
 * Ye asli phoneme lip-sync nahi hai. Asli ke liye viseme sprites / animated avatar chahiye (README dekho).
 *
 * mx,my = mooh ka center (image ka fraction), fw = mooh ki poori chaurai (image width ka fraction), ang = mooh ka jhukaav (degree).
 */
/** Mooh-hilne wala effect. Kuch phones par Listening shuru hote hi (Crossfade + drawBitmapMesh) app gira deta tha, isliye abhi OFF. */
const val LIP_SYNC_ENABLED = false

data class LipCfg(val mx: Float, val my: Float, val fw: Float, val ang: Float, val cavity: Boolean)

fun lipCfgFor(res: Int): LipCfg? = when (res) {
    R.drawable.divya_av_3d -> LipCfg(0.70f, 0.405f, 0.12f, 3f, true)
    R.drawable.divya_av_anime -> LipCfg(0.695f, 0.455f, 0.115f, -8f, true)
    R.drawable.divya_av_blue -> LipCfg(0.54f, 0.52f, 0.06f, 18f, false)
    R.drawable.divya_av_gold -> LipCfg(0.59f, 0.41f, 0.075f, 14f, false)
    R.drawable.divya_av_pink -> LipCfg(0.675f, 0.49f, 0.06f, 12f, false)
    else -> null
}

private class MouthPaints {
    private fun grad(rgb: Int, a0: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val c = (rgb and 0xFFFFFF)
        shader = RadialGradient(0f, 0f, 1f, intArrayOf((a0 shl 24) or c, (a0 shl 24) or c, c), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }
    val cavity = grad(0x340A12, 255)
    val tongue = grad(0x96323C, 255).apply { alpha = 140 }
    val teeth = grad(0xF0E6E2, 255).apply { alpha = 205 }
    val bmp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
}

private fun smooth01(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

@Composable
fun LipSyncImage(res: Int, cfg: LipCfg, level: Float, shape: Float, modifier: Modifier) {
    val bm = ImageBitmap.imageResource(res).asAndroidBitmap()
    val paints = remember { MouthPaints() }
    val cols = 60; val rows = 80
    val verts = remember { FloatArray((cols + 1) * (rows + 1) * 2) }
    // loudness -> mooh kholna (0..1); tez awaaz (s/ee) mein thoda kam khulta hai
    val o = ((level * 1.7f).coerceIn(0f, 1f)).let { it * (1f - 0.30f * shape) }
    val rnd = (1f - shape) * 0.6f * o
    val amp = if (cfg.cavity) 0.8f else 0.45f
    Canvas(modifier) {
        val sc = min(size.width / bm.width, size.height / bm.height)
        val dw = bm.width * sc; val dh = bm.height * sc
        val ox = (size.width - dw) / 2f; val oy = (size.height - dh) / 2f
        val mx = ox + cfg.mx * dw; val my = oy + cfg.my * dh
        val hw = cfg.fw * dw / 2f
        var k = 0
        for (j in 0..rows) {
            val y = oy + dh * j / rows
            val vy = smooth01(my - 0.2f * hw, my + 0.4f * hw, y) * (1f - smooth01(my + 1.6f * hw, my + 3.0f * hw, y))
            val ny = (y - my) / (0.9f * hw); val near = exp(-ny * ny)
            for (i in 0..cols) {
                val x = ox + dw * i / cols
                val nx = (x - mx) / (1.25f * hw); val hx = exp(-nx * nx)
                val kx = 1f - rnd * 0.22f * hx * near
                verts[k++] = mx + (x - mx) * kx
                verts[k++] = y + o * amp * hw * hx * vy
            }
        }
        drawIntoCanvas { cv ->
          try {
            val nc = cv.nativeCanvas
            nc.drawBitmapMesh(bm, cols, rows, verts, 0, null, 0, paints.bmp)
            if (cfg.cavity && o > 0.06f) {
                val dmax = o * amp * hw
                val cx = mx; val cy = my + dmax * 0.5f
                val rx = hw * (0.62f + 0.30f * o) * (1f - 0.25f * rnd)
                val ry = max(3f, dmax * 0.55f + 0.03f * hw)
                nc.save(); nc.translate(cx, cy); nc.rotate(cfg.ang)
                nc.save(); nc.scale(rx * 1.15f, ry * 1.15f)
                paints.cavity.alpha = (min(1f, o * 3f) * 255).toInt(); nc.drawCircle(0f, 0f, 1f, paints.cavity); nc.restore()
                nc.save(); nc.translate(0f, ry * 0.5f); nc.scale(rx * 0.72f, ry * 0.55f); nc.drawCircle(0f, 0f, 1f, paints.tongue); nc.restore()
                nc.save(); nc.translate(0f, -ry * 0.62f); nc.scale(rx * 0.80f, ry * 0.26f); nc.drawCircle(0f, 0f, 1f, paints.teeth); nc.restore()
                nc.restore()
            }
          } catch (e: Throwable) {
            android.util.Log.e("LipSync", "draw error", e)
            cv.nativeCanvas.drawBitmap(bm, null, android.graphics.RectF(ox, oy, ox + dw, oy + dh), paints.bmp)
          }
        }
    }
}
