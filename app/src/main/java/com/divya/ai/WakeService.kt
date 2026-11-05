package com.divya.ai

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object WakeBus {
    var req by mutableStateOf(false)          // WakeService -> MainActivity: "Divya ko jagao"
    @Volatile var liveActive = false          // Gemini session chal raha hai to wake listener ruka rahe (mic free rahe)
}

/**
 * "Wake up Divya" / "Divya" sunne ke liye chhota foreground service (phone ki on-device speech recognition).
 * v2.10.1: session chalne par mic poori tarah chhod deta hai, purane recognizer ke callbacks ignore hote hain,
 * aur no-match par backoff badha hai taaki mic bar bar on/off na ho.
 */
class WakeService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var sr: SpeechRecognizer? = null
    private var dead = false
    private var gen = 0                       // har naye recognizer ka id; purane ke callbacks ignore
    private val keys = listOf("divya", "दिव्या", "diviya", "divia", "deveya", "दिव्य")
    // Language fallback: offline Hindi pack na ho to agli language, last mein phone ka default
    private val langs: List<String?> = listOf("hi-IN", "en-IN", "en-US", null)
    private var li = 0

    companion object {
        fun start(c: Context) { try { c.startForegroundService(Intent(c, WakeService::class.java)) } catch (_: Exception) {} }
        fun stop(c: Context) { c.stopService(Intent(c, WakeService::class.java)) }
    }

    override fun onBind(i: Intent?): IBinder? = null

    private var lastStatus = ""
    private fun note(text: String): Notification =
        Notification.Builder(this, "wake").setContentTitle("Divya sun rahi hai")
            .setContentText(text).setSmallIcon(android.R.drawable.ic_btn_speak_now).setOnlyAlertOnce(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)).build()
    /** Debug: notification mein dikhao ki phone ne kya suna / kaunsa error aaya (logcat ke bina bhi dikhe). */
    private fun status(t: String) {
        if (t == lastStatus) return
        lastStatus = t
        try { getSystemService(NotificationManager::class.java).notify(77, note(t)) } catch (_: Exception) {}
    }
    private fun errName(e: Int) = when (e) {
        1 -> "network timeout"; 2 -> "network"; 3 -> "audio"; 4 -> "server"; 5 -> "client"
        6 -> "kuch bola nahi (timeout)"; 7 -> "samajh nahi aaya"; 8 -> "recognizer busy (mic kisi aur ke paas)"
        9 -> "mic permission"; 10 -> "too many requests"; 11 -> "server disconnected"
        12 -> "language not supported"; 13 -> "language unavailable"; else -> "error $e"
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (!LicenseGate.isActive(this)) { stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("wake", "Divya wake word", NotificationManager.IMPORTANCE_MIN))
        val n = note("\"Wake up Divya\" bolo")
        try { startForeground(77, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        dead = false; h.removeCallbacksAndMessages(null); h.post { loop() }
        return START_STICKY
    }

    private fun loop() {
        if (dead) return
        // Gemini session chal raha ho to mic bilkul free: recognizer band, bas thodi der mein dobara check
        if (WakeBus.liveActive) { releaseMic(); h.postDelayed({ loop() }, 1500); return }
        if (!micOk(this) || !SpeechRecognizer.isRecognitionAvailable(this)) { h.postDelayed({ loop() }, 3000); return }
        listen()
    }

    private fun matches(t: String): Boolean = SessionControl.isWake(t) || keys.any { t.lowercase().contains(it) } ||
        WakeWords.matches(getSharedPreferences("divya", 0), t)

    /** Recognizer band karo aur purane callbacks ko invalid karo. */
    private fun releaseMic() {
        gen++
        try { sr?.cancel() } catch (_: Exception) {}
        try { sr?.destroy() } catch (_: Exception) {}
        sr = null
    }

    private fun listen() {
        releaseMic()
        val my = gen
        val r = makeRecognizer(this); sr = r
        fun next(delay: Long) { if (!dead && my == gen) h.postDelayed({ loop() }, delay) }
        r.setRecognitionListener(object : RecognitionListener {
            fun check(b: Bundle?): Boolean {
                if (my != gen || WakeBus.liveActive) return false   // session chal raha hai to wake mat karo
                val l = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return false
                Log.d("WakeService", "heard: $l"); status("suna: " + (l.firstOrNull() ?: "").take(40))
                if (l.any { matches(it) }) { wake(); return true }
                return false
            }
            override fun onPartialResults(b: Bundle?) { check(b) }
            override fun onResults(b: Bundle?) { if (my == gen && !check(b)) next(250) }
            override fun onError(e: Int) {
                if (my != gen) return                           // purana recognizer (releaseMic ke baad) - ignore
                status("error $e: " + errName(e) + " [" + (langs[li.coerceIn(0, langs.size - 1)] ?: "default") + "]")
                Log.w("WakeService", "error $e" + if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) " (mic kisi aur app ke paas hai, jaise Maya)" else "")
                // 12 = LANGUAGE_NOT_SUPPORTED, 13 = LANGUAGE_UNAVAILABLE: agli language try karo, turant
                if (e == 12 || e == 13) { if (li < langs.size - 1) li++; next(400); return }
                val d = when (e) {
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 4000L
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> 15000L
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 400L
                    else -> 3000L
                }
                next(d)
            }
            override fun onReadyForSpeech(p: Bundle?) { status("sun rahi hoon... bolo: Wake up Divya") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        val lang = langs[li.coerceIn(0, langs.size - 1)]
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, lang != null)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        if (lang != null) i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        try { r.startListening(i) } catch (_: Exception) { next(2000) }
    }

    private fun wake() {
        releaseMic()                                // wake hone par mic turant chhod do
        WakeBus.liveActive = true
        WakeBus.req = true
        Log.d("WakeService", "WAKE! activity khol rahi hoon")
        val i = Intent(this, MainActivity::class.java).putExtra("wake", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // Android background se activity kholne nahi deta. Accessibility service ko chhoot milti hai (wahi se app/WhatsApp khulte hain), isliye pehle wahi.
        var ok = false
        try { DivyaService.instance?.let { it.startActivity(i); ok = true } } catch (e: Exception) { Log.w("WakeService", "a11y launch fail: ${e.message}") }
        if (!ok) try { startActivity(i) } catch (e: Exception) { Log.w("WakeService", "service launch fail: ${e.message}") }
        // Agar 2.5 sec mein bhi app aage nahi aayi (WakeBus.req abhi true) to notification (full-screen / tap) dikhao
        h.postDelayed({ if (WakeBus.req) showOpenNotification() }, 2500)
        h.postDelayed({ if (!WakeBus.req) return@postDelayed; WakeBus.liveActive = false; WakeBus.req = false; loop() }, 12000) // agar app khula hi nahi
    }

    private fun showOpenNotification() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("wakeopen", "Divya jag gayi", NotificationManager.IMPORTANCE_HIGH))
            val pi = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java).putExtra("wake", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = Notification.Builder(this, "wakeopen").setContentTitle("Divya ne suna 👂")
                .setContentText("Tap karo, Divya shuru ho jayegi").setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pi).setFullScreenIntent(pi, true).setAutoCancel(true).setTimeoutAfter(20000).build()
            nm.notify(79, n)
        } catch (e: Exception) { Log.w("WakeService", "notif fail: ${e.message}") }
    }

    override fun onDestroy() {
        dead = true; h.removeCallbacksAndMessages(null)
        releaseMic()
        super.onDestroy()
    }
}
