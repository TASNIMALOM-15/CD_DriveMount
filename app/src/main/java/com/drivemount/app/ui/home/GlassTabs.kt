package com.drivemount.app.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** ভিডিওর "Navigation Tabs V10" থেকে নেওয়া রঙ */
internal object GlassColors {
    val Mint = Color(0xFF86E3C0)
    val Idle = Color(0xFFE6E0DC)
    val BlueTop = Color(0xFF4F7BCF)
    val BlueBottom = Color(0xFF2E54A6)
    val BlueGlow = Color(0xFF3F74E0)
    val BlueRim = Color(0xFFA9C4F5)
    val TealTop = Color(0xFF53917F)
    val TealBottom = Color(0xFF2E5C50)
    val TealRim = Color(0xFF86CDB5)
}

/**
 * "Glass Pill" উপরের ট্যাব (Videos/Photos/Audios…) — ভিডিওর উপরের স্টাইল হুবহু:
 * নির্বাচিত ট্যাব আলাদা নীল glow-কাচ; তার বামে/ডানের ট্যাবগুলো দুটো আলাদা ধূসর কাচের গ্রুপে।
 * নির্বাচন বদলালে গ্রুপ ও নীল পিল spring-এ ছড়ায়/সরে, প্রতিটি লেখা নিজের জায়গায় গড়িয়ে যায়।
 * চাপ ধরলে লেখা mint-সবুজ হয়।
 */
@Composable
fun GlassTopTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (labels.isEmpty()) return
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
    val padH = with(density) { 16.dp.toPx() }
    val gap = with(density) { 10.dp.toPx() }
    val widths = remember(labels, style) { labels.map { measurer.measure(it, style).size.width + 2 * padH } }
    val n = labels.size
    val sel = selected.coerceIn(0, n - 1)

    val leftW = widths.take(sel).sum()
    val rightW = widths.drop(sel + 1).sum()
    val selX = if (sel > 0) leftW + gap else 0f
    val selW = widths[sel]
    val rightX = selX + selW + gap
    val total = widths.sum() + 2 * gap

    val spec = spring<Float>(dampingRatio = 0.72f, stiffness = 320f)
    val aLeftW by animateFloatAsState(leftW, spec, label = "leftW")
    val aSelX by animateFloatAsState(selX, spec, label = "selX")
    val aSelW by animateFloatAsState(selW, spec, label = "selW")
    val aRightX by animateFloatAsState(rightX, spec, label = "rightX")
    val aRightW by animateFloatAsState(rightW, spec, label = "rightW")

    val scroll = rememberScrollState()
    LaunchedEffect(sel) { scroll.animateScrollTo((selX - padH * 2).toInt().coerceAtLeast(0)) }

    val shape = RoundedCornerShape(14.dp)
    val white = Color.White
    Box(modifier = modifier.horizontalScroll(scroll).padding(horizontal = 12.dp, vertical = 5.dp)) {
        Box(Modifier.width(with(density) { total.toDp() }).height(36.dp)) {
            // বাম ও ডান ধূসর কাচের গ্রুপ
            for ((gx, gw) in listOf(0f to aLeftW, aRightX to aRightW)) {
                if (gw > 2f) {
                    Box(
                        Modifier
                            .offset { IntOffset(gx.roundToInt(), 0) }
                            .width(with(density) { gw.toDp() })
                            .fillMaxHeight()
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surface)
                            .background(Brush.verticalGradient(listOf(white.copy(alpha = 0.17f), white.copy(alpha = 0.06f))))
                            .border(
                                BorderStroke(1.dp, Brush.verticalGradient(listOf(white.copy(alpha = 0.60f), white.copy(alpha = 0.22f)))),
                                shape,
                            ),
                    )
                }
            }
            // নির্বাচিত: নীল কাচ + নিচে glow
            Box(
                Modifier
                    .offset { IntOffset(aSelX.roundToInt(), 0) }
                    .width(with(density) { aSelW.toDp() })
                    .fillMaxHeight()
                    .shadow(14.dp, shape, ambientColor = GlassColors.BlueGlow, spotColor = GlassColors.BlueGlow)
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(GlassColors.BlueTop, GlassColors.BlueBottom)))
                    .background(Brush.verticalGradient(listOf(white.copy(alpha = 0.16f), Color.Transparent), endY = 60f))
                    .border(
                        BorderStroke(1.dp, Brush.verticalGradient(listOf(white.copy(alpha = 0.55f), GlassColors.BlueRim.copy(alpha = 0.55f)))),
                        shape,
                    ),
            )
            // লেখাগুলো (প্রত্যেকে নিজের লক্ষ্যে spring-এ যায়)
            for (i in 0 until n) {
                val targetX = when {
                    i < sel -> widths.take(i).sum()
                    i == sel -> selX
                    else -> rightX + widths.subList(sel + 1, i).sum()
                }
                GlassTopItem(labels[i], widths[i], targetX, i == sel) { onSelect(i) }
            }
        }
    }
}

@Composable
private fun GlassTopItem(label: String, width: Float, targetX: Float, selected: Boolean, onClick: () -> Unit) {
    val density = LocalDensity.current
    val x by animateFloatAsState(targetX, spring(dampingRatio = 0.72f, stiffness = 320f), label = "itemX")
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val color by animateColorAsState(
        when {
            pressed -> GlassColors.Mint
            selected -> Color.White
            else -> GlassColors.Idle
        },
        label = "itemColor",
    )
    Box(
        Modifier
            .offset { IntOffset(x.roundToInt(), 0) }
            .width(with(density) { width.toDp() })
            .fillMaxHeight()
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = color,
        )
    }
}
