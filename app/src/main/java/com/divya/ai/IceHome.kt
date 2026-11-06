package com.divya.ai

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Ice Light" home: halka (light) background, poori height ka Divya model beech mein,
 * left/right glass tiles, upar speech bubble + Tap to Talk. Reference photo jaisa.
 * Avatar bolte waqt AvatarBus.level se halka hilta aur glow karta hai.
 */
private val IceNavy = Color(0xFF0B1B3F)
private val IceNavy2 = Color(0xFF06122C)
private val IceBlue = Color(0xFF4DB8FF)
private val IceEdge = Brush.linearGradient(listOf(Color(0xFF6FD0FF), Color(0xFF2F7BFF)))

private class IceTileData(val icon: ImageVector, val title: String, val tint: Color, val badge: Boolean, val onClick: () -> Unit)

@Composable
private fun IceTile(t: IceTileData, modifier: Modifier) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "icePress")
    Box(modifier.graphicsLayer { scaleX = sc; scaleY = sc }
        .clip(RoundedCornerShape(16.dp))
        .background(Brush.verticalGradient(listOf(IceNavy, IceNavy2)))
        .border(1.5.dp, IceEdge, RoundedCornerShape(16.dp))
        .clickable(interactionSource = src, indication = null) { t.onClick() }
        .padding(horizontal = 9.dp, vertical = 6.dp)) {
        Column(Modifier.align(Alignment.CenterStart)) {
            Icon(t.icon, t.title, tint = t.tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(3.dp))
            Text(t.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (t.badge) Text("NEW", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE53935)).padding(horizontal = 5.dp, vertical = 1.dp))
    }
}

