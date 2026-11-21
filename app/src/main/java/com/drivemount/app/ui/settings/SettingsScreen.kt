package com.drivemount.app.ui.settings

import com.composables.icons.lucide.ArrowUpDown as LuArrowUpDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette as LuPalette
import com.composables.icons.lucide.ArrowLeft as LuArrowLeft
import com.composables.icons.lucide.Cloud as LuCloud
import com.composables.icons.lucide.CloudUpload as LuCloudUpload
import com.composables.icons.lucide.Image as LuImage
import com.composables.icons.lucide.SunMoon as LuSunMoon
import com.composables.icons.lucide.Sparkles as LuSparkles
import com.composables.icons.lucide.Music as LuMusic
import com.composables.icons.lucide.ShieldCheck as LuShieldCheck
import com.composables.icons.lucide.Download as LuDownload
import com.composables.icons.lucide.RefreshCw as LuRefreshCw
import com.composables.icons.lucide.Info as LuInfo
import com.composables.icons.lucide.ChevronDown as LuChevronDown
import com.composables.icons.lucide.AppWindow as LuAppWindow
import com.composables.icons.lucide.Images as LuImages
import com.composables.icons.lucide.Film as LuFilm
import com.composables.icons.lucide.Search as LuSearch
import com.composables.icons.lucide.Plus as LuPlus
import com.composables.icons.lucide.Layers as LuLayers
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.draw.rotate
import kotlinx.coroutines.delay
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.abs
import kotlinx.coroutines.flow.filter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import com.drivemount.app.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivemount.app.R
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.components.IconBadge
import com.drivemount.app.ui.components.SectionTitle
import com.drivemount.app.ui.components.SettingsItem
import com.drivemount.app.ui.security.SecuritySection
import com.drivemount.app.ui.theme.ColorSource
import com.drivemount.app.ui.theme.ThemeMode
import com.drivemount.app.ui.theme.ThemeSettings
import com.drivemount.app.ui.theme.argbToHex
import com.drivemount.app.ui.theme.spacing

private enum class ColorSheet { NONE, MATERIAL, CUSTOM }

