package com.drivemount.app.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.AppWindow as LuAppWindow
import com.composables.icons.lucide.ArrowDown as LuArrowDown
import com.composables.icons.lucide.ArrowLeft as LuArrowLeft
import com.composables.icons.lucide.ArrowUp as LuArrowUp
import com.composables.icons.lucide.ArrowUpDown as LuArrowUpDown
import com.composables.icons.lucide.BatteryCharging as LuBatteryCharging
import com.composables.icons.lucide.Bell as LuBell
import com.composables.icons.lucide.Cast as LuCast
import com.composables.icons.lucide.Check as LuCheck
import com.composables.icons.lucide.ChevronDown as LuChevronDown
import com.composables.icons.lucide.ChevronRight as LuChevronRight
import com.composables.icons.lucide.ChevronsLeft as LuChevronsLeft
import com.composables.icons.lucide.ChevronsRight as LuChevronsRight
import com.composables.icons.lucide.CircleAlert as LuCircleAlert
import com.composables.icons.lucide.CircleCheck as LuCircleCheck
import com.composables.icons.lucide.CirclePlay as LuCirclePlay
import com.composables.icons.lucide.CircleX as LuCircleX
import com.composables.icons.lucide.ClipboardPaste as LuClipboardPaste
import com.composables.icons.lucide.Clock as LuClock
import com.composables.icons.lucide.Cloud as LuCloud
import com.composables.icons.lucide.CloudUpload as LuCloudUpload
import com.composables.icons.lucide.Copy as LuCopy
import com.composables.icons.lucide.Database as LuDatabase
import com.composables.icons.lucide.Download as LuDownload
import com.composables.icons.lucide.Ellipsis as LuEllipsis
import com.composables.icons.lucide.EllipsisVertical as LuEllipsisVertical
import com.composables.icons.lucide.FastForward as LuFastForward
import com.composables.icons.lucide.File as LuFile
import com.composables.icons.lucide.FileArchive as LuFileArchive
import com.composables.icons.lucide.FileAudio as LuFileAudio
import com.composables.icons.lucide.FileText as LuFileText
import com.composables.icons.lucide.FileUp as LuFileUp
import com.composables.icons.lucide.FileVideo as LuFileVideo
import com.composables.icons.lucide.Film as LuFilm
import com.composables.icons.lucide.Folder as LuFolder
import com.composables.icons.lucide.FolderOpen as LuFolderOpen
import com.composables.icons.lucide.FolderPlus as LuFolderPlus
import com.composables.icons.lucide.Gauge as LuGauge
import com.composables.icons.lucide.HardDrive as LuHardDrive
import com.composables.icons.lucide.Headphones as LuHeadphones
import com.composables.icons.lucide.House as LuHouse
import com.composables.icons.lucide.Image as LuImage
import com.composables.icons.lucide.Images as LuImages
import com.composables.icons.lucide.Info as LuInfo
import com.composables.icons.lucide.Layers as LuLayers
import com.composables.icons.lucide.LayoutGrid as LuLayoutGrid
import com.composables.icons.lucide.List as LuList
import com.composables.icons.lucide.ListFilter as LuListFilter
import com.composables.icons.lucide.ListMusic as LuListMusic
import com.composables.icons.lucide.ListX as LuListX
import com.composables.icons.lucide.Lock as LuLock
import com.composables.icons.lucide.LockKeyhole as LuLockKeyhole
import com.composables.icons.lucide.LockOpen as LuLockOpen
import com.composables.icons.lucide.Maximize as LuMaximize
import com.composables.icons.lucide.Music as LuMusic
import com.composables.icons.lucide.Palette as LuPalette
import com.composables.icons.lucide.Pause as LuPause
import com.composables.icons.lucide.Pencil as LuPencil
import com.composables.icons.lucide.PictureInPicture2 as LuPictureInPicture2
import com.composables.icons.lucide.Pin as LuPin
import com.composables.icons.lucide.Play as LuPlay
import com.composables.icons.lucide.Plus as LuPlus
import com.composables.icons.lucide.Puzzle as LuPuzzle
import com.composables.icons.lucide.Ratio as LuRatio
import com.composables.icons.lucide.RefreshCcw as LuRefreshCcw
import com.composables.icons.lucide.RefreshCw as LuRefreshCw
import com.composables.icons.lucide.Repeat as LuRepeat
import com.composables.icons.lucide.Repeat1 as LuRepeat1
import com.composables.icons.lucide.Rewind as LuRewind
import com.composables.icons.lucide.RotateCcw as LuRotateCcw
import com.composables.icons.lucide.RotateCw as LuRotateCw
import com.composables.icons.lucide.Scissors as LuScissors
import com.composables.icons.lucide.Search as LuSearch
import com.composables.icons.lucide.Settings as LuSettings
import com.composables.icons.lucide.Share2 as LuShare2
import com.composables.icons.lucide.ShieldCheck as LuShieldCheck
import com.composables.icons.lucide.Shuffle as LuShuffle
import com.composables.icons.lucide.SkipBack as LuSkipBack
import com.composables.icons.lucide.SkipForward as LuSkipForward
import com.composables.icons.lucide.Smartphone as LuSmartphone
import com.composables.icons.lucide.Sparkles as LuSparkles
import com.composables.icons.lucide.Star as LuStar
import com.composables.icons.lucide.SunMedium as LuSunMedium
import com.composables.icons.lucide.SunMoon as LuSunMoon
import com.composables.icons.lucide.Trash2 as LuTrash2
import com.composables.icons.lucide.UserPlus as LuUserPlus
import com.composables.icons.lucide.Video as LuVideo
import com.composables.icons.lucide.Volume1 as LuVolume1
import com.composables.icons.lucide.Volume2 as LuVolume2
import com.composables.icons.lucide.X as LuX
import java.util.IdentityHashMap

