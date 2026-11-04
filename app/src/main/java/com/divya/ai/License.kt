package com.divya.ai

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.MessageDigest

/**
 * Privacy password: pehli baar install par ek baar maangta hai. Uninstall karke dobara install karo to phir maangta hai
 * (kyunki saved flag app ke saath delete ho jata hai; manifest mein allowBackup=false hai).
 * Password plain text mein store nahi, sirf salted SHA-256 hash.
 * Password badalna ho: hashOf("naya") ka result HASH mein daalo.
 */
object LicenseGate {
    private const val SALT = "divya-lic-v1|"
    private const val HASH = "3f21c8c5a53acef3e0ed2f0c582907f46bc7779e14cb2bad32235ae4333d3820"
    private const val MAX_TRIES = 5
    private const val LOCK_MS = 30_000L

    fun hashOf(s: String): String =
        MessageDigest.getInstance("SHA-256").digest((SALT + s).toByteArray()).joinToString("") { "%02x".format(it) }

    private fun p(c: Context) = c.getSharedPreferences("divya_lic", 0)

    fun isActive(c: Context): Boolean = p(c).getString("ok", null) == HASH

    fun lockLeftSec(c: Context): Int {
        val left = p(c).getLong("lockUntil", 0L) - System.currentTimeMillis()
        return if (left > 0) ((left + 999) / 1000).toInt() else 0
    }

    fun activate(c: Context, input: String): Boolean {
        if (lockLeftSec(c) > 0) return false
        val sp = p(c)
        if (hashOf(input.trim().lowercase()) == HASH) { sp.edit().putString("ok", HASH).putInt("fails", 0).apply(); return true }
        val f = sp.getInt("fails", 0) + 1
        if (f >= MAX_TRIES) sp.edit().putInt("fails", 0).putLong("lockUntil", System.currentTimeMillis() + LOCK_MS).apply()
        else sp.edit().putInt("fails", f).apply()
        return false
    }
}

@Composable
fun LicenseScreen(onActivated: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var pw by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var lock by remember { mutableStateOf(LicenseGate.lockLeftSec(ctx)) }
    val accent = Color(0xFF29B6FF)

    LaunchedEffect(lock) {
        if (lock > 0) { kotlinx.coroutines.delay(1000); lock = LicenseGate.lockLeftSec(ctx); if (lock == 0) msg = "" }
    }
    fun go() {
        if (lock > 0) return
        if (LicenseGate.activate(ctx, pw)) { onActivated(); return }
        lock = LicenseGate.lockLeftSec(ctx)
        msg = if (lock > 0) "Bahut galat koshish. $lock sec baad try karo." else "Galat password."
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(28.dp))
        Text("Privacy", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1B44)),
            border = BorderStroke(1.dp, Color(0xFF1B3A7A)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter privacy password", color = accent, fontSize = 22.sp)
                Text("Ye app private hai. Pehli baar password daalna padega, baad mein dobara nahi poochhega.", color = Color(0xFF9FB3D9), fontSize = 14.sp)
                OutlinedTextField(pw, { pw = it; msg = "" }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Password") },
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    trailingIcon = { TextButton({ show = !show }) { Text(if (show) "Hide" else "Show", color = accent) } })
                if (msg.isNotEmpty()) Text(msg, color = Color(0xFFFF6B6B), fontSize = 13.sp)
                Button({ go() }, enabled = pw.isNotBlank() && lock == 0, shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text(if (lock > 0) "WAIT ${lock}s" else "UNLOCK", color = Color.White) }
            }
        }
    }
}
