package com.divya.ai

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * v3.2 "Stonic HUD" home: reference video (Stonic AI) jaisa sci-fi feel.
 *  - dark navy + faint grid, beech-dayen glowing character frame (corner brackets, scanline, awaaz ke saath pulse)
 *  - baayen taraf 4 nodes (MEMORY / SKILLS / CHARACTER / SETTINGS) rang-birangi connected lines, lines par chalte hue light dots
 *  - lal TERMINATE pill (Divya band), band hone par ACTIVATE
 *  - neeche LIVE FEED glass panel: jo suna/bola ja raha hai + waveform + VOICE / AI LINK / NET chips
 */
private val StBg = Color(0xFF030A14)
private val StPanel = Color(0xFF07182A)
private val StTeal = Color(0xFF19F5D4)
private val StRed = Color(0xFFFF3B5C)
private val StMono = FontFamily.Monospace

private class StNode(val label: String, val icon: ImageVector, val color: Color, val target: Screen)

@Composable
fun StonicHome(th: UiTheme, status: String, live: Boolean, callName: String, onMic: () -> Unit, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val jarvis = Persona.on
    val acc = if (jarvis) Color(0xFF3AB8FF) else StTeal
    val who = if (jarvis) "Jarvis" else "Divya"
    val tr = rememberInfiniteTransition(label = "st")
    val flow by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "flow")
    val breathe by tr.animateFloat(0.35f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "br")
    val scan by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "scan")
    val lvl by animateFloatAsState(AvatarBus.level, tween(90), label = "lv")
    val cap by produceState(initialValue = "", key1 = live) {
        while (true) { value = AvatarBus.caption.trim(); kotlinx.coroutines.delay(250) }
    }
    val net by produceState(initialValue = true, ctx) { while (true) { value = Net.online(ctx); kotlinx.coroutines.delay(5000) } }
    val nodes = remember {
        listOf(
            StNode("MEMORY", Icons.Filled.Memory, Color(0xFF3DFFA2), Screen.Journal),
            StNode("SKILLS", Icons.Filled.FlashOn, Color(0xFF19E3F0), Screen.Quick),
            StNode("CHARACTER", Icons.Filled.Face, Color(0xFFFF4D6D), Screen.Apps),
            StNode("SETTINGS", Icons.Filled.Settings, Color(0xFFB07CFF), Screen.Settings)
        )
    }
    val stateText = when {
        !live -> "STANDBY"
        AvatarBus.speaking -> "SPEAKING"
        else -> "LISTENING"
    }
    val stateCol = if (live) acc else Color(0xFF5E7A99)

    Column(Modifier.fillMaxSize().background(StBg)) {
        // ---- Header ----
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(StPanel).border(1.dp, acc.copy(alpha = 0.7f), CircleShape).clickable { go(Screen.Apps) },
                contentAlignment = Alignment.Center) { Icon(Icons.Filled.Menu, "Menu", tint = acc, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (jarvis) "JARVIS // CORE" else "DIVYA // CORE", color = acc, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = StMono,
                    style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(acc, blurRadius = 20f)))
                Text(if (jarvis) "At your service, $callName" else "Welcome, $callName", color = Color(0xFF8FB8C9), fontSize = 11.sp, fontFamily = StMono)
            }
            Box(Modifier.size(38.dp).clip(CircleShape).background(StPanel).border(1.dp, acc.copy(alpha = 0.7f), CircleShape).clickable { go(Screen.Settings) },
                contentAlignment = Alignment.Center) { Icon(Icons.Filled.Settings, "Settings", tint = acc, modifier = Modifier.size(20.dp)) }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val W = maxWidth
            val H = maxHeight
            val nodeX = W * 0.04f
            val nodeW = W * 0.31f
            val nodeH = H * 0.088f
            val frameX = W * 0.43f
            val frameW = W * 0.53f
            val frameY = H * 0.03f
            val frameH = H * 0.45f

            // ---- background: grid + glow + connection lines ----
            Canvas(Modifier.fillMaxSize()) {
                val step = 28.dp.toPx()
                var gx = 0f
                while (gx < size.width) { drawLine(acc.copy(alpha = 0.045f), Offset(gx, 0f), Offset(gx, size.height), 1f); gx += step }
                var gy = 0f
                while (gy < size.height) { drawLine(acc.copy(alpha = 0.045f), Offset(0f, gy), Offset(size.width, gy), 1f); gy += step }

                val fx = frameX.toPx(); val fy = frameY.toPx(); val fw = frameW.toPx(); val fh = frameH.toPx()
                val c = Offset(fx + fw / 2, fy + fh / 2)
                val glowA = (if (live) 0.18f + 0.30f * lvl else 0.10f) * (0.6f + 0.4f * breathe)
                drawCircle(Brush.radialGradient(listOf(acc.copy(alpha = glowA), Color.Transparent), center = c, radius = fw * 1.1f), radius = fw * 1.1f, center = c)
                // frame ke bahar glow rings
                for (k in 1..3) {
                    val g = k * 3.dp.toPx()
                    drawRoundRect(stateCol.copy(alpha = (0.22f - k * 0.05f) * (0.5f + breathe)), Offset(fx - g, fy - g), Size(fw + 2 * g, fh + 2 * g),
                        CornerRadius(18.dp.toPx() + g), style = Stroke(1.5.dp.toPx()))
                }

                // lines: node -> frame
                val x1 = fx
                val y1c = fy + fh / 2
                nodes.forEachIndexed { i, n ->
                    val x0 = (nodeX + nodeW).toPx()
                    val y0 = (frameY + (frameH - nodeH) * (i / 3f) + nodeH / 2).toPx()
                    val y1 = y1c + (i - 1.5f) * 10.dp.toPx()
                    val d = (x1 - x0) * 0.55f
                    val p = Path().apply { moveTo(x0, y0); cubicTo(x0 + d, y0, x1 - d, y1, x1, y1) }
                    drawPath(p, n.color.copy(alpha = 0.16f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(p, n.color.copy(alpha = 0.9f), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
                    // chalta hua dot (cubic bezier par)
                    val t = (flow + i * 0.25f) % 1f
                    val u = 1f - t
                    val bx = u * u * u * x0 + 3 * u * u * t * (x0 + d) + 3 * u * t * t * (x1 - d) + t * t * t * x1
                    val by = u * u * u * y0 + 3 * u * u * t * y0 + 3 * u * t * t * y1 + t * t * t * y1
                    drawCircle(n.color.copy(alpha = 0.35f), 7.dp.toPx(), Offset(bx, by))
                    drawCircle(Color.White, 2.5.dp.toPx(), Offset(bx, by))
                    drawCircle(n.color, 3.5.dp.toPx(), Offset(x1, y1))
                }
            }

            // ---- left nodes ----
            nodes.forEachIndexed { i, n ->
                val ny = frameY + (frameH - nodeH) * (i / 3f)
                val shape = RoundedCornerShape(12.dp)
                Row(Modifier.offset(nodeX, ny).size(nodeW, nodeH).clip(shape)
                    .background(StPanel.copy(alpha = 0.92f)).border(1.2.dp, n.color.copy(alpha = 0.85f), shape)
                    .clickable { go(n.target) }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(n.icon, n.label, tint = n.color, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(n.label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = StMono,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // ---- character frame ----
            val fShape = RoundedCornerShape(18.dp)
            Box(Modifier.offset(frameX, frameY).size(frameW, frameH).clip(fShape).background(StPanel)
                .border(1.6.dp, stateCol.copy(alpha = 0.6f + 0.4f * breathe), fShape).clickable { onMic() }) {
                if (jarvis) JarvisHolo(acc, lvl, live, scan, breathe)
                else androidx.compose.foundation.Image(
                    painterResource(th.img), "Divya",
                    Modifier.fillMaxSize().graphicsLayer { val s = 1f + 0.035f * lvl; scaleX = s; scaleY = s; alpha = if (live) 1f else 0.82f },
                    contentScale = ContentScale.Crop, alignment = Alignment.TopCenter
                )
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, StBg.copy(alpha = 0.8f)), startY = size.height * 0.6f, endY = size.height))
                    drawRect(acc.copy(alpha = 0.06f))
                    // scanline
                    val sy = size.height * scan
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, acc.copy(alpha = 0.28f), Color.Transparent), startY = sy - 40f, endY = sy + 40f),
                        topLeft = Offset(0f, sy - 40f), size = Size(size.width, 80f))
                    // corner brackets
                    val m = 7.dp.toPx(); val L = 20.dp.toPx(); val sw = 2.5.dp.toPx()
                    val w = size.width; val h = size.height
                    fun br(cx: Float, cy: Float, dx: Float, dy: Float) {
                        drawLine(acc, Offset(cx, cy), Offset(cx + dx * L, cy), sw, StrokeCap.Round)
                        drawLine(acc, Offset(cx, cy), Offset(cx, cy + dy * L), sw, StrokeCap.Round)
                    }
                    br(m, m, 1f, 1f); br(w - m, m, -1f, 1f); br(m, h - m, 1f, -1f); br(w - m, h - m, -1f, -1f)
                }
                Text("● $who is ${stateText.lowercase()}", color = stateCol, fontSize = 10.sp, fontFamily = StMono, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
            }

            // ---- TERMINATE / ACTIVATE ----
            val bCol = if (live) StRed else acc
            val bShape = RoundedCornerShape(50)
            Box(Modifier.offset(W * 0.25f, H * 0.515f).size(W * 0.5f, H * 0.062f).clip(bShape)
                .background(bCol.copy(alpha = 0.10f + 0.10f * breathe)).border(1.6.dp, bCol, bShape).clickable { onMic() },
                contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (live) Icons.Filled.PowerSettingsNew else Icons.Filled.Mic, null, tint = bCol, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (live) "TERMINATE" else "ACTIVATE", color = bCol, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = StMono, letterSpacing = 2.sp)
                }
            }

            // ---- LIVE FEED panel ----
            val pShape = RoundedCornerShape(14.dp)
            Column(Modifier.offset(W * 0.03f, H * 0.605f).size(W * 0.94f, H * 0.375f).clip(pShape)
                .background(StPanel.copy(alpha = 0.88f)).border(1.dp, acc.copy(alpha = 0.55f), pShape).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("LIVE FEED", color = acc, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = StMono, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(1f))
                    Text(stateText, color = stateCol, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = StMono)
                }
                Spacer(Modifier.height(6.dp))
                Text(if (cap.isNotBlank()) cap else status.ifBlank { if (jarvis) "Jarvis online. ACTIVATE dabao ya bolo: Divya mode." else "Divya ko jagane ke liye ACTIVATE dabao ya \"Wake up Divya\" bolo." },
                    color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp, fontFamily = StMono, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                WaveBars(acc, live, Modifier.fillMaxWidth().height(30.dp))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StChip("VOICE", "${(lvl * 100).toInt().coerceIn(0, 100)}%", acc, Modifier.weight(1f))
                    StChip("AI LINK", if (live) "ON" else "OFF", if (live) Color(0xFF3DFFA2) else Color(0xFF5E7A99), Modifier.weight(1f))
                    StChip("NET", if (net) "ONLINE" else "OFFLINE", if (net) Color(0xFF19E3F0) else StRed, Modifier.weight(1f))
                }
            }
        }
    }
}


