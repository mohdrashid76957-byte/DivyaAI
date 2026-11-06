package com.divya.ai

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * v2.22.3 - Offline Brain ke naye features (internet ki zaroorat nahi, koi naya dependency nahi).
 * OfflineBrain.execOne() sabse pehle yahan handle() ko poochta hai; samajh na aaye to null.
 * Sab kuch asli code hai, lekin kuch features chhote/basic hain (README mein saaf likha hai).
 */
object OfflineFeatures {
    @Volatile private var pend: ((String) -> String)? = null

    fun reset() { pend = null }

    private fun prefs(c: Context) = c.getSharedPreferences("divya", 0)
    private fun has(t: String, vararg w: String) = w.any { t.contains(it) }
    private fun now() = System.currentTimeMillis()
    private fun fmt(r: Double): String =
        if (r == floor(r) && abs(r) < 1e12) r.toLong().toString() else "%.4f".format(r).trimEnd('0').trimEnd('.')

    fun count(c: Context) = bump(c, "_total")

    private fun bump(c: Context, k: String) {
        try {
            val o = JSONObject(prefs(c).getString("offStats", "{}") ?: "{}")
            o.put(k, o.optInt(k, 0) + 1)
            prefs(c).edit().putString("offStats", o.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun startAct(c: Context, i: Intent) { try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} }

    // =====================================================================================
    fun handle(c: Context, t: String, raw: String, typed: Boolean, name: String): String? {
        pend?.let { f ->
            pend = null
            return if (has(t, "cancel", "rehne do", "रहने दो", "chhod", "छोड़")) "Theek hai, cancel kar diya." else f(raw.trim())
        }

        if (has(t, "offline features", "offline feature", "features list", "kya kya features", "फीचर"))
            return "Offline mein ye sab chalta hai: system health, root check, brightness, maths solver, games, PIN lock, private mode, vault, " +
                "intrusion log, data eraser, file compress, code debugger, neural network trainer, website / app / photo editor / 3D viewer builder, " +
                "local wallet ledger, quantum-safe signature, Hindi-English translator, update manager, wallpaper badlo aur analytics. Kisi ka naam bolo."

        if (has(t, "analytics", "usage stats", "kitne command", "edge ai")) {
            return try {
                val o = JSONObject(prefs(c).getString("offStats", "{}") ?: "{}")
                val total = o.optInt("_total", 0)
                val top = o.keys().asSequence().filter { it != "_total" }.map { it to o.optInt(it, 0) }.sortedByDescending { it.second }.take(5).toList()
                if (top.isEmpty()) "Ab tak $total command suni. Naye features ka use hua nahi."
                else "Ab tak $total command. Sabse zyada: " + top.joinToString(", ") { it.first + " " + it.second + " baar" } + ". Ye data sirf phone mein hai."
            } catch (e: Exception) { "Analytics padh nahi paayi." }
        }

        // ---- v2.22.4: app/face lock, live wallpaper, image analysis ----
        if (has(t, "app lock", "face lock", "fingerprint lock", "biometric lock", "ऐप लॉक")) {
            bump(c, "applock")
            val off = has(t, "off", "band", "hata", "बंद")
            val km = c.getSystemService(KeyguardManager::class.java)
            if (!off && (km == null || !km.isDeviceSecure)) return "Pehle phone mein screen lock (PIN, fingerprint ya face) set karo, tabhi app lock chalega."
            prefs(c).edit().putBoolean("appLock", !off).apply()
            return if (off) "App lock band." else "App lock ON. Ab app kholne par phone ka PIN, fingerprint ya face poochha jayega."
        }
        // v2.22.8: wallpaper badlo (voice se) - "live"/"neon" bole to purana animated wallpaper preview
        if (has(t, "wallpaper", "वॉलपेपर", "wall paper") && !has(t, "live", "लाइव", "neon", "preview")) { bump(c, "wallpaper"); return Wallpapers.handle(c, t) }
        if (has(t, "live wallpaper", "wallpaper lagao", "neon wallpaper")) {
            bump(c, "wallpaper")
            startAct(c, Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(c, DivyaWallpaper::class.java)))
            return "Divya Neon wallpaper ka preview khol diya, 'Set wallpaper' dabao."
        }
        if (has(t, "image analysis", "photo analy", "image analy", "last scan analy", "faces gino", "objects batao", "photo mein kya hai")) {
            bump(c, "imganalysis"); return MlTools.analyzeLastScan(c)
        }

        // ---- system health / resource monitor ----
        if (has(t, "system health", "health check", "resource monitor", "system monitor", "phone health", "ram usage", "memory usage",
                "cpu", "storage kitna", "storage space", "storage usage") || Regex("\\bram\\b").containsMatchIn(t)) {
            bump(c, "health"); return health(c)
        }
        if (has(t, "root status", "root check", "rooted", "root monitor")) {
            bump(c, "root")
            return if (isRooted()) "Chetavani: is phone mein root ke nishaan mile. Banking apps aur security par asar pad sakta hai."
            else "Root ke koi nishaan nahi mile. Phone safe lag raha hai."
        }

        // ---- brightness ----
        if (has(t, "brightness", "ब्राइटनेस") && !has(t, "setting", "सेटिंग")) { bump(c, "brightness"); return brightness(c, t) }

        // ---- maths solver ----
        val exprOnly = Regex("^[0-9+\\-*/^().x%\\s]+$").matches(t) && t.any { it.isDigit() } && t.count { it in "+-*/^%" } >= 1
        if (exprOnly || has(t, "calculate", "calc ", "solve", "hisab", "हिसाब", "sqrt", "square root", "factorial", "quadratic",
                "squared", "cube of", "power of", "to the power", "evaluate", "maths", "math ")) {
            mathAnswer(t)?.let { bump(c, "maths"); return it }
        }

        // ---- games ----
        if (has(t, "number game", "number guess", "guess the number", "guess game")) {
            bump(c, "game"); val s = (1..100).random(); pend = guess(s, 0)
            return "Maine 1 se 100 ke beech ek number socha hai. Pehla guess bolo."
        }
        if (has(t, "quiz", "क्विज")) { bump(c, "game"); pend = quiz(0, 0); return "Quiz shuru! Sawal 1: " + QUIZ[0].first }
        if (has(t, "riddle", "paheli", "पहेली")) {
            bump(c, "game"); val r = RIDDLES.random(); pend = { a -> if (r.second.split("|").any { a.lowercase(Locale.ROOT).contains(it) }) "Wah, sahi jawab!" else "Galat. Jawab hai " + r.second.substringBefore("|") + "." }
            return r.first
        }
        if (has(t, "dice", "pasa", "पासा")) { bump(c, "game"); return "Dice par " + (1..6).random() + " aaya." }
        if (has(t, "coin flip", "sikka uchal", "toss", "सिक्का")) { bump(c, "game"); return if ((0..1).random() == 0) "Heads aaya." else "Tails aaya." }
        if (Regex("\\bgames?\\b").containsMatchIn(t) || has(t, "khel", "खेल")) {
            bump(c, "game"); return "Offline game zone: 'number game', 'quiz', 'paheli', 'dice' ya 'coin flip' bolo."
        }

        // ---- PIN / security ----
        if (has(t, "pin hatao", "remove pin", "pin off", "pin band", "pin delete")) {
            if (!hasPin(c)) return "PIN set hi nahi hai."
            return needPin(c) { prefs(c).edit().remove("pinHash").remove("pinSalt").apply(); "PIN hata diya." }
        }
        if (has(t, "pin set", "set pin", "pin lagao", "pin badlo", "pin change", "app pin", "pin lock")) {
            bump(c, "pin")
            val lit = Regex("\\d{4,8}").find(t)?.value
            val act: () -> String = {
                if (lit != null) setPin(c, lit) else { pend = { a -> setPin(c, digitsOf(a)) }; "Naya PIN bolo, 4 se 8 number." }
            }
            return if (hasPin(c)) needPin(c, act) else act()
        }
        if (has(t, "private mode", "प्राइवेट मोड")) {
            val off = has(t, "off", "band", "hata", "बंद")
            prefs(c).edit().putBoolean("privMode", !off).apply(); bump(c, "private")
            return if (off) "Private mode band."
            else if (hasPin(c)) "Private mode ON. Ab notes padhne ke liye PIN lagega."
            else "Private mode ON, lekin PIN set nahi hai. Pehle 'pin set 1234' bolo."
        }
        if (prefs(c).getBoolean("privMode", false) && has(t, "note", "नोट") && !has(t, "notif", "नोटिफ") &&
            has(t, "padh", "sunao", "bata", "dikha", "read", "list", "पढ़", "सुना", "बता", "दिखा")) {
            if (!hasPin(c)) return "Private mode ON hai. Notes dekhne ke liye pehle PIN set karo."
            return needPin(c) {
                val arr = try { JSONArray(prefs(c).getString("offNotes", "[]")) } catch (_: Exception) { JSONArray() }
                if (arr.length() == 0) "Koi note nahi hai."
                else (0 until arr.length()).joinToString(". ") { (it + 1).toString() + ". " + arr.getString(it) }.take(600)
            }
        }
        if (has(t, "intrusion", "suspicious", "galat pin", "security log")) {
            bump(c, "intrusion")
            val log = try { JSONArray(prefs(c).getString("intrLog", "[]")) } catch (_: Exception) { JSONArray() }
            if (log.length() == 0) return "Koi galat PIN koshish record nahi hui."
            val last = (maxOf(0, log.length() - 5) until log.length()).joinToString(". ") { log.getString(it) }
            return "Ab tak ${log.length()} suspicious koshish. Aakhri: $last"
        }

        // ---- vault (encrypted, Android Keystore) ----
        if (has(t, "vault", "वॉल्ट")) {
            bump(c, "vault")
            if (has(t, "padh", "sunao", "bata", "dikha", "read", "list", "पढ़", "सुना", "बता", "दिखा"))
                return needPin(c) { vaultRead(c) }
            if (has(t, "clear", "delete", "hata", "saaf", "साफ", "हटा"))
                return needPin(c) { prefs(c).edit().putString("vaultItems", "[]").apply(); "Vault khali kar diya." }
            val stop = setOf("vault", "वॉल्ट", "mein", "me", "में", "save", "karo", "kar", "store", "secret", "likho", "rakh", "do", "dena")
            val body = t.split(" ").filter { it.isNotBlank() && it !in stop }.joinToString(" ")
            if (body.isBlank()) { pend = { a -> vaultAdd(c, a) }; return "Vault mein kya save karna hai?" }
            return vaultAdd(c, body)
        }

        // ---- secure data eraser ----
        if (has(t, "data erase", "secure erase", "sab data mita", "sara data mita", "data eraser", "data delete all")) {
            bump(c, "eraser")
            return needPin(c) {
                pend = { a ->
                    if (has(a.lowercase(Locale.ROOT), "haan", "हाँ", "हां", "yes", "mita do")) eraseAll(c) else "Theek hai, kuch nahi mita."
                }
                "Ye notes, vault, scan history, scans aur projects hamesha ke liye mita dega. Pakka? 'haan mita do' bolo."
            }
        }

        // ---- file compression ----
        if (has(t, "compress", "zip kar", "backup banao", "files zip")) { bump(c, "compress"); return try { compressAll(c) } catch (e: Exception) { "Compress nahi ho paya: " + e.message } }

        // ---- update manager ----
        if (has(t, "update check", "check update", "update manager", "app version", "version batao", "kaun sa version", "offline update")) {
            bump(c, "update"); return updateInfo(c)
        }

        // ---- code debugger ----
        if (has(t, "code debug", "debug code", "debug kar", "code check", "bug dhundo")) {
            bump(c, "debug")
            val body = raw.substringAfter(":", "").trim()
            if (body.isBlank()) { pend = { a -> debugCode(a) }; return "Code paste karo (typed chat mein, poora ek message mein)." }
            return debugCode(body)
        }

        // ---- neural network trainer ----
        if (has(t, "neural network", "neural net", "nn train", "train network")) {
            bump(c, "neuralnet")
            val gate = listOf("nand", "and", "xor", "or").firstOrNull { Regex("\\b$it\\b").containsMatchIn(t) } ?: "xor"
            val ep = (Regex("(\\d{3,5})").find(t)?.value?.toIntOrNull() ?: 8000).coerceIn(500, 20000)
            return trainNN(gate, ep)
        }

        // ---- offline builders (website / app / photo editor / 3D viewer) ----
        if (has(t, "website", "web app", "html app", "app builder", "site banao", "photo editor", "3d viewer", "3d model", "todo app", "calculator app", "notes app", "counter app") &&
            has(t, "banao", "bana do", "bana", "build", "create", "make", "kholo", "chahiye", "dikhao")) {
            bump(c, "builder"); return buildApp(c, t)
        }

        // ---- local wallet ledger ----
        if (has(t, "wallet", "ledger", "वॉलेट")) { bump(c, "wallet"); return wallet(c, t) }

        // ---- quantum-safe (hash-based) signature ----
        if (has(t, "quantum safe", "quantum-safe", "post quantum", "post-quantum", "lamport", "quantum resistant", "quantum-resistant")) {
            bump(c, "pq"); return pq(t, raw)
        }

        // ---- translator (phrasebook) ----
        if (has(t, "translate", "translation", "anuvad", "अनुवाद", "hindi mein bolo", "english mein bolo", "hindi mein batao", "english mein batao")) {
            bump(c, "translate"); return translate(t)
        }
        return null
    }

    /** Online (Gemini Live) se: phone_brain tool. PIN / vault / eraser yahan band hain, taaki secret cloud model ko na jaye. */
    fun online(c: Context, command: String): String {
        val raw = command.trim()
        if (raw.isBlank()) return "command khali hai"
        val t = raw.lowercase(Locale.ROOT)
        if (Regex("\\bpin\\b").containsMatchIn(t) || has(t, "vault", "data erase", "secure erase", "data eraser", "private mode", "intrusion", "वॉल्ट"))
            return "ye security kaam sirf Offline mode mein hota hai, taaki PIN aur vault ka data cloud model ko na jaye. User ko yehi batao."
        val nm = c.getSharedPreferences("divya", 0).getString("call", "Sir") ?: "Sir"
        return handle(c, t, raw, true, nm) ?: "ye command phone features mein nahi mili"
    }

    // =====================================================================================
    // System health
    private fun isRooted(): Boolean {
        val paths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/sd/xbin/su", "/data/local/xbin/su",
            "/data/local/bin/su", "/system/app/Superuser.apk", "/data/adb/magisk")
        return paths.any { File(it).exists() } || (Build.TAGS ?: "").contains("test-keys")
    }

