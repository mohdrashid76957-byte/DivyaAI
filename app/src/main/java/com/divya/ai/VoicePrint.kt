package com.divya.ai

import android.content.SharedPreferences
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.*

/** Basic (experimental) voice match: MFCC ki awaaz-chhaap (mean+std) se. Koi AI model/internet nahi. */
class VpModel(val m: DoubleArray, val s: DoubleArray, val thr: Double)

object VoicePrint {
    const val SR = 16000
    private const val FRAME = 400
    private const val HOP = 160
    private const val NFFT = 512
    private const val NMEL = 26
    private const val NCEP = 14          // c0..c13, c0 chhod dete hain -> 13 coeff
    /** Ek match window = 60 bolne wale frames (~0.6 sec). Enrollment aur gate dono isi lambai par compare karte hain. */
    const val WIN = 60
    /** Itne bolne wale frames se kam ho to awaaz pakki nahi hoti. */
    const val MIN_VOICED = 30

    private val window = DoubleArray(FRAME) { 0.54 - 0.46 * cos(2 * PI * it / (FRAME - 1)) }
    private val melBank: Array<DoubleArray> = buildMel()
    private val dct: Array<DoubleArray> = Array(NCEP) { k -> DoubleArray(NMEL) { n -> cos(PI * k * (n + 0.5) / NMEL) } }

    private fun hz2mel(h: Double) = 2595.0 * log10(1.0 + h / 700.0)
    private fun mel2hz(m: Double) = 700.0 * (10.0.pow(m / 2595.0) - 1.0)

    private fun buildMel(): Array<DoubleArray> {
        val lo = hz2mel(100.0); val hi = hz2mel(6500.0)
        val pts = DoubleArray(NMEL + 2) { mel2hz(lo + (hi - lo) * it / (NMEL + 1)) }
        val bins = IntArray(NMEL + 2) { floor((NFFT + 1) * pts[it] / SR).toInt() }
        return Array(NMEL) { m ->
            val f = DoubleArray(NFFT / 2 + 1)
            for (k in bins[m] until bins[m + 1]) f[k] = (k - bins[m]).toDouble() / max(1, bins[m + 1] - bins[m])
            for (k in bins[m + 1] until bins[m + 2]) f[k] = (bins[m + 2] - k).toDouble() / max(1, bins[m + 2] - bins[m + 1])
            f
        }
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val tr = re[i]; re[i] = re[j]; re[j] = tr; val ti = im[i]; im[i] = im[j]; im[j] = ti }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k; val b = i + k + len / 2
                    val tr = re[b] * cr - im[b] * ci
                    val ti = re[b] * ci + im[b] * cr
                    re[b] = re[a] - tr; im[b] = im[a] - ti
                    re[a] += tr; im[a] += ti
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** Sirf bolne wale frames ke MFCC (c1..c13). Khamoshi/shor wale frames hata deta hai. */
    fun voicedFrames(pcm: ShortArray): List<DoubleArray> {
        val n = pcm.size
        if (n < FRAME + HOP) return emptyList()
        val x = DoubleArray(n)
        for (i in 0 until n) x[i] = pcm[i] - (if (i > 0) 0.97 * pcm[i - 1] else 0.0)
        val cnt = (n - FRAME) / HOP + 1
        val energies = DoubleArray(cnt)
        val feats = ArrayList<DoubleArray>(cnt)
        val re = DoubleArray(NFFT); val im = DoubleArray(NFFT)
        for (f in 0 until cnt) {
            java.util.Arrays.fill(re, 0.0); java.util.Arrays.fill(im, 0.0)
            var e = 0.0
            for (i in 0 until FRAME) { val v = x[f * HOP + i] * window[i]; re[i] = v; e += v * v }
            energies[f] = e / FRAME
            fft(re, im)
            val pw = DoubleArray(NFFT / 2 + 1) { re[it] * re[it] + im[it] * im[it] }
            val mel = DoubleArray(NMEL) { m ->
                var sum = 0.0
                for (k in pw.indices) sum += melBank[m][k] * pw[k]
                ln(max(1e-10, sum))
            }
            feats.add(DoubleArray(NCEP - 1) { k -> var s = 0.0; for (q in 0 until NMEL) s += dct[k + 1][q] * mel[q]; s })
        }
        val sorted = energies.sortedArray()
        val ref = sorted[min(sorted.size - 1, (sorted.size * 0.9).toInt())]
        val out = ArrayList<DoubleArray>()
        // Relative threshold (mic gain ke hisaab se badalta nahi). Absolute floor bahut kam, sirf khamoshi hatane ke liye.
        for (f in 0 until cnt) if (energies[f] > ref * 0.05 && energies[f] > 200.0) out.add(feats[f])
        return out
    }

