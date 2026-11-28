package com.drivemount.app.ui.components

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import com.composables.icons.lucide.Puzzle as LuPuzzle
import com.composables.icons.lucide.Plus as LuPlus
import com.composables.icons.lucide.Cloud as LuCloud
import com.composables.icons.lucide.House as LuHouse
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.drivemount.app.ui.components.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowUpDown as LuArrowUpDown
import com.composables.icons.lucide.ChevronsLeft as LuChevronsLeft
import com.composables.icons.lucide.ChevronsRight as LuChevronsRight
import com.composables.icons.lucide.ClipboardPaste as LuClipboardPaste
import com.composables.icons.lucide.Copy as LuCopy
import com.composables.icons.lucide.Download as LuDownload
import com.composables.icons.lucide.FileUp as LuFileUp
import com.composables.icons.lucide.FolderPlus as LuFolderPlus
import com.composables.icons.lucide.Info as LuInfo
import com.composables.icons.lucide.LayoutGrid as LuLayoutGrid
import com.composables.icons.lucide.ListX as LuListX
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil as LuPencil
import com.composables.icons.lucide.Pin as LuPin
import com.composables.icons.lucide.RefreshCcw as LuRefreshCcw
import com.composables.icons.lucide.RefreshCw as LuRefreshCw
import com.composables.icons.lucide.Scissors as LuScissors
import com.composables.icons.lucide.Search as LuSearch
import com.composables.icons.lucide.Settings as LuSettings
import com.composables.icons.lucide.Sparkles as LuSparkles
import com.composables.icons.lucide.Trash2 as LuTrash2
import com.composables.icons.lucide.UserPlus as LuUserPlus
import com.composables.icons.lucide.X as LuX
import com.drivemount.app.DriveMountApp
import com.drivemount.app.R

