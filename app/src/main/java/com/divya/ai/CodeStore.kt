package com.divya.ai

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Voice tool -> UI: "preview_website" Divya ne bola to Preview screen khulti hai. */
object CodeBus {
    var previewProject by mutableStateOf<String?>(null)
    var previewTick by mutableStateOf(0)
}

/**
 * Coding / Website projects ka surakshit folder: app ka private storage (filesDir/projects).
 * Divya sirf yahin files bana/edit kar sakti hai - phone ke baaki folders ko nahi chhoti.
 */
object CodeStore {
    private val allowedExt = setOf("html", "css", "js", "json", "md", "txt", "py", "sh", "svg", "xml",
        "kt", "kts", "java", "gradle", "properties", "yml", "yaml", "toml", "ini", "cfg", "env", "example", "gitignore",
        "c", "cpp", "h", "hpp", "cs", "go", "rs", "php", "rb", "sql", "csv", "ts", "tsx", "jsx", "mjs", "vue", "svelte", "dart", "swift", "bat", "ps1", "lock", "pro", "cmake")
    private val allowedNames = setOf("dockerfile", "makefile", "license", "procfile", "gradlew", "jitpack")
    private const val MAX_FILE = 1_000_000
    private const val MAX_FILES = 500

    fun root(c: Context): File = File(c.filesDir, "projects").apply { mkdirs() }

    fun safeName(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9_-]+"), "-").trim('-').take(40).ifBlank { "project" }

    /** Naam se existing project dhundo (exact, phir contains). */
    fun resolve(c: Context, name: String): String? {
        val all = listProjects(c); val n = safeName(name)
        return all.firstOrNull { it == n } ?: all.firstOrNull { it.contains(n) || n.contains(it) } ?: if (name.isBlank()) all.firstOrNull() else null
    }

    fun listProjects(c: Context): List<String> =
        (root(c).listFiles() ?: emptyArray()).filter { it.isDirectory }.sortedByDescending { it.lastModified() }.map { it.name }

    private fun fileIn(c: Context, project: String, path: String): File? {
        val base = File(root(c), safeName(project)).canonicalFile
        val f = File(base, path.trim().trimStart('/')).canonicalFile
        // path traversal (../) rok
        return if (f.path.startsWith(base.path + File.separator)) f else null
    }

    fun files(c: Context, project: String): List<String> {
        val base = File(root(c), safeName(project))
        return base.walkTopDown().filter { it.isFile }.map { it.relativeTo(base).path }.sorted().toList()
    }

    fun read(c: Context, project: String, path: String): String? =
        fileIn(c, project, path)?.takeIf { it.isFile }?.readText()

    /** null = ho gaya, warna error ka karan. */
    fun write(c: Context, project: String, path: String, content: String): String? {
        val ext = path.substringAfterLast('.', "").lowercase()
        val base = path.substringAfterLast('/').lowercase()
        if (ext !in allowedExt && base !in allowedNames) return "'$path' allowed file type nahi hai"
        if (content.length > MAX_FILE) return "'$path' bahut bada hai"
        val f = fileIn(c, project, path) ?: return "'$path' ka path galat/unsafe hai"
        if (!f.exists() && files(c, project).size >= MAX_FILES) return "project mein bahut files hain"
        f.parentFile?.mkdirs(); f.writeText(content)
        return null
    }

    fun delete(c: Context, project: String) { File(root(c), safeName(project)).deleteRecursively() }

    /** Share ke liye ZIP (cache/share/). */
    fun zip(c: Context, project: String): File {
        val out = File(File(c.cacheDir, "share").apply { mkdirs() }, safeName(project) + ".zip")
        val base = File(root(c), safeName(project))
        ZipOutputStream(out.outputStream()).use { z ->
            base.walkTopDown().filter { it.isFile }.forEach { f ->
                z.putNextEntry(ZipEntry(f.relativeTo(base).path)); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
        return out
    }

    /** Local errors dhundo: index.html na ho, ya html mein jo css/js/page link hai wo file na ho. */
    fun lint(c: Context, project: String): List<String> {
        val issues = mutableListOf<String>()
        val fs = files(c, project)
        if ("index.html" !in fs) issues += "index.html missing hai"
        for (h in fs.filter { it.endsWith(".html") }) {
            val html = read(c, project, h) ?: continue
            if (!html.contains("</html>", true)) issues += "$h adhura hai (</html> nahi mila)"
            Regex("""(?:src|href)\s*=\s*["']([^"'#?]+)["']""", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
                val ref = m.groupValues[1]
                if (!ref.startsWith("http") && !ref.startsWith("//") && !ref.startsWith("data:") && !ref.startsWith("mailto:") && !ref.startsWith("tel:") && !ref.startsWith("javascript:")) {
                    val rel = File(h).parent?.let { File(it, ref).path } ?: ref
                    val norm = File("/x/$rel").normalize().path.removePrefix("/x/")
                    if (norm !in fs) issues += "$h mein '$ref' link hai par wo file nahi hai"
                }
            }
        }
        return issues
    }
}
