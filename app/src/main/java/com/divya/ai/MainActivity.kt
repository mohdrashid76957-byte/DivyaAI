package com.divya.ai

import android.Manifest
import com.divya.ai.glow.GlowOverlay
import com.divya.ai.glow.GlowState
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

enum class Screen { Home, Chat, Apps, Quick, Automate, Phone, Settings, Perms, Journal, Study, Scan, About, Code, Preview, Guardian, Backup, ProChat, VoiceSettings, ManageApi, WakeWords, Termux, VideoStudio }

class UiTheme(
    val name: String, val img: Int, val circle: Boolean, val bg: Brush, val text: Color, val accent: Color,
    val greet: String, val menu: List<Triple<String, Screen, ImageVector>>, val hud: Boolean = false, val maya: Boolean = false, val neon: Boolean = false, val ice: Boolean = false, val cyber: Boolean = false, val wall: Int? = null
)

val themes = listOf(
    UiTheme("Neon Blue", R.drawable.divya_dark, true,
        Brush.verticalGradient(listOf(Color(0xFF020B2D), Color(0xFF0A2A7A))), Color.White, Color(0xFF29B6FF),
        "Hello Boss! 👋\nHow can I help you today?",
        listOf(Triple("Quick Actions", Screen.Quick, Icons.Filled.FlashOn), Triple("Open Apps", Screen.Apps, Icons.Filled.Apps),
            Triple("Phone Control", Screen.Phone, Icons.Filled.PhoneAndroid), Triple("More", Screen.Settings, Icons.Filled.Star))),
    UiTheme("Lavender Light", R.drawable.divya_light, true,
        Brush.verticalGradient(listOf(Color(0xFFE6DCFF), Color(0xFFF8F5FF))), Color(0xFF2B1B5E), Color(0xFF6C3CE9),
        "Hi! I'm Divya 😊\nLet's chat, talk or get things done!",
        listOf(Triple("Chat", Screen.Chat, Icons.Filled.Chat), Triple("Apps", Screen.Apps, Icons.Filled.Apps),
            Triple("Automate", Screen.Automate, Icons.Filled.FlashOn), Triple("Settings", Screen.Settings, Icons.Filled.Settings))),
    UiTheme("Purple Full", R.drawable.divya_full, false,
        Brush.verticalGradient(listOf(Color(0xFF0A0630), Color(0xFF3B1A8C))), Color.White, Color(0xFFB07CFF),
        "Hello Boss!\nI'm Divya,\nYour AI Assistant ❤️",
        listOf(Triple("Home", Screen.Home, Icons.Filled.Home), Triple("Chat", Screen.Chat, Icons.Filled.Chat),
            Triple("Automations", Screen.Automate, Icons.Filled.SmartToy), Triple("Settings", Screen.Settings, Icons.Filled.Settings))),
    // Reel jaisa: black HUD + green neon + full-body Divya + typing text
    UiTheme("Neon Green HUD", R.drawable.divya_full, false,
        Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF03110A))), Color.White, Color(0xFF00FF40),
        "Boss, dil karda hai tere naal gall karan da...",
        listOf(Triple("APPS", Screen.Apps, Icons.Filled.Apps), Triple("THEMES", Screen.Settings, Icons.Filled.Palette),
            Triple("CHAT", Screen.Chat, Icons.Filled.Chat), Triple("TOOLS", Screen.Quick, Icons.Filled.Build)), hud = true),
    // Maya-style: light dashboard + floating avatar + cards + centre mic
    UiTheme("Maya Style", R.drawable.divya_full, false,
        Brush.verticalGradient(listOf(Color(0xFFCFE3FF), Color(0xFFF4F8FF))), Color(0xFF14233F), Color(0xFF1FB6E8),
        "",
        listOf(Triple("Home", Screen.Home, Icons.Filled.Home), Triple("Apps", Screen.Apps, Icons.Filled.Apps),
            Triple("Memories", Screen.Journal, Icons.Filled.Book), Triple("Chat", Screen.Chat, Icons.Filled.Chat)), maya = true),
    // Divya Neon: purple-blue neon + headphone avatar + 3x3 feature tiles (default)
    UiTheme("Divya Neon", R.drawable.divya_hero, false,
        Brush.verticalGradient(listOf(Color(0xFF05000F), Color(0xFF14003F), Color(0xFF05000F))), Color.White, Color(0xFFB07CFF),
        "", listOf(Triple("Home", Screen.Home, Icons.Filled.Home)), neon = true),
    UiTheme("Cyber Red", R.drawable.divya_dark, true,
        Brush.verticalGradient(listOf(Color(0xFF0B0000), Color(0xFF3A0010))), Color.White, Color(0xFFFF2D55),
        "Mission ready, Boss 🔥\nBolo, kya karna hai?",
        listOf(Triple("Quick", Screen.Quick, Icons.Filled.FlashOn), Triple("Apps", Screen.Apps, Icons.Filled.Apps),
            Triple("Chat", Screen.Chat, Icons.Filled.Chat), Triple("Settings", Screen.Settings, Icons.Filled.Settings))),
    UiTheme("Sunset Gold", R.drawable.divya_full, false,
        Brush.verticalGradient(listOf(Color(0xFF1E0A3C), Color(0xFF7A2E5D), Color(0xFFE0883A))), Color.White, Color(0xFFFFD166),
        "Namaste ji ✨\nMain aapki Divya hoon",
        listOf(Triple("Home", Screen.Home, Icons.Filled.Home), Triple("Chat", Screen.Chat, Icons.Filled.Chat),
            Triple("Apps", Screen.Apps, Icons.Filled.Apps), Triple("Settings", Screen.Settings, Icons.Filled.Settings))),
    UiTheme("AMOLED Black", R.drawable.divya_hero, true,
        Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF000000))), Color(0xFFEFEFEF), Color(0xFF00E5FF),
        "Ready.\nBolo Divya...",
        listOf(Triple("Home", Screen.Home, Icons.Filled.Home), Triple("Tools", Screen.Quick, Icons.Filled.Build),
            Triple("Chat", Screen.Chat, Icons.Filled.Chat), Triple("Settings", Screen.Settings, Icons.Filled.Settings))),
    UiTheme("Aurora Green", R.drawable.divya_light, true,
        Brush.verticalGradient(listOf(Color(0xFF00141F), Color(0xFF064E3B), Color(0xFF0B6E4F))), Color.White, Color(0xFF7CFFCB),
        "Hi! Aaj kaisa din hai? 🌿",
        listOf(Triple("Home", Screen.Home, Icons.Filled.Home), Triple("Automate", Screen.Automate, Icons.Filled.SmartToy),
            Triple("Phone", Screen.Phone, Icons.Filled.PhoneAndroid), Triple("Settings", Screen.Settings, Icons.Filled.Settings))),
    // Naye avatar designs (reference art se): sab Neon Home layout par, har ek ka apna avatar + rang
    UiTheme("3D Realistic", R.drawable.divya_av_3d, false,
        Brush.verticalGradient(listOf(Color(0xFF07001A), Color(0xFF1B0A4A), Color(0xFF07001A))), Color.White, Color(0xFFC77DFF),
        "", listOf(Triple("Home", Screen.Home, Icons.Filled.Home)), neon = true),
    UiTheme("Anime Neon", R.drawable.divya_av_anime, false,
        Brush.verticalGradient(listOf(Color(0xFF0A0030), Color(0xFF2A0A6B), Color(0xFF0A0030))), Color.White, Color(0xFFFF5CF0),
        "", listOf(Triple("Home", Screen.Home, Icons.Filled.Home)), neon = true),
    UiTheme("Blue Ice", R.drawable.divya_av_blue, false,
        Brush.verticalGradient(listOf(Color(0xFF020B2D), Color(0xFF0B3A8F), Color(0xFF020B2D))), Color.White, Color(0xFF4DB8FF),
        "", listOf(Triple("Home", Screen.Home, Icons.Filled.Home)), neon = true),
    UiTheme("Gold Premium", R.drawable.divya_av_gold, false,
        Brush.verticalGradient(listOf(Color(0xFF0A0600), Color(0xFF3A2400), Color(0xFF0A0600))), Color.White, Color(0xFFFFC83D),
        "", listOf(Triple("Home", Screen.Home, Icons.Filled.Home)), neon = true),
    UiTheme("Pink Cute", R.drawable.divya_av_pink, false,
        Brush.verticalGradient(listOf(Color(0xFF1A0010), Color(0xFF5A0A3C), Color(0xFF1A0010))), Color.White, Color(0xFFFF5CA8),
        "", listOf(Triple("Home", Screen.Home, Icons.Filled.Home)), neon = true),
    // Ice Light: halka (light) neon-glass theme, poori-height Divya model + dono taraf glass tiles (reference photo se)
    UiTheme("Ice Light", R.drawable.divya_ice, false,
        Brush.verticalGradient(listOf(Color(0xFFCFE4FF), Color(0xFFE6F1FF), Color(0xFFF6FAFF))), Color(0xFF0B1B3F), Color(0xFF1E90FF),
        "", listOf(Triple("Home", Screen.Home, Icons.Filled.Home)), ice = true),
    // Cyber HUD: dark navy + cyan neon, Core Systems gauges + Control Center (reference photo se)
    UiTheme("Cyber HUD", R.drawable.divya_ice, false,
        Brush.verticalGradient(listOf(Color(0xFF020B14), Color(0xFF041B2B), Color(0xFF020B14))), Color.White, Color(0xFF19E3F0),
        "", listOf(Triple("Home", Screen.Home, Icons.Filled.Home)), cyber = true),
    // Kali Linux: kala + electric neela neon, dragon/hacker-girl wallpaper (Divya Kali theme)
    UiTheme("Kali Linux", R.drawable.divya_av_blue, false,
        Brush.verticalGradient(listOf(Color(0xFF000208), Color(0xFF020B2D), Color(0xFF000208))), Color.White, Color(0xFF2F8BFF),
        "", listOf(Triple("Home", Screen.Home, Icons.Filled.Home)), neon = true, wall = R.drawable.divya_kali_wall)
)

