package com.divya.ai

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.media.*
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Video Gen ki live state (screen aur voice tool dono yahin se progress dikhate hain). */
object VideoBus {
    var busy by mutableStateOf(false)
    var stage by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var lastPath by mutableStateOf<String?>(null)
    /** Voice se video bani to Video screen khulwane ke liye. */
    var openTick by mutableStateOf(0)
    /** Instant live preview (mp4 export background mein chalti rehti hai). */
    var live by mutableStateOf<VideoGen.Live?>(null)
}

/**
 * Topic bolo -> 9:16 video (720x1280): Gemini script, phone ki Hindi/English TTS awaaz, subtitles, background visuals.
 * Sab phone par hi encode hota hai (MediaCodec + MediaMuxer), koi extra library nahi. Visuals: Pexels free key ho to
 * asli photos (Settings: "pexelsKey"), warna neon animated background.
 */
object VideoGen {
    private const val TAG = "DivyaVideoGen"
    const val W = 576      // encode size (halka = tez)
    const val H = 1024
    const val FPS = 15
    private const val DW = 720f   // design space (drawing coordinates)
    private const val DH = 1280f

    class Scene(val speak: String, val caption: String, val query: String, val img: String = "")
    class Plan(val title: String, val scenes: List<Scene>, val character: String = "", val style: String = "")
    class Result(val path: String, val title: String, val seconds: Int, val scenes: Int, val visuals: String)
    private class Pcm(val sr: Int, val s: ShortArray)
    class Live(val renderer: Renderer, val total: Double, val startNs: Long) { var track: AudioTrack? = null }

    @Volatile private var imgErr = ""
    private val imgHttp = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(75, TimeUnit.SECONDS).build()
    private val http = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()