    private fun health(c: Context): String {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
        val gb = 1073741824.0
        val ramTot = mi.totalMem / gb; val ramFree = mi.availMem / gb
        val st = StatFs(Environment.getDataDirectory().path)
        val stTot = st.totalBytes / gb; val stFree = st.availableBytes / gb
        val bi = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val temp = (bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val hl = when (bi?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "achhi"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "zyada garam"
            BatteryManager.BATTERY_HEALTH_DEAD -> "kharab"
            BatteryManager.BATTERY_HEALTH_COLD -> "bahut thandi"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over voltage"
            else -> "pata nahi"
        }
        val warn = ArrayList<String>()
        if (ramFree / ramTot < 0.1) warn.add("RAM bahut kam bachi hai")
        if (stFree / stTot < 0.1) warn.add("storage 10 percent se kam bacha hai")
        if (temp >= 42.0) warn.add("battery garam hai")
        if (isRooted()) warn.add("phone rooted lagta hai")
        return "RAM: %.1f GB mein se %.1f GB khali. Storage: %.0f GB mein se %.0f GB khali. Battery %d percent, %.0f degree, sehat %s. CPU cores %d, Android %s. "
            .format(ramTot, ramFree, stTot, stFree, lvl, temp, hl, Runtime.getRuntime().availableProcessors(), Build.VERSION.RELEASE) +
            (if (warn.isEmpty()) "Sab theek hai." else "Dhyan do: " + warn.joinToString(", ") + ".")
    }

    private fun brightness(c: Context, t: String): String {
        if (!Settings.System.canWrite(c)) {
            startAct(c, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + c.packageName)))
            return "Brightness badalne ke liye 'Modify system settings' allow karo, phir dobara bolo."
        }
        val cr = c.contentResolver
        val cur = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
        val num = Regex("(\\d{1,3})").find(t)?.groupValues?.get(1)?.toIntOrNull()
        val nv = when {
            num != null -> (num.coerceIn(0, 100) * 255 / 100).coerceAtLeast(5)
            has(t, "full", "max", "poori", "पूरी") -> 255
            has(t, "badha", "zyada", "jyada", "increase", "बढ़ा", "तेज") -> (cur + 51).coerceAtMost(255)
            has(t, "kam", "ghata", "decrease", "dim", "कम", "घटा", "low") -> (cur - 51).coerceAtLeast(5)
            else -> return "Brightness kitni karni hai? Jaise: brightness 60 percent."
        }
        return try {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, nv)
            "Brightness " + (nv * 100 / 255) + " percent kar di."
        } catch (e: Exception) { "Brightness badal nahi paayi." }
    }

    // =====================================================================================
    // Maths solver
    private class P(val s: String) {
        var i = 0
        fun parse(): Double { val v = expr(); if (i != s.length) throw IllegalArgumentException("extra"); return v }
        private fun peek(): Char = if (i < s.length) s[i] else '\u0000'
        private fun expr(): Double {
            var v = term()
            while (peek() == '+' || peek() == '-') { val o = s[i++]; val r = term(); v = if (o == '+') v + r else v - r }
            return v
        }
        private fun term(): Double {
            var v = pw()
            while (peek() == '*' || peek() == '/') {
                val o = s[i++]; val r = pw()
                if (o == '/' && r == 0.0) throw ArithmeticException("zero")
                v = if (o == '*') v * r else v / r
            }
            return v
        }
        private fun pw(): Double { val b = un(); if (peek() == '^') { i++; return b.pow(pw()) }; return b }
        private fun un(): Double {
            if (peek() == '-') { i++; return -un() }
            if (peek() == '+') { i++; return un() }
            var v = pr()
            while (peek() == '%') { i++; v /= 100.0 }
            return v
        }
        private fun pr(): Double {
            if (peek() == '(') { i++; val v = expr(); if (peek() != ')') throw IllegalArgumentException("paren"); i++; return v }
            if (peek().isDigit() || peek() == '.') { val st = i; while (peek().isDigit() || peek() == '.') i++; return s.substring(st, i).toDouble() }
            val st = i
            while (peek().isLetter()) i++
            val f = s.substring(st, i)
            if (f == "pi") return PI
            if (f.isEmpty()) throw IllegalArgumentException("token")
            val a = pr()
            return when (f) {
                "sqrt" -> { if (a < 0) throw ArithmeticException("neg"); sqrt(a) }
                "sin" -> sin(Math.toRadians(a))
                "cos" -> cos(Math.toRadians(a))
                "tan" -> tan(Math.toRadians(a))
                "log" -> log10(a)
                "ln" -> ln(a)
                else -> throw IllegalArgumentException("fn")
            }
        }
    }

    private fun mathAnswer(t0: String): String? {
        val nums = Regex("-?\\d+(?:\\.\\d+)?").findAll(t0).map { it.value.toDouble() }.toList()
        if (t0.contains("quadratic")) {
            if (nums.size < 3 || nums[0] == 0.0) return "Quadratic ke liye teen number do: a, b, c. Jaise: quadratic 1 -5 6."
            val a = nums[0]; val b = nums[1]; val cc = nums[2]
            val d = b * b - 4 * a * cc
            return if (d > 0) "Do asli roots: " + fmt((-b + sqrt(d)) / (2 * a)) + " aur " + fmt((-b - sqrt(d)) / (2 * a)) + "."
            else if (d == 0.0) "Ek root: " + fmt(-b / (2 * a)) + "."
            else "Asli roots nahi hain. Complex roots: " + fmt(-b / (2 * a)) + " plus minus " + fmt(sqrt(-d) / (2 * a)) + " i."
        }
        if (t0.contains("factorial")) {
            val n = nums.firstOrNull()?.toInt() ?: return null
            if (n < 0 || n > 20) return "Factorial 0 se 20 tak hi nikaal sakti hoon."
            var r = 1L; for (k in 2..n) r *= k
            return "$n ka factorial " + r + " hai."
        }
        var s = " " + t0 + " "
        s = s.replace(Regex("(\\d+(?:\\.\\d+)?)\\s*(?:percent|%|प्रतिशत)\\s*of\\s*"), "($1/100)*")
        val reps = listOf("square root of" to " sqrt ", "square root" to " sqrt ", "sqrt of" to " sqrt ", "squared" to "^2 ", "cubed" to "^3 ",
            "to the power" to "^", "power of" to "^", "multiplied by" to "*", "divided by" to "/", "divide by" to "/",
            "plus" to "+", "minus" to "-", "times" to "*", "into" to "*", "guna" to "*", "गुना" to "*", "bhag" to "/", "भाग" to "/",
            "jod" to "+", "जोड़" to "+", "ghata" to "-", "घटा" to "-", "calculate" to " ", "calc" to " ", "solve" to " ",
            "hisab" to " ", "हिसाब" to " ", "evaluate" to " ", "maths" to " ", "math" to " ", "kitna hota hai" to " ", "kya hai" to " ",
            "equals" to " ", "what is" to " ")
        for ((a, b) in reps) s = s.replace(a, b)
        s = s.replace(Regex("(?<=\\d)\\s*x\\s*(?=\\d)"), "*")
        val cleaned = s.replace(Regex("\\s+"), "")
        if (cleaned.isEmpty() || !cleaned.any { it.isDigit() }) return null
        if (!Regex("^[0-9a-z+\\-*/^().%]+$").matches(cleaned)) return null
        return try {
            val r = P(cleaned).parse()
            if (r.isNaN() || r.isInfinite()) "Ye result define nahi hota." else "Jawab hai " + fmt(r) + "."
        } catch (e: ArithmeticException) { if (e.message == "zero") "Zero se bhaag nahi hota." else "Negative number ka square root asli nahi hota." }
        catch (e: Exception) { null }
    }

    // =====================================================================================
    // Games
    private fun guess(secret: Int, tries: Int): (String) -> String = { inp ->
        val n = Regex("\\d+").find(inp)?.value?.toIntOrNull()
        if (n == null) { pend = guess(secret, tries); "Number bolo, 1 se 100 ke beech." }
        else if (n == secret) "Wah! $secret sahi hai, " + (tries + 1) + " koshish mein. Dobara ke liye 'number game' bolo."
        else if (tries >= 9) "Koshish khatam. Jawab $secret tha."
        else { pend = guess(secret, tries + 1); if (n < secret) "Thoda bada number bolo." else "Thoda chhota number bolo." }
    }

    private val QUIZ = listOf(
        "Bharat ki rajdhani kya hai?" to "delhi|दिल्ली",
        "Suraj kis disha mein ugta hai?" to "east|poorv|purab|पूर्व|पूरब",
        "Ek hafte mein kitne din hote hain?" to "7|seven|saat|सात",
        "Pani ka chemical formula kya hai?" to "h2o|h 2 o|एच2ओ",
        "Bharat ka rashtriya pashu kaun sa hai?" to "tiger|baagh|bagh|sher|बाघ|शेर"
    )
    private val RIDDLES = listOf(
        "Woh kaun si cheez hai jo hamesha aati hai par kabhi pahunchti nahi?" to "kal|tomorrow|कल",
        "Aisi cheez jiske paas chaabi hai par wo taala nahi khol sakti?" to "piano|keyboard|कीबोर्ड|पियानो",
        "Jitna nikaalo utna bada hota hai, wo kya hai?" to "gadha|gaddha|hole|गड्ढा|गढ्ढा"
    )

    private fun quiz(i: Int, score: Int): (String) -> String = { inp ->
        val ok = QUIZ[i].second.split("|").any { inp.lowercase(Locale.ROOT).contains(it) }
        val sc = score + (if (ok) 1 else 0)
        val fb = if (ok) "Sahi!" else "Galat, jawab " + QUIZ[i].second.substringBefore("|") + "."
        if (i + 1 < QUIZ.size) { pend = quiz(i + 1, sc); fb + " Agla sawal: " + QUIZ[i + 1].first }
        else fb + " Quiz khatam. Score $sc / " + QUIZ.size + "."
    }

    // =====================================================================================
    // PIN, intrusion log, vault, eraser
    private fun sha(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun pinHash(salt: String, pin: String) = hex(sha((salt + ":" + pin).toByteArray()))
    private fun hasPin(c: Context) = prefs(c).getString("pinHash", null) != null

    private val NUMW = mapOf("zero" to "0", "shunya" to "0", "one" to "1", "ek" to "1", "two" to "2", "do" to "2", "three" to "3", "teen" to "3",
        "four" to "4", "char" to "4", "chaar" to "4", "five" to "5", "paanch" to "5", "panch" to "5", "six" to "6", "chhe" to "6", "che" to "6",
        "seven" to "7", "saat" to "7", "eight" to "8", "aath" to "8", "nine" to "9", "nau" to "9")

    private fun digitsOf(s: String): String {
        val w = s.lowercase(Locale.ROOT).split(Regex("\\s+")).joinToString("") { NUMW[it] ?: it }
        return w.mapNotNull { ch -> Character.digit(ch, 10).takeIf { it >= 0 } }.joinToString("")
    }

    private fun logIntrusion(c: Context, what: String) {
        try {
            val a = JSONArray(prefs(c).getString("intrLog", "[]"))
            a.put(SimpleDateFormat("d MMM HH:mm", Locale.ENGLISH).format(Date()) + " " + what)
            val out = JSONArray()
            for (i in maxOf(0, a.length() - 20) until a.length()) out.put(a.getString(i))
            prefs(c).edit().putString("intrLog", out.toString()).apply()
        } catch (_: Exception) {}
    }

    /** null = PIN theek, warna error ka message. */
    private fun verifyPin(c: Context, entered: String): String? {
        val p = prefs(c)
        val until = p.getLong("pinLockUntil", 0L)
        if (now() < until) return "Bahut galat koshish hui. " + ((until - now()) / 60000 + 1) + " minute baad try karo."
        val ok = pinHash(p.getString("pinSalt", "") ?: "", digitsOf(entered)) == p.getString("pinHash", "")
        if (ok) { p.edit().putInt("pinFails", 0).apply(); return null }
        val f = p.getInt("pinFails", 0) + 1
        logIntrusion(c, "galat PIN")
        if (f >= 3) { p.edit().putInt("pinFails", 0).putLong("pinLockUntil", now() + 300000L).apply(); return "3 galat PIN. 5 minute ke liye lock." }
        p.edit().putInt("pinFails", f).apply()
        return "PIN galat hai."
    }

    private fun needPin(c: Context, action: () -> String): String {
        if (!hasPin(c)) return action()
        pend = { inp -> verifyPin(c, inp) ?: action() }
        return "PIN batao."
    }

    private fun setPin(c: Context, pin: String): String {
        if (pin.length !in 4..8) return "PIN 4 se 8 number ka hona chahiye."
        val salt = hex(ByteArray(8).also { SecureRandom().nextBytes(it) })
        prefs(c).edit().putString("pinSalt", salt).putString("pinHash", pinHash(salt, pin)).apply()
        return "PIN set ho gaya. Ye notes (private mode), vault aur data eraser ko surakshit karega."
    }

    private fun vaultKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null)
        (ks.getKey("divya_vault", null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder("divya_vault", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return kg.generateKey()
    }

    private fun enc(s: String): String {
        val ci = Cipher.getInstance("AES/GCM/NoPadding")
        ci.init(Cipher.ENCRYPT_MODE, vaultKey())
        val ct = ci.doFinal(s.toByteArray())
        return Base64.encodeToString(ci.iv + ct, Base64.NO_WRAP)
    }

    private fun dec(s: String): String {
        val b = Base64.decode(s, Base64.NO_WRAP)
        val ci = Cipher.getInstance("AES/GCM/NoPadding")
        ci.init(Cipher.DECRYPT_MODE, vaultKey(), GCMParameterSpec(128, b, 0, 12))
        return String(ci.doFinal(b, 12, b.size - 12))
    }

    private fun vaultAdd(c: Context, text: String): String = try {
        val a = JSONArray(prefs(c).getString("vaultItems", "[]"))
        a.put(enc(text.trim()))
        prefs(c).edit().putString("vaultItems", a.toString()).apply()
        "Vault mein encrypt karke rakh diya."
    } catch (e: Exception) { "Vault mein save nahi ho paya: " + e.message }

    private fun vaultRead(c: Context): String = try {
        val a = JSONArray(prefs(c).getString("vaultItems", "[]"))
        if (a.length() == 0) "Vault khali hai."
        else (0 until a.length()).joinToString(". ") { (it + 1).toString() + ". " + dec(a.getString(it)) }.take(600)
    } catch (e: Exception) { "Vault khul nahi paya: " + e.message }

    private fun shred(f: File): Int {
        var n = 0
        try {
            if (f.isDirectory) { f.listFiles()?.forEach { n += shred(it) }; f.delete(); return n }
            val len = f.length()
            if (len in 1..50_000_000L) {
                RandomAccessFile(f, "rw").use { r ->
                    val buf = ByteArray(8192); val rnd = SecureRandom(); var left = len
                    r.seek(0)
                    while (left > 0) {
                        rnd.nextBytes(buf)
                        val k = minOf(left, buf.size.toLong()).toInt()
                        r.write(buf, 0, k); left -= k
                    }
                    r.fd.sync()
                }
            }
            if (f.delete()) n++
        } catch (_: Exception) {}
        return n
    }

    private fun eraseAll(c: Context): String {
        var n = 0
        n += shred(File(c.filesDir, "scans")); n += shred(File(c.filesDir, "projects"))
        prefs(c).edit().putString("offNotes", "[]").putString("scanHist", "[]").putString("vaultItems", "[]")
            .putString("ledger", "[]").putString("offStats", "{}").putString("intrLog", "[]").apply()
        try { val ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null); ks.deleteEntry("divya_vault") } catch (_: Exception) {}
        return "Data mita diya: $n files overwrite karke delete hui, notes, vault, scan history aur wallet saaf."
    }

    // =====================================================================================
    // Compress / update
    private fun compressAll(c: Context): String {
        val out = File(c.getExternalFilesDir(null) ?: c.filesDir,
            "DivyaBackup_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".zip")
        var n = 0
        ZipOutputStream(out.outputStream().buffered()).use { z ->
            for (d in listOf("scans", "projects")) {
                val base = File(c.filesDir, d)
                if (!base.exists()) continue
                base.walkTopDown().filter { it.isFile }.forEach { f ->
                    z.putNextEntry(ZipEntry(d + "/" + f.relativeTo(base).path.replace('\\', '/')))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry(); n++
                }
            }
        }
        if (n == 0) { out.delete(); return "Compress karne ke liye koi file nahi mili. Scans aur projects khali hain." }
        return "$n files compress ho gayi: " + out.name + ", " + (out.length() / 1024) + " KB. Folder: Android/data/com.divya.ai/files."
    }

    private fun updateInfo(c: Context): String {
        val pm = c.packageManager
        val pi = pm.getPackageInfo(c.packageName, 0)
        val vc = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        val dir = File(c.getExternalFilesDir(null) ?: c.filesDir, "updates").apply { mkdirs() }
        val apks = dir.listFiles { f -> f.name.endsWith(".apk", true) }?.toList().orEmpty()
        var newer: String? = null
        for (f in apks) {
            val ai = try { pm.getPackageArchiveInfo(f.path, 0) } catch (_: Exception) { null } ?: continue
            val avc = if (Build.VERSION.SDK_INT >= 28) ai.longVersionCode else ai.versionCode.toLong()
            if (ai.packageName == c.packageName && avc > vc) newer = f.name + " (version " + ai.versionName + ")"
        }
        return "Divya AI version " + pi.versionName + ", build " + vc + ". " +
            (if (newer != null) "Naya APK mila: $newer. File manager se kholkar install karo."
            else "Naya update nahi mila. Update ke liye APK ko Android/data/com.divya.ai/files/updates folder mein daalo.")
    }

    // =====================================================================================
    // Code debugger (basic static check)
    private fun debugCode(code: String): String {
        val issues = ArrayList<String>()
        val tr = code.trim()
        if ((tr.startsWith("{") && tr.endsWith("}")) || (tr.startsWith("[") && tr.endsWith("]"))) {
            try { if (tr.startsWith("{")) JSONObject(tr) else JSONArray(tr); return "JSON bilkul sahi hai." }
            catch (e: Exception) { issues.add("JSON galat hai: " + (e.message ?: "").take(80)) }
        }
        val isHtml = Regex("<(html|div|body|head|script|p|span|ul|table)\\b", RegexOption.IGNORE_CASE).containsMatchIn(code)
        val isPy = !isHtml && !code.contains("{") && Regex("(?m)^\\s*(def |import |from \\w+ import|class .*:|print\\()").containsMatchIn(code)
        val stack = ArrayList<Pair<Char, Int>>()
        var line = 1; var q = '\u0000'; var qLine = 0; var i = 0
        while (i < code.length) {
            val ch = code[i]
            val nx = if (i + 1 < code.length) code[i + 1] else '\u0000'
            if (ch == '\n') {
                line++
                if (q == '"' || q == '\'') { issues.add("Line $qLine: quote ($q) band nahi hua"); q = '\u0000' }
            } else if (q != '\u0000') {
                if (ch == '\\') i++ else if (ch == q) q = '\u0000'
            } else if (!isHtml && ch == '/' && nx == '/') {
                while (i < code.length && code[i] != '\n') i++
                continue
            } else if (!isHtml && isPy && ch == '#') {
                while (i < code.length && code[i] != '\n') i++
                continue
            } else if (!isHtml && ch == '/' && nx == '*') {
                val e = code.indexOf("*/", i + 2)
                if (e < 0) { issues.add("Line $line: /* comment band nahi hua"); break }
                for (k in i until e) if (code[k] == '\n') line++
                i = e + 2
                continue
            } else if (!isHtml && (ch == '"' || ch == '\'' || ch == '`')) {
                q = ch; qLine = line
            } else if (ch == '(' || ch == '[' || ch == '{') {
                stack.add(ch to line)
            } else if (ch == ')' || ch == ']' || ch == '}') {
                val want = when (ch) { ')' -> '('; ']' -> '['; else -> '{' }
                if (stack.isEmpty()) issues.add("Line $line: extra '$ch', pehle koi khula bracket nahi")
                else {
                    if (stack.last().first != want) issues.add("Line $line: '$ch' mila, lekin '" + stack.last().first + "' (line " + stack.last().second + ") band hona baaki tha")
                    stack.removeAt(stack.size - 1)
                }
            }
            i++
        }
        if (q == '"' || q == '\'' || q == '`') issues.add("Line $qLine: quote ($q) band nahi hua")
        for (s in stack) issues.add("Line " + s.second + ": '" + s.first + "' khula hai, band nahi hua")
        if (isPy) {
            val lines = code.split("\n")
            lines.forEachIndexed { idx, l ->
                val s = l.trim()
                if (Regex("^(def|class|if|elif|for|while|with|except)\\b.*").matches(s) && !s.endsWith(":") && !s.endsWith(",") &&
                    !s.endsWith("(") && !s.endsWith("\\") && !s.contains("#")) issues.add("Line " + (idx + 1) + ": end mein ':' missing lagta hai")
                if (Regex("^(else|try|finally)\\s*$").matches(s)) issues.add("Line " + (idx + 1) + ": '" + s + "' ke baad ':' chahiye")
            }
            val tabs = lines.count { it.startsWith("\t") }; val sps = lines.count { it.startsWith("  ") }
            if (tabs > 0 && sps > 0) issues.add("Indentation mein tab aur space mix hain")
        }
        if (isHtml) {
            for (tag in listOf("div", "span", "p", "ul", "ol", "li", "table", "tr", "td", "a", "button", "form", "script", "style", "body", "html", "head")) {
                val o = Regex("<$tag[\\s>]", RegexOption.IGNORE_CASE).findAll(code).count()
                val cl = Regex("</$tag>", RegexOption.IGNORE_CASE).findAll(code).count()
                if (o != cl) issues.add("HTML: <$tag> $o baar khula, </$tag> $cl baar band")
            }
        }
        return if (issues.isEmpty()) "Basic check mein koi syntax problem nahi mili. Logic bug ke liye ye kaafi nahi hai, online AI se poochho."
        else "" + issues.size + " problem mili: " + issues.take(8).joinToString(". ") + ". Ye basic check hai, kuch galat alarm bhi ho sakte hain."
    }

    // =====================================================================================
    // Neural network trainer (2-4-1, backprop, phone par hi)
    private fun trainNN(gate: String, epochs: Int): String {
        val xs = arrayOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(0.0, 1.0), doubleArrayOf(1.0, 0.0), doubleArrayOf(1.0, 1.0))
        val ys = when (gate) {
            "and" -> doubleArrayOf(0.0, 0.0, 0.0, 1.0)
            "or" -> doubleArrayOf(0.0, 1.0, 1.0, 1.0)
            "nand" -> doubleArrayOf(1.0, 1.0, 1.0, 0.0)
            else -> doubleArrayOf(0.0, 1.0, 1.0, 0.0)
        }
        val rnd = java.util.Random(42)
        val hn = 4
        val w1 = Array(hn) { DoubleArray(2) { rnd.nextGaussian() } }
        val b1 = DoubleArray(hn)
        val w2 = DoubleArray(hn) { rnd.nextGaussian() }
        var b2 = 0.0
        fun sg(z: Double) = 1.0 / (1.0 + exp(-z))
        val lr = 0.5
        var loss = 0.0
        for (e in 0 until epochs) {
            loss = 0.0
            for (k in 0 until 4) {
                val h = DoubleArray(hn) { j -> sg(w1[j][0] * xs[k][0] + w1[j][1] * xs[k][1] + b1[j]) }
                var z = b2
                for (j in 0 until hn) z += w2[j] * h[j]
                val o = sg(z)
                val err = o - ys[k]
                loss += err * err
                val dO = err * o * (1 - o)
                for (j in 0 until hn) {
                    val dH = dO * w2[j] * h[j] * (1 - h[j])
                    w2[j] -= lr * dO * h[j]
                    w1[j][0] -= lr * dH * xs[k][0]
                    w1[j][1] -= lr * dH * xs[k][1]
                    b1[j] -= lr * dH
                }
                b2 -= lr * dO
            }
        }
        val sb = StringBuilder()
        sb.append("Neural network (2-4-1) ne ").append(gate.uppercase()).append(" gate ").append(epochs).append(" epochs mein seekha. Loss ")
            .append("%.4f".format(loss / 4)).append(". Nateeje: ")
        for (k in 0 until 4) {
            val h = DoubleArray(hn) { j -> sg(w1[j][0] * xs[k][0] + w1[j][1] * xs[k][1] + b1[j]) }
            var z = b2
            for (j in 0 until hn) z += w2[j] * h[j]
            sb.append(xs[k][0].toInt()).append(" aur ").append(xs[k][1].toInt()).append(" par ").append("%.2f".format(sg(z))).append(". ")
        }
        sb.append("Ye chhota demo network hai; 'and', 'or', 'nand', 'xor' try karo.")
        return sb.toString()
    }

    // =====================================================================================
    // Local wallet ledger (hash-chain; asli cryptocurrency nahi)
    private fun wallet(c: Context, t: String): String {
        val a = try { JSONArray(prefs(c).getString("ledger", "[]")) } catch (_: Exception) { JSONArray() }
        fun hashOf(o: JSONObject, prev: String) = hex(sha((prev + "|" + o.optInt("i") + "|" + o.optLong("ts") + "|" + o.optDouble("amt") + "|" + o.optString("note")).toByteArray()))
        var bal = 0.0
        for (i in 0 until a.length()) bal += a.getJSONObject(i).optDouble("amt", 0.0)
        val amt = Regex("\\d+(?:\\.\\d+)?").find(t)?.value?.toDoubleOrNull()
        if (has(t, "verify", "check chain", "chain check", "sahi hai")) {
            var prev = "genesis"
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                if (o.optString("prev") != prev || o.optString("hash") != hashOf(o, prev)) return "Chetavani: entry ${i + 1} par chain tooti hui hai, data badla gaya lagta hai."
                prev = o.optString("hash")
            }
            return "Chain sahi hai. ${a.length()} entries, koi chhed-chhaad nahi."
        }
        if (has(t, "history", "list", "pichhla", "hisab")) {
            if (a.length() == 0) return "Wallet mein abhi koi entry nahi."
            return (maxOf(0, a.length() - 5) until a.length()).joinToString(". ") {
                val o = a.getJSONObject(it); fmt(o.optDouble("amt")) + " " + o.optString("note")
            } + ". Balance " + fmt(bal) + "."
        }
        val isAdd = has(t, "add", "jama", "credit", "received", "mile", "aaye")
        val isSpend = has(t, "spend", "kharch", "debit", "diya", "diye", "gaye")
        if ((isAdd || isSpend) && amt != null && amt > 0) {
            val note = t.split(" ").filter { w -> w.isNotBlank() && w.none { it.isDigit() } && w !in setOf("wallet", "ledger", "add", "jama", "credit", "spend", "kharch", "debit", "rupaye", "rupees", "rs", "karo", "kar", "me", "mein", "se", "ko") }.joinToString(" ")
            val prev = if (a.length() == 0) "genesis" else a.getJSONObject(a.length() - 1).optString("hash")
            val o = JSONObject().put("i", a.length() + 1).put("ts", now()).put("amt", if (isSpend && !isAdd) -amt else amt).put("note", note).put("prev", prev)
            o.put("hash", hashOf(o, prev))
            a.put(o)
            prefs(c).edit().putString("ledger", a.toString()).apply()
            return (if (isSpend && !isAdd) "Kharch" else "Jama") + " darj. Balance " + fmt(bal + o.optDouble("amt")) + "."
        }
        return "Wallet balance " + fmt(bal) + ". Bolo: 'wallet add 500 salary', 'wallet spend 50 chai', 'wallet history', 'wallet verify'. Ye phone par hash-chain wala hisaab-kitaab hai, asli cryptocurrency nahi."
    }

    // =====================================================================================
    // Quantum-safe signature (Lamport one-time, SHA-256)
    private var lastMsg: String? = null
    private var lastPk: Array<ByteArray>? = null
    private var lastSig: Array<ByteArray>? = null

    private fun bitOf(d: ByteArray, bit: Int) = (d[bit / 8].toInt() shr (7 - bit % 8)) and 1

    private fun pq(t: String, raw: String): String {
        val msg = raw.substringAfter(":", "").trim().ifBlank { "" }
        if (has(t, "verify", "check", "jaanch")) {
            val pk = lastPk; val sig = lastSig; val m = lastMsg
            if (pk == null || sig == null || m == null) return "Pehle 'quantum safe sign: <text>' bolo."
            val check = if (msg.isBlank()) m else msg
            val d = sha(check.toByteArray())
            var ok = true
            for (bit in 0 until 256) if (!sha(sig[bit]).contentEquals(pk[bit * 2 + bitOf(d, bit)])) { ok = false; break }
            return if (ok) "Signature sahi hai, text mein koi badlav nahi." else "Signature match nahi hua, text badla gaya hai."
        }
        if (has(t, "sign", "hastakshar")) {
            if (msg.isBlank()) { pend = { a -> pqSign(a) }; return "Kya sign karna hai?" }
            return pqSign(msg)
        }
        return "Quantum-safe signature (Lamport, SHA-256 par based). Bolo: 'quantum safe sign: hello' phir 'quantum safe verify'. Har key sirf ek baar use hoti hai."
    }

    private fun pqSign(msg: String): String {
        val rnd = SecureRandom()
        val sk = Array(512) { ByteArray(32).also { rnd.nextBytes(it) } }
        val pk = Array(512) { sha(sk[it]) }
        val d = sha(msg.toByteArray())
        val sig = Array(256) { bit -> sk[bit * 2 + bitOf(d, bit)] }
        lastMsg = msg; lastPk = pk; lastSig = sig
        val all = ByteArray(512 * 32)
        for (i in 0 until 512) System.arraycopy(pk[i], 0, all, i * 32, 32)
        return "Sign ho gaya. Public key fingerprint " + hex(sha(all)).take(16) + ", signature 8 KB. Private key use hote hi mita di gayi (one-time)."
    }

    // =====================================================================================
    // Translator (chhoti phrasebook: Hindi <-> English, internet nahi)
    private val PHRASES = listOf(
        Triple("hello", "नमस्ते", "namaste"), Triple("good morning", "शुभ प्रभात", "shubh prabhat"),
        Triple("good night", "शुभ रात्रि", "shubh ratri"), Triple("thank you", "धन्यवाद", "dhanyavad"),
        Triple("how are you", "आप कैसे हैं", "aap kaise hain"), Triple("what is your name", "आपका नाम क्या है", "aapka naam kya hai"),
        Triple("my name is", "मेरा नाम है", "mera naam hai"), Triple("where is", "कहाँ है", "kahan hai"),
        Triple("how much", "कितना", "kitna"), Triple("water", "पानी", "paani"), Triple("food", "खाना", "khana"),
        Triple("help", "मदद", "madad"), Triple("yes", "हाँ", "haan"), Triple("no", "नहीं", "nahin"),
        Triple("please", "कृपया", "kripya"), Triple("sorry", "माफ़ कीजिए", "maaf kijiye"),
        Triple("i love you", "मैं तुमसे प्यार करता हूँ", "main tumse pyaar karta hoon"),
        Triple("i am hungry", "मुझे भूख लगी है", "mujhe bhookh lagi hai"), Triple("i am fine", "मैं ठीक हूँ", "main theek hoon"),
        Triple("goodbye", "अलविदा", "alvida"), Triple("welcome", "स्वागत है", "swagat hai"), Triple("friend", "दोस्त", "dost"),
        Triple("mother", "माँ", "maa"), Triple("father", "पिता", "pita"), Triple("school", "स्कूल", "school"),
        Triple("hospital", "अस्पताल", "aspataal"), Triple("doctor", "डॉक्टर", "doctor"), Triple("time", "समय", "samay"),
        Triple("today", "आज", "aaj"), Triple("tomorrow", "कल", "kal"),
        Triple("i do not understand", "मुझे समझ नहीं आया", "mujhe samajh nahin aaya")
    )

    private fun translate(t: String): String {
        val target = MlTools.LANGS.keys.firstOrNull { has(t, "to $it", "in $it", "into $it", "$it mein", "$it me", "ka $it") }
            ?: (if (has(t, "angrezi")) "english" else null)
        var p = t
        for (w in listOf("translate", "translation", "anuvad", "अनुवाद", "bolo", "batao", "karo", "please", "angrezi"))
            p = p.replace(w, " ")
        p = p.replace(Regex("\\b(in|to|into|ka|mein|me)\\b"), " ")
        for (l in MlTools.LANGS.keys) p = p.replace(l, " ")
        p = p.replace(Regex("\\s+"), " ").trim()
        if (p.isBlank()) return "Kya translate karna hai? Jaise: translate good morning to hindi."
        val toHi = target == "hindi"
        val toEn = target == "english"
        if (toHi || target == null) {
            PHRASES.sortedByDescending { it.first.length }.firstOrNull { Regex("\\b" + Regex.escape(it.first) + "\\b").containsMatchIn(p) }?.let { return "'" + it.first + "' ko Hindi mein kehte hain: " + it.second + "." }
        }
        if (toEn || target == null) {
            PHRASES.sortedByDescending { it.third.length }.firstOrNull { Regex("\\b" + Regex.escape(it.third) + "\\b").containsMatchIn(p) || p.contains(it.second) }?.let { return "'" + p + "' ka matlab hai: " + it.first + "." }
        }
        // phrasebook mein nahi: ML Kit (100+ nahi, ~20 languages; model pehli baar download hota hai)
        return MlTools.translate(p, target ?: if (p.any { it in '\u0900'..'\u097F' }) "english" else "hindi")
    }

    // =====================================================================================
    // Offline builders: HTML5 website / app / photo editor / 3D viewer
    private const val BASE_CSS = "body{margin:0;font-family:system-ui,sans-serif;background:#0b0b1a;color:#eee}" +
        ".wrap{max-width:720px;margin:auto;padding:20px}" +
        "h1{background:linear-gradient(90deg,#8e5bff,#00e5ff);-webkit-background-clip:text;color:transparent}" +
        "button,input,textarea{padding:10px 14px;border-radius:10px;border:1px solid #8e5bff;background:#16163a;color:#fff;font-size:16px}" +
        "button{background:#6a3dff;cursor:pointer}.card{background:#14143a;border:1px solid #2a2a6a;border-radius:14px;padding:16px;margin:12px 0}"

    private fun page(title: String, css: String, body: String, js: String): String =
        "<!DOCTYPE html><html lang=\"hi\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>" +
            title + "</title><style>" + BASE_CSS + css + "</style></head><body>" + body + "<script>" + js + "</script></body></html>"

    private fun buildApp(c: Context, t: String): String {
        val kind: String
        val html: String
        when {
            has(t, "photo") -> {
                kind = "photo-editor"
                val sl = listOf("b" to "Brightness|0|200|100", "c" to "Contrast|0|200|100", "s" to "Saturation|0|300|100",
                    "g" to "Grayscale|0|100|0", "p" to "Sepia|0|100|0", "bl" to "Blur|0|10|0")
                val sliders = sl.joinToString("") { (id, d) -> val x = d.split("|"); "<div><label>" + x[0] + " <input type=\"range\" id=\"" + id + "\" min=\"" + x[1] + "\" max=\"" + x[2] + "\" value=\"" + x[3] + "\"></label></div>" }
                html = page("Photo Editor", "canvas{max-width:100%;border-radius:12px}",
                    "<div class=\"wrap\"><h1>Photo Editor</h1><input type=\"file\" id=\"f\" accept=\"image/*\"><div class=\"card\">" + sliders + "</div><canvas id=\"c\"></canvas></div>",
                    "var img=new Image(),cv=document.getElementById('c'),cx=cv.getContext('2d');" +
                        "function v(id){return document.getElementById(id).value}" +
                        "function draw(){if(!img.src)return;cv.width=img.width;cv.height=img.height;cx.filter='brightness('+v('b')+'%) contrast('+v('c')+'%) saturate('+v('s')+'%) grayscale('+v('g')+'%) sepia('+v('p')+'%) blur('+v('bl')+'px)';cx.drawImage(img,0,0)}" +
                        "document.getElementById('f').onchange=function(e){var r=new FileReader();r.onload=function(){img.onload=draw;img.src=r.result};r.readAsDataURL(e.target.files[0])};" +
                        "['b','c','s','g','p','bl'].forEach(function(id){document.getElementById(id).oninput=draw});")
            }
            has(t, "3d") -> {
                kind = "3d-viewer"
                html = page("3D Viewer", "canvas{display:block;margin:auto;border-radius:12px;touch-action:none}",
                    "<div class=\"wrap\"><h1>3D Model Viewer</h1><div><button onclick=\"cur='cube'\">Cube</button> <button onclick=\"cur='pyramid'\">Pyramid</button> <button onclick=\"cur='octa'\">Octahedron</button></div><canvas id=\"c\"></canvas><p>Ungli se ghumao.</p></div>",
                    "var cv=document.getElementById('c'),cx=cv.getContext('2d');cv.width=Math.min(window.innerWidth-40,560);cv.height=cv.width;" +
                        "var shapes={cube:{v:[[-1,-1,-1],[1,-1,-1],[1,1,-1],[-1,1,-1],[-1,-1,1],[1,-1,1],[1,1,1],[-1,1,1]],e:[[0,1],[1,2],[2,3],[3,0],[4,5],[5,6],[6,7],[7,4],[0,4],[1,5],[2,6],[3,7]]}," +
                        "pyramid:{v:[[-1,1,-1],[1,1,-1],[1,1,1],[-1,1,1],[0,-1,0]],e:[[0,1],[1,2],[2,3],[3,0],[0,4],[1,4],[2,4],[3,4]]}," +
                        "octa:{v:[[1,0,0],[-1,0,0],[0,1,0],[0,-1,0],[0,0,1],[0,0,-1]],e:[[0,2],[0,3],[0,4],[0,5],[1,2],[1,3],[1,4],[1,5],[2,4],[2,5],[3,4],[3,5]]}};" +
                        "var cur='cube',ax=0.5,ay=0.5,dr=false,lx=0,ly=0;" +
                        "cv.onpointerdown=function(e){dr=true;lx=e.clientX;ly=e.clientY};window.onpointerup=function(){dr=false};" +
                        "window.onpointermove=function(e){if(dr){ay+=(e.clientX-lx)*0.01;ax+=(e.clientY-ly)*0.01;lx=e.clientX;ly=e.clientY}};" +
                        "function frame(){if(!dr)ay+=0.01;cx.fillStyle='#0b0b1a';cx.fillRect(0,0,cv.width,cv.height);var s=shapes[cur];" +
                        "var p=s.v.map(function(q){var c1=Math.cos(ay),s1=Math.sin(ay);var x1=q[0]*c1-q[2]*s1,z1=q[0]*s1+q[2]*c1;c1=Math.cos(ax);s1=Math.sin(ax);var y1=q[1]*c1-z1*s1,z2=q[1]*s1+z1*c1;var k=cv.width/(4+z2*0.5)*0.75;return [cv.width/2+x1*k,cv.height/2+y1*k]});" +
                        "cx.strokeStyle='#00e5ff';cx.lineWidth=2;s.e.forEach(function(e){cx.beginPath();cx.moveTo(p[e[0]][0],p[e[0]][1]);cx.lineTo(p[e[1]][0],p[e[1]][1]);cx.stroke()});requestAnimationFrame(frame)}frame();")
            }
            has(t, "todo", "task") -> {
                kind = "todo-app"
                html = page("Todo App", "",
                    "<div class=\"wrap\"><h1>Todo App</h1><div><input id=\"t\" placeholder=\"Naya kaam\"> <button onclick=\"add()\">Add</button></div><div id=\"l\"></div></div>",
                    "var items=[];try{items=JSON.parse(localStorage.getItem('todo')||'[]')}catch(e){}" +
                        "function save(){try{localStorage.setItem('todo',JSON.stringify(items))}catch(e){}}" +
                        "function draw(){var l=document.getElementById('l');l.innerHTML='';items.forEach(function(x,i){var d=document.createElement('div');d.className='card';d.style.textDecoration=x.d?'line-through':'none';d.textContent=x.t;d.onclick=function(){x.d=!x.d;save();draw()};var b=document.createElement('button');b.textContent='x';b.style.float='right';b.onclick=function(e){e.stopPropagation();items.splice(i,1);save();draw()};d.appendChild(b);l.appendChild(d)})}" +
                        "function add(){var v=document.getElementById('t').value.trim();if(!v)return;items.push({t:v,d:false});document.getElementById('t').value='';save();draw()}draw();")
            }
            has(t, "calculator") -> {
                kind = "calculator-app"
                val keys = "7,8,9,/,4,5,6,*,1,2,3,-,0,.,=,+".split(",").joinToString("") { "<button onclick=\"k('" + it + "')\">" + it + "</button>" }
                html = page("Calculator", ".g{display:grid;grid-template-columns:repeat(4,1fr);gap:8px}#d{width:100%;box-sizing:border-box;margin-bottom:10px;font-size:28px;text-align:right}",
                    "<div class=\"wrap\"><h1>Calculator</h1><input id=\"d\" readonly><div class=\"g\">" + keys + "<button onclick=\"document.getElementById('d').value=''\">C</button></div></div>",
                    "function k(x){var d=document.getElementById('d');if(x=='='){if(/^[0-9+\\-*/(). ]+\$/.test(d.value)){try{d.value=Function('return '+d.value)()}catch(e){d.value='Error'}}}else d.value+=x}")
            }
            has(t, "counter") -> {
                kind = "counter-app"
                html = page("Counter", "#n{font-size:80px;text-align:center}",
                    "<div class=\"wrap\"><h1>Counter</h1><div id=\"n\">0</div><button onclick=\"m(1)\">+1</button> <button onclick=\"m(-1)\">-1</button> <button onclick=\"m(0)\">Reset</button></div>",
                    "var n=0;function m(d){n=d==0?0:n+d;document.getElementById('n').textContent=n}")
            }
            has(t, "notes") -> {
                kind = "notes-app"
                html = page("Notes", "textarea{width:100%;height:60vh;box-sizing:border-box}",
                    "<div class=\"wrap\"><h1>Notes</h1><textarea id=\"a\" placeholder=\"Yahan likho, apne aap save hota hai\"></textarea></div>",
                    "var a=document.getElementById('a');try{a.value=localStorage.getItem('n')||''}catch(e){}a.oninput=function(){try{localStorage.setItem('n',a.value)}catch(e){}};")
            }
            has(t, "landing", "business", "shop", "dukaan") -> {
                kind = "landing-page"
                html = page("Landing Page", ".hero{text-align:center;padding:50px 0}.hero h1{font-size:40px}",
                    "<div class=\"wrap\"><div class=\"hero\"><h1>Aapka Business Naam</h1><p>Ek line mein batao aap kya karte ho.</p><button>Contact Karo</button></div><div class=\"card\"><b>Tez</b><br>Pehla fayda yahan likho.</div><div class=\"card\"><b>Bharosemand</b><br>Doosra fayda yahan likho.</div><div class=\"card\"><b>Sasta</b><br>Teesra fayda yahan likho.</div></div>", "")
            }
            else -> {
                kind = "portfolio-website"
                html = page("Portfolio", ".hero{text-align:center;padding:40px 0}",
                    "<div class=\"wrap\"><div class=\"hero\"><h1>Mera Naam</h1><p>Developer | Designer | Creator</p></div><div class=\"card\"><b>Mere baare mein</b><br>Yahan apne baare mein likho.</div><div class=\"card\"><b>Projects</b><br>Project 1, Project 2, Project 3</div><div class=\"card\"><b>Contact</b><br>email@example.com</div></div>", "")
            }
        }
        val err = CodeStore.write(c, "offline-$kind", "index.html", html)
        if (err != null) return "Nahi ban paya: $err"
        try { CodeBus.previewProject = CodeStore.safeName("offline-$kind"); CodeBus.previewTick++ } catch (_: Exception) {}
        return "$kind ban gaya, project 'offline-$kind'. Tools mein Code & Website Builder se dekho ya badlo. Ye ready-made template hai, apni marzi ka design ke liye online AI chahiye."
    }
}
