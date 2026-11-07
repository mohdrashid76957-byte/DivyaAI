package com.divya.ai

import android.content.Context
import android.content.Intent
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import java.io.File

/** Tools > Video Gen AI: topic likho/bolo -> voiceover + subtitles wali vertical video. Tabs: Create / Videos / Settings. */
@Composable
fun VideoGenScreen(th: UiTheme, ctx: Context) {
    val prefs = remember { ctx.getSharedPreferences("divya", 0) }
    var tab by remember { mutableStateOf(0) }
    var topic by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf(prefs.getString("vgLang", "hindi") ?: "hindi") }
    var secs by remember { mutableStateOf(prefs.getInt("vgSecs", 30)) }
    var vis by remember { mutableStateOf(prefs.getString("vgVis", "ai") ?: "ai") }
    var imgSrc by remember { mutableStateOf(prefs.getString("vgImg", "auto") ?: "auto") }
    var style by remember { mutableStateOf(prefs.getString("vgStyle", "facts") ?: "facts") }
    var sel by remember { mutableStateOf(VideoBus.lastPath) }
    var tick by remember { mutableStateOf(0) }
    var pexels by remember { mutableStateOf(prefs.getString("pexelsKey", "") ?: "") }
    var voiceSel by remember { mutableStateOf(if (prefs.getBoolean("vgPhoneVoice", false)) "phone" else (prefs.getString("vgVoiceName", "") ?: "").ifBlank { "auto" }) }
    LaunchedEffect(VideoBus.lastPath) { VideoBus.lastPath?.let { sel = it; tick++ } }
    fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()

    fun share(f: File) {
        try {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Video share karo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { toast("Share nahi hua: ${e.message}") }
    }

    @Composable fun Chips(opts: List<Pair<String, String>>, cur: String, on: (String) -> Unit) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            opts.forEach { (v, label) ->
                val on1 = v == cur
                Text(label, color = if (on1) Color.Black else th.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (on1) th.accent else th.accent.copy(alpha = 0.18f))
                        .clickable { on(v) }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
    }

    @Composable fun Player(path: String) {
        key(path, tick) {
            AndroidView(factory = { c ->
                VideoView(c).apply {
                    setVideoPath(path); setMediaController(MediaController(c).also { it.setAnchorView(this) })
                    setOnPreparedListener { it.isLooping = false; start() }
                }
            }, modifier = Modifier.fillMaxWidth().height(460.dp).clip(RoundedCornerShape(14.dp)))
        }
    }

    @Composable fun LivePreview(live: VideoGen.Live) {
        var frame by remember { mutableStateOf(0L) }
        LaunchedEffect(live) { while (true) { withFrameNanos { frame = it } } }
        Canvas(Modifier.height(460.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(14.dp))) {
            frame.let { }
            drawIntoCanvas { c ->
                val t = ((System.nanoTime() - live.startNs) / 1e9).coerceIn(0.0, live.total - 0.05)
                live.renderer.draw(c.nativeCanvas, t, size.width, size.height)
            }
        }
    }

    Page(th, "🎬 Video Gen AI") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("CREATE", "VIDEOS", "SETTINGS").forEachIndexed { i, l ->
                Text(l, color = if (tab == i) Color.Black else th.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (tab == i) th.accent else th.accent.copy(alpha = 0.15f))
                        .clickable { tab = i }.padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        when (tab) {
            0 -> {
                Text("Topic likho (ya Divya se bolo: \"is topic par video bana do\"). Script + awaaz + subtitles phone par ban jaate hain.", color = th.text.copy(alpha = 0.8f), fontSize = 13.sp)
                OutlinedTextField(topic, { topic = it }, Modifier.fillMaxWidth(), label = { Text("Video ka topic / idea") }, minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = th.text, unfocusedTextColor = th.text))
                Text("Bhasha", color = th.text.copy(alpha = 0.7f), fontSize = 12.sp)
                Chips(listOf("hindi" to "Hindi", "english" to "English"), lang) { lang = it; prefs.edit().putString("vgLang", it).apply() }
                Text("Visuals", color = th.text.copy(alpha = 0.7f), fontSize = 12.sp)
                Chips(listOf("ai" to "🎨 AI Real", "fast" to "⚡ Neon (fast)"), vis) { vis = it; prefs.edit().putString("vgVis", it).apply() }
                Text("Lambai", color = th.text.copy(alpha = 0.7f), fontSize = 12.sp)
                Chips(listOf("15" to "15s", "30" to "30s", "45" to "45s", "60" to "60s"), secs.toString()) { secs = it.toInt(); prefs.edit().putInt("vgSecs", secs).apply() }
                Text("Style", color = th.text.copy(alpha = 0.7f), fontSize = 12.sp)
                Chips(listOf("facts" to "Facts", "motivation" to "Motivation", "story" to "Story", "news" to "News"), style) { style = it; prefs.edit().putString("vgStyle", it).apply() }

                VideoBus.live?.let { LivePreview(it) }
                if (VideoBus.busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = th.accent, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(VideoBus.stage.ifBlank { "Chal rahi hai…" }, color = th.text, fontSize = 14.sp)
                    }
                } else {
                    Btn(th, "⚡ Video banao") {
                        val t = topic.trim()
                        if (t.isBlank()) toast("Pehle topic likho") else {
                            Thread { VideoGen.createTracked(ctx, t, lang, secs, style, "", "", vis) }.start()
                        }
                    }
                }
                VideoBus.error?.let { if (!VideoBus.busy) Text("⚠ $it", color = Color(0xFFFF6B6B), fontSize = 13.sp) }
                if (vis == "ai")
                    Text("Tip: AI Real mode mein har scene ki asli AI image banti hai (10-40 sec). Jaldi chahiye to Neon (fast) chuno.", color = th.text.copy(alpha = 0.6f), fontSize = 12.sp)

                val cur = sel?.let { File(it) }?.takeIf { it.exists() }
                if (cur != null) {
                    Text("RESULT", color = th.accent, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    if (VideoBus.live == null) Player(cur.absolutePath)
                    Text(cur.name, color = th.text.copy(alpha = 0.6f), fontSize = 11.sp)
                    Btn(th, "📤 Share") { share(cur) }
                    Btn(th, "🖼 Gallery mein save (Movies/DivyaVideos)") {
                        try { toast(if (VideoGen.saveToGallery(ctx, cur)) "Gallery mein save ho gayi" else "Ye Android purana hai - video app folder mein hai (Videos tab)") }
                        catch (e: Exception) { toast("Save nahi hua: ${e.message}") }
                    }
                }
            }
            1 -> {
                val vids = remember(tick, VideoBus.busy) { VideoGen.list(ctx) }
                if (vids.isEmpty()) Text("Abhi koi video nahi bani.", color = th.text.copy(alpha = 0.6f))
                vids.forEach { f ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(th.accent.copy(alpha = 0.16f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { sel = f.absolutePath; tick++; tab = 0 }) {
                            Text("🎞 " + (File(f.path + ".txt").takeIf { it.exists() }?.readLines()?.firstOrNull() ?: f.name), color = th.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("${f.length() / 1024} KB • tap = play", color = th.text.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                        TextButton({ share(f) }) { Text("Share", color = th.accent) }
                        TextButton({ VideoGen.delete(f); if (sel == f.absolutePath) sel = null; tick++ }) { Text("Delete", color = Color(0xFFFF6B6B)) }
                    }
                }
                Btn(th, "🔄 Refresh") { tick++ }
            }
            else -> {
                Text("AI image source (Visuals = AI Real ke liye)", color = th.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Chips(listOf("auto" to "Auto", "pollinations" to "Free AI", "gemini" to "Gemini"), imgSrc) { imgSrc = it; prefs.edit().putString("vgImg", it).apply() }
                Text("Auto = pehle free Pollinations AI (bina key), na chale to Gemini image (aapki key; free tier par band ho sakta hai), phir Pexels. AI images ko 10-40 sec lagte hain, isliye video thodi der baad shuru hoti hai.", color = th.text.copy(alpha = 0.6f), fontSize = 12.sp)
                Text("Background photos (optional, free): pexels.com/api par free key lo aur yahan paste karo. Key na ho to neon animated background chalta hai.", color = th.text.copy(alpha = 0.8f), fontSize = 13.sp)
                OutlinedTextField(pexels, { pexels = it }, Modifier.fillMaxWidth(), label = { Text("Pexels API key") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = th.text, unfocusedTextColor = th.text))
                Btn(th, "Save") { prefs.edit().putString("pexelsKey", pexels.trim()).apply(); toast("Save ho gaya") }
                Text("Video ki awaaz", color = th.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                val setVoice = { v: String ->
                    voiceSel = v
                    prefs.edit().putBoolean("vgPhoneVoice", v == "phone").putString("vgVoiceName", if (v == "auto" || v == "phone") "" else v).apply()
                }
                Chips(listOf("auto" to "Auto (style ke hisab se)", "phone" to "Phone (robot)"), voiceSel, setVoice)
                Chips(listOf("Puck" to "Puck mazedaar", "Fenrir" to "Fenrir josh", "Algenib" to "Algenib gehri"), voiceSel, setVoice)
                Chips(listOf("Aoede" to "Aoede naram", "Kore" to "Kore pakki", "Charon" to "Charon samjhane wali"), voiceSel, setVoice)
                Text("Auto = funny me Puck, attitude me Algenib, sad me Aoede, baaki me Fenrir. Ye awaazein Gemini TTS se aati hain (wahi API key, 1-3 sec zyada lagte hain). Gemini na chale to phone ki awaaz apne aap chalti hai.", color = th.text.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
    }
}