class MainActivity : ComponentActivity() {
    private var lastAuthAt = 0L
    private var authBusy = false
    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        authBusy = false
        if (r.resultCode == RESULT_OK) lastAuthAt = System.currentTimeMillis() else finish()
    }

    /** App lock / Face lock: phone ka apna PIN, fingerprint ya face (Settings mein "app lock on" bolo). */
    override fun onResume() {
        super.onResume()
        if (getSharedPreferences("divya", 0).getBoolean("appLock", false) && !authBusy && System.currentTimeMillis() - lastAuthAt > 30_000L) {
            val km = getSystemService(android.app.KeyguardManager::class.java)
            if (km != null && km.isDeviceSecure) {
                val i = km.createConfirmDeviceCredentialIntent("Divya AI", "Phone ke PIN, fingerprint ya face se kholo")
                if (i != null) { authBusy = true; authLauncher.launch(i) }
            }
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        CrashLog.install(this)
        if (intent?.getBooleanExtra("wake", false) == true) WakeBus.req = true
        val p = getSharedPreferences("divya", 0)
        if (LicenseGate.isActive(this) && p.getBoolean("wake", false) && micOk(this)) WakeService.start(this)
        // Full-screen: status/navigation bar ke peeche bhi draw hone do, taaki RGB border poore screen par aaye
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MaterialTheme {
                var crash by remember { mutableStateOf(CrashLog.read(this)) }
                if (crash != null) {
                    val cm = getSystemService(android.content.ClipboardManager::class.java)
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { CrashLog.clear(this); crash = null },
                        title = { Text("Pichli baar app gir gayi thi") },
                        text = { Text(crash!!.take(1500), fontSize = 11.sp, modifier = Modifier.verticalScroll(rememberScrollState())) },
                        confirmButton = { TextButton({ cm?.setPrimaryClip(android.content.ClipData.newPlainText("crash", crash)); Toast.makeText(this, "Copy ho gaya - Claude ko bhejo", Toast.LENGTH_LONG).show() }) { Text("Copy") } },
                        dismissButton = { TextButton({ CrashLog.clear(this); crash = null }) { Text("Band karo") } }
                    )
                }
                var unlocked by remember { mutableStateOf(LicenseGate.isActive(this)) }
                if (unlocked) DivyaApp(this)
                else Box(Modifier.fillMaxSize().background(Color(0xFF020611))) {
                    LicenseScreen {
                        unlocked = true
                        if (getSharedPreferences("divya", 0).getBoolean("wake", false) && micOk(this@MainActivity)) WakeService.start(this@MainActivity)
                    }
                }
            }
        }
    }
    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i); setIntent(i)
        if (i.getBooleanExtra("wake", false)) WakeBus.req = true
    }
}

