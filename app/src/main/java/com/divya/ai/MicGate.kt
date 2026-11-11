package com.divya.ai

import android.content.Context
import kotlin.math.sqrt

/**
 * Mic gate (Listen mode): Gemini ko audio jaane se PEHLE check karta hai ki bolne wala kaun hai.
 *  - Owner only      : sirf owner ki awaaz Gemini tak jati hai
 *  - Owner + family  : owner + enrolled family
 *  - Everyone        : koi filter nahi (gate use hi nahi hota)
 * Dusre ki awaaz ki jagah Gemini ko khamoshi (zeros) jaati hai, taaki stream chalta rahe aur Gemini ko kuch sunai na de.
 *
 * Tareeka: awaaz shuru hone par ~0.6 sec rok kar (HOLD) awaaz-chhaap match karte hain. Match -> ruka hua audio + aage ka audio
 * bhejte hain (PASS). Match nahi -> poori baat ke liye khamoshi bhejte hain (DROP). Isliye "Owner only" mein jawab ~0.5-0.7 sec der se shuru ho sakta hai.
 * Sirf mic thread se call karo (thread-safe nahi hai).
 */
class MicGate(private val ctx: Context) {
    private enum class St { IDLE, HOLD, PASS, DROP }

    private val CH = 800                    // 50 ms @16k (1600 bytes)
    private val PRE = 6                     // 300 ms pre-roll (baat ka shuruwati hissa na kate)
    private val DECIDE_VOICED = 12          // 12 x 50ms = ~0.6s bolne wala audio -> decision
    private val MAX_HOLD = 24               // 1.2 s se zyada nahi rokenge
    private val END_SIL = 8                 // 8 x 50ms = 400 ms khamoshi = baat khatam
    private val ZEROS = ByteArray(CH * 2)

    private var st = St.IDLE
    private val pre = ArrayDeque<ShortArray>()
    private val held = ArrayList<ShortArray>()
    private var voiced = 0
    private var sil = 0
    private var floor = 120.0
    private var lastNotify = 0L
    private val RECHECK = 16                // PASS ke dauran har 16 bolne wale chunk (~0.8s) par awaaz dobara check
    private val recheck = ArrayList<ShortArray>()

    private fun rms(a: ShortArray): Double { var s = 0.0; for (v in a) s += v.toDouble() * v; return sqrt(s / a.size) }
    private fun bytes(a: ShortArray): ByteArray {
        val b = ByteArray(a.size * 2)
        for (i in a.indices) { b[2 * i] = (a[i].toInt() and 0xFF).toByte(); b[2 * i + 1] = (a[i].toInt() shr 8).toByte() }
        return b
    }

    /** Gate chalna chahiye? (Guardian ON + mode Owner only / Owner+family + owner enrolled) */
    fun active(): Boolean {
        val p = ctx.getSharedPreferences("divya", 0)
        return p.getBoolean("voiceLock", false) && p.getInt("vgMode", 1) >= 1 && VoiceGuardian.owner(p) != null
    }

    /**
     * Ek 50ms ka chunk do. Return: Gemini ko bhejne wale chunks (0, 1 ya kai). Gemini ko jo audio gaya wahi `onSent` se ring buffer mein jata hai.
     */
    fun feed(chunk: ShortArray, onSent: (ByteArray) -> Unit): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        val r = rms(chunk)
        val speech = r > maxOf(220.0, floor * 2.8)
        if (!speech) floor = floor * 0.95 + r * 0.05

        fun send(a: ShortArray) { val b = bytes(a); out.add(b); onSent(b) }
        fun silence() { out.add(ZEROS) }

        when (st) {
            St.IDLE -> {
                if (speech) {
                    held.clear(); held.addAll(pre); pre.clear(); held.add(chunk)
                    voiced = 1; sil = 0; st = St.HOLD
                } else {
                    pre.addLast(chunk); while (pre.size > PRE) pre.removeFirst()
                    silence()
                }
            }
            St.HOLD -> {
                held.add(chunk)
                if (speech) { voiced++; sil = 0 } else sil++
                // 200ms chup = baat khatam, lekin sirf jab itna bola ja chuka ho ki awaaz pakki ho sake
                val ended = sil >= 4 && voiced >= VoicePrint.MIN_VOICED / 3
                if (voiced >= DECIDE_VOICED || held.size >= MAX_HOLD + PRE || ended) decide(out) { send(it) }
            }
            St.PASS -> {
                // Owner ke bolna shuru karne ke baad koi dusra bole (ya beech mein bol pade) to use bhi na jane do
                if (speech) { sil = 0; recheck.add(chunk) } else sil++
                if (recheck.size >= RECHECK && !stillAllowed()) {
                    recheck.clear(); sil = 0; st = St.DROP; silence()
                } else {
                    if (recheck.size >= RECHECK) recheck.clear()
                    send(chunk)
                    if (!speech && sil >= END_SIL) { st = St.IDLE; pre.clear(); recheck.clear() }
                }
            }
            St.DROP -> {
                silence()
                if (speech) sil = 0 else { sil++; if (sil >= END_SIL) { st = St.IDLE; pre.clear() } }
            }
        }
        return out
    }

    private fun stillAllowed(): Boolean {
        val total = recheck.sumOf { it.size }
        val pcm = ShortArray(total); var o = 0
        for (h in recheck) { System.arraycopy(h, 0, pcm, o, h.size); o += h.size }
        val p = ctx.getSharedPreferences("divya", 0)
        val sp = VoiceGuardian.identifyRaw(p, pcm)
        if (sp.role == "unverified") return true
        VoiceGuardian.last = sp
        val mode = p.getInt("vgMode", 1)
        return when (sp.role) {
            "owner" -> true
            "family" -> mode == 2
            else -> {
                // thoda hysteresis: owner ki khansi/shor se ek baar score thoda upar jaye to na kato; saaf alag awaaz par kato
                val bad = sp.limit > 0 && sp.score / sp.limit > 1.15
                if (bad) VoiceGuardian.blockedHeard(ctx, sp)
                !bad
            }
        }
    }

    private fun decide(out: ArrayList<ByteArray>, send: (ShortArray) -> Unit) {
        val total = held.sumOf { it.size }
        val pcm = ShortArray(total); var o = 0
        for (h in held) { System.arraycopy(h, 0, pcm, o, h.size); o += h.size }
        val p = ctx.getSharedPreferences("divya", 0)
        val sp = VoiceGuardian.identifyRaw(p, pcm)
        VoiceGuardian.last = sp
        android.util.Log.i("DivyaGuard", "role=${sp.role} name=${sp.name} score=${"%.2f".format(sp.score)} limit=${"%.2f".format(sp.limit)} conf=${sp.conf}")
        val mode = p.getInt("vgMode", 1)
        // "unverified" = bahut chhoti awaaz (verify nahi ho sakti): owner-only mein pass nahi karte
        val ok = when (sp.role) { "owner" -> true; "family" -> mode == 2; else -> false }
        if (ok) {
            for (h in held) send(h)
            sil = 0; st = St.PASS
        } else {
            for (i in held.indices) out.add(ZEROS)
            // bahut chhoti awaaz (click/khansi) verify nahi hoti: DROP mein mat phanso, taaki aglli asli baat na kate
            sil = 0; st = if (sp.role == "unverified") St.IDLE else St.DROP
            if (sp.role == "unknown") VoiceGuardian.blockedHeard(ctx, sp)
        }
        held.clear()
    }

    fun reset() { st = St.IDLE; pre.clear(); held.clear(); recheck.clear(); voiced = 0; sil = 0 }
}
