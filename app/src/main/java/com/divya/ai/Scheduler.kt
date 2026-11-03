package com.divya.ai

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Baad ke time ke kaam: "3 baje ammi ko call karke bolo ...".
 * AlarmManager se time par chalta hai, tasks SharedPreferences mein save rehte hain (reboot ke baad bhi).
 * kind: call_and_say | send_whatsapp | remind
 */
object Scheduler {
    private const val TAG = "DivyaScheduler"
    private const val KEY = "sched"

    private fun load(c: Context) = JSONArray(c.getSharedPreferences("divya", 0).getString(KEY, "[]"))
    private fun save(c: Context, a: JSONArray) = c.getSharedPreferences("divya", 0).edit().putString(KEY, a.toString()).apply()

    private fun pi(c: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(c, id, Intent(c, SchedReceiver::class.java).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun arm(c: Context, id: Int, at: Long) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exact = android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c, id))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c, id))   // thoda late ho sakta hai
    }

    fun add(c: Context, kind: String, person: String, message: String, hour: Int, minute: Int, inMin: Int): String {
        if (kind != "remind" && person.isBlank()) return "failed: kisko? person ka naam batao."
        val cal = Calendar.getInstance()
        if (inMin > 0) cal.add(Calendar.MINUTE, inMin)
        else if (hour in 0..23) {
            cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, minute.coerceIn(0, 59)); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis <= System.currentTimeMillis() + 5000) cal.add(Calendar.DAY_OF_YEAR, 1)
        } else return "failed: time samajh nahi aaya. hour (0-23) + minute ya in_minutes do."
        val id = (System.currentTimeMillis() % 1_000_000_000L).toInt()
        val arr = load(c)
        arr.put(JSONObject().put("id", id).put("kind", kind).put("person", person).put("message", message).put("at", cal.timeInMillis))
        save(c, arr)
        arm(c, id, cal.timeInMillis)
        Log.i(TAG, "scheduled $kind $person at ${cal.time}")
        val f = SimpleDateFormat("EEE h:mm a", Locale.getDefault()).format(cal.time)
        val warn = if (android.os.Build.VERSION.SDK_INT >= 31 && !(c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms())
            " (note: 'Alarms & reminders' permission OFF hai, isliye time thoda late ho sakta hai)" else ""
        return "done: $f par schedule ho gaya$warn. Phone me Accessibility ON aur Divya app battery-optimisation se free rakho."
    }

    fun describe(c: Context): String {
        val a = load(c); if (a.length() == 0) return "koi scheduled task nahi hai"
        val f = SimpleDateFormat("EEE h:mm a", Locale.getDefault())
        return (0 until a.length()).joinToString("; ") { val o = a.getJSONObject(it)
            "${f.format(Date(o.getLong("at")))}: ${o.getString("kind")} ${o.optString("person")} - '${o.optString("message").take(40)}'" }
    }

    fun cancelAll(c: Context): String {
        val a = load(c); val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until a.length()) am.cancel(pi(c, a.getJSONObject(i).getInt("id")))
        save(c, JSONArray()); return "done: ${a.length()} scheduled task cancel kar diye"
    }

    fun remove(c: Context, id: Int) {
        val a = load(c); val n = JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).getInt("id") != id) n.put(a.getJSONObject(i))
        save(c, n)
    }

    fun rearmAll(c: Context) {
        val a = load(c); val now = System.currentTimeMillis()
        for (i in 0 until a.length()) { val o = a.getJSONObject(i); arm(c, o.getInt("id"), maxOf(o.getLong("at"), now + 3000)) }
    }

    fun notify(c: Context, title: String, text: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("sched", "Divya scheduled tasks", NotificationManager.IMPORTANCE_HIGH))
        nm.notify((System.currentTimeMillis() % 100000).toInt(),
            android.app.Notification.Builder(c, "sched").setContentTitle(title).setContentText(text)
                .setStyle(android.app.Notification.BigTextStyle().bigText(text)).setSmallIcon(android.R.drawable.ic_popup_reminder).build())
    }

    /** Time aane par chalta hai (background thread). */
    fun run(c: Context, o: JSONObject) {
        val kind = o.getString("kind"); val person = o.optString("person"); val msg = o.optString("message")
        val svc = DivyaService.instance
        if (kind != "remind" && svc == null) { notify(c, "Divya: kaam nahi hua", "Accessibility OFF tha. '$kind $person: $msg' manually karo."); return }
        when (kind) {
            "send_whatsapp" -> notify(c, "Divya: WhatsApp", svc!!.whatsappMessage(person, msg))
            "remind" -> { notify(c, "Divya yaad dila rahi hai", msg); speak(c, msg, false) }
            "call_and_say" -> {
                val r = svc!!.callPerson(person)
                if (!r.startsWith("done")) { notify(c, "Divya: call nahi lagi", r); return }
                val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                // call connect hone ka intezaar (audio mode IN_CALL), max 45 sec; na mile to 20 sec baad bol do
                val t0 = System.currentTimeMillis(); var connected = false
                while (System.currentTimeMillis() - t0 < 45_000) {
                    if (am.mode == AudioManager.MODE_IN_CALL) { connected = true; break }
                    Thread.sleep(500)
                }
                if (connected) Thread.sleep(2000) else if (System.currentTimeMillis() - t0 < 20_000) Thread.sleep(20_000 - (System.currentTimeMillis() - t0))
                svc.callSpeaker(true)
                speak(c, msg, true); Thread.sleep(1200); speak(c, msg, true)
                notify(c, "Divya ne $person ko bol diya", msg)
            }
        }
    }

    /** TTS se bolo (call mein speaker se). Bolne tak block karta hai. */
    private fun speak(c: Context, text: String, loud: Boolean) {
        val done = java.util.concurrent.CountDownLatch(1)
        var tts: TextToSpeech? = null
        tts = TextToSpeech(c.applicationContext) { st ->
            if (st != TextToSpeech.SUCCESS) { done.countDown(); return@TextToSpeech }
            val t = tts!!
            val hasHindi = t.isLanguageAvailable(Locale("hi", "IN")) >= TextToSpeech.LANG_AVAILABLE
            t.language = if (hasHindi) Locale("hi", "IN") else Locale.getDefault()
            t.setSpeechRate(0.9f)
            t.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(u: String?) {}
                override fun onDone(u: String?) { done.countDown() }
                @Deprecated("x") override fun onError(u: String?) { done.countDown() }
            })
            if (loud) { val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                try { am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0) } catch (_: Exception) {} }
            val b = android.os.Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
            t.speak(text, TextToSpeech.QUEUE_FLUSH, b, "divya_sched")
        }
        done.await(40, java.util.concurrent.TimeUnit.SECONDS)
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
    }
}

class SchedReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", -1); val pr = goAsync()
        Thread {
            try {
                val a = JSONArray(c.getSharedPreferences("divya", 0).getString("sched", "[]"))
                for (k in 0 until a.length()) { val o = a.getJSONObject(k)
                    if (o.getInt("id") == id) { Scheduler.remove(c, id); Scheduler.run(c, o) } }
            } catch (e: Exception) { Log.e("DivyaScheduler", "run failed", e); Scheduler.notify(c, "Divya: error", e.message ?: "unknown") }
            finally { pr.finish() }
        }.start()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { if (i.action == Intent.ACTION_BOOT_COMPLETED) Scheduler.rearmAll(c) }
}
