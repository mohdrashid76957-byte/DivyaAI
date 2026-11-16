package com.divya.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * v3.20: safe update check. Sirf naya version dhoondhta hai aur APK ka download link browser mein kholta hai.
 * Install hamesha aapke haath se hota hai. Koi silent install nahi.
 * Update URL: GitHub release ka API link, jaise https://api.github.com/repos/USER/REPO/releases/latest
 */
object AppUpdate {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    fun currentVersion(c: Context): String =
        try { c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "0" } catch (_: Exception) { "0" }

    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.trim().removePrefix("v").split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val l = local.trim().removePrefix("v").split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    fun checkAsync(c: Context) {
        val url = c.getSharedPreferences("divya", 0).getString("updateUrl", "")?.trim() ?: ""
        val main = Handler(Looper.getMainLooper())
        fun say(msg: String) { main.post { Toast.makeText(c, msg, Toast.LENGTH_LONG).show() } }
        if (!url.startsWith("https://")) { say("Pehle Settings mein Update URL daalo (https://...)"); return }
        Thread {
            try {
                val req = Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) { say("Update check fail: ${resp.code}"); return@Thread }
                    val j = JSONObject(resp.body?.string() ?: "{}")
                    val tag = j.optString("tag_name")
                    var apk = ""
                    val assets = j.optJSONArray("assets")
                    if (assets != null) for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break }
                    }
                    val local = currentVersion(c)
                    if (apk.isBlank() || !isNewer(tag, local)) { say("Divya up-to-date hai (v$local)"); return@Thread }
                    main.post {
                        try { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apk)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                        Toast.makeText(c, "Naya version $tag mila. Download ke baad aap install karna.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                say("Update check nahi ho paya. Internet aur URL check karo.")
            }
        }.start()
    }
}
