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
        if (startCount == 0 && DivyaService.instance != null) Thread {
            val r = refreshCounts(c); p(c).edit().putInt("gr_start", p(c).getInt("gr_now", 0)).apply(); Log.i(TAG, "baseline $r")
        }.start()
        val perDay = Math.ceil((target - startCount).coerceAtLeast(1).toDouble() / days).toInt()
        return "done: goal set - $pf par $days din mein $target (abhi $startCount). Roz ~$perDay naye follower chahiye (abhi ka asli count Divya khud padh rahi hai). " +
            "Honest baat user ko batao: ye stretch goal hai, guarantee nahi - sabse zyada fark roz 1-3 achhi video + pehle 2 second ka hook + consistency se padta hai. " +
            "Ab pucho: roz kitne baje auto-post chalu karun? (growth_autopilot)"
    }

    /** current>0: user ka bataya count; 0: Divya khud app se padhti hai; -1: sirf saved count se pace. */
    fun status(c: Context, current: Int): String {
        val pr = p(c)
        val target = pr.getInt("gr_target", 0)
        if (target == 0) return "failed: abhi koi goal set nahi hai. Pehle set_growth_goal (platform, target, days, niche)."
        var src = "user ne bataya"
        if (current > 0) pr.edit().putInt("gr_now", current).apply()
        else if (current == 0) { val r = refreshCounts(c); src = "khud padha ($r)" }
        else src = "pichhli reading"
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
            "(Count source: $src)"
    }


    // ------------------------------------------------------------------ AUTO FOLLOWER / SUBSCRIBER COUNT
    private val numRe = Regex("([0-9][0-9.,]*)\\s*([KkMm]?)[\\s|]*(?:\\[btn\\]\\s*)?(?:followers?|subscribers?)", RegexOption.IGNORE_CASE)

    /** "1,234" / "1.2K" / "3M" -> Int. Suffix ho to '.' decimal; warna , aur . hazaar-separator. */
    private fun parseCount(num: String, suf: String): Int? {
        val n = num.trim().trimEnd('.', ',')
        if (suf.isNotEmpty()) {
            val v = n.replace(",", ".").toDoubleOrNull() ?: return null
            return (v * if (suf.equals("k", true)) 1_000 else 1_000_000).toInt()
        }
        return n.replace(",", "").replace(".", "").toIntOrNull()
    }

    private fun countFrom(screen: String): Int? =
        numRe.findAll(screen).mapNotNull { parseCount(it.groupValues[1], it.groupValues[2]) }.firstOrNull()

    private fun backToDivya(c: Context) {
        try { c.packageManager.getLaunchIntentForPackage(c.packageName)?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } catch (_: Exception) {}
    }

    /** App kholkar apna profile/channel dekhta hai aur count padhta hai. null = nahi mila (login/screen alag). */
    fun readCount(c: Context, platform: String): Int? {
        val svc = DivyaService.instance ?: return null
        val pkg = if (platform == "instagram") IG else YT
        val l = c.packageManager.getLaunchIntentForPackage(pkg) ?: return null
        try { c.startActivity(l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { return null }
        svc.waitOpen(pkg, 5000); SystemClock.sleep(1800)
        var found: Int? = null
        try {
            if (platform == "instagram") {
                if (!(svc.tapDesc("Profile", 2500) || svc.tapText("Profile", 800))) return null
                for (i in 0 until 5) { SystemClock.sleep(1200); found = countFrom(svc.readScreen(2500)); if (found != null) break }
            } else {
                if (svc.tapAnyExact(listOf("You", "YOU"), 2500) == null) return null
                SystemClock.sleep(1500)
                found = countFrom(svc.readScreen(2500))
                if (found == null) {
                    svc.tapAnyExact(listOf("View channel", "Your channel", "VIEW CHANNEL"), 2500)
                    for (i in 0 until 5) { SystemClock.sleep(1200); found = countFrom(svc.readScreen(2500)); if (found != null) break }
                }
            }
        } finally { backToDivya(c) }
        return found
    }

    /** Goal wale platform(s) ka count khud padho aur save karo. Result: kya mila. */
    fun refreshCounts(c: Context): String {
        val pr = p(c); val pf = pr.getString("gr_platform", "youtube").orEmpty()
        val list = if (pf == "both") listOf("youtube", "instagram") else listOf(pf)
        val got = HashMap<String, Int>()
        for (t in list) { BgTasks.stage("$t count dekh rahi hoon…"); readCount(c, t)?.let { got[t] = it; pr.edit().putInt("gr_now_$t", it).apply() } }
        if (got.isEmpty()) return "auto-count nahi mil paya (login / Accessibility / app screen check karo)"
        val now = if (pf == "both") list.minOf { pr.getInt("gr_now_$it", 0) } else got.values.first()
        pr.edit().putInt("gr_now", now).apply(); stampCount(c, now)
        return got.entries.joinToString(", ") { "${it.key}: ${it.value}" }
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
        val trend = try { PhoneTools.webSearch(c, "trending $niche short video topics this week India").take(700) } catch (_: Exception) { "" }
        val best = bestTopics(c)
        val r = ai(c, "You are a viral short-form video strategist. Reply with ONE video idea only (one line, Hinglish, under 20 words), no quotes, no numbering.",
            "Channel niche: $niche. Already made (do NOT repeat): $prev. " + (if (best.isNotBlank()) "Topics that gained the most followers so far (make something in this spirit): $best. " else "") + (if (trend.isNotBlank()) "Current trend notes: $trend. " else "") + "Give a fresh idea with a shocking first-2-second hook that a new viewer would watch fully and follow for more.", false)
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
        val vf: File = file
        val meta = metaFor(c, vf)
        val targets = if (pf == "both") listOf("youtube", "instagram") else listOf(pf)
        val topicUsed = topic.ifBlank { p(c).let { pr -> try { JSONArray(pr.getString("gr_topics", "[]")).let { a -> if (a.length() > 0) a.getString(a.length() - 1) else meta.title } } catch (_: Exception) { meta.title } } }
        val out = targets.map { t ->
            BgTasks.stage("${t.replaceFirstChar { it.uppercase() }} par upload…")
            var r = uploadTo(c, svc, t, vf, meta)
            if (!r.startsWith("done") && !r.contains("installed nahi") && !BgTasks.isCancelled()) { BgTasks.stage("Dobara koshish…"); SystemClock.sleep(2000); svc.global("back"); r = uploadTo(c, svc, t, vf, meta) }
            logPost(c, topicUsed, meta.title, t, r.startsWith("done"))
            "$t: $r"
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

    // ------------------------------------------------------------------ HISTORY + LEARNING
    private fun hist(c: Context): JSONArray = try { JSONArray(p(c).getString("gr_hist", "[]")) } catch (_: Exception) { JSONArray() }
    private fun saveHist(c: Context, a: JSONArray) { while (a.length() > 60) a.remove(0); p(c).edit().putString("gr_hist", a.toString()).apply() }

    private fun logPost(c: Context, topic: String, title: String, pf: String, ok: Boolean) {
        val a = hist(c)
        a.put(JSONObject().put("t", System.currentTimeMillis()).put("topic", topic).put("title", title).put("pf", pf).put("ok", ok).put("count", 0))
        saveHist(c, a)
    }

    /** Latest entry ka count bharo (agar pehle khali ho) - isse pata chalta hai kis post ke baad kitne naye follower aaye. */
    private fun stampCount(c: Context, now: Int) {
        val a = hist(c); if (a.length() == 0) return
        val o = a.getJSONObject(a.length() - 1)
        if (o.optInt("count") == 0) { o.put("count", now); saveHist(c, a) }
    }

    private class Row(val topic: String, val title: String, val delta: Int, val hour: Int, val ok: Boolean)

    private fun rows(c: Context): List<Row> {
        val a = hist(c); val out = ArrayList<Row>()
        var prev = p(c).getInt("gr_start", 0)
        for (k in 0 until a.length()) {
            val o = a.getJSONObject(k); val cnt = o.optInt("count")
            val d = if (cnt > 0 && prev > 0) cnt - prev else 0
            if (cnt > 0) prev = cnt
            val h = java.util.Calendar.getInstance().apply { timeInMillis = o.optLong("t") }.get(java.util.Calendar.HOUR_OF_DAY)
            out.add(Row(o.optString("topic"), o.optString("title"), d, h, o.optBoolean("ok")))
        }
        return out
    }

    private fun bestTopics(c: Context): String =
        rows(c).filter { it.delta > 0 }.sortedByDescending { it.delta }.take(3).joinToString("; ") { it.topic.take(60) }

    /** growth_report: pichhli posts ka hisaab + kya chal raha hai. */
    fun report(c: Context): String {
        if (p(c).getInt("gr_target", 0) == 0) return "failed: pehle goal set karo."
        val r = rows(c).takeLast(7)
        if (r.isEmpty()) return "done: abhi koi post nahi hui. Pehle growth_post ya autopilot chalao."
        val lines = r.joinToString("\n") { "• ${it.title.take(45)} - ${if (it.delta != 0) (if (it.delta > 0) "+" else "") + it.delta + " followers" else "count pending"}${if (!it.ok) " (upload adhura)" else ""}" }
        val best = r.filter { it.delta > 0 }.maxByOrNull { it.delta }
        val bestHour = r.filter { it.delta > 0 }.groupBy { it.hour }.maxByOrNull { e -> e.value.sumOf { it.delta } }?.key
        return "done: Pichhli ${r.size} posts:\n$lines\n" +
            (if (best != null) "Sabse achha: '${best.topic.take(50)}' (+${best.delta}). Aisa aur banaungi. " else "Abhi kisi post se clear jeet nahi dikhi. ") +
            (if (bestHour != null) "Best time ~$bestHour:00 ke aas-paas lag raha hai. " else "") +
            "Note: ye andaza hai (follower aur bhi wajah se badhte hain). Total posts ${p(c).getInt("gr_posts", 0)}."
    }

    // ------------------------------------------------------------------ PROMOTION (real share, spam nahi)
    fun promote(c: Context, link: String): String {
        val pr = p(c)
        if (link.isNotBlank()) pr.edit().putString("gr_link", link.trim()).apply()
        val lk = pr.getString("gr_link", "").orEmpty()
        val niche = pr.getString("gr_niche", "").orEmpty(); val pf = pr.getString("gr_platform", "youtube").orEmpty()
        val msg = ai(c, "You write short, friendly Hinglish promo messages that a creator sends to friends/family/WhatsApp status. 2-3 lines, 1-2 emojis, a clear ask (watch + follow), no false claims, no begging spam.",
            "Channel: $pf, niche: $niche, link: ${lk.ifBlank { "(none yet)" }}. Include the link if present.", false)
            ?: "Meri nayi $pf channel dekho aur follow karo! ${lk}"
        val i = Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg), "Channel promote karo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { c.startActivity(i) } catch (e: Exception) { return "failed: share sheet nahi khula (${e.message})" }
        return "done: promo message tayyar, share sheet khul gayi - user WhatsApp status / groups / dost chun kar bheje (Divya sabko apne-aap spam nahi karti). Message: ${msg.take(160)}" +
            if (lk.isBlank()) " | Tip: channel ka link bata do to message mein judega." else ""
    }

    // ------------------------------------------------------------------ AUTOPILOT (roz, 1-3 posts)
    fun autopilot(c: Context, on: Boolean, hour: Int, minute: Int, perDay: Int = 1): String {
        val pr = p(c)
        if (!on) { pr.edit().putBoolean("gr_auto", false).putInt("gr_gen", pr.getInt("gr_gen", 0) + 1).apply(); return "done: roz ka auto-post band." }
        if (pr.getInt("gr_target", 0) == 0) return "failed: pehle goal set karo (set_growth_goal)."
        if (hour !in 0..23) return "failed: time batao (kitne baje roz post karun?)."
        val n = perDay.coerceIn(1, 3); val m = minute.coerceIn(0, 59)
        val gen = pr.getInt("gr_gen", 0) + 1   // purane schedules khud ignore ho jaate hain
        val slots = (0 until n).map { (hour + it * (if (n == 2) 8 else 5)) % 24 }
        pr.edit().putBoolean("gr_auto", true).putInt("gr_gen", gen).putInt("gr_fail", 0).putString("gr_slots", slots.joinToString(",") { "$it:$m" }).apply()
        val pf = pr.getString("gr_platform", "youtube").orEmpty()
        var last = ""
        slots.forEachIndexed { k, h -> last = Scheduler.add(c, "growth_post", pf, "slot:$k:$gen", h, m, 0); SystemClock.sleep(5) }
        return if (last.startsWith("done")) "done: roz ${slots.joinToString(", ") { "%02d:%02d".format(it, m) }} par $pf par nayi video apne-aap ban kar post hogi. $last" else last
    }

    /** Scheduler se bulaya jata hai. Pehle agla din arm karo, phir post. 3 baar lagataar fail ho to autopilot ruk jata hai. */
    fun runScheduled(c: Context, platform: String, msg: String = "") {
        val pr = p(c)
        if (!pr.getBoolean("gr_auto", false)) return
        val parts = msg.split(":")
        val slot = parts.getOrNull(1)?.toIntOrNull() ?: 0; val gen = parts.getOrNull(2)?.toIntOrNull() ?: pr.getInt("gr_gen", 0)
        if (gen != pr.getInt("gr_gen", 0)) return
        val sl = pr.getString("gr_slots", "18:0").orEmpty().split(",").getOrNull(slot)?.split(":")
        Scheduler.add(c, "growth_post", platform, msg.ifBlank { "slot:0:$gen" }, sl?.getOrNull(0)?.toIntOrNull() ?: 18, sl?.getOrNull(1)?.toIntOrNull() ?: 0, 0)
        val t = BgTasks.start("Growth post")
        try {
            val r = post(c, platform, "", true)
            val ok = r.contains("done:")
            val fails = if (ok) 0 else pr.getInt("gr_fail", 0) + 1
            pr.edit().putInt("gr_fail", fails).apply()
            BgTasks.stage("Count update…")
            val cnt = try { refreshCounts(c) } catch (_: Exception) { "count nahi mila" }
            val prog = status(c, current = -1)
            var note = ""
            if (fails >= 3) { pr.edit().putBoolean("gr_auto", false).apply(); note = " | ⚠ 3 baar lagataar fail - autopilot roka. Apps/login/Accessibility check karke dobara chalao." }
            Scheduler.notify(c, if (ok) "Divya: aaj ka post ✅" else "Divya: post adhura ⚠", (r.take(110) + " | " + cnt + " | " + prog.take(120) + note))
        } finally { BgTasks.finish(t) }
    }
}
