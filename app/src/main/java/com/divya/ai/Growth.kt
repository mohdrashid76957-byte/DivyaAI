package com.divya.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Channel Growth Autopilot (YouTube Shorts + Instagram Reels).
 *  - Target: "1 hafte mein 1000 followers/subscribers" (set_growth_goal)
 *  - Video: VideoGen se nayi video banti hai (ya latest wali use hoti hai), AI se title/caption/hashtags
 *  - Post: YouTube/Instagram app ko share-intent + Accessibility se Next/Share/Upload dabana
 *  - Autopilot: roz tay time par apne-aap video banao + post karo (Scheduler)
 *
 * Sirf asli content + asli posting. Fake follower/like/bot/follow-for-follow NAHI - ye YouTube/Instagram ke rules ke khilaaf hai
 * aur account ban karwa deta hai. Kisi ko "1000 pakka" ka vaada nahi - ye app sirf consistent, achhe, optimised posting karta hai.
 */
object Growth {
    private const val TAG = "DivyaGrowth"
    private const val YT = "com.google.android.youtube"
    private const val IG = "com.instagram.android"
    private fun p(c: Context) = c.getSharedPreferences("divya", 0)

    // ------------------------------------------------------------------ GOAL
    fun setGoal(c: Context, platform: String, target: Int, days: Int, niche: String, startCount: Int): String {
        val pf = norm(platform)
        if (target <= 0 || days <= 0) return "failed: target (kitne followers/subscribers) aur days (kitne din mein) batao."
        p(c).edit().putString("gr_platform", pf).putInt("gr_target", target).putInt("gr_days", days)
            .putString("gr_niche", niche.trim()).putInt("gr_start", startCount.coerceAtLeast(0)).putInt("gr_now", startCount.coerceAtLeast(0))
            .putLong("gr_startAt", System.currentTimeMillis()).putInt("gr_posts", 0).putString("gr_topics", "[]").apply()
        val perDay = Math.ceil((target - startCount).coerceAtLeast(1).toDouble() / days).toInt()
        return "done: goal set - $pf par $days din mein $target (abhi $startCount). Roz ~$perDay naye follower chahiye. " +
            "Honest baat user ko batao: ye stretch goal hai, guarantee nahi - sabse zyada fark roz 1-3 achhi video + pehle 2 second ka hook + consistency se padta hai. " +
            "Ab pucho: roz kitne baje auto-post chalu karun? (growth_autopilot)"
    }

    /** current > 0 ho to naya follower count save karke pace bataata hai. */
    fun status(c: Context, current: Int): String {
        val pr = p(c)
        val target = pr.getInt("gr_target", 0)
        if (target == 0) return "failed: abhi koi goal set nahi hai. Pehle set_growth_goal (platform, target, days, niche)."
        if (current > 0) pr.edit().putInt("gr_now", current).apply()
        val now = pr.getInt("gr_now", 0); val start = pr.getInt("gr_start", 0); val days = pr.getInt("gr_days", 7)
        val used = ((System.currentTimeMillis() - pr.getLong("gr_startAt", 0L)) / 86_400_000L).toInt() + 1
        val left = (days - used + 1).coerceAtLeast(0)
        val need = (target - now).coerceAtLeast(0)
        val perDay = if (left > 0) Math.ceil(need.toDouble() / left).toInt() else need
        val gained = now - start
        val pace = if (used > 0) gained.toDouble() / used else 0.0
        val verdict = when {
            need == 0 -> "Target poora! 🎉"
            left == 0 -> "Time khatam, $need baaki. Naya goal/zyada din rakhna behtar."
            pace * left >= need -> "Pace achhi hai, isi tarah chalte raho."
            else -> "Abhi pace kam hai (roz ~${"%.0f".format(pace)} aa rahe, chahiye ~$perDay). Posts badhao ya hook/topic badlo - best chalne wale topic dobara banao."
        }
        return "done: ${pr.getString("gr_platform", "")} | din $used/$days | abhi $now / $target | ${pr.getInt("gr_posts", 0)} posts ho chuke | $verdict " +
            "(Follower count Divya khud nahi padh sakti - user se pucho/ya wo bataye to growth_status(current) se update karo.)"
    }

