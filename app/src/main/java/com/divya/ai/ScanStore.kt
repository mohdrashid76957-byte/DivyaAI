package com.divya.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Scan history: QR / Barcode / OCR text / Document / Image, phone par hi (SharedPreferences), last 50. */
object ScanStore {
    class Item(val ts: Long, val type: String, val text: String, val img: String)

    private const val KEY = "scanHist"
    private const val MAX = 50

    fun list(c: Context): List<Item> = try {
        val a = JSONArray(c.getSharedPreferences("divya", 0).getString(KEY, "[]") ?: "[]")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Item(o.optLong("ts"), o.optString("type"), o.optString("text"), o.optString("img"))
        }
    } catch (e: Exception) { emptyList() }

    fun add(c: Context, type: String, text: String, img: String = "") {
        if (text.isBlank()) return
        val cur = list(c)
        val last = cur.firstOrNull()
        if (last != null && last.text == text && last.type == type) return   // wahi cheez baar baar save na ho
        val o = JSONArray()
        o.put(JSONObject().put("ts", System.currentTimeMillis()).put("type", type).put("text", text.take(4000)).put("img", img))
        cur.take(MAX - 1).forEach { o.put(JSONObject().put("ts", it.ts).put("type", it.type).put("text", it.text).put("img", it.img)) }
        c.getSharedPreferences("divya", 0).edit().putString(KEY, o.toString()).apply()
    }

    fun clear(c: Context) { c.getSharedPreferences("divya", 0).edit().remove(KEY).apply() }

    fun whenText(ts: Long): String = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(ts))
}
