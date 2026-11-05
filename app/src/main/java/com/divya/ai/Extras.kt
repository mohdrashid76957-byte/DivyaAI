package com.divya.ai

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.widget.Toast
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// ======================= Cyber HUD common look =======================
val CyCyan = Color(0xFF19E3F0)
val CyCard = Color(0xFF06222E)
val CyDeep = Color(0xFF031521)
val CyEdge = Brush.linearGradient(listOf(Color(0xFF6CF3FF), Color(0xFF12A8C8)))

@Composable
fun CyCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(CyCard).border(1.2.dp, CyEdge, shape).padding(12.dp), content = content)
}

@Composable
private fun CyPage(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val shape = RoundedCornerShape(14.dp)
        Box(Modifier.fillMaxWidth().clip(shape).background(CyDeep).border(1.5.dp, CyEdge, shape).padding(14.dp), contentAlignment = Alignment.Center) {
            Text(title, color = CyCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

@Composable
private fun cyFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    focusedBorderColor = CyCyan, unfocusedBorderColor = CyCyan.copy(alpha = 0.4f),
    focusedLabelColor = CyCyan, unfocusedLabelColor = Color(0xFF8FB8C0), cursorColor = CyCyan)

@Composable
private fun cySlider() = SliderDefaults.colors(thumbColor = CyCyan, activeTrackColor = CyCyan, inactiveTrackColor = CyCyan.copy(alpha = 0.25f))

// ======================= VOICE SETTINGS =======================
private class VoiceOpt(val id: String, val tone: String, val female: Boolean)

private val voiceOpts = listOf(
    VoiceOpt("Aoede", "Breezy", true), VoiceOpt("Kore", "Firm", true), VoiceOpt("Leda", "Youthful", true),
    VoiceOpt("Zephyr", "Bright", true), VoiceOpt("Callirrhoe", "Easy-going", true), VoiceOpt("Autonoe", "Bright", true),
    VoiceOpt("Despina", "Smooth", true), VoiceOpt("Erinome", "Clear", true), VoiceOpt("Laomedeia", "Upbeat", true),
    VoiceOpt("Achernar", "Soft", true), VoiceOpt("Gacrux", "Mature", true), VoiceOpt("Pulcherrima", "Forward", true),
    VoiceOpt("Vindemiatrix", "Gentle", true), VoiceOpt("Sulafat", "Warm", true),
    VoiceOpt("Puck", "Upbeat", false), VoiceOpt("Charon", "Informative", false), VoiceOpt("Fenrir", "Excitable", false),
    VoiceOpt("Orus", "Firm", false), VoiceOpt("Enceladus", "Breathy", false), VoiceOpt("Iapetus", "Clear", false),
    VoiceOpt("Umbriel", "Easy-going", false), VoiceOpt("Algieba", "Smooth", false), VoiceOpt("Algenib", "Gravelly", false),
    VoiceOpt("Rasalgethi", "Informative", false), VoiceOpt("Alnilam", "Firm", false), VoiceOpt("Schedar", "Even", false),
    VoiceOpt("Achird", "Friendly", false), VoiceOpt("Zubenelgenubi", "Casual", false), VoiceOpt("Sadachbia", "Lively", false),
    VoiceOpt("Sadaltager", "Knowledgeable", false)
)

object VoiceExtras {
    /** Live session ke system prompt mein judta hai (pitch / speed / accent). Default par kuch nahi judta. */
    fun hint(p: SharedPreferences): String {
        val pitch = p.getFloat("vPitch", 1f); val speed = p.getFloat("vSpeed", 1f); val acc = p.getString("vAccent", "India") ?: "India"
        val s = StringBuilder()
        if (acc == "USA") s.append("Speak English words with a natural American accent. ")
        if (acc == "UK") s.append("Speak English words with a natural British accent. ")
        if (speed <= 0.85f) s.append("Speak noticeably slower than normal. ") else if (speed >= 1.2f) s.append("Speak noticeably faster than normal. ")
        if (pitch <= 0.85f) s.append("Use a slightly deeper, lower voice pitch. ") else if (pitch >= 1.2f) s.append("Use a slightly higher voice pitch. ")
        return s.toString().trim()
    }
}

@Composable
fun VoiceSettingsScreen(th: UiTheme, prefs: SharedPreferences) {
    val ctx = LocalContext.current
    var sel by remember { mutableStateOf(prefs.getString("voice", "Aoede") ?: "Aoede") }
    var pitch by remember { mutableStateOf(prefs.getFloat("vPitch", 1f)) }
    var speed by remember { mutableStateOf(prefs.getFloat("vSpeed", 1f)) }
    var accent by remember { mutableStateOf(prefs.getString("vAccent", "India") ?: "India") }
    var filter by remember { mutableStateOf("All") }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var t: TextToSpeech? = null
        t = TextToSpeech(ctx) { st -> if (st == TextToSpeech.SUCCESS) tts.value = t }
        onDispose { try { t?.stop(); t?.shutdown() } catch (_: Exception) {} }
    }
    fun preview() {
        val t = tts.value
        if (t == null) { Toast.makeText(ctx, "Phone ka TTS abhi ready nahi", Toast.LENGTH_SHORT).show(); return }
        t.setLanguage(when (accent) { "USA" -> Locale.US; "UK" -> Locale.UK; else -> Locale("en", "IN") })
        t.setPitch(pitch); t.setSpeechRate(speed)
        t.speak("Hello! Main Divya hoon. Aapka har kaam, ab aur bhi aasan.", TextToSpeech.QUEUE_FLUSH, null, "pv")
    }
    CyPage("Divya AI – Voice Settings") {
        CyCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(76.dp).clip(CircleShape).background(CyDeep).border(2.5.dp, CyEdge, CircleShape).clickable { preview() },
                    contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, "Preview", tint = CyCyan, modifier = Modifier.size(40.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Advanced Settings", color = CyCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Pitch  ${"%.1f".format(pitch)}x", color = Color.White, fontSize = 12.sp)
                    Slider(pitch, { pitch = it; prefs.edit().putFloat("vPitch", it).apply() }, valueRange = 0.5f..1.5f, colors = cySlider())
                    Text("Speed  ${"%.1f".format(speed)}x", color = Color.White, fontSize = 12.sp)
                    Slider(speed, { speed = it; prefs.edit().putFloat("vSpeed", it).apply() }, valueRange = 0.5f..1.5f, colors = cySlider())
                }
            }
            Text("Accent Region", color = CyCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("India" to "🇮🇳 India", "USA" to "🇺🇸 USA", "UK" to "🇬🇧 UK").forEach { (id, label) ->
                    FilterChip(accent == id, { accent = id; prefs.edit().putString("vAccent", id).apply() }, { Text(label, fontSize = 12.sp) })
                }
            }
            Text("▶ Preview phone ki apni TTS awaaz se hota hai (pitch/speed/accent sunne ke liye). Live Divya mein awaaz ka naam alag se lagta hai, pitch/speed/accent usko instruction ke roop mein milte hain.",
                color = Color(0xFF8FB8C0), fontSize = 10.sp, fontStyle = FontStyle.Italic)
        }
        CyCard {
            Text("🔊 CHOOSE DIVYA'S VOICE (${voiceOpts.size} Available)", color = CyCyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Female", "Male").forEach { f -> FilterChip(filter == f, { filter = f }, { Text(f, fontSize = 12.sp) }) }
            }
            voiceOpts.filter { filter == "All" || (filter == "Female") == it.female }.forEach { v ->
                val on = sel == v.id
                val shape = RoundedCornerShape(12.dp)
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(shape)
                    .then(if (on) Modifier.border(1.5.dp, CyEdge, shape).background(CyCyan.copy(alpha = 0.12f)) else Modifier)
                    .clickable { sel = v.id; prefs.edit().putString("voice", v.id).apply() }.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VolumeUp, null, tint = if (on) CyCyan else Color(0xFF9DB7BD), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("${v.id} · ${v.tone} (${if (v.female) "F" else "M"})", color = if (on) CyCyan else Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (on) Text("ACTIVE", color = CyCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("Nayi awaaz agle connection se lagti hai (mic ya stop karke dobara shuru karo).", color = th.text.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

// ======================= MANAGE API =======================
private class Prov(val id: String, val name: String, val url: String)
private class ApiCat(val id: String, val title: String, val provs: List<Prov>)

private val apiCats = listOf(
    ApiCat("llm", "LLM Models", listOf(
        Prov("gemini", "Gemini", "https://aistudio.google.com/apikey"),
        Prov("claude", "Claude", "https://console.anthropic.com/settings/keys"),
        Prov("grok", "Grok", "https://console.x.ai"),
        Prov("openai", "OpenAI", "https://platform.openai.com/api-keys"),
        Prov("groq", "Groq", "https://console.groq.com/keys"),
        Prov("cerebras", "Cerebras", "https://cloud.cerebras.ai"),
        Prov("together", "Together AI", "https://api.together.ai/settings/api-keys"),
        Prov("deepseek", "DeepSeek", "https://platform.deepseek.com/api_keys"),
        Prov("openrouter", "OpenRouter", "https://openrouter.ai/keys"))),
    ApiCat("voice", "Voice Models", listOf(
        Prov("elevenlabs", "ElevenLabs", "https://elevenlabs.io/app/settings/api-keys"),
        Prov("openaitts", "OpenAI TTS", "https://platform.openai.com/api-keys"),
        Prov("cartesia", "Cartesia", "https://play.cartesia.ai/keys"))),
    ApiCat("clone", "Voice Cloner", listOf(
        Prov("elevenclone", "ElevenLabs Clone", "https://elevenlabs.io/app/settings/api-keys"),
        Prov("resemble", "Resemble AI", "https://app.resemble.ai"),
        Prov("playht", "PlayHT", "https://play.ht/studio/api-access"))),
    ApiCat("stt", "Speech Recognition", listOf(
        Prov("deepgram", "Deepgram", "https://console.deepgram.com"),
        Prov("assembly", "AssemblyAI", "https://www.assemblyai.com/dashboard"),
        Prov("groqwhisper", "Groq Whisper", "https://console.groq.com/keys"))),
    ApiCat("music", "Video & Music", listOf(
        Prov("suno", "Suno", "https://suno.com"),
        Prov("udio", "Udio", "https://www.udio.com"))),
    ApiCat("image", "Image Generation", listOf(
        Prov("stability", "Stability AI", "https://platform.stability.ai/account/keys"),
        Prov("openaiimg", "OpenAI Images", "https://platform.openai.com/api-keys"),
        Prov("replicate", "Replicate", "https://replicate.com/account/api-tokens"),
        Prov("togetherimg", "Together FLUX", "https://api.together.ai/settings/api-keys"))),
    ApiCat("video", "Video Generation", listOf(
        Prov("runway", "Runway", "https://dev.runwayml.com"),
        Prov("luma", "Luma", "https://lumalabs.ai/api"),
        Prov("kling", "Kling", "https://klingai.com")))
)

private fun keyName(id: String) = if (id == "gemini") "key" else "api_$id"

@Composable
fun ManageApiScreen(th: UiTheme, prefs: SharedPreferences) {
    val ctx = LocalContext.current
    var cat by remember { mutableStateOf(0) }
    var activeTick by remember { mutableStateOf(0) }
    CyPage("← MANAGE API") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            apiCats.forEachIndexed { i, c -> FilterChip(cat == i, { cat = i }, { Text(c.title, fontSize = 12.sp) }) }
        }
        val c = apiCats[cat]
        if (c.id != "llm") Text("Is category ki keys yahan save hoti hain. Abhi app mein sirf LLM Models (chat) aur Gemini (live voice) chalte hain, baaki features agle update mein judenge.",
            color = th.text.copy(alpha = 0.7f), fontSize = 11.sp, fontStyle = FontStyle.Italic)
        else Text("Gemini = live voice (mic). Doosre LLM = Chat screen ke typed jawab. 'Switch to this' dabao.",
            color = th.text.copy(alpha = 0.7f), fontSize = 11.sp, fontStyle = FontStyle.Italic)
        c.provs.forEach { p ->
            key(c.id + p.id) {
                var k by remember { mutableStateOf(prefs.getString(keyName(p.id), "") ?: "") }
                val active = (prefs.getString("apiActive_" + c.id, c.provs.first().id) ?: c.provs.first().id) == p.id && activeTick >= 0
                CyCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VpnKey, null, tint = CyCyan, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(p.name.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        if (active) Text("ACTIVE", color = CyDeep, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(CyCyan).padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                    OutlinedTextField(k, { k = it; prefs.edit().putString(keyName(p.id), it.trim()).apply() }, Modifier.fillMaxWidth().padding(top = 6.dp),
                        label = { Text(p.name + " API key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, colors = cyFieldColors())
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} },
                            Modifier.weight(1f)) { Text("GET API KEY", color = CyCyan, fontSize = 11.sp) }
                        Button({
                            prefs.edit().putString("apiActive_" + c.id, p.id).apply(); activeTick++
                            Toast.makeText(ctx, p.name + " active", Toast.LENGTH_SHORT).show()
                        }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = CyCyan)) { Text("SWITCH TO THIS", color = CyDeep, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

// ======================= LLM ROUTER (Groq / Cerebras / Together / Grok / OpenAI / DeepSeek / OpenRouter / Claude) =======================
object LlmRouter {
    private class P(val url: String, val model: String, val anthropic: Boolean = false)
    private val table = mapOf(
        "groq" to P("https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile"),
        "cerebras" to P("https://api.cerebras.ai/v1/chat/completions", "llama-3.3-70b"),
        "together" to P("https://api.together.xyz/v1/chat/completions", "meta-llama/Llama-3.3-70B-Instruct-Turbo"),
        "grok" to P("https://api.x.ai/v1/chat/completions", "grok-4"),
        "openai" to P("https://api.openai.com/v1/chat/completions", "gpt-4o-mini"),
        "openrouter" to P("https://openrouter.ai/api/v1/chat/completions", "openai/gpt-4o-mini"),
        "deepseek" to P("https://api.deepseek.com/chat/completions", "deepseek-chat"),
        "claude" to P("https://api.anthropic.com/v1/messages", "claude-sonnet-5-5", true)
    )

    fun activeId(p: SharedPreferences) = p.getString("apiActive_llm", "gemini") ?: "gemini"

    /** Gemini ke alawa koi LLM active hai aur uski key bhari hai? */
    fun ready(p: SharedPreferences): Boolean {
        val a = activeId(p)
        return a != "gemini" && table.containsKey(a) && !p.getString("api_$a", "").isNullOrBlank()
    }

    fun ask(p: SharedPreferences, history: List<Pair<String, String>>, callName: String, cb: (String) -> Unit) {
        val id = activeId(p); val key = p.getString("api_$id", "") ?: ""
        val conf = table[id] ?: return
        val model = p.getString("apiModel_$id", null)?.takeIf { it.isNotBlank() } ?: conf.model
        val sys = "You are Divya, a warm, friendly female AI assistant. Reply in Hinglish (Hindi+English), short and helpful. Call the user '$callName'."
        Thread {
            val r = try { call(conf, key, model, sys, history) } catch (e: Exception) { "⚠️ ${id}: ${e.message}" }
            Handler(Looper.getMainLooper()).post { cb(r) }
        }.start()
    }

    private fun call(c: P, key: String, model: String, sys: String, history: List<Pair<String, String>>): String {
        val msgs = JSONArray()
        history.takeLast(12).forEach { (who, t) ->
            val role = when (who) { "You" -> "user"; "Divya" -> "assistant"; else -> null }
            if (role != null && t.isNotBlank() && !t.startsWith("⚠️")) msgs.put(JSONObject().put("role", role).put("content", t))
        }
        val body = JSONObject().put("model", model)
        if (c.anthropic) body.put("system", sys).put("max_tokens", 1024).put("messages", msgs)
        else { val all = JSONArray().put(JSONObject().put("role", "system").put("content", sys)); for (i in 0 until msgs.length()) all.put(msgs.get(i)); body.put("messages", all) }
        val con = URL(c.url).openConnection() as HttpURLConnection
        con.requestMethod = "POST"; con.connectTimeout = 20000; con.readTimeout = 60000; con.doOutput = true
        con.setRequestProperty("Content-Type", "application/json")
        if (c.anthropic) { con.setRequestProperty("x-api-key", key); con.setRequestProperty("anthropic-version", "2023-06-01") }
        else con.setRequestProperty("Authorization", "Bearer $key")
        con.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = con.responseCode
        val txt = (if (code in 200..299) con.inputStream else con.errorStream)?.bufferedReader()?.readText() ?: ""
        if (code !in 200..299) throw RuntimeException("HTTP $code " + txt.take(160))
        val j = JSONObject(txt)
        return if (c.anthropic) j.getJSONArray("content").getJSONObject(0).getString("text")
        else j.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }
}

// ======================= WAKE WORDS =======================
data class WakeW(val word: String, val on: Boolean, val sens: Int)

object WakeWords {
    val library = listOf("hey divya", "ok divya", "hello divya", "namaste divya", "jaago divya", "suno divya", "hey jarvis", "hey assistant")

    fun load(p: SharedPreferences): MutableList<WakeW> =
        (p.getString("wakeWords", "") ?: "").lines().filter { it.isNotBlank() }.mapNotNull {
            val s = it.split("\t"); if (s.size < 3) null else WakeW(s[0], s[1] == "1", s[2].toIntOrNull() ?: 60)
        }.toMutableList()

    fun save(p: SharedPreferences, l: List<WakeW>) {
        p.edit().putString("wakeWords", l.joinToString("\n") { "${it.word}\t${if (it.on) 1 else 0}\t${it.sens}" }).apply()
    }

    private fun lev(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.length]
    }

    /** WakeService yahi check karti hai: user ke custom wake words (sensitivity ke hisaab se thoda galat bola bhi chalega). */
    fun matches(p: SharedPreferences, text: String): Boolean {
        val words = load(p).filter { it.on }
        if (words.isEmpty()) return false
        val norm = text.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()
        if (norm.isEmpty()) return false
        val tokens = norm.split(' ')
        for (w in words) {
            val wl = w.word.lowercase(Locale.ROOT).trim()
            if (wl.isEmpty()) continue
            if (norm.contains(wl)) return true
            val allowed = when { w.sens >= 70 -> maxOf(1, wl.length / 5); w.sens >= 40 -> wl.length / 8; else -> 0 }
            if (allowed == 0) continue
            val n = wl.split(' ').size
            for (i in 0..(tokens.size - n)) if (lev(tokens.subList(i, i + n).joinToString(" "), wl) <= allowed) return true
        }
        return false
    }
}

@Composable
fun WakeWordsScreen(th: UiTheme, prefs: SharedPreferences) {
    val ctx = LocalContext.current
    val list = remember { mutableStateListOf<WakeW>().also { it.addAll(WakeWords.load(prefs)) } }
    var q by remember { mutableStateOf("") }
    var nw by remember { mutableStateOf("") }
    var master by remember { mutableStateOf(prefs.getBoolean("wake", false)) }
    fun persist() = WakeWords.save(prefs, list.toList())
    fun add(w: String) {
        val t = w.trim().lowercase(Locale.ROOT)
        if (t.length < 3) { Toast.makeText(ctx, "Wake word kam se kam 3 akshar ka rakho", Toast.LENGTH_SHORT).show(); return }
        if (list.any { it.word == t }) { Toast.makeText(ctx, "Ye pehle se hai", Toast.LENGTH_SHORT).show(); return }
        list.add(WakeW(t, true, 60)); persist()
    }
    CyPage("< WAKE WORDS") {
        CyCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Background mein wake word sunna", color = Color.White, modifier = Modifier.weight(1f))
                Switch(master, {
                    if (it && !micOk(ctx)) Toast.makeText(ctx, "Pehle Microphone permission do (Permissions)", Toast.LENGTH_LONG).show()
                    else { master = it; prefs.edit().putBoolean("wake", it).apply(); if (it) WakeService.start(ctx) else WakeService.stop(ctx) }
                })
            }
            Text("\"Wake up Divya\" aur \"Divya\" hamesha chalte hain. Neeche apne extra wake words jodo.", color = Color(0xFF8FB8C0), fontSize = 11.sp)
        }
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text("SEARCH WAKE WORDS") },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = CyCyan) }, singleLine = true, colors = cyFieldColors())
        CyCard {
            Text("➕ APNA WAKE WORD", color = CyCyan, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(nw, { nw = it }, Modifier.weight(1f), label = { Text("jaise: hey jarvis") }, singleLine = true, colors = cyFieldColors())
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(CyDeep).border(1.5.dp, CyEdge, RoundedCornerShape(12.dp))
                    .clickable { add(nw); nw = "" }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, "Add", tint = CyCyan) }
            }
        }
        CyCard {
            Text("MERE WAKE WORDS", color = CyCyan, fontWeight = FontWeight.Bold)
            if (list.isEmpty()) Text("Abhi koi extra wake word nahi. Upar jodo ya neeche library se download karo.", color = Color(0xFF8FB8C0), fontSize = 12.sp)
            list.toList().forEachIndexed { i, w ->
                if (q.isBlank() || w.word.contains(q.trim(), true)) key(w.word) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(w.word.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Switch(w.on, { list[i] = w.copy(on = it); persist() })
                            IconButton({ list.removeAt(i); persist() }) { Icon(Icons.Filled.Delete, "Delete", tint = Color(0xFFFF6B81)) }
                        }
                        Text("Sensitivity ${w.sens}%", color = Color(0xFF8FB8C0), fontSize = 11.sp)
                        Slider(w.sens.toFloat(), { list[i] = w.copy(sens = it.toInt()) }, valueRange = 10f..100f, onValueChangeFinished = { persist() }, colors = cySlider())
                    }
                }
            }
        }
        CyCard {
            Text("DOWNLOAD WAKE WORDS", color = CyCyan, fontWeight = FontWeight.Bold)
            Text("Taiyaar library (app mein hi hai, internet nahi chahiye)", color = Color(0xFF8FB8C0), fontSize = 11.sp)
            WakeWords.library.filter { lw -> list.none { it.word == lw } && (q.isBlank() || lw.contains(q.trim(), true)) }.forEach { lw ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Mic, null, tint = CyCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(lw.uppercase(), color = Color.White, modifier = Modifier.weight(1f))
                    OutlinedButton({ add(lw) }) { Text("ADD", color = CyCyan, fontSize = 11.sp) }
                }
            }
        }
        Text("Wake word ke liye Microphone permission + upar ka switch ON hona chahiye.", color = th.text.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}
