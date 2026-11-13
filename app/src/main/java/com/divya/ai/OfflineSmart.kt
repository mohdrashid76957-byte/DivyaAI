package com.divya.ai

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Offline "smart" layer: internet ke bina bhi Chat/voice ko online jaisa samajhdaar banane ke liye.
 * 1) fixTypos: galat likhe command-words ko theek karta hai (batery -> battery, volum -> volume).
 * 2) answer: extra skills (unit convert, table, din/date hisaab, phone info, random, sawaal-jawab).
 * Koi AI model nahi hai, isliye sirf wahi cheezein jo yahan likhi hain. Baaki ke liye internet chahiye.
 */
object OfflineSmart {
    private val VOCAB = listOf(
        "battery", "volume", "torch", "flashlight", "alarm", "timer", "whatsapp", "instagram", "youtube", "bluetooth",
        "screenshot", "notification", "notifications", "calculator", "message", "settings", "reminder", "remind",
        "weather", "recents", "brightness", "airplane", "storage", "camera", "gallery", "contacts", "calendar",
        "silent", "vibrate", "music", "previous", "multiply", "divide", "percent", "temperature", "charging",
        "system", "health", "wallet", "vault", "neural", "network", "translate", "calculate", "compress", "analytics", "wallpaper",
        "website", "games", "riddle", "update", "version", "status", "monitor", "builder", "editor", "ledger", "quantum"
    )

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

    /** Sirf lambe (5+ akshar) English-jaise shabd theek karta hai, taaki contact naam kharab na hon. */
    fun fixTypos(raw: String): String {
        val words = raw.split(" ")
        val out = words.map { w ->
            val lw = w.lowercase(Locale.ROOT)
            if (lw.length < 5 || !lw.all { it in 'a'..'z' } || lw in VOCAB) w
            else {
                var best: String? = null; var bd = 99
                for (v in VOCAB) {
                    if (abs(v.length - lw.length) > 2) continue
                    val d = lev(lw, v)
                    if (d < bd) { bd = d; best = v }
                }
                val lim = if (lw.length >= 8) 2 else 1
                if (best != null && bd <= lim) best else w
            }
        }
        return out.joinToString(" ")
    }

    private fun has(t: String, vararg w: String) = w.any { t.contains(it) }
    private fun fmt(r: Double): String = if (r == floor(r) && abs(r) < 1e12) r.toLong().toString() else "%.2f".format(r)
    private fun nums(t: String): List<Double> = Regex("-?\\d+(?:\\.\\d+)?").findAll(t).map { it.value.toDouble() }.toList()

    private val days = listOf("Ravivaar (Sunday)", "Somvaar (Monday)", "Mangalvaar (Tuesday)", "Budhvaar (Wednesday)", "Guruvaar (Thursday)", "Shukravaar (Friday)", "Shanivaar (Saturday)")

    private val quotes = listOf(
        "Chhote chhote kadam bhi manzil tak pahunchate hain.",
        "Jo aaj kar sakte ho, usse shuru kar do. Kal ka intezaar mat karo.",
        "Girna fail hona nahi hai, wahi ruk jana fail hona hai.",
        "Mehnat ki awaaz nahi hoti, uska result bolta hai.",
        "Sabr rakho, achha waqt bhi aayega."
    )

    private val facts = listOf(
        "Shahad kabhi kharab nahi hota.", "Octopus ke teen dil hote hain.", "Insaan ki haddi mein steel se zyada taakat hoti hai, vajan ke hisaab se.",
        "Bijli ki raftaar roshni ke barabar nahi, par bahut tez hoti hai.", "Kele mein halki si radioactivity hoti hai, par bilkul safe."
    )

    /** Table: "7 ka table". */
    private fun table(n: Int): String = (1..10).joinToString(". ") { "$n x $it = ${n * it}" }