    fun dir(c: Context): File = (c.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(c.filesDir, "videos")).also { it.mkdirs() }
    fun list(c: Context): List<File> = dir(c).listFiles { f -> f.extension == "mp4" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** UI aur voice dono yahin se. Result string Live model ko jata hai ("done: ..." / "failed: ..."). */
    fun createTracked(c: Context, topic: String, lang: String, secs: Int, style: String, script: String = "", captions: String = "", visuals: String = "ai"): String {
        if (VideoBus.busy) return "failed: ek video pehle se ban rahi hai, thoda ruko."
        VideoBus.busy = true; VideoBus.error = null; VideoBus.stage = "Shuru…"
        return try {
            val r = create(c, topic, lang, secs, style, script, captions, visuals) { s -> VideoBus.stage = s; BgTasks.stage(s) }
            VideoBus.lastPath = r.path
            "done: video '${r.title}' ready (${r.seconds} sec, ${r.scenes} scenes, visuals: ${r.visuals}). Phone mein Movies/DivyaVideos mein save hai aur Video screen mein dikh rahi hai - wahan se Share kar sakte ho."
        } catch (e: Throwable) {
            Log.e(TAG, "video fail", e)
            VideoBus.error = e.message ?: e.javaClass.simpleName
            "failed: ${VideoBus.error}"
        } finally {
            VideoBus.live?.track?.let { try { it.stop(); it.release() } catch (_: Exception) {} }
            VideoBus.live = null; VideoBus.busy = false; VideoBus.stage = ""
        }
    }

    fun create(c: Context, topic: String, lang: String, secs: Int, style: String, script: String, captions: String, visuals: String, onStage: (String) -> Unit): Result {
        require(topic.isNotBlank()) { "Topic khali hai" }
        val t0 = System.currentTimeMillis()
        val seconds = secs.coerceIn(10, 60)
        val isEn = lang.startsWith("en", true)
        val ai = visuals != "fast" && visuals != "instant"
        imgErr = ""
        val plan = (if (!ai) localPlan(topic.trim(), script, captions, style) else null) ?: run { onStage(if (ai) "Kahani aur scenes likh rahi hoon…" else "Script likh rahi hoon…"); writePlan(c, topic.trim(), isEn, seconds, style, ai) }

        // visuals background threads mein (AI images ya photos), TTS saath-saath
        val prefs = c.getSharedPreferences("divya", 0)
        val pexels = prefs.getString("pexelsKey", "")?.trim() ?: ""
        val src = prefs.getString("vgImg", "auto") ?: "auto"
        val seed = (System.currentTimeMillis() % 90000).toInt() + 1000
        // SPEED: saari scenes ki images ek saath (parallel). Jaise hi ek image tayyar hoti hai wo `shared` list mein aa jati hai
        // aur live preview use turant dikha deta hai -> preview ko saari images ka intezaar nahi karna padta.
        val pool = Executors.newFixedThreadPool(if (ai) min(8, plan.scenes.size).coerceAtLeast(1) else 3)
        val used = java.util.Collections.synchronizedSet(mutableSetOf<Long>())
        val shared: MutableList<Bitmap?> = java.util.Collections.synchronizedList(MutableList<Bitmap?>(plan.scenes.size) { null })
        val futs: List<Future<Bitmap?>> = plan.scenes.mapIndexed { i, s ->
            pool.submit<Bitmap?> {
                val b = if (ai) aiImage(c, plan, s, seed, src, pexels, used) else if (pexels.isBlank()) null else fetchPhoto(pexels, s.query, used)
                if (b != null) shared[i] = b
                b
            }
        }

        onStage("Awaaz bana rahi hoon…")
        val voices = try { synth(c, plan.scenes, isEn) } catch (e: Throwable) { pool.shutdownNow(); throw e }
        // Preview shuru karne se pehle sirf PEHLI image ka thoda intezaar (max 4 sec). Baaki images video chalte-chalte aati rahengi.
        val firstDl = System.currentTimeMillis() + (if (ai) 4_000L else 1_500L)
        while (System.currentTimeMillis() < firstDl && (if (ai) !futs[0].isDone else futs.any { !it.isDone })) {
            if (ai) onStage("Pehla scene ban raha hai…")
            try { Thread.sleep(80) } catch (_: InterruptedException) { break }
        }

        val sr = voices.first().sr
        val lead = (sr * 0.12).toInt(); val tail = (sr * 0.30).toInt()
        val starts = ArrayList<Double>(); val durs = ArrayList<Double>(); val speakDur = ArrayList<Double>()
        var total = 0
        val pcmAll = ArrayList<Short>(sr * seconds)
        voices.forEach { v ->
            val pcm = if (v.sr == sr) v.s else resample(v.s, v.sr, sr)
            starts.add(total.toDouble() / sr)
            repeat(lead) { pcmAll.add(0) }
            pcm.forEach { pcmAll.add(it) }
            repeat(tail) { pcmAll.add(0) }
            val n = lead + pcm.size + tail
            durs.add(n.toDouble() / sr); speakDur.add(pcm.size.toDouble() / sr)
            total += n
        }
        val audio = ShortArray(pcmAll.size) { pcmAll[it] }
        val totalSec = total.toDouble() / sr

        // INSTANT: awaaz + live video preview abhi shuru, mp4 export peeche chalti rahegi
        // Preview `shared` list se chalta hai: jo images baad mein aati hain wo apne aap dikhne lagti hain.
        val live = Live(Renderer(plan, shared, starts, durs, speakDur), totalSec, System.nanoTime())
        live.track = playAudio(audio, sr)
        VideoBus.live = live
        Handler(Looper.getMainLooper()).post { VideoBus.openTick++ }
        val tmpV = File(c.cacheDir, "vg_v.mp4"); val tmpA = File(c.cacheDir, "vg_a.m4a")
        tmpV.delete(); tmpA.delete()
        var audioErr: Throwable? = null
        val aThread = Thread { try { encodeAudio(audio, sr, tmpA) } catch (e: Throwable) { audioErr = e } }.also { it.start() }

        // mp4 export ke liye baaki images ka intezaar (preview isse nahi rukta)
        if (ai) {
            val dl = System.currentTimeMillis() + 70_000L
            while (futs.any { !it.isDone } && System.currentTimeMillis() < dl) {
                onStage("Baaki AI images aa rahi hain… ${futs.count { it.isDone }}/${futs.size}")
                try { Thread.sleep(200) } catch (_: InterruptedException) { break }
            }
        }
        pool.shutdownNow()
        val bmps: List<Bitmap?> = fillGaps(ArrayList(shared))
        onStage("Video encode ho rahi hai…")
        val visualsTxt = if (bmps.any { it != null }) (if (ai) "AI images" else "photos") else (if (ai) "neon (AI image nahi bani: $imgErr)" else "neon animated")
        encodeVideo(plan, bmps, starts, durs, speakDur, totalSec, tmpV) { p -> onStage("Video encode… ${(p * 100).toInt()}%") }

        aThread.join(); audioErr?.let { throw it }
        onStage("Awaaz + video jod rahi hoon…")
        val name = "divya_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date()) + ".mp4"
        val out = File(dir(c), name)
        merge(tmpV, tmpA, out)
        tmpV.delete(); tmpA.delete()
        Thread { try { saveToGallery(c, out) } catch (e: Exception) { Log.w(TAG, "gallery save", e) } }.start()
        // title ko bhi saath rakhne ke liye chhota sidecar
        try { File(out.path + ".txt").writeText(plan.title + "\n" + plan.scenes.joinToString("\n") { it.caption }) } catch (_: Exception) {}
        // preview khatam hone tak ruko taaki screen par awaaz do baar na chale
        val rem = ((totalSec * 1e9 - (System.nanoTime() - live.startNs)) / 1e6).toLong().coerceIn(0, 60_000)
        if (rem > 0) try { Thread.sleep(rem) } catch (_: InterruptedException) {}
        Log.i(TAG, "video ready in ${(System.currentTimeMillis() - t0) / 1000.0}s")
        return Result(out.absolutePath, plan.title, totalSec.toInt(), plan.scenes.size, visualsTxt)
    }

    // ------------------------------------------------------------------ SCRIPT
    /** Divya (Live model) ne script khud bheji ho ("scene1 | scene2 | ...") to alag Gemini call ki zaroorat nahi -> 2-5 sec bachte hain. */
    private fun localPlan(topic: String, script: String, captions: String, style: String): Plan? {
        val sp = script.split("|").map { it.trim() }.filter { it.isNotEmpty() }.take(8)
        if (sp.isEmpty()) return null
        val cp = captions.split("|").map { it.trim() }
        return Plan(topic.take(40).ifBlank { "Video" }, sp.mapIndexed { i, t -> Scene(t, cp.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: t, topic) }, "", style.lowercase())
    }

    private fun writePlan(c: Context, topic: String, en: Boolean, seconds: Int, style: String, ai: Boolean): Plan {
        val n = if (ai) (seconds / 5).coerceIn(3, 12) else (seconds / 6).coerceIn(2, 7)
        val words = (seconds * if (en) 2.4 else 2.2).toInt()
        val sys = "You write scripts for short vertical social videos (Reels/Shorts). Reply ONLY with JSON."
        val langRule = if (en) "Narration in simple spoken English. caption = same text as speak."
        else "speak = narration in HINDI written in DEVANAGARI script (natural spoken Hindi, simple words, a little English words allowed in Devanagari). caption = the SAME sentence written in Hinglish (Roman letters) for subtitles."
        val user = if (ai) """Topic / story idea: $topic
Make a CINEMATIC STORY style short video (like a viral AI-generated story reel with a consistent main character).
Make exactly $n scenes of about 5 seconds each, about $words words of narration in total.
$langRule
"character" = ONE detailed English visual description of the main character(s), identical in every scene (species/age/clothes/colours/accessories, e.g. "a cute white lion cub wearing a white linen shirt and a thin gold chain"). Use "" if there is no character.
Each scene "image" = a detailed English prompt for a PHOTOREALISTIC cinematic still: camera angle, location, lighting, what the character is doing. Vary the shots (wide, close-up, over-the-shoulder, aerial). Always mention the character. No text, logos or captions inside the image.
Scene 1 = strong hook. Last scene = short ending / follow-like call to action.
JSON format: {"title":"short title (max 6 words)","character":"...","scenes":[{"speak":"...","caption":"...","image":"..."}]}""" else """Topic: $topic
Style: ${style.ifBlank { "facts" }}
Make exactly $n scenes, about $words words of narration in total (about $seconds seconds when spoken).
$langRule
Scene 1 must be a strong hook (question or surprising fact). Last scene: short call to action (follow/like).
query = 2-4 English words describing a good background stock photo for the scene (no people names, no brands).
JSON format: {"title":"short title (max 6 words)","scenes":[{"speak":"...","caption":"...","query":"..."}]}"""
        val raw = CodeGen.ask(c, sys, user)
        var t = raw.trim()
        if (t.startsWith("```")) t = t.substringAfter('\n').substringBeforeLast("```")
        val j = try { JSONObject(t) } catch (e: Exception) { throw CodeGen.GenError("Script ka jawab JSON nahi tha, dobara try karo.") }
        val arr = j.optJSONArray("scenes") ?: JSONArray()
        val scenes = (0 until arr.length()).mapNotNull {
            val o = arr.optJSONObject(it) ?: return@mapNotNull null
            val sp = o.optString("speak").trim()
            if (sp.isBlank()) null else { val im = o.optString("image").trim(); Scene(sp, o.optString("caption").trim().ifBlank { sp }, o.optString("query").trim().ifBlank { im.take(60).ifBlank { topic } }, im) }
        }
        if (scenes.isEmpty()) throw CodeGen.GenError("Script khali aayi, dobara try karo.")
        return Plan(j.optString("title", topic).take(60).ifBlank { topic.take(40) }, scenes, j.optString("character").trim())
    }

    // ------------------------------------------------------------------ AI IMAGES
    private fun fillGaps(l: List<Bitmap?>): List<Bitmap?> {
        if (l.all { it == null }) return l
        return l.mapIndexed { i, b -> b ?: ((i - 1 downTo 0).firstNotNullOfOrNull { l[it] } ?: l.firstNotNullOf { it }) }
    }

    private fun decodeBmp(b0: ByteArray): Bitmap? {
        val bm = BitmapFactory.decodeByteArray(b0, 0, b0.size) ?: return null
        return if (bm.height > 1100) Bitmap.createScaledBitmap(bm, bm.width * 1100 / bm.height, 1100, true).also { if (it !== bm) bm.recycle() } else bm
    }

    private fun buildPrompt(ch: String, s: Scene): String {
        val base = s.img.ifBlank { s.query }
        val withCh = if (ch.isNotBlank() && !base.contains(ch.take(18), true)) "$base, $ch" else base
        return (withCh + ", photorealistic cinematic still, ultra detailed, natural lighting, sharp focus, 9:16 vertical, no text, no watermark").take(900)
    }

    /** Auto: Pollinations (free, bina key) -> Gemini image (aapki key) -> Pexels photo. */
    private fun aiImage(c: Context, plan: Plan, s: Scene, seed: Int, src: String, pexels: String, used: MutableSet<Long>): Bitmap? {
        val prompt = buildPrompt(plan.character, s)
        val order = when (src) { "pollinations" -> listOf("p"); "gemini" -> listOf("g"); "pexels" -> listOf("x"); else -> listOf("p", "g", "x") }
        for (o in order) {
            val b = try {
                when (o) { "p" -> pollinations(prompt, seed, c.getSharedPreferences("divya", 0).getString("vgModel", "turbo") ?: "turbo"); "g" -> geminiImage(c, prompt); else -> if (pexels.isBlank()) null else fetchPhoto(pexels, s.query, used) }
            } catch (e: Exception) { imgErr = "$o: ${e.message}"; Log.w(TAG, "image $o: ${e.message}"); null }
            if (b != null) return b
        }
        return null
    }

    /** SPEED: video 576x1024 par encode hoti hai, isliye image bhi utni hi maango (720x1280 bekaar mein slow thi).
     *  Model "turbo" = bahut tez (2-5 sec); "flux" = behtar quality par slow. Pref "vgModel" se badlo. */
    private fun pollinations(prompt: String, seed: Int, model: String = "turbo"): Bitmap? {
        for (attempt in 0..1) {
            val url = "https://image.pollinations.ai/prompt/" + java.net.URLEncoder.encode(prompt, "UTF-8").replace("+", "%20") +
                "?width=$W&height=$H&model=$model&nologo=true&seed=${seed + attempt}"
            val bytes = imgHttp.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 DivyaAI").build()).execute().use { r ->
                if (!r.isSuccessful) { imgErr = "pollinations ${r.code}"; null } else r.body?.bytes()
            }
            if (bytes != null && bytes.size > 5000) decodeBmp(bytes)?.let { return it }
        }
        return null
    }

