package com.divya.ai

/**
 * v2.16.1: "Divya off ho jao" jaise bolne par session band karna, aur "Wake up Divya" ko thoda dhila (fuzzy) pehchanna.
 * Stop sirf tab jab baat Divya ke baare mein ho ("band ho jao", "so jao", "mic band") - "wifi band karo" jaise kaam par nahi.
 */
object SessionControl {
    private val stopRes = listOf(
        // off/band/chup/ruk/stop + ho jao / raho
        Regex("(\\boff|ऑफ|\\bband|\\bbandh|बंद|\\bchup|चुप|\\bruk|रुक|\\bstop|स्टॉप|\\bsleep|\\bbas)\\s*(ho\\s*ja+o|ho\\s*ja+|ho\\s*jaiye|ho\\s*jana|हो\\s*जा(ओ|इए|ना)?|raho|rahiye|रहो|रहिए)"),
        Regex("\\bso\\s*ja+o|\\bso\\s*ja+\\b|सो\\s*जा(ओ|ना)?|go\\s*to\\s*sleep|sleep\\s*mode|stand\\s*by|standby|good\\s*bye|goodbye|alvida|अलविदा"),
        // Divya + off/band/stop/bye/bas/chup (kisi bhi order mein)
        Regex("(divya|दिव्या|diviya|divia)\\s*,?\\s*(off|ऑफ|bye|बाय|bas|बस|chup|चुप|sleep)"),
        Regex("(off|ऑफ|bye|बाय|bas|बस|chup|चुप)\\s*,?\\s*(divya|दिव्या|diviya|divia)"),
        // mic / sunna / session band
        Regex("(mic|microphone|माइक|sunna|सुनना|listening|session|सेशन)\\s*(ko\\s*)?(off|ऑफ|band|बंद|stop|स्टॉप)"),
        Regex("(off|ऑफ|band|बंद|stop|स्टॉप)\\s*(kar\\s*do|karo|कर\\s*दो|करो)?\\s*(mic|microphone|माइक|sunna|सुनना|listening)")
    )

    fun isStop(text: String): Boolean {
        val t = text.lowercase().replace(Regex("[.!?,।]+"), " ").replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return false
        return stopRes.any { it.containsMatchIn(t) }
    }

    // ---------- wake word ----------
    private val wakeExact = listOf("divya", "दिव्या", "दिव्य", "diviya", "divia", "dibya", "dhivya", "diva", "दिव्यां", "दीव्या", "डिव्या", "दिविया", "divyaa", "divyah", "deviya", "devya", "dvya", "दिवया")
    private val wakePhrase = Regex("wake\\s*up|wakeup|वेक\\s*अप|वेकअप|jaag|जाग|uth\\s*ja|उठ\\s*जा")

    private fun lev(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.length]
    }

    fun isWake(text: String): Boolean {
        val t = text.lowercase().replace(Regex("[.!?,।]+"), " ").replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return false
        if (wakeExact.any { t.contains(it) }) return true
        val hasWake = wakePhrase.containsMatchIn(t)
        for (w in t.split(' ')) {
            if (w.length < 4) continue
            val d = minOf(lev(w, "divya"), lev(w, "दिव्या"))
            if ((d <= 1 && (w.length >= 5 || hasWake)) || (hasWake && d <= 2)) return true
        }
        return false
    }
}
