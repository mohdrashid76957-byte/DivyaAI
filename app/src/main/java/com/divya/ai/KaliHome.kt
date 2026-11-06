package com.divya.ai

import android.app.Activity
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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

/**
 * v2.22.22: Divya Kali HOME. Ye asli phone ka launcher hai (HOME activity), sirf app ke andar ki screen nahi.
 * Default Home app banate hi phone ke Home par wahi Kali wallpaper + neon hex icons + categories + search + dock aata hai.
 * Back/Home dabane par bhi yahi dikhta hai (kyunki ab ye phone ka Home hai).
 * Limit: Android kisi normal app ko dusre launcher (vivo) ke icons badalne nahi deta; isiliye Divya khud launcher banti hai.
 */
private val KBlue = Color(0xFF2F8BFF)
private val KCyan = Color(0xFF38D6FF)

private class HApp(val label: String, val pkg: String, val icon: Bitmap?, val cat: String)

private fun hBmp(d: Drawable): Bitmap? = try {
    if (d is BitmapDrawable && d.bitmap != null) d.bitmap else {
        val w = d.intrinsicWidth.coerceAtLeast(1).coerceAtMost(192); val h = d.intrinsicHeight.coerceAtLeast(1).coerceAtMost(192)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(b); d.setBounds(0, 0, w, h); d.draw(c); b
    }
} catch (_: Throwable) { null }

private fun hCat(pkg: String, label: String): String {
    val s = (pkg + " " + label).lowercase()
    return when {
        listOf("whatsapp", "telegram", "snapchat", "discord", "messag", "dialer", "contacts", "gmail", "mail", "signal", "phone", "sms", "mms").any { s.contains(it) } -> "Communication"
        listOf("youtube", "instagram", "facebook", "netflix", "spotify", "music", "video", "player", "capcut", "kinemaster", "vn", "hotstar", "gallery", "photos", "camera", "twitter", "reel", "dailyhunt", "albums").any { s.contains(it) } -> "Entertainment"
        listOf("game", "pubg", "freefire", "free fire", "ludo", "candy", "supercell", "roblox", "minecraft", "bgmi").any { s.contains(it) } -> "Games"
        else -> "Tools"
    }
}

private fun findApp(apps: List<HApp>, labels: List<String>, pkgBits: List<String>): HApp? =
    apps.firstOrNull { a -> labels.any { a.label.equals(it, true) } }
        ?: apps.firstOrNull { a -> pkgBits.any { a.pkg.contains(it, true) } }

/** Context se Activity nikalo (Compose mein ctx kabhi ContextWrapper hota hai). */
fun hostActivity(c: Context): Activity? {
    var x: Context? = c
    while (x is ContextWrapper) { if (x is Activity) return x; x = x.baseContext }
    return null
}

object HomeRole {
    fun isDefault(c: Context): Boolean = try {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = c.packageManager.resolveActivity(i, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        ri?.activityInfo?.packageName == c.packageName
    } catch (_: Throwable) { false }

    /** Divya ko phone ka default Home banane ka system dialog kholo (Android 10+), nahi to Home settings. */
    fun request(c: Context): String = try {
        val act = hostActivity(c)
        if (isDefault(c)) "Divya Kali Home pehle se default hai ✅"
        else if (Build.VERSION.SDK_INT >= 29 && act != null) {
            val rm = c.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                act.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 7701)
                "Dialog mein \"Divya AI (Kali Home)\" chuno aur OK dabao."
            } else { openHomeSettings(c) }
        } else openHomeSettings(c)
    } catch (e: Throwable) { openHomeSettings(c) }

    private fun openHomeSettings(c: Context): String = try {
        c.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Settings khul gayi: Home app mein \"Divya AI (Kali Home)\" chuno."
    } catch (e: Throwable) {
        try {
            c.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Settings > Apps > Default apps > Home app mein \"Divya AI (Kali Home)\" chuno."
        } catch (e2: Throwable) { "Settings nahi khuli: " + (e2.message ?: "") }
    }
}

