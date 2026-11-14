package com.drivemount.app.engine

/** ফোল্ডারের ভেতরের একটি ফাইল বা ফোল্ডার */
data class RemoteEntry(
    val name: String,
    /** অ্যাকাউন্টের browse root থেকে পূর্ণ পথ (যেমন "Docs/report.pdf") */
    val path: String,
    val isDir: Boolean,
    val size: Long?,
    val modTimeMillis: Long?,
    val mimeType: String,
)

/** rclone-এর "fs" স্ট্রিং। Local-এ root "/" ধরা হয়। */
fun fsRootFor(remoteName: String, type: String): String =
    if (type == "local") "$remoteName:/" else "$remoteName:"

/** অ্যাকাউন্টের সবচেয়ে ওপরের ফোল্ডার। Local-এ এটি ফোনের ফাইল-সিস্টেমের root ("/") */
fun browseRootFor(type: String): String = ""

/** অ্যাকাউন্ট খুললে যে ফোল্ডার থেকে দেখানো শুরু হবে (Local-এ চলমান ব্যবহারকারীর অভ্যন্তরীণ স্টোরেজ) */
fun startPathFor(type: String): String =
    if (type == "local") LocalPaths.internalRelative() else ""

fun joinPath(dir: String, name: String): String = if (dir.isEmpty()) name else "$dir/$name"

fun parentPath(path: String): String = path.substringBeforeLast('/', "")

/** একই নাম থাকলে "নাম (1).ext" বানায় */
fun uniqueName(name: String, existing: Set<String>): String {
    if (name !in existing) return name
    val dot = name.lastIndexOf('.')
    val base = if (dot > 0) name.substring(0, dot) else name
    val ext = if (dot > 0) name.substring(dot) else ""
    var i = 1
    while ("$base ($i)$ext" in existing) i++
    return "$base ($i)$ext"
}

/** fs স্ট্রিং আর পথ জোড়ে: "dm:" + "Docs/a" -> "dm:Docs/a", "/sdcard/X" + "a" -> "/sdcard/X/a" */
fun fsJoin(fs: String, path: String): String = when {
    path.isEmpty() -> fs
    fs.endsWith(":") || fs.endsWith("/") -> fs + path
    else -> "$fs/$path"
}

/** ফাইলের নাম থেকে MIME ধরন আন্দাজ; না পেলে hint, তাও না থাকলে সাধারণ ধরন */
fun guessMime(name: String, hint: String = ""): String {
    val ext = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
    val fromExt = if (ext.isEmpty()) null else android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    return fromExt ?: hint.ifBlank { "*/*" }
}
