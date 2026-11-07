package com.divya.ai

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Chat mein "ek ... ki image bana do" bolo -> image ban kar chat mein dikhti hai (ChatGPT/Gemini jaisa).
 * Pehle Gemini image (aapki API key), wo quota/error de to free Pollinations (bina key) -> app kabhi limit nahi lagati.
 */
object ImageGen {
    private const val TAG = "DivyaImageGen"
    class Result(val file: File, val via: String)

    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(100, TimeUnit.SECONDS).build()

    private val NOUNS = listOf("image", "imege", "imej", "img", "photo", "foto", "pic", "picture", "tasveer", "tasvir", "wallpaper", "logo", "poster", "thumbnail",
        "इमेज", "फोटो", "तस्वीर", "फ़ोटो", "पिक्चर", "लोगो", "पोस्टर")
    private val VERBS = listOf("bana", "banao", "banaa", "bnao", "bna", "generate", "create", "make", "draw", "design", "render", "बना", "बनाओ", "बनाइए")

    fun isRequest(t: String): Boolean {
        val l = t.lowercase().trim()
        if (l.startsWith("/image") || l.startsWith("/img")) return true
        if (l.length > 600) return false     // lamba code/text paste hai, image request nahi
        return NOUNS.any { l.contains(it) } && VERBS.any { l.contains(it) }
    }

    fun cleanPrompt(t: String): String = t.trim().removePrefix("/image").removePrefix("/img").trim()

    private fun dims(t: String): Triple<Int, Int, String> {
        val l = t.lowercase()
        return when {
            listOf("9:16", "reel", "story", "vertical", "wallpaper", "mobile", "phone").any { l.contains(it) } -> Triple(720, 1280, "9:16")
            listOf("16:9", "banner", "youtube", "thumbnail", "wide", "landscape", "desktop").any { l.contains(it) } -> Triple(1280, 720, "16:9")
            else -> Triple(1024, 1024, "1:1")
        }
    }

    private fun dir(c: Context) = File(c.filesDir, "chatimg").also { it.mkdirs() }

    fun generate(c: Context, request: String, onStage: (String) -> Unit): Result {
        val prompt = cleanPrompt(request)
        require(prompt.isNotBlank()) { "Kaisi image chahiye? Thoda bata do." }
        val (w, h, ar) = dims(prompt)
        val errs = ArrayList<String>()

        // 1) Gemini image (key ho to)
        val key = c.getSharedPreferences("divya", 0).getString("key", "")?.trim().orEmpty()
        if (key.isNotBlank()) {
            onStage("Gemini se image ban rahi hai…")
            try { gemini(c, key, prompt, ar)?.let { return Result(save(c, it), "Gemini") } }
            catch (e: Exception) { errs.add("gemini: ${e.message}"); Log.w(TAG, "gemini", e) }
        }
        // 2) Free Pollinations (bina key, bina limit-counter)
        onStage("Free AI se image ban rahi hai…")
        val en = try { toEnglishPrompt(c, prompt) } catch (e: Exception) { prompt }
        for (model in listOf("flux", "turbo")) {
            try { pollinations(en, w, h, model)?.let { return Result(save(c, it), "Free AI ($model)") } }
            catch (e: Exception) { errs.add("$model: ${e.message}"); Log.w(TAG, "poll $model", e) }
        }
        throw IllegalStateException("Image nahi ban payi. Internet check karo, thodi der baad dobara try karo. (${errs.joinToString("; ").take(160)})")
    }

    private fun gemini(c: Context, key: String, prompt: String, ar: String): ByteArray? {
        val model = c.getSharedPreferences("divya", 0).getString("imgModel", "gemini-2.5-flash-image") ?: "gemini-2.5-flash-image"
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("IMAGE")).put("imageConfig", JSONObject().put("aspectRatio", ar)))
            .toString()
        val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").header("x-goog-api-key", key)
            .post(body.toRequestBody("application/json".toMediaType())).build()
        val txt = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException(if (r.code == 429) "quota khatam (429)" else "error ${r.code}")
            r.body?.string()
        } ?: return null
        val parts = JSONObject(txt).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return null
        for (i in 0 until parts.length()) {
            val d = parts.getJSONObject(i).optJSONObject("inlineData")?.optString("data")
            if (!d.isNullOrBlank()) return android.util.Base64.decode(d, android.util.Base64.DEFAULT)
        }
        return null
    }

    /** Hinglish/Hindi request ko detailed English image prompt banao (free model English mein behtar chalta hai). */
    private fun toEnglishPrompt(c: Context, p: String): String {
        val raw = CodeGen.ask(c, "You turn a user's image request (Hindi/Hinglish/English) into ONE detailed English text-to-image prompt. Reply ONLY JSON {\"prompt\":\"...\"}.",
            "Request: $p\nInclude subject, style, lighting, camera/composition, colours. No text or watermark in the image.")
        var t = raw.trim()
        if (t.startsWith("```")) t = t.substringAfter('\n').substringBeforeLast("```")
        return JSONObject(t).optString("prompt").ifBlank { p }.take(900)
    }

    private fun pollinations(prompt: String, w: Int, h: Int, model: String): ByteArray? {
        val seed = (System.currentTimeMillis() % 1_000_000).toInt()
        val url = "https://image.pollinations.ai/prompt/" + java.net.URLEncoder.encode(prompt, "UTF-8").replace("+", "%20") +
            "?width=$w&height=$h&model=$model&nologo=true&seed=$seed"
        val bytes = http.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 DivyaAI").build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("error ${r.code}") else r.body?.bytes()
        }
        return bytes?.takeIf { it.size > 5000 }
    }

    private fun save(c: Context, bytes: ByteArray): File {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0) throw IllegalStateException("Image kharab aayi")
        val ext = if (bytes.size > 4 && bytes[0] == 0x89.toByte()) "png" else "jpg"
        val f = File(dir(c), "divya_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date()) + "." + ext)
        f.writeBytes(bytes)
        return f
    }

    fun saveToGallery(c: Context, f: File): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val mime = if (f.extension == "png") "image/png" else "image/jpeg"
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, f.name); put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DivyaImages"); put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = c.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return false
        c.contentResolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } } ?: return false
        c.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return true
    }

    fun share(c: Context, f: File) {
        val tmp = File(File(c.cacheDir, "share").also { it.mkdirs() }, f.name); f.copyTo(tmp, true)
        val uri = FileProvider.getUriForFile(c, c.packageName + ".fileprovider", tmp)
        val mime = if (f.extension == "png") "image/png" else "image/jpeg"
        c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Image bhejo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
