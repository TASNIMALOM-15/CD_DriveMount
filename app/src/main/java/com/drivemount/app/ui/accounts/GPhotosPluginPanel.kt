package com.drivemount.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.drivemount.app.plugin.GPhotosPlugin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** "Google Photos Unlimited" প্লাগইনের অবস্থা ও পরীক্ষা (ধাপ ১: নকল আপলোড) */
@Composable
fun GPhotosPluginPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(GPhotosPlugin.isInstalled(context)) }
    var status by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Float?>(null) }
    var running by remember { mutableStateOf(false) }

    fun run(flow: Flow<GPhotosPlugin.Event>) {
        if (running) return
        running = true
        progress = null
        status = "Connecting to plugin…"
        scope.launch {
            val finished = withTimeoutOrNull(20_000) {
                flow.collect { e ->
                    when (e) {
                        is GPhotosPlugin.Event.Hello ->
                            status = "Plugin v${e.versionName} (${e.stage}). " + if (e.signedIn) "Account: ${e.account}" else "No account selected yet."
                        is GPhotosPlugin.Event.Progress -> {
                            progress = e.percent / 100f
                            status = "${e.text}… ${e.percent}%"
                        }
                        is GPhotosPlugin.Event.Done -> status = e.message
                        is GPhotosPlugin.Event.Error -> status = "Error: ${e.message}"
                    }
                }
                true
            }
            if (finished == null) status = "The plugin did not respond in time"
            running = false
            progress = null
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!installed) {
            Text(
                "The Google Photos Unlimited plugin is not installed. Install the DriveMount Google Photos Plugin APK, then check again.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { installed = GPhotosPlugin.isInstalled(context) }) { Text("Check again") }
        } else {
            Text("Plugin installed.", style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent().setComponent(
                                android.content.ComponentName(GPhotosPlugin.PACKAGE, GPhotosPlugin.PACKAGE + ".MainActivity"),
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.onFailure { status = "Could not open the plugin: ${it.message}" }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open plugin (choose account / test token)") }
            OutlinedButton(
                onClick = { run(GPhotosPlugin.request(context, GPhotosPlugin.MSG_HELLO)) },
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Check connection") }
            OutlinedButton(
                onClick = { run(GPhotosPlugin.request(context, GPhotosPlugin.MSG_TEST_UPLOAD)) },
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Run test upload (simulated)") }
            progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
