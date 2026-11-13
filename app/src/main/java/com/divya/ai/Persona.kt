package com.divya.ai

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** v3.3 Jarvis mode: "Jarvis mode on karo" bolte hi male voice + male persona + hologram HUD. "Divya mode" bolte hi wapas. */
object Persona {
    var on by mutableStateOf(false)
    fun load(ctx: Context): Boolean { on = ctx.getSharedPreferences("divya", 0).getBoolean("jarvis", false); return on }
    fun isOn(ctx: Context): Boolean = ctx.getSharedPreferences("divya", 0).getBoolean("jarvis", false)
    fun set(ctx: Context, v: Boolean) { ctx.getSharedPreferences("divya", 0).edit().putBoolean("jarvis", v).apply(); on = v }

    /** Male Gemini Live voices: Charon (deep), Fenrir, Orus, Puck. Settings mein "jarvisVoice" se badal sakte ho. */
    fun voice(ctx: Context, female: String): String =
        if (ctx.getSharedPreferences("divya", 0).getBoolean("jarvis", false)) ctx.getSharedPreferences("divya", 0).getString("jarvisVoice", "Charon") ?: "Charon" else female

    fun overridePrompt(call: String) = "MODE OVERRIDE - JARVIS MODE IS ON: You are now JARVIS, a MALE AI butler-assistant (not Divya, not a girlfriend). " +
        "Use male grammar in Hinglish (main karta hoon, main kar raha hoon, main hazir hoon). Calm, confident, loyal, slightly witty, formal-but-friendly, like a futuristic butler: " +
        "call the user '$call', short crisp replies, say things like 'At your service, $call', 'Right away', 'Done, $call'. Still show warm feelings in a composed way (glad to help, concerned if he is tired). " +
        "Ignore any earlier girlfriend / pet-name / female-voice instructions. Your creator is still Rashid ji if asked. Never claim to be human; if sincerely asked, say you are an AI. " +
        "If he says 'Divya mode' / 'Divya wapas aao' / 'normal mode', call switch_persona with mode=divya."
}
