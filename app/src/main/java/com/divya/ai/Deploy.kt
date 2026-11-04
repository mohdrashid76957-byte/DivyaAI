package com.divya.ai

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Website ko seconds mein live karna (Maya jaisa). Netlify ka official "zip deploy" API:
 *   POST https://api.netlify.com/api/v1/sites                 (naya site)
 *   POST https://api.netlify.com/api/v1/sites/{id}/deploys    (wahi site update)
 * Body = ZIP (index.html root mein), Content-Type: application/zip, Authorization: Bearer <token>.
 * Token sirf Settings mein user ne daala ho tabhi chalta hai. Token backup/log mein nahi jata.
 */
object Deploy {
    private const val TAG = "DivyaDeploy"
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()

    private fun prefs(c: Context) = c.getSharedPreferences("divya", 0)
    fun hasToken(c: Context) = !prefs(c).getString("netlifyToken", "").isNullOrBlank()

    /** README / DEPLOY.md public site par nahi jaati, baaki files root se. */
    private fun zipBytes(c: Context, project: String): ByteArray {
        val base = File(CodeStore.root(c), CodeStore.safeName(project))
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            base.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(base).path.replace('\\', '/')
                val n = rel.lowercase()
                if (n == "readme.md" || n == "deploy.md") return@forEach
                z.putNextEntry(ZipEntry(rel)); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    /** Result string live model ko jata hai: "https://... live ho gayi" ya "failed: ...". */
    fun netlify(c: Context, project: String): String = try {
        val token = prefs(c).getString("netlifyToken", "")?.trim().orEmpty()
        val p = CodeStore.resolve(c, project)
        when {
            token.isBlank() -> "failed: Netlify token nahi hai. User ko bolo: Settings > Website publish mein token daale (app.netlify.com > User settings > Applications > Personal access token, free)."
            p == null -> "failed: project nahi mila. list_projects chalao."
            "index.html" !in CodeStore.files(c, p) -> "failed: '$p' mein index.html nahi hai."
            else -> {
                val siteKey = "nlSite_$p"
                val siteId = prefs(c).getString(siteKey, "") ?: ""
                val url = if (siteId.isBlank()) "https://api.netlify.com/api/v1/sites" else "https://api.netlify.com/api/v1/sites/$siteId/deploys"
                val req = Request.Builder().url(url).header("Authorization", "Bearer $token")
                    .post(zipBytes(c, p).toRequestBody("application/zip".toMediaType())).build()
                http.newCall(req).execute().use { r ->
                    val txt = r.body?.string() ?: ""
                    when {
                        r.code == 401 -> "failed: Netlify token galat/expire hai (Settings mein naya daalo)."
                        r.code == 404 && siteId.isNotBlank() -> { prefs(c).edit().remove(siteKey).apply(); "failed: purana site Netlify par nahi mila, dobara deploy_website chalao (naya site banega)." }
                        r.code == 422 || r.code == 402 -> "failed: Netlify ne mana kiya (${r.code}): ${txt.take(140)}"
                        !r.isSuccessful -> "failed: Netlify error ${r.code}: ${txt.take(140)}"
                        else -> {
                            val j = JSONObject(txt)
                            // naya site: j = site. redeploy: j = deploy (site_id hota hai)
                            val id = j.optString("site_id").ifBlank { j.optString("id") }
                            if (siteId.isBlank() && id.isNotBlank()) prefs(c).edit().putString(siteKey, id).apply()
                            val link = j.optString("ssl_url").ifBlank { j.optString("url") }.ifBlank { j.optString("deploy_ssl_url") }
                            if (link.isBlank()) "failed: Netlify ne link nahi diya."
                            else "$link (live ho gaya; 5-20 sec mein khulne lagega). Ye exact link user ko batao / WhatsApp par bhejo."
                        }
                    }
                }
            }
        }
    } catch (e: java.io.IOException) { Log.w(TAG, "net", e); "failed: internet/timeout: ${e.message}" }
    catch (e: Exception) { Log.e(TAG, "deploy", e); "failed: ${e.javaClass.simpleName}: ${e.message}" }
}
