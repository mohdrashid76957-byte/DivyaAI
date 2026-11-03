package com.divya.ai

import java.io.File
import java.util.zip.ZipInputStream

/**
 * Project ZIP ko chhote aur kaam ke context mein badalta hai.
 * Pehle har message ke saath poora ZIP (~700K chars) Gemini ko jaata tha -> bahut slow, "hi" bhi minutes leta tha.
 * Ab: file tree + README/manifest/gradle + sirf wo files jo sawal se milti hain (poori), baaki ka outline.
 */
object ZipCtx {
    class F(val path: String, val text: String)

    private var cachePath = ""
    private var cacheMod = 0L
    private var cacheFiles: List<F> = emptyList()

    private val BIN = setOf("png", "jpg", "jpeg", "gif", "webp", "ico", "keystore", "jks", "jar", "apk", "aar", "so", "zip", "class", "dex",
        "mp3", "mp4", "wav", "ogg", "ttf", "otf", "woff", "woff2", "pdf", "bin", "db", "ttc")

    private val STOP = setOf("karo", "karna", "karne", "mera", "meri", "mere", "dekho", "isme", "isko", "usko", "please", "bhai", "help",
        "file", "files", "zip", "project", "code", "upgrade", "update", "chahiye", "wala", "wali", "hai", "hain", "kaise", "kuch", "sab",
        "this", "that", "with", "from", "have", "make", "need", "want", "divya")

    private val DECL = Regex("""^\s*(?:(?:private|internal|public|protected|override|suspend|data|sealed|abstract|open|inline)\s+)*(?:fun|class|object|interface)\s+\S.*""")

    fun load(f: File): List<F> {
        if (f.absolutePath == cachePath && f.lastModified() == cacheMod && cacheFiles.isNotEmpty()) return cacheFiles
        val out = ArrayList<F>()
        try {
            ZipInputStream(f.inputStream()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        val low = e.name.lowercase()
                        val ext = low.substringAfterLast('.', "")
                        val skip = ext in BIN || low.contains("/build/") || low.contains("/.git/") || low.contains("node_modules/") ||
                            low.contains("/.gradle/") || low.contains("/.idea/")
                        if (!skip) {
                            val bytes = z.readBytes()
                            if (bytes.size <= 150_000 && !bytes.take(2000).any { it.toInt() == 0 }) out.add(F(e.name, String(bytes)))
                        }
                    }
                    e = z.nextEntry
                }
            }
        } catch (_: Exception) {}
        cachePath = f.absolutePath; cacheMod = f.lastModified(); cacheFiles = out
        return out
    }

    private fun outline(f: F): String {
        val ext = f.path.substringAfterLast('.', "").lowercase()
        val body = if (ext == "kt" || ext == "java")
            f.text.lineSequence().filter { DECL.matches(it) }.map { it.trim().take(110) }.take(45).joinToString("\n")
        else f.text.lineSequence().take(6).joinToString("\n") { it.take(110) }
        return "--- OUTLINE: ${f.path} (${f.text.length} chars) ---\n$body\n"
    }

    private fun score(f: F, q: String, words: List<String>): Int {
        var s = 0
        val low = f.path.lowercase()
        val name = low.substringAfterLast('/')
        val stem = name.substringBeforeLast('.')
        if (name.length >= 4 && q.contains(name)) s += 100
        if (stem.length >= 4 && q.contains(stem)) s += 60
        if (words.isNotEmpty()) {
            val tl = f.text.lowercase()
            for (w in words) {
                if (low.contains(w)) s += 8
                if (tl.contains(w)) s += 3
            }
        }
        return s
    }

    /** light = chhoti/casual baat (jaise "hi"): sirf file tree, taaki jawab turant aaye. */
    fun build(zip: File, query: String, budget: Int, light: Boolean): String {
        val files = load(zip)
        if (files.isEmpty()) return "[ZIP mein padhne layak text/code file nahi mili]"
        val q = query.lowercase()
        val words = q.split(Regex("[^a-z0-9_]+")).filter { it.length >= 4 && it !in STOP }.distinct().take(12)

        val sb = StringBuilder()
        sb.append("PROJECT ZIP '").append(zip.name).append("' - ").append(files.size).append(" text files. FILE TREE (path, chars):\n")
        files.forEach { sb.append("- ").append(it.path).append(" (").append(it.text.length).append(")\n") }
        if (light) {
            sb.append("\n(Sirf file tree bheja hai. Code chahiye to user file ka naam ya kaam bata dega.)")
            return sb.toString()
        }

        sb.append("\nFULL = poori file ka asli code. OUTLINE = sirf declarations. OUTLINE wali file ko kabhi andaze se dobara mat likho; ")
        sb.append("agar uska code chahiye to user se bolo ki wo file ka naam apne message mein likhe.\n")

        val full = LinkedHashSet<String>()
        // README (shuru ka hissa) + manifest + gradle hamesha
        files.firstOrNull { it.path.lowercase().endsWith("readme.md") }?.let {
            sb.append("\n=== README (pehle 5000 chars) ===\n").append(it.text.take(5000)).append("\n")
            full.add(it.path)
        }
        for (f in files) {
            val n = f.path.lowercase()
            if (full.contains(f.path)) continue
            if ((n.endsWith("androidmanifest.xml") || n.endsWith("build.gradle.kts") || n.endsWith("build.gradle")) && f.text.length <= 6000) {
                sb.append("\n=== FILE: ").append(f.path).append(" ===\n").append(f.text).append("\n"); full.add(f.path)
            }
        }
        // sawal se milti files poori (budget tak)
        val scored = files.map { it to score(it, q, words) }.filter { it.second > 0 }.sortedByDescending { it.second }
        val fullCap = (budget * 0.8).toInt()
        for ((f, _) in scored) {
            if (full.contains(f.path)) continue
            if (sb.length + f.text.length + 40 > fullCap) continue
            sb.append("\n=== FILE: ").append(f.path).append(" ===\n").append(f.text).append("\n"); full.add(f.path)
            if (full.size >= 14) break
        }
        // baaki ka outline
        for (f in files) {
            if (full.contains(f.path)) continue
            val o = outline(f)
            if (sb.length + o.length > budget) break
            sb.append(o)
        }
        return sb.toString()
    }
}
