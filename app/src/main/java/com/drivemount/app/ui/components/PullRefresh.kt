package com.drivemount.app.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * অ্যাপের একমাত্র রিফ্রেশ উপায়: তালিকার একদম ওপরে গিয়ে নিচে টানলে (pull-down) রিফ্রেশ হয়।
 * রিফ্রেশ বোতাম/মেনু আর নেই। তালিকা ওপরে না থাকলে টান কাজ করে না (স্ক্রল আগে চলে)।
 * [topInset]: ওপরে স্বচ্ছ টপ-বার থাকলে ইন্ডিকেটরটি তার নিচে নামিয়ে দেখায়।
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset),
            )
        },
        content = content,
    )
}

/** Library পর্দার কনটেন্ট-বক্স: পুল-টু-রিফ্রেশ নেই (রিফ্রেশ "All" মেনুর বাটনে); চলাকালীন শুধু সরু প্রগ্রেস-রেখা */
@androidx.compose.runtime.Composable
fun LibraryBox(
    refreshing: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Box(modifier) {
        content()
        if (refreshing) androidx.compose.material3.LinearProgressIndicator(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(2.dp),
        )
    }
}