fun a11yOn(c: Context) = (Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").contains(c.packageName)
fun micOk(c: Context) = c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

@Composable
fun DivyaApp(ctx: Context) {
    val prefs = remember { ctx.getSharedPreferences("divya", 0) }
    // Ek baar: purana default model (2.5 native-audio, dheema) -> naya low-latency gemini-3.8-live. Apna custom model ho to chhedta nahi.
    if (!prefs.getBoolean("modelV38", false)) {
        val cur = prefs.getString("model", null)
        val ed = prefs.edit().putBoolean("modelV38", true)
        if (cur == null || cur == "gemini-2.5-flash-native-audio-preview-09-2025") ed.putString("model", "gemini-3.8-live")
        ed.apply()
    }
    // Ek baar: purani saved "glow OFF" value ko ON kar do (v2.0 se glow default ON hai)
    if (!prefs.getBoolean("glowV2", false)) prefs.edit().putBoolean("glow", true).putBoolean("glowV2", true).apply()
    var themeIdx by remember { mutableStateOf(prefs.getInt("theme2", themes.lastIndex).coerceIn(0, themes.lastIndex)) }
    var screen by remember { mutableStateOf(Screen.Home) }
    var status by remember { mutableStateOf("Tap to Talk") }
    val msgs = remember { mutableStateListOf<Pair<String, String>>() }
    val live = remember {
        GeminiLive(ctx, { status = it; if (it.startsWith("Error") || it.startsWith("Closed")) msgs.add("Divya" to "⚠️ $it") }, { t, n ->
            if (n || msgs.lastOrNull()?.first != "Divya") msgs.add("Divya" to t)
            else msgs[msgs.lastIndex] = "Divya" to (msgs.last().second + t)
        }, { h ->
            if (msgs.lastOrNull()?.first == "🎤") msgs[msgs.lastIndex] = "🎤" to (msgs.last().second + h)
            else msgs.add("🎤" to h)
        }, {
            // Divya ne "camera kholo / dekho" bola -> Scanner screen kholo
            screen = Screen.Scan
            try { (DivyaService.instance ?: ctx).startActivity(Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) } catch (_: Exception) {}
        })
    }
    val off = remember { OfflineBrain(ctx, { st -> status = st }, { w, t -> msgs.add(w to t); AvatarBus.say(t) }) }
    SideEffect { com.divya.ai.glow.HudOverlay.onTap = null }   // v2.22.9: ring ab touch nahi leta, tap se Divya band nahi hoti
    val th = themes[themeIdx]
    val lightBars = th.name == "Lavender Light"
    SideEffect {
        (ctx as? android.app.Activity)?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).apply {
                isAppearanceLightStatusBars = lightBars; isAppearanceLightNavigationBars = lightBars
            }
        }
    }
    fun key() = prefs.getString("key", "") ?: ""
    fun model() = prefs.getString("model", "gemini-3.8-live")!!
    fun voice() = prefs.getString("voice", "Aoede")!!

    fun toggle() {
        if (off.active) { off.stop(); return }
        if (!live.running && (!Net.online(ctx) || prefs.getBoolean("offlineOnly", false))) { off.start(); return }
        if (key().isBlank()) { status = "Settings mein Gemini API key daalo"; screen = Screen.Settings; return }
        if (live.running) { if (live.micOn) live.stop() else live.enableMic(); return }
        live.gfMode = prefs.getBoolean("gf", true); live.callName = prefs.getString("call", "Sir") ?: "Sir"
        live.idleStopMs = 0L   // wake-word wala 45 sec auto-stop sirf wake session ke liye, manual tap par nahi
        live.start(key(), model(), voice(), true)
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) toggle() }
    val tapMic = { if (micOk(ctx)) toggle() else micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
    fun sendChat(t: String) {
        // "image bana do" jaisi baat Pro Chat mein jaati hai (wahi image banakar dikhati hai)
        if (ImageGen.isRequest(t)) { ProBus.pending = t; screen = Screen.ProChat; return }
        msgs.add("You" to t)
        val offlineNow = !Net.online(ctx) || prefs.getBoolean("offlineOnly", false)
        if (live.running) live.sendText(t)
        else if (!offlineNow && LlmRouter.ready(prefs)) {
            LlmRouter.ask(prefs, msgs.toList(), prefs.getString("call", "Sir") ?: "Sir") { r -> msgs.add("Divya" to r) }
        }
        else if (offlineNow || key().isBlank()) {
            // Internet/API key nahi: Offline smart brain typed sawaal ka jawab deti hai
            off.askText(t) { r -> msgs.add("Divya" to r) }
        }
        else { live.gfMode = prefs.getBoolean("gf", true); live.callName = prefs.getString("call", "Sir") ?: "Sir"; live.start(key(), model(), voice(), false, t) }
    }

    // "Wake up Divya" bolne par WakeService yahan aata hai -> session turant shuru
    LaunchedEffect(WakeBus.req) {
        if (!WakeBus.req) return@LaunchedEffect
        WakeBus.req = false
        if (!live.running && !off.active && micOk(ctx) && (!Net.online(ctx) || prefs.getBoolean("offlineOnly", false))) { off.start(); WakeBus.liveActive = off.active; return@LaunchedEffect }
        if (!live.running && key().isNotBlank() && micOk(ctx)) {
            live.gfMode = prefs.getBoolean("gf", true); live.callName = prefs.getString("call", "Sir") ?: "Sir"
            live.idleStopMs = 45000
            live.start(key(), model(), voice(), true, "(Wake word suna) Sirf ek chhota sa 'Ji?' bolo aur meri baat suno.")
        } else WakeBus.liveActive = live.running
    }

    BackHandler(screen != Screen.Home) { screen = Screen.Home }
    // Home par Back dabane par agar Divya chal rahi hai to app band mat karo, background mein bhej do (session + glow chalte rahein)
    BackHandler(screen == Screen.Home) {
        val act = ctx as? android.app.Activity
        if (live.running || off.active) act?.moveTaskToBack(true) else act?.finish()
    }
    // App sach mein band ho (swipe/finish) to session aur glow saaf band karo, leak na ho
    DisposableEffect(Unit) { onDispose { if ((ctx as? android.app.Activity)?.isFinishing == true) { live.stop(); off.release() } } }
    // Divya ne voice se "preview dikhao" bola -> Preview screen
    // Divya ne voice se video banayi -> Video Gen screen
    LaunchedEffect(VideoBus.openTick) { if (VideoBus.openTick > 0) screen = Screen.VideoStudio }
    LaunchedEffect(CodeBus.previewTick) { if (CodeBus.previewTick > 0 && CodeBus.previewProject != null) screen = Screen.Preview }

    // System-wide neon glow (sab apps ke upar) jab Divya sun rahi ho
    val glowOn = remember(screen) { prefs.getBoolean("glow", true) }
    val micNow = live.micOn || off.active
    val runningNow = live.running
    LaunchedEffect(status, micNow, runningNow, glowOn) {
        val svc: android.content.Context = DivyaService.instance ?: ctx   // v2.22.8: accessibility ke bina bhi (overlay permission se)
        when {
            micNow -> GlowOverlay.show(svc, GlowState.LISTENING)
            runningNow -> GlowOverlay.show(svc, GlowState.SPEAKING)
            else -> GlowOverlay.hide()
        }
    }
    Box(Modifier.fillMaxSize().background(th.bg)) {
    if (th.wall != null) Image(painterResource(th.wall), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Box(Modifier.weight(1f)) {
            // Screen badalne par smooth fade + slide
            AnimatedContent(
                targetState = screen,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 20 }) togetherWith fadeOut(tween(160))
                },
                label = "screen"
            ) { s -> when (s) {
                Screen.Home -> if (th.cyber) CyberHome(th, status, micNow, prefs.getString("call", "Sir") ?: "Sir", tapMic) { screen = it }
                    else if (th.ice) IceHome(th, status, micNow, prefs.getString("call", "Sir") ?: "Sir", tapMic) { screen = it }
                    else if (th.neon) NeonHome(th, status, micNow, prefs.getString("call", "Sir") ?: "Sir", tapMic, {
                    // Avatar par tap: agla avatar design (Neon, 3D, Anime, Blue, Gold, Pink)
                    val n = themes.indices.filter { themes[it].neon }
                    val next = n[(n.indexOf(themeIdx) + 1) % n.size]
                    themeIdx = next; prefs.edit().putInt("theme2", next).apply()
                    android.widget.Toast.makeText(ctx, "Avatar: " + themes[next].name, android.widget.Toast.LENGTH_SHORT).show()
                }) { screen = it }
                    else if (th.maya) HomeMaya(th, status, micNow, prefs, { screen = it }) { t -> sendChat(t); screen = Screen.Chat }
                    else Home(th, status, micNow, tapMic) { screen = Screen.Chat }
                Screen.Chat -> ChatScreen(th, msgs, !(!Net.online(ctx) || prefs.getBoolean("offlineOnly", false)) && key().isNotBlank(), { sendChat(it) }) { screen = Screen.ProChat }
                Screen.ProChat -> ProChatScreen(th) { screen = Screen.Code }
                Screen.Journal -> JournalScreen(th, prefs)
                Screen.Study -> StudyScreen(th)
                Screen.Apps -> AppsScreen(th, ctx, prefs, { themeIdx = it; prefs.edit().putInt("theme2", it).apply() })
                Screen.Quick -> QuickScreen(th, ctx, { screen = Screen.Scan }, { screen = Screen.Apps }, { screen = Screen.VideoStudio }) { screen = Screen.Code }
                Screen.Code -> CodeScreen(th, ctx) { CodeBus.previewProject = it; CodeBus.previewTick++; screen = Screen.Preview }
                Screen.Preview -> PreviewScreen(th, ctx, CodeBus.previewProject ?: "", CodeBus.previewTick) { screen = Screen.Code }
                Screen.Scan -> ScanScreen(th, live, live.running, status, { if (!live.running) tapMic() }, { t -> sendChat(t) }) { screen = Screen.Home }
                Screen.Automate -> AutomateScreen(th)
                Screen.Phone -> PhoneScreen(th, ctx) { screen = Screen.Perms }
                Screen.Guardian -> GuardianScreen(th, prefs, ctx)
                Screen.Settings -> SettingsScreen(th, prefs, themeIdx, { themeIdx = it; prefs.edit().putInt("theme2", it).apply() }, { screen = Screen.Guardian }, { screen = Screen.Backup }, { screen = it }) { screen = Screen.Perms }
                Screen.Perms -> PermsScreen(th, ctx)
                Screen.About -> AboutScreen(th)
                Screen.VoiceSettings -> VoiceSettingsScreen(th, prefs)
                Screen.ManageApi -> ManageApiScreen(th, prefs)
                Screen.WakeWords -> WakeWordsScreen(th, prefs)
                Screen.Termux -> TermuxScreen(th, prefs)
                Screen.VideoStudio -> VideoGenScreen(th, ctx)
                Screen.Backup -> BackupScreen(th, ctx)
            } }
        }
        if (th.ice) NeonNav(screen, micNow, tapMic, th.accent, true) { screen = it }
        else if (th.neon) NeonNav(screen, micNow, tapMic) { screen = it }
        else if (th.maya) MayaNav(th, screen, micNow, tapMic) { screen = it }
        else Row(Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(22.dp))
            .background(th.accent.copy(alpha = 0.18f)).padding(vertical = 8.dp), Arrangement.SpaceEvenly) {
            th.menu.forEach { (label, target, icon) ->
                Column(Modifier.clickable { screen = target }.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, label, tint = if (screen == target) th.accent else th.text)
                    Text(label, color = th.text, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
    if (glowOn) GlowBorder(if (micNow) Color(0xFF00FF40) else th.accent, micNow)
    BgOverlay()
    // Live screen sharing ka chhota button (session chalu hone par dikhta hai). ON hone par Divya aapki screen dekhti rehti hai.
    if (live.running && screen != Screen.Scan && screen != Screen.Chat && screen != Screen.ProChat) {
        Box(Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(top = 4.dp, end = 10.dp)
            .clip(RoundedCornerShape(50)).background(if (live.screenShare) Color(0xFFE53935) else Color(0xAA000000))
            .clickable {
                val r = live.setScreenShare(!live.screenShare)
                if (r.startsWith("failed")) Toast.makeText(ctx, r.removePrefix("failed: "), Toast.LENGTH_LONG).show()
            }.padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(if (live.screenShare) "📺 Screen share ON" else "📺 Screen share", color = Color.White, fontSize = 11.sp)
        }
    }
    }
}

@Composable
fun GlowBorder(color: Color, active: Boolean) {
    val t = rememberInfiniteTransition(label = "glow")
    val a by t.animateFloat(0.6f, 1f, infiniteRepeatable(tween(if (active) 600 else 1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val hue by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (active) 2500 else 6000, easing = LinearEasing), RepeatMode.Restart), label = "h")
    val view = androidx.compose.ui.platform.LocalView.current
    // Phone ke asli screen corner ka radius (Android 12+), warna 40dp
    val devR = if (android.os.Build.VERSION.SDK_INT >= 31)
        view.rootWindowInsets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() else null
    // Canvas sirf draw karta hai, touch consume nahi karta -> buttons par asar nahi
    Canvas(Modifier.fillMaxSize()) {
        val r = devR ?: 40.dp.toPx()
        // purple, blue, cyan, green, pink ... lagataar badalte rang
        val cols = listOf(0f, 60f, 120f, 180f, 240f, 300f).map { Color.hsv((hue + it) % 360f, 0.85f, 1f) }
        val brush = Brush.sweepGradient(cols + cols.first(), center)
        for (i in 3 downTo 0) {
            val w = (1.5f + i * 2.5f).dp.toPx()
            drawRoundRect(brush, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(r), alpha = (a * (0.10f + (3 - i) * 0.30f)).coerceIn(0f, 1f), style = Stroke(w))
        }
    }
}

@Composable
fun Typewriter(text: String, color: Color, accent: Color) {
    var n by remember(text) { mutableStateOf(0) }
    LaunchedEffect(text) { n = 0; while (n < text.length) { kotlinx.coroutines.delay(70); n++ } }
    val blink by rememberInfiniteTransition(label = "cur").animateFloat(0f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "b")
    Text(text.take(n) + if (blink > 0.5f) "▌" else " ", color = color, fontSize = 22.sp, fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(accent, blurRadius = 18f)))
}

