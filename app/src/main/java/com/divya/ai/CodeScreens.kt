package com.divya.ai

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider

/** Tools > Code: projects ki list, files dekho/copy karo, preview, ZIP share, delete (confirm ke saath). */
@Composable
fun CodeScreen(th: UiTheme, ctx: Context, openPreview: (String) -> Unit) {
    var tick by remember { mutableStateOf(0) }
    var sel by remember { mutableStateOf<String?>(null) }
    var file by remember { mutableStateOf<String?>(null) }
    var confirmDel by remember { mutableStateOf<String?>(null) }
    BackHandler(sel != null) { if (file != null) file = null else sel = null }

    fun toast(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()
    fun copy(t: String) {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("divya", t)); toast("Copy ho gaya")
    }
    fun share(p: String) {
        try {
            val z = CodeStore.zip(ctx, p)
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", z)
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/zip")
                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "ZIP share karo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { toast("ZIP share nahi hua: ${e.message}") }
    }

    val projects = remember(tick, sel) { CodeStore.listProjects(ctx) }
    val p = sel
    if (p == null) {
        Page(th, "💻 Code & Website Builder") {
            Text("Bolo: \"Divya, mere liye portfolio website banao\" ya \"ek Python script likho\". Files yahin save hongi (sirf is app ke private folder mein).", color = th.text, fontSize = 14.sp)
            if (projects.isEmpty()) Text("Abhi koi project nahi hai.", color = th.text.copy(alpha = 0.6f))
            projects.forEach { name ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(th.accent.copy(alpha = 0.18f))
                    .clickable { sel = name }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📁 $name", color = th.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${CodeStore.files(ctx, name).size} files", color = th.text.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
            Btn(th, "🔄 Refresh") { tick++ }
        }
    } else if (file == null) {
        Page(th, "📁 $p") {
            val fs = CodeStore.files(ctx, p)
            if (fs.any { it.endsWith(".html") }) Btn(th, "▶ Preview kholo") { openPreview(p) }
            Btn(th, "📤 ZIP share karo (GitHub/Netlify upload ke liye)") { share(p) }
            fs.forEach { f ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(th.accent.copy(alpha = 0.12f))
                    .clickable { file = f }.padding(14.dp)) { Text("📄 $f", color = th.text) }
            }
            if (fs.any { it.equals("DEPLOY.md", true) }) Text("Deploy guide ke liye DEPLOY.md kholo.", color = th.text.copy(alpha = 0.7f), fontSize = 12.sp)
            OutlinedButton({ confirmDel = p }, Modifier.fillMaxWidth()) { Text("🗑 Project delete karo", color = Color(0xFFFF6B6B)) }
        }
    } else {
        val f = file!!
        Page(th, "📄 $f") {
            val text = CodeStore.read(ctx, p, f) ?: "(file nahi mili)"
            Btn(th, "📋 Copy (Termux/editor mein paste karo)") { copy(text) }
            SelectionContainer {
                Text(text, color = th.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(10.dp))
            }
        }
    }
    confirmDel?.let { d ->
        AlertDialog(onDismissRequest = { confirmDel = null },
            title = { Text("'$d' delete karun?") }, text = { Text("Is project ki saari files hamesha ke liye hat jayengi.") },
            confirmButton = { TextButton({ CodeStore.delete(ctx, d); confirmDel = null; sel = null; file = null; tick++ }) { Text("Delete") } },
            dismissButton = { TextButton({ confirmDel = null }) { Text("Rehne do") } })
    }
}

/** Website preview (app ke andar WebView). Sirf us project ki local files; generated JS ko koi app-access nahi. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewScreen(th: UiTheme, ctx: Context, project: String, tick: Int, onBack: () -> Unit) {
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("← Wapas", color = th.text) }
            Text(project, color = th.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        val index = java.io.File(CodeStore.root(ctx), CodeStore.safeName(project) + "/index.html")
        if (!index.exists()) { Text("index.html nahi mila", color = th.text, modifier = Modifier.padding(16.dp)); return@Column }
        key(tick) {
            AndroidView(modifier = Modifier.fillMaxSize().weight(1f), factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = false
                    @Suppress("DEPRECATION") run { settings.allowFileAccessFromFileURLs = false; settings.allowUniversalAccessFromFileURLs = false }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                            val u = r.url
                            if (u.scheme == "file") return false
                            // bahar ke links browser mein khulenge (preview ke andar nahi)
                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                            return true
                        }
                    }
                    loadUrl("file://" + index.absolutePath)
                }
            }, onRelease = { it.destroy() })
        }
    }
}
