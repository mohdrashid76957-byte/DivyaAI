package com.divya.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** Home ke "Ask" jaisi jagah se chat mein sawaal bhejne ke liye. */
object ProBus { var pending by mutableStateOf<String?>(null) }

class PAtt(val name: String, val mime: String, val b64: String?, val text: String?, val zipFile: java.io.File? = null)
class AttErr(msg: String) : Exception(msg)

class PMsg(val role: String, text: String = "", val atts: List<PAtt> = emptyList()) {
    var text by mutableStateOf(text)
    var project by mutableStateOf<String?>(null)     // is jawab se bana project (ZIP ke liye)
    var status by mutableStateOf("")                  // progress line (project banate waqt)
    var busy by mutableStateOf(false)
    var images by mutableStateOf<List<java.io.File>>(emptyList())   // chat mein bani images
}

/** Chat ki halat app mein ek jagah rehti hai, screen badalne par bhi nahi jaati. */
object ProState {
    val msgs = mutableStateListOf<PMsg>()
    var busy by mutableStateOf(false)
    var mode by mutableStateOf(0)                    // 0 Fast, 1 Smart, 2 Pro
    var web by mutableStateOf(false)
    var projectMode by mutableStateOf(false)
    var usedModel by mutableStateOf("")
    @Volatile var startedAt = 0L                    // jawab kab shuru hua (Stop ke galat tap se bachne ke liye)
    private val cancel = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())
    private var loaded = false
    var baseZip by mutableStateOf<java.io.File?>(null)     // upload kiya hua project ZIP (isi par "Updated ZIP" banta hai)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        c.getSharedPreferences("divya", 0).getString("proBaseZip", "")?.takeIf { it.isNotBlank() }?.let { f -> java.io.File(f).takeIf { it.exists() }?.let { baseZip = it } }
        try {
            val a = JSONArray(c.getSharedPreferences("divya", 0).getString("proChat", "[]") ?: "[]")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val m = PMsg(o.optString("r"), o.optString("t"))
                o.optString("p").takeIf { it.isNotBlank() }?.let { p -> if (CodeStore.listProjects(c).contains(p)) m.project = p }
                o.optString("i").takeIf { it.isNotBlank() }?.let { f -> java.io.File(f).takeIf { it.exists() }?.let { m.images = listOf(it) } }
                msgs.add(m)
            }
        } catch (_: Exception) {}
    }

    private fun save(c: Context) {
        try {
            val a = JSONArray()
            msgs.takeLast(40).forEach { m ->
                if (m.text.isNotBlank()) a.put(JSONObject().put("r", m.role).put("t", m.text.take(60_000)).put("p", m.project ?: "").put("i", m.images.firstOrNull()?.absolutePath ?: ""))
            }
            c.getSharedPreferences("divya", 0).edit().putString("proChat", a.toString()).apply()
        } catch (_: Exception) {}
    }

    fun newChat(c: Context) { stop(); msgs.clear(); baseZip = null; c.getSharedPreferences("divya", 0).edit().remove("proBaseZip").apply(); save(c) }
    @Volatile var userStopped = false
    fun stop() { userStopped = true; cancel.set(true); try { ProEngine.curCall?.cancel() } catch (_: Exception) {} }

    private fun systemPrompt(c: Context): String {
        val p = c.getSharedPreferences("divya", 0)
        val custom = p.getString("proCustom", "") ?: ""
        return ProEngine.SYSTEM + Memory.brief(c) + " The user's name for you to call them: ${p.getString("call", "Sir")}. Today's date: ${java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US).format(java.util.Date())}." +
            (if (custom.isNotBlank()) " USER'S CUSTOM INSTRUCTIONS: $custom" else "")
    }

    private val ZIP_WORDS = listOf("zip", "project", "file", "code", "bug", "fix", "error", "add", "change", "upgrade", "update", "build",
        "app", "feature", "kotlin", "gradle", "screen", "banao", "badlo", "theek", "sahi", "dekho", "improve", "optimi", "refactor", ".kt", ".xml")

    private fun contents(hist: List<PMsg>, mode: Int): JSONArray {
        val arr = JSONArray()
        // ZIP sirf sabse naye zip wale message mein, aur wo bhi chhota/relevant (poora ZIP har baar bhejna bahut slow tha)
        val zipHolder = hist.lastOrNull { m -> m.atts.any { it.zipFile != null } }
        val lastUser = hist.lastOrNull { it.role == "user" }
        val q = lastUser?.text.orEmpty()
        val ql = q.lowercase()
        val light = lastUser !== zipHolder && q.length <= 40 && ZIP_WORDS.none { ql.contains(it) }
        val zipBudget = when (mode) { 0 -> 70_000; 1 -> 110_000; else -> 180_000 }
        fun attText(m: PMsg, a: PAtt): String? {
            val zf = a.zipFile
            if (zf != null) return if (m === zipHolder) ZipCtx.build(zf, q + " " + m.text, zipBudget, light) else "[Pehle attach kiya gaya ZIP: ${a.name}]"
            return a.text
        }
        var budget = 900_000
        val keep = ArrayList<PMsg>()
        for (m in hist.asReversed()) {          // sabse naya pehle, budget khatam hone par purana chhod do
            if (m.text.isBlank() && m.atts.isEmpty()) continue
            budget -= m.text.length + m.atts.sumOf { a -> if (a.zipFile != null) (if (m === zipHolder) zipBudget else 60) else (a.text?.length ?: 0) + (a.b64?.length ?: 0) / 4 }
            if (budget < 0 && keep.isNotEmpty()) break
            keep.add(m)
        }
        for (m in keep.asReversed()) {
            val parts = JSONArray()
            for (a in m.atts) {
                if (a.b64 != null) parts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", a.mime).put("data", a.b64)))
                else {
                    val t = attText(m, a)
                    if (t != null) parts.put(JSONObject().put("text", "[Attached file: ${a.name}]\n```\n$t\n```"))
                }
            }
            if (m.text.isNotBlank()) parts.put(JSONObject().put("text", m.text))
            if (parts.length() == 0) continue
            arr.put(JSONObject().put("role", if (m.role == "user") "user" else "model").put("parts", parts))
        }
        return arr
    }
    fun send(c: Context, text: String, atts: List<PAtt>) {
        if (busy || (text.isBlank() && atts.isEmpty())) return
        val u = PMsg("user", text.trim(), atts)
        atts.firstOrNull { it.zipFile != null }?.let { at -> val it = at.zipFile!!; baseZip = it; c.getSharedPreferences("divya", 0).edit().putString("proBaseZip", it.absolutePath).putString("proBaseName", at.name).apply() }
        msgs.add(u)
        // v2.40: yaadein aur habits - sirf message ki shuruaat wale saaf commands (general chat ko na roke)
        val personalCmd = Regex("(?i)^(divya[,\\s]+)?(yaad rakh|remember that|bhool jao|bhul jao|mujhe kya yaad hai|habits?\\s|\\S+\\s+(piya|pi li|pi liya|kar li|kar liya|khaya|done)\\s*[.!]?$)")
        if (atts.isEmpty() && personalCmd.containsMatchIn(text.trim())) {
            val reply = try { Memory.handle(c, text.trim()) ?: Habits.handle(c, text.trim()) } catch (_: Exception) { null }
            if (reply != null) { msgs.add(PMsg("model", reply)); return }
        }
        if (atts.isEmpty() && ImageGen.isRequest(text)) { genImage(c, text); return }
        go(c, text.trim().startsWith("/project") || projectMode, text.trim().removePrefix("/project").trim())
        projectMode = false
    }

    /** "... ki image bana do" -> image ban kar chat mein dikhti hai. */
    private fun genImage(c: Context, req: String) {
        val m = PMsg("model"); m.busy = true; m.status = "image bana rahi hoon…"
        msgs.add(m); busy = true; userStopped = false; startedAt = System.currentTimeMillis()
        val ctx = c.applicationContext
        Thread {
            try {
                val r = ImageGen.generate(ctx, req) { s -> main.post { m.status = s } }
                main.post { m.images = listOf(r.file); m.text = "🖼️ Image ban gayi (${r.via}): " + ImageGen.cleanPrompt(req).take(120) }
            } catch (e: Exception) {
                main.post { m.text = "⚠️ ${e.message}" }
            } finally {
                main.post { m.busy = false; m.status = ""; busy = false; save(ctx) }
            }
        }.start()
    }

    fun regenerate(c: Context) {
        if (busy) return
        if (msgs.lastOrNull()?.role == "model") msgs.removeAt(msgs.lastIndex)
        if (msgs.lastOrNull()?.role != "user") return
        go(c, false, "")
    }

    private fun go(c: Context, project: Boolean, projectReq: String) {
        val m = PMsg("model"); m.busy = true
        msgs.add(m); busy = true; userStopped = false; cancel.set(false); startedAt = System.currentTimeMillis()
        val hist = msgs.toList().dropLast(1)
        val md = mode; val wb = web
        val ctx = c.applicationContext
        // Intezaar ke dauran seconds + model ka naam dikhao (taaki pata chale atka nahi hai)
        val t0 = System.currentTimeMillis()
        val tick = object : Runnable {
            override fun run() {
                if (!m.busy) return
                if (!project) m.status = if (m.text.isBlank()) "soch rahi hoon… ${(System.currentTimeMillis() - t0) / 1000}s • ${ProEngine.lastModel}" else ""
                main.postDelayed(this, 1000)
            }
        }
        main.postDelayed(tick, 1000)
        Thread {
            try {
                if (project) {
                    val req = projectReq.ifBlank { hist.lastOrNull { it.role == "user" }?.text ?: "" }
                    val chatCtx = hist.dropLast(1).takeLast(6).joinToString("\n") { "${it.role}: ${it.text.take(1500)}" }
                    val t0 = System.currentTimeMillis()
                    val r = ProjectGen.build(ctx, req, chatCtx, md, cancel) { s -> main.post { m.status = s } }
                    val secs = (System.currentTimeMillis() - t0) / 1000
                    val sb = StringBuilder("✅ **${r.name}** ready - ${r.files.size} files, ${secs} sec mein.\n\n${r.summary}\n\n**Files**\n")
                    r.files.forEach { sb.append("- `").append(it).append("`\n") }
                    if (r.run.isNotBlank()) sb.append("\n**Chalane ka tareeka**\n").append(r.run).append("\n")
                    if (r.failed.isNotEmpty()) sb.append("\n⚠️ Ye files nahi ban payi (dobara try karo): ").append(r.failed.joinToString().take(400)).append("\n")
                    if (r.issues.isNotEmpty()) sb.append("\n⚠️ Check: ").append(r.issues.joinToString("; ")).append("\n")
                    sb.append("\nneeche **ZIP** button se download/share karo.")
                    main.post { m.text = sb.toString(); m.project = r.name }
                } else {
                    val last = StringBuilder(); var lastPost = 0L
                    val sys = systemPrompt(ctx) + (if (baseZip != null) " PROJECT UPDATE: the user attached their project as a ZIP (the conversation has the file tree, FULL files as '=== FILE: path ===' and OUTLINE-only entries for the rest; only rewrite files you have in FULL, otherwise ask the user to name the file). " +
                        "When asked to change/fix/add something, output the COMPLETE new content of EVERY file you change or add (never diffs or partial snippets), each preceded by its exact path from the ZIP in bold on its own line like **app/src/main/java/com/x/File.kt**, in a fenced code block. Do not repeat unchanged files. After the code, list in 2-3 lines what you changed. The app will merge them into an updated ZIP automatically; mention that the user taps '🔄 Updated ZIP'." else "")
                    val full = ProEngine.stream(ctx, sys, contents(hist, md), md, wb, cancel) { d ->
                        last.append(d)
                        val now = System.currentTimeMillis()
                        if (now - lastPost > 80) { lastPost = now; val s = last.toString(); main.post { m.text = s } }
                    }
                    main.post { m.text = if (full.isBlank() && userStopped) "(rok diya)" else full.ifBlank { "(khali jawab - dobara try karo)" } }
                }
            } catch (e: Exception) {
                val msg = if (userStopped) "(rok diya)" else "⚠️ ${e.message}"
                main.post { m.text = if (m.text.isBlank() || m.text.startsWith("⚠️")) msg else m.text + "\n\n" + msg }
            } finally {
                main.post { m.busy = false; m.status = ""; busy = false; usedModel = ProEngine.lastModel; save(ctx) }
            }
        }.start()
    }

    /** Kisi jawab ke code-blocks se ZIP project. */
    fun exportReply(c: Context, m: PMsg) {
        val n = ZipOut.export(c, m.text)
        if (n == null) Toast.makeText(c, "Is jawab mein code nahi mila", Toast.LENGTH_SHORT).show() else { m.project = n; save(c) }
    }
}