@Composable
fun Home(th: UiTheme, status: String, live: Boolean, onMic: () -> Unit, onSearch: () -> Unit = {}) {
    val ctx = LocalContext.current
    val hr = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val hello = when { hr < 5 -> "GOOD NIGHT"; hr < 12 -> "GOOD MORNING"; hr < 17 -> "GOOD AFTERNOON"; hr < 21 -> "GOOD EVENING"; else -> "GOOD NIGHT" }
    val date = remember { java.text.SimpleDateFormat("EEE, d MMMM", java.util.Locale.ENGLISH).format(java.util.Date()).uppercase() }
    val battery = remember { try { (ctx.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager).getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) } catch (e: Exception) { 0 } }
    val tr = rememberInfiniteTransition(label = "float")
    val amp = if (live) 2f else 1f
    val f by tr.animateFloat(-1f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "f")
    val sway by tr.animateFloat(-1f, 1f, infiniteRepeatable(tween(3800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    val breathe by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val anim = Modifier.graphicsLayer {
        translationY = f * 7.dp.toPx() * amp
        rotationZ = sway * 1.2f * amp
        val sc = 1f + breathe * 0.015f * amp
        scaleX = sc; scaleY = sc
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        Column(Modifier.fillMaxWidth()) {
            val ff = if (th.hud) androidx.compose.ui.text.font.FontFamily.Monospace else androidx.compose.ui.text.font.FontFamily.Default
            val ls = if (th.hud) 2.sp else 0.sp
            Text("Divya AI", color = th.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = ff, letterSpacing = ls)
            Text("$hello  •  $date  •  🔋$battery%", color = th.text.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = ff, letterSpacing = ls)
            Text("BOSS", color = th.text, fontSize = if (th.hud) 52.sp else 40.sp, fontWeight = FontWeight.Black, fontFamily = ff, letterSpacing = ls)
            Text("I'M ALWAYS WITH YOU", color = th.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = ff, letterSpacing = ls)
        }
        if (th.circle)
            Image(painterResource(th.img), "Divya", Modifier.then(anim).size(190.dp).clip(CircleShape).border(3.dp, th.accent, CircleShape), contentScale = ContentScale.Crop)
        else
            Image(painterResource(th.img), "Divya", Modifier.weight(1f).fillMaxWidth().then(anim), contentScale = ContentScale.Fit)
        if (th.hud) Typewriter(th.greet, th.text, th.accent)
        else Box(Modifier.clip(RoundedCornerShape(18.dp)).background(th.accent.copy(alpha = 0.2f)).border(1.dp, th.accent, RoundedCornerShape(18.dp)).padding(12.dp)) {
            Text(th.greet, color = th.text, textAlign = TextAlign.Center)
        }
        Box(Modifier.size(88.dp).clip(CircleShape).background(if (live) Color(0xFFE53935) else th.accent.copy(alpha = 0.3f))
            .border(3.dp, th.accent, CircleShape).clickable { onMic() }, contentAlignment = Alignment.Center) {
            Icon(if (live) Icons.Filled.Stop else Icons.Filled.Mic, "Talk", tint = Color.White, modifier = Modifier.size(42.dp))
        }
        Text(status, color = th.accent, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (th.hud) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF0B1A12))
            .border(1.dp, th.accent.copy(alpha = 0.5f), RoundedCornerShape(24.dp)).clickable { onSearch() }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, "Search", tint = th.accent)
            Spacer(Modifier.width(10.dp))
            Text("Search anything", color = th.text.copy(alpha = 0.5f), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
    }
}

@Composable
fun Page(th: UiTheme, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = th.text, fontSize = 26.sp, fontWeight = FontWeight.Bold); content()
    }
}

@Composable
fun Btn(th: UiTheme, label: String, onClick: () -> Unit) =
    Button(onClick, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = th.accent)) { Text(label, color = Color.White) }

@Composable
fun ChatScreen(th: UiTheme, msgs: List<Pair<String, String>>, online: Boolean, send: (String) -> Unit, openPro: () -> Unit = {}) {
    var t by remember { mutableStateOf("") }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    val mainH = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    // Photo/video/PDF bhejna: Pro Chat mein jata hai (wahi Gemini ko file dekhata hai)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            val note = t
            Thread {
                val list = ArrayList<PAtt>()
                uris.take(5).forEach { u -> try { list.add(loadAtt(ctx, u)) } catch (e: Exception) { mainH.post { Toast.makeText(ctx, e.message ?: "File nahi khuli", Toast.LENGTH_LONG).show() } } }
                if (list.isNotEmpty()) mainH.post { ProState.send(ctx, note.ifBlank { "Isko dekho aur batao ye kya hai." }, list); t = ""; openPro() }
            }.start()
        }
    }
    val ls = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(msgs.size, msgs.lastOrNull()?.second?.length ?: 0) { if (msgs.isNotEmpty()) ls.scrollToItem(msgs.lastIndex) }
    Column(Modifier.fillMaxSize().imePadding().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Divya Chat", color = th.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(if (online) "🟢 Online (Gemini Live)" else "🟠 Offline smart mode (bina internet)", color = th.text.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            IconButton({ clip.setText(androidx.compose.ui.text.AnnotatedString(msgs.joinToString("\n\n") { (if (it.first == "Divya") "Divya: " else "Aap: ") + it.second })); Toast.makeText(ctx, "Poori chat copy ho gayi", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Filled.ContentCopy, "Poori chat copy", tint = th.accent) }
            TextButton(openPro) { Text("🚀 Pro Chat", color = th.accent) }
        }
        LazyColumn(Modifier.weight(1f), state = ls, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(msgs) { (who, txt) ->
                val me = who != "Divya"
                Box(Modifier.fillMaxWidth(), contentAlignment = if (me) Alignment.CenterEnd else Alignment.CenterStart) {
                    Column(horizontalAlignment = if (me) Alignment.End else Alignment.Start) {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(txt, color = th.text, modifier = Modifier.clip(RoundedCornerShape(14.dp))
                                .background(th.accent.copy(alpha = if (me) 0.45f else 0.2f)).padding(10.dp))
                        }
                        Text("📋 Copy", color = th.text.copy(alpha = 0.6f), fontSize = 11.sp,
                            modifier = Modifier.clickable { clip.setText(androidx.compose.ui.text.AnnotatedString(txt)); Toast.makeText(ctx, "Copy ho gaya", Toast.LENGTH_SHORT).show() }.padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ pick.launch("*/*") }) { Icon(Icons.Filled.AttachFile, "Photo/Video bhejo", tint = th.accent) }
            OutlinedTextField(t, { t = it }, Modifier.weight(1f), placeholder = { Text("Divya se chat karo…") },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = th.text, unfocusedTextColor = th.text))
            IconButton({ if (t.isNotBlank()) { send(t.trim()); t = "" } }) { Icon(Icons.Filled.Send, "Send", tint = th.accent) }
        }
    }
}