/**
 * কম্প্যাক্ট সারি (বাকি সরল সেটিংসের সমান সাইজ)। ট্যাপ করলে নিচে পুরো সেটিংস খোলে;
 * একসাথে একটাই খোলা থাকে। [autoScroll] সত্য থাকলে স্ক্রোল-হাইড কাজ করে না (খোলার পর নিজে থেকে দৃশ্যে আনার সময়)।
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpandableSetting(
    id: String,
    expanded: String?,
    onExpandedChange: (String?) -> Unit,
    autoScroll: androidx.compose.runtime.MutableState<Boolean>,
    icon: ImageVector,
    title: String,
    subtitle: String?,
    content: @Composable () -> Unit,
) {
    val isOpen = expanded == id
    val sp = MaterialTheme.spacing
    val requester = remember { BringIntoViewRequester() }
    val rotation by animateFloatAsState(if (isOpen) 180f else 0f, label = "chevron")

    LaunchedEffect(isOpen) {
        if (isOpen) {
            delay(320) // খোলার অ্যানিমেশন শেষ হোক
            autoScroll.value = true
            try {
                requester.bringIntoView()
            } finally {
                autoScroll.value = false
            }
        }
    }

    Column(Modifier.bringIntoViewRequester(requester)) {
        DmCard(contentPadding = PaddingValues(0.dp)) {
            SettingsItem(
                icon = icon,
                title = title,
                subtitle = subtitle,
                subtitleMaxLines = 1,
                onClick = { onExpandedChange(if (isOpen) null else id) },
                trailing = {
                    Icon(
                        imageVector = Lucide.LuChevronDown,
                        contentDescription = null,
                        modifier = Modifier.rotate(rotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
        AnimatedVisibility(
            visible = isOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            // লম্বা হলে নিজের ভেতরেই স্ক্রোল হয়
            Column(
                modifier = Modifier
                    .padding(top = sp.sm)
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.6f).dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(sp.md),
            ) { content() }
        }
    }
}

@Composable
private fun colorSubtitle(settings: ThemeSettings): String = when (settings.source) {
    ColorSource.DEFAULT -> stringResource(R.string.color_default)
    ColorSource.MATERIAL -> stringResource(R.string.color_material_fmt, argbToHex(settings.seedColor))
    ColorSource.CUSTOM -> stringResource(R.string.color_custom_fmt, argbToHex(settings.seedColor))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenBackup: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val settings by vm.settings.collectAsState()
    val cloudAccounts by vm.cloudAccounts.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    var showStorage by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(ColorSheet.NONE) }
    val sp = MaterialTheme.spacing
    val scrollState = rememberScrollState()
    var expanded by remember { mutableStateOf<String?>(null) }
    val autoScroll = remember { mutableStateOf(false) }

    // ব্যবহারকারী পেজ স্ক্রোল শুরু করলে খোলা সেটিংস আবার কম্প্যাক্ট হয়ে যায়
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.isScrollInProgress }
            .filter { it && !autoScroll.value }
            .collect { expanded = null }
    }

    Scaffold(
        topBar = { com.drivemount.app.ui.components.TopBarIcons {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Lucide.LuArrowLeft,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        }},
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = sp.md, vertical = sp.sm),
            verticalArrangement = Arrangement.spacedBy(sp.md),
        ) {
            SectionTitle(stringResource(R.string.appearance))
            ExpandableSetting(
                id = "theme_mode",
                expanded = expanded,
                onExpandedChange = { expanded = it },
                autoScroll = autoScroll,
                icon = Lucide.LuSunMoon,
                title = stringResource(R.string.theme_mode),
                subtitle = stringResource(when (settings.mode) { ThemeMode.SYSTEM -> R.string.mode_system; ThemeMode.LIGHT -> R.string.mode_light; ThemeMode.DARK -> R.string.mode_dark }),
            ) {
                ThemeModeCard(mode = settings.mode, onMode = vm::setMode, amoled = settings.amoled, onAmoled = vm::setAmoled)
            }
            ExpandableSetting(
                id = "theme_color",
                expanded = expanded,
                onExpandedChange = { expanded = it },
                autoScroll = autoScroll,
                icon = Lucide.LuLayers,
                title = stringResource(R.string.theme_color),
                subtitle = colorSubtitle(settings),
            ) {
                ThemeColorCard(
                    settings = settings,
                    onMaterial = { sheet = ColorSheet.MATERIAL },
                    onCustom = { sheet = ColorSheet.CUSTOM },
                    onReset = {
                        sheet = ColorSheet.NONE
                        vm.resetColor()
                    },
                )
            }
            ExpandableSetting(
                id = "theme_preview",
                expanded = expanded,
                onExpandedChange = { expanded = it },
                autoScroll = autoScroll,
                icon = Lucide.LuAppWindow,
                title = stringResource(R.string.preview),
                subtitle = null,
            ) {
                ThemePreviewCard()
            }
            ExpandableSetting(
                id = "player_appearance",
                expanded = expanded,
                onExpandedChange = { expanded = it },
                autoScroll = autoScroll,
                icon = Lucide.LuFilm,
                title = "Player Appearance",
                subtitle = "Normal, YouTube Style, Liquid Glass",
            ) {
                PlayerStyleCard()
            }

            SectionTitle(stringResource(R.string.settings_display))
            // আগে থেকেই কম্প্যাক্ট — অপরিবর্তিত
            DmCard(contentPadding = PaddingValues(0.dp)) {
                SettingsItem(
                    icon = Lucide.LuCloud,
                    title = stringResource(R.string.settings_original_icons),
                    subtitle = stringResource(R.string.settings_original_icons_desc),
                    onClick = { vm.setOriginalIcons(!settings.originalIcons) },
                    trailing = { Switch(checked = settings.originalIcons, onCheckedChange = null) },
                )
                SettingsItem(
                    icon = Lucide.LuImage,
                    title = stringResource(R.string.settings_thumbnails),
                    subtitle = stringResource(R.string.settings_thumbnails_desc),
                    onClick = { vm.setThumbnails(!settings.thumbnails) },
                    trailing = { Switch(checked = settings.thumbnails, onCheckedChange = null) },
                )
                SettingsItem(
                    icon = Lucide.LuSparkles,
                    title = stringResource(R.string.settings_liquid_glass),
                    subtitle = stringResource(R.string.settings_liquid_glass_desc),
                    onClick = { vm.setLiquidGlass(!settings.liquidGlass) },
                    trailing = { Switch(checked = settings.liquidGlass, onCheckedChange = null) },
                )
                SettingsItem(
                    icon = Lucide.LuMusic,
                    title = stringResource(R.string.settings_bitchord_player),
                    subtitle = stringResource(R.string.settings_bitchord_player_desc),
                    onClick = { vm.setBitChordPlayer(!settings.bitChordPlayer) },
                    trailing = { Switch(checked = settings.bitChordPlayer, onCheckedChange = null) },
                )
            }
            ExpandableSetting(
                id = "cloud_glow",
                expanded = expanded,
                onExpandedChange = { expanded = it },
                autoScroll = autoScroll,
                icon = Lucide.LuCloud,
                title = "Cloud card glow",
                subtitle = "Choose the glow colour of each cloud's card",
            ) {
                CloudGlowCard()
            }
            ExpandableSetting(
                id = "fab",
                expanded = expanded,
                onExpandedChange = { expanded = it },
                autoScroll = autoScroll,
                icon = Lucide.LuPlus,
                title = stringResource(R.string.fab_title),
                subtitle = stringResource(R.string.fab_desc),
            ) {
                FabSettingsCard()
            }
            ExpandableSetting(
                id = "icon_style",
                expanded = expanded,
                onExpandedChange = { expanded = it },
                autoScroll = autoScroll,
                icon = Lucide.LuPalette,
                title = stringResource(R.string.icons_title),
                subtitle = stringResource(R.string.icons_desc),
            ) {
                IconStyleCard()
            }
            ExpandableSetting(
                id = "cloud_order",
                expanded = expanded,
                onExpandedChange = { expanded = it },
                autoScroll = autoScroll,
                icon = Lucide.LuArrowUpDown,
                title = stringResource(R.string.cloud_order_title),
                subtitle = stringResource(R.string.cloud_order_desc),
            ) {
                CloudOrderCard()
            }

            SectionTitle(stringResource(R.string.settings_cat_security))
            // আগে থেকেই কম্প্যাক্ট — অপরিবর্তিত
            SecuritySection()
            DmCard(contentPadding = PaddingValues(0.dp)) {
                SettingsItem(
                    icon = Lucide.LuCloudUpload,
                    title = stringResource(R.string.settings_backup),
                    subtitle = stringResource(R.string.settings_backup_desc),
                    onClick = onOpenBackup,
                )
            }

            SectionTitle(stringResource(R.string.offline_section))
            ExpandableSetting(
                id = "offline",
                expanded = expanded,
                onExpandedChange = { expanded = it },
                autoScroll = autoScroll,
                icon = Lucide.LuDownload,
                title = stringResource(R.string.offline_title),
                subtitle = stringResource(R.string.offline_desc),
            ) {
                OfflineCard()
            }
            ExpandableSetting(
                id = "photo_offline",
                expanded = expanded,
                onExpandedChange = { expanded = it },
                autoScroll = autoScroll,
                icon = Lucide.LuImages,
                title = stringResource(R.string.photo_offline_title),
                subtitle = stringResource(R.string.photo_offline_desc),
            ) {
                PhotoOfflineCard()
                VideoStyleCard()
            }
            ExpandableSetting(
                id = "nav_style",
                expanded = expanded,
                onExpandedChange = { expanded = it },
                autoScroll = autoScroll,
                icon = Lucide.LuLayers,
                title = "Navigation Style",
                subtitle = "Original, Liquid Glass, Glass Pill",
            ) {
                NavStyleCard()
            }
            ExpandableSetting(
                id = "library_tabs",
                expanded = expanded,
                onExpandedChange = { expanded = it },
                autoScroll = autoScroll,
                icon = Lucide.LuLayers,
                title = "Library tabs position",
                subtitle = "Show Library tabs at the top or bottom",
            ) {
                LibraryTabsCard()
            }

            SectionTitle(stringResource(R.string.settings_cat_sync))
            ExpandableSetting(
                id = "backfill",
                expanded = expanded,
                onExpandedChange = { expanded = it },
                autoScroll = autoScroll,
                icon = Lucide.LuFilm,
                title = stringResource(R.string.backfill_title),
                subtitle = stringResource(R.string.backfill_desc),
            ) {
                BackfillCard()
            }
            ExpandableSetting(
                id = "index",
                expanded = expanded,
                onExpandedChange = { expanded = it },
                autoScroll = autoScroll,
                icon = Lucide.LuSearch,
                title = stringResource(R.string.index_title),
                subtitle = stringResource(R.string.index_desc),
            ) {
                IndexCard()
            }
            // আগে থেকেই কম্প্যাক্ট — অপরিবর্তিত
            TransferSpeedCard()

            SectionTitle(stringResource(R.string.about))
            EngineInfoCard()
            Spacer(Modifier.height(sp.lg))
        }
    }

    if (sheet != ColorSheet.NONE) {
        ModalBottomSheet(
            onDismissRequest = { sheet = ColorSheet.NONE },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            when (sheet) {
                ColorSheet.MATERIAL -> MaterialPalettePicker(
                    selectedArgb = if (settings.source == ColorSource.MATERIAL) settings.seedColor else null,
                    onSelect = vm::setMaterialColor,
                )

                ColorSheet.CUSTOM -> CustomColorPicker(
                    initialArgb = settings.seedColor,
                    onColorChange = vm::setCustomColor,
                )

                ColorSheet.NONE -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeCard(mode: ThemeMode, onMode: (ThemeMode) -> Unit, amoled: Boolean, onAmoled: (Boolean) -> Unit) {
    val sp = MaterialTheme.spacing
    val options = listOf(
        ThemeMode.SYSTEM to R.string.mode_system,
        ThemeMode.LIGHT to R.string.mode_light,
        ThemeMode.DARK to R.string.mode_dark,
    )
    DmCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sp.md),
        ) {
            IconBadge(Lucide.LuSunMoon)
            Text(
                text = stringResource(R.string.theme_mode),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(sp.md))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = mode == value,
                    onClick = { onMode(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(stringResource(label))
                }
            }
        }
        Spacer(Modifier.height(sp.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AMOLED Dark", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Pure black background in dark mode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = amoled, onCheckedChange = onAmoled)
        }
    }
}

@Composable
private fun ThemeColorCard(
    settings: ThemeSettings,
    onMaterial: () -> Unit,
    onCustom: () -> Unit,
    onReset: () -> Unit,
) {
    val sp = MaterialTheme.spacing
    val subtitle = when (settings.source) {
        ColorSource.DEFAULT -> stringResource(R.string.color_default)
        ColorSource.MATERIAL -> stringResource(R.string.color_material_fmt, argbToHex(settings.seedColor))
        ColorSource.CUSTOM -> stringResource(R.string.color_custom_fmt, argbToHex(settings.seedColor))
    }
    val buttonPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)

    DmCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sp.md),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Column {
                Text(
                    text = stringResource(R.string.theme_color),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(sp.md))
        Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
            FilledTonalButton(
                onClick = onMaterial,
                modifier = Modifier.weight(1f),
                contentPadding = buttonPadding,
            ) { Text(stringResource(R.string.action_material)) }
            FilledTonalButton(
                onClick = onCustom,
                modifier = Modifier.weight(1f),
                contentPadding = buttonPadding,
            ) { Text(stringResource(R.string.action_custom)) }
            OutlinedButton(
                onClick = onReset,
                enabled = settings.source != ColorSource.DEFAULT,
                modifier = Modifier.weight(1f),
                contentPadding = buttonPadding,
            ) { Text(stringResource(R.string.action_reset)) }
        }
    }
}

/** নির্বাচিত রং কেমন দেখায় তার ছোট নমুনা */
@Composable
private fun ThemePreviewCard() {
    val sp = MaterialTheme.spacing
    DmCard {
        Text(
            text = stringResource(R.string.preview),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(sp.md))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sp.sm),
        ) {
            Button(onClick = {}) { Text(stringResource(R.string.preview_filled)) }
            FilledTonalButton(onClick = {}) { Text(stringResource(R.string.preview_tonal)) }
            Switch(checked = true, onCheckedChange = {})
        }
        Spacer(Modifier.height(sp.md))
        LinearProgressIndicator(progress = { 0.6f }, modifier = Modifier.fillMaxWidth())
    }
}