// ---------------- Markdown ----------------
private class Blk(val code: Boolean, val lang: String, val body: String)

private fun splitBlocks(t: String): List<Blk> {
    val out = ArrayList<Blk>(); val cur = StringBuilder(); var inCode = false; var lang = ""
    for (line in t.split("\n")) {
        val tl = line.trimStart()
        if (tl.startsWith("```")) {
            if (!inCode) {
                if (cur.isNotEmpty()) { out.add(Blk(false, "", cur.toString().trimEnd('\n'))); cur.setLength(0) }
                inCode = true; lang = tl.removePrefix("```").trim()
            } else { out.add(Blk(true, lang, cur.toString().trimEnd('\n'))); cur.setLength(0); inCode = false }
        } else cur.append(line).append('\n')
    }
    if (cur.isNotEmpty() || inCode) out.add(Blk(inCode, if (inCode) lang else "", cur.toString().trimEnd('\n')))
    return out
}

private fun inlineMd(s: String, codeBg: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        if (s.startsWith("**", i)) {
            val e = s.indexOf("**", i + 2)
            if (e > i + 2) { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(i + 2, e)) }; i = e + 2; continue }
        } else if (s[i] == '`') {
            val e = s.indexOf('`', i + 1)
            if (e > i + 1) { withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(s.substring(i + 1, e)) }; i = e + 1; continue }
        }
        append(s[i]); i++
    }
}

