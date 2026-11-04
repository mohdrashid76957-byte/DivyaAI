package com.divya.ai

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Divya Pro Chat ka dimaag: Gemini text API (REST). Live voice model se alag, taaki chat tez aur lambe code wale jawab de sake.
 * mode: 0 = Fast (flash, thinking band), 1 = Smart (flash, thinking auto), 2 = Pro (proModel).
 */
object ProEngine {
    class ApiError(msg: String) : Exception(msg)

    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS).writeTimeout(60, TimeUnit.SECONDS).build()

    @Volatile var curCall: okhttp3.Call? = null
    @Volatile var lastModel = ""          // akhri baar kaunsa model use hua (header mein dikhta hai)

    private fun prefs(c: Context) = c.getSharedPreferences("divya", 0)
    fun key(c: Context) = prefs(c).getString("key", "") ?: ""
    // Purane default (2.5) ab naye API keys par band hain (404). Isliye khali/purana ID = AUTO: Google se model list mangwa kar sabse naya flash/pro chunte hain.
    private val OLD = setOf("gemini-2.5-flash", "gemini-2.5-pro")
    private fun userModel(c: Context, k: String): String? = prefs(c).getString(k, "")?.trim()?.takeIf { it.isNotBlank() && it !in OLD }
    fun fastModel(c: Context) = userModel(c, "codeModel") ?: autoModel(c, false)
    fun proModel(c: Context) = userModel(c, "proModel") ?: autoModel(c, true)
    fun modelFor(c: Context, mode: Int) = if (mode == 2) proModel(c) else fastModel(c)

    fun autoModel(c: Context, pro: Boolean, force: Boolean = false): String {
        val p = prefs(c); val ck = if (pro) "autoPro" else "autoFast"
        val cached = p.getString(ck, "") ?: ""
        if (!force && cached.isNotBlank() && System.currentTimeMillis() - p.getLong("autoAt", 0) < 12 * 3600_000L) return cached
        try { discover(c) } catch (_: Exception) {}
        val v = p.getString(ck, "") ?: ""
        return v.ifBlank { if (pro) "gemini-pro-latest" else "gemini-flash-latest" }
    }

    private fun ping(k: String, model: String): String = try {
        val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", "hi"))))
        val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", k).post(body("Reply with: ok", contents, null, false, false, 16, 0.2).toRequestBody("application/json".toMediaType())).build()
        http.newBuilder().readTimeout(20, TimeUnit.SECONDS).build().newCall(req).execute().use { r ->
            when {
                r.isSuccessful -> "OK"
                r.code == 503 -> "503 busy"
                r.code == 429 -> "429 limit"
                r.code == 404 -> "404 nahi mila"
                else -> "error ${r.code}"
            }
        }
    } catch (e: Exception) { "error: " + (e.message ?: "").take(40) }

    /** Sab candidate models ko 'hi' bhej kar dekho kaun sa abhi chal raha hai. */
    fun diagnose(c: Context): List<Pair<String, String>> {
        val k = key(c); if (k.isBlank()) return listOf("API key" to "Settings mein daalo")
        prefs(c).edit().remove("autoAt").apply()
        try { discover(c) } catch (e: Exception) { return listOf("Model list" to "fail: ${(e.message ?: "").take(60)}") }
        val p = prefs(c)
        val all = ((p.getString("autoFastList", "") ?: "") + "," + (p.getString("autoProList", "") ?: "")).split(",").filter { it.isNotBlank() }.distinct().take(8)
        if (all.isEmpty()) return listOf("Model list" to "khali (key ya internet check karo)")
        val pool = Executors.newFixedThreadPool(4)
        val fs = all.map { m -> pool.submit(java.util.concurrent.Callable { m to ping(k, m) }) }
        val res = fs.map { try { it.get() } catch (e: Exception) { "?" to "error" } }
        pool.shutdown()
        return res
    }

    /** Model busy (503/429) ho to agla model (jo abhi try nahi hua). */
    fun fallbackModel(c: Context, tried: Set<String>, pro: Boolean): String? {
        val l = (prefs(c).getString(if (pro) "autoProList" else "autoFastList", "") ?: "").split(",").filter { it.isNotBlank() }
        val extra = if (pro) listOf("gemini-pro-latest", "gemini-flash-latest", "gemini-flash-lite-latest") else listOf("gemini-flash-latest", "gemini-flash-lite-latest")
        return (l + extra).firstOrNull { it !in tried }
    }

    private fun discover(c: Context) {
        val k = key(c); if (k.isBlank()) return
        val cl = http.newBuilder().readTimeout(8, TimeUnit.SECONDS).build()
        val names = ArrayList<String>(); var token = ""
        for (page in 0 until 5) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200" + (if (token.isNotBlank()) "&pageToken=$token" else "")
            cl.newCall(Request.Builder().url(url).header("x-goog-api-key", k).build()).execute().use { r ->
                if (!r.isSuccessful) return
                val j = JSONObject(r.body?.string().orEmpty())
                val a = j.optJSONArray("models") ?: JSONArray()
                for (i in 0 until a.length()) {
                    val m = a.getJSONObject(i); val gm = m.optJSONArray("supportedGenerationMethods") ?: continue
                    var ok = false; for (x in 0 until gm.length()) if (gm.getString(x) == "generateContent") ok = true
                    if (ok) names.add(m.optString("name").removePrefix("models/"))
                }
                token = j.optString("nextPageToken")
            }
            if (token.isBlank()) break
        }
        val bad = listOf("live", "tts", "image", "embed", "audio", "robotics", "computer", "native", "customtools", "vision", "learnlm", "8b", "thinking", "exp", "latest")
        fun ver(n: String) = Regex("^gemini-(\\d+(?:\\.\\d+)?)").find(n)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val cands = names.filter { n -> n.startsWith("gemini-") && bad.none { n.contains(it) } }
        fun ranked(f: (String) -> Boolean) = cands.filter(f)
            .sortedWith(compareBy<String> { it.contains("lite") }.thenByDescending { ver(it) }.thenBy { it.contains("preview") }.thenBy { it.length })
        val fastList = ranked { it.contains("flash") }
        val proList = ranked { it.contains("-pro") }
        if (fastList.isEmpty() && proList.isEmpty()) return
        val fast = fastList.firstOrNull(); val pro = proList.firstOrNull()
        prefs(c).edit().putString("autoFast", fast ?: pro ?: "").putString("autoPro", pro ?: fast ?: "")
            .putString("autoFastList", (fastList.take(4) + "gemini-flash-latest").distinct().joinToString(","))
            .putString("autoProList", (proList.take(2) + fastList.take(2) + "gemini-pro-latest").distinct().joinToString(","))
            .putLong("autoAt", System.currentTimeMillis()).apply()
    }

    /** 2.5 models ke liye thinking budget: Fast=0 (pro mein band nahi hota), Smart=-1 (auto), Pro=default. null = field mat bhejo. */
    fun thinkFor(model: String, mode: Int): Int? = when {
        !model.contains("2.5") -> if (mode == 0 && model.contains("flash")) -2 else null   // 3.x: thinkingLevel=low (400 aaye to hat jata hai)
        mode == 0 -> if (model.contains("pro")) null else 0
        mode == 1 -> -1
        else -> null
    }

    val SYSTEM = "You are Divya Pro, a world-class senior software engineer and all-round assistant with Claude-level quality. " +
        "Reply in the user's language: Hinglish (Roman Hindi + English) if they write Hindi/Hinglish, English if they write English. Be direct and accurate: answer first, then a short explanation. " +
        "CODING: write COMPLETE, working, production-quality code - never placeholders, TODOs or 'rest of code here'. Always use fenced code blocks with a language tag. " +
        "When you give several files, put the exact relative path in bold on its own line immediately before each code block, like **src/app.py**, so the user can export everything as a ZIP. " +
        "For debugging: find the root cause, give the fixed code, say in 1-2 lines what changed. Handle edge cases and errors, validate input, never hardcode secrets. " +
        "Ask at most ONE clarifying question and only if truly blocked; otherwise state your assumption and build. " +
        "For very big projects tell the user they can tap the 📦 Project button to get a full ZIP generated in parallel. " +
        "Add short run instructions (Termux / Android Studio / PC) after code. Never claim you ran code that you did not run. Keep non-coding answers concise and friendly."

    /** Google ke 429 jawab se asli karan nikalo: per-minute / roz ka limit / model ka free tier hi nahi (limit 0). */
    fun quotaWhy(txt: String, model: String): String {
        var msg = ""; var id = ""; var delay = ""
        try {
            val e = JSONObject(txt).optJSONObject("error")
            msg = e?.optString("message").orEmpty()
            val d = e?.optJSONArray("details")
            if (d != null) for (i in 0 until d.length()) {
                val o = d.optJSONObject(i) ?: continue
                val t = o.optString("@type")
                if (t.endsWith("QuotaFailure")) id += " " + (o.optJSONArray("violations")?.optJSONObject(0)?.optString("quotaId").orEmpty())
                if (t.endsWith("RetryInfo")) delay = o.optString("retryDelay")
            }
        } catch (_: Exception) { msg = txt }
        val whole = "$id $msg"
        val head = when {
            Regex("limit:\\s*0").containsMatchIn(whole) -> "Model '$model' ka free tier is key par hai hi nahi (limit 0). Doosra model chuno (⚙) ya billing on karo."
            whole.contains("PerDay", true) || whole.contains("per day", true) -> "Model '$model' ka AAJ ka (roz ka) free limit khatam ho gaya. Kal tak ruko ya billing on karo."
            whole.contains("PerMinute", true) || whole.contains("per minute", true) -> "Model '$model' ka per-minute limit lag gaya" + (if (delay.isNotBlank()) " - $delay ruk kar bhejo." else " - 1 minute ruk kar bhejo.")
            else -> "Google ne limit laga di (429) model '$model' par."
        }
        return head + " [Google: " + msg.replace(Regex("\\s+"), " ").take(140) + "]"
    }
    /** Roz ka limit / limit 0: dobara try karna bekaar hai. */
    fun hardQuota(txt: String): Boolean = Regex("limit:\\s*0").containsMatchIn(txt) || txt.contains("PerDay", true) || txt.contains("per day", true)

    private fun err(code: Int, txt: String, model: String, tried: Collection<String> = emptyList()): ApiError = ApiError(when {
        code == 400 && txt.contains("API key", true) -> "API key galat hai (Settings mein check karo)."
        code == 401 || code == 403 -> "API key reject hui ya permission nahi ($code)."
        code == 404 -> "Model '$model' nahi mila. Pro Chat ⚙ mein model ID khali chhod do (auto chunega)."
        code == 503 || code == 500 -> "Google ke models abhi busy hain (high demand). Try kiye: ${(tried + model).distinct().joinToString()}. ⚙ > '🔍 Models test' se dekho kaun sa chal raha hai, ya 1-2 minute baad dobara bhejo."
        code == 429 -> quotaWhy(txt, model) + " (Try kiye: ${(tried + model).distinct().joinToString()}). Pakka hal: Google AI Studio me billing on karo."
        else -> "API error $code: ${(try { JSONObject(txt).optJSONObject("error")?.optString("message").orEmpty() } catch (_: Exception) { "" }).ifBlank { txt }.take(160)}"
    })

    private fun body(system: String, contents: JSONArray, think: Int?, json: Boolean, web: Boolean, maxOut: Int, temp: Double): String {
        val gc = JSONObject().put("temperature", temp).put("maxOutputTokens", maxOut)
        if (json) gc.put("responseMimeType", "application/json")
        if (think != null) gc.put("thinkingConfig", if (think == -2) JSONObject().put("thinkingLevel", "low") else JSONObject().put("thinkingBudget", think))
        val b = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents).put("generationConfig", gc)
        if (web) b.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        return b.toString()
    }

    private fun textOf(j: JSONObject): String {
        val sb = StringBuilder()
        val parts = j.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (p.optBoolean("thought")) continue
            sb.append(p.optString("text"))
        }
        return sb.toString()
    }

    private sealed class Ev {
        class Delta(val m: String, val t: String) : Ev()
        class Done(val m: String) : Ev()
        class Fail(val m: String, val code: Int, val txt: String, val e: Exception?) : Ev()
    }

    /** Ek model ke liye ek SSE connection (alag thread mein). Events queue mein jaate hain. */
    private fun startLane(k: String, model: String, system: String, contents: JSONArray, mode: Int, web: Boolean,
                          q: java.util.concurrent.LinkedBlockingQueue<Ev>, calls: java.util.concurrent.ConcurrentHashMap<String, okhttp3.Call>,
                          alive: () -> Boolean) {
        Thread {
            var think = thinkFor(model, mode)
            var retried = false
            try {
                while (alive()) {
                    val bodyStr = body(system, contents, think, false, web, 32768, 0.6)
                    val req = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse")
                        .header("x-goog-api-key", k)
                        .post(bodyStr.toRequestBody("application/json".toMediaType())).build()
                    val call = http.newBuilder().readTimeout(90, TimeUnit.SECONDS).build().newCall(req)
                    calls[model] = call; curCall = call
                    var retry = false
                    var fail: Ev? = null
                    call.execute().use { r ->
                        if (!r.isSuccessful) {
                            val t = r.body?.string().orEmpty()
                            if (r.code == 400 && think != null && !retried) { think = null; retried = true; retry = true }
                            else fail = Ev.Fail(model, r.code, t, null)
                            return@use
                        }
                        val src = r.body!!.source()
                        var got = false
                        while (alive()) {
                            val line = src.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val js = line.substring(5).trim()
                            if (js.isEmpty() || js == "[DONE]") continue
                            val j = try { JSONObject(js) } catch (_: Exception) { continue }
                            val br = j.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                            if (br.isNotBlank()) { fail = Ev.Fail(model, -3, "Safety filter ne roka ($br). Sawaal thoda badal ke poochho.", null); break }
                            val t = textOf(j)
                            if (t.isNotEmpty()) { got = true; q.put(Ev.Delta(model, t)) }
                        }
                        if (fail == null && alive()) q.put(if (got) Ev.Done(model) else Ev.Fail(model, -4, "Model ne khali jawab diya.", null))
                    }
                    val fe = fail
                    if (fe != null) { if (alive()) q.put(fe); break }
                    if (retry) continue
                    break
                }
            } catch (e: java.io.IOException) {
                if (alive()) q.put(Ev.Fail(model, -1, "", e))
            } catch (e: Exception) {
                if (alive()) q.put(Ev.Fail(model, -1, "", e))
            }
        }.start()
    }

    private fun failToError(f: Ev.Fail, tried: Collection<String>): ApiError = when {
        f.code > 0 -> err(f.code, f.txt, f.m, tried)
        f.code == -3 || f.code == -4 -> ApiError(f.txt)
        else -> ApiError("Internet/timeout problem: ${f.e?.message ?: "jawab nahi aaya"}")
    }

    /**
     * Streaming jawab (SSE) - HEDGED: pehla model chup rahe to doosra model parallel mein shuru ho jata hai,
     * jo pehle bolna shuru kare wahi jeet-ta hai, baaki cancel. Isse busy/atka hua model jawab nahi rokta.
     */
    fun stream(c: Context, system: String, contents: JSONArray, mode: Int, web: Boolean, cancel: AtomicBoolean, onDelta: (String) -> Unit): String {
        val k = key(c); if (k.isBlank()) throw ApiError("Gemini API key nahi hai. Settings mein daalo.")
        val pro = mode == 2
        val primary = modelFor(c, mode)
        val q = java.util.concurrent.LinkedBlockingQueue<Ev>()
        val calls = java.util.concurrent.ConcurrentHashMap<String, okhttp3.Call>()
        val win = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val tried = LinkedHashSet<String>()
        val sb = StringBuilder()
        val sizeExtra = (contents.toString().length / 20_000).coerceAtMost(60)
        val hedgeMs = ((when (mode) { 0 -> 6L; 1 -> 10L; else -> 20L }) + sizeExtra.coerceAtMost(20)) * 1000
        val totalMs = ((when (mode) { 0 -> 70L; 1 -> 100L; else -> 170L }) + sizeExtra) * 1000
        val t0 = System.currentTimeMillis()
        var lastStart = t0
        var running = 0
        var rediscovered = false
        var retriedAll = false

        fun launch(m: String) {
            tried.add(m); lastModel = m; lastStart = System.currentTimeMillis(); running++
            startLane(k, m, system, contents, mode, web, q, calls) { !cancel.get() && win.get().let { it == null || it == m } }
        }

        try {
            launch(primary)
            while (true) {
                if (cancel.get()) return sb.toString()
                val ev = q.poll(250, TimeUnit.MILLISECONDS)
                val now = System.currentTimeMillis()
                if (ev == null) {
                    if (win.get() == null) {
                        if (now - t0 > totalMs) throw ApiError("Models ne ${totalMs / 1000} sec mein jawab nahi diya (try kiye: ${tried.joinToString()}). Thodi der baad dobara bhejo ya ⚙ > '🔍 Models test' dekho.")
                        if (running < 2 && now - lastStart > hedgeMs) fallbackModel(c, tried, pro)?.let { launch(it) }
                    }
                    continue
                }
                when (ev) {
                    is Ev.Delta -> {
                        if (win.get() == null) {
                            win.set(ev.m); lastModel = ev.m
                            for ((m, cl) in calls) if (m != ev.m) { try { cl.cancel() } catch (_: Exception) {} }
                        }
                        if (win.get() == ev.m) { sb.append(ev.t); onDelta(ev.t) }
                    }
                    is Ev.Done -> if (win.get() == ev.m) return sb.toString()
                    is Ev.Fail -> {
                        if (win.get() == ev.m) {                 // jeetne wala beech mein toota
                            if (sb.isNotEmpty()) return sb.toString()
                            win.set(null)
                        }
                        running--
                        if (ev.code == 404 && !rediscovered) {
                            rediscovered = true
                            val nm = autoModel(c, pro, true)
                            if (nm !in tried) { launch(nm); continue }
                        }
                        if (ev.code == -3) throw failToError(ev, tried)
                        if (running >= 1) continue          // doosra model abhi chal raha hai, wahi dekho
                        val nxt = fallbackModel(c, tried, pro)
                        if (nxt != null) {
                            if (ev.code == 429 || ev.code >= 500) { try { Thread.sleep(700) } catch (_: InterruptedException) {} }
                            launch(nxt)
                        } else if (running <= 0) {
                            if (!retriedAll && ((ev.code == 429 && !hardQuota(ev.txt)) || ev.code >= 500 || ev.code == -1)) {   // sab busy: poori list ek baar dobara
                                retriedAll = true
                                try { Thread.sleep(2000) } catch (_: InterruptedException) {}
                                tried.clear(); launch(primary)
                            } else throw failToError(ev, tried)
                        }
                    }
                }
            }
        } finally {
            for ((_, cl) in calls) { try { cl.cancel() } catch (_: Exception) {} }
        }
    }

    /** Ek baar poora jawab (plan / ek file). 429 / 5xx par backoff ke saath retry. */
    fun complete(c: Context, system: String, user: String, modelIn: String, json: Boolean, cancel: AtomicBoolean, thinkBudget: Int?, maxOut: Int = 32768): String {
        val k = key(c); if (k.isBlank()) throw ApiError("Gemini API key nahi hai. Settings mein daalo.")
        var model = modelIn
        var rediscovered = false
        val triedM = HashSet<String>().apply { add(model) }
        var think = thinkBudget
        val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user))))
        var tries = 0
        while (true) {
            if (cancel.get()) throw ApiError("Cancel kiya")
            lastModel = model
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                .header("x-goog-api-key", k)
                .post(body(system, contents, think, json, false, maxOut, 0.4).toRequestBody("application/json".toMediaType())).build()
            var wait = -1L; var out: String? = null
            try {
                val call = http.newBuilder().readTimeout(120, TimeUnit.SECONDS).build().newCall(req); curCall = call
                call.execute().use { r ->
                    val txt = r.body?.string().orEmpty()
                    if (r.isSuccessful) {
                        val j = JSONObject(txt)
                        val br = j.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                        if (br.isNotBlank()) throw ApiError("Safety filter ne roka ($br).")
                        out = textOf(j); return@use
                    }
                    if (r.code == 404 && !rediscovered) {
                        rediscovered = true
                        val nm = autoModel(c, model.contains("pro"), true)
                        if (nm != model) { model = nm; if (think != null) think = if (nm.contains("2.5")) think else -2; wait = 0; return@use }
                    }
                    if (r.code == 400 && think != null) { think = null; wait = 0; return@use }
                    if (r.code == 429 || r.code >= 500) {
                        if (tries >= 1) {
                            val fb = fallbackModel(c, triedM, model.contains("pro"))
                            if (fb != null) { triedM.add(fb); model = fb; if (think != null) think = if (fb.contains("2.5")) think else -2; tries = 0; wait = 0; return@use }
                        }
                        if (tries < 4) { wait = if (r.code == 429) 5000L * (tries + 1) else 1500L; return@use }
                    }
                    throw err(r.code, txt, model, triedM)
                }
            } catch (e: java.io.IOException) {
                if (cancel.get()) throw ApiError("Cancel kiya")
                val fb = if (tries >= 1 || e is java.net.SocketTimeoutException) fallbackModel(c, triedM, model.contains("pro")) else null
                if (fb != null) { triedM.add(fb); model = fb; if (think != null) think = if (fb.contains("2.5")) think else -2; tries = 0; wait = 0 }
                else if (tries >= 2) throw ApiError("Internet/timeout problem: ${e.message}")
                else wait = 1500
            }
            out?.let { if (it.isBlank()) throw ApiError("Model ne khali jawab diya.") else return it }
            tries++; try { Thread.sleep(wait) } catch (_: InterruptedException) {}
        }
    }
}

