package com.divya.ai

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Termux se aaya result (stdout / stderr / exit code). */
class TermuxRes(val out: String, val err: String, val code: Int, val errMsg: String)

object TermuxBus {
    val ids = AtomicInteger(100)
    val latches = ConcurrentHashMap<Int, CountDownLatch>()
    val results = ConcurrentHashMap<Int, TermuxRes>()
}

/** Termux apne RUN_COMMAND ka natija is receiver par PendingIntent se bhejta hai. */
class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("divya_id", -1)
        if (id < 0) return
        val b = i.getBundleExtra("result")
        val res = if (b == null) TermuxRes("", "", -1, "Termux ne koi result nahi diya")
        else TermuxRes(b.getString("stdout") ?: "", b.getString("stderr") ?: "", b.getInt("exitCode", -1), b.getString("errmsg") ?: "")
        TermuxBus.results[id] = res
        TermuxBus.latches[id]?.countDown()
    }
}

/**
 * Divya ka Termux control. Official "RUN_COMMAND" intent use hota hai (Termux ke setup ke baad):
 *   1) Termux mein: allow-external-apps=true  (~/.termux/termux.properties)
 *   2) Is app ko "Run commands in Termux" permission
 *   3) App ke Termux screen mein "Termux control" switch ON
 * Khatarnak commands (home/storage delete, format, shutdown, curl|sh, SMS/call/contacts) block hain.
 */
object Termux {
    const val PKG = "com.termux"
    const val PERM = "com.termux.permission.RUN_COMMAND"
    private const val HOME = "/data/data/com.termux/files/home"
    private const val BASH = "/data/data/com.termux/files/usr/bin/bash"
    val log = mutableStateListOf<String>()
    private val main by lazy { Handler(Looper.getMainLooper()) }

    fun installed(c: Context) = try { c.packageManager.getPackageInfo(PKG, 0); true } catch (_: Exception) { false }
    fun hasPerm(c: Context) = c.checkSelfPermission(PERM) == android.content.pm.PackageManager.PERMISSION_GRANTED
    fun enabled(c: Context) = c.getSharedPreferences("divya", 0).getBoolean("termuxOn", false)

    private fun note(s: String) { main.post { log.add(0, s.take(400)); while (log.size > 40) log.removeAt(log.lastIndex) } }

    // Regex ko lazy + try/catch mein rakha hai: Android (ICU) ke regex engine mein agar koi pattern na chale
    // to poora Termux object crash (NoClassDefFoundError) na ho. Braces '}' ab escape kiye hain.
    private fun safeRegex(p: String): Regex? = try { Regex(p) } catch (_: Throwable) { null }
    private val dangerRm: Regex? by lazy {
        safeRegex("\\brm\\b[^;&|]*\\s-[a-z]*r[a-z]*\\b[^;&|]*\\s(/|~/?|\\\$home/?|\\\$\\{home\\}/?|\\*|/sdcard/?|/storage/?|/data/?|/storage/emulated/?(0/?)?)(\\s|\$|;|&|\\|)")
    }
    private val blockedWords = listOf("mkfs", "dd if=", ":(){", "> /dev/sd", "> /dev/block", "chmod -r 777 /", "chown -r", "su -c", "reboot", "shutdown", "poweroff",
        "termux-sms", "termux-telephony", "termux-contact", "termux-call-log", "termux-clipboard-get", "termux-camera", "termux-microphone", "termux-location", "termux-wifi")
    private val pipeShell: Regex? by lazy { safeRegex("(curl|wget)[^|;]*\\|\\s*(sudo\\s+)?(ba|z)?sh\\b") }

    /** null = theek hai, warna block karne ka karan. */
    fun danger(cmd: String): String? {
        val t = cmd.lowercase().replace(Regex("\\s+"), " ")
        val rm = dangerRm
        if (rm != null) { if (rm.containsMatchIn(t)) return "poora home/storage delete karne wala rm" }
        else if (t.contains("rm ") && Regex("\\s-[a-z]*r").containsMatchIn(t) && listOf(" /", " ~", "\$home", " *").any { t.contains(it) }) return "rm -r (safety)"
        blockedWords.firstOrNull { t.contains(it) }?.let { return "'$it'" }
        val ps = pipeShell
        val piped = if (ps != null) ps.containsMatchIn(t) else ((t.contains("curl") || t.contains("wget")) && (t.contains("| sh") || t.contains("|sh") || t.contains("| bash") || t.contains("|bash")))
        if (piped) return "internet se script download karke seedha chalana (curl|sh)"
        return null
    }