@Composable
private fun CodeBlock(lang: String, code: String) {
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF0D1117))) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF1B2330)).padding(start = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(lang.ifBlank { "code" }, color = Color(0xFF9FB3C8), fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextButton({ clip.setText(AnnotatedString(code)); Toast.makeText(ctx, "Copy ho gaya", Toast.LENGTH_SHORT).show() },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Copy", fontSize = 12.sp, color = Color(0xFF29B6FF)) }
        }
        Text(code, color = Color(0xFFE6EDF3), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(10.dp), softWrap = false)
    }
}

@Composable
private fun TableLine(th: UiTheme, t: String) {
    val sep = t.replace("|", "").replace("-", "").replace(":", "").replace(" ", "").isEmpty()
    if (!sep) Text(t, color = th.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, softWrap = false, modifier = Modifier.horizontalScroll(rememberScrollState()))
}

@Composable
private fun MdText(th: UiTheme, text: String) {
    val blocks = remember(text) { splitBlocks(text) }
    val codeBg = th.text.copy(alpha = 0.15f)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { b ->
            if (b.code) CodeBlock(b.lang, b.body)
            else b.body.split("\n").forEach { raw ->
                val l = raw.trimEnd()
                val t = l.trimStart()
                when {
                    t.isEmpty() -> Spacer(Modifier.height(2.dp))
                    t.startsWith("### ") -> Text(inlineMd(t.drop(4), codeBg), color = th.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    t.startsWith("## ") -> Text(inlineMd(t.drop(3), codeBg), color = th.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    t.startsWith("# ") -> Text(inlineMd(t.drop(2), codeBg), color = th.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    t == "---" || t == "***" -> HorizontalDivider(color = th.text.copy(alpha = 0.25f))
                    t.startsWith("- ") || t.startsWith("* ") || t.startsWith("• ") ->
                        Text(inlineMd("•  " + t.drop(2), codeBg), color = th.text, fontSize = 14.sp, modifier = Modifier.padding(start = ((l.length - t.length) * 4 + 6).dp))
                    Regex("^\\d+[.)] .*").matches(t) -> Text(inlineMd(t, codeBg), color = th.text, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
                    t.startsWith("> ") -> Text(inlineMd(t.drop(2), codeBg), color = th.accent, fontSize = 14.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(start = 8.dp))
                    t.startsWith("|") -> TableLine(th, t)
                    else -> Text(inlineMd(l, codeBg), color = th.text, fontSize = 14.sp)
                }
            }
        }
    }
}

// ---------------- Attachment loading ----------------
private fun displayName(c: Context, uri: Uri): String {
    try {
        c.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
            if (cur.moveToFirst()) cur.getString(0)?.let { return it }
        }
    } catch (_: Exception) {}
    return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
}

private val BIN_EXT = setOf("png", "jpg", "jpeg", "gif", "webp", "ico", "keystore", "jks", "jar", "apk", "aar", "so", "zip", "class", "dex", "mp3", "mp4", "wav", "ogg", "ttf", "otf", "woff", "woff2", "pdf", "bin", "db", "ttc")

/** ZIP ko text mein badalo (binary/build/.git chhod kar). Original ZIP cache mein rakhte hain taaki baad mein "Updated ZIP" bane. */
private fun zipAtt(c: Context, uri: Uri, name: String): PAtt {
    val dir = java.io.File(c.filesDir, "base").apply { mkdirs() }
    val f = java.io.File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "project.zip" })
    c.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } } ?: throw AttErr("ZIP khul nahi payi")
    val sb = StringBuilder(); var count = 0; var skipped = 0; var capped = false
    java.util.zip.ZipInputStream(f.inputStream()).use { z ->
        var e = z.nextEntry
        while (e != null) {
            if (!e.isDirectory) {
                val low = e.name.lowercase()
                val ext = low.substringAfterLast('.', "")
                val skip = ext in BIN_EXT || low.contains("/build/") || low.contains("/.git/") || low.contains("node_modules/") || low.contains("/.gradle/") || low.contains("/.idea/")
                if (skip) skipped++
                else {
                    val bytes = z.readBytes()
                    if (bytes.size > 150_000 || bytes.take(2000).any { it.toInt() == 0 }) skipped++
                    else if (sb.length + bytes.size > 700_000) capped = true
                    else { sb.append("\n=== FILE: ").append(e.name).append(" ===\n").append(String(bytes)); count++ }
                }
            }
            e = z.nextEntry
        }
    }
    if (count == 0) throw AttErr("ZIP mein padhne layak text/code file nahi mili")
    val head = "Project ZIP '$name': $count text files included" + (if (skipped > 0) ", $skipped binary/bade files skipped" else "") + (if (capped) ", BAKI FILES size limit ki wajah se chhooti" else "") + "."
    return PAtt(name, "application/zip", null, head, f)
}