/** Poora project parallel mein: pehle plan (file list + shared spec), phir sab files ek saath. Badi cheez bhi kuch seconds mein. */
object ProjectGen {
    class Result(val name: String, val summary: String, val run: String, val files: List<String>, val failed: List<String>, val issues: List<String>)

    private val PLAN_SYS = "You are a principal software architect. Design a COMPLETE, working, runnable project for the user's request. Output ONLY JSON: " +
        "{\"project\":\"short-kebab-name\",\"summary\":\"one sentence in Hinglish\",\"stack\":\"languages/frameworks\",\"run\":\"step-by-step run instructions in Hinglish with exact commands\"," +
        "\"spec\":\"the SHARED CONTRACT every file must follow: data models, exact function/class signatures and which file defines them, API routes, DOM ids and CSS classes, config keys, dependencies with versions (max ~1200 words)\"," +
        "\"files\":[{\"path\":\"relative/path.ext\",\"purpose\":\"what this file contains\"}]}. " +
        "Rules: 3 to 28 files; always include README.md and every manifest the stack needs (requirements.txt, package.json, build.gradle.kts, etc.); relative paths only; text files only (no images/binaries - use SVG or CSS); " +
        "no real secrets (use .env.example); choose the simplest stack that works with minimal setup unless the user named a stack; Android requests: full Gradle project with Kotlin sources, manifest and resources."