    private fun geminiImage(c: Context, prompt: String): Bitmap? {
        val pr = c.getSharedPreferences("divya", 0)
        val key = pr.getString("key", "") ?: ""; if (key.isBlank()) return null
        val model = pr.getString("imgModel", "gemini-2.5-flash-image") ?: "gemini-2.5-flash-image"
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("IMAGE")).put("imageConfig", JSONObject().put("aspectRatio", "9:16")))
            .toString()
        val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").header("x-goog-api-key", key)
            .post(body.toRequestBody("application/json".toMediaType())).build()
        val txt = imgHttp.newCall(req).execute().use { r -> if (!r.isSuccessful) { imgErr = "gemini ${r.code}"; null } else r.body?.string() } ?: return null
        val parts = JSONObject(txt).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return null
        for (i in 0 until parts.length()) {
            val d = parts.getJSONObject(i).optJSONObject("inlineData")?.optString("data")
            if (!d.isNullOrBlank()) return decodeBmp(android.util.Base64.decode(d, android.util.Base64.DEFAULT))
        }
        return null
    }

    // ------------------------------------------------------------------ PHOTOS (Pexels, optional)
    private fun fetchPhoto(key: String, q: String, used: MutableSet<Long>): Bitmap? = try {
        val url = "https://api.pexels.com/v1/search?query=" + java.net.URLEncoder.encode(q, "UTF-8") + "&orientation=portrait&size=medium&per_page=8"
        val txt = http.newCall(Request.Builder().url(url).header("Authorization", key).build()).execute().use { r -> if (!r.isSuccessful) null else r.body?.string() }
        val photos = JSONObject(txt ?: "{}").optJSONArray("photos") ?: JSONArray()
        var pick: JSONObject? = null
        for (i in 0 until photos.length()) { val p = photos.getJSONObject(i); if (used.add(p.optLong("id"))) { pick = p; break } }
        val link = pick?.optJSONObject("src")?.optString("portrait")
        if (link.isNullOrBlank()) null else {
            val bytes = http.newCall(Request.Builder().url(link).build()).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
            bytes?.let { b0 -> val o = BitmapFactory.Options().apply { inSampleSize = 1 }; val bm = BitmapFactory.decodeByteArray(b0, 0, b0.size, o)
                if (bm != null && bm.height > 1100) Bitmap.createScaledBitmap(bm, bm.width * 1100 / bm.height, 1100, true).also { if (it !== bm) bm.recycle() } else bm }
        }
    } catch (e: Exception) { Log.w(TAG, "pexels: ${e.message}"); null }

    // ------------------------------------------------------------------ TTS
    private fun synth(c: Context, scenes: List<Scene>, en: Boolean): List<Pcm> {
        val ready = CountDownLatch(1); var ok = false
        val tts = TextToSpeech(c.applicationContext) { st -> ok = st == TextToSpeech.SUCCESS; ready.countDown() }
        if (!ready.await(10, TimeUnit.SECONDS) || !ok) { tts.shutdown(); throw CodeGen.GenError("Phone ka TTS (Google Text-to-speech) start nahi hua. Settings > Text-to-speech engine check karo.") }
        try {
            val want = if (en) Locale("en", "IN") else Locale("hi", "IN")
            var r = tts.setLanguage(want)
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                r = tts.setLanguage(if (en) Locale.US else Locale.ENGLISH)
                if (!en) Log.w(TAG, "Hindi TTS voice nahi mili, English fallback")
            }
            tts.setSpeechRate(1.12f)
            val files = scenes.indices.map { File(c.cacheDir, "vg_tts_$it.wav").also { f -> f.delete() } }
            val latches = scenes.indices.map { CountDownLatch(1) }
            val errs = BooleanArray(scenes.size)
            fun idx(u: String?) = u?.removePrefix("vg")?.toIntOrNull()?.takeIf { it in scenes.indices }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(u: String?) {}
                override fun onDone(u: String?) { idx(u)?.let { latches[it].countDown() } }
                @Deprecated("old") override fun onError(u: String?) { idx(u)?.let { errs[it] = true; latches[it].countDown() } }
                override fun onError(u: String?, code: Int) { idx(u)?.let { errs[it] = true; latches[it].countDown() } }
            })
            scenes.forEachIndexed { i, s ->
                if (tts.synthesizeToFile(s.speak, Bundle(), files[i], "vg$i") != TextToSpeech.SUCCESS) throw CodeGen.GenError("Scene ${i + 1} ki awaaz queue nahi hui (TTS error).")
            }
            val out = ArrayList<Pcm>()
            scenes.indices.forEach { i ->
                if (!latches[i].await(40, TimeUnit.SECONDS) || errs[i] || !files[i].exists() || files[i].length() < 100)
                    throw CodeGen.GenError("Scene ${i + 1} ki awaaz nahi ban payi (TTS error).")
                out.add(readWav(files[i])); files[i].delete()
            }
            return out
        } finally { tts.shutdown() }
    }

    private fun readWav(f: File): Pcm {
        val b = f.readBytes(); val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        var p = 12; var sr = 22050; var ch = 1; var bits = 16; var off = -1; var len = 0
        while (p + 8 <= b.size) {
            val id = String(b, p, 4, Charsets.US_ASCII); val l = bb.getInt(p + 4)
            if (id == "fmt ") { ch = bb.getShort(p + 10).toInt(); sr = bb.getInt(p + 12); bits = bb.getShort(p + 22).toInt() }
            if (id == "data") { off = p + 8; len = if (l <= 0 || off + l > b.size) b.size - off else l; break }
            if (l < 0) break
            p += 8 + l + (l and 1)
        }
        if (off < 0 || bits != 16 || ch < 1) throw CodeGen.GenError("TTS ki audio file ka format samajh nahi aaya.")
        val n = len / 2 / ch
        val s = ShortArray(n) { i -> var acc = 0; for (k in 0 until ch) acc += bb.getShort(off + (i * ch + k) * 2); (acc / ch).toShort() }
        return Pcm(sr, s)
    }

    private fun resample(s: ShortArray, from: Int, to: Int): ShortArray {
        if (from == to || s.isEmpty()) return s
        val n = (s.size.toLong() * to / from).toInt()
        return ShortArray(n) { i -> val pos = i.toDouble() * from / to; val i0 = pos.toInt().coerceAtMost(s.size - 1); val i1 = (i0 + 1).coerceAtMost(s.size - 1); val fr = pos - i0; (s[i0] * (1 - fr) + s[i1] * fr).toInt().toShort() }
    }

    private fun playAudio(pcm: ShortArray, sr: Int): AudioTrack? = try {
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(pcm.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
        t.write(pcm, 0, pcm.size); t.play(); t
    } catch (e: Exception) { Log.w(TAG, "preview audio: ${e.message}"); null }

    // ------------------------------------------------------------------ AUDIO ENCODE (AAC)
    private fun encodeAudio(pcm: ShortArray, sr: Int, out: File) {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sr, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64000); setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start()
        val mux = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1; var started = false
        val info = MediaCodec.BufferInfo(); var pos = 0; var inDone = false; var outDone = false
        try {
            while (!outDone) {
                if (!inDone) {
                    val ii = codec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val buf = codec.getInputBuffer(ii)!!; buf.clear(); buf.order(ByteOrder.LITTLE_ENDIAN)
                        val n = min(pcm.size - pos, buf.capacity() / 2)
                        val pts = pos * 1_000_000L / sr
                        if (n <= 0) { codec.queueInputBuffer(ii, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { for (k in 0 until n) buf.putShort(pcm[pos + k]); codec.queueInputBuffer(ii, 0, n * 2, pts, 0); pos += n }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 10_000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { track = mux.addTrack(codec.outputFormat); mux.start(); started = true }
                else if (oi >= 0) {
                    val ob = codec.getOutputBuffer(oi)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && started) { ob.position(info.offset); ob.limit(info.offset + info.size); mux.writeSampleData(track, ob, info) }
                    codec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                }
            }
        } finally {
            try { codec.stop() } catch (_: Exception) {}; codec.release()
            try { if (started) mux.stop() } catch (_: Exception) {}; mux.release()
        }
    }

    // ------------------------------------------------------------------ VIDEO ENCODE (H.264 from Canvas)
    private fun encodeVideo(plan: Plan, bmps: List<Bitmap?>, starts: List<Double>, durs: List<Double>, speak: List<Double>,
                            totalSec: Double, out: File, progress: (Float) -> Unit) {
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, W, H).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000); setInteger(MediaFormat.KEY_FRAME_RATE, FPS); setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface(); codec.start()
        val mux = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1; var started = false; var outCount = 0L
        val info = MediaCodec.BufferInfo()

        fun drain(end: Boolean) {
            while (true) {
                val oi = codec.dequeueOutputBuffer(info, if (end) 10_000 else 0)
                when {
                    oi == MediaCodec.INFO_TRY_AGAIN_LATER -> { if (!end) return }
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = mux.addTrack(codec.outputFormat); mux.start(); started = true }
                    oi >= 0 -> {
                        val ob = codec.getOutputBuffer(oi)!!
                        val cfg = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!cfg && info.size > 0 && started) {
                            // Canvas surface par timestamp system clock ka aata hai -> apna frame-counter pts
                            info.presentationTimeUs = outCount * 1_000_000L / FPS; outCount++
                            ob.position(info.offset); ob.limit(info.offset + info.size); mux.writeSampleData(track, ob, info)
                        }
                        codec.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        val r = Renderer(plan, bmps, starts, durs, speak)
        val frames = Math.ceil(totalSec * FPS).toInt()
        try {
            for (f in 0 until frames) {
                val canvas = surface.lockHardwareCanvas()
                try { r.draw(canvas, f.toDouble() / FPS) } finally { surface.unlockCanvasAndPost(canvas) }
                drain(false)
                if (f % 15 == 0) progress(f.toFloat() / frames)
            }
            codec.signalEndOfInputStream(); drain(true)
        } finally {
            try { codec.stop() } catch (_: Exception) {}; codec.release(); surface.release()
            try { if (started) mux.stop() } catch (_: Exception) {}; mux.release()
        }
    }

    /** Ek frame banane wala: background (photo Ken-Burns ya neon), gradient, title, subtitles. */
    class Renderer(val plan: Plan, val bmps: List<Bitmap?>, val starts: List<Double>, val durs: List<Double>, val speak: List<Double>) {
        val gold = 0xFFFFE81F.toInt()
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 60f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.WHITE }
        val tpStroke = TextPaint(tp).apply { style = Paint.Style.STROKE; strokeWidth = 12f; color = Color.BLACK; strokeJoin = Paint.Join.ROUND }
        val titleP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 44f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.BLACK }
        val wmP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f; typeface = Typeface.MONOSPACE; color = gold; alpha = 150 }
        val chunks: List<List<String>> = plan.scenes.map { chunk(it.caption) }
        val layouts = HashMap<String, Pair<StaticLayout, StaticLayout>>()
        val basePalette = intArrayOf(0xFF0B3D91.toInt(), 0xFF6A1B9A.toInt(), 0xFF00695C.toInt(), 0xFFB71C1C.toInt(), 0xFFEF6C00.toInt(), 0xFF283593.toInt(), 0xFF004D40.toInt(), 0xFF880E4F.toInt())
        val palette: IntArray = when {
            plan.style.contains("funny") -> intArrayOf(0xFFFF6F00.toInt(), 0xFFFFB300.toInt(), 0xFFD81B60.toInt(), 0xFF00ACC1.toInt(), 0xFF7CB342.toInt(), 0xFF8E24AA.toInt())
            plan.style.contains("attitude") || plan.style.contains("badmash") -> intArrayOf(0xFF8B0000.toInt(), 0xFF212121.toInt(), 0xFFB71C1C.toInt(), 0xFF37474F.toInt(), 0xFF4A148C.toInt(), 0xFF000000.toInt())
            else -> basePalette
        }
        val grad = LinearGradient(0f, DH * 0.5f, 0f, DH.toFloat(), 0x00000000, 0xAA000000.toInt(), Shader.TileMode.CLAMP)
        val gradTop = LinearGradient(0f, 0f, 0f, DH * 0.2f, 0x99000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)

        fun chunk(t: String): List<String> {
            val w = t.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val out = ArrayList<String>(); var cur = ArrayList<String>()
            for (x in w) { cur.add(x); if (cur.size >= 3 || cur.joinToString(" ").length > 16 || x.endsWith(".") || x.endsWith("?") || x.endsWith(",")) { out.add(cur.joinToString(" ")); cur = ArrayList() } }
            if (cur.isNotEmpty()) out.add(cur.joinToString(" "))
            return out
        }

        fun draw(cv: Canvas, t: Double, ow: Float = W.toFloat(), oh: Float = H.toFloat()) {
            cv.save(); cv.scale(ow / DW, oh / DH)
            drawScene(cv, t)
            cv.restore()
        }

        fun drawScene(cv: Canvas, t: Double) {
            var si = starts.indexOfLast { it <= t }.coerceAtLeast(0)
            if (si >= plan.scenes.size) si = plan.scenes.size - 1
            val local = (t - starts[si]).coerceAtLeast(0.0); val dur = durs[si]
            val prog = (local / dur).coerceIn(0.0, 1.0).toFloat()
            // is scene ki image abhi na aayi ho to pichhli scene ki image dikhao (neon sirf tab jab koi bhi image nahi)
            val bmp = (si downTo 0).firstNotNullOfOrNull { k -> bmps[k]?.takeIf { !it.isRecycled } }
            if (bmp != null) drawPhoto(cv, bmp, prog, si) else drawNeon(cv, t.toFloat(), si)
            if (bmp != null && si + 1 < bmps.size && dur - local < 0.4) { // crossfade agle scene ki image mein
                val nb = bmps[si + 1]
                if (nb != null && nb !== bmp) { p.alpha = (255 * (1 - (dur - local) / 0.4)).toInt().coerceIn(0, 255); drawPhoto(cv, nb, 0f, si + 1); p.alpha = 255 }
            }
            p.shader = grad; cv.drawRect(0f, DH * 0.5f, DW.toFloat(), DH.toFloat(), p)
            p.shader = gradTop; cv.drawRect(0f, 0f, DW.toFloat(), DH * 0.2f, p); p.shader = null

            cv.drawText("DIVYA AI", 36f, 70f, wmP)
            if (t < 2.8 && si == 0 && bmps[0] == null) { // title pill (sirf neon background par)
                val a = (min(1.0, (2.8 - t) / 0.4)).toFloat()
                val tl = StaticLayout.Builder.obtain(plan.title, 0, plan.title.length, titleP, (DW - 160).toInt()).setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(2).build()
                p.color = gold; p.alpha = (255 * a).toInt()
                cv.drawRoundRect(40f, 150f, DW - 40f, 150f + tl.height + 40f, 28f, 28f, p)
                cv.save(); cv.translate(80f, 170f); titleP.alpha = (255 * a).toInt(); tl.draw(cv); cv.restore(); titleP.alpha = 255
                p.alpha = 255
            }
            // subtitles
            val ch = chunks[si]
            if (ch.isNotEmpty()) {
                val sp = speak[si].coerceAtLeast(0.5); val st = (local - 0.12).coerceAtLeast(0.0)
                val frac = (st / sp).coerceIn(0.0, 0.9999)
                val totalChars = ch.sumOf { it.length + 1 }.toDouble(); var acc = 0.0; var idx = ch.size - 1; var cStart = 0.0
                for (i in ch.indices) { val s0 = acc / totalChars; acc += (ch[i].length + 1); if (frac < acc / totalChars) { idx = i; cStart = s0 * sp; break } }
                val since = (st - cStart).toFloat().coerceAtLeast(0f)
                val pop = if (since < 0.14f) 0.88f + 0.12f * (since / 0.14f) else 1f
                val text = ch[idx]
                val (strokeL, fillL) = layouts.getOrPut(text) {
                    Pair(StaticLayout.Builder.obtain(text, 0, text.length, tpStroke, (DW - 120).toInt()).setAlignment(Layout.Alignment.ALIGN_CENTER).build(),
                        StaticLayout.Builder.obtain(text, 0, text.length, tp, (DW - 120).toInt()).setAlignment(Layout.Alignment.ALIGN_CENTER).build())
                }
                cv.save(); cv.translate(60f, DH * 0.72f); cv.scale(pop, pop, (DW - 120) / 2f, fillL.height / 2f)
                strokeL.draw(cv); fillL.draw(cv); cv.restore()
            }
            // scene fade-in
            if (local < 0.25 && si > 0 && bmp == null) { p.color = Color.BLACK; p.alpha = (255 * (1 - local / 0.25)).toInt(); cv.drawRect(0f, 0f, DW.toFloat(), DH.toFloat(), p); p.alpha = 255 }
            if (t < 0.3) { p.color = Color.BLACK; p.alpha = (255 * (1 - t / 0.3)).toInt(); cv.drawRect(0f, 0f, DW.toFloat(), DH.toFloat(), p); p.alpha = 255 }
        }

        fun drawPhoto(cv: Canvas, b: Bitmap, prog: Float, si: Int) {
            val target = DW.toFloat() / DH
            var cw = b.width.toFloat(); var chh = cw / target
            if (chh > b.height) { chh = b.height.toFloat(); cw = chh * target }
            val pz = if (si % 2 == 0) prog else 1f - prog
            val zoom = 1f + 0.18f * pz
            val vw = cw / zoom; val vh = chh / zoom
            val dir = if (si % 2 == 0) 1f else -1f
            val cx = b.width / 2f + dir * (cw - vw) * (pz - 0.5f) * 0.8f
            val cy = b.height / 2f
            val src = Rect((cx - vw / 2).toInt().coerceIn(0, b.width - 1), (cy - vh / 2).toInt().coerceIn(0, b.height - 1),
                (cx + vw / 2).toInt().coerceIn(1, b.width), (cy + vh / 2).toInt().coerceIn(1, b.height))
            cv.drawBitmap(b, src, Rect(0, 0, DW.toInt(), DH.toInt()), p)
        }

        fun drawNeon(cv: Canvas, t: Float, si: Int) {
            val base = palette[si % palette.size]
            p.shader = LinearGradient(0f, 0f, 0f, DH.toFloat(), 0xFF05070D.toInt(), base, Shader.TileMode.CLAMP)
            cv.drawRect(0f, 0f, DW.toFloat(), DH.toFloat(), p); p.shader = null
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = Color.WHITE; p.alpha = 22
            var gy = (t * 40f) % 80f
            while (gy < DH) { cv.drawLine(0f, gy, DW.toFloat(), gy, p); gy += 80f }
            var gx = 0f; while (gx < DW) { cv.drawLine(gx, 0f, gx, DH.toFloat(), p); gx += 80f }
            val cx = DW / 2f; val cy = DH * 0.34f
            for (k in 0 until 4) {
                val r = 120f + k * 62f + 8f * sin(t * 1.6f + k)
                p.color = gold; p.alpha = 200 - k * 40; p.strokeWidth = 10f - k * 2f
                cv.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), (t * (50f + k * 25f) * (if (k % 2 == 0) 1 else -1)) % 360f, 95f + k * 20f, false, p)
            }
            p.style = Paint.Style.FILL; p.alpha = 255
            for (k in 0 until 14) {
                val x = ((k * 97 + si * 31) % DW).toFloat(); val y = (DH - ((t * (60f + k * 9f) + k * 150f) % DH))
                p.color = Color.WHITE; p.alpha = 70; cv.drawCircle(x, y, 3f + (k % 3), p)
            }
            p.alpha = 255
        }
    }

    // ------------------------------------------------------------------ MERGE
    private fun merge(video: File, audio: File, out: File) {
        val ev = MediaExtractor().apply { setDataSource(video.path) }
        val ea = MediaExtractor().apply { setDataSource(audio.path) }
        val mux = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            ev.selectTrack(0); ea.selectTrack(0)
            val tv = mux.addTrack(ev.getTrackFormat(0)); val ta = mux.addTrack(ea.getTrackFormat(0))
            mux.start()
            val buf = ByteBuffer.allocate(1 shl 20); val info = MediaCodec.BufferInfo()
            var vDone = false; var aDone = false
            while (!vDone || !aDone) {
                val useV = !vDone && (aDone || ev.sampleTime <= ea.sampleTime)
                val ex = if (useV) ev else ea; val tr = if (useV) tv else ta
                buf.clear()
                val n = ex.readSampleData(buf, 0)
                if (n < 0) { if (useV) vDone = true else aDone = true; continue }
                info.set(0, n, ex.sampleTime, if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                mux.writeSampleData(tr, buf, info); ex.advance()
            }
            mux.stop()
        } finally { ev.release(); ea.release(); try { mux.release() } catch (_: Exception) {} }
    }

    // ------------------------------------------------------------------ SAVE / DELETE
    /** Android 10+ par Gallery (Movies/DivyaVideos) mein bhi dikhe; purane Android par sirf app folder. */
    fun saveToGallery(c: Context, f: File): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val cv = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, f.name); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/DivyaVideos"); put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = c.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv) ?: return false
        c.contentResolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } } ?: return false
        val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
        c.contentResolver.update(uri, done, null, null)
        return true
    }

    fun delete(f: File) { f.delete(); File(f.path + ".txt").delete() }
}