internal fun loadAtt(c: Context, uri: Uri): PAtt {
    val name = displayName(c, uri)
    val mime = c.contentResolver.getType(uri) ?: "application/octet-stream"
    try {
        if (name.lowercase().endsWith(".zip") || mime.contains("zip")) return zipAtt(c, uri, name)
        if (mime.startsWith("image/")) {
            val bytes = c.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw AttErr("$name khul nahi payi")
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            var s = 1; while (o.outWidth / s > 1800 || o.outHeight / s > 1800) s *= 2
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = s }) ?: throw AttErr("$name image nahi padh paya")
            val bo = java.io.ByteArrayOutputStream(); bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, bo)
            return PAtt(name, "image/jpeg", Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP), null)
        }
        if (mime.startsWith("video/") || mime.startsWith("audio/")) {
            val len = try { c.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L } catch (_: Exception) { -1L }
            if (len > 12_000_000) throw AttErr("$name bahut badi hai (video/audio 12MB tak, chhoti clip do)")
            val vb = c.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw AttErr("$name khul nahi payi")
            if (vb.size > 12_000_000) throw AttErr("$name bahut badi hai (video/audio 12MB tak, chhoti clip do)")
            return PAtt(name, mime, Base64.encodeToString(vb, Base64.NO_WRAP), null)
        }
        val bytes = c.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw AttErr("$name khul nahi payi")
        if (mime == "application/pdf" || name.lowercase().endsWith(".pdf")) {
            if (bytes.size > 12_000_000) throw AttErr("$name PDF bahut badi hai (12MB tak)")
            return PAtt(name, "application/pdf", Base64.encodeToString(bytes, Base64.NO_WRAP), null)
        }
        if (bytes.take(2000).any { it.toInt() == 0 }) throw AttErr("$name binary file hai. Text/code, image, PDF ya ZIP do")
        return PAtt(name, mime, null, String(bytes).take(250_000))
    } catch (e: AttErr) { throw e }
    catch (e: Exception) { throw AttErr("$name nahi khuli: ${e.message}") }
}

