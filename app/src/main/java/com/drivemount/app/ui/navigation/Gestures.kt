package com.drivemount.app.ui.navigation

import androidx.compose.animation.core.snap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.drivemount.app.ui.components.Icon
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * পর্দার ঠিক মাঝখানের এক-চতুর্থাংশ উচ্চতার (৩৭.৫%–৬২.৫%) পূর্ণ-প্রস্থ ফিতেয় শুরু হওয়া সোয়াইপ ধরে।
 * [left] = true হলে বাঁ দিকে, নইলে ডান দিকে। টান দূরত্বের সীমা (প্রস্থের ১২% বা অন্তত ৫৬dp) পেরোলেই
 * ছাড়ার অপেক্ষা না করে [onSwipe] ডাকে; টানার সময় [onProgress] (০..১) জানায়।
 * ইভেন্ট খরচ করে না; কোনো উপাদান (পেজার, সিক-বার, অনুভূমিক তালিকা) টানটা নিজে ব্যবহার করলে বাদ দেয়।
 */
private fun Modifier.bandSwipe(left: Boolean, fullHeight: Boolean, onProgress: (Float) -> Unit, onArm: () -> Unit, onSwipe: () -> Unit): Modifier =
    pointerInput(left, fullHeight) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val h = size.height
            if (!fullHeight && (down.position.y < h * 0.375f || down.position.y > h * 0.625f)) return@awaitEachGesture
            // আরামদায়ক: বেশি টান লাগে এবং আঙুল ছাড়লেই কাজ হয় (ছাড়ার আগে শুধু সূচক ফোটে + হালকা কম্পন)
            val threshold = maxOf(80.dp.toPx(), size.width * 0.2f)
            val slop = viewConfiguration.touchSlop
            var blocked = false
            var armed = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    if (event.changes.size > 1) blocked = true
                    val c = event.changes.firstOrNull { it.id == down.id } ?: break
                    val dx = c.position.x - down.position.x
                    val dy = c.position.y - down.position.y
                    // তালিকার ঊর্ধ্ব-স্ক্রল সামান্য কোনাকুনি টানেও টান "খরচ" করে — তাই সেটা গোনা হয় না;
                    // শুধু আড়াআড়ি টান কেউ খরচ করলে (যেমন breadcrumb-এর স্ক্রল) সোয়াইপ বাদ
                    if (c.isConsumed && abs(dx) > slop && abs(dy) <= abs(dx) * 0.6f) blocked = true
                    val along = if (left) -dx else dx
                    val ok = !blocked && along > 0f && abs(dx) >= abs(dy) * 1.5f
                    if (!ok) {
                        armed = false
                        onProgress(0f)
                    } else {
                        val p = (along / threshold).coerceIn(0f, 2f)
                        onProgress(p)
                        if (p >= 1f && !armed) { armed = true; onArm() }
                        else if (p < 0.8f) armed = false
                    }
                    if (!c.pressed) {
                        if (armed && ok) onSwipe()
                        break
                    }
                }
            } finally {
                onProgress(0f)
            }
        }
    }

/**
 * মাঝখানের ফিতেয় [left] দিকে সোয়াইপ ধরে [onSwipe] ডাকে, আর টানার সময় সেই পাশে একটি গোল সূচক
 * (আইকনসহ) কিনারা থেকে সরে এসে বড় হয়ে ফোটে, সীমা পেরোলে কম্পন দেয়।
 */
@Composable
fun BandSwipeHost(
    left: Boolean,
    icon: ImageVector,
    onSwipe: () -> Unit,
    modifier: Modifier = Modifier,
    fullHeight: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var commit by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val latest by rememberUpdatedState(onSwipe)
    val dir = if (left) -1f else 1f
    val widthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val thresholdDp = maxOf(80f, widthDp * 0.2f)
    // আঙুল চলার সময় কনটেন্ট সঙ্গে সঙ্গে সরে (pager-এর মতো), ছাড়লে স্প্রিংয়ে ফেরে বা সরে গিয়ে নেভিগেট করে
    val dragging = progress > 0f
    val target = if (commit) 1.7f else progress
    val shown by animateFloatAsState(
        target,
        animationSpec = if (dragging && !commit) snap() else spring(dampingRatio = 0.85f, stiffness = 380f),
        label = "bandSwipe",
    )
    androidx.compose.runtime.LaunchedEffect(commit) {
        if (commit) {
            kotlinx.coroutines.delay(110)
            latest()
            commit = false
        }
    }
    Box(
        modifier.bandSwipe(
            left = left,
            fullHeight = fullHeight,
            onProgress = { progress = it },
            onArm = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
            onSwipe = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                commit = true
            },
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (shown > 0.001f) Modifier.graphicsLayer {
                        translationX = dir * shown * thresholdDp.dp.toPx() * 0.6f
                        alpha = 1f - 0.2f * shown.coerceAtMost(1.5f)
                    } else Modifier,
                ),
        ) { content() }
        if (shown > 0.01f) {
            val p = shown.coerceIn(0f, 1f)
            Box(
                Modifier
                    .align(if (left) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 14.dp)
                    .graphicsLayer {
                        val sc = 0.5f + 0.5f * p
                        scaleX = sc
                        scaleY = sc
                        alpha = p
                    }
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** পর্দার দুই পাশে স্বচ্ছ ফিতে যা টাচ গিলে নেয়, যাতে কিনারা ঘেঁষে ধরলে ভুলে কিছু চেপে না যায় */
@Composable
fun EdgeDeadZones(width: Dp = 16.dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
        listOf(Alignment.CenterStart, Alignment.CenterEnd).forEach { side ->
            Box(
                Modifier
                    .align(side)
                    .fillMaxHeight()
                    .width(width)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val e = awaitPointerEvent()
                                e.changes.forEach { it.consume() }
                            } while (e.changes.any { it.pressed })
                        }
                    },
            )
        }
    }
}