/**
 * অ্যাপের কোডে ব্যবহৃত সব Lucide আইকনের তালিকা (সেটিংসে আইকন বদলানো/রং দেওয়ার জন্য)।
 * কী = আইকনের নাম (যেমন "Folder")।
 */
object AppIcons {
    val all: List<Pair<String, ImageVector>> by lazy {
        listOf(
        "AppWindow" to Lucide.LuAppWindow,
        "ArrowDown" to Lucide.LuArrowDown,
        "ArrowLeft" to Lucide.LuArrowLeft,
        "ArrowUp" to Lucide.LuArrowUp,
        "ArrowUpDown" to Lucide.LuArrowUpDown,
        "BatteryCharging" to Lucide.LuBatteryCharging,
        "Bell" to Lucide.LuBell,
        "Cast" to Lucide.LuCast,
        "Check" to Lucide.LuCheck,
        "ChevronDown" to Lucide.LuChevronDown,
        "ChevronRight" to Lucide.LuChevronRight,
        "ChevronsLeft" to Lucide.LuChevronsLeft,
        "ChevronsRight" to Lucide.LuChevronsRight,
        "CircleAlert" to Lucide.LuCircleAlert,
        "CircleCheck" to Lucide.LuCircleCheck,
        "CirclePlay" to Lucide.LuCirclePlay,
        "CircleX" to Lucide.LuCircleX,
        "ClipboardPaste" to Lucide.LuClipboardPaste,
        "Clock" to Lucide.LuClock,
        "Cloud" to Lucide.LuCloud,
        "CloudUpload" to Lucide.LuCloudUpload,
        "Copy" to Lucide.LuCopy,
        "Database" to Lucide.LuDatabase,
        "Download" to Lucide.LuDownload,
        "Ellipsis" to Lucide.LuEllipsis,
        "EllipsisVertical" to Lucide.LuEllipsisVertical,
        "FastForward" to Lucide.LuFastForward,
        "File" to Lucide.LuFile,
        "FileArchive" to Lucide.LuFileArchive,
        "FileAudio" to Lucide.LuFileAudio,
        "FileText" to Lucide.LuFileText,
        "FileUp" to Lucide.LuFileUp,
        "FileVideo" to Lucide.LuFileVideo,
        "Film" to Lucide.LuFilm,
        "Folder" to Lucide.LuFolder,
        "FolderOpen" to Lucide.LuFolderOpen,
        "FolderPlus" to Lucide.LuFolderPlus,
        "Gauge" to Lucide.LuGauge,
        "HardDrive" to Lucide.LuHardDrive,
        "Headphones" to Lucide.LuHeadphones,
        "House" to Lucide.LuHouse,
        "Image" to Lucide.LuImage,
        "Images" to Lucide.LuImages,
        "Info" to Lucide.LuInfo,
        "Layers" to Lucide.LuLayers,
        "LayoutGrid" to Lucide.LuLayoutGrid,
        "List" to Lucide.LuList,
        "ListFilter" to Lucide.LuListFilter,
        "ListMusic" to Lucide.LuListMusic,
        "ListX" to Lucide.LuListX,
        "Lock" to Lucide.LuLock,
        "LockKeyhole" to Lucide.LuLockKeyhole,
        "LockOpen" to Lucide.LuLockOpen,
        "Maximize" to Lucide.LuMaximize,
        "Music" to Lucide.LuMusic,
        "Palette" to Lucide.LuPalette,
        "Pause" to Lucide.LuPause,
        "Pencil" to Lucide.LuPencil,
        "PictureInPicture2" to Lucide.LuPictureInPicture2,
        "Pin" to Lucide.LuPin,
        "Play" to Lucide.LuPlay,
        "Plus" to Lucide.LuPlus,
        "Puzzle" to Lucide.LuPuzzle,
        "Ratio" to Lucide.LuRatio,
        "RefreshCcw" to Lucide.LuRefreshCcw,
        "RefreshCw" to Lucide.LuRefreshCw,
        "Repeat" to Lucide.LuRepeat,
        "Repeat1" to Lucide.LuRepeat1,
        "Rewind" to Lucide.LuRewind,
        "RotateCcw" to Lucide.LuRotateCcw,
        "RotateCw" to Lucide.LuRotateCw,
        "Scissors" to Lucide.LuScissors,
        "Search" to Lucide.LuSearch,
        "Settings" to Lucide.LuSettings,
        "Share2" to Lucide.LuShare2,
        "ShieldCheck" to Lucide.LuShieldCheck,
        "Shuffle" to Lucide.LuShuffle,
        "SkipBack" to Lucide.LuSkipBack,
        "SkipForward" to Lucide.LuSkipForward,
        "Smartphone" to Lucide.LuSmartphone,
        "Sparkles" to Lucide.LuSparkles,
        "Star" to Lucide.LuStar,
        "SunMedium" to Lucide.LuSunMedium,
        "SunMoon" to Lucide.LuSunMoon,
        "Trash2" to Lucide.LuTrash2,
        "UserPlus" to Lucide.LuUserPlus,
        "Video" to Lucide.LuVideo,
        "Volume1" to Lucide.LuVolume1,
        "Volume2" to Lucide.LuVolume2,
        "X" to Lucide.LuX,
        )
    }
    val byKey: Map<String, ImageVector> by lazy { all.toMap() }

    private val byIdentity: IdentityHashMap<ImageVector, String> by lazy {
        IdentityHashMap<ImageVector, String>().also { m -> all.forEach { (k, v) -> m[v] = k } }
    }
    private val byName: Map<String, String> by lazy {
        all.filter { it.second.name.isNotBlank() }.associate { it.second.name to it.first }
    }

    /** কোনো ImageVector আসলে কোন কী-এর আইকন (তালিকায় না থাকলে null) */
    fun keyOf(v: ImageVector): String? = byIdentity[v] ?: byName[v.name]

    /** "ArrowUpDown" -> "Arrow Up Down" */
    fun label(key: String): String = key.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")
}
