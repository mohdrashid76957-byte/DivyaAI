package com.divya.ai

import java.util.Locale

/**
 * v2.22.6: Offline sunne ki galtiyan kam karne wala layer.
 * Problem: hi-IN recognizer English shabd Devanagari mein likhta hai ("सिस्टम हेल्थ"), jabki features ke keywords Roman mein the -> match nahi hota tha.
 *  1) loan(): Devanagari ke English shabd -> English ("सिस्टम हेल्थ" -> "system health"), Devanagari ank -> 0-9.
 *  2) roman(): baaki Devanagari -> Roman Hinglish (schwa rule ke saath: "बंद" -> "band", "खोलो" -> "kholo").
 *  3) pick(): recognizer ke 5 options mein se wo chunta hai jisme sabse zyada command-shabd hon (pehle sirf pehla option liya jata tha).
 */
object OfflineNorm {
    private val RAW = """
सिस्टम,सीस्टम=system
हेल्थ,हैल्थ=health
रैम,रेम=ram
सीपीयू=cpu
रूट=root
स्टेटस=status
मॉनिटर,मोनिटर=monitor
रिसोर्स=resource
चेक=check
ब्राइटनेस=brightness
मैथ्स,मैथ,मैथमेटिक्स=maths
कैलकुलेट,कैल्कुलेट,कैलकुलेशन=calculate
सॉल्व,सोल्व=solve
स्क्वायर,स्क्वेयर=square
फैक्टोरियल,फैक्टोरिअल=factorial
गेम,गेम्स=game
नंबर,नम्बर=number
गेस=guess
क्विज,क्विज़=quiz
डाइस,डाइज़=dice
कॉइन,सिक्का=coin
फ्लिप=flip
टॉस=toss
पिन=pin
सेट=set
लॉक=lock
प्राइवेट=private
मोड=mode
वॉल्ट,वोल्ट,वाल्ट=vault
सीक्रेट=secret
सेव=save
इंट्रूजन,इंट्रूज़न=intrusion
लॉग=log
डेटा,डाटा=data
इरेज,इरेज़=erase
इरेज़र,इरेजर=eraser
कंप्रेस,कम्प्रेस=compress
बैकअप=backup
जिप=zip
अपडेट=update
वर्जन,वर्ज़न=version
डिबग,डीबग=debug
कोड=code
न्यूरल=neural
नेटवर्क=network
ट्रेन=train
वेबसाइट=website
ऐप,ऐप्प,एप,एप्प=app
बिल्डर=builder
फोटो=photo
एडिटर=editor
थ्रीडी=3d
वॉलेट,वैलेट,वालेट=wallet
बैलेंस=balance
लेजर,लैजर,लेज़र=ledger
क्वांटम=quantum
सेफ=safe
साइन=sign
वेरीफाई,वेरिफाई=verify
ट्रांसलेट=translate
एनालिटिक्स=analytics
फेस=face
फिंगरप्रिंट=fingerprint
वॉलपेपर=wallpaper
लाइव=live
इमेज=image
एनालिसिस=analysis
स्कैन=scan
फीचर,फीचर्स=features
ऑफलाइन=offline
नोटिफिकेशन्स=notifications
""".trimIndent()

    private val LOAN: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        for (ln in RAW.lines()) {
            val eq = ln.indexOf('=')
            if (eq <= 0) continue
            val en = ln.substring(eq + 1).trim()
            for (k in ln.substring(0, eq).split(',')) m[k.trim().replace("\u093C", "")] = en
        }
        m
    }

    private val CONS: Map<Char, String> = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n", 'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n", 'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "f", 'ब' to "b", 'भ' to "bh", 'म' to "m", 'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh",
        'ष' to "sh", 'स' to "s", 'ह' to "h", '\u0958' to "k", '\u0959' to "kh", '\u095A' to "g", '\u095B' to "z", '\u095C' to "r",
        '\u095D' to "rh", '\u095E' to "f", '\u095F' to "y"
    )

    private val OTHER: Map<Char, String> = mapOf(
        'अ' to "a", 'आ' to "a", 'इ' to "i", 'ई' to "i", 'उ' to "u", 'ऊ' to "u", 'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o",
        'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u", 'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o", 'ॅ' to "e",
        'ं' to "n", 'ँ' to "n", 'ः' to "h", '्' to "", '।' to "."
    )

    fun hasDevanagari(s: String) = s.any { it in '\u0900'..'\u097F' }

    /** Devanagari mein likhe English shabd -> English; Devanagari ank -> 0-9. Baaki text jaisa tha waisa. */
    fun loan(raw: String): String {
        if (!hasDevanagari(raw)) return raw
        val sb = StringBuilder()
        for (ch in raw) sb.append(if (ch in '\u0966'..'\u096F') ('0' + (ch - '\u0966')) else ch)
        return sb.toString().split(" ").joinToString(" ") { w ->
            val k = w.replace("\u093C", "").trim('।', ',', '.', '?', '!')
            LOAN[k] ?: w
        }
    }

    /** Devanagari -> Roman Hinglish (andaza). Roman akshar jaise ke taise rehte hain. */
    fun roman(s: String): String {
        val t = s.replace("\u093C", "")
        val sb = StringBuilder()
        for (i in t.indices) {
            val ch = t[i]
            val c = CONS[ch]
            if (c != null) {
                sb.append(c)
                val nx = if (i + 1 < t.length) t[i + 1] else ' '
                // schwa: consonant ke baad agla consonant / anusvara ho to 'a' lagao ("बंद" -> band), shabd ke ant mein nahi ("समय" -> samay)
                if (CONS.containsKey(nx) || nx == 'ं' || nx == 'ँ' || nx == 'ः') sb.append('a')
            } else sb.append(OTHER[ch] ?: ch.toString())
        }
        return sb.toString()
    }

    private val KEYS: List<String> = (
        "system health ram cpu root brightness maths calculate solve square factorial game quiz dice coin toss guess pin lock private mode vault secret " +
        "intrusion erase compress backup zip update version debug code neural network train website app builder photo editor wallet balance ledger " +
        "quantum sign verify translate analytics face fingerprint wallpaper live image analysis scan features offline " +
        "battery torch flashlight volume awaz call message sms whatsapp instagram youtube open kholo khol band chalu bajao batao bata sunao " +
        "alarm timer yaad remind note notification screenshot settings wifi bluetooth music gaana time date baje tareekh plus minus guna bhag percent " +
        "joke paheli hisab kitna kitni badha kam silent vibrate weather wallpaper badlo lagao random"
    ).split(" ")

    /** Is sentence mein kitne known command-shabd hain (galat sune hue option ko pehchanne ke liye). */
    fun score(s: String): Int {
        val a = loan(s).lowercase(Locale.ROOT)
        val b = roman(a)
        var n = 0
        for (k in KEYS) if (a.contains(k) || b.contains(k)) n++
        return n
    }

    /** Recognizer ke options mein se sabse "command jaisa" option; barabari par pehla (sabse bharosemand). */
    fun pick(alts: List<String>): String {
        var best = alts.first(); var bs = score(best)
        for (x in alts.drop(1)) { val sc = score(x); if (sc > bs) { best = x; bs = sc } }
        return best
    }
}
