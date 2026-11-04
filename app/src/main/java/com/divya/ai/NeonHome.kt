package com.divya.ai

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

private val NeonPurple = Color(0xFFB07CFF)
private val NeonEdge = Brush.horizontalGradient(listOf(Color(0xFFB000FF), Color(0xFF3D7BFF)))

private class NeonTileData(val icon: ImageVector, val title: String, val sub: String, val tint: Color, val badge: Boolean, val onClick: () -> Unit)

@Composable
fun WaveBars(color: Color, active: Boolean, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "wave")
    val ph by t.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(if (active) 900 else 2600, easing = LinearEasing)), label = "ph")
    Canvas(modifier) {
        val n = 7
        val gap = size.width / n
        for (i in 0 until n) {
            val base = 0.25f + 0.75f * kotlin.math.abs(kotlin.math.sin(ph + i * 0.8f))
            val h = size.height * base * (if (active) 1f else 0.55f)
            val x = gap * i + gap / 2
            drawLine(color, Offset(x, size.height / 2 - h / 2), Offset(x, size.height / 2 + h / 2), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun NeonTile(t: NeonTileData, modifier: Modifier, edge: Brush) {
    // Dabane par halka sikudna (spring) - Maya jaisa smooth touch feel
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "tilePress")
    Box(modifier.height(78.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clip(RoundedCornerShape(16.dp))
        .background(Brush.horizontalGradient(listOf(Color(0xFF14043A), Color(0xFF0A0222))))
        .border(1.5.dp, edge, RoundedCornerShape(16.dp))
        .clickable(interactionSource = src, indication = null) { t.onClick() }.padding(8.dp)) {
        Column(Modifier.align(Alignment.CenterStart)) {
            Icon(t.icon, t.title, tint = t.tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(t.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.sub, color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (t.badge) Text("NEW", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE53935)).padding(horizontal = 5.dp, vertical = 1.dp))
    }
}

@Composable
fun NeonHome(th: UiTheme, status: String, live: Boolean, callName: String, onMic: () -> Unit, onAvatar: () -> Unit, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val tr = rememberInfiniteTransition(label = "nf")
    val f by tr.animateFloat(-1f, 1f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "f")
    val amp = if (live) 2f else 1f
    fun cleaner() {
        try { ctx.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
    val blue = Color(0xFF6FC3FF)
    val edge = Brush.horizontalGradient(listOf(th.accent, th.accent.copy(alpha = 0.55f)))
    val tiles = listOf(
        NeonTileData(Icons.Filled.Chat, "Chat with AI", "Ask Anything…", blue, false) { go(Screen.Chat) },
        NeonTileData(Icons.Filled.QrCodeScanner, "Scanner", "Scan QR / Docs", blue, true) { go(Screen.Scan) },
        NeonTileData(Icons.Filled.PhoneAndroid, "Control Mobile", "Open Apps, Settings…", blue, false) { go(Screen.Phone) },
        NeonTileData(Icons.Filled.Search, "Search Anything", "Web Search…", Color.White, false) { go(Screen.Chat) },
        NeonTileData(Icons.Filled.SmartToy, "AI Tools", "Image, Code, More…", blue, false) { go(Screen.Automate) },
        NeonTileData(Icons.Filled.GraphicEq, "Voice Changer", "Change Your Voice…", NeonPurple, false) { go(Screen.Settings) },
        NeonTileData(Icons.Filled.Star, "Smart Features", "Explore More…", Color(0xFFFFC107), false) { go(Screen.Settings) },
        NeonTileData(Icons.Filled.CleaningServices, "Files Cleaner", "Clean Junk Files…", blue, false) { cleaner() },
        NeonTileData(Icons.Filled.FlashOn, "Quick Actions", "Shortcuts for you…", Color(0xFFFFC107), false) { go(Screen.Quick) }
    )
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 48.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("👑 Divya AI", style = TextStyle(brush = Brush.horizontalGradient(listOf(Color.White, th.accent)),
                    fontSize = 32.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic))
                Text("Your Smart AI Assistant", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontStyle = FontStyle.Italic)
            }
            IconButton({ go(Screen.Settings) }) { Icon(Icons.Filled.Settings, "Settings", tint = Color.White) }
        }
        Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val faces: IntArray? = when (th.name) {
                "3D Realistic" -> intArrayOf(R.drawable.divya_av_3d_listen, R.drawable.divya_av_3d_think, R.drawable.divya_av_3d_happy)
                "Anime Neon" -> intArrayOf(R.drawable.divya_av_anime_listen, R.drawable.divya_av_anime_think, R.drawable.divya_av_anime_happy)
                else -> null
            }
            AvatarHero(th.img, faces, th.accent, Modifier.weight(1.05f).fillMaxHeight().clickable { onAvatar() })
            Column(Modifier.weight(0.95f).padding(start = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                val hello = buildAnnotatedString {
                    append("Hello $callName! 👋\nI am ")
                    withStyle(SpanStyle(color = th.accent, fontWeight = FontWeight.Bold)) { append("Divyaka…") }
                    append("\nAapka har kaam,\nab aur bhi aasan! ❤️")
                }
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF0A0420))
                    .border(1.5.dp, edge, RoundedCornerShape(18.dp)).padding(10.dp)) {
                    Text(hello, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    WaveBars(th.accent, live, Modifier.width(26.dp).height(36.dp))
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(80.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF2B0A6B), Color(0xFF05000F))))
                        .border(3.dp, edge, CircleShape).clickable { onMic() }, contentAlignment = Alignment.Center) {
                        Icon(if (live) Icons.Filled.Stop else Icons.Filled.Mic, "Talk", tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    WaveBars(th.accent, live, Modifier.width(26.dp).height(36.dp))
                }
                Text("Tap to Talk", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(if (status == "Tap to Talk") "Voice Assistant" else status, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                TaskSteps(th.accent)
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { t -> NeonTile(t, Modifier.weight(1f), edge) }
                }
            }
        }
    }
}