    /** Unit convert: "10 km kitne miles", "5 kg to pound", "30 celsius to fahrenheit". */
    private fun convert(t: String): String? {
        val n = nums(t).firstOrNull() ?: return null
        return when {
            has(t, "km") && has(t, "mile") && t.indexOf("km") < t.indexOf("mile") -> "${fmt(n)} km = ${fmt(n * 0.621371)} miles."
            has(t, "mile") && has(t, "km") && t.indexOf("mile") < t.indexOf("km") -> "${fmt(n)} miles = ${fmt(n * 1.609344)} km."
            has(t, "kg") && has(t, "pound", "lb") -> "${fmt(n)} kg = ${fmt(n * 2.20462)} pound."
            has(t, "pound", "lb") && has(t, "kg") -> "${fmt(n)} pound = ${fmt(n / 2.20462)} kg."
            has(t, "celsius", "centigrade") && has(t, "fahrenheit") && t.indexOf("fahrenheit") > t.indexOf(if (t.contains("celsius")) "celsius" else "centigrade") -> "${fmt(n)} C = ${fmt(n * 9 / 5 + 32)} F."
            has(t, "fahrenheit") && has(t, "celsius") -> "${fmt(n)} F = ${fmt((n - 32) * 5 / 9)} C."
            has(t, "meter", "metre") && has(t, "feet", "foot") -> "${fmt(n)} meter = ${fmt(n * 3.28084)} feet."
            has(t, "feet", "foot") && has(t, "meter", "metre") -> "${fmt(n)} feet = ${fmt(n / 3.28084)} meter."
            has(t, "inch") && has(t, "cm") -> "${fmt(n)} inch = ${fmt(n * 2.54)} cm."
            has(t, "cm") && has(t, "inch") -> "${fmt(n)} cm = ${fmt(n / 2.54)} inch."
            has(t, "litre", "liter") && has(t, "ml") && t.indexOf("lit") < t.indexOf("ml") -> "${fmt(n)} litre = ${fmt(n * 1000)} ml."
            else -> null
        }
    }

