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
                            // naya site: j = site (deploy_id hota hai). redeploy: j = deploy (id = deploy id, site_id = site)
                            val id = j.optString("site_id").ifBlank { j.optString("id") }
                            if (siteId.isBlank() && id.isNotBlank()) prefs(c).edit().putString(siteKey, id).apply()
                            val deployId = if (siteId.isBlank()) j.optString("deploy_id") else j.optString("id")
                            val link = j.optString("ssl_url").ifBlank { j.optString("url") }.ifBlank { j.optString("deploy_ssl_url") }.replace("http://", "https://")
                            if (link.isBlank()) "failed: Netlify ne link nahi diya."
                            else finish(c, token, id, deployId, link)
                        }
                    }
                }
            }
        }
    } catch (e: java.io.IOException) { Log.w(TAG, "net", e); "failed: internet/timeout: ${e.message}" }
    catch (e: Exception) { Log.e(TAG, "deploy", e); "failed: ${e.javaClass.simpleName}: ${e.message}" }

    // ---- Publish ke baad: ready hone tak ruko, link sach mein khulta hai ya nahi check karo, phir Chrome mein kholo ----
    private val plain = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).followRedirects(true).build()

    private fun getJson(url: String, token: String): JSONObject? = try {
        http.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).execute().use { r ->
            if (r.isSuccessful) JSONObject(r.body?.string() ?: "{}") else null
        }
    } catch (_: Exception) { null }

    private fun latestDeploy(token: String, siteId: String): String = try {
        http.newCall(Request.Builder().url("https://api.netlify.com/api/v1/sites/$siteId/deploys?per_page=1").header("Authorization", "Bearer $token").build()).execute().use { r ->
            if (r.isSuccessful) org.json.JSONArray(r.body?.string() ?: "[]").optJSONObject(0)?.optString("id").orEmpty() else ""
        }
    } catch (_: Exception) { "" }

    /** Netlify ka "This site is private / Sign in" wall pehchano (team-level site protection). */
    private fun isPrivateWall(finalUrl: String, body: String): Boolean =
        finalUrl.contains("app.netlify.com", true) || finalUrl.contains("edge-access", true) ||
        body.contains("This site is private", true) || body.contains("Sign in to Netlify", true) ||
        body.contains("edge-access", true)

    /** Link browser jaisa GET karke dekho: 200 + asli content aaye aur login wall na ho tabhi "khulta hai". Triple = (ok, info, private). */
    private fun verify(link: String): Triple<Boolean, String, Boolean> {
        var info = "no response"
        for (i in 0 until 8) {
            try {
                plain.newCall(Request.Builder().url(link).header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Chrome/124 Mobile Safari/537.36").build()).execute().use { r ->
                    val body = r.body?.string() ?: ""
                    info = "HTTP ${r.code}"
                    if (isPrivateWall(r.request.url.toString(), body)) return Triple(false, "PRIVATE (Netlify login wall)", true)
                    if (r.isSuccessful && body.contains("<html", true) && !body.contains("Site not found", true)) return Triple(true, info, false)
                }
            } catch (e: Exception) { info = e.javaClass.simpleName + ": " + (e.message ?: "").take(60) }
            try { Thread.sleep(1500) } catch (_: InterruptedException) { break }
        }
        return Triple(false, info, false)
    }

    /** Best-effort: site ka password/visitor protection hatao (API se jitna ho sake). Fail ho to chup-chaap false. */
    private fun tryMakePublic(token: String, siteId: String): Boolean = try {
        val body = "{\"password\":\"\"}".toRequestBody("application/json".toMediaType())
        http.newCall(Request.Builder().url("https://api.netlify.com/api/v1/sites/$siteId")
            .header("Authorization", "Bearer $token").patch(body).build()).execute().use { it.isSuccessful }
    } catch (_: Exception) { false }

    private fun openAndCopy(c: Context, link: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try { (c.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("site", link)) } catch (_: Exception) {}
            val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            try { c.startActivity(android.content.Intent(i).setPackage("com.android.chrome")) }
            catch (_: Exception) { try { c.startActivity(i) } catch (_: Exception) {} }
        }
    }

    private fun finish(c: Context, token: String, siteId: String, deployId: String, link: String): String {
        prefs(c).edit().putString("lastSiteUrl", link).apply()
        var did = deployId
        if (did.isBlank() && siteId.isNotBlank()) did = latestDeploy(token, siteId)
        var state = ""; var err = ""
        val end = System.currentTimeMillis() + 45_000
        while (did.isNotBlank() && System.currentTimeMillis() < end) {
            val j = getJson("https://api.netlify.com/api/v1/deploys/$did", token) ?: break
            state = j.optString("state"); err = j.optString("error_message")
            if (state == "ready" || state == "error") break
            try { Thread.sleep(700) } catch (_: InterruptedException) { break }
        }
        if (state == "error") return "failed: Netlify ne deploy reject kiya: ${err.ifBlank { "karan nahi bataya" }}. (Netlify dashboard > Deploys mein dekho.)"
        var (ok, info, priv) = verify(link)
        if (priv && siteId.isNotBlank()) {
            tryMakePublic(token, siteId)
            val v2 = verify(link); ok = v2.first; info = v2.second; priv = v2.third
        }
        if (priv) {
            // Private link Chrome mein kholne se wahi "This site is private" screen aati hai - isliye kholo mat, sirf copy karo.
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try { (c.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("site", link)) } catch (_: Exception) {}
            }
            return "$link deploy to hua LEKIN Netlify ne use PRIVATE rakha hai (visitors ko 'This site is private - Sign in' dikhta hai), isliye public ko nahi khulegi. " +
                "Ye jhooth mat bolo ki LIVE hai. User ko batao: Netlify dashboard > us site > Site configuration > Access & security > Visitor access/Site protection ko OFF/Public karo " +
                "(agar team-level hai to Team settings > Site protection > Off). Tab tak preview_website chalao taaki site abhi app mein dikhe. Link copy ho gaya."
        }
        openAndCopy(c, link)
        return if (ok) "$link LIVE hai aur check kar li (sach mein khulti hai, $info). Link copy ho gaya aur Chrome mein khul raha hai. Ye exact link user ko batao."
        else "$link deploy to hua par abhi khul nahi rahi ($info). Link copy ho gaya, Chrome mein khol diya. Agar 'Site not available' dikhe to Netlify account ka free limit/pause check karo."
    }
}
