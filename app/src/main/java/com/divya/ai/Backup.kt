package com.divya.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Yaadein (notes), Pro chats, scan history aur settings ko ek JSON file mein backup/restore karta hai.
 * API key aur privacy password jaan-boojh kar BACKUP mein nahi jaate (security).
 */
object BackupStore {
    private val KEYS = setOf(
        "notes", "proChat", "proCustom", "proModel", "proConc", "scanHist",
        "call", "voice", "model", "gf", "glow", "theme2", "offlineOnly", "wake"
    )

    fun export(c: Context): File {
        val p = c.getSharedPreferences("divya", 0)
        val s = JSONObject(); val b = JSONObject(); val i = JSONObject(); val f = JSONObject()
        for ((k, v) in p.all) {
            if (k !in KEYS || v == null) continue
            when (v) {
                is String -> s.put(k, v)
                is Boolean -> b.put(k, v)
                is Int -> i.put(k, v)
                is Float -> f.put(k, v.toDouble())
            }
        }
        val root = JSONObject()
        root.put("app", "DivyaAI")
        root.put("format", 1)
        root.put("ts", System.currentTimeMillis())
        root.put("s", s); root.put("b", b); root.put("i", i); root.put("f", f)
        val dir = File(c.cacheDir, "share").apply { mkdirs() }
        val name = "divya_backup_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".json"
        val file = File(dir, name)
        file.writeText(root.toString(2))
        return file
    }

    fun share(c: Context) {
        val file = export(c)
        val uri = androidx.core.content.FileProvider.getUriForFile(c, c.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        c.startActivity(Intent.createChooser(send, "Divya backup share karo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Hinglish status message lautata hai. */
    fun restore(c: Context, uri: Uri): String {
        try {
            val text = c.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return "File khul nahi payi."
            val root = JSONObject(text)
            if (root.optString("app") != "DivyaAI") return "Ye Divya ka backup nahi lag raha."
            val ed = c.getSharedPreferences("divya", 0).edit()
            var n = 0
            root.optJSONObject("s")?.let { o -> o.keys().forEach { k -> if (k in KEYS) { ed.putString(k, o.getString(k)); n++ } } }
            root.optJSONObject("b")?.let { o -> o.keys().forEach { k -> if (k in KEYS) { ed.putBoolean(k, o.getBoolean(k)); n++ } } }
            root.optJSONObject("i")?.let { o -> o.keys().forEach { k -> if (k in KEYS) { ed.putInt(k, o.getInt(k)); n++ } } }
            root.optJSONObject("f")?.let { o -> o.keys().forEach { k -> if (k in KEYS) { ed.putFloat(k, o.getDouble(k).toFloat()); n++ } } }
            ed.apply()
            return "✅ $n cheezein wapas aa gayi. App ek baar band karke phir kholo."
        } catch (e: Exception) {
            return "Restore fail: ${e.message ?: "file theek nahi hai"}"
        }
    }
}

@Composable
fun BackupScreen(th: UiTheme, ctx: Context) = Page(th, "Backup & restore") {
    var msg by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) msg = BackupStore.restore(ctx, uri)
    }
    Text(
        "Apni yaadein (notes), Pro chats, scan history aur settings ek file mein le jao. API key aur privacy password is file mein NAHI jaate.",
        color = th.text, fontSize = 14.sp
    )
    Btn(th, "Backup banao aur share karo") {
        msg = try { BackupStore.share(ctx); "" } catch (e: Exception) { "Backup fail: ${e.message}" }
    }
    Btn(th, "Backup file se restore karo") { pick.launch(arrayOf("*/*")) }
    if (msg.isNotBlank()) Text(msg, color = th.accent, fontWeight = FontWeight.SemiBold)
}