    /** Frames -> ek vector (har coeff ka mean + std). */
    fun vector(fr: List<DoubleArray>): DoubleArray {
        val d = fr[0].size
        val v = DoubleArray(d * 2)
        for (j in 0 until d) {
            var m = 0.0; for (f in fr) m += f[j]; m /= fr.size
            var s = 0.0; for (f in fr) s += (f[j] - m) * (f[j] - m)
            v[j] = m; v[d + j] = sqrt(s / fr.size)
        }
        return v
    }

    /** Enrollment audio ko WIN-frame (~0.6 sec) ke overlapping tukdon mein todo, gate wali lambai jaisa. */
    fun chunks(pcm: ShortArray): List<DoubleArray> {
        val fr = voicedFrames(pcm)
        val out = ArrayList<DoubleArray>()
        var i = 0
        while (i + WIN <= fr.size) { out.add(vector(fr.subList(i, i + WIN))); i += WIN / 2 }
        return out
    }

    private fun fit(c: List<DoubleArray>): Pair<DoubleArray, DoubleArray> {
        val d = c[0].size
        val m = DoubleArray(d) { j -> c.sumOf { it[j] } / c.size }
        val s = DoubleArray(d) { j -> max(sqrt(c.sumOf { (it[j] - m[j]) * (it[j] - m[j]) } / c.size), 0.05 * abs(m[j]) + 0.3) }
        return Pair(m, s)
    }

    /**
     * Threshold out-of-sample se: har chunk ko model se bahar rakh kar uska score nikalta hai.
     * Pehle in-sample tha, isliye naye waqt ki owner ki awaaz hamesha limit se upar jaati thi.
     */
    /**
     * Poore bolne (chahe 0.6 sec ho ya 4 sec) ka score: har WIN-window ka score nikal kar median.
     * Pehle sirf aakhri window dekhte the (ya poora 4 sec), jo enrollment se mail nahi khata tha.
     */
    fun matchScore(md: VpModel, fr: List<DoubleArray>): Double {
        if (fr.size <= WIN) return score(md, vector(fr))
        val s = ArrayList<Double>()
        var i = 0
        while (i + WIN <= fr.size) { s.add(score(md, vector(fr.subList(i, i + WIN)))); i += WIN / 2 }
        s.sort()
        return s[s.size / 2]
    }

    fun train(c: List<DoubleArray>): VpModel? {
        if (c.size < 8) return null
        val (m, s) = fit(c)
        val loo = ArrayList<Double>()
        for (i in c.indices) {
            val rest = c.filterIndexed { j, _ -> j != i }
            val (mi, si) = fit(rest)
            loo.add(score(VpModel(mi, si, 0.0), c[i]))
        }
        loo.sort()
        val p90 = loo[min(loo.size - 1, (loo.size * 0.9).toInt())]
        // Floor: normal bolne ka variation enrollment se thoda zyada hota hai, isliye limit kam na ho
        return VpModel(m, s, max(p90 * 1.25 + 0.2, 1.5))
    }

    fun score(md: VpModel, v: DoubleArray): Double {
        var t = 0.0
        for (j in v.indices) { val z = (v[j] - md.m[j]) / md.s[j]; t += z * z }
        return sqrt(t / v.size)
    }

    fun save(p: SharedPreferences, md: VpModel) {
        p.edit().putString("vp", md.m.joinToString(",") + "|" + md.s.joinToString(",") + "|" + md.thr).apply()
    }

    fun load(p: SharedPreferences): VpModel? = try {
        val parts = (p.getString("vp", null) ?: "").split("|")
        if (parts.size != 3) null else VpModel(
            parts[0].split(",").map { it.toDouble() }.toDoubleArray(),
            parts[1].split(",").map { it.toDouble() }.toDoubleArray(), parts[2].toDouble())
    } catch (e: Exception) { null }

    /** Mic se `seconds` sec record (Gemini wale mic jaisa hi source). */
    @android.annotation.SuppressLint("MissingPermission")
    fun record(seconds: Int): ShortArray? {
        MicBus.recording = true
        try { return recordInner(seconds) } finally { MicBus.recording = false }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun recordInner(seconds: Int): ShortArray? {
        val total = SR * seconds
        val minBuf = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, 8000) * 2)
        if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); return null }
        val out = ShortArray(total); var n = 0
        try {
            r.startRecording()
            while (n < total) { val k = r.read(out, n, min(1600, total - n)); if (k <= 0) break; n += k }
        } finally { try { r.stop() } catch (_: Exception) {}; r.release() }
        return if (n > SR / 2) out.copyOf(n) else null
    }
}
