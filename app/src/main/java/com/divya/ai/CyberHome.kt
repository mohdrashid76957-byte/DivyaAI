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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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

/**
 * "Cyber HUD" home: dark navy + cyan neon. Core Systems gauges (asli battery / storage / RAM / network),
 * Divya model beech mein, left+right glass tiles, Control Center grid, neeche nav bar.
 */
private class SysInfo(val battery: Int, val storage: Int, val ram: Int)

private fun readSys(c: android.content.Context): SysInfo {
    val bat = try { (c.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager)
        .getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) } catch (e: Exception) { 0 }
    val st = try {
        val s = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        (100 - s.availableBytes * 100 / s.totalBytes).toInt()
    } catch (e: Exception) { 0 }
    val ram = try {
        val am = c.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
        (100 - mi.availMem * 100 / mi.totalMem).toInt()
    } catch (e: Exception) { 0 }
    return SysInfo(bat.coerceIn(0, 100), st.coerceIn(0, 100), ram.coerceIn(0, 100))
}

@Composable
private fun CyGauge(label: String, pct: Int, modifier: Modifier = Modifier) {
    val v by animateFloatAsState(pct.coerceIn(0, 100) / 100f, tween(700), label = "g")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 4.dp.toPx()
                val tl = Offset(sw / 2, sw / 2); val sz = Size(size.width - sw, size.height - sw)
                drawArc(CyCyan.copy(alpha = 0.18f), 0f, 360f, false, tl, sz, style = Stroke(sw))
                drawArc(CyCyan, -90f, 360f * v, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            }
            Text("${(v * 100).toInt()}%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(label, color = Color(0xFF9FE9F0), fontSize = 8.sp, maxLines = 1)
    }
}

@Composable
private fun CyTile(icon: ImageVector, title: String, badge: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF06222E), Color(0xFF031521))))
        .border(1.4.dp, CyEdge, shape).clickable { onClick() }.padding(horizontal = 9.dp, vertical = 5.dp)) {
        Column(Modifier.align(Alignment.CenterStart)) {
            Icon(icon, title, tint = CyCyan, modifier = Modifier.size(21.dp))
            Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (badge) Text("NEW", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE53935)).padding(horizontal = 5.dp, vertical = 1.dp))
    }
}

@Composable
private fun CyCell(icon: ImageVector, title: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Column(modifier.clip(shape).background(CyDeep).border(1.dp, CyCyan.copy(alpha = 0.6f), shape).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, title, tint = CyCyan, modifier = Modifier.size(20.dp))
        Text(title, color = Color(0xFFBFF3F7), fontSize = 7.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 9.sp)
    }
}

