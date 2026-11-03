package com.divya.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

fun camOk(c: Context) = c.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * Scanner / "Dekho" screen: camera preview + live frames (about 1 per second) Divya ko bhejta hai.
 * Divya apni awaaz mein bata deti hai ki kya dikh raha hai (cheezein, text, documents, jagah, QR ke aas-paas ka text).
 */
@Composable
fun ScanScreen(th: UiTheme, live: GeminiLive, running: Boolean, status: String, startSession: () -> Unit, ask: (String) -> Unit, close: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(camOk(ctx)) }
    var qr by remember { mutableStateOf<String?>(null) }
    val holder = remember { CamHolder() }
    var front by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(0f) }
    var ocr by remember { mutableStateOf<String?>(null) }
    var ocrTitle by remember { mutableStateOf("Text") }
    var busy by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val latin = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val devan = remember { TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()) }
    val galleryScanner = remember { BarcodeScanning.getClient() }
    DisposableEffect(Unit) { onDispose { try { latin.close(); devan.close(); galleryScanner.close() } catch (_: Exception) {} } }
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    fun copy(t: String) {
        try { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Divya scan", t)); toast("Copy ho gaya") }
        catch (e: Exception) { toast("Copy nahi hua: ${e.message}") }
    }
    /** English + Hindi dono padho, jo zyada text de wahi rakho. */
    fun runOcr(img: InputImage, title: String, imgPath: String = "", done: (String) -> Unit = {}) {
        busy = true
        var a: String? = null; var b: String? = null
        fun finish() {
            if (a == null || b == null) return
            busy = false
            val t = (if ((b ?: "").length > (a ?: "").length) b else a)?.trim().orEmpty()
            if (t.isBlank()) { toast("Koi text nahi mila. Camera steady rakho, roshni badhao ya paas jao."); ocr = null }
            else { ocr = t; ocrTitle = title; ScanStore.add(ctx, title, t, imgPath); done(t) }
        }
        latin.process(img).addOnSuccessListener { a = it.text }.addOnFailureListener { a = "" }.addOnCompleteListener { finish() }
        devan.process(img).addOnSuccessListener { b = it.text }.addOnFailureListener { b = "" }.addOnCompleteListener { finish() }
    }
    fun scanFrameOcr() {
        val bm = holder.bmp
        if (bm == null) { toast("Camera abhi ready nahi hai, 1-2 second ruko"); return }
        runOcr(InputImage.fromBitmap(bm, 0), "Text (OCR)")
    }
    fun captureDocument() {
        val bm = holder.bmp
        if (bm == null) { toast("Camera abhi ready nahi hai, 1-2 second ruko"); return }
        var path = ""
        try {
            val dir = File(ctx.filesDir, "scans"); dir.mkdirs()
            val f = File(dir, "doc_" + System.currentTimeMillis() + ".jpg")
            f.outputStream().use { bm.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            path = f.absolutePath
        } catch (e: Exception) { toast("Photo save nahi hui: ${e.message}") }
        runOcr(InputImage.fromBitmap(bm, 0), "Document", path)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) try {
            val img = InputImage.fromFilePath(ctx, uri)
            galleryScanner.process(img).addOnSuccessListener { codes ->
                val v = codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                if (v != null) { qr = v; ScanStore.add(ctx, "QR/Barcode (image)", v) }
            }
            runOcr(img, "Image text")
        } catch (e: Exception) { toast("Image khul nahi payi: ${e.message}") }
    }
    DisposableEffect(Unit) {
        ScanBus.grab = { holder.bmp?.let { jpegOf(it, 1280, 88) } }
        onDispose { ScanBus.grab = null }
    }
    LaunchedEffect(zoom) { holder.zoom = zoom; try { holder.camera?.cameraControl?.setLinearZoom(zoom) } catch (_: Exception) {} }
    LaunchedEffect(torch) { holder.torch = torch; try { if (!front) holder.camera?.cameraControl?.enableTorch(torch) } catch (_: Exception) {} }
    // Naya frame Divya ko bhejo + sawal poochho (purana text dohrane se bachne ke liye)
    fun askFresh(q: String) {
        val b = holder.bmp
        if (b != null && live.ready) live.sendFrame(jpegOf(b, 1280, 88))
        ask(q + " (naya frame bheja hai; sirf isi se jawab do, purana dohrao mat)")
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    // Screen khulte hi Divya ka session shuru karo (agar chal nahi raha)
    LaunchedEffect(granted) { if (granted && !live.running) startSession() }

    if (showHistory) ScanHistoryDialog({ showHistory = false }) { copy(it) }
    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        detectTransformGestures { _, _, z, _ -> zoom = (zoom + (z - 1f) * 0.6f).coerceIn(0f, 1f) }
    }) {
        if (granted) {
            CameraPreview(live, front, holder) { v -> if (v != qr) { qr = v; ScanStore.add(ctx, "QR/Barcode", v) } }
            ScanHud(running, th.accent, busy)
        } else {
            Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("Divya ko dekhne ke liye Camera permission chahiye", color = th.text, textAlign = TextAlign.Center, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                Btn(th, "Camera Allow karo") { launcher.launch(Manifest.permission.CAMERA) }
            }
        }
        // Upar: status
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.QrCodeScanner, "Scanner", tint = th.accent)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Divya Scanner v2.0", color = Color.White, fontWeight = FontWeight.Bold)
                Text(if (running) "👁️ Divya dekh rahi hai • $status" else "Connect ho raha hai… ($status)", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
            }
            IconButton({ showHistory = true }) { Icon(Icons.Filled.History, "Scan history", tint = Color.White) }
            IconButton(close) { Icon(Icons.Filled.Close, "Band karo", tint = Color.White) }
        }
        // Neeche: quick sawal
        if (granted) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            qr?.let { v ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, th.accent, RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("✅ QR / Code mila:", color = th.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(v.take(300), color = Color.White, fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (v.startsWith("http", true)) ScanChip(th, "Kholo", Modifier.weight(1f)) {
                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                        }
                        ScanChip(th, "Copy", Modifier.weight(1f)) { copy(v) }
                        ScanChip(th, "Divya ko batao", Modifier.weight(1f)) {
                            askFresh("Camera ne ye QR/barcode padha: " + v.take(500) + " . Iska matlab batao aur ye safe lagta hai ya nahi?")
                        }
                        ScanChip(th, "Band", Modifier.weight(1f)) { qr = null }
                    }
                }
            }
            if (busy) Text("⏳ Text padh rahi hoon…", color = Color.White, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(10.dp))
            ocr?.let { t ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.8f))
                    .border(1.dp, th.accent, RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📄 $ocrTitle mila:", color = th.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Column(Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState())) {
                        Text(t, color = Color.White, fontSize = 13.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScanChip(th, "Copy", Modifier.weight(1f)) { copy(t) }
                        ScanChip(th, "Divya ko batao", Modifier.weight(1f)) {
                            ask("Camera/image se ye text mila: " + t.take(1500) + " . Isse padhkar sunao aur matlab batao.")
                        }
                        ScanChip(th, "Band", Modifier.weight(1f)) { ocr = null }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScanChip(th, "OCR", Modifier.weight(1f)) { scanFrameOcr() }
                ScanChip(th, "Document", Modifier.weight(1f)) { captureDocument() }
                ScanChip(th, "Gallery", Modifier.weight(1f)) { pickImage.launch("image/*") }
            }
            val chips = listOf(
                "Ye kya hai?" to "Camera mein jo dikh raha hai use dekho aur batao ye kya hai.",
                "Text padho" to "Camera mein jo text ya document abhi dikh raha hai use padhkar sunao.",
                "QR / Code" to "Camera mein jo QR code ya barcode dikh raha hai, uske aas-paas ka text padho aur batao kya likha hai.",
                "Samjhao" to "Jo dikh raha hai use asaan Hinglish mein samjhao."
            )
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.55f)), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ front = !front; if (front) torch = false }) { Icon(Icons.Filled.FlipCameraAndroid, "Front/Back camera", tint = Color.White) }
                IconButton({ if (!front) torch = !torch }) { Icon(if (torch) Icons.Filled.FlashOn else Icons.Filled.FlashOff, "Torch", tint = if (torch) th.accent else Color.White) }
                Text("🔍", fontSize = 14.sp, color = Color.White)
                Slider(zoom, { zoom = it }, Modifier.weight(1f).padding(horizontal = 8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.take(2).forEach { (l, q) -> ScanChip(th, l, Modifier.weight(1f)) { askFresh(q) } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.drop(2).forEach { (l, q) -> ScanChip(th, l, Modifier.weight(1f)) { askFresh(q) } }
            }
            Text("Do ungli se pinch karke zoom karo • Bolo bhi sakte ho: “ye kya hai?”, “ye padh do”", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(6.dp))
        }
    }
}

