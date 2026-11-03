package com.divya.ai

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Handler
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

/** "Wake up Divya" / "Divya" sunne ke liye chhota foreground service (phone ki on-device speech recognition). */
class WakeService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var sr: SpeechRecognizer? = null
    private var dead = false
    private val keys = listOf("divya", "दिव्या", "diviya", "divia", "deveya", "दिव्य")

    companion object {
        fun start(c: Context) { try { c.startForegroundService(Intent(c, WakeService::class.java)) } catch (_: Exception) {} }
        fun stop(c: Context) { c.stopService(Intent(c, WakeService::class.java)) }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("wake", "Divya wake word", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "wake").setContentTitle("Divya sun rahi hai")
            .setContentText("\"Wake up Divya\" bolo").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)).build()
        try { startForeground(77, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        dead = false; h.removeCallbacksAndMessages(null); h.post { loop() }
        return START_STICKY
    }

    private fun loop() {
        if (dead) return
        if (WakeBus.liveActive || !micOk(this) || !SpeechRecognizer.isRecognitionAvailable(this)) { h.postDelayed({ loop() }, 1200); return }
        listen()
    }

    private fun matches(t: String): Boolean { val s = t.lowercase(); return keys.any { s.contains(it) } }

    private fun listen() {
        try { sr?.destroy() } catch (_: Exception) {}
        val r = SpeechRecognizer.createSpeechRecognizer(this); sr = r
        r.setRecognitionListener(object : RecognitionListener {
            fun check(b: Bundle?): Boolean {
                val l = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return false
                if (l.any { matches(it) }) { wake(); return true }
                return false
            }
            override fun onPartialResults(b: Bundle?) { check(b) }
            override fun onResults(b: Bundle?) { if (!check(b)) h.postDelayed({ loop() }, 150) }
            override fun onError(e: Int) { h.postDelayed({ loop() }, if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500 else 250) }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        try { r.startListening(i) } catch (_: Exception) { h.postDelayed({ loop() }, 1000) }
    }

    private fun wake() {
        try { sr?.cancel(); sr?.destroy() } catch (_: Exception) {}
        sr = null
        WakeBus.liveActive = true   // mic Gemini ko dene ke liye turant ruk jao
        WakeBus.req = true
        try {
            startActivity(Intent(this, MainActivity::class.java).putExtra("wake", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        } catch (_: Exception) {}
        h.postDelayed({ if (!WakeBus.req) return@postDelayed; WakeBus.liveActive = false; WakeBus.req = false; loop() }, 8000) // agar app khula hi nahi
    }

    override fun onDestroy() {
        dead = true; h.removeCallbacksAndMessages(null)
        try { sr?.destroy() } catch (_: Exception) {}
        super.onDestroy()
    }
}
