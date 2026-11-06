package com.divya.ai

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * v2.22.4 - ML Kit: image analysis (objects + faces, model app ke saath bundled = offline)
 * aur translation (language model pehli baar download hota hai, phir offline).
 * Ye functions background thread par chalne chahiye (OfflineBrain.exec wahi hai).
 */
object MlTools {
    val LANGS: Map<String, String> = mapOf(
        "hindi" to TranslateLanguage.HINDI, "english" to TranslateLanguage.ENGLISH, "urdu" to TranslateLanguage.URDU,
        "bengali" to TranslateLanguage.BENGALI, "tamil" to TranslateLanguage.TAMIL, "telugu" to TranslateLanguage.TELUGU,
        "marathi" to TranslateLanguage.MARATHI, "gujarati" to TranslateLanguage.GUJARATI, "kannada" to TranslateLanguage.KANNADA,
        "french" to TranslateLanguage.FRENCH, "german" to TranslateLanguage.GERMAN, "spanish" to TranslateLanguage.SPANISH,
        "arabic" to TranslateLanguage.ARABIC, "chinese" to TranslateLanguage.CHINESE, "japanese" to TranslateLanguage.JAPANESE,
        "korean" to TranslateLanguage.KOREAN, "russian" to TranslateLanguage.RUSSIAN, "portuguese" to TranslateLanguage.PORTUGUESE,
        "italian" to TranslateLanguage.ITALIAN, "turkish" to TranslateLanguage.TURKISH, "indonesian" to TranslateLanguage.INDONESIAN
    )

    fun translate(text: String, targetName: String): String {
        val tgt = LANGS[targetName] ?: return "Ye language abhi supported nahi hai."
        val hasDev = text.any { it in '\u0900'..'\u097F' }
        val src = if (hasDev) TranslateLanguage.HINDI else TranslateLanguage.ENGLISH
        if (src == tgt) return "Source aur target language ek hi hain."
        val tr = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(tgt).build())
        return try {
            Tasks.await(tr.downloadModelIfNeeded(DownloadConditions.Builder().build()), 90, TimeUnit.SECONDS)
            Tasks.await(tr.translate(text), 20, TimeUnit.SECONDS)
        } catch (e: Exception) {
            "Is language ka model abhi phone mein nahi hai. Ek baar internet on karke dobara bolo, phir ye offline chalega."
        } finally { try { tr.close() } catch (_: Exception) {} }
    }

    fun analyzeLastScan(c: Context): String {
        val item = ScanStore.list(c).firstOrNull { it.img.isNotBlank() && File(it.img).exists() }
            ?: return "Koi scan ki hui photo nahi mili. Pehle Scanner mein Document scan karo ya Gallery se image scan karo."
        return try {
            val img = InputImage.fromFilePath(c, Uri.fromFile(File(item.img)))
            val labels = Tasks.await(ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS).process(img), 25, TimeUnit.SECONDS)
            val faces = Tasks.await(FaceDetection.getClient(
                FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build()).process(img), 25, TimeUnit.SECONDS)
            val top = labels.sortedByDescending { it.confidence }.take(5).joinToString(", ") { it.text + " " + (it.confidence * 100).toInt() + " percent" }
            val txt = item.text.trim().take(150)
            (if (top.isBlank()) "Koi pakki cheez nahi pehchani. " else "Photo mein dikh raha hai: $top. ") +
                (if (faces.isEmpty()) "Koi chehra nahi mila. " else faces.size.toString() + " chehre mile. ") +
                (if (txt.isNotBlank()) "Likha hua text: $txt" else "")
        } catch (e: Exception) { "Image analysis nahi ho paya: " + (e.message ?: "").take(80) }
    }
}
