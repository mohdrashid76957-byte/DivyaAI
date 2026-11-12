package com.divya.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.Locale

/**
 * v2.40 - Habit & wellness tracker: paani, dawai, exercise, padhai, namaz... roz ka goal,
 * aaj ka progress aur streak (lagataar din goal poora hua).
 *
 * Data: filesDir/divya_habits.json. Structure:
 *   [ { "name": "paani", "goal": 8, "log": { "2026-11-12": 5, ... } } ]
 * Sirf local, koi network nahi. Ye sirf ginti rakhta hai, medical salah nahi deta.
 */
object Habits {
    private const val FILE = "divya_habits.json"
    private const val MAX_HABITS = 30
    private val lock = Any()

    private fun file(c: Context) = File(c.filesDir, FILE)

    private fun load(c: Context): MutableList<JSONObject> = try {
        val f = file(c)
        val a = if (f.exists()) JSONArray(f.readText()) else JSONArray()
        (0 until a.length()).map { a.getJSONObject(it) }.toMutableList()
    } catch (_: Exception) {
        mutableListOf()
    }

    private fun store(c: Context, list: List<JSONObject>) {
        val a = JSONArray()
        for (o in list) a.put(o)
        try { file(c).writeText(a.toString()) } catch (_: Exception) {}
    }

    private fun norm(s: String) = s.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    /** Naam se habit dhoondo: pehle exact, phir jo naam mein ho ya jiske andar naam ho. */
    private fun find(list: List<JSONObject>, name: String): JSONObject? {
        val n = norm(name)
        if (n.isEmpty()) return null
        return list.firstOrNull { norm(it.optString("name")) == n }
            ?: list.firstOrNull {
                val hn = norm(it.optString("name"))
                hn.contains(n) || n.contains(hn)
            }
    }

    private fun countOn(h: JSONObject, date: String): Int =
        h.optJSONObject("log")?.optInt(date, 0) ?: 0

    /** Streak: aaj goal poora ho to aaj se, warna kal se ginti. */
    private fun streak(h: JSONObject): Int {
        val goal = h.optInt("goal", 1)
        val today = LocalDate.now()
        var d = if (countOn(h, today.toString()) >= goal) today else today.minusDays(1)
        var s = 0
        while (countOn(h, d.toString()) >= goal) {
            s++
            d = d.minusDays(1)
        }
        return s
    }

    /** Nayi habit banao, ya same naam ka goal badlo. */
    fun add(c: Context, name: String, goal: Int): String {
        val n = norm(name).take(40)
        if (n.isEmpty()) return "habit ka naam batao, jaise paani ya dawai."
        val g = goal.coerceIn(1, 50)
        synchronized(lock) {
            val list = load(c)
            val existing = list.firstOrNull { norm(it.optString("name")) == n }
            if (existing != null) {
                existing.put("goal", g)
                store(c, list)
                return "$n ka roz ka goal $g set kar diya."
            }
            if (list.size >= MAX_HABITS) return "habits ki limit ($MAX_HABITS) poori ho gayi. Koi purani habit hatao."
            list.add(JSONObject().put("name", n).put("goal", g).put("log", JSONObject()))
            store(c, list)
        }
        return "habit bana di: $n, roz $g baar. Jab bhi kar lo, bata dena."
    }

    /** Aaj ke liye count badhao (default 1). */
    fun log(c: Context, name: String, count: Int): String {
        val amount = count.coerceIn(1, 50)
        return synchronized(lock) {
            val list = load(c)
            val h = find(list, name)
                ?: return "ye habit abhi nahi hai. Pehle bolo: $name ki habit 1 baar roz."
            val today = LocalDate.now().toString()
            val lg = h.optJSONObject("log") ?: JSONObject().also { h.put("log", it) }
            val now = lg.optInt(today, 0) + amount
            lg.put(today, now)
            store(c, list)
            val goal = h.optInt("goal", 1)
            val nm = h.optString("name")
            val st = streak(h)
            when {
                now == goal -> "shabash! $nm ka aaj ka goal pura ($now/$goal). Streak $st din."
                now > goal -> "$nm: aaj $now ho gaye (goal $goal). Streak $st din."
                else -> "$nm: aaj $now/$goal. Aur ${goal - now} baaki hain."
            }
        }
    }

    /** Ek habit ka ya sab habits ka aaj ka progress aur streak. */
    fun status(c: Context, name: String): String {
        val list = synchronized(lock) { load(c) }
        if (list.isEmpty()) return "abhi koi habit nahi hai. Bolo: paani ki habit 8 baar roz."
        val targets: List<JSONObject> = if (name.isBlank()) list else listOfNotNull(find(list, name))
        if (targets.isEmpty()) return "ye habit nahi mili."
        val today = LocalDate.now().toString()
        return targets.joinToString(" | ") { h ->
            "${h.optString("name")}: aaj ${countOn(h, today)}/${h.optInt("goal", 1)}, streak ${streak(h)} din"
        }
    }

    fun delete(c: Context, name: String): String = synchronized(lock) {
        val list = load(c)
        val h = find(list, name) ?: return "ye habit nahi mili."
        list.remove(h)
        store(c, list)
        "${h.optString("name")} habit hata di."
    }

    /**
     * Pro Chat (online typed chat) ke liye:
     *   "habit add paani 8", "habit done paani 2", "habit status", "habit hatao paani",
     *   aur natural: "paani piya" / "dawai kar li" (sirf tab jab wo habit pehle se ho).
     * Match na ho to null.
     */
    fun handle(c: Context, raw: String): String? {
        val s = raw.trim()
        Regex("(?i)\\bhabit\\s+(?:add|banao|bana|set)\\s+(.+?)\\s+(\\d{1,2})\\b").find(s)?.let { m ->
            return add(c, m.groupValues[1], m.groupValues[2].toInt())
        }
        Regex("(?i)\\bhabit\\s+(?:done|log|kiya|complete|kar\\s*di)\\s+(.+?)(?:\\s+(\\d{1,2}))?\\s*[.!]?$").find(s)?.let { m ->
            val n = m.groupValues[2].ifEmpty { "1" }.toInt()
            return log(c, m.groupValues[1], n)
        }
        Regex("(?i)\\bhabits?\\s+(?:status|batao|report|dikhao|progress)\\b\\s*(.*)").find(s)?.let { m ->
            return status(c, m.groupValues[1])
        }
        Regex("(?i)\\bhabit\\s+(?:delete|hatao|remove|band karo)\\s+(.+)").find(s)?.let { m ->
            return delete(c, m.groupValues[1])
        }
        // Natural: "paani piya", "dawai kar li" - sirf agar wo habit pehle se bani ho
        Regex("(?i)^(.+?)\\s+(?:piya|pi\\s*li|pi\\s*liya|khaya|kha\\s*liya|kar\\s*liya|kar\\s*li|ho\\s*gaya|done)\\s*[.!]?$").find(s)?.let { m ->
            val nm = m.groupValues[1]
            val exists = synchronized(lock) { find(load(c), nm) != null }
            if (exists) return log(c, nm, 1)
        }
        return null
    }
}