class KaliHomeActivity : ComponentActivity() {
    private var resetTick by mutableStateOf(0)

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { MaterialTheme { KaliHomeScreen(this, resetTick) } }
    }

    /** Home button dubara dabne par search/tab reset ho jaye. */
    override fun onNewIntent(i: Intent) { super.onNewIntent(i); setIntent(i); resetTick++ }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KaliHomeScreen(ctx: Context, resetTick: Int) {
    val pm = ctx.packageManager
    var apps by remember { mutableStateOf<List<HApp>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("All Apps") }
    var menu by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var pressed by remember { mutableStateOf<HApp?>(null) }
    var reload by remember { mutableStateOf(0) }
    val cats = listOf("All Apps", "Communication", "Entertainment", "Tools", "Games")

    LaunchedEffect(resetTick) { q = ""; cat = "All Apps"; menu = false; pressed = null }

    // App install/uninstall par list khud refresh
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { reload++ } }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }
        try { ctx.registerReceiver(r, f) } catch (_: Throwable) {}
        onDispose { try { ctx.unregisterReceiver(r) } catch (_: Throwable) {} }
    }
    LaunchedEffect(reload) {
        thread {
            val list = try {
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .map { ri ->
                        val l = ri.loadLabel(pm).toString(); val p = ri.activityInfo.packageName
                        HApp(l, p, try { hBmp(ri.loadIcon(pm)) } catch (_: Throwable) { null }, hCat(p, l))
                    }
                    .distinctBy { it.pkg }
                    .sortedWith(compareBy<HApp>({ if (it.pkg == ctx.packageName) 0 else 1 }, { it.label.lowercase() }))
            } catch (_: Throwable) { emptyList() }
            android.os.Handler(android.os.Looper.getMainLooper()).post { apps = list }
        }
    }

    fun launch(a: HApp) {
        val l = pm.getLaunchIntentForPackage(a.pkg) ?: return
        l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); try { ctx.startActivity(l) } catch (_: Exception) {}
    }

    // Dock: Phone, Messages, Divya (beech mein), Camera, Gallery
    val dock = remember(apps) {
        listOf(
            findApp(apps, listOf("Phone", "Dialer"), listOf("dialer", "incallui")),
            findApp(apps, listOf("Messages", "Messaging", "Message"), listOf("mms", "messag")),
            apps.firstOrNull { it.pkg == ctx.packageName },
            findApp(apps, listOf("Camera"), listOf("camera")),
            findApp(apps, listOf("Gallery", "Albums", "Photos"), listOf("gallery", "photos"))
        )
    }
    val shown = apps.filter { (cat == "All Apps" || it.cat == cat) && (q.isBlank() || it.label.contains(q, true)) }

    BackHandler(true) { if (q.isNotEmpty() || cat != "All Apps") { q = ""; cat = "All Apps" } }

    Box(Modifier.fillMaxSize().background(Color(0xFF01040C))) {
        Image(painterResource(R.drawable.divya_kali_wall), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 12.dp)) {
            // Search bar + settings
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(26.dp)).background(Color(0xCC020817))
                        .border(2.dp, Brush.linearGradient(listOf(KCyan, KBlue, KCyan)), RoundedCornerShape(26.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Search, null, tint = KCyan, modifier = Modifier.size(24.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        q, { q = it }, singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = Brush.verticalGradient(listOf(KCyan, KCyan)),
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                        decorationBox = { inner ->
                            Box { if (q.isEmpty()) Text("Search apps...", color = KCyan, fontSize = 16.sp); inner() }
                        }
                    )
                }
                Box {
                    Icon(Icons.Filled.Settings, "Menu", tint = KCyan, modifier = Modifier.padding(start = 10.dp).size(40.dp).clickable { menu = true })
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("🐉 Divya AI kholo") }, { menu = false; apps.firstOrNull { it.pkg == ctx.packageName }?.let { launch(it) } })
                        DropdownMenuItem({ Text("🖼 Kali wallpaper lagao") }, {
                            menu = false; note = "Wallpaper laga rahi hoon…"
                            thread { val r = Wallpapers.setKali(ctx); android.os.Handler(android.os.Looper.getMainLooper()).post { note = r } }
                        })
                        DropdownMenuItem({ Text("🏠 Default Home chuno") }, { menu = false; note = HomeRole.request(ctx) })
                        DropdownMenuItem({ Text("⚙ Phone Settings") }, {
                            menu = false; try { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
                        })
                    }
                }
            }
            // Categories
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEach { c ->
                    Text(c, color = if (c == cat) Color.White else KCyan, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (c == cat) KBlue.copy(alpha = 0.35f) else Color.Transparent)
                            .border(1.5.dp, if (c == cat) KCyan else Color.Transparent, RoundedCornerShape(50)).clickable { cat = c }.padding(horizontal = 14.dp, vertical = 6.dp))
                }
            }
            if (note.isNotEmpty()) Text(note, color = KCyan, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
            // Icons grid: upar ~55%, neeche wallpaper art dikhe
            Box(Modifier.fillMaxWidth().weight(0.58f)) {
                LazyVerticalGrid(GridCells.Fixed(5), Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(shown, key = { it.pkg }) { a -> HIcon(a, { launch(a) }, { pressed = a }) }
                }
            }
            Box(Modifier.weight(0.30f))
            // Dock
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                dock.forEachIndexed { i, a ->
                    if (a != null) HIcon(a, { launch(a) }, { pressed = a }, big = true, label = false, center = i == 2)
                    else Box(Modifier.size(64.dp))
                }
            }
        }
        // Long press menu
        val pa = pressed
        if (pa != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { pressed = null },
                title = { Text(pa.label) },
                text = { Text(pa.pkg, fontSize = 12.sp) },
                confirmButton = {
                    androidx.compose.material3.TextButton({
                        pressed = null
                        try { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pa.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
                    }) { Text("App info") }
                },
                dismissButton = {
                    if (pa.pkg != ctx.packageName) androidx.compose.material3.TextButton({
                        pressed = null
                        try { ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pa.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
                    }) { Text("Uninstall") }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HIcon(a: HApp, onClick: () -> Unit, onLong: () -> Unit, big: Boolean = false, label: Boolean = true, center: Boolean = false) {
    val shape = CutCornerShape(if (big) 16.dp else 14.dp)
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLong), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(if (big) 64.dp else 58.dp)
                .shadow(10.dp, shape, ambientColor = KCyan, spotColor = KCyan)
                .clip(shape).background(Brush.verticalGradient(listOf(Color(0xFF020817), Color(0xFF06214F))))
                .border(if (center) 3.dp else 2.dp, Brush.linearGradient(listOf(KCyan, KBlue, KCyan)), shape),
            contentAlignment = Alignment.Center
        ) {
            if (a.icon != null) Image(a.icon.asImageBitmap(), a.label, Modifier.size(if (big) 42.dp else 38.dp))
            else Text(a.label.take(1).uppercase(), color = KCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        if (label) Text(a.label, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp))
    }
}