    private fun gate(c: Context): String? = when {
        !enabled(c) -> "Termux control OFF hai. User ko bolo: app mein Termux Setup screen kholke 'Termux control' switch ON kare."
        !installed(c) -> "Termux install nahi hai (F-Droid / GitHub wala Termux chahiye, Play Store wala nahi)."
        !hasPerm(c) -> "Termux permission nahi mili. User ko bolo: Termux Setup screen mein 'Permission do' dabaye."
        else -> null
    }

    /** Command Termux mein chalao. visible=true: Termux window mein type/run hota hai (output dikhta hai). false: background, output wapas milta hai. */
    fun run(c: Context, command: String, visible: Boolean = false, waitSec: Int = 30): String {
        gate(c)?.let { return "failed: $it" }
        val cmd = command.trim()
        if (cmd.isBlank()) return "failed: khali command"
        danger(cmd)?.let { note("🚫 BLOCK: ${cmd.take(80)}"); return "blocked: safety ke liye ye command nahi chalaungi ($it). User ko batao ki ye khud Termux mein chalana pade to chalaye." }
        val id = TermuxBus.ids.incrementAndGet()
        val i = Intent().setClassName(PKG, "com.termux.app.RunCommandService").setAction("com.termux.RUN_COMMAND")
            .putExtra("com.termux.RUN_COMMAND_PATH", BASH)
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", if (visible) "$cmd; echo; echo '[Divya: command pura hua]'; exec bash" else cmd))
            .putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND", !visible)
        if (visible) i.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
        else {
            val pi = PendingIntent.getBroadcast(c, id, Intent(c, TermuxResultReceiver::class.java).setAction("com.divya.ai.TERMUX_RESULT").putExtra("divya_id", id),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            i.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pi)
            TermuxBus.latches[id] = CountDownLatch(1)
        }
        note("▶ ${cmd.take(120)}")
        try { c.startForegroundService(i) }
        catch (e: SecurityException) { return "failed: permission nahi mili / Termux mein allow-external-apps=true set nahi hai. Termux Setup screen dekho." }
        catch (e: Exception) { try { c.startService(i) } catch (e2: Exception) { return "failed: Termux ko command nahi bhej paaya: ${e2.message}" } }
        if (visible) return "done: Termux window mein command type karke chala diya. Output dekhna ho to read_screen ya look_at_screen chalao."
        val l = TermuxBus.latches[id]!!
        val ok = try { l.await(waitSec.coerceIn(3, 120).toLong(), TimeUnit.SECONDS) } catch (_: InterruptedException) { false }
        TermuxBus.latches.remove(id)
        val r = TermuxBus.results.remove(id)
        if (!ok || r == null) { note("⏳ result nahi aaya: ${cmd.take(60)}"); return "timeout: ${waitSec}s mein result nahi aaya (command abhi chal raha ho sakta hai, ya allow-external-apps=true set nahi hai)." }
        if (r.errMsg.contains("allow-external-apps", true) || r.err.contains("allow-external-apps", true) || (r.errMsg.contains("RunCommandService", true) && r.code == -1)) {
            note("⚠ Termux mein allow-external-apps=true set nahi hai")
            return "failed: Termux ne command rok di kyunki usme allow-external-apps=true set nahi hai. User ko bolo: Termux kholke ye ek command chalaye:  echo 'allow-external-apps=true' >> ~/.termux/termux.properties  phir Termux ko poora band karke (recent apps se hata ke) dobara khole. Uske baad main command chala sakti hoon."
        }
        val s = StringBuilder("exit=${r.code}")
        if (r.out.isNotBlank()) s.append("\nstdout:\n").append(r.out.trim().takeLast(1500))
        if (r.err.isNotBlank()) s.append("\nstderr:\n").append(r.err.trim().takeLast(800))
        if (r.errMsg.isNotBlank()) s.append("\nerror: ").append(r.errMsg)
        note("✔ " + s.toString().replace('\n', ' '))
        return (if (r.code == 0) "done: " else "failed: ") + s
    }

    private fun okPath(p: String) = p.isNotBlank() && !p.contains("..") && Regex("[A-Za-z0-9._/~-]+").matches(p)

    /** File Termux ke home mein likho (base64 se, koi quoting problem nahi). path home ke andar hi hona chahiye. */
    fun writeFile(c: Context, path: String, content: String): String {
        val p = path.trim().removePrefix("~/").removePrefix("$HOME/").trimStart('/')
        if (!okPath(p)) return "failed: path galat hai ('..' ya ajeeb akshar nahi chalenge): $path"
        if (content.length > 120_000) return "failed: file bahut badi hai (120KB se kam rakho)"
        val b64 = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
        val r = run(c, "mkdir -p \"\$(dirname \"\$HOME/$p\")\" && printf %s '$b64' | base64 -d > \"\$HOME/$p\" && echo saved:$p", false, 20)
        return if (r.startsWith("done")) "done: Termux mein ~/$p ban gayi" else r
    }