@Composable
fun QuickScreen(th: UiTheme, ctx: Context, openScan: () -> Unit = {}, openApps: () -> Unit = {}, openVideo: () -> Unit = {}, openCode: () -> Unit = {}) = Page(th, "Quick Actions") {
    Btn(th, "🎬 Video Gen AI (topic -> voiceover video)") { openVideo() }
    Btn(th, "🐉 Kali Apps Launcher + Theme + Wallpaper") { openApps() }
    Btn(th, "💻 Code & Website Builder") { openCode() }
    Btn(th, "📷 Scanner (Divya ko dikhao)") { openScan() }
    listOf("Home" to "home", "Back" to "back", "Recent Apps" to "recents", "Notifications" to "notifications",
        "Quick Settings" to "quick_settings", "Screenshot" to "screenshot", "Lock Screen" to "lock").forEach { (l, a) ->
        Btn(th, l) {
            val s = DivyaService.instance
            if (s == null) Toast.makeText(ctx, "Pehle Accessibility permission ON karo", Toast.LENGTH_LONG).show() else s.global(a)
        }
    }
}

@Composable
fun AutomateScreen(th: UiTheme) = Page(th, "Automations") {
    Text("Mic dabake bolo, Divya khud kaam karegi:", color = th.text)
    listOf("“YouTube kholo”", "“Notifications dikhao”", "“Neeche scroll karo”", "“Search box mein 'lofi songs' likho”",
        "“Settings kholo aur Wi-Fi par tap karo”", "“Screenshot lo”", "“Phone lock kar do”").forEach {
        Text(it, color = th.text, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(th.accent.copy(alpha = 0.2f)).padding(12.dp))
    }
}

@Composable
fun PhoneScreen(th: UiTheme, ctx: Context, openPerms: () -> Unit) = Page(th, "Phone Control") {
    Text(if (a11yOn(ctx)) "✅ Accessibility ON — Divya phone control kar sakti hai" else "⚠️ Accessibility OFF — Divya phone control nahi kar paegi", color = th.text)
    Btn(th, "Permissions kholo", openPerms)
}

