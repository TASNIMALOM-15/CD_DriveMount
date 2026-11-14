package com.drivemount.app.data.meta

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** একটি APK-র সাইডকার তথ্য: নাম, সাইজ (চেনার জন্য), অ্যাপের নাম, package, version ও ছোট আইকন (WebP) */
data class SidecarEntry(
    val name: String,
    val size: Long,
    val label: String,
    val pkg: String,
    val version: String,
    val icon: ByteArray,
    /** শুধু আপলোডের সময় (ছবি): বড় প্রিভিউ; প্যাকে যায় না, আলাদা ফাইল হিসেবে আপলোড হয় */
    val preview: ByteArray? = null,
)

/**
 * সাইডকারের বাইনারি ফরম্যাট (সরল, দ্রুত পড়া যায়)।
 *  - pack: একটি ফোল্ডারের সব APK-র তথ্য একটা ফাইলে (`.dmmeta/pack.dmp`), তাই ফোল্ডার-প্রতি মাত্র ১টা রিকোয়েস্ট
 *  - one: লোকাল ক্যাশে একটি APK-র তথ্য
 */
object SidecarCodec {

    private const val MAGIC_PACK = 0x444D5031 // "DMP1"
    private const val MAGIC_ONE = 0x444D4531 // "DME1"
    private const val MAX_ICON = 1 shl 20
    private const val MAX_ENTRIES = 5000

    fun encodeOne(e: SidecarEntry): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use {
            it.writeInt(MAGIC_ONE)
            writeEntry(it, e)
        }
        return bytes.toByteArray()
    }

    fun decodeOne(data: ByteArray): SidecarEntry =
        DataInputStream(ByteArrayInputStream(data)).use {
            require(it.readInt() == MAGIC_ONE) { "bad sidecar entry" }
            readEntry(it)
        }

    fun encodePack(entries: Collection<SidecarEntry>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use {
            it.writeInt(MAGIC_PACK)
            it.writeInt(entries.size)
            for (e in entries) writeEntry(it, e)
        }
        return bytes.toByteArray()
    }

    fun decodePack(data: ByteArray): List<SidecarEntry> =
        DataInputStream(ByteArrayInputStream(data)).use {
            require(it.readInt() == MAGIC_PACK) { "bad sidecar pack" }
            val n = it.readInt()
            require(n in 0..MAX_ENTRIES) { "bad sidecar pack size" }
            List(n) { _ -> readEntry(it) }
        }

    private fun writeEntry(o: DataOutputStream, e: SidecarEntry) {
        o.writeUTF(e.name)
        o.writeLong(e.size)
        o.writeUTF(e.label)
        o.writeUTF(e.pkg)
        o.writeUTF(e.version)
        o.writeInt(e.icon.size)
        o.write(e.icon)
    }

    private fun readEntry(i: DataInputStream): SidecarEntry {
        val name = i.readUTF()
        val size = i.readLong()
        val label = i.readUTF()
        val pkg = i.readUTF()
        val version = i.readUTF()
        val n = i.readInt()
        require(n in 0..MAX_ICON) { "bad icon size" }
        val icon = ByteArray(n)
        i.readFully(icon)
        return SidecarEntry(name, size, label, pkg, version, icon)
    }
}
