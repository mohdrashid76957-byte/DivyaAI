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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v3.31 "Jarvis OS" design - Divya + Jarvis ke liye ek hi naya look (reference mockups se prerit, apna original code/assets):
 *  OLED dark, greeting, beech mein 3D particle orb (Jarvis) ya glowing circular Divya avatar, JARVIS/DIVYA ON-OFF pill,
 *  live weather card, Screen Share / Memories / Language tiles, floating nav (Home, Chat, bada mic, Settings, Updates).
 *  Dono persona isi design mein: Jarvis ON = particle orb + blue, Divya = avatar + violet. Persona chip se badlo (jab session band ho).
 */
private val OsBg = Color(0xFF05060D)
private val OsPanel = Color(0xFF0B1226)
private val OsEdge = Color(0xFF223A7A)
private val OsBlue = Color(0xFF4D8DFF)
private val OsViolet = Color(0xFF9B6BFF)

@Composable
fun OsHome(th: UiTheme, status: String, live: Boolean, callName: String, onMic: () -> Unit, onShare: () -> Unit, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val jarvis = Persona.on
    val who = if (jarvis) "JARVIS" else "DIVYA"
    val acc = if (jarvis) OsBlue else OsViolet
    val lvl by animateFloatAsState(AvatarBus.level, tween(90), label = "oslv")
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greet = when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }
    var retry by remember { mutableStateOf(0) }
    val wx by produceState(initialValue = "...", retry) {
        value = "..."
        val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { try { PhoneTools.weather(ctx, "") } catch (e: Exception) { "failed" } }
        value = if (r.startsWith("failed")) "" else r.replace("\n", " ").trim().take(110)
    }
    val stateText = when { !live -> "TAP TO START"; AvatarBus.speaking -> "SPEAKING"; else -> "LISTENING" }

    Column(Modifier.fillMaxSize().background(OsBg).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // ---- top bar ----
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OsIconBtn(Icons.Filled.Chat, "Chat") { go(Screen.Chat) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (jarvis) "Jarvis" else "Divya", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light, letterSpacing = 3.sp)
                Text("$greet, $callName", color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            OsIconBtn(Icons.Filled.Settings, "Settings") { go(Screen.Settings) }
        }

        // ---- orb / avatar ----
        Box(Modifier.weight(1f).fillMaxWidth().clickable { onMic() }, contentAlignment = Alignment.Center) {
            if (jarvis) ParticleOrb(lvl, live, Modifier.fillMaxSize().padding(8.dp))
            else DivyaOrb(th.img, lvl, live, acc, Modifier.fillMaxSize().padding(14.dp))
        }

        // ---- status pill + persona chip ----
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val pill = RoundedCornerShape(50)
            Row(Modifier.clip(pill).border(1.2.dp, acc.copy(alpha = 0.8f), pill).clickable { onMic() }.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (live) Color(0xFF3DFFA2) else Color(0xFF6F7B99)))
                Spacer(Modifier.width(8.dp))
                Text("$who ${if (live) "ON" else "OFF"} · $stateText", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }
            val chip = RoundedCornerShape(50)
            Row(Modifier.clip(chip).border(1.dp, OsEdge, chip).background(OsPanel).clickable(enabled = !live) { Persona.set(ctx, !jarvis) }.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (jarvis) "💜 Divya" else "🤖 Jarvis", color = if (live) Color(0xFF6F7B99) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(if (live) (if (status.isNotBlank()) status else "Bol rahe ho, main sun raha hoon") else "Tap mic or orb to power on",
            color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))

        // ---- weather ----
        val card = RoundedCornerShape(20.dp)
        Row(Modifier.fillMaxWidth().clip(card).background(OsPanel).border(1.dp, OsEdge, card).clickable { retry++ }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WbSunny, "Weather", tint = Color(0xFFFFC857), modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Live Weather", color = acc, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(if (wx == "...") "Loading..." else if (wx.isBlank()) "Tap to retry" else wx, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(10.dp))

        // ---- tiles ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OsTile(Icons.Filled.ScreenShare, "Screen", "Share screen", acc, Modifier.weight(1f)) { onShare() }
            OsTile(Icons.Filled.Memory, "Memories", "Recall Core", acc, Modifier.weight(1f)) { go(Screen.Journal) }
            OsTile(Icons.Filled.Language, "Language", "Auto", acc, Modifier.weight(1f)) { go(Screen.VoiceSettings) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OsIconBtn(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(OsPanel).border(1.dp, OsEdge, RoundedCornerShape(14.dp)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun OsTile(icon: ImageVector, title: String, sub: String, acc: Color, modifier: Modifier, onClick: () -> Unit) {
    val s = RoundedCornerShape(18.dp)
    Column(modifier.clip(s).background(OsPanel).border(1.dp, OsEdge, s).clickable { onClick() }.padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, title, tint = acc, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(sub, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, maxLines = 1)
    }
}

/** Jarvis: ghoomta hua 3D particle sphere (Fibonacci sphere), awaaz ke saath dhadakta hai, bahar ghoomte arcs. */
@Composable
private fun ParticleOrb(lvl: Float, live: Boolean, modifier: Modifier) {
    val n = 380
    val pts = remember {
        FloatArray(n * 3).also { a ->
            val ga = (PI * (3.0 - sqrt(5.0))).toFloat()
            for (i in 0 until n) {
                val y = 1f - (i / (n - 1f)) * 2f
                val r = sqrt(1f - y * y)
                val th = ga * i
                a[i * 3] = cos(th) * r; a[i * 3 + 1] = y; a[i * 3 + 2] = sin(th) * r
            }
        }
    }
    val tr = rememberInfiniteTransition(label = "orb")
    val ang by tr.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(22000, easing = LinearEasing)), label = "ang")
    val ring by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "ring")
    val breathe by tr.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "br")
    Canvas(modifier) {
        val c = center
        val base = size.minDimension * 0.34f
        val r = base * breathe * (1f + 0.10f * lvl)
        drawCircle(Brush.radialGradient(listOf(OsBlue.copy(alpha = if (live) 0.30f else 0.14f), Color.Transparent), center = c, radius = r * 1.9f), radius = r * 1.9f, center = c)
        val ca = cos(ang); val sa = sin(ang); val ct = 0.93f; val st = 0.37f
        for (i in 0 until n) {
            val px = pts[i * 3]; val py = pts[i * 3 + 1]; val pz = pts[i * 3 + 2]
            val x = px * ca + pz * sa
            val z0 = -px * sa + pz * ca
            val y = py * ct - z0 * st
            val z = py * st + z0 * ct
            val depth = (z + 1f) / 2f
            val wob = 1f + 0.05f * lvl * sin(ang * 6f + i)
            val col = if (i % 3 == 0) OsViolet else OsBlue
            drawCircle(col.copy(alpha = 0.25f + 0.75f * depth), (1.2f + 2.2f * depth).dp.toPx(), Offset(c.x + x * r * wob, c.y + y * r * wob))
        }
        // bahar ke arcs
        for (k in 0..2) {
            val rr = r * (1.28f + 0.14f * k)
            drawArc(OsBlue.copy(alpha = 0.55f - 0.12f * k), ring * (if (k % 2 == 0) 1f else -1f) + k * 70f, 110f, false,
                Offset(c.x - rr, c.y - rr), androidx.compose.ui.geometry.Size(rr * 2, rr * 2), style = Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }
}

/** Divya: gol glowing frame mein uska avatar, wahi natural motion (saans/nod/sway), awaaz ke saath ring dhadakti hai. */
@Composable
private fun DivyaOrb(img: Int, lvl: Float, live: Boolean, acc: Color, modifier: Modifier) {
    val motion = rememberAvatarMotion()
    val tr = rememberInfiniteTransition(label = "dorb")
    val ring by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "dring")
    val breathe by tr.animateFloat(0.5f, 1f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dbr")
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val d = minOf(maxWidth, maxHeight) * 0.80f
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = d.toPx() / 2f
            drawCircle(Brush.radialGradient(listOf(acc.copy(alpha = (if (live) 0.35f else 0.16f) * (0.6f + 0.4f * breathe) + 0.25f * lvl), Color.Transparent), center = c, radius = r * 1.6f), radius = r * 1.6f, center = c)
            for (k in 0..1) {
                val rr = r * (1.10f + 0.07f * k) * (1f + 0.04f * lvl)
                drawArc(acc.copy(alpha = 0.6f - 0.2f * k), ring * (if (k == 0) 1f else -1f), 120f, false,
                    Offset(c.x - rr, c.y - rr), androidx.compose.ui.geometry.Size(rr * 2, rr * 2), style = Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
        }
        Image(painterResource(img), "Divya",
            Modifier.size(d).clip(CircleShape).border(2.dp, acc.copy(alpha = 0.9f), CircleShape).graphicsLayer { motion() },
            contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
    }
}

/** Floating nav (mockup jaisa): Home, Chat, bada mic, Settings, Updates (=About). */
@Composable
fun OsNav(screen: Screen, live: Boolean, onMic: () -> Unit, go: (Screen) -> Unit) {
    val jarvis = Persona.on
    val acc = if (jarvis) OsBlue else OsViolet
    val shape = RoundedCornerShape(28.dp)
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp).clip(shape).background(OsPanel).border(1.2.dp, OsEdge, shape).padding(vertical = 6.dp),
        Arrangement.SpaceEvenly, Alignment.CenterVertically) {
        OsNavItem(Icons.Filled.Home, "Home", screen == Screen.Home, acc) { go(Screen.Home) }
        OsNavItem(Icons.Filled.Chat, "Chat", screen == Screen.Chat || screen == Screen.ProChat, acc) { go(Screen.Chat) }
        Box(Modifier.size(58.dp).clip(CircleShape).background(if (live) Color(0xFF3A1020) else Color(0xFF0E1A3A))
            .border(2.dp, if (live) Color(0xFFFF4D6D) else acc, CircleShape).clickable { onMic() }, contentAlignment = Alignment.Center) {
            Icon(if (live) Icons.Filled.Stop else Icons.Filled.Mic, if (live) "Stop" else "Mic", tint = Color.White, modifier = Modifier.size(28.dp))
        }
        OsNavItem(Icons.Filled.Settings, "Settings", screen == Screen.Settings, acc) { go(Screen.Settings) }
        OsNavItem(Icons.Filled.Notifications, "Updates", screen == Screen.About, acc) { go(Screen.About) }
    }
}

@Composable
private fun OsNavItem(icon: ImageVector, label: String, selected: Boolean, acc: Color, onClick: () -> Unit) {
    val c = if (selected) acc else Color.White.copy(alpha = 0.6f)
    Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable { onClick() }.padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = c)
        Text(label, color = c, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
