package com.divya.ai

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Website / code banane ka kaam. Live voice session ko halka rakhne ke liye alag text model
 * (Settings ki "codeModel", default gemini-2.5-flash) REST se bulaya jata hai - wahi Gemini API key.
 * Divya ke sabhi jawab "result" string hote hain jo Live model ko wapas jaate hain.
 */
object CodeGen {
    private const val TAG = "DivyaCodeGen"
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build()

    class GenError(msg: String) : Exception(msg)

    private fun model(c: Context) = ProEngine.fastModel(c)

    /** Ek baar bhejo (5xx / timeout par ek retry). JSON text wapas. Errors samajhne layak Hindi-English mein. */
    private fun ask(c: Context, system: String, user: String): String {
        val key = c.getSharedPreferences("divya", 0).getString("key", "") ?: ""
        if (key.isBlank()) throw GenError("Gemini API key nahi hai. Settings mein daalo.")
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0.7)).toString()
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/${model(c)}:generateContent")
            .header("x-goog-api-key", key)                       // key URL/log mein nahi jaati
            .post(body.toRequestBody("application/json".toMediaType())).build()
        var last: Exception? = null
        for (attempt in 1..2) {
            try {
                http.newCall(req).execute().use { r ->
                    val txt = r.body?.string() ?: ""
                    when {
                        r.code == 400 && txt.contains("API key", true) -> throw GenError("API key galat hai (Settings mein check karo).")
                        r.code == 401 || r.code == 403 -> throw GenError("API key reject hui ya permission nahi (${r.code}).")
                        r.code == 404 -> throw GenError("Model '${model(c)}' nahi mila. Settings mein codeModel badlo.")
                        r.code == 429 -> throw GenError("API limit/quota khatam. Thodi der baad try karo.")
                        r.code >= 500 -> { last = GenError("Gemini server error ${r.code}"); Log.w(TAG, "server ${r.code}, retry") }
                        !r.isSuccessful -> throw GenError("API error ${r.code}: ${txt.take(150)}")
                        else -> {
                            val parts = JSONObject(txt).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                                ?: throw GenError("Model ne khali jawab diya (safety filter ho sakta hai).")
                            return (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }
                        }
                    }
                }
            } catch (e: GenError) { throw e
            } catch (e: java.io.IOException) { last = e; Log.w(TAG, "network error, attempt $attempt", e) }
        }
        throw GenError("Internet/timeout problem: ${last?.message}")
    }

    private fun parse(raw: String): JSONObject {
        var t = raw.trim()
        if (t.startsWith("```")) t = t.substringAfter('\n').substringBeforeLast("```")
        return try { JSONObject(t) } catch (e: Exception) { throw GenError("Model ka jawab JSON nahi tha, dobara try karo.") }
    }

    /** JSON {"files":[{"path","content"}]} ko project mein likho. Likhi hui files ki list wapas. */
    private fun writeFiles(c: Context, project: String, j: JSONObject): List<String> {
        val arr = j.optJSONArray("files") ?: throw GenError("Model ne koi file nahi di.")
        val done = mutableListOf<String>(); val bad = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val f = arr.getJSONObject(i); val p = f.optString("path"); val body = f.optString("content")
            val err = CodeStore.write(c, project, p, body)
            if (err == null) done += p else { bad += err; Log.w(TAG, "skip: $err") }
        }
        if (done.isEmpty()) throw GenError("Koi file save nahi hui: ${bad.joinToString()}")
        return done
    }

    private val RULES = "Rules: plain HTML/CSS/JS only (no build tools, no backend, no frameworks needing install). Mobile-first responsive, modern clean design, semantic HTML, accessible. " +
        "Every file path must be relative (index.html, style.css, script.js, assets/...). Link css/js correctly from index.html. NEVER include real API keys, passwords or private data; contact forms must be front-end only (mailto: or clearly marked placeholder). " +
        "Use placeholder text the user can edit; images only via inline SVG or CSS gradients (no hotlinked images). Output ONLY JSON."

    fun buildWebsite(c: Context, description: String, kind: String): String = try {
        val sys = "You are a senior web developer. Create a COMPLETE website project from the requirements. $RULES " +
            "JSON shape: {\"project\":\"short-kebab-name\",\"summary\":\"one short sentence in Hinglish\",\"files\":[{\"path\":\"index.html\",\"content\":\"...\"},{\"path\":\"style.css\",...},{\"path\":\"script.js\",...},{\"path\":\"README.md\",...},{\"path\":\"DEPLOY.md\",...}]}. " +
            "DEPLOY.md must give simple step-by-step Hinglish instructions to publish on GitHub Pages (create repo, upload files, Settings > Pages > main branch / root) and Netlify drop as alternative."
        val j = parse(ask(c, sys, "Website type: $kind\nRequirements: $description"))
        var name = CodeStore.safeName(j.optString("project").ifBlank { kind })
        if (CodeStore.listProjects(c).contains(name)) name += "-" + (System.currentTimeMillis() % 1000)
        val files = writeFiles(c, name, j).toMutableList()
        // Error dhundo aur ek baar khud theek karo
        var issues = CodeStore.lint(c, name)
        if (issues.isNotEmpty()) {
            Log.w(TAG, "lint: $issues")
            try { files += fix(c, name, issues.joinToString("; ")); issues = CodeStore.lint(c, name) } catch (e: GenError) { Log.w(TAG, "auto-fix fail: ${e.message}") }
        }
        "done: project '$name' bana (${files.distinct().size} files: ${files.distinct().joinToString()}). " + j.optString("summary") +
            (if (issues.isEmpty()) " Koi error nahi mila." else " Bache issues: ${issues.joinToString("; ")}") +
            " Ab preview_website('$name') chalao aur user ko bolo ki Tools > Code mein edit/share/deploy guide mil jayegi."
    } catch (e: GenError) { Log.e(TAG, "buildWebsite", e); "failed: ${e.message}" }
    catch (e: Exception) { Log.e(TAG, "buildWebsite", e); "failed: ${e.javaClass.simpleName}: ${e.message}" }

    private fun projectDump(c: Context, project: String, limit: Int = 120_000): String {
        val sb = StringBuilder()
        for (f in CodeStore.files(c, project)) {
            val t = CodeStore.read(c, project, f) ?: continue
            if (sb.length + t.length > limit) { sb.append("\n--- $f (skipped: too large) ---\n"); continue }
            sb.append("\n--- FILE: $f ---\n").append(t)
        }
        return sb.toString()
    }

    private fun fix(c: Context, project: String, problem: String): List<String> {
        val sys = "You fix and modify web projects. Return ONLY JSON {\"files\":[{\"path\":...,\"content\":...}]} containing the COMPLETE new content of every file you changed or must create (unchanged files omitted). $RULES"
        val j = parse(ask(c, sys, "Problem/instruction: $problem\n\nCurrent project:\n" + projectDump(c, project)))
        return writeFiles(c, project, j)
    }

    fun editWebsite(c: Context, project: String, instruction: String): String = try {
        val p = CodeStore.resolve(c, project)
        if (p == null) "failed: '$project' naam ka project nahi mila. list_projects chalao."
        else {
            val changed = fix(c, p, instruction)
            val issues = CodeStore.lint(c, p)
            "done: '$p' mein badlav hua (${changed.joinToString()})." + (if (issues.isEmpty()) "" else " Bache issues: ${issues.joinToString("; ")}") + " preview_website se dikha sakte ho."
        }
    } catch (e: GenError) { Log.e(TAG, "edit", e); "failed: ${e.message}" }
    catch (e: Exception) { Log.e(TAG, "edit", e); "failed: ${e.javaClass.simpleName}: ${e.message}" }

    /** Python / shell / html-css-js ki single file ya chhoti script. 'scripts' project mein jati hai. Run NAHI hoti. */
    fun generateCode(c: Context, task: String, filename: String): String = try {
        val sys = "You are a senior developer. Write the requested code as ONE file (or a few small files) with clear comments. For Termux/Python scripts: use only the standard library unless needed, and put pip/pkg install commands in a comment at the top. " +
            "Never include real secrets. Output ONLY JSON {\"files\":[{\"path\":\"name.ext\",\"content\":\"...\"}],\"how_to_run\":\"short Hinglish instructions\"}. Allowed extensions: py, sh, html, css, js, json, md, txt."
        val j = parse(ask(c, sys, "Task: $task\nPreferred filename: ${filename.ifBlank { "auto" }}"))
        val saved = writeFiles(c, "scripts", j)
        "done: ${saved.joinToString()} 'scripts' project mein save hui (Tools > Code mein dekho/copy karo). Run karne ke liye: ${j.optString("how_to_run")}. " +
            "Phone par code khud run nahi hota - user Termux mein copy karke chalaye."
    } catch (e: GenError) { Log.e(TAG, "gen", e); "failed: ${e.message}" }
    catch (e: Exception) { Log.e(TAG, "gen", e); "failed: ${e.javaClass.simpleName}: ${e.message}" }
}
