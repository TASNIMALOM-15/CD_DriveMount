package com.drivemount.app.data.meta

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

/**
 * MP4/MOV "fast-start": moov atom ফাইলের শেষে থাকলে (ক্যামেরা/স্ক্রিন-রেকর্ডারের ভিডিও) প্লেব্যাক শুরুর আগে
 * পুরো ফাইলের শেষ অংশ লাগে, স্ট্রিমে ধীর। এখানে ভিডিও/অডিও ডেটা একটুও বদলানো ছাড়া (lossless, re-encode নেই)
 * moov-কে ftyp-এর ঠিক পরে এনে নতুন একটি ফাইল বানানো হয়; chunk-offset (stco/co64) সেই অনুযায়ী ঠিক করা হয়।
 * মূল ফাইল শুধু পড়া হয়: কখনো বদলানো বা মোছা হয় না। ফল সবসময় আলাদা নতুন ফাইল, আকার হুবহু মূলের সমান।
 */
object FastStart {
    private const val MAX_FILE = 3_800_000_000L // ৩২-বিট stco ওভারফ্লো এড়াতে (৪GB-র নিচে)
    private const val MAX_MOOV = 128 * 1024 * 1024
    private val EXT = setOf("mp4", "mov", "m4v")
    private val CONTAINERS = setOf("trak", "mdia", "minf", "stbl")

    private class Atom(val type: String, val start: Long, val size: Long, val header: Int) {
        val end get() = start + size
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("transfer_ui", Context.MODE_PRIVATE).getBoolean("faststart", true)

    fun isCandidate(name: String) = name.substringAfterLast('.', "").lowercase() in EXT

    private fun scan(raf: RandomAccessFile): List<Atom>? {
        val len = raf.length()
        val out = ArrayList<Atom>()
        var pos = 0L
        val h = ByteArray(16)
        while (pos + 8 <= len) {
            raf.seek(pos)
            raf.readFully(h, 0, 8)
            var size = u32(h, 0)
            val type = String(h, 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L) {
                if (pos + 16 > len) return null
                raf.readFully(h, 8, 8)
                size = u64(h, 8); header = 16
            } else if (size == 0L) size = len - pos
            if (size < header || pos + size > len) return null
            out += Atom(type, pos, size, header)
            pos += size
        }
        return if (pos == len && out.isNotEmpty()) out else null
    }

    private fun needs(atoms: List<Atom>): Boolean {
        val moov = atoms.indexOfFirst { it.type == "moov" }
        val mdat = atoms.indexOfFirst { it.type == "mdat" }
        return moov >= 0 && mdat >= 0 && moov > mdat
    }

    /** moov শেষে আছে কি (শুধু ছোট হেডার পড়া, দ্রুত) */
    fun needsFastStart(file: File): Boolean = runCatching {
        RandomAccessFile(file, "r").use { r -> scan(r)?.let { needs(it) } == true }
    }.getOrDefault(false)

    /** [src] থেকে fast-start কপি [dst]-তে; সফল হলে true। ব্যর্থ হলে [dst] মুছে যায় (মূল ফাইল কখনোই নয়) */
    fun remux(src: File, dst: File, isActive: () -> Boolean = { true }): Boolean {
        val ok = runCatching { doRemux(src, dst, isActive) }.getOrDefault(false)
        if (!ok) dst.delete()
        return ok
    }

    private fun doRemux(src: File, dst: File, isActive: () -> Boolean): Boolean {
        if (src.length() > MAX_FILE) return false
        dst.parentFile?.mkdirs()
        RandomAccessFile(src, "r").use { r ->
            val atoms = scan(r) ?: return false
            if (!needs(atoms)) return false
            val moov = atoms.first { it.type == "moov" }
            if (moov.size > MAX_MOOV) return false
            val insert = if (atoms[0].type == "ftyp") atoms[0].end else 0L
            val moovSize = moov.size

            val buf = ByteArray(moovSize.toInt())
            r.seek(moov.start)
            r.readFully(buf)
            val firstChunk = longArrayOf(-1L)
            if (!patch(buf, moov.header, buf.size, insert, moov.start, moovSize, firstChunk)) return false

            RandomAccessFile(dst, "rw").use { w ->
                w.setLength(0)
                copy(r, w, 0, insert, isActive)
                w.write(buf)
                copy(r, w, insert, moov.start - insert, isActive)
                copy(r, w, moov.end, r.length() - moov.end, isActive)
            }
        }
        // যাচাই: আকার সমান, moov এখন mdat-এর আগে, একই atom-গুলো (ধরন+আকার), প্রথম chunk mdat-এর ভেতরে
        RandomAccessFile(src, "r").use { a ->
            RandomAccessFile(dst, "r").use { b ->
                if (a.length() != b.length()) return false
                val sa = scan(a) ?: return false
                val sb = scan(b) ?: return false
                if (needs(sb) || sb.none { it.type == "moov" }) return false
                val sourceAtoms = sa.groupingBy { it.type to it.size }.eachCount()
                val outputAtoms = sb.groupingBy { it.type to it.size }.eachCount()
                if (sourceAtoms != outputAtoms) return false
                val mdat = sb.filter { it.type == "mdat" }
                val moov = sb.first { it.type == "moov" }
                val mb = ByteArray(moov.size.toInt())
                b.seek(moov.start); b.readFully(mb)
                val first = longArrayOf(-1L)
                // দ্বিতীয়বার "patch" না করে শুধু প্রথম chunk-offset পড়া
                readFirstChunk(mb, moov.header, mb.size, first)
                if (first[0] >= 0 && mdat.none { first[0] >= it.start && first[0] < it.end }) return false
            }
        }
        return true
    }