@Composable
fun IceHome(th: UiTheme, status: String, live: Boolean, callName: String, onMic: () -> Unit, go: (Screen) -> Unit) {
    val tr = rememberInfiniteTransition(label = "ice")
    val f by tr.animateFloat(-1f, 1f, infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "f")
    val pulse by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val smooth by animateFloatAsState(AvatarBus.level, tween(90), label = "lv")
    val stage = AvatarBus.stage
    val ink = th.text
    val blue = IceBlue

    val leftTiles = listOf(
        IceTileData(Icons.Filled.Chat, "Chat with AI", blue, false) { go(Screen.Chat) },
        IceTileData(Icons.Filled.Apps, "Kali Launcher", Color.White, true) { go(Screen.Apps) },
        IceTileData(Icons.Filled.Star, "Smart Features", Color(0xFFFFC107), false) { go(Screen.Settings) }
    )
    val rightTiles = listOf(
        IceTileData(Icons.Filled.QrCodeScanner, "Scanner", blue, true) { go(Screen.Scan) },
        IceTileData(Icons.Filled.PhoneAndroid, "Control Mobile", blue, false) { go(Screen.Phone) },
        IceTileData(Icons.Filled.SmartToy, "AI Tools", blue, false) { go(Screen.Automate) },
        IceTileData(Icons.Filled.GraphicEq, "Voice Changer", blue, false) { go(Screen.VoiceSettings) },
        IceTileData(Icons.Filled.FlashOn, "Quick Actions", Color(0xFFFFC107), false) { go(Screen.Quick) }
    )

    Column(Modifier.fillMaxSize()) {
        // ---- Header ----
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(IceNavy).border(1.5.dp, IceEdge, CircleShape).clickable { go(Screen.Apps) },
                contentAlignment = Alignment.Center) { Icon(Icons.Filled.Menu, "Menu", tint = IceBlue) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Divya AI", style = TextStyle(brush = Brush.horizontalGradient(listOf(IceNavy, Color(0xFF1E90FF))),
                    fontSize = 30.sp, fontWeight = FontWeight.Black))
                Text("Your Voice, My World", color = ink.copy(alpha = 0.8f), fontSize = 12.sp, fontStyle = FontStyle.Italic)
            }
            IconButton({ go(Screen.Settings) }, Modifier.size(40.dp)) { Icon(Icons.Filled.Settings, "Settings", tint = IceNavy) }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val H = maxHeight
            val W = maxWidth
            val tileH = (H * 0.092f).coerceIn(44.dp, 66.dp)
            val colGap = 6.dp

            // ---- floor glow ring ----
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width * 0.5f
                val cy = size.height * 0.93f
                for (i in 0..2) {
                    val rw = size.width * (0.32f + 0.12f * i) + smooth * 20f
                    val rh = rw * 0.22f
                    drawOval(Color(0xFF3AA0FF).copy(alpha = 0.30f - 0.08f * i), Offset(cx - rw, cy - rh), Size(rw * 2, rh * 2),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = (3 - i).coerceAtLeast(1).dp.toPx()))
                }
                drawCircle(Brush.radialGradient(listOf(Color(0xFF7CC4FF).copy(alpha = 0.35f + 0.3f * smooth), Color.Transparent),
                    center = Offset(cx, size.height * 0.45f), radius = size.width * (0.55f + 0.1f * smooth)),
                    radius = size.width * 0.7f, center = Offset(cx, size.height * 0.45f))
            }

            // ---- Divya model ----
            Image(painterResource(th.img), "Divya",
                Modifier.align(Alignment.Center).fillMaxHeight(0.97f).padding(bottom = 2.dp).graphicsLayer {
                    translationY = f * 4.dp.toPx() * (if (stage > 0) 1.6f else 1f)
                    val sc = 1f + 0.015f * smooth
                    scaleX = sc; scaleY = sc
                    rotationZ = f * (if (smooth > 0.02f) 0.9f else 0.35f)
                }, contentScale = ContentScale.Fit)

            // ---- Left: orb (top) + tiles (bottom) ----
            Box(Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = H * 0.07f).size(92.dp)
                .graphicsLayer { val s = 1f + 0.05f * smooth + 0.02f * pulse; scaleX = s; scaleY = s }
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF1C4FD0), Color(0xFF071A4D))))
                .border(2.5.dp, IceEdge, CircleShape), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("👑", fontSize = 20.sp)
                    Text("Divya AI", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = H * 0.05f).width(W * 0.30f),
                verticalArrangement = Arrangement.spacedBy(colGap)) {
                leftTiles.forEach { IceTile(it, Modifier.fillMaxWidth().height(tileH)) }
            }

            // ---- Right: bubble, mic, tiles ----
            Column(Modifier.align(Alignment.TopEnd).padding(end = 8.dp).width(W * 0.37f), horizontalAlignment = Alignment.CenterHorizontally) {
                val hello = buildAnnotatedString {
                    append("Hello $callName! 👋\nI am ")
                    withStyle(SpanStyle(color = Color(0xFF29D3FF), fontWeight = FontWeight.Bold)) { append("Divya…") }
                    append("\nAapka har kaam,\nab aur bhi aasan! ❤️")
                }
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(IceNavy2)
                    .border(1.5.dp, IceEdge, RoundedCornerShape(16.dp)).padding(horizontal = 9.dp, vertical = 7.dp)) {
                    Text(hello, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    WaveBars(Color(0xFF29B6FF), live, Modifier.width(18.dp).height(34.dp))
                    Spacer(Modifier.width(2.dp))
                    Box(Modifier.size(70.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFF123A8F), IceNavy2)))
                        .border(3.dp, IceEdge, CircleShape).clickable { onMic() }, contentAlignment = Alignment.Center) {
                        Icon(if (live) Icons.Filled.Stop else Icons.Filled.Mic, "Talk", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.width(2.dp))
                    WaveBars(Color(0xFF29B6FF), live, Modifier.width(18.dp).height(34.dp))
                }
                Text("Tap to Talk", color = IceNavy, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(3.dp))
                Row(Modifier.clip(RoundedCornerShape(14.dp)).background(IceNavy.copy(alpha = 0.08f)).padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Mic, null, tint = Color(0xFF6C3CE9), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(if (status == "Tap to Talk") "Bolo, Divya sun rahi hai…" else status, color = IceNavy, fontSize = 10.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                if (stage > 0) TaskSteps(Color(0xFF1E90FF))
                else if (live) Text("Listening…", color = Color(0xFF1E90FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = H * 0.02f).width(W * 0.34f),
                verticalArrangement = Arrangement.spacedBy(colGap)) {
                rightTiles.forEach { IceTile(it, Modifier.fillMaxWidth().height(tileH * 0.86f)) }
            }
        }
    }
}
