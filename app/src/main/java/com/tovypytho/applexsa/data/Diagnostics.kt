package com.tovypytho.applexsa.data

import com.tovypytho.applexsa.core.Stage
import kotlin.math.ceil

data class Attempt(val stage: Stage, val code: String, val elapsed: Long, val sendMillis: Long?, val timeline: List<String> = emptyList())
class Diagnostics {
    private val attempts = ArrayDeque<Attempt>()
    private var structure = "Belum ada inspeksi layar."
    fun structure(value: String) { structure = value }
    fun record(value: Attempt) { attempts.addLast(value); while (attempts.size > 20) attempts.removeFirst() }
    fun report(): String {
        val times = attempts.mapNotNull { it.sendMillis }.sorted()
        val median = if (times.isEmpty()) null else if (times.size % 2 == 1) times[times.size / 2] else (times[times.size / 2 - 1] + times[times.size / 2]) / 2
        val p95 = times.getOrNull((ceil(times.size * .95).toInt() - 1).coerceAtLeast(0))
        return "AppLexsa • selector eksperimental • Android 15\n" +
            "Percobaan: ${attempts.size}/20; median tekan→kirim: ${median ?: "—"} ms; p95: ${p95 ?: "—"} ms\n" +
            "Gagal/perlu diperiksa: ${attempts.count { it.stage == Stage.ERROR }}\n" +
            "Ini waktu perintah UI, bukan waktu pesan diterima.\n\n" +
            attempts.joinToString("\n") { "${it.stage} ${it.code} elapsed=${it.elapsed}ms send=${it.sendMillis ?: "—"}ms\n${it.timeline.joinToString("\n")}" } +
            "\n\nStruktur terakhir (tanpa teks/deskripsi layar):\n$structure"
    }
}
