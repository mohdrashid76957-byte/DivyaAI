package com.divya.ai

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * Divya ka outfit / look: user apni image (gallery se) chunta hai, wo sirf is phone ke andar save hoti hai.
 * App ki asli drawables ko nahi chhedta. Koi outfit na chuna ho to asli avatar dikhta hai.
 */
object Outfits {
    private const val DIR = "outfits"
    private const val PREF_KEY = "outfitSel"
    private val EXT = setOf("jpg", "jpeg", "png", "webp")

    fun dir(c: Context): File = File(c.filesDir, DIR).apply { mkdirs() }

    fun list(c: Context): List<File> =
        dir(c).listFiles { f -> f.extension.lowercase() in EXT }?.sortedBy { it.name } ?: emptyList()

    /** Gallery ki image ko app ke andar copy karta hai. Return: naya file ya null. */
    fun importFrom(c: Context, uri: android.net.Uri): File? = try {
        val out = File(dir(c), "outfit_${System.currentTimeMillis()}.jpg")
        c.contentResolver.openInputStream(uri)?.use { ins -> out.outputStream().use { ins.copyTo(it) } }
        out.takeIf { it.exists() && it.length() > 0 }
    } catch (e: Exception) { null }

    fun selected(c: Context): String? = c.getSharedPreferences("divya", 0).getString(PREF_KEY, null)

    fun select(c: Context, name: String?) {
        val e = c.getSharedPreferences("divya", 0).edit()
        if (name == null) e.remove(PREF_KEY) else e.putString(PREF_KEY, name)
        e.apply()
    }

    fun delete(c: Context, f: File) {
        f.delete()
        if (selected(c) == f.name) select(c, null)
    }

    /** Chuna hua outfit (memory bachane ke liye half size). Nahi chuna / file nahi hai to null. */
    fun loadSelected(c: Context): ImageBitmap? {
        val n = selected(c) ?: return null
        val f = File(dir(c), n)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap()
    }
}