/** Jarvis hologram: cyan wireframe bust, scanline fill, glowing eyes, mooh awaaz ke saath khulta hai (video ke hologram character jaisa). */
@Composable
private fun JarvisHolo(c: Color, lvl: Float, live: Boolean, scan: Float, breathe: Float) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF021525), Color(0xFF031F38))))
        val w = size.width; val h = size.height
        val cx = w / 2; val hr = w * 0.21f; val hy = h * 0.34f
        val head = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(cx, hy), hr)) }
        val body = Path().apply {
            moveTo(cx - hr * 2.3f, h); quadraticBezierTo(cx - hr * 2.3f, hy + hr * 1.7f, cx - hr * 0.55f, hy + hr * 1.25f)
            lineTo(cx + hr * 0.55f, hy + hr * 1.25f); quadraticBezierTo(cx + hr * 2.3f, hy + hr * 1.7f, cx + hr * 2.3f, h); close()
        }
        val a = if (live) 1f else 0.6f
        for (pth in listOf(head, body)) {
            drawPath(pth, c.copy(alpha = 0.10f * a))
            clipPath(pth) {
                var y = 0f
                while (y < h) { drawLine(c.copy(alpha = 0.32f * a), Offset(0f, y), Offset(w, y), 1.dp.toPx()); y += 5.dp.toPx() }
                var x = cx - hr * 2.4f
                while (x < cx + hr * 2.4f) { drawLine(c.copy(alpha = 0.12f * a), Offset(x, 0f), Offset(x, h), 1f); x += 9.dp.toPx() }
            }
            drawPath(pth, c.copy(alpha = 0.9f * a), style = Stroke(1.6.dp.toPx()))
        }
        // aankhein + mooh (lip-sync)
        val ey = hy - hr * 0.12f
        for (sx in listOf(-1f, 1f)) drawRoundRect(Color.White.copy(alpha = 0.55f + 0.45f * breathe), Offset(cx + sx * hr * 0.42f - hr * 0.2f, ey - 2.dp.toPx()), Size(hr * 0.4f, 4.dp.toPx()), CornerRadius(4.dp.toPx()))
        val mh = 1.5.dp.toPx() + hr * 0.28f * lvl
        drawRoundRect(c, Offset(cx - hr * 0.3f, hy + hr * 0.52f - mh / 2), Size(hr * 0.6f, mh), CornerRadius(mh / 2))
        // arc-reactor ring (seene par)
        val rc = Offset(cx, h * 0.80f)
        drawCircle(c.copy(alpha = 0.25f + 0.25f * breathe), 16.dp.toPx(), rc)
        drawCircle(c, 12.dp.toPx(), rc, style = Stroke(2.dp.toPx()))
        drawCircle(Color.White, 4.dp.toPx(), rc)
        // data dots
        var i = 0
        while (i < 22) { val px = (i * 37 % 100) / 100f * w; val py = ((i * 53 + (scan * 100).toInt()) % 100) / 100f * h
            drawCircle(c.copy(alpha = 0.5f), 1.4.dp.toPx(), Offset(px, py)); i++ }
    }
}

@Composable
private fun StChip(label: String, value: String, color: Color, modifier: Modifier) {
    val s = RoundedCornerShape(8.dp)
    Column(modifier.clip(s).background(color.copy(alpha = 0.08f)).border(1.dp, color.copy(alpha = 0.6f), s).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color(0xFF8FB8C9), fontSize = 8.sp, fontFamily = StMono, textAlign = TextAlign.Center)
        Text(value, color = color, fontSize = 11.sp, fontFamily = StMono, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