    private fun dayMath(t: String): String? {
        val cal = Calendar.getInstance()
        val fmtD = SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH)
        fun dn(c: Calendar) = days[c.get(Calendar.DAY_OF_WEEK) - 1]
        if (has(t, "kal ", "kal?", "tomorrow", "कल") && has(t, "din", "date", "tareekh", "day", "दिन", "तारीख")) {
            cal.add(Calendar.DAY_OF_YEAR, 1); return "Kal ${dn(cal)} hai, ${fmtD.format(cal.time)}."
        }
        if (has(t, "parso", "परसों", "day after tomorrow") && has(t, "din", "date", "tareekh", "day", "दिन", "तारीख")) {
            cal.add(Calendar.DAY_OF_YEAR, 2); return "Parso ${dn(cal)} hai, ${fmtD.format(cal.time)}."
        }
        if (has(t, "kal ", "yesterday", "कल") && has(t, "tha", "thi", "yesterday", "था")) {
            cal.add(Calendar.DAY_OF_YEAR, -1); return "Kal ${dn(cal)} tha, ${fmtD.format(cal.time)}."
        }
        val m = Regex("(\\d{1,4})\\s*(din|days|day|हफ्ते|hafte|week|weeks|दिन)\\s*(baad|bad|later|बाद)").find(t)
        if (m != null) {
            val n = m.groupValues[1].toInt()
            val mul = if (m.groupValues[2].startsWith("haf") || m.groupValues[2].startsWith("week") || m.groupValues[2].startsWith("हफ")) 7 else 1
            cal.add(Calendar.DAY_OF_YEAR, n * mul)
            return "${n * mul} din baad ${dn(cal)} hoga, ${fmtD.format(cal.time)}."
        }
        if (has(t, "month", "mahina", "महीना", "mahine") && has(t, "kaun sa", "kaunsa", "which", "kya", "कौन")) return "Abhi " + SimpleDateFormat("MMMM", Locale.ENGLISH).format(Date()) + " ka mahina chal raha hai."
        if (has(t, "year", "saal", "sal ", "साल") && has(t, "kaun sa", "kaunsa", "which", "kya", "कौन")) return "Abhi saal " + SimpleDateFormat("yyyy", Locale.ENGLISH).format(Date()) + " hai."
        return null
    }

    private fun phoneInfo(c: Context, t: String): String? {
        if (has(t, "storage", "memory kitni", "space", "स्टोरेज") && has(t, "kitn", "free", "bachi", "bacha", "बची", "कितन")) {
            val st = StatFs(Environment.getDataDirectory().path)
            val free = st.availableBytes / 1_073_741_824.0; val tot = st.totalBytes / 1_073_741_824.0
            return "Storage mein ${"%.1f".format(free)} GB free hai, kul ${"%.1f".format(tot)} GB."
        }
        if (has(t, "ram") && has(t, "kitn", "free", "kitni", "कितन")) {
            val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
            return "RAM: ${"%.1f".format(mi.availMem / 1_073_741_824.0)} GB free, kul ${"%.1f".format(mi.totalMem / 1_073_741_824.0)} GB."
        }
        if (has(t, "phone model", "mobile model", "kaun sa phone", "konsa phone", "मॉडल")) return "Ye phone ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} hai, Android ${android.os.Build.VERSION.RELEASE}."
        if (has(t, "android version", "android kaun")) return "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})."
        if (has(t, "charging", "charge ho", "charger") && has(t, "kya", "ho raha", "hai", "है")) {
            val bm = c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            return if (bm.isCharging) "Haan, charge ho raha hai, battery ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)} percent." else "Nahi, charger nahi laga hai."
        }
        if (has(t, "internet", "wifi", "net ") && has(t, "chal raha", "hai kya", "connected", "on hai", "चल रहा")) {
            return if (Net.online(c)) "Haan, internet chal raha hai." else "Nahi, abhi internet nahi hai, main offline mode mein hoon."
        }
        return null
    }

    /** Main entry: samajh aaya to jawab, warna null. t = lowercase text. */
    fun answer(c: Context, t: String, callName: String): String? {
        // Table
        Regex("(\\d{1,3})\\s*(?:ka|ki|ke|का|की)\\s*(?:table|pahada|पहाड़ा|टेबल)").find(t)?.let { return table(it.groupValues[1].toInt()) }
        if (has(t, "table") && nums(t).size == 1 && !has(t, "kitchen")) return table(nums(t)[0].toInt().coerceIn(1, 999))

        // Square / root / power
        if (has(t, "square root", "root of", "वर्गमूल", "under root")) nums(t).firstOrNull()?.let { if (it >= 0) return "${fmt(it)} ka square root ${fmt(sqrt(it))} hai." }
        if (has(t, "square of", "ka square", "का वर्ग", "squared")) nums(t).firstOrNull()?.let { return "${fmt(it)} ka square ${fmt(it * it)} hai." }
        if (has(t, "cube of", "ka cube", "का घन")) nums(t).firstOrNull()?.let { return "${fmt(it)} ka cube ${fmt(it * it * it)} hai." }
        if (has(t, "power", "raised to", "ki power") && nums(t).size == 2) return fmt(Math.pow(nums(t)[0], nums(t)[1])).let { "Jawab hai $it." }

        // Tip / discount / GST
        if (has(t, "discount", "chhoot", "छूट", "off") && nums(t).size == 2) {
            val (price, pc) = nums(t); val d = price * pc / 100
            return "${fmt(pc)} percent discount par ${fmt(d)} kam hoga, yaani final ${fmt(price - d)}."
        }
        if (has(t, "gst") && nums(t).size == 2) {
            val (price, pc) = nums(t); val g = price * pc / 100
            return "GST ${fmt(g)} hua, total ${fmt(price + g)}."
        }

        // Unit convert
        if (has(t, "kitne", "kitna", "convert", " to ", "mein badlo", "में बदलो", "कितने", "कितना", "in ")) convert(t)?.let { return it }

        // Din / date
        dayMath(t)?.let { return it }

        // Phone info
        phoneInfo(c, t)?.let { return it }

        // Random
        if (has(t, "coin", "sikka", "toss", "सिक्का", "टॉस")) return if (Math.random() < 0.5) "Heads (chit) aaya." else "Tails (pat) aaya."
        if (has(t, "dice", "pasa", "paasa", "ludo", "पासा")) return "Paase par ${(1..6).random()} aaya."
        if (has(t, "random number", "koi number", "number batao") ) {
            val n = nums(t)
            return if (n.size >= 2) "Random number: ${(n[0].toInt()..maxOf(n[0].toInt(), n[1].toInt())).random()}." else "Random number: ${(1..100).random()}."
        }

        // Motivation / fact
        if (has(t, "motivat", "inspire", "प्रेरणा", "suvichar", "सुविचार", "quote")) return quotes.random()
        if (has(t, "fact", "gyaan", "ज्ञान", "interesting", "kuch naya")) return facts.random()

        // Salutations by time
        if (has(t, "good morning", "suprabhat", "सुप्रभात")) return "Good morning $callName! Aapka din shandaar ho."
        if (has(t, "good night", "shubh ratri", "शुभ रात्रि")) return "Good night $callName! Achhi neend aaye."
        if (has(t, "good afternoon", "good evening")) return "Namaste $callName! Kaise madad karun?"

        // Divya ke baare mein
        if (has(t, "kisne banaya", "who made you", "who created", "किसने बनाया", "creator"))
            { CreatorBus.trigger(); return "Mujhe mere Rashid ji ne banaya hai. Main unki hamesha ahsanmand rahungi." }
        if (has(t, "offline kya", "internet nahi", "net nahi", "bina internet")) return "Bina internet ke main phone ke kaam, calculator, date-time, notes, reminder, unit convert aur kuch sawaal-jawab kar sakti hoon. Gehri baat-cheet ke liye internet chahiye."
        if (has(t, "i love you", "love you", "pyaar", "प्यार")) return "Aww, shukriya $callName! Main hamesha aapke saath hoon."
        if (has(t, "tum kya kar", "kya kar rahi", "क्या कर रही")) return "Aapki madad ka intezaar kar rahi hoon $callName."
        if (has(t, "kahan ho", "kaha ho", "कहाँ हो")) return "Main aapke phone ke andar hoon, hamesha paas."
        if (has(t, "kitne baje uthna", "neend", "so jao")) return "Aap araam kijiye $callName. Alarm bolo to laga dungi."
        if (has(t, "joke") ) return null

        // Chhote general knowledge (fixed)
        gk(t)?.let { return it }
        return null
    }

    private val GK = listOf(
        listOf("capital of india", "bharat ki rajdhani", "भारत की राजधानी") to "Bharat ki rajdhani New Delhi hai.",
        listOf("prime minister of india", "bharat ke pradhanmantri", "pradhan mantri") to "Ye jankari badalti rehti hai, sahi jawab ke liye internet chahiye.",
        listOf("national animal of india", "rashtriya pashu") to "Bharat ka rashtriya pashu Bengal tiger (baagh) hai.",
        listOf("national bird of india", "rashtriya pakshi") to "Bharat ka rashtriya pakshi mor (peacock) hai.",
        listOf("national flower of india", "rashtriya phool") to "Bharat ka rashtriya phool kamal (lotus) hai.",
        listOf("largest planet", "sabse bada grah") to "Sabse bada grah Jupiter (Brihaspati) hai.",
        listOf("closest planet to sun", "sabse paas grah", "sabse chhota grah") to "Sooraj ke sabse paas aur sabse chhota grah Mercury (Budh) hai.",
        listOf("tallest mountain", "sabse oonchi", "sabse unchi", "everest") to "Sabse oonchi chotti Mount Everest hai, lagbhag 8849 meter.",
        listOf("longest river in india", "bharat ki sabse lambi nadi") to "Bharat ki sabse lambi nadi Ganga hai.",
        listOf("largest ocean", "sabse bada mahasagar") to "Sabse bada mahasagar Pacific (Prashant) hai.",
        listOf("how many days in a year", "saal mein kitne din", "साल में कितने दिन") to "Ek saal mein 365 din hote hain, leap year mein 366.",
        listOf("how many hours in a day", "din mein kitne ghante") to "Ek din mein 24 ghante hote hain.",
        listOf("how many states in india", "bharat mein kitne rajya", "kitne rajya") to "Bharat mein 28 rajya aur 8 kendra shasit pradesh hain.",
        listOf("speed of light", "roshni ki raftaar") to "Roshni ki raftaar lagbhag 3 lakh kilometer per second hai.",
        listOf("boiling point of water", "paani kitne degree par ubalta") to "Paani 100 degree Celsius par ubalta hai.",
        listOf("freezing point of water", "paani kitne degree par jamta") to "Paani 0 degree Celsius par jamta hai.",
        listOf("father of nation", "rashtrapita") to "Bharat ke rashtrapita Mahatma Gandhi hain.",
        listOf("independence day", "swatantrata diwas") to "Bharat ka Swatantrata Diwas 15 August ko hota hai.",
        listOf("republic day", "ganatantra diwas") to "Bharat ka Ganatantra Diwas 26 January ko hota hai.",
        listOf("pi value", "value of pi", "pai ki value") to "Pi ki value lagbhag 3.14159 hai."
    )

    private fun gk(t: String): String? = GK.firstOrNull { (keys, _) -> keys.any { t.contains(it) } }?.second
}