    private val FILE_SYS = "You write ONE file of a larger project. Output ONLY the raw, complete content of that file - no markdown fences, no commentary before or after. " +
        "The file must be FULLY implemented and production quality: no TODOs, no placeholders, no 'rest of code'. Follow the SPEC and the file list EXACTLY (imports, names, routes, ids) so all files work together. Never hardcode secrets."

    private fun clean(t: String): String {
        var s = t.trim('\n', '\r')
        if (s.trimStart().startsWith("```")) {
            s = s.trimStart().substringAfter('\n', "")
            if (s.trimEnd().endsWith("```")) s = s.trimEnd().removeSuffix("```")
        }
        return s.trimEnd() + "\n"
    }

    fun build(c: Context, request: String, context: String, mode: Int, cancel: AtomicBoolean, progress: (String) -> Unit): Result {
        val model = ProEngine.modelFor(c, mode)
        val think = ProEngine.thinkFor(model, mode)
        progress("📐 Plan ban raha hai…")
        var raw = ProEngine.complete(c, PLAN_SYS, "Earlier chat context (may be empty):\n$context\n\nUSER REQUEST:\n$request", model, true, cancel, think, 16384).trim()
        if (raw.startsWith("```")) raw = raw.substringAfter('\n').substringBeforeLast("```")
        val plan = try { JSONObject(raw) } catch (e: Exception) { throw ProEngine.ApiError("Plan ka JSON samajh nahi aaya, dobara try karo.") }
        val arr = plan.optJSONArray("files") ?: throw ProEngine.ApiError("Plan mein koi file nahi thi.")
        val files = ArrayList<Pair<String, String>>()
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: continue
            val p = f.optString("path").trim().trimStart('/')
            if (p.isNotBlank() && files.none { it.first == p } && files.size < 30) files.add(p to f.optString("purpose"))
        }
        if (files.isEmpty()) throw ProEngine.ApiError("Plan mein koi file nahi thi.")
        var name = CodeStore.safeName(plan.optString("project").ifBlank { "project" })
        if (CodeStore.listProjects(c).contains(name)) name += "-" + (System.currentTimeMillis() % 1000)
        val spec = plan.optString("spec"); val stack = plan.optString("stack"); val summary = plan.optString("summary")
        val list = files.joinToString("\n") { "- ${it.first}: ${it.second}" }
        val total = files.size
        progress("✍️ 0/$total files likh raha hai…")

