package com.divya.ai

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object Net {
    /** Internet chal raha hai? (check fail ho to online maan lete hain, taaki purana behaviour na bigde) */
    fun online(c: Context): Boolean = try {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork ?: return false)
        cap != null && cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (_: Exception) { true }
}

/**
 * Offline mode: bina internet ke phone ki apni speech recognition + TextToSpeech se chhote-chhote kaam.
 * Gemini nahi chalta, isliye sirf fixed commands samajhti hai (app kholo, call, time, battery, torch, volume, alarm, timer, phone buttons).
 * Sab callbacks main thread par aate hain.
 */
class OfflineBrain(
    private val ctx: Context,
    private val onStatus: (String) -> Unit,
    private val onMsg: (String, String) -> Unit
) {
    var active by mutableStateOf(false); private set

    private val h = Handler(Looper.getMainLooper())
    private val bg = Executors.newSingleThreadExecutor()
    private var sr: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speaking = false
    private var doneCb: (() -> Unit)? = null
    private var uid = 0
    private var errs = 0
    private var lastHeard = 0L
    private var torch = false

    private val p get() = ctx.getSharedPreferences("divya", 0)
    private fun name() = p.getString("call", "Sir") ?: "Sir"
    private fun now() = System.currentTimeMillis()

    // ---------------- start / stop ----------------
    fun start() {
        if (active) return
        if (!micOk(ctx)) { onStatus("Mic permission do"); return }
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { onStatus("Is phone mein speech recognition nahi hai"); return }
        active = true; WakeBus.liveActive = true; lastHeard = now(); errs = 0
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
        speaking = false; doneCb = null
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
        doneCb = { if (after != null) after() else listen() }
        onStatus("Divya bol rahi hai…")
        val my = ++uid
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "d$my")
        h.postDelayed({ if (speaking && my == uid) finishSpeak() }, 8000L + text.length * 90L)   // TTS atak jaye to bhi aage badho
    }

    private fun listen() {
        if (!active || speaking) return
        if (now() - lastHeard > 40_000) { stop("Offline mode band (kuch suna nahi)"); return }
        try { sr?.destroy() } catch (_: Exception) {}
        val r = SpeechRecognizer.createSpeechRecognizer(ctx); sr = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(b: Bundle?) {
                if (!active) return
                val s = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                if (s.isEmpty()) { h.postDelayed({ listen() }, 200); return }
                errs = 0; lastHeard = now(); handle(s)
            }
            override fun onError(e: Int) {
                if (!active) return
                when (e) {
                    12, 13 -> stop("Offline voice pack nahi hai: Google app > Settings > Voice > Offline speech recognition mein Hindi/English download karo")
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> stop("Mic permission do")
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> h.postDelayed({ listen() }, 1200)
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> h.postDelayed({ listen() }, 200)
                    else -> if (++errs >= 6) stop("Speech recognition chal nahi raha (error $e)") else h.postDelayed({ listen() }, 700)
                }
            }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        onStatus("Offline: boliye…")
        try { r.startListening(i) } catch (_: Exception) { h.postDelayed({ listen() }, 1000) }
    }

    private fun handle(s: String) {
        onMsg("🎤", s); onStatus("Soch rahi hoon…")
        bg.execute {
            val r = try { exec(s) } catch (e: Exception) { "Kuch gadbad ho gayi: ${e.message}" }
            h.post {
                if (!active) return@post
                if (r == null) say("Theek hai ${name()}, bye!") { stop() } else say(r)
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

    /** Return: jawab (bolne ke liye). null = offline mode band karo. */
    private fun exec(raw: String): String? {
        val t = raw.lowercase(Locale.ROOT).trim()
        val svc = DivyaService.instance
        val voiceLock = p.getBoolean("voiceLock", false) && p.getInt("vgMode", 1) >= 1
        val phoneLocked = (ctx.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked && p.getBoolean("needUnlock", true)
        // Sensitive kaam ke pehle: voice lock ON ho to offline mein block (offline mein awaaz match nahi ho sakti); phone locked ho to bhi block
        fun gate(): String? =
            if (voiceLock) "Voice lock ON hai, isliye offline mein sirf basic kaam hote hain: time, battery, torch, volume."
            else if (phoneLocked) "Phone locked hai, pehle unlock karo ${name()}."
            else null

        // --- band karo ---
        if (t == "stop" || t == "bas" || t == "बस" || t == "bye" || t == "बाय" ||
            has(t, "offline band", "sunna band", "सुनना बंद", "alvida", "अलविदा", "goodbye", "good bye")) return null

        // --- basic (hamesha allowed) ---
        if (t == "hi" || has(t, "hello", "namaste", "नमस्ते", "हेलो")) return "Namaste ${name()}! Boliye, kya karna hai?"
        if (has(t, "tumhara naam", "your name", "तुम्हारा नाम", "kaun ho", "कौन हो")) return "Main Divya hoon, aapki assistant. Abhi offline mode mein hoon."
        if (has(t, "kya kar sakti", "what can you do", "क्या कर सकती", "help", "madad", "मदद"))
            return "Offline mein main app kholna, call, time, date, battery, torch, volume, alarm, timer aur phone ke buttons sambhal sakti hoon."

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

        if (has(t, "battery", "बैटरी")) {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            return "Battery ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)} percent hai" + if (bm.isCharging) ", charge ho rahi hai." else "."
        }

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

        if (has(t, "date", "tareekh", "tarikh", "तारीख", "kaun sa din", "कौन सा दिन", "kya din", "क्या दिन"))
            return "Aaj " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH).format(Date()) + " hai."
        if (has(t, "time", "samay", "baje", "टाइम", "समय", "बजे"))
            return "Abhi " + SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date()) + " hue hain."

        // --- jo internet maangte hain ---
        if (has(t, "message", "msg", "मैसेज", "whatsapp par", "weather", "mausam", "मौसम", "news", "khabar", "खबर", "search", "सर्च") ||
            (has(t, "youtube", "यूट्यूब") && has(t, "chala", "play", "bajao", "gaana", "song", "बजा", "चला", "गाना")))
            return "Ye kaam internet ke bina nahi ho sakta. Internet aane par bolna."

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
            return if (who.isBlank()) "Kisko call karna hai?" else callName(who, svc)
        }

        if (has(t, "notification", "नोटिफिकेशन")) {
            gate()?.let { return it }
            if (has(t, "padh", "read", "sunao", "bata", "पढ़", "सुना", "बता"))
                return DivyaNotifListener.summary(5).replace("\n", ". ").take(500)
            svc ?: return "Accessibility service ON karo."
            return if (svc.global("notifications")) "Ho gaya." else "Nahi ho paya."
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

        return "Ye mujhe samajh nahi aaya. Offline mein app kholna, call, time, battery, torch, volume, alarm aur timer kar sakti hoon."
    }

    /** Contact naam se call (Accessibility ke bina bhi chalta hai). Contact ka naam English mein save ho to behtar match hota hai. */
    private fun callName(who: String, svc: DivyaService?): String {
        var num: String? = if (who.none { it.isLetter() } && who.count { it.isDigit() } >= 5) who.filter { it.isDigit() || it == '+' } else null
        if (num == null) {
            if (ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                return "Contacts permission do, tab naam se call laga paungi."
            val col = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(col, ContactsContract.CommonDataKinds.Phone.NUMBER), "$col LIKE ?", arrayOf("%$who%"), null)?.use { c ->
                var first: String? = null
                while (c.moveToNext()) {
                    val n = c.getString(0) ?: ""; val x = c.getString(1) ?: continue
                    if (n.equals(who, true)) { num = x; break }
                    if (first == null) first = x
                }
                if (num == null) num = first
            }
        }
        val number = num ?: return "'$who' naam ka contact nahi mila."
        val can = ctx.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        startAct(Intent(if (can) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
        if (!can) {
            if (svc != null) { Thread.sleep(800); if (svc.tapDesc("Call", 2500) || svc.tapDesc("Dial", 800)) return "$who ko call laga rahi hoon." }
            return "Dialer khol diya, call button dabao."
        }
        return "$who ko call laga rahi hoon."
    }
}
