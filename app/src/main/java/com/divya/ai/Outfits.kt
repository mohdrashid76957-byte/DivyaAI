package com.divya.ai

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * Divya ka outfit / look.
 *  - Built-in looks: app ki apni images (drawable), koi bahar ki art nahi.
 *  - User ki images: gallery se chuni hui, sirf is phone ke andar save hoti hain.
 * Koi look na chuna ho to asli avatar dikhta hai.
 */
object Outfits {
    private const val DIR = "outfits"
    private const val PREF_KEY = "outfitSel"
    private const val RES = "res:"
    private val EXT = setOf("jpg", "jpeg", "png", "webp")

    /** Divya ki apni built-in looks. */
    val BUILTIN: IntArray = intArrayOf(
        R.drawable.divya_av_3d, R.drawable.divya_av_anime, R.drawable.divya_av_blue,
        R.drawable.divya_av_gold, R.drawable.divya_av_pink, R.drawable.divya_av_3d_happy,
        R.drawable.divya_av_anime_happy, R.drawable.divya_ice, R.drawable.divya_hero, R.drawable.divya_full
    )

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

    fun select(c: Context, value: String?) {
        val e = c.getSharedPreferences("divya", 0).edit()
        if (value == null) e.remove(PREF_KEY) else e.putString(PREF_KEY, value)
        e.apply()
    }

    fun selectBuiltin(c: Context, resId: Int) = select(c, RES + resId)

    fun delete(c: Context, f: File) {
        f.delete()
        if (selected(c) == f.name) select(c, null)
    }

    /** Built-in look chuna hai to uska drawable id, warna null. */
    fun selectedRes(c: Context): Int? =
        selected(c)?.takeIf { it.startsWith(RES) }?.removePrefix(RES)?.toIntOrNull()

    /** User ki gallery image chuni hai to bitmap (half size), warna null. */
    fun loadSelected(c: Context): ImageBitmap? {
        val n = selected(c) ?: return null
        if (n.startsWith(RES)) return null
        val f = File(dir(c), n)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap()
    }
}