        val conc = c.getSharedPreferences("divya", 0).getInt("proConc", 4).coerceIn(1, 8)
        val pool = Executors.newFixedThreadPool(conc)
        val done = AtomicInteger(0)
        val ok = Collections.synchronizedList(ArrayList<String>())
        val failed = Collections.synchronizedList(ArrayList<String>())
        val futures = files.map { (path, purpose) ->
            pool.submit(Runnable {
                try {
                    if (cancel.get()) return@Runnable
                    val user = "PROJECT: $name\nSUMMARY: $summary\nSTACK: $stack\n\nSPEC (shared contract):\n$spec\n\nALL FILES:\n$list\n\nORIGINAL USER REQUEST:\n$request\n\nNOW WRITE THIS FILE: $path\nPURPOSE: $purpose"
                    val txt = clean(ProEngine.complete(c, FILE_SYS, user, model, false, cancel, think, 32768))
                    val e = CodeStore.write(c, name, path, txt)
                    if (e == null) ok.add(path) else failed.add("$path ($e)")
                } catch (e: Exception) { failed.add("$path (${e.message})") }
                progress("✍️ ${done.incrementAndGet()}/$total files…")
            })
        }
        for (f in futures) { try { f.get() } catch (_: Exception) {} }
        pool.shutdown()
        if (cancel.get()) throw ProEngine.ApiError("Cancel kiya")
        if (ok.isEmpty()) throw ProEngine.ApiError("Koi file nahi ban payi: ${failed.joinToString().take(300)}")
        val okList = files.map { it.first }.filter { it in ok }
        val issues = if (okList.any { it.endsWith(".html") }) CodeStore.lint(c, name) else emptyList()
        return Result(name, summary, plan.optString("run"), okList, failed.toList(), issues)
    }
}

