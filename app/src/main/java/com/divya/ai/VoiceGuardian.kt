package com.divya.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Family voice-templates ko phone ke Android Keystore (AES-GCM) se encrypt karke rakhta hai. Raw recording kabhi save/upload nahi hoti. */
object Vault {
    private const val ALIAS = "divya_voice_vault"
    private fun key(): javax.crypto.SecretKey {
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? javax.crypto.SecretKey)?.let { return it }
        val g = javax.crypto.KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(android.security.keystore.KeyGenParameterSpec.Builder(ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return g.generateKey()
    }
    fun enc(plain: String): String = try {
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding"); c.init(javax.crypto.Cipher.ENCRYPT_MODE, key())
        "e:" + Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    } catch (e: Exception) { "p:$plain" }
    fun dec(s: String): String? = try {
        if (s.startsWith("p:")) s.substring(2)
        else {
            val p = s.split(":")
            val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            c.init(javax.crypto.Cipher.DECRYPT_MODE, key(), javax.crypto.spec.GCMParameterSpec(128, Base64.decode(p[1], Base64.NO_WRAP)))
            String(c.doFinal(Base64.decode(p[2], Base64.NO_WRAP)))
        }
    } catch (e: Exception) { null }
}

/**
 * Voice Guardian: owner (purana "vp") + family voices, Listen mode, Away/Guard mode, log.
 * Master switch = purana "voiceLock". Awaaz pehchan perfect authentication NAHI hai (basic MFCC), isliye sirf
 * "kaun bol raha hai" ka andaza deti hai; phone ka unlock (needUnlock) alag se laga rehta hai.
 */
object VoiceGuardian {
    class Speaker(val role: String, val name: String, val score: Double, val limit: Double, val conf: Int, val ts: Long = System.currentTimeMillis())
    class Person(val id: String, val name: String, val model: VpModel)

    /** Dashboard par "last suna gaya" dikhane ke liye. */
    var last by mutableStateOf<Speaker?>(null)

    /** Aakhri baar owner ki awaaz (ya typed chat) kab mili. Auto-bye rokne ke liye use hota hai. */
    @Volatile var lastOwnerAt = 0L

    private fun guardOn(p: SharedPreferences): Boolean =
        p.getBoolean("voiceLock", false) && p.getInt("vgMode", 1) >= 1 && owner(p) != null

    /**
     * Session band karne (end_session / bye signal) ki ijazat hai ya nahi.
     * Guardian ON ho aur 8 sec mein owner ki awaaz na mili ho to signal shor / anjaan logon se aaya maana jata hai,
     * isliye Divya khud bye bolkar band nahi hogi.
     */
    fun allowEnd(c: Context): Boolean {
        val p = c.getSharedPreferences("divya", 0)
        if (!guardOn(p)) return true
        return System.currentTimeMillis() - lastOwnerAt < 8_000L
    }

    fun noteIgnoredEnd(c: Context) = log(c, "Auto-bye roka: owner ki awaaz nahi thi (anjaan/shor), session chalu raha")

    /** Ye tools "privileged" nahi hain (sirf code/website banane wale), baaki sab device-control/camera/screen/notification hain. */
    private val SAFE = setOf("list_projects", "list_schedules", "preview_website", "build_website", "build_project", "make_video", "edit_website", "generate_code", "end_session", "web_search", "get_weather", "open_google_search")

    private fun enc(md: VpModel) = md.m.joinToString(",") + "|" + md.s.joinToString(",") + "|" + md.thr
    private fun dec(s: String): VpModel? = try {
        val p = s.split("|")
        VpModel(p[0].split(",").map { it.toDouble() }.toDoubleArray(), p[1].split(",").map { it.toDouble() }.toDoubleArray(), p[2].toDouble())
    } catch (e: Exception) { null }

    fun owner(p: SharedPreferences): VpModel? = VoicePrint.load(p)
    fun removeOwner(p: SharedPreferences) { p.edit().remove("vp").apply() }

    fun family(p: SharedPreferences): List<Person> = try {
        val a = JSONArray(p.getString("vgFamily", "[]") ?: "[]")
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val md = Vault.dec(o.optString("d"))?.let { dec(it) } ?: return@mapNotNull null
            Person(o.optString("id"), o.optString("name"), md)
        }
    } catch (e: Exception) { emptyList() }

