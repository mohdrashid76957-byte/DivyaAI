package com.divya.ai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray

class DivyaService : AccessibilityService() {
    companion object { var instance: DivyaService? = null }
    override fun onServiceConnected() {
        instance = this
        if (getSharedPreferences("divya", 0).getBoolean("float", false)) com.divya.ai.glow.FloatingAvatar.show(this)
    }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        com.divya.ai.glow.GlowOverlay.hide(); com.divya.ai.glow.FloatingAvatar.hide(); instance = null; super.onDestroy()
    }

    private fun nap(ms: Long) { try { Thread.sleep(ms) } catch (_: InterruptedException) {} }

    fun global(a: String): Boolean = when (a) {
        "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        "lock" -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        "screenshot" -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        else -> false
    }

    fun curPkg(): String = rootInActiveWindow?.packageName?.toString() ?: ""

    /** App khulne tak ruko (fixed delay nahi, jaise hi khule turant aage badho). */
    fun waitOpen(pkg: String, timeoutMs: Long = 2500) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (curPkg() == pkg) { nap(250); return }
            nap(50)
        }
    }

    /** Active window + baaki application windows ki roots (apne overlay ko chhodkar). */
    private fun allRoots(): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        try { rootInActiveWindow?.let { out.add(it) } } catch (_: Exception) {}
        try {
            for (w in windows) {
                if (w.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val r = w.root ?: continue
                if (r.packageName?.toString() == packageName) continue
                if (out.none { it.packageName == r.packageName && it.windowId == r.windowId }) out.add(r)
            }
        } catch (_: Exception) {}
        return out
    }

    private fun labelsOf(n: AccessibilityNodeInfo): List<String> {
        val l = ArrayList<String>(3)
        n.text?.toString()?.let { if (it.isNotBlank()) l.add(it.trim()) }
        n.contentDescription?.toString()?.let { if (it.isNotBlank()) l.add(it.trim()) }
        if (Build.VERSION.SDK_INT >= 26) n.hintText?.toString()?.let { if (it.isNotBlank()) l.add(it.trim()) }
        return l
    }

    /** Text / description / hint mein t wala sabse achha node (exact > shuru > beech mein; clickable ko tarjeeh). */
    private fun findBest(t: String, exactOnly: Boolean = false): AccessibilityNodeInfo? {
        val q = t.trim().lowercase()
        if (q.isEmpty()) return null
        var best: AccessibilityNodeInfo? = null; var bestScore = Int.MAX_VALUE
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 60) return
            if (n.isVisibleToUser) {
                var sc = Int.MAX_VALUE
                for (lb in labelsOf(n)) {
                    val x = lb.lowercase()
                    val s = when { x == q -> 0; exactOnly -> Int.MAX_VALUE; x.startsWith(q) -> 2; x.contains(q) -> 4; else -> Int.MAX_VALUE }
                    if (s < sc) sc = s
                }
                if (sc != Int.MAX_VALUE) { if (n.isClickable) sc -= 1; if (sc < bestScore) { bestScore = sc; best = n } }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        for (r in allRoots()) walk(r, 0)
        return best
    }

    /** Screen par text aane tak ruko, aate hi turant mil jaye. */
    private fun waitNode(t: String, timeoutMs: Long): AccessibilityNodeInfo? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            findBest(t)?.let { return it }
            if (SystemClock.uptimeMillis() >= end) return null
            nap(60)
        }
    }

    fun tapAt(x: Float, y: Float): Boolean {
        val p = Path().apply { moveTo(x, y) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 40)).build(), null, null)
    }

    /** n ya uske kisi clickable parent par click, na chale to asli touch gesture. */
    private fun clickOrTap(n: AccessibilityNodeInfo): Boolean {
        var c: AccessibilityNodeInfo? = n
        while (c != null && !c.isClickable) c = c.parent
        if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val r = Rect(); n.getBoundsInScreen(r)
        if (r.isEmpty) return false
        return tapAt(r.centerX().toFloat(), r.centerY().toFloat())
    }

    fun tapText(t: String, waitMs: Long = 2500): Boolean {
        val n = waitNode(t, waitMs) ?: return false
        return clickOrTap(n)
    }

    /** In labels mein se jo bhi screen par EXACT (poora) label ho use dabao (jaise Play Store ka "Install"). Jo dabaya wo label lautata hai. */
    fun tapAnyExact(labels: List<String>, timeoutMs: Long): String? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            for (lab in labels) {
                val n = findBest(lab, true)
                if (n != null && clickOrTap(n)) return lab
            }
            if (SystemClock.uptimeMillis() >= end) return null
            nap(200)
        }
    }

    private fun isInput(n: AccessibilityNodeInfo): Boolean {
        if (n.isEditable) return true
        val cn = n.className?.toString() ?: ""
        if (cn.contains("EditText") || cn.contains("AutoCompleteTextView")) return true
        return n.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
    }

    private fun findInput(n: AccessibilityNodeInfo?, depth: Int = 0): AccessibilityNodeInfo? {
        if (n == null || depth > 60) return null
        if (n.isVisibleToUser && isInput(n)) return n
        for (i in 0 until n.childCount) findInput(n.getChild(i), depth + 1)?.let { return it }
        return null
    }

    private fun currentInput(): AccessibilityNodeInfo? {
        for (r in allRoots()) {
            val f = try { r.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) } catch (_: Exception) { null }
            if (f != null && isInput(f)) return f
            findInput(r)?.let { return it }
        }
        return null
    }

    @Volatile private var lastTyped: AccessibilityNodeInfo? = null

    private fun textOf(n: AccessibilityNodeInfo): String = try { n.refresh(); n.text?.toString().orEmpty() } catch (_: Exception) { "" }
    private fun hasText(n: AccessibilityNodeInfo, t: String): Boolean {
        val cur = textOf(n).lowercase(); val k = t.trim().take(15).lowercase()
        return k.isNotEmpty() && cur.contains(k)
    }

    /** Box mein text daalo: click (focus) -> fresh node -> SET_TEXT -> verify -> na bane to select-all + paste. Chrome/Instagram ke box pehle click par badal jaate hain, isliye node dobara dhundte hain. */
    private fun setIn(n0: AccessibilityNodeInfo, t: String): Boolean {
        var n = n0
        n.performAction(AccessibilityNodeInfo.ACTION_CLICK); nap(220)
        currentInput()?.let { n = it }
        n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val b = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t) }
        val setOk = n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
        nap(120)
        if (setOk && hasText(n, t)) { lastTyped = n; return true }
        // Backup: clipboard se (purana text select karke) paste
        val pasted = try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("divya", t))
            n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val sel = Bundle().apply { putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0); putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, textOf(n).length) }
            n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
            n.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } catch (_: Exception) { false }
        nap(150)
        lastTyped = n
        return pasted || setOk
    }

    private val termuxPkg = "com.termux"

    private fun longPressAt(x: Float, y: Float): Boolean {
        val p = Path().apply { moveTo(x, y) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 900)).build(), null, null)
    }

    /** Termux ka terminal normal text-box nahi hota (SET_TEXT nahi chalta). 1) official RUN_COMMAND (visible window) 2) clipboard + long-press > Paste (newline = Enter). Safety filter dono mein lagta hai. */
    fun termuxType(cmd: String): Boolean {
        val c = cmd.trim(); if (c.isBlank()) return false
        if (Termux.danger(c) != null) return false
        if (Termux.enabled(this) && Termux.installed(this) && Termux.hasPerm(this)) {
            if (Termux.run(this, c, true).startsWith("done")) return true
        }
        if (curPkg() != termuxPkg) { Termux.open(this); waitOpen(termuxPkg, 4000) }
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("divya", c + "\n"))
        } catch (_: Exception) { return false }
        val dm = resources.displayMetrics
        longPressAt(dm.widthPixels / 2f, dm.heightPixels * 0.35f)
        nap(900)
        val hit = tapAnyExact(listOf("Paste", "PASTE", "चिपकाएं"), 2500)
        nap(300)
        return hit != null
    }

    /** Focused / pehle input box mein likho. Termux mein terminal par paste. Box na dikhe to \"Search\" wala box dabake dobara dhundta hai. */
    fun typeText(t: String): Boolean {
        if (curPkg() == termuxPkg) return termuxType(t)
        val end = SystemClock.uptimeMillis() + 3500
        var node: AccessibilityNodeInfo? = null
        var triedSearch = false
        while (true) {
            node = currentInput()
            if (node != null || SystemClock.uptimeMillis() >= end) break
            if (!triedSearch && SystemClock.uptimeMillis() > end - 2800) {
                triedSearch = true
                findBest("search")?.let { clickOrTap(it); nap(500) }
            }
            nap(80)
        }
        val n = node ?: return false
        if (setIn(n, t)) return true
        clickOrTap(n); nap(450)
        val m = currentInput() ?: return false
        return setIn(m, t)
    }

    /** Enter / Search / Go / Send dabana: pehle jo box abhi bhara tha usi par IME-Enter, phir focused box, phir button. */
    fun submit(): Boolean {
        if (curPkg() == termuxPkg) return true   // paste ke saath Enter ja chuka
        val cands = ArrayList<AccessibilityNodeInfo>()
        lastTyped?.let { try { it.refresh(); cands.add(it) } catch (_: Exception) {} }
        try { rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { cands.add(it) } } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT >= 30) for (f in cands) {
            f.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            if (f.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) return true
        }
        return tapText("Search", 500) || tapDesc("Search", 300) || tapText("Go", 400) || tapText("Send", 500) || tapDesc("Send", 300)
    }

    fun scroll(dir: String): Boolean {
        fun find(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (n == null) return null
            if (n.isScrollable) return n
            for (i in 0 until n.childCount) find(n.getChild(i))?.let { return it }
            return null
        }
        val s = find(rootInActiveWindow) ?: return false
        return s.performAction(if (dir == "up") AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    /** Screen par kya dikh raha hai (Divya ko "aankhein"). */
    fun readScreen(max: Int = 1800): String {
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null || sb.length > max || !n.isVisibleToUser) return
            val label = (n.text ?: n.contentDescription ?: "").toString().trim().replace("\n", " ")
            if (label.isNotEmpty() || n.isEditable) {
                val tag = if (n.isEditable) "[input] " else if (n.isClickable) "[btn] " else ""
                sb.append(tag).append(label.take(60)).append(" | ")
            }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(rootInActiveWindow)
        return "app=" + curPkg() + " :: " + sb.toString().take(max)
    }

    private fun findFirstVideo(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        val d = n.contentDescription?.toString() ?: ""
        if (n.isVisibleToUser && n.isClickable && (d.contains("play video", true) ||
                (d.length > 25 && d.contains(" - ") && (d.contains("minute", true) || d.contains("second", true))))) return n
        for (i in 0 until n.childCount) findFirstVideo(n.getChild(i))?.let { return it }
        return null
    }

    /** YouTube par song search karke pehla result seedha play karta hai. */
    /** YouTube search page se pehle video ka ID nikalo (UI tap par nirbhar nahi). Video-only filter => Shorts/channel/playlist skip. */
    private fun firstVideoId(q: String): String? = try {
        val http = okhttp3.OkHttpClient.Builder().connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS).build()
        val req = okhttp3.Request.Builder()
            .url("https://www.youtube.com/results?search_query=" + android.net.Uri.encode(q) + "&sp=EgIQAQ%3D%3D")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
            .header("Accept-Language", "en-US,en;q=0.9").build()
        http.newCall(req).execute().use { r ->
            val body = r.body?.string()
            if (body == null) null else Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"").find(body)?.groupValues?.get(1)
        }
    } catch (e: Exception) { null }

    private fun tapCenter(n: AccessibilityNodeInfo): Boolean {
        val r = Rect(); n.getBoundsInScreen(r)
        return tapAt(r.centerX().toFloat(), r.centerY().toFloat())
    }

    fun playOnYoutube(q: String): String {
        // Tarika 1 (sabse pakka): video ID nikalo aur seedha watch link kholo -> YouTube app mein khud play hota hai
        firstVideoId(q)?.let { id ->
            val ytp = "com.google.android.youtube"
            val wi = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (packageManager.getLaunchIntentForPackage(ytp) != null) wi.setPackage(ytp)
            try {
                startActivity(wi); waitOpen(ytp, 4000)
                return "done: playing '$q' (video $id)"
            } catch (_: Exception) {}
        }
        // Tarika 2 (backup): search page kholkar UI se tap
        val uri = android.net.Uri.parse("https://www.youtube.com/results?search_query=" + android.net.Uri.encode(q))
        val yt = "com.google.android.youtube"
        val i = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (packageManager.getLaunchIntentForPackage(yt) != null) i.setPackage(yt)
        try { startActivity(i) } catch (e: Exception) { return "failed: YouTube nahi khula" }
        waitOpen(yt, 4000)
        val end = SystemClock.uptimeMillis() + 8000
        var tries = 0
        while (SystemClock.uptimeMillis() < end && tries < 3) {
            val v = findFirstVideo(rootInActiveWindow)
            if (v != null) {
                tries++
                tapCenter(v)                       // YouTube kai baar ACTION_CLICK ignore karta hai, isliye pehle asli touch
                if (waitPlayer(2500)) return "done: playing first result for $q"
                clickNode(v)
                if (waitPlayer(2500)) return "done: playing first result for $q"
            }
            nap(150)
        }
        val dm = resources.displayMetrics
        tapAt(dm.widthPixels / 2f, dm.heightPixels * 0.32f)
        if (waitPlayer(3000)) return "done: playing (fallback tap) for $q"
        return "done?: result par tap kiya par player confirm nahi hua. Screen: " + readScreen(400)
    }

    /** YouTube ka watch/player screen khula? (Minimize / Pause / Fullscreen button dikhe) */
    private fun waitPlayer(ms: Long): Boolean {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) {
            if (findNodes { n ->
                    val d = (n.contentDescription?.toString() ?: "").lowercase()
                    d.contains("minimize") || d.contains("pause video") || d.contains("fullscreen") || d.contains("full screen")
                }.isNotEmpty()) return true
            nap(150)
        }
        return false
    }

    // ---------- WhatsApp (seedha link se chat kholo, UI search ki zaroorat nahi) ----------
    private fun normNumber(raw: String): String {
        var d = raw.filter { it.isDigit() }
        val cc = getSharedPreferences("divya", 0).getString("cc", "91") ?: "91"
        if (d.startsWith("00")) d = d.drop(2)
        else if (d.length == 11 && d.startsWith("0")) d = cc + d.drop(1)
        else if (d.length == 10) d = cc + d
        return d
    }
    private fun lookupNumber(name: String): String? {
        if (name.none { it.isLetter() } && name.count { it.isDigit() } >= 10) return normNumber(name)
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val colName = android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        val colNum = android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
        val c = contentResolver.query(uri, arrayOf(colName, colNum), "$colName LIKE ?", arrayOf("%$name%"), null) ?: return null
        return c.use {
            var first: String? = null
            while (it.moveToNext()) {
                val n = it.getString(0) ?: ""; val num = it.getString(1) ?: continue
                if (n.equals(name, true)) return normNumber(num)
                if (first == null) first = num
            }
            first?.let { f -> normNumber(f) }
        }
    }
    fun whatsappMessage(person: String, message: String): String {
        val num = lookupNumber(person) ?: return "failed: '$person' naam ka contact nahi mila (ya Contacts permission OFF hai). User se contact ka exact naam poochho."
        val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull {
            try { packageManager.getPackageInfo(it, 0); true } catch (_: Exception) { false } } ?: return "failed: WhatsApp installed nahi hai"
        val uri = android.net.Uri.parse("https://wa.me/$num?text=" + android.net.Uri.encode(message))
        try { startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { return "failed: WhatsApp nahi khula: ${e.message}" }
        waitOpen(pkg, 4000)
        nap(600)
        if (!tapDesc("Send", 5000)) return "failed: Send button nahi mila. Screen: " + readScreen(500)
        nap(300)
        return "done: WhatsApp par $person ko message bhej diya"
    }

    // ---------- Phone calls ----------
    fun callPerson(person: String): String {
        val num = lookupNumber(person) ?: return "failed: '$person' naam ka contact nahi mila (ya Contacts permission OFF hai). User se exact naam ya number poochho."
        val canCall = checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val i = Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, android.net.Uri.parse("tel:+$num")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { startActivity(i) } catch (e: Exception) { return "failed: call nahi lagi: ${e.message}" }
        if (canCall) return "done: $person ko call laga di"
        nap(800)
        return if (tapDesc("Call", 2500) || tapDesc("Dial", 800)) "done: $person ko call laga di" else "dialer khula hai par call button nahi daba. Permissions mein Phone allow karo."
    }
    fun answerCall(): String {
        if (Build.VERSION.SDK_INT >= 26 && checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                (getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager).acceptRingingCall()
                return "done: call utha li"
            } catch (_: Exception) {}
        }
        if (tapDesc("Answer", 1500) || tapText("Answer", 800) || tapDesc("Accept", 800)) return "done: call utha li"
        return "failed: call utha nahi payi (Phone permission allow karo ya koi call ring nahi ho rahi)"
    }
    fun endCall(): String {
        if (Build.VERSION.SDK_INT >= 28 && checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                if ((getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager).endCall()) return "done: call kaat di"
            } catch (_: Exception) {}
        }
        if (tapDesc("End call", 1200) || tapDesc("Decline", 800) || tapDesc("Reject", 800) || tapDesc("Hang up", 800)) return "done: call kaat di"
        return "failed: call kaat nahi payi"
    }
    @Suppress("DEPRECATION")
    fun callSpeaker(on: Boolean): String {
        return try {
            (getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager).isSpeakerphoneOn = on
            if (on) "done: speaker ON. Ab saamne wale ko sunane ke liye zor se bolo." else "done: speaker OFF"
        } catch (e: Exception) { "failed: ${e.message}" }
    }

    // ---------- helpers ----------
    private fun allNodes(n: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (n == null) return
        out.add(n)
        for (i in 0 until n.childCount) allNodes(n.getChild(i), out)
    }
    private fun findNodes(pred: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
        val all = mutableListOf<AccessibilityNodeInfo>(); allNodes(rootInActiveWindow, all)
        return all.filter { it.isVisibleToUser && pred(it) }
    }
    private fun clickNode(n: AccessibilityNodeInfo): Boolean {
        var c: AccessibilityNodeInfo? = n
        while (c != null && !c.isClickable) c = c.parent
        if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val r = Rect(); n.getBoundsInScreen(r)
        return tapAt(r.centerX().toFloat(), r.centerY().toFloat())
    }
    /** Icon jisme text nahi hota (jaise Instagram ka message icon) - contentDescription se dhundo. */
    fun tapDesc(d: String, waitMs: Long = 1500): Boolean {
        val end = SystemClock.uptimeMillis() + waitMs
        while (true) {
            findNodes { (it.contentDescription?.toString() ?: "").contains(d, true) }.firstOrNull()?.let { return clickNode(it) }
            if (SystemClock.uptimeMillis() >= end) return false
            nap(60)
        }
    }
    /** Search result ka naam tap karo (type kiya hua text box skip karke). */
    fun tapResult(name: String, waitMs: Long = 3000): Boolean {
        val end = SystemClock.uptimeMillis() + waitMs
        while (true) {
            findNodes { !it.isEditable && ((it.text?.toString() ?: "") + " " + (it.contentDescription?.toString() ?: "")).contains(name, true) }
                .firstOrNull()?.let { return clickNode(it) }
            if (SystemClock.uptimeMillis() >= end) return false
            nap(80)
        }
    }

    /** Instagram DM: seedha inbox deep-link -> search -> naam -> message -> Send (verify ke saath). UI badle to adjust karna pad sakta hai. */
    fun instagramDm(person: String, message: String): String {
        val ig = "com.instagram.android"
        val l = packageManager.getLaunchIntentForPackage(ig) ?: return "failed: Instagram installed nahi hai"
        // 1) inbox seedha kholo (icon dhundne se zyada pakka); na chale to app kholke icon dabao
        var inbox = false
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("instagram://direct_inbox")).setPackage(ig).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitOpen(ig, 4500); nap(1200); inbox = readScreen(600).contains("Search", true) || readScreen(600).contains("Message", true)
        } catch (_: Exception) {}
        if (!inbox) {
            startActivity(l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); waitOpen(ig, 4500); nap(800)
            if (!(tapDesc("Messenger", 1500) || tapDesc("Message", 600) || tapDesc("Direct", 600) || tapText("Messages", 600)))
                return "failed: message icon nahi mila (Instagram login hai?). Screen: " + readScreen(700)
            nap(900)
        }
        // 2) search
        if (!(tapText("Search", 1800) || tapDesc("Search", 600))) return "failed: DM search bar nahi mila. Screen: " + readScreen(700)
        nap(300)
        if (!typeText(person)) return "failed: naam type nahi hua. Screen: " + readScreen(500)
        nap(1100)
        // 3) naam chuno (poora naam, na mile to pehla shabd)
        if (!tapResult(person, 3000) && !tapResult(person.trim().split(" ").first(), 1500)) return "failed: '$person' search mein nahi mila. Screen: " + readScreen(700)
        nap(1100)
        // 4) message likho
        if (!typeText(message)) return "failed: message box nahi mila. Screen: " + readScreen(700)
        nap(400)
        // 5) Send (Instagram ka Send icon text likhne ke baad aata hai) - 3 koshish
        var sent = false
        for (k in 0 until 3) { if (tapDesc("Send", 1200) || tapText("Send", 600)) { sent = true; break }; nap(500) }
        if (!sent) return "failed: Send button nahi mila. Screen: " + readScreen(500)
        nap(900)
        val after = readScreen(900)
        val stillInBox = currentInput()?.let { textOf(it).contains(message.trim().take(15), true) } == true
        return if (stillInBox) "failed: message box mein reh gaya, bheja nahi gaya. Screen: " + after.take(400)
        else "done: Instagram par $person ko message bhej diya"
    }

    private val shotExec = java.util.concurrent.Executors.newSingleThreadExecutor()
    /** Abhi ki screen ka screenshot (JPEG) - Divya ko "dikhane" ke liye. Android 11+ chahiye. */
    fun screenshotJpeg(maxW: Int = 1080, quality: Int = 80): ByteArray? {
        if (Build.VERSION.SDK_INT < 30) return null
        val latch = java.util.concurrent.CountDownLatch(1)
        var out: ByteArray? = null
        takeScreenshot(android.view.Display.DEFAULT_DISPLAY, shotExec, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                try {
                    val hb = r.hardwareBuffer
                    val bmp = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    hb.close()
                    if (bmp != null) {
                        val w = minOf(maxW, bmp.width); val h = (bmp.height * w / bmp.width.toFloat()).toInt()
                        val bo = java.io.ByteArrayOutputStream()
                        val sc = Bitmap.createScaledBitmap(bmp, w, h, true)
                        sc.compress(Bitmap.CompressFormat.JPEG, quality, bo)
                        out = bo.toByteArray()
                        if (sc !== bmp) sc.recycle(); bmp.recycle()
                    }
                } catch (_: Exception) {}
                latch.countDown()
            }
            override fun onFailure(code: Int) { latch.countDown() }
        })
        latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
        return out
    }

    /** Poora kaam ek hi call mein: open_app, tap, type, submit, scroll_down/up, wait, home/back... */
    fun runSteps(steps: JSONArray): String {
        for (i in 0 until steps.length()) {
            val el = steps.get(i)
            var act = ""; var arg = ""
            if (el is org.json.JSONObject) {
                act = el.optString("action").ifBlank { el.optString("name") }
                arg = el.optString("arg").ifBlank { el.optString("value").ifBlank { el.optString("text") } }
            } else {
                val t = el.toString().trim(); val k = t.indexOf(':')
                if (k > 0) { act = t.substring(0, k).trim(); arg = t.substring(k + 1).trim() } else act = t
            }
            act = act.lowercase().replace(" ", "_")
            val ok = when (act) {
                "open_app" -> { val o = Actions.openApp(this, arg); if (o) waitOpen(Actions.lastPkg); o }
                "tap" -> tapText(arg)
                "type" -> typeText(arg)
                "submit" -> submit()
                "scroll_down" -> scroll("down")
                "scroll_up" -> scroll("up")
                "play_youtube" -> playOnYoutube(arg).startsWith("done")
                "wait" -> { nap((arg.toLongOrNull() ?: 500L).coerceIn(50L, 3000L)); true }
                "home", "back", "recents", "notifications", "quick_settings", "lock", "screenshot" -> global(act)
                else -> false
            }
            if (!ok) return "failed at step ${i + 1} ($act $arg). Screen: " + readScreen(900)
            if (act == "home" || act == "back" || act == "recents" || act == "notifications" || act == "quick_settings") nap(250)
        }
        return "all steps done"
    }
}

object Actions {
    @Volatile var lastPkg: String = ""
    fun openApp(ctx: Context, name: String): Boolean {
        val pm = ctx.packageManager
        val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val m = list.firstOrNull { it.loadLabel(pm).toString().equals(name, true) }
            ?: list.firstOrNull { it.loadLabel(pm).toString().contains(name, true) } ?: return false
        val l = pm.getLaunchIntentForPackage(m.activityInfo.packageName) ?: return false
        l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        lastPkg = m.activityInfo.packageName
        (DivyaService.instance ?: ctx).startActivity(l)
        return true
    }
}