    private fun copy(r: RandomAccessFile, w: RandomAccessFile, from: Long, count: Long, isActive: () -> Boolean) {
        if (count <= 0) return
        val buf = ByteArray(1 shl 20)
        r.seek(from)
        var left = count
        while (left > 0) {
            if (!isActive()) throw java.util.concurrent.CancellationException()
            val n = r.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) throw java.io.EOFException()
            w.write(buf, 0, n)
            left -= n
        }
    }

    /** moov-এর ভেতরে stco/co64 খুঁজে প্রতিটি offset-এ moovSize যোগ (শুধু যেগুলো সরে যাওয়া অংশের ডেটা নির্দেশ করে) */
    private fun patch(b: ByteArray, from: Int, to: Int, insert: Long, oldMoovStart: Long, moovSize: Long, first: LongArray): Boolean {
        var p = from
        while (p + 8 <= to) {
            var size = u32(b, p).toInt()
            val type = String(b, p + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1) { if (p + 16 > to) return false; size = u64(b, p + 8).toInt(); header = 16 }
            if (size < header || p + size > to) return false
            when (type) {
                "cmov" -> return false // সংকুচিত moov: সমর্থিত নয়
                in CONTAINERS -> if (!patch(b, p + header, p + size, insert, oldMoovStart, moovSize, first)) return false
                "stco" -> {
                    val n = u32(b, p + header + 4).toInt()
                    if (p + header + 8 + n * 4L > p + size) return false
                    for (i in 0 until n) {
                        val o = p + header + 8 + i * 4
                        val v = u32(b, o)
                        if (first[0] < 0) first[0] = v
                        if (v in insert until oldMoovStart) {
                            val nv = v + moovSize
                            if (nv > 0xFFFFFFFFL) return false
                            put32(b, o, nv)
                        }
                    }
                }
                "co64" -> {
                    val n = u32(b, p + header + 4).toInt()
                    if (p + header + 8 + n * 8L > p + size) return false
                    for (i in 0 until n) {
                        val o = p + header + 8 + i * 8
                        val v = u64(b, o)
                        if (first[0] < 0) first[0] = v
                        if (v in insert until oldMoovStart) put64(b, o, v + moovSize)
                    }
                }
            }
            p += size
        }
        return true
    }

    private fun readFirstChunk(b: ByteArray, from: Int, to: Int, first: LongArray) {
        var p = from
        while (p + 8 <= to && first[0] < 0) {
            var size = u32(b, p).toInt()
            val type = String(b, p + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1) { if (p + 16 > to) return; size = u64(b, p + 8).toInt(); header = 16 }
            if (size < header || p + size > to) return
            when (type) {
                in CONTAINERS -> readFirstChunk(b, p + header, p + size, first)
                "stco" -> if (u32(b, p + header + 4) > 0) first[0] = u32(b, p + header + 8)
                "co64" -> if (u32(b, p + header + 4) > 0) first[0] = u64(b, p + header + 8)
            }
            p += size
        }
    }

    private fun u32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 255) shl 24) or ((b[o + 1].toLong() and 255) shl 16) or ((b[o + 2].toLong() and 255) shl 8) or (b[o + 3].toLong() and 255)

    private fun u64(b: ByteArray, o: Int): Long = (u32(b, o) shl 32) or u32(b, o + 4)

    private fun put32(b: ByteArray, o: Int, v: Long) {
        b[o] = (v shr 24).toByte(); b[o + 1] = (v shr 16).toByte(); b[o + 2] = (v shr 8).toByte(); b[o + 3] = v.toByte()
    }

    private fun put64(b: ByteArray, o: Int, v: Long) { put32(b, o, v shr 32); put32(b, o + 4, v and 0xFFFFFFFFL) }
}