@Composable
fun SettingsScreen(th: UiTheme, prefs: android.content.SharedPreferences, themeIdx: Int, setTheme: (Int) -> Unit, openGuardian: () -> Unit, openBackup: () -> Unit, openExtra: (Screen) -> Unit, openPerms: () -> Unit) = Page(th, "Settings") {
    var key by remember { mutableStateOf(prefs.getString("key", "") ?: "") }
    var model by remember { mutableStateOf(prefs.getString("model", "gemini-3.8-live")!!) }
    var voice by remember { mutableStateOf(prefs.getString("voice", "Aoede")!!) }
    var callName by remember { mutableStateOf(prefs.getString("call", "Sir")!!) }
    var nlToken by remember { mutableStateOf(prefs.getString("netlifyToken", "") ?: "") }
    val homeOrig = remember { prefs.getString("homeAddr", "") ?: "" }
    var homeAddr by remember { mutableStateOf(homeOrig) }
    var saved by remember { mutableStateOf(false) }
    val colors = OutlinedTextFieldDefaults.colors(focusedTextColor = th.text, unfocusedTextColor = th.text)
    Text("Gemini API Key", color = th.accent, fontWeight = FontWeight.Bold)
    OutlinedTextField(key, { key = it; saved = false }, Modifier.fillMaxWidth(), label = { Text("API key") },
        visualTransformation = PasswordVisualTransformation(), singleLine = true, colors = colors)
    OutlinedTextField(model, { model = it; saved = false }, Modifier.fillMaxWidth(), label = { Text("Live voice model ID (sabse tez: gemini-3.8-live)") }, colors = colors)
    OutlinedTextField(voice, { voice = it; saved = false }, Modifier.fillMaxWidth(), label = { Text("Female voice (Aoede / Kore / Leda / Zephyr)") }, colors = colors)
    OutlinedTextField(callName, { callName = it; saved = false }, Modifier.fillMaxWidth(), label = { Text("Divya tumhe kya bulaye (Sir / Boss / naam)") }, colors = colors)
    OutlinedTextField(homeAddr, { homeAddr = it; saved = false }, Modifier.fillMaxWidth(), label = { Text("Ghar ka address (Divya rasta dikhane ke liye)") }, colors = colors)
    Text("Ya Divya se bolo: \"yahi mera ghar hai, save kar lo\" - tab sabse sahi nishaan (GPS) save hota hai.", color = th.text, fontSize = 12.sp)
    Text("Website publish (Maya jaisa, seconds mein live link)", color = th.accent, fontWeight = FontWeight.Bold)
    Text("app.netlify.com par free account > User settings > Applications > Personal access token banao aur yahan daalo. Phir bolo: \"Divya, mere liye portfolio website banao aur live karo\".", color = th.text, fontSize = 12.sp)
    OutlinedTextField(nlToken, { nlToken = it; saved = false }, Modifier.fillMaxWidth(), label = { Text("Netlify token") },
        visualTransformation = PasswordVisualTransformation(), singleLine = true, colors = colors)
    Btn(th, "Save") { prefs.edit().putString("netlifyToken", nlToken.trim()).putString("key", key.trim()).putString("model", model.trim()).putString("voice", voice.trim()).putString("call", callName.trim().ifBlank { "Sir" })
        .also { e -> e.putString("homeAddr", homeAddr.trim()); if (homeAddr.trim() != homeOrig) { e.remove("homeLat"); e.remove("homeLng") } }.apply(); saved = true }
    if (saved) Text("✅ Saved", color = th.accent)
    Btn(th, "Backup & restore (yaadein, chats)") { openBackup() }
    Btn(th, "🎙 Voice Settings (30 awaazein, pitch, speed, accent)") { openExtra(Screen.VoiceSettings) }
    Btn(th, "🔑 Manage API (Gemini, Claude, Grok, Groq…)") { openExtra(Screen.ManageApi) }
    Btn(th, "👂 Wake Words (apne wake word jodo)") { openExtra(Screen.WakeWords) }
    Btn(th, "💻 Termux Control (Divya khud commands chalaye, code likhe)") { openExtra(Screen.Termux) }
    AnnounceSettings(th, prefs)
    Text("UI Design / Avatar (3D, Anime, Blue, Gold, Pink, Neon…)", color = th.accent, fontWeight = FontWeight.Bold)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        themes.forEachIndexed { i, t -> FilterChip(themeIdx == i, { setTheme(i) }, { Text(t.name, fontSize = 11.sp) }) }
    }
    val sctx = LocalContext.current
    var gf by remember { mutableStateOf(prefs.getBoolean("gf", true)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Girlfriend mode (pyaar bhari, caring baatein)", color = th.text, modifier = Modifier.weight(1f))
        Switch(gf, { gf = it; prefs.edit().putBoolean("gf", it).apply() })
    }
    Text("Naya mode agle connection se lagta hai (mic ya stop karke dobara shuru karo).", color = th.text.copy(alpha = 0.6f), fontSize = 11.sp)
    var gl by remember { mutableStateOf(prefs.getBoolean("glow", true)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Glow light (screen ke kinare ki rangeen light)", color = th.text, modifier = Modifier.weight(1f))
        Switch(gl, { gl = it; prefs.edit().putBoolean("glow", it).apply() })
    }
    var hd by remember { mutableStateOf(prefs.getBoolean("hud", true)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Voice orb (Divya se baat karte waqt awaaz ke saath naachta gola + live caption)", color = th.text, modifier = Modifier.weight(1f))
        Switch(hd, { hd = it; prefs.edit().putBoolean("hud", it).apply(); if (!it) com.divya.ai.glow.HudOverlay.hide() })
    }
    var hdd by remember { mutableStateOf(prefs.getBoolean("hudDim", true)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Orb ke peeche halka andhera (orb chhota rehta hai, touch/Back neeche ke app ko hi jaata hai)", color = th.text, modifier = Modifier.weight(1f))
        Switch(hdd, { hdd = it; prefs.edit().putBoolean("hudDim", it).apply(); com.divya.ai.glow.HudOverlay.hide() })
    }
    var orbSt by remember { mutableStateOf(prefs.getString("orbStyle", "classic") ?: "classic") }
    Text("Orb style (agle session se lagta hai)", color = th.text, fontSize = 13.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("classic" to "Classic", "4dzine" to "4DZINE").forEach { (key, label) ->
            FilterChip(orbSt == key, {
                orbSt = key; prefs.edit().putString("orbStyle", key).apply(); com.divya.ai.glow.HudOverlay.hide()
            }, { Text(label, fontSize = 11.sp) })
        }
    }
    Text("Ring ke liye Accessibility ya 'Display over other apps' permission ON honi chahiye.", color = th.text.copy(alpha = 0.6f), fontSize = 11.sp)
    var fl by remember { mutableStateOf(prefs.getBoolean("float", false)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Floating Divya (home/apps ke upar avatar)", color = th.text, modifier = Modifier.weight(1f))
        Switch(fl, {
            fl = it; prefs.edit().putBoolean("float", it).apply()
            val sv = DivyaService.instance
            if (sv == null) Toast.makeText(sctx, "Pehle Accessibility permission ON karo", Toast.LENGTH_LONG).show()
            else if (it) com.divya.ai.glow.FloatingAvatar.show(sv) else com.divya.ai.glow.FloatingAvatar.hide()
        })
    }
    var wk by remember { mutableStateOf(prefs.getBoolean("wake", false)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("\"Wake up Divya\" bolne par jaag jaye (background mein mic sunta hai)", color = th.text, modifier = Modifier.weight(1f))
        Switch(wk, {
            if (it && !micOk(sctx)) { Toast.makeText(sctx, "Pehle Microphone permission do (Permissions)", Toast.LENGTH_LONG).show() }
            else { wk = it; prefs.edit().putBoolean("wake", it).apply(); if (it) WakeService.start(sctx) else WakeService.stop(sctx) }
        })
    }
    var nu by remember { mutableStateOf(prefs.getBoolean("needUnlock", true)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Safety: phone locked ho to Divya koi kaam na kare (sirf aap unlock karke chalao)", color = th.text, modifier = Modifier.weight(1f))
        Switch(nu, { nu = it; prefs.edit().putBoolean("needUnlock", it).apply() })
    }
    var oo by remember { mutableStateOf(prefs.getBoolean("offlineOnly", false)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Hamesha Offline mode (internet ho tab bhi phone par hi chalo, Gemini nahi)", color = th.text, modifier = Modifier.weight(1f))
        Switch(oo, { oo = it; prefs.edit().putBoolean("offlineOnly", it).apply() })
    }
    // ---------- Voice Lock (basic) ----------
    var vpOn by remember { mutableStateOf(prefs.getBoolean("voiceLock", false)) }
    var vpMsg by remember { mutableStateOf(if (VoicePrint.load(prefs) != null) "✅ Awaaz saved hai" else "Awaaz abhi record nahi hui") }
    var vpSens by remember { mutableStateOf(prefs.getFloat("vpSens", 1f)) }
    var vpBusy by remember { mutableStateOf(false) }
    Btn(th, "Voice Guardian dashboard kholo") { openGuardian() }
    Text("Voice Lock (basic, experimental)", color = th.accent, fontWeight = FontWeight.Bold)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Sirf meri awaaz par kaam kare", color = th.text, modifier = Modifier.weight(1f))
        Switch(vpOn, {
            vpOn = it; prefs.edit().putBoolean("voiceLock", it).apply()
            if (it && VoicePrint.load(prefs) == null) {
                vpMsg = "⚠️ Voice Lock ON hai, par awaaz record nahi hui. Neeche 'Meri awaaz record karo' dabao."
                Toast.makeText(sctx, "Voice Lock ON. Ab neeche apni awaaz record karo", Toast.LENGTH_LONG).show()
            }
        })
    }
    Btn(th, if (vpBusy) "Chal raha hai..." else "Meri awaaz record karo (20 sec)") {
        if (vpBusy) return@Btn
        if (!micOk(sctx)) { Toast.makeText(sctx, "Pehle Microphone permission do", Toast.LENGTH_LONG).show(); return@Btn }
        if (MicBus.busy()) { Toast.makeText(sctx, "Pehle Divya ka mic band karo (stop)", Toast.LENGTH_LONG).show(); return@Btn }
        vpBusy = true; vpMsg = "🎤 20 sec lagatar bolte raho (kuch bhi, normal awaaz mein)..."
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            WakeBus.liveActive = true
            val pcm = try { VoicePrint.record(20) } catch (e: Exception) { null }
            WakeBus.liveActive = false
            val md = pcm?.let { VoicePrint.train(VoicePrint.chunks(it)) }
            h.post {
                vpBusy = false
                if (md != null) { VoicePrint.save(prefs, md); prefs.edit().putBoolean("voiceLock", true).apply(); vpOn = true; vpMsg = "✅ Awaaz save ho gayi, Voice Lock ON (limit ${"%.1f".format(md.thr)}). Ab Test dabao." }
                else vpMsg = "❌ Kam awaaz mili. Shaant jagah par 20 sec lagatar bolo aur dobara try karo."
            }
        }.start()
    }
    Btn(th, "Test: 4 sec bolo, match check") {
        if (vpBusy) return@Btn
        val md = VoicePrint.load(prefs)
        if (md == null) { Toast.makeText(sctx, "Pehle awaaz record karo", Toast.LENGTH_LONG).show(); return@Btn }
        if (!micOk(sctx) || MicBus.busy()) { Toast.makeText(sctx, "Mic permission do / Divya ka mic band karo", Toast.LENGTH_LONG).show(); return@Btn }
        vpBusy = true; vpMsg = "🎤 4 sec bolo..."
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            WakeBus.liveActive = true
            val pcm = try { VoicePrint.record(4) } catch (e: Exception) { null }
            WakeBus.liveActive = false
            val fr = pcm?.let { VoicePrint.voicedFrames(it) } ?: emptyList()
            val sc = if (fr.size >= VoicePrint.MIN_VOICED) VoicePrint.matchScore(md, fr) else -1.0
            h.post {
                vpBusy = false
                val lim = VoicePrint.limit(md, prefs.getFloat("vpSens", 1f))
                vpMsg = if (sc < 0) "Awaaz bahut kam mili, thoda zor se bolo"
                    else "Score ${"%.1f".format(sc)} / limit ${"%.1f".format(lim)} → " + (if (sc <= lim) "✅ MATCH" else "❌ match nahi")
            }
        }.start()
    }
    Text(vpMsg, color = th.text)
    Text("Narmi: bada = aasani se maanegi, chhota = sakht. Pehle khud test karo, phir kisi aur se test karke limit set karo.", color = th.text.copy(alpha = 0.6f), fontSize = 11.sp)
    Slider(vpSens, { vpSens = it; prefs.edit().putFloat("vpSens", it).apply() }, valueRange = 0.6f..2.0f)
    Btn(th, "Permissions") { openPerms() }
}

@Composable
fun PermsScreen(th: UiTheme, ctx: Context) {
    var tick by remember { mutableStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    val micL = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val conL = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val notiL = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val callL = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val locL = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    fun go(i: Intent) = ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    key(tick) {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val rows = listOf(
            Triple("Accessibility (phone control + apps kholna)", a11yOn(ctx)) { go(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            Triple("Microphone (voice baat)", micOk(ctx)) { micL.launch(Manifest.permission.RECORD_AUDIO) },
            Triple("Display over other apps (background mein apps kholna)", Settings.canDrawOverlays(ctx)) {
                go(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))) },
            Triple("Contacts (WhatsApp par naam se message bhejne ke liye)", ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) { conL.launch(Manifest.permission.READ_CONTACTS) },
            Triple("Phone calls (call lagana, uthana, kaatna)",
                ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED &&
                ctx.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                callL.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.ANSWER_PHONE_CALLS)) },
            Triple("Location (Google Maps rasta, 'main kahan hoon', mausam)", PhoneTools.hasLoc(ctx)) {
                locL.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
            Triple("Notification access (Divya aapki notifications padh sake)", DivyaNotifListener.instance != null) { go(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            Triple("Notifications", Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= 33) notiL.launch(Manifest.permission.POST_NOTIFICATIONS) },
            Triple("Battery optimization OFF (background chalne ke liye)", pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
                go(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))) }
        )
        Page(th, "Permissions") {
            rows.forEach { (label, ok, act) ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(th.accent.copy(alpha = 0.18f)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = th.text, modifier = Modifier.weight(1f))
                    if (ok) Text("✅", fontSize = 20.sp) else Button(act, colors = ButtonDefaults.buttonColors(containerColor = th.accent)) { Text("Allow") }
                }
            }
            Text("Accessibility: Settings → Installed apps → Divya AI → ON karo.", color = th.text, fontSize = 12.sp)
        }
    }
}


// ===================== Maya-style upgrade =====================
val moods = listOf("Warm" to "All good", "Happy" to "Great day", "Calm" to "Relaxed", "Tired" to "Rest lo")

@Composable
fun MayaCard(th: UiTheme, icon: ImageVector, label: String, big: String, small: String, modifier: Modifier, onClick: () -> Unit = {}) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).clickable { onClick() }.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, label, tint = th.accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = th.text.copy(alpha = 0.6f), fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(big, color = th.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(small, color = th.text.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

@Composable
fun MayaChip(th: UiTheme, icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(24.dp)).background(Color.White).clickable { onClick() }.padding(vertical = 10.dp),
        Arrangement.Center, Alignment.CenterVertically) {
        Icon(icon, label, tint = th.accent, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = th.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun HomeMaya(th: UiTheme, status: String, live: Boolean, prefs: android.content.SharedPreferences, go: (Screen) -> Unit, onAsk: (String) -> Unit) {
    val ctx = LocalContext.current
    val hr = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val hello = when { hr < 5 -> "Good night"; hr < 12 -> "Good morning"; hr < 17 -> "Good afternoon"; hr < 21 -> "Good evening"; else -> "Good night" }
    val date = remember { java.text.SimpleDateFormat("d MMM", java.util.Locale.ENGLISH).format(java.util.Date()) }
    val day = remember { java.text.SimpleDateFormat("EEEE", java.util.Locale.ENGLISH).format(java.util.Date()) }
    val battery = remember { try { (ctx.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager).getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) } catch (e: Exception) { 0 } }
    var moodIdx by remember { mutableStateOf(prefs.getInt("mood", 0).coerceIn(0, moods.lastIndex)) }
    var ask by remember { mutableStateOf("") }
    val tr = rememberInfiniteTransition(label = "mf")
    val amp = if (live) 2f else 1f
    val f by tr.animateFloat(-1f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "f")
    val sway by tr.animateFloat(-1f, 1f, infiniteRepeatable(tween(3800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    val pulse by tr.animateFloat(0.6f, 1f, infiniteRepeatable(tween(if (live) 600 else 2000), RepeatMode.Reverse), label = "p")
    val anim = Modifier.graphicsLayer { translationY = f * 6.dp.toPx() * amp }
    fun music() {
        if (!(Actions.openApp(ctx, "YouTube Music") || Actions.openApp(ctx, "Spotify") || Actions.openApp(ctx, "YouTube")))
            Toast.makeText(ctx, "Music app nahi mila", Toast.LENGTH_SHORT).show()
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text("$hello,", color = th.text.copy(alpha = 0.7f), fontSize = 14.sp)
            Text("Boss", color = th.text, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text(if (live) "Listening..." else status, color = th.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(240.dp).clip(CircleShape).background(
                Brush.radialGradient(listOf(th.accent.copy(alpha = (if (live) 0.5f else 0.28f) * pulse), Color.Transparent))))
            Image(painterResource(R.drawable.divya_full), "Divya", Modifier.fillMaxHeight().aspectRatio(430f / 615f).then(anim)
                .clip(RoundedCornerShape(28.dp)).border(2.dp, th.accent, RoundedCornerShape(28.dp)), contentScale = ContentScale.Crop)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MayaChip(th, Icons.Filled.MusicNote, "Music", Modifier.weight(1f)) { music() }
            MayaChip(th, Icons.Filled.School, "Study", Modifier.weight(1f)) { go(Screen.Study) }
            MayaChip(th, Icons.Filled.Edit, "Journal", Modifier.weight(1f)) { go(Screen.Journal) }
            MayaChip(th, Icons.Filled.QrCodeScanner, "Scan", Modifier.weight(1f)) { go(Screen.Scan) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MayaCard(th, Icons.Filled.BatteryFull, "Battery", "$battery%", "Phone", Modifier.weight(1f).height(104.dp))
            MayaCard(th, Icons.Filled.CalendarToday, "Today", date, day, Modifier.weight(1f).height(104.dp))
            MayaCard(th, Icons.Filled.Favorite, "Mood", moods[moodIdx].first, moods[moodIdx].second, Modifier.weight(1f).height(104.dp)) {
                moodIdx = (moodIdx + 1) % moods.size; prefs.edit().putInt("mood", moodIdx).apply()
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color.White).padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(ask, { ask = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Ask Divya anything...") },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = th.text, unfocusedTextColor = th.text,
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent))
            IconButton({ if (ask.isNotBlank()) { onAsk(ask.trim()); ask = "" } }) { Icon(Icons.Filled.Send, "Send", tint = th.accent) }
        }
    }
}

@Composable
fun NavItem(th: UiTheme, label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable { onClick() }.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = if (selected) th.accent else th.text.copy(alpha = 0.45f))
        Text(label, color = if (selected) th.accent else th.text.copy(alpha = 0.45f), fontSize = 11.sp)
    }
}

@Composable
fun MayaNav(th: UiTheme, screen: Screen, live: Boolean, onMic: () -> Unit, go: (Screen) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 14.dp).clip(RoundedCornerShape(28.dp))
        .background(Color.White).padding(vertical = 6.dp), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
        th.menu.take(2).forEach { (l, t, i) -> NavItem(th, l, i, screen == t) { go(t) } }
        Box(Modifier.size(62.dp).clip(CircleShape).background(if (live) Color(0xFFE53935) else th.accent).clickable { onMic() },
            contentAlignment = Alignment.Center) {
            Icon(if (live) Icons.Filled.Stop else Icons.Filled.Mic, "Talk", tint = Color.White, modifier = Modifier.size(30.dp))
        }
        th.menu.drop(2).forEach { (l, t, i) -> NavItem(th, l, i, screen == t) { go(t) } }
    }
}

@Composable
fun JournalScreen(th: UiTheme, prefs: android.content.SharedPreferences) {
    val notes = remember { mutableStateListOf<String>().apply { addAll((prefs.getString("notes", "") ?: "").split("\u0001").filter { it.isNotBlank() }) } }
    var t by remember { mutableStateOf("") }
    fun persist() = prefs.edit().putString("notes", notes.joinToString("\u0001")).apply()
    val colors = OutlinedTextFieldDefaults.colors(focusedTextColor = th.text, unfocusedTextColor = th.text)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Memories & Journal", color = th.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(t, { t = it }, Modifier.weight(1f), placeholder = { Text("Aaj kya yaad rakhna hai?") }, colors = colors)
            IconButton({
                if (t.isNotBlank()) {
                    val d = java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.ENGLISH).format(java.util.Date())
                    notes.add(0, "$d\n${t.trim()}"); persist(); t = ""
                }
            }) { Icon(Icons.Filled.Add, "Save", tint = th.accent) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(notes.toList()) { n ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n, color = th.text, modifier = Modifier.weight(1f))
                    IconButton({ notes.remove(n); persist() }) { Icon(Icons.Filled.Delete, "Delete", tint = th.accent) }
                }
            }
        }
    }
}