/** পেজে এই মুহূর্তে করা যায় এমন একটি কাজ। [id] সেটিংসের তালিকার সাথে মেলে। */
class FabAction(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/** সেটিংসে দেখানোর জন্য: কোন পেজে কোন কোন কাজ থাকতে পারে */
class FabActionDef(val id: String, val icon: ImageVector, @StringRes val label: Int)

class FabPage(val key: String, @StringRes val title: Int, val actions: List<FabActionDef>)

object FabCatalog {
    const val BROWSER = "browser"
    const val BROWSER_SEL = "browser_sel"
    const val HOME = "home"
    const val CLOUDS = "clouds"
    const val TRANSFERS = "transfers"
    /** নিচের ট্যাবগুলোর বাটন: সব পেজের ফ্লোটিং বাটনেই আসে */
    const val TABS = "tabs"

    /** মূল (chevrons) বাটনের নিজের দুই অবস্থার আইকন সেটিংসে একই icons-মানচিত্রে জমা থাকে: "_toggle:closed" / "_toggle:open" */
    const val TOGGLE = "_toggle"
    const val TOGGLE_CLOSED = "closed"
    const val TOGGLE_OPEN = "open"

    val pages: List<FabPage> = listOf(
        FabPage(
            BROWSER, R.string.fab_page_browser,
            listOf(
                FabActionDef("upload", Lucide.LuFileUp, R.string.browser_upload),
                FabActionDef("new_folder", Lucide.LuFolderPlus, R.string.browser_new_folder),
                FabActionDef("search", Lucide.LuSearch, R.string.browser_search),
                FabActionDef("view", Lucide.LuLayoutGrid, R.string.fab_toggle_view),
                FabActionDef("sort", Lucide.LuArrowUpDown, R.string.browser_sort),
                FabActionDef("paste", Lucide.LuClipboardPaste, R.string.browser_paste_here),
            ),
        ),
        FabPage(
            BROWSER_SEL, R.string.fab_page_browser_sel,
            listOf(
                FabActionDef("download", Lucide.LuDownload, R.string.browser_download),
                FabActionDef("offline", Lucide.LuPin, R.string.offline_keep_action),
                FabActionDef("copy", Lucide.LuCopy, R.string.browser_copy),
                FabActionDef("cut", Lucide.LuScissors, R.string.browser_cut),
                FabActionDef("info", Lucide.LuInfo, R.string.info_title),
                FabActionDef("rename", Lucide.LuPencil, R.string.menu_rename),
                FabActionDef("delete", Lucide.LuTrash2, R.string.browser_delete),
                FabActionDef("clear", Lucide.LuX, R.string.cancel),
            ),
        ),
        FabPage(
            HOME, R.string.fab_page_home,
            listOf(
                FabActionDef("search", Lucide.LuSearch, R.string.browser_search),
                FabActionDef("add_account", Lucide.LuUserPlus, R.string.add_account),
                FabActionDef("sync", Lucide.LuRefreshCcw, R.string.sync_title),
                FabActionDef("transfers", Lucide.LuArrowUpDown, R.string.transfers),
                FabActionDef("settings", Lucide.LuSettings, R.string.settings),
            ),
        ),
        FabPage(
            CLOUDS, R.string.fab_page_clouds,
            listOf(
                FabActionDef("add_account", Lucide.LuUserPlus, R.string.add_account),
                FabActionDef("galaxy", Lucide.LuSparkles, R.string.galaxy_manage),
                FabActionDef("search", Lucide.LuSearch, R.string.browser_search),
                FabActionDef("transfers", Lucide.LuArrowUpDown, R.string.transfers),
            ),
        ),
        FabPage(
            TRANSFERS, R.string.fab_page_transfers,
            listOf(
                FabActionDef("clear_finished", Lucide.LuListX, R.string.tr_clear_finished),
            ),
        ),
    )
}

/**
 * ডান-নিচের ফ্লোটিং বাটন (chevrons-left)। ট্যাপ করলে আইকনগুলো বাঁ দিকে একটি সারিতে খোলে (প্রতিটির নিচে অতি ছোট নাম):
 * শুধু এই পেজের মূল কাজ (ট্যাবে যাওয়া এখন নিচের স্থায়ী বারে)। বাকি কাজ (Search/View/Sort ইত্যাদি) টপ-বারে।
 * সারি পর্দার চেয়ে বড় হলে আড়াআড়ি স্ক্রল করা যায়। যেকোনো আইকনে চেপে ধরে (tap & hold) টেনে সারির অন্য জায়গায়
 * সরানো যায় — নতুন ক্রম সেভ থাকে, সবসময় সেভাবেই দেখায়।
 * পেজ স্ক্রল করলে ([collapse] = true) গুটিয়ে আগের অবস্থায় ফিরে যায়।
 */
@Composable
fun ActionFab(
    page: String,
    actions: List<FabAction>,
    collapse: Boolean,
    modifier: Modifier = Modifier,
) {
    val settings = (LocalContext.current.applicationContext as DriveMountApp).fabSettings
    val enabled by settings.enabled.collectAsState()
    val hidden by settings.hidden.collectAsState()
    val icons by settings.icons.collectAsState()
    val iconColors by settings.colors.collectAsState()
    val orders by settings.orders.collectAsState()
    // ট্যাবে যাওয়ার বাটন এখন নিচের স্থায়ী বারে; এই বাটনে শুধু এই পেজের কাজ
    val pageVisible = actions.filter { "$page:${it.id}" !in hidden }
    if (!enabled || pageVisible.isEmpty()) return

    var expanded by remember(page) { mutableStateOf(false) }
    LaunchedEffect(collapse) { if (collapse) expanded = false }
    val colors = MaterialTheme.colorScheme
    val maxRowWidth = (LocalConfiguration.current.screenWidthDp - 92).dp

    Row(
        // নিচের স্থায়ী বার দেখা গেলে তার ওপরে; স্ক্রলে বার লুকালে নিচে নেমে আসে
        modifier = modifier.padding(bottom = com.drivemount.app.ui.home.LocalFabInset.current),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = colors.surfaceContainerHigh,
                shadowElevation = 4.dp,
                modifier = Modifier.widthIn(max = maxRowWidth),
            ) {
                val scroll = rememberScrollState()
                // খুললে বাটনের কাছের (ডান) প্রান্ত আগে দেখায়
                LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
                Row(
                    Modifier.horizontalScroll(scroll).padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (pageVisible.isNotEmpty()) {
                        FabGroup(
                            pageKey = page, items = pageVisible, savedOrder = orders[page],
                            highlightedId = null,
                            icons = icons, iconColors = iconColors,
                            onSaveOrder = { settings.setOrder(page, it) },
                            onDone = { expanded = false },
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(if (expanded) colors.primaryContainer else colors.surfaceContainerHigh)
                .pointerInput(Unit) { detectTapGestures(onTap = { expanded = !expanded }) },
            contentAlignment = Alignment.Center,
        ) {
            FabIcon(
                key = icons["${FabCatalog.TOGGLE}:${if (expanded) FabCatalog.TOGGLE_OPEN else FabCatalog.TOGGLE_CLOSED}"],
                default = if (expanded) Lucide.LuChevronsRight else Lucide.LuChevronsLeft,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (expanded) colors.onPrimaryContainer else colors.onSurface,
                color = iconColors["${FabCatalog.TOGGLE}:${if (expanded) FabCatalog.TOGGLE_OPEN else FabCatalog.TOGGLE_CLOSED}"],
            )
        }
    }
}

private val SlotWidth = 56.dp

private fun <T> List<T>.moved(from: Int, to: Int): List<T> =
    toMutableList().also { it.add(to, it.removeAt(from)) }

/**
 * একটি দলের আইকন-সারি। চেপে ধরে (long-press) টানলে আইকন সারিতে সরে; ছাড়লে নতুন ক্রম [onSaveOrder]-এ সেভ হয়।
 * প্রতিটি ঘর সমান চওড়া ([SlotWidth]), তাই টানা দূরত্ব দেখে পাশের ঘরের সাথে অদলবদল করা যায়।
 */
@Composable
private fun FabGroup(
    pageKey: String,
    items: List<FabAction>,
    savedOrder: List<String>?,
    highlightedId: String?,
    icons: Map<String, String>,
    iconColors: Map<String, Int>,
    onSaveOrder: (List<String>) -> Unit,
    onDone: () -> Unit,
) {
    val slotPx = with(LocalDensity.current) { SlotWidth.toPx() }
    val ids = items.map { it.id }
    val defaultOrder = remember(ids, savedOrder) {
        val saved = savedOrder.orEmpty()
        ids.sortedBy { saved.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    }
    var working by remember(defaultOrder) { mutableStateOf(defaultOrder) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val byId = items.associateBy { it.id }

    working.forEach { id ->
        val a = byId[id] ?: return@forEach
        androidx.compose.runtime.key(id) {
            FabItem(
                action = a,
                highlighted = highlightedId == id,
                iconKey = icons["$pageKey:$id"],
                iconColor = iconColors["$pageKey:$id"],
                dragging = dragId == id,
                offsetX = if (dragId == id) offset else 0f,
                onDragStart = { dragId = id; offset = 0f },
                onDrag = { dx ->
                    offset += dx
                    val i = working.indexOf(id)
                    if (offset > slotPx / 2 && i < working.lastIndex) {
                        working = working.moved(i, i + 1); offset -= slotPx
                    } else if (offset < -slotPx / 2 && i > 0) {
                        working = working.moved(i, i - 1); offset += slotPx
                    }
                },
                onDragEnd = { dragId = null; offset = 0f; onSaveOrder(working) },
                onDone = onDone,
            )
        }
    }
}

/** একটি ঘর: গোল আইকন + নিচে অতি ছোট নাম। ট্যাপে কাজ, চেপে ধরে টানলে সরানো */
@Composable
private fun FabItem(
    action: FabAction,
    highlighted: Boolean,
    iconKey: String?,
    iconColor: Int?,
    dragging: Boolean,
    offsetX: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // ক্লিক/টানার কাজ প্রতিবার নতুন হতে পারে; পুরোনোটা ধরে না রেখে সর্বশেষটা ডাকা হয়
    val click by androidx.compose.runtime.rememberUpdatedState(action.onClick)
    val dragL by androidx.compose.runtime.rememberUpdatedState(onDrag)
    val startL by androidx.compose.runtime.rememberUpdatedState(onDragStart)
    val endL by androidx.compose.runtime.rememberUpdatedState(onDragEnd)
    val doneL by androidx.compose.runtime.rememberUpdatedState(onDone)
    val haptic = LocalHapticFeedback.current
    // টানার পর আঙুল তুললে যেন ভুলে কাজটা না চলে যায়
    var suppressTap by remember { mutableStateOf(false) }
    LaunchedEffect(dragging) { if (!dragging) { kotlinx.coroutines.delay(200); suppressTap = false } }

    Column(
        modifier = Modifier
            .width(SlotWidth)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationX = offsetX
                val s = if (dragging) 1.12f else 1f
                scaleX = s
                scaleY = s
            }
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                if (!suppressTap) { doneL(); click() }
            }
            .pointerInput(action.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        suppressTap = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        startL()
                    },
                    onDrag = { change, amount -> change.consume(); dragL(amount.x) },
                    onDragEnd = { endL() },
                    onDragCancel = { endL() },
                )
            }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(if (dragging) 8.dp else 2.dp, CircleShape)
                .clip(CircleShape)
                .background(if (highlighted) colors.primaryContainer else colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            FabIcon(
                iconKey, action.icon, action.label, Modifier.size(20.dp),
                if (highlighted) colors.onPrimaryContainer else colors.onSurface, iconColor,
            )
        }
        Text(
            action.label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
        )
    }
}
