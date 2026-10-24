package com.drivemount.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drivemount.app.R
import com.drivemount.app.ui.theme.MaterialColorFamily
import com.drivemount.app.ui.theme.MaterialPalette
import com.drivemount.app.ui.theme.readableOn
import com.drivemount.app.ui.theme.spacing

/**
 * Material palette: উপরে color family, নিচে সেই family-র সব shade।
 * শেডে ট্যাপ করলেই পুরো অ্যাপের theme সঙ্গে সঙ্গে বদলে যায়।
 */
@Composable
fun MaterialPalettePicker(
    selectedArgb: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sp = MaterialTheme.spacing
    var familyIndex by remember {
        mutableIntStateOf(
            MaterialPalette.indexOfFirst { f -> f.shades.any { it.second.toArgb() == selectedArgb } }
                .takeIf { it >= 0 } ?: 5, // না পেলে Blue
        )
    }
    val family = MaterialPalette[familyIndex]

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = sp.md),
        verticalArrangement = Arrangement.spacedBy(sp.md),
    ) {
        Text(
            text = stringResource(R.string.sheet_material_title),
            style = MaterialTheme.typography.titleLarge,
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
            itemsIndexed(MaterialPalette) { index, fam ->
                FamilyDot(
                    family = fam,
                    selected = index == familyIndex,
                    onClick = { familyIndex = index },
                )
            }
        }

        Text(
            text = family.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        family.shades.chunked(5).forEach { rowShades ->
            Row(horizontalArrangement = Arrangement.spacedBy(sp.sm), modifier = Modifier.fillMaxWidth()) {
                rowShades.forEach { (shade, color) ->
                    ShadeSwatch(
                        shade = shade,
                        color = color,
                        selected = color.toArgb() == selectedArgb,
                        onClick = { onSelect(color.toArgb()) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(sp.lg))
    }
}

@Composable
private fun FamilyDot(family: MaterialColorFamily, selected: Boolean, onClick: () -> Unit) {
    val base = family.shades[5].second // 500
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(base)
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = family.name, tint = readableOn(base))
        }
    }
}

@Composable
private fun ShadeSwatch(
    shade: Int,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = readableOn(color)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(color)
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.medium)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = content)
        } else {
            Text(text = shade.toString(), style = MaterialTheme.typography.labelMedium, color = content)
        }
    }
}
