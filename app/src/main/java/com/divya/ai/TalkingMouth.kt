package com.divya.ai

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * v3.25 Safe lip-sync: drawBitmapMesh (jo crash karta tha) ki jagah photo ke upar Compose shapes ka mooh (cavity + daant + zaban + hont ki lakeer).
 * Mooh Divya ki ASLI awaaz se hilta hai: level = kitna khula, shape = gol (aa/oo) ya chaudha (ee/s). LipCfg (mooh ki jagah) LipSync.kt wali hi hai.
 * Ye procedural hai (awaaz ki loudness + zero-crossing se andaza), asli phoneme/viseme nahi.
 */
@Composable
fun TalkingMouth(res: Int, cfg: LipCfg, level: Float, shape: Float, modifier: Modifier) {
    val iz = painterResource(res).intrinsicSize
    Canvas(modifier) {
        val o = ((level * 1.7f).coerceIn(0f, 1f)).let { it * (1f - 0.30f * shape) }
        if (o < 0.05f || iz.width <= 0f || iz.height <= 0f) return@Canvas
        val sc = min(size.width / iz.width, size.height / iz.height)
        val dw = iz.width * sc; val dh = iz.height * sc
        val ox = (size.width - dw) / 2f; val oy = (size.height - dh) / 2f
        val mx = ox + cfg.mx * dw; val my = oy + cfg.my * dh
        val hw = cfg.fw * dw / 2f
        val amp = if (cfg.cavity) 0.8f else 0.5f
        val rnd = (1f - shape) * 0.6f * o
        val dmax = o * amp * hw
        val rx = hw * (0.60f + 0.30f * o) * (1f - 0.25f * rnd)
        val ry = max(2.5.dp.toPx(), dmax * 0.55f + 0.03f * hw)
        val cy = my + dmax * 0.35f
        val a = min(1f, o * 3f)
        rotate(cfg.ang, Offset(mx, cy)) {
            // hont ki naram lakeer (mooh ke kinare photo se mil jaye)
            drawOval(Color(0xFFB03A4A).copy(alpha = 0.45f * a), Offset(mx - rx * 1.12f, cy - ry * 1.12f), Size(rx * 2.24f, ry * 2.24f), style = Stroke(width = max(1.5.dp.toPx(), ry * 0.18f)))
            // andar ka andhera
            drawOval(Color(0xFF340A12).copy(alpha = a), Offset(mx - rx, cy - ry), Size(rx * 2f, ry * 2f))
            if (cfg.cavity) {
                drawOval(Color(0xFF96323C).copy(alpha = 0.55f * a), Offset(mx - rx * 0.62f, cy + ry * 0.05f), Size(rx * 1.24f, ry * 0.85f))   // zaban
                drawOval(Color(0xFFF0E6E2).copy(alpha = 0.80f * a), Offset(mx - rx * 0.70f, cy - ry * 0.92f), Size(rx * 1.40f, ry * 0.52f))  // upar ke daant
            }
        }
    }
}