@Composable
fun StudyScreen(th: UiTheme) {
    var total by remember { mutableStateOf(25 * 60) }
    var left by remember { mutableStateOf(25 * 60) }
    var run by remember { mutableStateOf(false) }
    LaunchedEffect(run) {
        while (run && left > 0) { kotlinx.coroutines.delay(1000); left-- }
        if (left <= 0) run = false
    }
    Page(th, "Study Timer") {
        Text(String.format("%02d:%02d", left / 60, left % 60), color = th.text, fontSize = 64.sp, fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 25, 45).forEach { m ->
                FilterChip(total == m * 60, { run = false; total = m * 60; left = m * 60 }, { Text("$m min") })
            }
        }
        Btn(th, if (run) "Pause" else "Start") { run = !run }
        Btn(th, "Reset") { run = false; left = total }
    }
}


// =====================  VOICE GUARDIAN DASHBOARD  =====================
@Composable
private fun GCard(th: UiTheme, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f))
        .border(1.dp, th.accent.copy(alpha = 0.55f), RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
fun GuardianScreen(th: UiTheme, prefs: android.content.SharedPreferences, ctx: Context) = Page(th, "Voice Guardian") {
    var on by remember { mutableStateOf(prefs.getBoolean("voiceLock", false)) }
    var mode by remember { mutableStateOf(prefs.getInt("vgMode", 1)) }
    var away by remember { mutableStateOf(prefs.getBoolean("vgAway", false)) }
    var strict by remember { mutableStateOf(2.6f - prefs.getFloat("vpSens", 1f)) }
    var tick by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var famName by remember { mutableStateOf("") }
    val hasOwner = remember(tick) { VoiceGuardian.owner(prefs) != null }
    val family = remember(tick) { VoiceGuardian.family(prefs) }
    val log = remember(tick) { VoiceGuardian.log(prefs) }
    val last = VoiceGuardian.last
    val colors = OutlinedTextFieldDefaults.colors(focusedTextColor = th.text, unfocusedTextColor = th.text)

    fun micReady(): Boolean {
        if (!micOk(ctx)) { Toast.makeText(ctx, "Pehle Microphone permission do", Toast.LENGTH_LONG).show(); return false }
        if (MicBus.busy()) { Toast.makeText(ctx, "Pehle Divya ka mic band karo (stop)", Toast.LENGTH_LONG).show(); return false }
        return true
    }

    Text("Divya sirf enrolled awaazon ke hisaab se kaam kare. Awaaz-pehchan basic hai aur perfect security nahi, isliye ise phone ke unlock ke saath hi use karo.",
        color = th.text.copy(alpha = 0.7f), fontSize = 12.sp)

    GCard(th) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Voice Guardian ON", color = th.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Switch(on, { on = it; prefs.edit().putBoolean("voiceLock", it).apply()
                if (it && !hasOwner) msg = "⚠️ Guardian ON hai par Owner awaaz enroll nahi hui. Neeche enroll karo." })
        }
        val l = last
        Text(if (l == null) "Abhi koi awaaz check nahi hui" else when (l.role) {
            "owner" -> "✅ Last: Owner • match ${l.conf}%"
            "family" -> "👪 Last: ${l.name} (family) • match ${l.conf}%"
            "unknown" -> "❓ Last: anjaan awaaz • match ${l.conf}%"
            else -> "ℹ️ Last: awaaz verify nahi ho paayi (typed chat ya bahut kam awaaz)"
        }, color = th.accent, fontSize = 13.sp)
        // Live match meter (Maya jaisa): Divya ka session chalu karke ~3 sec bolo, yahan match % live badalta hai
        if (l != null && l.role != "unverified") {
            LinearProgressIndicator(progress = { (l.conf / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = th.accent)
        }
        Text("🎙️ Home par Talk ON karo aur ~3 sec bolo, live match yahin dikhega.", color = th.text.copy(alpha = 0.6f), fontSize = 11.sp)
    }

    GCard(th) {
        Text("Listen mode", color = th.accent, fontWeight = FontWeight.Bold)
        Text("Owner only = aas-paas ke dost/log ki awaaz Divya ke sunne se PEHLE hi filter ho jati hai (Divya sirf aapki awaaz sunke jawab degi). Iske liye aapki awaaz Owner ke roop mein enroll honi chahiye. Owner + family = aap aur enrolled family. Everyone = koi filter nahi. Filter ki wajah se jawab ~0.5 sec der se shuru ho sakta hai.",
            color = th.text.copy(alpha = 0.7f), fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Everyone", "Owner only", "Owner + family").forEachIndexed { i, n ->
                FilterChip(mode == i, { mode = i; prefs.edit().putInt("vgMode", i).apply() }, { Text(n, fontSize = 11.sp) })
            }
        }
        Text("Match strictness: ${"%.2f".format(strict)}", color = th.text, fontSize = 13.sp)
        Text("Zyada = sakht (owner kabhi-kabhi miss ho sakta hai, doosra kam match hoga). Kam = aasan (owner hamesha pehchana jayega par doosri awaaz bhi match ho sakti hai).",
            color = th.text.copy(alpha = 0.6f), fontSize = 11.sp)
        Slider(strict, { strict = it; prefs.edit().putFloat("vpSens", 2.6f - it).apply() }, valueRange = 0.6f..2.0f)
    }

    GCard(th) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Away / Guard mode", color = th.text, fontWeight = FontWeight.Bold)
                Text("ON hone par anjaan awaaz koi phone ka kaam nahi kar sakti. Block hone par notification aur neeche log mein dikhega. Phone kabhi lock nahi hota, emergency/calls pe asar nahi. Manual override: ise OFF kar do.",
                    color = th.text.copy(alpha = 0.7f), fontSize = 11.sp)
            }
            Switch(away, { away = it; prefs.edit().putBoolean("vgAway", it).apply() })
        }
    }

    GCard(th) {
        Text("Enrolled voices", color = th.accent, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (hasOwner) "👑 Owner (aap)" else "Owner: enroll nahi hui", color = th.text, modifier = Modifier.weight(1f))
            if (hasOwner) TextButton({ VoiceGuardian.removeOwner(prefs); tick++ }) { Text("Hatao") }
        }
        family.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("👪 ${f.name}", color = th.text, modifier = Modifier.weight(1f))
                TextButton({ VoiceGuardian.removeFamily(prefs, f.id); tick++ }) { Text("Hatao") }
            }
        }
        Btn(th, if (busy) "Chal raha hai..." else (if (hasOwner) "Meri awaaz dobara record karo (20 sec)" else "Meri (Owner) awaaz record karo (20 sec)")) {
            if (busy || !micReady()) return@Btn
            busy = true; msg = "🎤 20 sec lagatar bolte raho (kuch bhi, normal awaaz mein)..."
            VoiceGuardian.enrollAsync(20) { md ->
                busy = false
                if (md != null) { VoicePrint.save(prefs, md); prefs.edit().putBoolean("voiceLock", true).apply(); on = true; tick++; msg = "✅ Owner awaaz save ho gayi." }
                else msg = "❌ Kam awaaz mili. Shaant jagah par 20 sec lagatar bolo aur dobara try karo."
            }
        }
        OutlinedTextField(famName, { famName = it }, Modifier.fillMaxWidth(), label = { Text("Family member ka naam") }, colors = colors, singleLine = true)
        Btn(th, if (busy) "Chal raha hai..." else "Family member ki awaaz record karo (20 sec)") {
            if (busy) return@Btn
            if (famName.isBlank()) { Toast.makeText(ctx, "Pehle naam likho", Toast.LENGTH_SHORT).show(); return@Btn }
            if (!micReady()) return@Btn
            busy = true; msg = "🎤 ${famName.trim()} se kaho 20 sec lagatar bole..."
            val nm = famName.trim()
            VoiceGuardian.enrollAsync(20) { md ->
                busy = false
                if (md != null) { VoiceGuardian.addFamily(prefs, nm, md); famName = ""; tick++; msg = "✅ $nm ki awaaz save ho gayi (phone ke andar encrypted)." }
                else msg = "❌ Kam awaaz mili, dobara try karo."
            }
        }
        Btn(th, "Test: 4 sec bolo, kaun hai check karo") {
            if (busy || !micReady()) return@Btn
            busy = true; msg = "🎤 4 sec bolo..."
            VoiceGuardian.testAsync(prefs) { sp ->
                busy = false
                msg = if (sp == null) "Mic se awaaz nahi mili" else when (sp.role) {
                    "unverified" -> "Awaaz bahut kam mili ya koi voice enrolled nahi. Thoda zor se bolke dobara try karo."
                    "owner" -> "✅ Owner pehchani • match ${sp.conf}% (score ${"%.1f".format(sp.score)} / limit ${"%.1f".format(sp.limit)})"
                    "family" -> "👪 ${sp.name} pehchani • match ${sp.conf}%"
                    else -> "❓ Anjaan awaaz • match ${sp.conf}% (score ${"%.1f".format(sp.score)} / limit ${"%.1f".format(sp.limit)}). Confidence kam ho to dobara bolo."
                }
            }
        }
        if (msg.isNotBlank()) Text(msg, color = th.text, fontSize = 13.sp)
    }

    GCard(th) {
        Text("Guard log (roke gaye kaam)", color = th.accent, fontWeight = FontWeight.Bold)
        if (log.isEmpty()) Text("Abhi kuch roka nahi gaya.", color = th.text.copy(alpha = 0.7f), fontSize = 12.sp)
        log.take(10).forEach { (t, m) ->
            Text(java.text.SimpleDateFormat("dd MMM hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(t)) + "  •  " + m, color = th.text, fontSize = 12.sp)
        }
        if (log.isNotEmpty()) TextButton({ VoiceGuardian.clearLog(prefs); tick++ }) { Text("Log saaf karo") }
    }
    Text("Privacy: sirf awaaz ki chhoti 'chhaap' (numbers) phone mein rehti hai (family wali encrypted). Raw recording save ya upload nahi hoti. Jab Divya ka session chalta hai, sirf allowed awaaz (Listen mode ke hisaab se) Gemini ko jati hai. Screen sharing ON ho to screen ke screenshots bhi Gemini ko jate hain (phone lock ho ya Scanner khula ho to nahi).",
        color = th.text.copy(alpha = 0.55f), fontSize = 11.sp)
}
