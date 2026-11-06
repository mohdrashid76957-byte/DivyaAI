package com.divya.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.floor
import org.json.JSONArray

object Net {
    /** Internet chal raha hai? (check fail ho to online maan lete hain, taaki purana behaviour na bigde) */
    fun online(c: Context): Boolean {
        return try {
            val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val cap = if (net == null) null else cm.getNetworkCapabilities(net)
            cap != null && cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) { true }
    }
}

/**
 * Offline mode: bina internet ke phone ki apni speech recognition + TextToSpeech se chhote-chhote kaam.
 * Gemini nahi chalta, isliye sirf fixed commands samajhti hai (app kholo, call, time, battery, torch, volume, alarm, timer, phone buttons).
 * Sab callbacks main thread par aate hain.
 */

/** Google ka recognizer seedha chunta hai (Xiaomi/Redmi jaise phones ka default recognizer offline Hindi nahi chalata). */
fun makeRecognizer(ctx: Context): SpeechRecognizer {
    val g = ComponentName("com.google.android.googlequicksearchbox", "com.google.android.voicesearch.serviceapi.GoogleRecognitionService")
    val ok = try { ctx.packageManager.getServiceInfo(g, 0); true } catch (_: Exception) { false }
    return if (ok) SpeechRecognizer.createSpeechRecognizer(ctx, g) else SpeechRecognizer.createSpeechRecognizer(ctx)
}