/** Camera ke upar HUD: ghoomta ring + corner brackets + LIVE/STANDBY label. Touch nahi rokta. */
@Composable
private fun ScanHud(running: Boolean, accent: Color, busy: Boolean) {
    val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "hud")
    val rot by tr.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(if (running || busy) 3500 else 9000, easing = androidx.compose.animation.core.LinearEasing)), label = "rot")
    val pulse by tr.animateFloat(0.5f, 1f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "p")
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val c = androidx.compose.ui.geometry.Offset(size.width / 2, size.height * 0.42f)
            val r = size.minDimension * 0.26f
            val col = accent.copy(alpha = 0.55f)
            // ghoomti dashed ring
            rotate(rot, c) {
                drawCircle(col, radius = r, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(18f, 14f))))
            }
            drawCircle(accent.copy(alpha = 0.25f * pulse), radius = r * 0.72f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
            // corner brackets
            val m = 28.dp.toPx(); val l = 34.dp.toPx(); val w = 3.dp.toPx(); val k = accent.copy(alpha = 0.8f)
            val top = size.height * 0.16f; val bot = size.height * 0.68f
            fun br(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(k, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x + dx * l, y), w)
                drawLine(k, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x, y + dy * l), w)
            }
            br(m, top, 1f, 1f); br(size.width - m, top, -1f, 1f); br(m, bot, 1f, -1f); br(size.width - m, bot, -1f, -1f)
        }
        Text(if (busy) "● SCANNING…" else if (running) "● LIVE – Divya dekh rahi hai" else "○ STANDBY – mic dabao, Divya ko aankhein do",
            color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 130.dp)
                .clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun ScanHistoryDialog(onClose: () -> Unit, onCopy: (String) -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(ScanStore.list(ctx)) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Scan history") },
        text = {
            if (items.isEmpty()) Text("Abhi koi scan save nahi hai.")
            else Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { it0 ->
                    Column {
                        Text(it0.type + " • " + ScanStore.whenText(it0.ts), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(it0.text.take(300), fontSize = 13.sp)
                        TextButton({ onCopy(it0.text) }) { Text("Copy") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Band") } },
        dismissButton = { if (items.isNotEmpty()) TextButton({ ScanStore.clear(ctx); items = emptyList() }) { Text("Sab hatao") } }
    )
}

@Composable
private fun ScanChip(th: UiTheme, label: String, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick, modifier.border(1.dp, th.accent, RoundedCornerShape(22.dp)), shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f))) { Text(label, color = Color.White, fontSize = 13.sp) }
}

/** Divya ke tool (look_camera) ko ABHI ka tez frame dene ke liye. */
object ScanBus { @Volatile var grab: (() -> ByteArray?)? = null }

class CamHolder {
    @Volatile var bmp: Bitmap? = null
    var camera: Camera? = null
    var provider: ProcessCameraProvider? = null
    var boundFront: Boolean? = null
    var front = false
    var zoom = 0f
    var torch = false
    var rebind: (() -> Unit)? = null
}

private fun jpegOf(b: Bitmap, maxSide: Int, q: Int): ByteArray {
    val sc = maxSide.toFloat() / maxOf(b.width, b.height)
    val s = if (sc < 1f) Bitmap.createScaledBitmap(b, (b.width * sc).toInt(), (b.height * sc).toInt(), true) else b
    val o = ByteArrayOutputStream(); s.compress(Bitmap.CompressFormat.JPEG, q, o); return o.toByteArray()
}

@Composable
private fun CameraPreview(live: GeminiLive, front: Boolean, holder: CamHolder, onQr: (String) -> Unit) {
    val owner = LocalLifecycleOwner.current
    val exec = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    DisposableEffect(Unit) {
        onDispose {
            holder.rebind = null
            try { holder.provider?.unbindAll() } catch (_: Exception) {}
            holder.camera = null; holder.boundFront = null
            exec.shutdown(); try { scanner.close() } catch (_: Exception) {}
        }
    }
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
        val pv = PreviewView(c).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        var lastScan = 0L
        var lastSend = 0L
        holder.rebind = rebind@{
            val provider = holder.provider ?: return@rebind
            if (holder.boundFront == holder.front) return@rebind
            try {
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(exec) { img ->
                    try {
                        val now = System.currentTimeMillis()
                        if (now - lastScan >= 600) {
                            lastScan = now
                            var bmp: Bitmap = img.toBitmap()
                            val rot = img.imageInfo.rotationDegrees
                            if (rot != 0) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
                            holder.bmp = bmp
                            // QR / barcode decode (poore resolution par, phone par hi)
                            scanner.process(InputImage.fromBitmap(bmp, 0)).addOnSuccessListener { codes ->
                                val v = codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                if (v != null) onQr(v)
                            }
                            // Divya ko live frame (tez, 1024px)
                            if (live.running && live.ready && now - lastSend >= 1000) {
                                lastSend = now
                                live.sendFrame(jpegOf(bmp, 1024, 80))
                            }
                        }
                    } catch (_: Exception) {
                    } finally { img.close() }
                }
                provider.unbindAll()
                val sel = if (holder.front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                val cam = provider.bindToLifecycle(owner, sel, preview, analysis)
                holder.camera = cam; holder.boundFront = holder.front
                try { cam.cameraControl.setLinearZoom(holder.zoom) } catch (_: Exception) {}
                try { if (!holder.front) cam.cameraControl.enableTorch(holder.torch) } catch (_: Exception) {}
            } catch (_: Exception) {}
        }
        val fut = ProcessCameraProvider.getInstance(c)
        fut.addListener({
            try { holder.provider = fut.get(); holder.rebind?.invoke() } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(c))
        pv
    }, update = { holder.front = front; holder.rebind?.invoke() })
}
