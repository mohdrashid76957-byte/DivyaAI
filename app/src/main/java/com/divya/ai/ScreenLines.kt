package com.divya.ai

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

/**
 * v3.13: screen ki line ka style aur jagah user ki pasand se.
 * lineStyle: glow (purana rainbow) | zine (2D flat + shadow) | move (chalti line)
 * linePos:   all (chaaron taraf) | side (dono side) | top (upar) | bottom (neeche)
 */
@Composable
fun ScreenLineAuto(color: Color, active: Boolean) {
    val p = LocalContext.current.getSharedPreferences("divya", 0)
    val pos = p.getString("linePos", "all") ?: "all"
    when (p.getString("lineStyle", "glow")) {
        "zine" -> ZineLine(color, active, pos)
        "move" -> MovingLine(color, active, pos)
        else -> GlowBorder(color, active)
    }
}

@Composable
fun ZineLine(color: Color, active: Boolean, pos: String) {
    val t = rememberInfiniteTransition(label = "zine")
    val hue by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (active) 2000 else 5000, easing = LinearEasing), RepeatMode.Restart), label = "zh")
    val view = LocalView.current
    Canvas(Modifier.fillMaxSize()) {
        val r = radiusPx(view, 40.dp.toPx())
        val c = Color.hsv(hue % 360f, 0.9f, 1f)
        val w = 6.dp.toPx()
        val off = 4.dp.toPx()
        clipPos(pos) {
            // hard shadow (comic/2D look)
            drawRoundRect(Color.Black, topLeft = Offset(w / 2 + off, w / 2 + off),
                size = Size(size.width - w, size.height - w), cornerRadius = CornerRadius(r), style = Stroke(w))
            drawRoundRect(c, topLeft = Offset(w / 2, w / 2),
                size = Size(size.width - w, size.height - w), cornerRadius = CornerRadius(r), style = Stroke(w))
        }
    }
}

@Composable
fun MovingLine(color: Color, active: Boolean, pos: String) {
    val t = rememberInfiniteTransition(label = "mv")
    val prog by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (active) 2200 else 4500, easing = LinearEasing), RepeatMode.Restart), label = "mp")
    val hue by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "mh")
    val view = LocalView.current
    Canvas(Modifier.fillMaxSize()) {
        val r = radiusPx(view, 40.dp.toPx())
        val inset = 3.dp.toPx()
        val path = Path().apply {
            addRoundRect(RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(r)))
        }
        val pm = PathMeasure()
        pm.setPath(path, false)
        val len = pm.length
        if (len <= 0f) return@Canvas
        val head = prog * len
        val tail = head + len * 0.30f
        val c = Color.hsv(hue % 360f, 0.9f, 1f)
        clipPos(pos) {
            drawWrapped(pm, len, head, tail, c.copy(alpha = 0.35f), 12.dp.toPx())
            drawWrapped(pm, len, head, tail, Color.White, 3.dp.toPx())
        }
    }
}

private fun radiusPx(view: android.view.View, fallback: Float): Float =
    if (android.os.Build.VERSION.SDK_INT >= 31)
        view.rootWindowInsets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: fallback
    else fallback

private fun DrawScope.drawWrapped(pm: PathMeasure, len: Float, a: Float, b: Float, col: Color, width: Float) {
    val p = Path()
    if (b <= len) {
        pm.getSegment(a, b, p, true)
    } else {
        pm.getSegment(a, len, p, true)
        pm.getSegment(0f, b - len, p, false)
    }
    drawPath(p, col, style = Stroke(width, cap = StrokeCap.Round))
}

/** Line sirf chuni hui jagah par dikhe. */
private fun DrawScope.clipPos(pos: String, block: DrawScope.() -> Unit) {
    val cv = drawContext.canvas
    val strip = size.width * 0.12f
    val hStrip = size.height * 0.16f
    when (pos) {
        "side" -> {
            cv.save(); cv.clipRect(0f, 0f, strip, size.height); block(); cv.restore()
            cv.save(); cv.clipRect(size.width - strip, 0f, size.width, size.height); block(); cv.restore()
        }
        "top" -> { cv.save(); cv.clipRect(0f, 0f, size.width, hStrip); block(); cv.restore() }
        "bottom" -> { cv.save(); cv.clipRect(0f, size.height - hStrip, size.width, size.height); block(); cv.restore() }
        else -> block()
    }
}

fun lineStyleLabel(s: String) = when (s) { "zine" -> "2D Zine"; "move" -> "Chalti line"; else -> "Glow (purana)" }
fun nextLineStyle(s: String) = when (s) { "glow" -> "zine"; "zine" -> "move"; else -> "glow" }
fun linePosLabel(s: String) = when (s) { "side" -> "Dono side"; "top" -> "Upar"; "bottom" -> "Neeche"; else -> "Chaaron taraf" }
fun nextLinePos(s: String) = when (s) { "all" -> "side"; "side" -> "top"; "top" -> "bottom"; else -> "all" }