    /** Task ke liye code likhwao, Termux ke ~/divya-code mein rakho, chaaho to chalao. */
    fun codeAndRun(c: Context, task: String, filename: String, runIt: Boolean): String {
    try {
        gate(c)?.let { return "failed: $it" }
        BgTasks.stage("Code likh rahi hoon…")
        val (files, how) = CodeGen.genFiles(c, task, filename)
        if (files.isEmpty()) return "failed: model ne koi file nahi di"
        BgTasks.stage("Termux mein file save…")
        val saved = mutableListOf<String>()
        for ((p, body) in files) {
            BgTasks.feed(body)
            val r = writeFile(c, "divya-code/$p", body)
            if (!r.startsWith("done")) return r
            saved += p
        }
        val main = saved.firstOrNull { it.endsWith(".py") || it.endsWith(".sh") || it.endsWith(".js") } ?: saved.first()
        var out = "done: Termux mein ~/divya-code/ mein ${saved.joinToString()} bani."
        if (runIt) {
            val runner = when { main.endsWith(".py") -> "python"; main.endsWith(".sh") -> "bash"; main.endsWith(".js") -> "node"; else -> "" }
            if (runner.isNotEmpty()) {
                BgTasks.stage("Chala ke dekh rahi hoon…")
                out += " Chalane ka natija: " + run(c, "cd ~/divya-code && $runner $main", false, 40)
            } else out += " (is file type ko auto-run nahi kiya)"
        }
        return out + " Kaise chalana: $how"
    } catch (e: Exception) { return "failed: ${e.message}" }
    }

    private val tunnelUrl by lazy { Regex("https://[a-z0-9-]+\\.trycloudflare\\.com") }

    /** Netlify token ke bina: project Termux mein copy -> python http.server -> cloudflared quick tunnel -> public link (jab tak Termux chal raha hai). */
    fun publishTunnel(c: Context, project: String): String {
    try {
        gate(c)?.let { return "failed: $it" }
        val p = CodeStore.resolve(c, project) ?: return "failed: project nahi mila. list_projects chalao."
        if ("index.html" !in CodeStore.files(c, p)) return "failed: '$p' mein index.html nahi hai."
        BgTasks.stage("Site Termux mein copy ho rahi hai…")
        for (f in CodeStore.files(c, p)) {
            if (f.equals("README.md", true) || f.equals("DEPLOY.md", true)) continue
            val r = writeFile(c, "divya-sites/$p/$f", CodeStore.read(c, p, f) ?: continue)
            if (!r.startsWith("done")) return r
        }
        BgTasks.stage("Public link ban rahi hai…")
        val script = """
            P=§HOME/divya-sites
            command -v python >/dev/null || DEBIAN_FRONTEND=noninteractive pkg install -y python
            command -v cloudflared >/dev/null || DEBIAN_FRONTEND=noninteractive pkg install -y cloudflared
            pkill -f "http.server 8099" 2>/dev/null
            pkill -x cloudflared 2>/dev/null
            cd §P/$p || exit 1
            nohup python -m http.server 8099 >§P/http.log 2>&1 &
            nohup cloudflared tunnel --url http://localhost:8099 >§P/tunnel.log 2>&1 &
            for i in §(seq 1 45); do
              u=§(grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' §P/tunnel.log | head -1)
              if [ -n "§u" ]; then echo "§u"; exit 0; fi
              sleep 1
            done
            echo NO_URL; tail -5 §P/tunnel.log
        """.trimIndent().replace("§", "$")
        val r = run(c, script, false, 110)
        val u = tunnelUrl.find(r)?.value
        return if (u != null) "done: $u (temporary public link; tab tak chalega jab tak Termux/phone on hai. Ye exact link user ko batao.)"
        else "failed: link nahi bana (pkg install cloudflared / internet check karo). Detail: ${r.take(200)}"
    } catch (e: Exception) { return "failed: ${e.message}" }
    }

    fun open(c: Context): Boolean {
        val i = c.packageManager.getLaunchIntentForPackage(PKG) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { (DivyaService.instance ?: c).startActivity(i); true } catch (_: Exception) { false }
    }
}

private const val SETUP_CMD = "mkdir -p ~/.termux && grep -q allow-external-apps ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps=true' >> ~/.termux/termux.properties; termux-reload-settings; pkg install -y python nodejs git"