    fun addFamily(p: SharedPreferences, name: String, md: VpModel) {
        val a = try { JSONArray(p.getString("vgFamily", "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
        a.put(JSONObject().put("id", UUID.randomUUID().toString()).put("name", name.trim()).put("d", Vault.enc(enc(md))))
        p.edit().putString("vgFamily", a.toString()).apply()
    }

    fun removeFamily(p: SharedPreferences, id: String) {
        val a = try { JSONArray(p.getString("vgFamily", "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
        val out = JSONArray()
        for (i in 0 until a.length()) { val o = a.getJSONObject(i); if (o.optString("id") != id) out.put(o) }
        p.edit().putString("vgFamily", out.toString()).apply()
    }

    /** Awaaz kis ki hai. role: owner / family / unknown / unverified (awaaz mili hi nahi ya koi voice enrolled nahi). */
    fun identifyRaw(p: SharedPreferences, pcm: ShortArray): Speaker {
        val people = ArrayList<Triple<String, String, VpModel>>()
        owner(p)?.let { people.add(Triple("owner", "Owner", it)) }
        family(p).forEach { people.add(Triple("family", it.name, it.model)) }
        if (people.isEmpty()) return Speaker("unverified", "", 0.0, 0.0, 0)
        val fr = VoicePrint.voicedFrames(pcm)
        if (fr.size < VoicePrint.MIN_VOICED) return Speaker("unverified", "", 0.0, 0.0, 0)
        val sens = p.getFloat("vpSens", 1f)
        var bestRole = "unknown"; var bestName = ""; var bestRatio = Double.MAX_VALUE; var bestSc = 0.0; var bestLim = 1.0
        var bestMd: VpModel? = null
        for ((role, name, md) in people) {
            val sc = VoicePrint.matchScore(md, fr); val lim = VoicePrint.limit(md, sens)
            val ratio = sc / lim
            if (ratio < bestRatio) { bestRatio = ratio; bestRole = role; bestName = name; bestSc = sc; bestLim = lim; bestMd = md }
        }
        val matched = bestRatio <= 1.0
        if (matched && bestRole == "owner" && bestRatio < 0.8) bestMd?.let { VoicePrint.adapt(p, it, fr) }
        // Indicative confidence: match mein 50-100, no-match mein 0-50
        val conf = if (matched) (50 + 50 * (1 - bestRatio)).toInt() else (50 / bestRatio).toInt().coerceIn(0, 49)
        return Speaker(if (matched) bestRole else "unknown", if (matched) bestName else "", bestSc, bestLim, conf)
    }

    /** Guardian OFF ho to null. */
    fun identify(c: Context, pcm: ShortArray): Speaker? {
        val p = c.getSharedPreferences("divya", 0)
        if (!p.getBoolean("voiceLock", false)) return null
        return identifyRaw(p, pcm)
    }

    /** null = kaam karne do. Warna model ko batane wala karan. */
    fun check(c: Context, sp: Speaker?, tool: String): String? {
        if (sp == null) return null
        last = sp
        if (tool in SAFE || sp.role == "unverified") return null      // awaaz nahi mili (typed chat) -> allow, pehle jaisa
        val p = c.getSharedPreferences("divya", 0)
        val mode = p.getInt("vgMode", 1)                                // 0 Everyone, 1 Owner only (purana Voice Lock jaisa), 2 Owner+Family
        val away = p.getBoolean("vgAway", false)
        var ok = when (mode) { 0 -> true; 1 -> sp.role == "owner"; else -> sp.role == "owner" || sp.role == "family" }
        if (away && sp.role == "unknown") ok = false
        if (ok) return null
        val who = when (sp.role) { "family" -> "family member (${sp.name})"; "unknown" -> "anjaan awaaz"; else -> "awaaz" }
        log(c, "Roka: $tool - $who (match ${sp.conf}%)")
        if (away || sp.role == "unknown") notifyOwner(c, "Divya: $who ne '$tool' karwane ki koshish ki, roka gaya.")
        return "Voice Guardian ne rok diya: $who (match ${sp.conf}%). Ye kaam sirf allowed awaaz se hota hai. Kuch mat karo, user ko chhota sa batao."
    }

    private var lastHeardLog = 0L
    private var lastHeardNotify = 0L

    /** Mic gate ne anjaan awaaz Gemini tak jaane se roki. Log har 20 sec mein ek baar; Away mode ON ho to notification (har 2 min mein ek baar). */
    fun blockedHeard(c: Context, sp: Speaker) {
        val now = System.currentTimeMillis()
        val p = c.getSharedPreferences("divya", 0)
        if (now - lastHeardLog > 20_000) { lastHeardLog = now; log(c, "Filter: anjaan awaaz Divya tak nahi pahunchi (match ${sp.conf}%)") }
        if (p.getBoolean("vgAway", false) && now - lastHeardNotify > 120_000) {
            lastHeardNotify = now
            notifyOwner(c, "Divya: ek anjaan awaaz suni gayi, Divya tak nahi pahunchi.")
        }
    }

    // ---------- log + notification ----------
    fun log(p: SharedPreferences): List<Pair<Long, String>> = try {
        val a = JSONArray(p.getString("vgLog", "[]") ?: "[]")
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Pair(o.optLong("t"), o.optString("m")) } }
    } catch (e: Exception) { emptyList() }

    fun clearLog(p: SharedPreferences) { p.edit().remove("vgLog").apply() }

    private fun log(c: Context, msg: String) {
        val p = c.getSharedPreferences("divya", 0)
        val out = JSONArray().put(JSONObject().put("t", System.currentTimeMillis()).put("m", msg))
        log(p).take(19).forEach { out.put(JSONObject().put("t", it.first).put("m", it.second)) }
        p.edit().putString("vgLog", out.toString()).apply()
    }

    private fun notifyOwner(c: Context, text: String) {
        try {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel("guard", "Voice Guardian", NotificationManager.IMPORTANCE_DEFAULT))
            val n = android.app.Notification.Builder(c, "guard").setContentTitle("Voice Guardian").setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock).setAutoCancel(true).build()
            nm.notify(7001, n)
        } catch (_: Exception) {}
    }

    // ---------- recording helpers (UI se) ----------
    fun enrollAsync(secs: Int, done: (VpModel?) -> Unit) {
        val h = Handler(Looper.getMainLooper())
        Thread {
            WakeBus.liveActive = true
            val pcm = try { VoicePrint.record(secs) } catch (e: Exception) { null }
            WakeBus.liveActive = false
            val md = pcm?.let { VoicePrint.train(VoicePrint.chunks(it)) }
            h.post { done(md) }
        }.start()
    }

    fun testAsync(p: SharedPreferences, done: (Speaker?) -> Unit) {
        val h = Handler(Looper.getMainLooper())
        Thread {
            WakeBus.liveActive = true
            val pcm = try { VoicePrint.record(4) } catch (e: Exception) { null }
            WakeBus.liveActive = false
            val sp = pcm?.let { identifyRaw(p, it) }
            h.post { if (sp != null) last = sp; done(sp) }
        }.start()
    }
}