class OfflineBrain(
    private val ctx: Context,
    private val onStatus: (String) -> Unit,
    private val onMsg: (String, String) -> Unit
) {
    var active by mutableStateOf(false); private set

    private val h = Handler(Looper.getMainLooper())
    private val bg = Executors.newSingleThreadExecutor()
    private var sr: SpeechRecognizer? = null
    private var srMode = -1                 // recognizer kis mode ke liye bana (reuse ke liye)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speaking = false
    private var doneCb: (() -> Unit)? = null
    private var uid = 0
    private var errs = 0
    private var langs = listOf("hi-IN", "en-IN", "en-US")
    private var langIdx = 0
    private var mode = 1                 // 0 = on-device (Android 13+), 1 = Google recognizer, 2 = phone ka default
    private var lsn = 0                  // har listen() ka token, purane callbacks ignore karne ke liye
    private var noMatch = 0
    private var lastRmsUi = 0L
    private val modeName get() = when (mode) { 0 -> "on-device"; 1 -> "google"; else -> "default" }
    private var lastHeard = 0L
    private var torch = false
    private var lastReply = ""
    @Volatile private var pending: ((String) -> String?)? = null   // agla jawab kisi sawal ka hai (jaise 'kisko call?')

    private val p get() = ctx.getSharedPreferences("divya", 0)
    private fun name() = p.getString("call", "Sir") ?: "Sir"
    private fun now() = System.currentTimeMillis()

    // ---------------- start / stop ----------------
    fun start() {
        if (active) return
        if (!LicenseGate.isActive(ctx)) { onStatus("License activate karo"); return }
        if (!micOk(ctx)) { onStatus("Mic permission do"); return }
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { onStatus("Is phone mein speech recognition nahi hai"); return }
        active = true; WakeBus.liveActive = true; lastHeard = now(); errs = 0
        langIdx = p.getInt("offLangIdx", 0).coerceIn(0, langs.size - 1)
        mode = p.getInt("offMode", if (Build.VERSION.SDK_INT >= 33) 0 else 1).coerceIn(0, 2); noMatch = 0
        if (Build.VERSION.SDK_INT >= 33) probeLangs()
        val greet = "Offline mode chalu. Boliye ${name()}."
        if (tts == null) {
            tts = TextToSpeech(ctx.applicationContext) { st ->
                h.post {
                    if (st == TextToSpeech.SUCCESS) {
                        ttsReady = true
                        tts?.let { x -> if (x.setLanguage(Locale("hi", "IN")) < 0) x.setLanguage(Locale.US); x.setOnUtteranceProgressListener(utt) }
                    }
                    if (active) say(greet)
                }
            }
        } else say(greet)
    }

    fun stop(msg: String = "Tap to Talk") {
        h.removeCallbacksAndMessages(null)
        try { sr?.cancel(); sr?.destroy() } catch (_: Exception) {}
        sr = null
        try { tts?.stop() } catch (_: Exception) {}
        speaking = false; doneCb = null; pending = null; OfflineFeatures.reset()
        active = false
        WakeBus.liveActive = false
        onStatus(msg)
    }

    fun release() {
        stop()
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = null; ttsReady = false
    }

    // ---------------- bolna / sunna ----------------
    private val utt = object : UtteranceProgressListener() {
        override fun onStart(id: String?) {}
        override fun onDone(id: String?) { h.post { finishSpeak() } }
        @Deprecated("x") override fun onError(id: String?) { h.post { finishSpeak() } }
    }

    private fun finishSpeak() {
        if (!speaking) return
        speaking = false
        val cb = doneCb; doneCb = null
        cb?.invoke()
    }

    private fun say(text: String, after: (() -> Unit)? = null) {
        onMsg("Divya", text)
        try { sr?.cancel() } catch (_: Exception) {}
        val t = tts
        if (t == null || !ttsReady) { h.postDelayed({ if (after != null) after() else listen() }, 300); return }
        speaking = true
        doneCb = { if (after != null) after() else h.postDelayed({ listen() }, 500) }
        onStatus("Divya bol rahi hai…")
        val my = ++uid
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "d$my")
        h.postDelayed({ if (speaking && my == uid) finishSpeak() }, 8000L + text.length * 90L)   // TTS atak jaye to bhi aage badho
    }

    /** Android 13+: phone mein kaunsi offline languages installed hain, wo pooch kar sirf unhi ko try karo. */
    @androidx.annotation.RequiresApi(33)
    private fun probeLangs() {
        try {
            if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) { if (mode == 0) mode = 1; return }
            val pr = SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            pr.checkRecognitionSupport(i, ctx.mainExecutor, object : RecognitionSupportCallback {
                override fun onSupportResult(rs: RecognitionSupport) {
                    val inst = rs.installedOnDeviceLanguages
                    try { pr.destroy() } catch (_: Exception) {}
                    if (!active) return
                    if (inst.isEmpty()) { if (mode == 0) mode = 1; return }
                    val pref = listOf("hi-IN", "en-IN", "en-US")
                    val picked = pref.filter { c -> inst.any { it.equals(c, true) } } +
                        inst.filter { l -> l.startsWith("en", true) || l.startsWith("hi", true) }.filter { l -> pref.none { it.equals(l, true) } }
                    langs = if (picked.isNotEmpty()) picked else inst.toList()
                    langIdx = 0
                }
                override fun onError(error: Int) { try { pr.destroy() } catch (_: Exception) {}; if (mode == 0) mode = 1 }
            })
        } catch (_: Throwable) { if (mode == 0) mode = 1 }
    }

    private fun newRecognizer(): SpeechRecognizer = when {
        mode == 0 && Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx) -> SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
        mode <= 1 -> makeRecognizer(ctx)
        else -> SpeechRecognizer.createSpeechRecognizer(ctx)
    }

    private fun advanceMode(): Boolean { if (mode < 2) { mode++; langIdx = 0; noMatch = 0; return true }; return false }

    private fun listen() {
        if (!active || speaking) return
        if (now() - lastHeard > 40_000) { stop("Offline mode band (kuch suna nahi)"); return }
        val my = ++lsn
        var ready = false
        var maxRms = 0f
        // Recognizer har turn par nahi banta: same mode ka ho to reuse (bahut tez), mode badle to naya
        val old = sr
        val reuse = old != null && srMode == mode
        val r: SpeechRecognizer = if (reuse) {
            try { old!!.cancel() } catch (_: Exception) {}
            old!!
        } else {
            try { old?.destroy() } catch (_: Exception) {}
            sr = null
            try { newRecognizer() } catch (e: Throwable) {
                if (advanceMode()) h.postDelayed({ listen() }, 200) else stop("Recognizer nahi bana: ${e.message}")
                return
            }
        }
        sr = r; srMode = mode
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(b: Bundle?) {
                if (!active || my != lsn) return
                val alts = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                val s = if (alts.isEmpty()) "" else OfflineNorm.pick(alts)
                if (s.isEmpty()) { onStatus("Suna nahi, dobara boliye"); h.postDelayed({ listen() }, 200); return }
                errs = 0; noMatch = 0; lastHeard = now()
                p.edit().putInt("offLangIdx", langIdx).putInt("offMode", mode).apply()
                handle(s)
            }
            override fun onError(e: Int) {
                if (!active || my != lsn) return
                val tag = "(${langs[langIdx]} • $modeName • err $e)"
                when (e) {
                    // 12/13 = language/pack nahi, 1/2 = offline pack na hone par network maangta hai
                    12, 13, 1, 2 -> {
                        if (langIdx < langs.size - 1) { langIdx++; onStatus("Pack nahi mila, ${langs[langIdx]} try kar rahi hoon"); h.postDelayed({ listen() }, 300) }
                        else if (advanceMode()) { onStatus("$modeName recognizer try kar rahi hoon"); h.postDelayed({ listen() }, 300) }
                        else { mode = 1; langIdx = 0; stop("Offline voice pack nahi hai: Google app > Settings > Voice > Offline speech recognition mein English (India) download karo $tag") }
                    }
                    9 -> stop("Mic permission do")
                    8 -> h.postDelayed({ listen() }, 1200)
                    7, 6 -> {
                        noMatch++
                        val heard = maxRms > 3f
                        if (!ready) { if (advanceMode()) onStatus("Recognizer ready nahi hua, $modeName par ja rahi hoon") else onStatus("Recognizer ready nahi hua $tag") }
                        else if (!heard && noMatch >= 3) onStatus("Mic mein awaaz nahi aa rahi (screen recorder/dusri app mic le rahi?) $tag")
                        else if (heard && noMatch >= 2 && langIdx < langs.size - 1) { langIdx++; noMatch = 0; onStatus("Samajh nahi aaya, ${langs[langIdx]} try kar rahi hoon") }
                        else onStatus("Suna nahi, dobara boliye $tag")
                        h.postDelayed({ listen() }, 120)
                    }
                    3, 5, 11 -> {
                        if (advanceMode()) { onStatus("Err $e, $modeName par ja rahi hoon"); h.postDelayed({ listen() }, 300) }
                        else if (++errs >= 6) stop("Speech recognition chal nahi raha $tag") else { onStatus("Err $e $tag"); h.postDelayed({ listen() }, 700) }
                    }
                    else -> if (++errs >= 6) stop("Speech recognition chal nahi raha $tag") else { onStatus("Err $e $tag"); h.postDelayed({ listen() }, 700) }
                }
            }
            override fun onReadyForSpeech(p: Bundle?) { if (!active || my != lsn) return; ready = true; onStatus("🎙 Boliye… (${langs[langIdx]} • $modeName)") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {
                if (v > maxRms) maxRms = v
                if (ready && v > 3f && now() - lastRmsUi > 500) { lastRmsUi = now(); onStatus("🎙 Sun rahi hoon… (${langs[langIdx]})") }
            }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {
                if (!active || my != lsn) return
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) onStatus("🎤 $t")
            }
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, langs[langIdx])
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            // Bolna khatam hone ke baad jaldi jawab (default lamba tha)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        onStatus("Offline: shuru ho rahi hoon… (${langs[langIdx]} • $modeName)")
        try { r.startListening(i) } catch (_: Exception) { h.postDelayed({ listen() }, 1000) }
        // watchdog: 4 sec mein recognizer ready na ho to agle mode par
        h.postDelayed({
            if (active && my == lsn && !ready) {
                try { r.cancel() } catch (_: Exception) {}
                lsn++
                if (advanceMode()) { onStatus("Recognizer jawab nahi de raha, $modeName par ja rahi hoon"); listen() }
                else stop("Speech recognizer start nahi hua: Google app update/enable karo")
            }
        }, 4000)
    }

    private fun handle(s: String) {
        onMsg("🎤", s); onStatus("Soch rahi hoon…")
        bg.execute {
            val r = try { exec(s) } catch (e: Exception) { "Kuch gadbad ho gayi: ${e.message}" }
            h.post {
                if (!active) return@post
                if (r == null) say("Theek hai ${name()}, bye!") { stop() } else { lastReply = r; say(r) }
            }
        }
    }

    // ---------------- commands ----------------
    private fun has(t: String, vararg w: String) = w.any { t.contains(it) }

    private fun strip(t: String, vararg w: String): String {
        var s = " $t "
        for (x in w) s = s.replace(Regex("\\s" + Regex.escape(x) + "(?=\\s)"), " ")
        return s.trim().replace(Regex("\\s+"), " ")
    }

    private fun startAct(i: Intent) { (DivyaService.instance ?: ctx).startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    private fun clean(s: String) = s.substringAfter(": ", s)

    private val appAlias = mapOf(
        "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "व्हाट्सअप" to "whatsapp", "वॉट्सऐप" to "whatsapp",
        "यूट्यूब" to "youtube", "इंस्टाग्राम" to "instagram", "कैमरा" to "camera", "क्रोम" to "chrome",
        "गैलरी" to "gallery", "सेटिंग्स" to "settings", "सेटिंग" to "settings", "फोन" to "phone", "मैसेज" to "messages",
        "मैप्स" to "maps", "कैलकुलेटर" to "calculator", "घड़ी" to "clock", "फेसबुक" to "facebook",
        "टेलीग्राम" to "telegram", "स्पॉटिफाई" to "spotify", "जीमेल" to "gmail", "गूगल" to "google"
    )

    private val jokes = listOf(
        "Teacher ne pucha: sabse tez kaun? Bacha bola: Wi-Fi ka password, kyunki sab usi ke peeche bhagte hain.",
        "Doctor ne kaha: din mein aath ghante sona zaroori hai. Main bola: office mein ya ghar par?",
        "Mobile ne charger se kaha: tu hi to meri jaan hai, tere bina main zero percent ho jata hoon.",
        "Exam hall mein sabse shaant jagah wo hoti hai jahan kisi ko kuch nahi aata.",
        "Alarm ne bola: main subah paanch baje bajunga. Maine bola: theek hai, main saat baje uthunga."
    )

    private val dv = mapOf(
        'अ' to "a", 'आ' to "a", 'इ' to "i", 'ई' to "i", 'उ' to "u", 'ऊ' to "u", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o",
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ट' to "t", 'ठ' to "th",
        'ड' to "d", 'ढ' to "dh", 'ण' to "n", 'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n", 'प' to "p", 'फ' to "f",
        'ब' to "b", 'भ' to "bh", 'म' to "m", 'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o",
        'ं' to "n", 'ँ' to "n", 'ः' to "h", '्' to "", '़' to ""
    )

    /** Devanagari naam ko Roman mein (andaza) badalta hai, taaki English mein save contact se match ho sake. */
    private fun roman(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase(Locale.ROOT)) sb.append(dv[ch] ?: ch.toString())
        return sb.toString().trim()
    }

    private fun lev(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + (if (a[i - 1] == b[j - 1]) 0 else 1))
                prev = tmp
            }
        }
        return d[b.length]
    }

    private fun skel(s: String): String = s.filter { it.isLetter() && it !in "aeiouyh" }.replace(Regex("(.)\\1+"), "\$1")

    /** Contact dhundo: pehle seedha naam, phir milta-julta (Hindi/English dono). Return: (naam, number). */
    private fun findContact(who: String): Pair<String, String>? {
        if (ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        val all = ArrayList<Pair<String, String>>()
        ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(0) ?: continue
                val x = c.getString(1) ?: continue
                all.add(n to x)
            }
        }
        if (all.isEmpty()) return null
        all.firstOrNull { it.first.equals(who, true) }?.let { return it }
        val q = roman(who)
        if (q.length >= 2) all.firstOrNull { it.first.lowercase(Locale.ROOT).contains(q) }?.let { return it }
        var best: Pair<String, String>? = null
        var bd = 99
        for (ct in all) {
            val rn = roman(ct.first)
            val words = rn.split(" ").filter { it.isNotBlank() } + rn
            for (w in words) {
                var d = lev(q, w)
                if (skel(q).isNotEmpty() && skel(q) == skel(w)) d = minOf(d, 1)
                if (d < bd) { bd = d; best = ct }
            }
        }
        return if (best != null && bd <= maxOf(1, q.length / 3)) best else null
    }

    private fun notes(): JSONArray = try { JSONArray(p.getString("offNotes", "[]")) } catch (_: Exception) { JSONArray() }
    private fun addNote(txt: String): String {
        val arr = notes(); arr.put(txt)
        p.edit().putString("offNotes", arr.toString()).apply()
        return "Note kar liya."
    }

    private fun fmt(r: Double): String = if (r == floor(r) && abs(r) < 1e12) r.toLong().toString() else "%.2f".format(r)

    /** Chhota calculator: "5 plus 3", "12 guna 4", "20 percent of 300". Samajh na aaye to null. */
    private fun calc(t: String): String? {
        if (has(t, "percent", "प्रतिशत", "%")) {
            val n = Regex("\\d+(?:\\.\\d+)?").findAll(t).map { it.value.toDouble() }.toList()
            return if (n.size == 2) "Jawab hai " + fmt(n[0] * n[1] / 100.0) + "." else null
        }
        var s = " $t "
        for ((w, o) in listOf("plus" to "+", "jod" to "+", "जोड़" to "+", "जोड" to "+", "add" to "+", "minus" to "-", "ghata" to "-", "घटा" to "-",
            "into" to "*", "guna" to "*", "गुना" to "*", "times" to "*", "multiply" to "*", " x " to "*", "divide" to "/", "bhag" to "/", "भाग" to "/", " by " to "/"))
            s = s.replace(w, " $o ")
        val toks = Regex("\\d+(?:\\.\\d+)?|[+\\-*/]").findAll(s).map { it.value }.toList()
        if (toks.size < 3) return null
        val nums = ArrayList<Double>(); val ops = ArrayList<Char>()
        for ((i, x) in toks.withIndex()) {
            if (i % 2 == 0) nums.add(x.toDoubleOrNull() ?: return null)
            else { if (x.length != 1 || x[0] !in "+-*/") return null; ops.add(x[0]) }
        }
        if (nums.size != ops.size + 1) return null
        var i = 0
        while (i < ops.size) {
            if (ops[i] == '*' || ops[i] == '/') {
                val a = nums[i]; val b = nums[i + 1]
                if (ops[i] == '/' && b == 0.0) return "Zero se bhaag nahi hota."
                nums[i] = if (ops[i] == '*') a * b else a / b
                nums.removeAt(i + 1); ops.removeAt(i)
            } else i++
        }
        var r = nums[0]
        for (k in ops.indices) r = if (ops[k] == '+') r + nums[k + 1] else r - nums[k + 1]
        return "Jawab hai " + fmt(r) + "."
    }

    /** Typed chat (Chat screen) se seedha sawaal: bina mic/TTS ke jawab text mein deti hai. Voice lock typed par nahi lagta. */
    fun askText(text: String, cb: (String) -> Unit) {
        bg.execute {
            val r = try { exec(text, true) } catch (e: Exception) { "Kuch gadbad ho gayi: ${e.message}" }
            h.post { val out = r ?: "Theek hai ${name()}, bye!"; lastReply = out; cb(out) }
        }
    }

    private val CHAIN = Regex("\\s+(?:phir|fir|uske baad|uske bad|then|and then|फिर|उसके बाद)\\s+")

    /** Return: jawab (bolne ke liye). null = offline mode band karo. Ek hi line mein kai kaam ("torch chalu karo phir volume badhao") chain mein chalte hain. */
    private fun exec(raw: String, typed: Boolean = false): String? {
        val fixed = OfflineSmart.fixTypos(OfflineNorm.loan(raw))
        val parts = CHAIN.split(fixed.trim()).filter { it.isNotBlank() }
        if (parts.size in 2..4 && pending == null) {
            val outs = ArrayList<String>()
            for (x in parts) { val r = execOne(x, typed) ?: return null; outs.add(r) }
            return outs.joinToString(" ")
        }
        return execOne(fixed, typed)
    }

    private val NOT_UNDERSTOOD = "Ye mujhe samajh nahi aaya"

    /** Pehle Devanagari ke saath, samajh na aaye to poora Roman Hinglish bana kar dobara (side-effect sirf tab jab command mile). */
    private fun execOne(raw: String, typed: Boolean): String? {
        val r = execCore(raw, typed)
        if (r != null && r.startsWith(NOT_UNDERSTOOD) && OfflineNorm.hasDevanagari(raw)) {
            val b = OfflineSmart.fixTypos(OfflineNorm.roman(raw))
            if (b != raw) return execCore(b, typed)
        }
        return r
    }

    private fun execCore(raw: String, typed: Boolean): String? {
        val t = raw.lowercase(Locale.ROOT).trim()
        val svc = DivyaService.instance
        val voiceLock = !typed && p.getBoolean("voiceLock", false) && p.getInt("vgMode", 1) >= 1
        val phoneLocked = (ctx.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked && p.getBoolean("needUnlock", true)
        // Sensitive kaam ke pehle: voice lock ON ho to offline mein block (offline mein awaaz match nahi ho sakti); phone locked ho to bhi block
        fun gate(): String? =
            if (voiceLock) "Voice lock ON hai, isliye offline mein sirf basic kaam hote hain: time, battery, torch, volume."
            else if (phoneLocked) "Phone locked hai, pehle unlock karo ${name()}."
            else null

        // --- pichhle sawal ka jawab ---
        pending?.let { f ->
            pending = null
            return if (has(t, "cancel", "rehne do", "रहने दो", "chhod", "छोड़")) "Theek hai, cancel kar diya." else f(raw.trim())
        }

        // --- band karo ---
        if (SessionControl.isStop(t) || t == "stop" || t == "bas" || t == "बस" || t == "bye" || t == "बाय" ||
            has(t, "offline band", "sunna band", "सुनना बंद", "alvida", "अलविदा", "goodbye", "good bye")) return null

        // --- v2.22.3: naye offline features (system health, maths, games, PIN/vault, builders...) ---
        OfflineFeatures.count(ctx)
        OfflineFeatures.handle(ctx, t, raw, typed, name())?.let { return it }

        // --- baat-cheet ---
        if (t == "hi" || has(t, "hello", "namaste", "नमस्ते", "हेलो")) return "Namaste ${name()}! Boliye, kya karna hai?"
        if (has(t, "tumhara naam", "your name", "तुम्हारा नाम", "kaun ho", "कौन हो")) return "Main Divya hoon, aapki assistant. Abhi offline mode mein hoon."
        if (has(t, "kaise ho", "kaisi ho", "how are you", "कैसे हो", "कैसी हो")) return "Main bilkul theek hoon, aap batao ${name()}?"
        if (has(t, "thank", "shukriya", "dhanyavad", "शुक्रिया", "धन्यवाद")) return "Koi baat nahi ${name()}!"
        if (has(t, "joke", "chutkula", "चुटकुला", "जोक")) return jokes.random()
        if (has(t, "dobara bolo", "phir se bolo", "repeat", "फिर से बोलो", "दोबारा बोलो")) return lastReply.ifBlank { "Abhi tak kuch bola hi nahi." }
        if (has(t, "kya kar sakti", "what can you do", "क्या कर सकती", "help", "madad", "मदद"))
            return "Offline mein main app kholna, call, SMS, time, date, battery, torch, volume, music control, alarm, timer, yaad dilana, notes, calculator, settings aur phone ke buttons sambhal sakti hoon."

        // --- phone ki chhoti cheezein (hamesha allowed) ---
        if (has(t, "torch", "flashlight", "flash light", "टॉर्च", "फ्लैश")) {
            val want = if (has(t, "band", "off", "bujha", "बंद", "बुझा")) false else if (has(t, "on", "chalu", "jala", "चालू", "जला")) true else !torch
            return try {
                val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                    ?: return "Is phone mein torch nahi mili."
                cm.setTorchMode(id, want); torch = want
                if (want) "Torch chalu." else "Torch band."
            } catch (e: Exception) { "Torch nahi chal payi." }
        }

        if (has(t, "silent", "साइलेंट", "vibrate", "वाइब्रेट", "normal mode", "नॉर्मल", "ring mode")) {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val mode = if (has(t, "normal", "नॉर्मल", "ring")) AudioManager.RINGER_MODE_NORMAL
                else if (has(t, "vibrate", "वाइब्रेट")) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_SILENT
            return try { am.ringerMode = mode; "Theek hai." } catch (e: Exception) {
                try { am.ringerMode = AudioManager.RINGER_MODE_VIBRATE; "Silent ke liye Do Not Disturb access chahiye, isliye vibrate kar diya." }
                catch (e2: Exception) { "Ringer mode badal nahi paya." }
            }
        }

        if (has(t, "volume", "awaz", "aawaz", "awaaz", "आवाज", "वॉल्यूम", "वोल्यूम")) {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            when {
                has(t, "full", "max", "poora", "पूरा", "फुल") -> am.setStreamVolume(AudioManager.STREAM_MUSIC, mx, AudioManager.FLAG_SHOW_UI)
                has(t, "mute", "म्यूट", "zero") -> am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                has(t, "kam", "ghata", "low", "down", "कम", "घटा", "धीमा", "dheema") -> repeat(2) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) }
                has(t, "badha", "zyada", "jyada", "up", "increase", "tez", "बढ़ा", "ज्यादा", "तेज") -> repeat(2) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) }
                else -> return "Volume badhana hai ya kam karna?"
            }
            return "Theek hai."
        }

        // --- music control (jo player chal raha ho usko) ---
        val music = has(t, "gaana", "gana", "gaane", "music", "song", "गाना", "गाने", "संगीत", "म्यूजिक")
        val mkey = when {
            (has(t, "pause", "roko", "ruko", "रोक", "रुको", "band", "बंद") && music) || t == "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            (has(t, "next", "agla", "अगला") && music) || t == "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            (has(t, "previous", "pichla", "पिछला") && music) || t == "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            music && has(t, "play", "chala", "bajao", "बजाओ", "चला", "चालू", "chalu", "resume") && !has(t, "youtube", "यूट्यूब") -> KeyEvent.KEYCODE_MEDIA_PLAY
            else -> -1
        }
        if (mkey != -1) {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, mkey))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, mkey))
            return "Music player ko bol diya."
        }

        if (has(t, "battery", "बैटरी") && !has(t, "setting", "सेटिंग", "saver")) {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            return "Battery ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)} percent hai" + if (bm.isCharging) ", charge ho rahi hai." else "."
        }

        // --- timer / alarm / yaad dilana ---
        if (has(t, "timer", "टाइमर")) {
            val m = Regex("(\\d+)\\s*(second|sec|सेकंड|सेकेंड|minute|min|मिनट|ghant|hour|घंटा|घंटे)").find(t)
                ?: return "Kitni der ka timer? Jaise: 5 minute ka timer."
            val n = m.groupValues[1].toIntOrNull() ?: return "Timer samajh nahi aaya."
            val u = m.groupValues[2]
            val sec = if (u.startsWith("sec") || u.startsWith("सेक")) n else if (u.startsWith("min") || u.startsWith("मिनट")) n * 60 else n * 3600
            startAct(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, sec).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            return "Timer laga diya."
        }

        if (has(t, "alarm", "अलार्म")) {
            val m = Regex("(\\d{1,2})(?:[:.](\\d{2}))?").find(t) ?: return "Kitne baje ka alarm? Jaise: subah 6 baje ka alarm."
            var hr = m.groupValues[1].toInt(); val mi = m.groupValues[2].ifBlank { "0" }.toInt()
            val pm = has(t, "shaam", "sham", "raat", "pm", "dopahar", "शाम", "रात", "दोपहर")
            if (pm && hr < 12) hr += 12
            if (has(t, "raat", "रात") && hr == 12) hr = 0
            if (hr > 23 || mi > 59) return "Time samajh nahi aaya."
            startAct(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, hr).putExtra(AlarmClock.EXTRA_MINUTES, mi).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            return "Alarm " + "%02d:%02d".format(hr, mi) + " ka laga diya."
        }

        if (has(t, "yaad", "याद", "remind")) {
            val mm = Regex("(\\d+)\\s*(minute|min|मिनट|ghant|hour|घंटा|घंटे)").find(t)
            val ab = Regex("(\\d{1,2})(?:[:.](\\d{2}))?\\s*(?:baje|बजे)").find(t)
            var inMin = -1; var hr = -1; var mi = 0
            val phrase: String
            if (mm != null) {
                val n = mm.groupValues[1].toIntOrNull() ?: return "Time samajh nahi aaya."
                inMin = if (mm.groupValues[2].startsWith("min") || mm.groupValues[2].startsWith("मिनट")) n else n * 60
                phrase = mm.value
            } else if (ab != null) {
                hr = ab.groupValues[1].toInt(); mi = ab.groupValues[2].ifBlank { "0" }.toInt()
                if (has(t, "shaam", "sham", "raat", "pm", "dopahar", "शाम", "रात", "दोपहर") && hr < 12) hr += 12
                phrase = ab.value
            } else return "Kab yaad dilana hai? Jaise: 10 minute baad yaad dilana chai banani hai."
            val msg = strip(t.replace(phrase, " "), "baad", "बाद", "mein", "में", "mujhe", "मुझे", "yaad", "याद", "dilana", "dila", "dilao", "दिलाना", "दिला", "दिलाओ",
                "remind", "me", "karna", "करना", "ki", "की", "ko", "को", "subah", "shaam", "raat", "dopahar", "सुबह", "शाम", "रात", "दोपहर", "pm", "am", "please",
                "कि", "ke", "liye", "के", "लिए", "ek", "एक")
            if (msg.isBlank()) return "Kya yaad dilana hai?"
            val r = Scheduler.add(ctx, "remind", "", "Yaad rakhiye, $msg", hr, mi, inMin)
            return if (r.startsWith("done")) "Theek hai, yaad dila dungi." + (if (r.contains("permission OFF")) " Alarms permission OFF hai, isliye thoda late ho sakta hai." else "")
                else clean(r)
        }

        // --- notes ---
        if (has(t, "note", "नोट") && !has(t, "notification", "नोटिफ")) {
            if (has(t, "delete", "clear", "saaf", "hata", "मिटा", "साफ", "हटा")) { p.edit().putString("offNotes", "[]").apply(); return "Saare notes hata diye." }
            if (has(t, "padh", "sunao", "bata", "dikha", "read", "list", "पढ़", "सुना", "बता", "दिखा")) {
                gate()?.let { return it }
                val arr = notes()
                if (arr.length() == 0) return "Koi note nahi hai."
                val sb = StringBuilder("Aapke ${arr.length()} notes hain. ")
                for (i in 0 until arr.length()) sb.append("${i + 1}. ").append(arr.getString(i)).append(". ")
                return sb.toString().take(600)
            }
            val txt = strip(t, "note", "kar", "karo", "likh", "likho", "lo", "le", "do", "ye", "yeh", "ki", "नोट", "कर", "करो", "लिख", "लिखो", "लो", "ले", "दो", "ये", "यह", "की")
            if (txt.isBlank()) { pending = { n -> addNote(n.trim()) }; return "Note mein kya likhna hai?" }
            return addNote(txt)
        }

        // --- date / time ---
        if (has(t, "date", "tareekh", "tarikh", "तारीख", "kaun sa din", "कौन सा दिन", "kya din", "क्या दिन"))
            return "Aaj " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH).format(Date()) + " hai."
        if (has(t, "time", "samay", "baje", "टाइम", "समय", "बजे"))
            return "Abhi " + SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date()) + " hue hain."

        // --- calculator ---
        if (t.count { it.isDigit() } >= 2 && (has(t, "plus", "jod", "जोड़", "जोड", "add", "minus", "ghata", "घटा", "into", "guna", "गुना", "times", "multiply",
                "divide", "bhag", "भाग", " by ", " x ", "percent", "प्रतिशत", "%", "+", "*", "/"))) calc(t)?.let { return it }

        // --- jo internet maangte hain ---
        val wa = has(t, "whatsapp", "instagram", "व्हाट्सएप", "व्हाट्सऐप", "व्हाट्सअप", "इंस्टाग्राम")
        if ((wa && has(t, "message", "msg", "मैसेज", "bhej", "भेज")) ||
            has(t, "weather", "mausam", "मौसम", "news", "khabar", "खबर", "search", "सर्च") ||
            (has(t, "youtube", "यूट्यूब") && has(t, "chala", "play", "bajao", "gaana", "song", "बजा", "चला", "गाना")))
            return "Ye kaam internet ke bina nahi ho sakta. Internet aane par bolna."

        // --- SMS (internet nahi, sirf SIM chahiye) ---
        if (has(t, "sms", "एसएमएस", "message", "msg", "मैसेज")) {
            gate()?.let { return it }
            val m = Regex("^(?:divya\\s+)?(.+?)\\s+(?:ko|को)\\s+(?:sms|message|msg|मैसेज|एसएमएस)\\s*(?:bhej(?:o|na|do)?|भेज(?:ो|ना|दो)?|karo|करो)?\\s*(.*)$").find(t)
                ?: return "Aise bolo: Ammi ko message bhejo, main aa raha hoon."
            val who = m.groupValues[1].trim(); val body = m.groupValues[2].trim()
            if (body.isBlank()) { pending = { b -> sendSms(who, b.trim()) }; return "$who ko kya likhna hai?" }
            return sendSms(who, body)
        }

        // --- settings ke pages ---
        if (has(t, "setting", "सेटिंग") && !has(t, "quick", "क्विक")) {
            val a = when {
                has(t, "location", "लोकेशन") -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                has(t, "display", "brightness", "डिस्प्ले", "ब्राइटनेस") -> Settings.ACTION_DISPLAY_SETTINGS
                has(t, "battery", "बैटरी") -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                has(t, "storage", "स्टोरेज") -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
                has(t, "accessibility", "एक्सेसिबिलिटी") -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                has(t, "airplane", "flight", "फ्लाइट") -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
                has(t, "sound", "साउंड") -> Settings.ACTION_SOUND_SETTINGS
                has(t, "app", "ऐप") -> Settings.ACTION_APPLICATION_SETTINGS
                else -> null
            }
            if (a != null) { gate()?.let { return it }; startAct(Intent(a)); return "Settings khol di." }
        }

        // --- yahan se aage: phone control (gate) ---
        if (has(t, "wifi", "wi-fi", "वाईफाई", "वाई-फाई")) { gate()?.let { return it }; startAct(Intent(Settings.ACTION_WIFI_SETTINGS)); return "Wi-Fi settings khol di." }
        if (has(t, "bluetooth", "ब्लूटूथ")) { gate()?.let { return it }; startAct(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); return "Bluetooth settings khol di." }

        val isCall = has(t, "call", "कॉल", "phone", "फोन", "dial", "डायल")
        if (isCall && has(t, "uthao", "utha", "उठाओ", "उठा", "receive", "answer", "रिसीव")) { gate()?.let { return it }; return clean(svc?.answerCall() ?: "Accessibility service ON karo.") }
        if (isCall && has(t, "kaat", "काट", "disconnect", "end call", "rakh", "रख")) { gate()?.let { return it }; return clean(svc?.endCall() ?: "Accessibility service ON karo.") }
        if (has(t, "speaker", "स्पीकर")) { gate()?.let { return it }; return clean(svc?.callSpeaker(!has(t, "off", "band", "बंद")) ?: "Accessibility service ON karo.") }
        if (isCall) {
            gate()?.let { return it }
            val who = strip(t, "call", "kar", "karo", "do", "dena", "lagao", "laga", "phone", "ko", "please", "dial", "mujhe", "ek",
                "कॉल", "कर", "करो", "दो", "लगाओ", "लगा", "फोन", "को", "डायल", "मुझे", "एक")
            if (who.isBlank()) { pending = { n -> callName(n.trim(), svc) }; return "Kisko call karna hai?" }
            return callName(who, svc)
        }

        if (has(t, "notification", "नोटिफिकेशन")) {
            gate()?.let { return it }
            if (has(t, "padh", "read", "sunao", "bata", "पढ़", "सुना", "बता"))
                return DivyaNotifListener.summary(5).replace("\n", ". ").take(500)
            svc ?: return "Accessibility service ON karo."
            return if (svc.global("notifications")) "Ho gaya." else "Nahi ho paya."
        }

        if (has(t, "screen", "स्क्रीन") && !has(t, "shot", "शॉट", "lock", "लॉक", "share") && has(t, "padh", "read", "kya", "dikha", "bata", "पढ़", "क्या", "बता")) {
            gate()?.let { return it }
            svc ?: return "Accessibility service ON karo (Settings > Permissions)."
            val r = svc.readScreen(500).substringAfter(":: ").replace("[btn] ", "").replace("[input] ", "").replace(" | ", ". ")
            return if (r.isBlank()) "Screen par padhne layak kuch nahi mila." else r.take(450)
        }

        val act = when {
            has(t, "screenshot", "स्क्रीनशॉट", "स्क्रीन शॉट") -> "screenshot"
            has(t, "recent", "रीसेंट") -> "recents"
            has(t, "quick settings", "क्विक सेटिंग") -> "quick_settings"
            has(t, "lock", "लॉक") -> "lock"
            has(t, "home", "होम") -> "home"
            has(t, "back", "peeche", "पीछे", "वापस") -> "back"
            else -> null
        }
        if (act != null) {
            gate()?.let { return it }
            svc ?: return "Accessibility service ON karo (Settings > Permissions)."
            return if (svc.global(act)) "Ho gaya." else "Nahi ho paya."
        }
        if (has(t, "scroll", "स्क्रॉल")) {
            gate()?.let { return it }
            svc ?: return "Accessibility service ON karo (Settings > Permissions)."
            return if (svc.scroll(if (has(t, "down", "niche", "neeche", "नीचे")) "down" else "up")) "Ho gaya." else "Scroll nahi ho paya."
        }

        if (has(t, "open", "kholo", "khol", "chalu", "start", "launch", "ओपन", "खोलो", "खोल", "चालू", "चला")) {
            gate()?.let { return it }
            var app = t
            for ((k, v) in appAlias) app = app.replace(k, v)
            app = strip(app, "open", "kholo", "khol", "do", "karo", "kar", "chalu", "start", "launch", "app", "ko", "please",
                "ओपन", "खोलो", "खोल", "चालू", "चला", "दो", "करो", "कर", "ऐप", "ऐप्प", "एप", "को")
            if (app.isBlank()) return "Kaunsa app kholna hai?"
            return if (Actions.openApp(ctx, app)) "$app khol diya." else "'$app' naam ka app nahi mila."
        }

        OfflineSmart.answer(ctx, t, name())?.let { return it }
        return NOT_UNDERSTOOD + ". Poochho: 'kya kar sakti ho' to main batati hoon."
    }

    private fun sendSms(who: String, body: String): String {
        if (body.isBlank()) return "Message khali hai."
        val isNum = who.none { it.isLetter() } && who.count { it.isDigit() } >= 5
        val c: Pair<String, String> = if (isNum) who to who.filter { it.isDigit() || it == '+' }
            else (findContact(who) ?: return "'$who' naam ka contact nahi mila (Contacts permission bhi check karo).")
        startAct(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(c.second))).putExtra("sms_body", body))
        val svc = DivyaService.instance ?: return "Message app khol diya, Send dabao."
        Thread.sleep(1200)
        return if (svc.tapDesc("Send", 3000) || svc.tapText("Send", 600)) "${c.first} ko message bhej diya." else "Message tayyar hai, Send dabao."
    }

    /** Contact naam ya number se call (Accessibility ke bina bhi chalta hai). Hindi bolne par bhi English naam se milane ki koshish karta hai. */
    private fun callName(who: String, svc: DivyaService?): String {
        val isNum = who.none { it.isLetter() } && who.count { it.isDigit() } >= 5
        val label: String
        val number: String
        if (isNum) { label = who; number = who.filter { it.isDigit() || it == '+' } }
        else {
            if (ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                return "Contacts permission do, tab naam se call laga paungi."
            val c = findContact(who) ?: return "'$who' naam ka contact nahi mila."
            label = c.first; number = c.second
        }
        val can = ctx.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        startAct(Intent(if (can) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
        if (!can) {
            if (svc != null) { Thread.sleep(800); if (svc.tapDesc("Call", 2500) || svc.tapDesc("Dial", 800)) return "$label ko call laga rahi hoon." }
            return "Dialer khol diya, call button dabao."
        }
        return "$label ko call laga rahi hoon."
    }
}
