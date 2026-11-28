package com.drivemount.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import com.drivemount.app.ui.components.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.drivemount.app.ui.theme.LocalOriginalIcons
import com.drivemount.app.ui.theme.spacing

/** অ্যাপের সব card এটি দিয়ে বানানো হবে: rounded, নরম রং, elevation ছাড়া */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(MaterialTheme.spacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val shape = MaterialTheme.shapes.large
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/**
 * কার্ড, যার এক পাশ (বাম) থেকে [glow] রঙের হালকা আলো আসছে বলে মনে হয়। ক্লাউড-কার্ডে ব্র্যান্ড-রঙের আলো।
 * আলো শুধু আঁকার ধাপে (recompose ছাড়া), তাই হালকা।
 */
@Composable
fun GlowCard(
    glow: androidx.compose.ui.graphics.Color?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp) // ক্লাউড-কার্ডের কোণ কম গোল
    val inner: @Composable () -> Unit = {
        Column(
            modifier = if (glow == null) Modifier else Modifier.drawBehind {
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = 0.30f), glow.copy(alpha = 0.10f), androidx.compose.ui.graphics.Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(0f, size.height * 0.5f),
                        radius = size.width * 0.75f,
                    ),
                )
            },
            content = content,
        )
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) { inner() }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) { inner() }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = MaterialTheme.spacing.sm),
    )
}

/** গোল pastel পটভূমিতে আইকন */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, providerType: String? = null) {
    // আসল ক্লাউড আইকন চালু থাকলে গোল পটভূমি ছাড়াই সেটি
    if (providerType != null && LocalOriginalIcons.current && originalIconRes(providerType) != null) {
        Box(modifier = modifier.size(40.dp), contentAlignment = Alignment.Center) {
            ProviderIcon(providerType, Modifier.size(34.dp))
        }
        return
    }
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** আইকন + শিরোনাম + ঐচ্ছিক উপশিরোনাম/ট্রেইলিং — তালিকা ও settings-এর সাধারণ সারি */
@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** দিলে "আসল ক্লাউড আইকন" সেটিং অনুযায়ী ব্র্যান্ড আইকন দেখানো যায় */
    providerType: String? = null,
    /** লম্বা বিবরণ এক লাইনে কেটে দেখাতে (কম্প্যাক্ট সারির জন্য) */
    subtitleMaxLines: Int = Int.MAX_VALUE,
) {
    val sp = MaterialTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = sp.md, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.md),
    ) {
        IconBadge(icon, providerType = providerType)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = subtitleMaxLines,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}
