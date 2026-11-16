package com.divya.ai

import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.io.File

/**
 * v3.19: Divya ke banaye mini-apps. Ek HTML file per app, app ke private storage mein.
 * Ye apps sandbox mein chalte hain: internet nahi, phone ki files nahi, phone control nahi.
 */
object MiniApps {
    private const val PREF = "divya"
    private const val KEY_INDEX = "miniAppIndex"   // {slug: display name}
    private const val MAX_HTML = 200_000

    private fun dir(c: Context) = File(c.filesDir, "miniapps").apply { mkdirs() }

    fun slug(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "app" }

    private fun index(c: Context): JSONObject =
        JSONObject(c.getSharedPreferences(PREF, 0).getString(KEY_INDEX, "{}") ?: "{}")

    fun save(c: Context, name: String, html: String): String {
        val s = slug(name)
        File(dir(c), "$s.html").writeText(html.take(MAX_HTML), Charsets.UTF_8)
        val idx = index(c).put(s, name)
        c.getSharedPreferences(PREF, 0).edit().putString(KEY_INDEX, idx.toString()).apply()
        return s
    }

    fun load(c: Context, slug: String): String? {
        val f = File(dir(c), "$slug.html")
        return if (f.exists()) f.readText(Charsets.UTF_8) else null
    }

    fun names(c: Context): List<Pair<String, String>> {
        val idx = index(c)
        return idx.keys().asSequence().map { it to idx.getString(it) }.toList()
    }

    /** Naam se dhoondo: poora naam, ya usme ye shabd ho. */
    fun find(c: Context, query: String): String? {
        val q = query.lowercase().trim()
        if (q.isBlank()) return null
        val all = names(c)
        return all.firstOrNull { it.second.lowercase() == q }?.first
            ?: all.firstOrNull { it.second.lowercase().contains(q) || q.contains(it.second.lowercase()) }?.first
    }
}

/** Gemini tools ka handler. GeminiLive se call hota hai. */
object MiniAppTool {
    fun create(c: Context, name: String, html: String): String {
        if (name.isBlank() || html.isBlank()) return "App ka naam aur HTML dono chahiye."
        val slug = MiniApps.save(c, name, html)
        open(c, slug)
        return "Mini-app '$name' ban gaya aur khul gaya. Dobara kholne ke liye 'open ${name}' bolo."
    }

    fun openByName(c: Context, name: String): String {
        val slug = MiniApps.find(c, name) ?: return "Ye naam ka mini-app nahi mila. list_mini_apps se dekh lo."
        open(c, slug)
        return "Mini-app khol diya."
    }

    fun list(c: Context): String {
        val all = MiniApps.names(c)
        return if (all.isEmpty()) "Abhi koi mini-app nahi hai." else "Mini-apps: " + all.joinToString(", ") { it.second }
    }

    private fun open(c: Context, slug: String) {
        c.startActivity(Intent(c, MiniAppActivity::class.java).putExtra("slug", slug).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
