package com.divya.ai

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale

/** Background mein bolne ke liye chhota TTS (notification / call / charger announcements). */
object Announcer {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayList<String>()
    private val main = Handler(Looper.getMainLooper())

    fun speak(c: Context, text: String) {
        main.post {
            if (tts == null) {
                tts = TextToSpeech(c.applicationContext) { st ->
                    main.post {
                        val t = tts ?: return@post
                        if (st == TextToSpeech.SUCCESS) {
                            if (t.setLanguage(Locale("hi", "IN")) < 0 && t.setLanguage(Locale("en", "IN")) < 0) t.setLanguage(Locale.US)
                            ready = true
                            pending.forEach { t.speak(it, TextToSpeech.QUEUE_ADD, null, "ann") }
                            pending.clear()
                        }
                    }
                }
            }
            if (ready) tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "ann") else pending.add(text)
        }
    }
}

/** Settings ka hissa: naam + teen switch. */
@Composable
fun AnnounceSettings(th: UiTheme, prefs: SharedPreferences) {
    Text("Announcements (notification, call, charger)", color = th.accent, fontWeight = FontWeight.Bold)
    var nm by remember { mutableStateOf(prefs.getString("annName", "Rashid sir") ?: "Rashid sir") }
    OutlinedTextField(nm, { nm = it; prefs.edit().putString("annName", it).apply() }, Modifier.fillMaxWidth(),
        label = { Text("Call par kis naam se bulaye (jaise Rashid sir)") },
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = th.text, unfocusedTextColor = th.text))
    AnnSwitch(th, "Notification aaye to padh kar sunao", "annNotif", prefs)
    AnnSwitch(th, "Call aaye to caller ka naam bolo", "annCall", prefs)
    AnnSwitch(th, "Charger lagane / hatane par bolo", "annCharge", prefs)
}

@Composable
private fun AnnSwitch(th: UiTheme, label: String, key: String, prefs: SharedPreferences) {
    var on by remember { mutableStateOf(prefs.getBoolean(key, true)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = th.text, modifier = Modifier.weight(1f))
        Switch(on, { on = it; prefs.edit().putBoolean(key, it).apply() })
    }
}