    private fun norm(s: String): String = when {
        s.contains("insta", true) || s.contains("reel", true) -> "instagram"
        s.contains("both", true) || s.contains("dono", true) -> "both"
        else -> "youtube"
    }

    // ------------------------------------------------------------------ AI IDEA + METADATA
    private fun ai(c: Context, sys: String, user: String, json: Boolean): String? = try {
        ProEngine.complete(c, sys, user, ProEngine.fastModel(c), json, AtomicBoolean(false), null, 2048).trim()
    } catch (e: Exception) { Log.w(TAG, "ai", e); null }

    private fun ideaFor(c: Context): String {
        val pr = p(c); val niche = pr.getString("gr_niche", "").orEmpty().ifBlank { "interesting facts" }
        val old = try { JSONArray(pr.getString("gr_topics", "[]")) } catch (_: Exception) { JSONArray() }
        val prev = (0 until old.length()).joinToString("; ") { old.getString(it) }.takeLast(600)
        val r = ai(c, "You are a viral short-form video strategist. Reply with ONE video idea only (one line, Hinglish, under 20 words), no quotes, no numbering.",
            "Channel niche: $niche. Already made (do NOT repeat): $prev. Give a fresh idea with a shocking first-2-second hook that a new viewer would watch fully and follow for more.", false)
        val idea = r?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.trim('"') ?: "$niche - ek chauka dene wala fact"
        old.put(idea); while (old.length() > 25) old.remove(0)
        pr.edit().putString("gr_topics", old.toString()).apply()
        return idea
    }

    private class Meta(val title: String, val caption: String)

    private fun metaFor(c: Context, f: File): Meta {
        val side = try { File(f.path + ".txt").readText() } catch (_: Exception) { "" }
        val firstLine = side.lineSequence().firstOrNull()?.take(90).orEmpty().ifBlank { "Must watch" }
        val niche = p(c).getString("gr_niche", "").orEmpty()
        val raw = ai(c, "You write YouTube Shorts / Instagram Reels metadata. Reply ONLY JSON: {\"title\":\"max 70 chars, curiosity hook, ends with #Shorts\",\"caption\":\"2 short lines: hook + 'Follow/Subscribe for more' call-to-action, then 8-12 relevant hashtags (mix of broad + niche), max 300 chars\"}",
            "Niche: $niche\nVideo script/captions:\n${side.take(900)}", true)
        return try {
            val j = JSONObject(raw!!.removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
            Meta(j.optString("title").ifBlank { "$firstLine #Shorts" }, j.optString("caption").ifBlank { "$firstLine\nFollow for more! #shorts #reels #viral" })
        } catch (_: Exception) { Meta("$firstLine #Shorts", "$firstLine\nFollow for more! #shorts #reels #trending") }
    }

    // ------------------------------------------------------------------ POST
    private fun hasApp(c: Context, pkg: String) = try { c.packageManager.getLaunchIntentForPackage(pkg) != null } catch (_: Exception) { false }

    /** platform: youtube | instagram | both. makeNew=true to nayi video banti hai (topic khali ho to AI idea deta hai). */
    fun post(c: Context, platform: String, topic: String, makeNew: Boolean): String {
        val pf = norm(platform.ifBlank { p(c).getString("gr_platform", "youtube").orEmpty() })
        val svc = DivyaService.instance ?: return "failed: Accessibility ON nahi hai (Settings > Permissions)"
        var file: File? = if (makeNew) null else VideoGen.list(c).firstOrNull()
        if (file == null) {
            BgTasks.stage("Idea soch rahi hoon…")
            val t = topic.ifBlank { ideaFor(c) }
            BgTasks.stage("Video ban rahi hai…")
            val r = VideoGen.createTracked(c, t, "hindi", 30, "facts")
            if (!r.startsWith("done")) return "failed: video nahi bani - $r"
            file = VideoGen.list(c).firstOrNull() ?: return "failed: video file nahi mili"
        }
        if (BgTasks.isCancelled()) return "done: cancel kar diya."
        val meta = metaFor(c, file)
        val targets = if (pf == "both") listOf("youtube", "instagram") else listOf(pf)
        val out = targets.map { t ->
            BgTasks.stage("${t.replaceFirstChar { it.uppercase() }} par upload…")
            "$t: " + uploadTo(c, svc, t, file, meta)
        }
        val pr = p(c); if (out.any { it.contains("done:") }) pr.edit().putInt("gr_posts", pr.getInt("gr_posts", 0) + 1).apply()
        return out.joinToString(" | ") + " | Title: ${meta.title}"
    }

    private fun uploadTo(c: Context, svc: DivyaService, platform: String, f: File, m: Meta): String {
        val pkg = if (platform == "instagram") IG else YT
        if (!hasApp(c, pkg)) return "failed: ${platform} app installed nahi hai ya login nahi hai."
        val uri = try { FileProvider.getUriForFile(c, c.packageName + ".fileprovider", f) } catch (e: Exception) { return "failed: file share nahi ho payi (${e.message})" }
        try { (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("caption", m.caption)) } catch (_: Exception) {}
        val i = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, m.caption).putExtra(Intent.EXTRA_SUBJECT, m.title).setPackage(pkg)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try { c.startActivity(i) } catch (e: Exception) { return "failed: ${platform} khul nahi paaya (${e.message})" }
        svc.waitOpen(pkg, 6000); SystemClock.sleep(2500)

        val text = if (platform == "instagram") m.caption else m.title
        val steps = if (platform == "instagram") listOf("Reels", "REEL", "Reel", "New reel", "Next", "Continue") else listOf("Next", "NEXT", "Continue", "Done")
        val finals = if (platform == "instagram") listOf("Share", "Post", "SHARE") else listOf("Upload Short", "Upload short", "Upload video", "Upload", "UPLOAD")
        var typed = false
        for (round in 0 until 14) {
            if (BgTasks.isCancelled()) return "done: cancel kar diya."
            val screen = svc.readScreen(900)
            if (!typed && (screen.contains("caption", true) || screen.contains("title", true) || screen.contains("describe", true))) {
                typed = svc.typeText(text); SystemClock.sleep(600)
            }
            if (typed) {
                val fin = svc.tapAnyExact(finals, 1200)
                if (fin != null) {
                    SystemClock.sleep(5000)
                    return "done: '$fin' dabaya - ${platform} par upload shuru ho gaya (processing mein 1-2 min lag sakte hain). Ek baar app mein check kar lena."
                }
            }
            val st = svc.tapAnyExact(steps, 1500)
            if (st == null && !typed) SystemClock.sleep(1500)
            SystemClock.sleep(1200)
        }
        return "partial: ${platform} khola aur video load ki, par aakhri Upload/Share button nahi mila (app ka design badla ho sakta hai). Caption copy ho gaya hai - screen par bacha step haath se kar do. Screen: " + svc.readScreen(400)
    }