@Composable
fun TermuxScreen(th: UiTheme, prefs: SharedPreferences) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    var tick by remember { mutableStateOf(0) }
    var on by remember { mutableStateOf(prefs.getBoolean("termuxOn", false)) }
    var cmd by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var out by remember { mutableStateOf("") }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val inst = remember(tick) { try { Termux.installed(ctx) } catch (_: Throwable) { false } }
    val perm = remember(tick) { try { Termux.hasPerm(ctx) } catch (_: Throwable) { false } }
    fun runBg(c: String, visible: Boolean) {
        if (busy) return
        busy = true; out = "chal raha hai…"
        Thread {
            val r = Termux.run(ctx, c, visible, 40)
            Handler(Looper.getMainLooper()).post { out = r; busy = false }
        }.start()
    }
    CyPage("TERMUX CONTROL") {
        CyCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Divya ko Termux chalane do", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(on, { on = it; prefs.edit().putBoolean("termuxOn", it).apply() })
            }
            Text("ON hone par Divya aawaaz se bole gaye kaam ke liye Termux mein command chala sakti hai, code likh ke save/run kar sakti hai, aur site ka temporary public link bana sakti hai. Home/storage delete, format, shutdown, curl|sh aur SMS/call/contacts wali commands block hain.",
                color = Color(0xFF8FB8C0), fontSize = 11.sp)
        }
        CyCard {
            Text("SETUP (ek baar)", color = CyCyan, fontWeight = FontWeight.Bold)
            Text(if (inst) "✅ 1. Termux install hai" else "❌ 1. Termux install nahi (F-Droid ya GitHub se lo, Play Store wala nahi chalega)", color = Color.White, fontSize = 13.sp)
            if (!inst) OutlinedButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.termux/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Termux download page", color = CyCyan) }
            Text("2. Termux kholke ye ek command paste karo (allow-external-apps on karta hai + python/node/git lagata hai):", color = Color.White, fontSize = 13.sp)
            Text(SETUP_CMD, color = Color(0xFF7CFFB2), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ clip.setText(AnnotatedString(SETUP_CMD)); Toast.makeText(ctx, "Copy ho gaya", Toast.LENGTH_SHORT).show() }) { Text("Copy command", color = CyCyan, fontSize = 12.sp) }
                OutlinedButton({ if (!Termux.open(ctx)) Toast.makeText(ctx, "Termux nahi khula", Toast.LENGTH_SHORT).show() }) { Text("Termux kholo", color = CyCyan, fontSize = 12.sp) }
            }
            Text(if (perm) "✅ 3. 'Run commands in Termux' permission mili hui hai" else "❌ 3. Permission baaki hai", color = Color.White, fontSize = 13.sp)
            if (!perm) Button({ permLauncher.launch(Termux.PERM) }, colors = ButtonDefaults.buttonColors(containerColor = CyCyan)) { Text("Permission do", color = CyDeep) }
            if (!perm) Text("Agar dialog na aaye: Android Settings > Apps > Divya AI > Permissions > Additional permissions > Run commands in Termux.", color = Color(0xFF8FB8C0), fontSize = 10.sp)
            Button({ tick++; runBg("echo Divya connected && uname -m && python --version 2>&1 | head -1", false) }, enabled = on && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = CyCyan)) { Text("🔌 Connection test", color = CyDeep) }
            if (!on) Text("Test ke liye upar ka switch ON karo.", color = Color(0xFF8FB8C0), fontSize = 10.sp)
        }
        CyCard {
            Text("COMMAND CHALAO (khud)", color = CyCyan, fontWeight = FontWeight.Bold)
            OutlinedTextField(cmd, { cmd = it }, Modifier.fillMaxWidth(), label = { Text("jaise: ls -la") }, colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = CyCyan, unfocusedBorderColor = CyCyan.copy(alpha = 0.4f),
                focusedLabelColor = CyCyan, unfocusedLabelColor = Color(0xFF8FB8C0), cursorColor = CyCyan))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ runBg(cmd, false) }, enabled = on && !busy && cmd.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = CyCyan)) { Text("Background", color = CyDeep, fontSize = 12.sp) }
                OutlinedButton({ runBg(cmd, true) }, enabled = on && !busy && cmd.isNotBlank()) { Text("Termux window mein", color = CyCyan, fontSize = 12.sp) }
            }
            if (out.isNotBlank()) Text(out, color = Color(0xFF7CFFB2), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        CyCard {
            Text("PICHHLE KAAM", color = CyCyan, fontWeight = FontWeight.Bold)
            if (Termux.log.isEmpty()) Text("Abhi koi command nahi chali.", color = Color(0xFF8FB8C0), fontSize = 12.sp, fontStyle = FontStyle.Italic)
            Termux.log.forEach { Text(it, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
        }
        Text("Bolo: \"Divya, Termux mein ek python script likh ke chala\" / \"ls chalao\" / \"meri site ka public link banao\".", color = th.text.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}