@Composable
fun CyberHome(th: UiTheme, status: String, live: Boolean, callName: String, onMic: () -> Unit, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val tr = rememberInfiniteTransition(label = "cy")
    val f by tr.animateFloat(-1f, 1f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "f")
    val rot by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "r")
    val smooth by animateFloatAsState(AvatarBus.level, tween(90), label = "lv")
    val sys by produceState(initialValue = SysInfo(0, 0, 0), ctx) {
        while (true) { value = readSys(ctx); kotlinx.coroutines.delay(5000) }
    }
    val net by produceState(initialValue = true, ctx) { while (true) { value = Net.online(ctx); kotlinx.coroutines.delay(5000) } }

    Column(Modifier.fillMaxSize()) {
        // ---- Header ----
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(CyDeep).border(1.5.dp, CyEdge, CircleShape).clickable { go(Screen.Apps) },
                contentAlignment = Alignment.Center) { Icon(Icons.Filled.Menu, "Menu", tint = CyCyan) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Divya AI", color = CyCyan, fontSize = 30.sp, fontWeight = FontWeight.Black,
                    style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(CyCyan, blurRadius = 22f)))
                Text("Your Voice, My World", color = Color(0xFF9FE9F0), fontSize = 12.sp, fontStyle = FontStyle.Italic)
            }
            IconButton({ go(Screen.Settings) }, Modifier.size(40.dp)) { Icon(Icons.Filled.Settings, "Settings", tint = CyCyan) }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val H = maxHeight
            val W = maxWidth

            // ---- background glow ----
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Brush.radialGradient(listOf(CyCyan.copy(alpha = 0.22f + 0.25f * smooth), Color.Transparent),
                    center = Offset(size.width / 2, size.height * 0.40f), radius = size.width * 0.75f),
                    radius = size.width * 0.75f, center = Offset(size.width / 2, size.height * 0.40f))
            }

            // ---- panels: Core Systems + HUD ----
            Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                Spacer(Modifier.height(H * 0.15f))
                val shape = RoundedCornerShape(10.dp)
                Box(Modifier.fillMaxWidth().height(H * 0.22f).clip(shape).background(CyDeep.copy(alpha = 0.55f)).border(1.dp, CyCyan.copy(alpha = 0.7f), shape)) {
                    Text("CORE SYSTEMS", color = CyCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 3.dp))
                    Column(Modifier.fillMaxSize().padding(top = 16.dp, bottom = 2.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                                CyGauge("BATTERY", sys.battery); CyGauge("STORAGE", sys.storage)
                            }
                            Spacer(Modifier.width(W * 0.34f))
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                                CyGauge("RAM", sys.ram); CyGauge("NETWORK", if (net) 100 else 0)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                                CyGauge("VOICE", (smooth * 100).toInt().coerceIn(0, 100))
                            }
                            Spacer(Modifier.width(W * 0.34f))
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                                CyGauge("AI LINK", if (live) 100 else 0)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(H * 0.15f).clip(shape).background(CyDeep.copy(alpha = 0.55f)).border(1.dp, CyCyan.copy(alpha = 0.7f), shape)) {
                    Canvas(Modifier.fillMaxSize()) {
                        // left: rotating rings + triangle core
                        val cx = size.width * 0.2f; val cy = size.height / 2; val r = size.height * 0.36f
                        rotate(rot, Offset(cx, cy)) {
                            drawCircle(CyCyan.copy(alpha = 0.5f), r * 1.35f, Offset(cx, cy), style = Stroke(1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 10f))))
                        }
                        rotate(-rot * 1.6f, Offset(cx, cy)) {
                            drawCircle(CyCyan.copy(alpha = 0.7f), r, Offset(cx, cy), style = Stroke(2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(30f, 14f))))
                        }
                        val tri = Path().apply {
                            moveTo(cx, cy - r * 0.55f); lineTo(cx + r * 0.5f, cy + r * 0.4f); lineTo(cx - r * 0.5f, cy + r * 0.4f); close()
                        }
                        drawPath(tri, CyCyan.copy(alpha = 0.35f + 0.5f * smooth))
                        drawPath(tri, CyCyan, style = Stroke(2.dp.toPx()))
                        // right: dotted globe
                        val gx = size.width * 0.82f; val gr = size.height * 0.40f
                        drawCircle(CyCyan.copy(alpha = 0.5f), gr, Offset(gx, cy), style = Stroke(1.dp.toPx()))
                        val step = gr / 4.5f
                        var yy = -gr
                        while (yy <= gr) {
                            var xx = -gr
                            while (xx <= gr) {
                                if (xx * xx + yy * yy <= gr * gr * 0.92f && ((xx / step).toInt() + (yy / step).toInt()) % 2 == 0)
                                    drawCircle(CyCyan.copy(alpha = 0.75f), 1.6.dp.toPx(), Offset(gx + xx, cy + yy))
                                xx += step
                            }
                            yy += step
                        }
                    }
                    Text(if (live) "LISTENING" else "STANDBY", color = CyCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp))
                }
            }

            // ---- Divya model ----
            Image(painterResource(th.img), "Divya",
                Modifier.align(Alignment.TopCenter).padding(top = H * 0.10f).height(H * 0.66f).graphicsLayer {
                    translationY = f * 3.dp.toPx()
                    val sc = 1f + 0.015f * smooth; scaleX = sc; scaleY = sc
                }, contentScale = ContentScale.Fit)

            // ---- speech bubble ----
            val hello = buildAnnotatedString {
                append("Hello $callName! 👋\nI am ")
                withStyle(SpanStyle(color = CyCyan, fontWeight = FontWeight.Bold)) { append("Divya…") }
                append("\nAapka har kaam,\nab aur bhi aasan! ❤️")
            }
            val bShape = RoundedCornerShape(16.dp)
            Column(Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 2.dp).width(W * 0.50f)) {
                Box(Modifier.fillMaxWidth().clip(bShape).background(CyDeep).border(1.4.dp, CyEdge, bShape).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(hello, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
                }
            }
            Text(if (status == "Tap to Talk") "Tap the crown to talk" else status, color = Color(0xFF9FE9F0), fontSize = 10.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 6.dp).width(W * 0.40f))

            // ---- bottom: left tiles | control center | right tiles ----
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(H * 0.40f).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Column(Modifier.weight(0.30f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(5.dp), ) {
                    Spacer(Modifier.weight(1.4f))
                    CyTile(Icons.Filled.Chat, "Chat with AI", false, Modifier.fillMaxWidth().weight(1f)) { go(Screen.Chat) }
                    CyTile(Icons.Filled.Search, "Search Anything", false, Modifier.fillMaxWidth().weight(1f)) { go(Screen.Chat) }
                    CyTile(Icons.Filled.Star, "Smart Features", false, Modifier.fillMaxWidth().weight(1f)) { go(Screen.Settings) }
                }
                val cShape = RoundedCornerShape(8.dp)
                Column(Modifier.weight(0.38f).fillMaxHeight().clip(cShape).background(CyDeep.copy(alpha = 0.85f)).border(1.dp, CyCyan.copy(alpha = 0.7f), cShape).padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("DIVYA CONTROL CENTER", color = CyCyan, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    val cells = listOf(
                        Triple(Icons.Filled.SmartToy, "AUTOMATION", Screen.Automate), Triple(Icons.Filled.Terminal, "TERMUX", Screen.Termux),
                        Triple(Icons.Filled.QrCodeScanner, "SYSTEM SCAN", Screen.Scan), Triple(Icons.Filled.Apps, "APP LAUNCHER", Screen.Apps),
                        Triple(Icons.Filled.Mic, "WAKE WORDS", Screen.WakeWords), Triple(Icons.Filled.VpnKey, "MANAGE API", Screen.ManageApi))
                    for (r in 0 until 3) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (c in 0 until 2) { val (ic, t, s) = cells[r * 2 + c]; CyCell(ic, t, Modifier.weight(1f).fillMaxHeight()) { go(s) } }
                    }
                }
                Column(Modifier.weight(0.32f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    CyTile(Icons.Filled.QrCodeScanner, "Scanner", true, Modifier.fillMaxWidth().weight(1f)) { go(Screen.Scan) }
                    CyTile(Icons.Filled.PhoneAndroid, "Control Mobile", false, Modifier.fillMaxWidth().weight(1f)) { go(Screen.Phone) }
                    CyTile(Icons.Filled.SmartToy, "AI Tools", false, Modifier.fillMaxWidth().weight(1f)) { go(Screen.Automate) }
                    CyTile(Icons.Filled.GraphicEq, "Voice Changer", false, Modifier.fillMaxWidth().weight(1f)) { go(Screen.VoiceSettings) }
                    CyTile(Icons.Filled.FlashOn, "Quick Actions", false, Modifier.fillMaxWidth().weight(1f)) { go(Screen.Quick) }
                }
            }
        }

        // ---- bottom nav ----
        val nShape = RoundedCornerShape(30.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp).height(58.dp).clip(nShape).background(CyDeep).border(1.4.dp, CyEdge, nShape),
            verticalAlignment = Alignment.CenterVertically) {
            @Composable fun navItem(ic: ImageVector, label: String, sel: Boolean, onClick: () -> Unit) {
                Column(Modifier.weight(1f).clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(ic, label, tint = if (sel) CyCyan else Color(0xFF8FA9AE), modifier = Modifier.size(22.dp))
                    Text(label, color = if (sel) CyCyan else Color(0xFF8FA9AE), fontSize = 10.sp)
                }
            }
            navItem(Icons.Filled.Home, "Home", true) {}
            navItem(Icons.Filled.History, "History", false) { go(Screen.Chat) }
            Box(Modifier.size(52.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF0B3A4A), CyDeep)))
                .border(2.5.dp, CyEdge, CircleShape).clickable { onMic() }, contentAlignment = Alignment.Center) {
                if (live) Icon(Icons.Filled.Stop, "Stop", tint = CyCyan, modifier = Modifier.size(28.dp)) else Text("👑", fontSize = 24.sp)
            }
            navItem(Icons.Filled.Apps, "Tools", false) { go(Screen.Quick) }
            navItem(Icons.Filled.Info, "About", false) { go(Screen.About) }
        }
    }
}