    // ------------------------------------------------------------------ AUTOPILOT (roz)
    fun autopilot(c: Context, on: Boolean, hour: Int, minute: Int): String {
        val pr = p(c)
        if (!on) { pr.edit().putBoolean("gr_auto", false).apply(); return "done: roz ka auto-post band. (Pehle se laga hua ek aakhri schedule aa sakta hai - skip karne ke liye cancel_schedules.)" }
        if (pr.getInt("gr_target", 0) == 0) return "failed: pehle goal set karo (set_growth_goal)."
        if (hour !in 0..23) return "failed: time batao (kitne baje roz post karun?)."
        pr.edit().putBoolean("gr_auto", true).putInt("gr_h", hour).putInt("gr_m", minute.coerceIn(0, 59)).apply()
        val r = Scheduler.add(c, "growth_post", pr.getString("gr_platform", "youtube").orEmpty(), "daily growth post", hour, minute, 0)
        return if (r.startsWith("done")) "done: roz %02d:%02d par ${pr.getString("gr_platform", "")} par nayi video apne-aap ban kar post hogi. $r".format(hour, minute) else r
    }

    /** Scheduler se bulaya jata hai (background thread). Pehle agla din arm karo, phir aaj ki video post karo. */
    fun runScheduled(c: Context, platform: String) {
        val pr = p(c)
        if (!pr.getBoolean("gr_auto", false)) return
        Scheduler.add(c, "growth_post", platform, "daily growth post", pr.getInt("gr_h", 18), pr.getInt("gr_m", 0), 0)
        val t = BgTasks.start("Growth post")
        try {
            val r = post(c, platform, "", true)
            Scheduler.notify(c, "Divya: aaj ka post", r.take(180))
        } finally { BgTasks.finish(t) }
    }
}