/** Chat ke jawab ke code-blocks ko project/ZIP mein badalna + ZIP share / Downloads mein save. */
object ZipOut {
    private val EXT = mapOf("python" to "py", "py" to "py", "javascript" to "js", "js" to "js", "typescript" to "ts", "ts" to "ts", "tsx" to "tsx", "jsx" to "jsx",
        "html" to "html", "css" to "css", "json" to "json", "kotlin" to "kt", "kt" to "kt", "java" to "java", "bash" to "sh", "sh" to "sh", "shell" to "sh",
        "c" to "c", "cpp" to "cpp", "c++" to "cpp", "go" to "go", "rust" to "rs", "sql" to "sql", "yaml" to "yml", "yml" to "yml", "markdown" to "md", "md" to "md",
        "xml" to "xml", "php" to "php", "ruby" to "rb", "dart" to "dart", "swift" to "swift", "csharp" to "cs", "toml" to "toml", "text" to "txt", "txt" to "txt")
    private val PATH = Regex("""([A-Za-z0-9_./-]+\.[A-Za-z0-9]{1,6})""")

    fun hasCode(text: String) = text.contains("```")

    /** Jawab ke code blocks: (path, content, pathSaafThaYaNahi). */
    private fun parse(text: String): List<Triple<String, String, Boolean>> {
        val lines = text.split("\n")
        val found = ArrayList<Triple<String, String, Boolean>>()
        var i = 0; var n = 0
        while (i < lines.size) {
            val tl = lines[i].trimStart()
            if (tl.startsWith("```")) {
                val lang = tl.removePrefix("```").trim().lowercase()
                var prev = ""; var k = i - 1
                while (k >= 0 && prev.isBlank()) { prev = lines[k].trim(); k-- }
                val body = StringBuilder(); i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) { body.append(lines[i]).append('\n'); i++ }
                var path = PATH.find(prev.replace("*", "").replace("`", ""))?.groupValues?.get(1)?.takeIf { prev.length < 120 }
                if (path == null) {
                    val first = body.lineSequence().firstOrNull().orEmpty()
                    if (first.startsWith("//") || first.startsWith("#") || first.startsWith("<!--")) path = PATH.find(first)?.groupValues?.get(1)
                }
                val explicit = path != null
                if (path == null) { n++; path = "snippet$n." + (EXT[lang] ?: "txt") }
                var p = path.trimStart('/').removePrefix("./")
                if (found.any { it.first == p }) { if (explicit) { found.removeAll { it.first == p } } else p = "dup${found.size}_$p" }
                found.add(Triple(p, body.toString(), explicit))
            }
            i++
        }
        return found
    }

    /** Original (upload kiya hua) ZIP + jawab ke badle hue files = naya updated ZIP. Returns (file, summary). */
    fun exportMerged(c: Context, text: String, base: java.io.File, origName: String = ""): Pair<java.io.File, String>? {
        val blocks = parse(text).filter { it.third }
        if (blocks.isEmpty() || !base.exists()) return null
        val names = ArrayList<String>()
        java.util.zip.ZipInputStream(base.inputStream()).use { z -> var e = z.nextEntry; while (e != null) { if (!e.isDirectory) names.add(e.name); e = z.nextEntry } }
        val root = names.map { it.substringBefore('/', "") }.distinct().let { if (it.size == 1 && it[0].isNotEmpty() && names.all { n -> n.contains('/') }) it[0] + "/" else "" }
        val repl = HashMap<String, String>(); val added = HashMap<String, String>()
        for ((p, body, _) in blocks) {
            val hit = names.filter { it == p || it.endsWith("/$p") }.minByOrNull { it.length }
            if (hit != null) repl[hit] = body else added[root + p] = body
        }
        // Naam wahi jo user ne upload kiya tha (jaise DivyaAI_v2_18.zip); nahi mila to base file ka naam
        val outName = origName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { base.name }.let { if (it.lowercase().endsWith(".zip")) it else "$it.zip" }
        val out = java.io.File(java.io.File(c.cacheDir, "share").apply { mkdirs() }, outName)
        java.util.zip.ZipOutputStream(out.outputStream()).use { zo ->
            java.util.zip.ZipInputStream(base.inputStream()).use { zi ->
                var e = zi.nextEntry
                while (e != null) {
                    zo.putNextEntry(java.util.zip.ZipEntry(e.name))
                    if (!e.isDirectory) { val r = repl[e.name]; if (r != null) zo.write(r.toByteArray()) else zi.copyTo(zo) }
                    zo.closeEntry(); e = zi.nextEntry
                }
            }
            for ((n, b) in added) { zo.putNextEntry(java.util.zip.ZipEntry(n)); zo.write(b.toByteArray()); zo.closeEntry() }
        }
        return out to "${repl.size} file badli, ${added.size} nayi"
    }

    fun shareFile(c: Context, f: java.io.File) {
        val uri = FileProvider.getUriForFile(c, c.packageName + ".fileprovider", f)
        c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "ZIP bhejo / save karo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun saveFileToDownloads(c: Context, z: java.io.File): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, z.name); put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return null
            c.contentResolver.openOutputStream(uri)?.use { o -> z.inputStream().use { it.copyTo(o) } }
            z.name
        } catch (e: Exception) { null }
    }

    /** Reply ke saare code blocks ko naye project mein likho. Project ka naam (ya null agar kuch nahi mila). */
    fun export(c: Context, text: String): String? {
        val found = parse(text)
        if (found.isEmpty()) return null
        val name = "chat-code-" + (System.currentTimeMillis() % 100000)
        var okc = 0
        for ((p, b, _) in found) if (CodeStore.write(c, name, p, b) == null) okc++
        return if (okc > 0) name else null
    }

    fun share(c: Context, project: String) {
        val z = CodeStore.zip(c, project)
        val uri = FileProvider.getUriForFile(c, c.packageName + ".fileprovider", z)
        c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "ZIP bhejo / save karo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Downloads folder mein ZIP (Android 10+). Naam wapas, warna null (purane Android par Share use karo). */
    fun saveToDownloads(c: Context, project: String): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            val z = CodeStore.zip(c, project)
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, z.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return null
            c.contentResolver.openOutputStream(uri)?.use { o -> z.inputStream().use { it.copyTo(o) } }
            z.name
        } catch (e: Exception) { null }
    }
}