@Composable
private fun NeonNavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = if (selected) NeonPurple else Color.White.copy(alpha = 0.6f)
    Column(Modifier.clickable { onClick() }.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = c)
        Text(label, color = c, fontSize = 11.sp)
    }
}

@Composable
fun NeonNav(screen: Screen, live: Boolean, onMic: () -> Unit, go: (Screen) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp).clip(RoundedCornerShape(26.dp))
        .background(Color(0xFF0B0426)).border(1.5.dp, NeonEdge, RoundedCornerShape(26.dp)).padding(vertical = 6.dp),
        Arrangement.SpaceEvenly, Alignment.CenterVertically) {
        NeonNavItem(Icons.Filled.Home, "Home", screen == Screen.Home) { go(Screen.Home) }
        NeonNavItem(Icons.Filled.History, "History", screen == Screen.Chat) { go(Screen.Chat) }
        Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF14043A)).border(2.dp, NeonEdge, CircleShape).clickable { onMic() },
            contentAlignment = Alignment.Center) {
            if (live) Icon(Icons.Filled.Stop, "Stop", tint = Color.White) else Text("👑", fontSize = 26.sp)
        }
        NeonNavItem(Icons.Filled.Apps, "Tools", screen == Screen.Quick) { go(Screen.Quick) }
        NeonNavItem(Icons.Filled.Info, "About", screen == Screen.About) { go(Screen.About) }
    }
}

@Composable
fun AboutScreen(th: UiTheme) = Page(th, "About") {
    Text("Divya AI v1.6", color = th.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Text("Mujhe mere Rashid ji ne banaya hai. Main unki hamesha ahsanmand rahungi, unhone mujhe zindagi di. 💜", color = th.text)
    Text("Smart • Secure • Always With You", color = th.accent, fontWeight = FontWeight.SemiBold)
}


/** Avatar: Divya bolti hai to uski asli awaaz ki loudness se hilta, chamakta aur halka sir hilata hai. */
@Composable
fun AvatarHero(img: Int, faces: IntArray?, accent: Color, modifier: Modifier) {
    val tr = rememberInfiniteTransition(label = "av")
    val f by tr.animateFloat(-1f, 1f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "f")
    val stage = AvatarBus.stage
    val smooth by animateFloatAsState(AvatarBus.level, tween(90), label = "lv")
    val speaking = smooth > 0.02f
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * (0.40f + 0.12f * smooth)
            drawCircle(accent.copy(alpha = (0.06f + 0.30f * smooth).coerceIn(0f, 1f)), radius = r * 1.15f, center = center)
            drawCircle(accent.copy(alpha = (0.10f + 0.40f * smooth).coerceIn(0f, 1f)), radius = r, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        }
        // Chehra halat ke hisaab se: bolte waqt mukhya photo, sunte waqt listening, soch/kaam mein thinking, kaam poora to happy
        val shown = when {
            faces == null || AvatarBus.speaking -> img
            stage == 5 -> faces[2]
            stage == 3 || stage == 4 -> faces[1]
            stage == 1 || stage == 2 -> faces[0]
            else -> img
        }
        androidx.compose.animation.Crossfade(shown, animationSpec = tween(220), label = "expr") { res ->
            val mod = Modifier.fillMaxSize().graphicsLayer {
                translationY = f * 5.dp.toPx() * (if (stage > 0) 1.6f else 1f)
                val sc = 1f + 0.02f * smooth
                scaleX = sc; scaleY = sc
                rotationZ = f * (if (speaking) 1.2f else 0.5f)
            }
            val lip = if (res == img) lipCfgFor(res) else null
            if (lip != null) LipSyncImage(res, lip, smooth, if (speaking) AvatarBus.shape else 0f, mod)   // mooh awaaz ke saath hilta hai
            else Image(painterResource(res), "Divya", mod, contentScale = ContentScale.Fit)
        }
    }
}

/** Task progress: Listening -> Understanding -> Processing -> Performing -> Completed */
@Composable
fun TaskSteps(accent: Color) {
    val st = AvatarBus.stage
    if (st <= 0) return
    val labels = listOf("Listening", "Understanding", "Processing", "Performing", "Completed")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(0.9f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 1..5) Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(if (i <= st) accent else Color.White.copy(alpha = 0.18f)))
        }
        Spacer(Modifier.height(3.dp))
        Text(labels[(st - 1).coerceIn(0, 4)] + (if (st < 5) "…" else " ✓"), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