// ---------------- Screen ----------------
@Composable
fun ProChatScreen(th: UiTheme, openCode: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("divya", 0) }
    val clip = LocalClipboardManager.current
    val main = remember { Handler(Looper.getMainLooper()) }
    var input by remember { mutableStateOf("") }
    val atts = remember { mutableStateListOf<PAtt>() }
    var showCfg by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val msgs = ProState.msgs
    LaunchedEffect(Unit) { ProState.load(ctx) }
    LaunchedEffect(ProBus.pending) { ProBus.pending?.let { ProBus.pending = null; ProState.send(ctx, it, emptyList()) } }
    val tail = msgs.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(msgs.size, tail / 150) {
        if (msgs.isNotEmpty() && !listState.isScrollInProgress) listState.scrollToItem(msgs.lastIndex, Int.MAX_VALUE / 4)
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        Thread {
            uris.take(5).forEach { u ->
                try { val a = loadAtt(ctx, u); main.post { atts.add(a); Toast.makeText(ctx, "📎 ${a.name} jud gayi", Toast.LENGTH_SHORT).show() } }
                catch (e: Exception) { main.post { Toast.makeText(ctx, e.message ?: "File nahi khuli", Toast.LENGTH_LONG).show() } }
            }
        }.start()
    }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { input = (input + " " + it).trim() }
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(focusedTextColor = th.text, unfocusedTextColor = th.text)

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val ver = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (_: Exception) { "" } }
            Column(Modifier.weight(1f)) {
                Text("Divya Pro Chat", color = th.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("v$ver" + (if (ProState.usedModel.isNotBlank()) "  •  ${ProState.usedModel}" else ""), color = th.text.copy(alpha = 0.5f), fontSize = 10.sp)
            }
            IconButton({ clip.setText(AnnotatedString(msgs.joinToString("\n\n") { (if (it.role == "user") "Aap: " else "Divya: ") + it.text })); Toast.makeText(ctx, "Poori chat copy ho gayi", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Filled.ContentCopy, "Poori chat copy", tint = th.accent) }
            IconButton({ ProState.newChat(ctx); input = ""; atts.clear() }) { Icon(Icons.Filled.Add, "Nayi chat", tint = th.accent) }
            IconButton({ showCfg = true }) { Icon(Icons.Filled.Settings, "Settings", tint = th.accent) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("⚡ Fast", "🧠 Smart", "🔬 Pro").forEachIndexed { i, n -> FilterChip(ProState.mode == i, { ProState.mode = i }, { Text(n, fontSize = 12.sp) }) }
            FilterChip(ProState.web, { ProState.web = !ProState.web }, { Text("🌐 Web", fontSize = 12.sp) })
            FilterChip(ProState.projectMode, { ProState.projectMode = !ProState.projectMode }, { Text("📦 Project (ZIP)", fontSize = 12.sp) })
        }
        if (ProState.projectMode) Text("Project mode ON: ab jo likhoge uska poora project (kai files) parallel mein bankar ZIP milega.", color = th.accent, fontSize = 11.sp)

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (msgs.isEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Kuch bhi poochho ya banwao. Badi coding bhi seconds mein 🚀", color = th.text.copy(alpha = 0.8f), fontSize = 14.sp)
                    listOf("📦 Todo app banao (HTML/CSS/JS) ZIP mein", "🐍 Python mein Telegram bot ka poora code", "🐞 Ye error fix karo (code paste karunga)", "📱 Android Kotlin Compose ka login screen project").forEach { s ->
                        Text(s, color = th.text, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(th.accent.copy(alpha = 0.2f))
                            .clickable { if (s.startsWith("📦") || s.startsWith("📱")) ProState.projectMode = true; input = s.drop(2).trim() }.padding(10.dp))
                    }
                }
            }
            items(msgs) { m ->
                val me = m.role == "user"
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (me) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column(Modifier.fillMaxWidth(if (me) 0.88f else 1f).clip(RoundedCornerShape(14.dp))
                        .background(th.accent.copy(alpha = if (me) 0.42f else 0.16f)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        m.atts.forEach { Text("📎 ${it.name}", color = th.text.copy(alpha = 0.8f), fontSize = 11.sp) }
                        m.images.forEach { f -> GenImage(th, f) }
                        if (m.text.isNotBlank()) SelectionContainer { if (me) Text(m.text, color = th.text, fontSize = 14.sp) else MdText(th, m.text) }
                        if (m.busy) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = th.accent)
                                Text(m.status.ifBlank { if (m.text.isBlank()) "soch rahi hoon…" else "likh rahi hoon…" }, color = th.text.copy(alpha = 0.7f), fontSize = 12.sp)
                            }
                        }
                    }
                    if (me && m.text.isNotBlank()) IconButton({ clip.setText(AnnotatedString(m.text)); Toast.makeText(ctx, "Copy ho gaya", Toast.LENGTH_SHORT).show() }, Modifier.size(30.dp)) {
                        Icon(Icons.Filled.ContentCopy, "Copy", tint = th.text.copy(alpha = 0.5f), modifier = Modifier.size(16.dp)) }
                    if (!me && !m.busy && m.text.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            IconButton({ clip.setText(AnnotatedString(m.text)); Toast.makeText(ctx, "Copy ho gaya", Toast.LENGTH_SHORT).show() }, Modifier.size(34.dp)) {
                                Icon(Icons.Filled.ContentCopy, "Copy", tint = th.text.copy(alpha = 0.7f), modifier = Modifier.size(18.dp)) }
                            if (m === msgs.lastOrNull() && !ProState.busy) IconButton({ ProState.regenerate(ctx) }, Modifier.size(34.dp)) {
                                Icon(Icons.Filled.Refresh, "Dobara", tint = th.text.copy(alpha = 0.7f), modifier = Modifier.size(18.dp)) }
                            val base = ProState.baseZip
                            if (base != null && ZipOut.hasCode(m.text)) AssistChip({
                                val allText = msgs.take(msgs.indexOf(m) + 1).filter { it.role != "user" }.joinToString("\n\n") { it.text }
                                val origName = ctx.getSharedPreferences("divya", 0).getString("proBaseName", "") ?: ""
                                val r = try { ZipOut.exportMerged(ctx, allText, base, origName) } catch (e: Exception) { null }
                                if (r == null) Toast.makeText(ctx, "Is jawab mein path wali file nahi mili (Divya se bolo: har file ka path likhkar do)", Toast.LENGTH_LONG).show()
                                else {
                                    val n = ZipOut.saveFileToDownloads(ctx, r.first)
                                    if (n != null) Toast.makeText(ctx, "Downloads mein save: $n (${r.second})", Toast.LENGTH_LONG).show()
                                    else try { ZipOut.shareFile(ctx, r.first) } catch (_: Exception) {}
                                }
                            }, { Text("🔄 Updated ZIP", fontSize = 12.sp) })
                            val pr = m.project
                            if (pr == null && base == null && ZipOut.hasCode(m.text)) AssistChip({ ProState.exportReply(ctx, m) }, { Text("📦 Code ka ZIP", fontSize = 12.sp) })
                            if (pr != null) {
                                AssistChip({ try { ZipOut.share(ctx, pr) } catch (e: Exception) { Toast.makeText(ctx, "Share nahi hua: ${e.message}", Toast.LENGTH_LONG).show() } }, { Text("📤 ZIP share", fontSize = 12.sp) })
                                AssistChip({
                                    val n = ZipOut.saveToDownloads(ctx, pr)
                                    if (n != null) Toast.makeText(ctx, "Downloads mein save: $n", Toast.LENGTH_LONG).show()
                                    else try { ZipOut.share(ctx, pr) } catch (_: Exception) {}
                                }, { Text("⬇️ Downloads", fontSize = 12.sp) })
                                if (CodeStore.files(ctx, pr).contains("index.html")) AssistChip({ CodeBus.previewProject = pr; CodeBus.previewTick++ }, { Text("👁 Preview", fontSize = 12.sp) })
                                AssistChip({ openCode() }, { Text("📂 Files", fontSize = 12.sp) })
                            }
                        }
                    }
                }
            }
        }

        if (atts.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            atts.toList().forEach { a -> AssistChip({ atts.remove(a) }, { Text("✖ " + a.name.take(22), fontSize = 11.sp) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ pick.launch("*/*") }) { Icon(Icons.Filled.AttachFile, "File", tint = th.accent) }
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), maxLines = 6, colors = fieldColors,
                placeholder = { Text(if (ProState.projectMode) "Kaisa project chahiye?" else "Divya Pro se poochho…") })
            IconButton({
                try { speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)) }
                catch (_: Exception) { Toast.makeText(ctx, "Voice typing is phone mein nahi hai", Toast.LENGTH_SHORT).show() }
            }) { Icon(Icons.Filled.Mic, "Bolo", tint = th.accent) }
            // Send hamesha wahi jagah rehta hai (jawab chalte waqt bhi). Stop alag button hai, taaki galti se tap na ho.
            IconButton({
                if (!ProState.busy && (input.isNotBlank() || atts.isNotEmpty())) { ProState.send(ctx, input, atts.toList()); input = ""; atts.clear() }
            }) { Icon(Icons.Filled.Send, "Bhejo", tint = th.accent.copy(alpha = if (ProState.busy) 0.3f else 1f)) }
        }
        if (ProState.busy) TextButton({ ProState.stop() }, Modifier.align(Alignment.CenterHorizontally)) {
            Text("⏹ Jawab roko", color = Color(0xFFE53935), fontSize = 13.sp)
        }
    }

    if (showCfg) {
        val oldIds = setOf("gemini-2.5-flash", "gemini-2.5-pro")
        var fm by remember { mutableStateOf((prefs.getString("codeModel", "") ?: "").takeIf { it !in oldIds } ?: "") }
        var pm by remember { mutableStateOf((prefs.getString("proModel", "") ?: "").takeIf { it !in oldIds } ?: "") }
        var cu by remember { mutableStateOf(prefs.getString("proCustom", "") ?: "") }
        var cc by remember { mutableStateOf(prefs.getInt("proConc", 4).toFloat()) }
        AlertDialog(onDismissRequest = { showCfg = false },
            confirmButton = { TextButton({
                prefs.edit().putString("codeModel", fm.trim()).putString("proModel", pm.trim()).putString("proCustom", cu.trim()).putInt("proConc", cc.toInt()).apply()
                showCfg = false }) { Text("Save") } },
            dismissButton = { TextButton({ showCfg = false }) { Text("Cancel") } },
            title = { Text("Pro Chat settings") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(fm, { fm = it }, label = { Text("Fast/Smart model ID (khali = auto)") }, singleLine = true,
                    placeholder = { Text(prefs.getString("autoFast", "")?.ifBlank { "auto" } ?: "auto") })
                OutlinedTextField(pm, { pm = it }, label = { Text("Pro model ID (khali = auto)") }, singleLine = true,
                    placeholder = { Text(prefs.getString("autoPro", "")?.ifBlank { "auto" } ?: "auto") })
                var diag by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
                var diagBusy by remember { mutableStateOf(false) }
                TextButton({
                    if (!diagBusy) { diagBusy = true; diag = null
                        Thread { val r = ProEngine.diagnose(ctx); main.post { diag = r; diagBusy = false } }.start() }
                }) { Text(if (diagBusy) "Test chal raha hai… (10-20 sec)" else "🔍 Models test (kaun sa chal raha hai)") }
                diag?.forEach { (m, st) ->
                    TextButton({ if (st == "OK") { fm = m; Toast.makeText(ctx, "Fast model: $m - Save dabao", Toast.LENGTH_LONG).show() } }) {
                        Text((if (st == "OK") "✅ " else "❌ ") + m + "  →  " + st, fontSize = 12.sp)
                    }
                }
                if (diag != null) Text("✅ wale model par tap karo to wahi Fast model ban jayega (phir Save).", fontSize = 11.sp)
                OutlinedTextField(cu, { cu = it }, label = { Text("Custom instructions (optional)") }, minLines = 2, maxLines = 5)
                Text("Project mode parallel files: ${cc.toInt()}", fontSize = 13.sp)
                Slider(cc, { cc = it }, valueRange = 1f..8f, steps = 6)
                Text("Free API key par limit ~10 requests/min hoti hai, to 429 aaye to ise 1-2 rakho. Billing wali key par 6-8 tak.", fontSize = 11.sp)
            } })
    }
}


@Composable
private fun GenImage(th: UiTheme, f: java.io.File) {
    val ctx = LocalContext.current
    val bmp = remember(f.absolutePath) {
        try {
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(f.absolutePath, o)
            var sm = 1; while (o.outWidth / sm > 1400 || o.outHeight / sm > 1400) sm *= 2
            android.graphics.BitmapFactory.decodeFile(f.absolutePath, android.graphics.BitmapFactory.Options().apply { inSampleSize = sm })
        } catch (_: Throwable) { null }
    }
    if (bmp != null) Image(bmp.asImageBitmap(), "Bani hui image", Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.FillWidth)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AssistChip({ Toast.makeText(ctx, if (ImageGen.saveToGallery(ctx, f)) "Gallery mein save ho gayi" else "Ye Android purana hai - Share se save karo", Toast.LENGTH_SHORT).show() }, { Text("⬇️ Gallery", fontSize = 12.sp) })
        AssistChip({ try { ImageGen.share(ctx, f) } catch (e: Exception) { Toast.makeText(ctx, "Share nahi hua: ${e.message}", Toast.LENGTH_LONG).show() } }, { Text("📤 Share", fontSize = 12.sp) })
    }
}
