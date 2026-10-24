package com.drivemount.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import com.drivemount.app.ui.theme.ColorSource
import com.drivemount.app.ui.theme.ThemeMode
import com.drivemount.app.ui.theme.ThemeSettings
import com.drivemount.app.ui.theme.argbToHex
import com.drivemount.app.ui.theme.spacing

private enum class ColorSheet { NONE, MATERIAL, CUSTOM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val settings by vm.settings.collectAsState()
    var sheet by remember { mutableStateOf(ColorSheet.NONE) }
    val sp = MaterialTheme.spacing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = sp.md, vertical = sp.sm),
            verticalArrangement = Arrangement.spacedBy(sp.md),
        ) {
            SectionTitle(stringResource(R.string.appearance))
            ThemeModeCard(mode = settings.mode, onMode = vm::setMode)
            ThemeColorCard(
                settings = settings,
                onMaterial = { sheet = ColorSheet.MATERIAL },
                onCustom = { sheet = ColorSheet.CUSTOM },
                onReset = {
                    sheet = ColorSheet.NONE
                    vm.resetColor()
                },
            )
            ThemePreviewCard()
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
private fun ThemeModeCard(mode: ThemeMode, onMode: (ThemeMode) -> Unit) {
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
            IconBadge(Icons.Rounded.BrightnessAuto)
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
