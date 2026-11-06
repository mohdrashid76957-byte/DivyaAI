package com.divya.ai

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.concurrent.thread

/** Divya Kali Launcher: neon hex-frame icons, categories, search. Apps screen ka naya roop. */
private val KaliBlue = Color(0xFF2F8BFF)
private val KaliCyan = Color(0xFF38D6FF)

private class AppItem(val label: String, val pkg: String, val icon: Bitmap?, val cat: String)

private fun toBmp(d: Drawable): Bitmap? = try {
    if (d is BitmapDrawable && d.bitmap != null) d.bitmap else {
        val w = d.intrinsicWidth.coerceAtLeast(1).coerceAtMost(192); val h = d.intrinsicHeight.coerceAtLeast(1).coerceAtMost(192)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(b); d.setBounds(0, 0, w, h); d.draw(c); b
    }
} catch (_: Throwable) { null }

private fun category(pkg: String, label: String): String {
    val s = (pkg + " " + label).lowercase()
    return when {
        listOf("whatsapp", "telegram", "snapchat", "discord", "messag", "dialer", "contacts", "gmail", "mail", "signal", "facebook.orca", "phone", "sms").any { s.contains(it) } -> "Communication"
        listOf("youtube", "instagram", "facebook", "netflix", "spotify", "music", "video", "player", "capcut", "kinemaster", "vn", "hotstar", "gallery", "photos", "camera", "x.android", "twitter", "reel", "dailyhunt").any { s.contains(it) } -> "Entertainment"
        listOf("game", "pubg", "freefire", "ludo", "candy", "supercell", "roblox", "minecraft", "bgmi").any { s.contains(it) } -> "Games"
        else -> "Tools"
    }
}

@Composable
fun AppsScreen(th: UiTheme, ctx: Context, prefs: SharedPreferences, setTheme: (Int) -> Unit) {
    val pm = ctx.packageManager
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    LaunchedEffect(Unit) {
        thread {
            val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { ri -> val l = ri.loadLabel(pm).toString(); val p = ri.activityInfo.packageName
                    AppItem(l, p, try { toBmp(ri.loadIcon(pm)) } catch (_: Throwable) { null }, category(p, l)) }
                .distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
            apps = list
        }
    }
    var kali by remember { mutableStateOf(prefs.getBoolean("kaliLauncher", true)) }
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("All Apps") }
    var msg by remember { mutableStateOf("") }
    val cats = listOf("All Apps", "Communication", "Entertainment", "Tools", "Games")
    val shown = apps.filter { (cat == "All Apps" || it.cat == cat) && (q.isBlank() || it.label.contains(q, true)) }
    fun launch(a: AppItem) {
        val l = pm.getLaunchIntentForPackage(a.pkg) ?: return
        l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); try { ctx.startActivity(l) } catch (_: Exception) {}
    }
    val kIdx = themes.indexOfFirst { it.name == "Kali Linux" }
    // v2.22.19: pehli baar Apps screen khulte hi Kali theme + wallpaper khud lag jaye (ek hi baar)
    LaunchedEffect(kIdx) {
        if (kIdx >= 0 && !prefs.getBoolean("kaliAuto_22_19", false)) {
            prefs.edit().putBoolean("kaliAuto_22_19", true).apply()
            setTheme(kIdx); msg = "Kali theme lag gayi ✅ wallpaper laga rahi hoon…"
            thread { val r = Wallpapers.setKali(ctx); android.os.Handler(android.os.Looper.getMainLooper()).post { msg = r } }
        }
    }

    Box(Modifier.fillMaxSize()) {
    // Kali wallpaper Apps screen ke peeche hamesha dikhe (theme koi bhi ho)
    if (kali && th.wall == null) Image(painterResource(R.drawable.divya_kali_wall), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // --- Options ---
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(kali, { kali = !kali; prefs.edit().putBoolean("kaliLauncher", kali).apply() }, { Text("🐉 Divya Kali Launcher", fontSize = 12.sp) })
            AssistChip({ if (kIdx >= 0) { setTheme(kIdx); msg = "Kali Linux theme lag gayi ✅" } }, { Text("🎨 Kali Theme", fontSize = 12.sp) })
            AssistChip({
                msg = "Wallpaper laga rahi hoon…"
                thread { val r = Wallpapers.setKali(ctx); android.os.Handler(android.os.Looper.getMainLooper()).post { msg = r } }
            }, { Text("🖼 Kali Wallpaper", fontSize = 12.sp) })
            AssistChip({ msg = Wallpapers.openChooser(ctx) }, { Text("📲 Wallpaper (manual)", fontSize = 12.sp) })
            AssistChip({ msg = HomeRole.request(ctx) }, { Text("🏠 Phone Home banao", fontSize = 12.sp) })
        }
        if (msg.isNotEmpty()) Text(msg, color = KaliCyan, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        if (kali) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(top = 6.dp), placeholder = { Text("Search apps…", color = KaliCyan.copy(alpha = 0.7f)) },
                singleLine = true, shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = KaliCyan, unfocusedBorderColor = KaliBlue))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEach { c ->
                    Text(c, color = if (c == cat) Color.White else KaliCyan, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (c == cat) KaliBlue.copy(alpha = 0.35f) else Color.Transparent)
                            .border(1.5.dp, if (c == cat) KaliCyan else Color.Transparent, RoundedCornerShape(50)).clickable { cat = c }.padding(horizontal = 14.dp, vertical = 6.dp))
                }
            }
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(shown, key = { it.pkg }) { a ->
                    Column(Modifier.clickable { launch(a) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(62.dp).clip(CutCornerShape(14.dp)).background(Brush.verticalGradient(listOf(Color(0xFF020817), Color(0xFF06214F))))
                            .border(2.dp, Brush.linearGradient(listOf(KaliCyan, KaliBlue, KaliCyan)), CutCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                            if (a.icon != null) Image(a.icon.asImageBitmap(), a.label, Modifier.size(40.dp))
                            else Text(a.label.take(1).uppercase(), color = KaliCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(a.label, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        } else {
            Text("Open Apps", color = th.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            LazyColumn { items(apps) { a ->
                Text(a.label, color = th.text, modifier = Modifier.fillMaxWidth().clickable { launch(a) }.padding(vertical = 12.dp))
                HorizontalDivider(color = th.accent.copy(alpha = 0.3f))
            } }
        }
    }
    }
}
