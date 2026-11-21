package com.drivemount.app.ui.settings

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.CircleAlert as LuCircleAlert
import com.composables.icons.lucide.CircleCheck as LuCircleCheck
import com.composables.icons.lucide.Info as LuInfo
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivemount.app.EngineUiState
import com.drivemount.app.MainViewModel
import com.drivemount.app.R
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.components.SettingsItem

/** rclone engine-এর অবস্থা ও সংস্করণ */
@Composable
fun EngineInfoCard(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    DmCard(contentPadding = PaddingValues(0.dp)) {
        when (val s = state) {
            EngineUiState.Loading -> SettingsItem(
                icon = Lucide.LuInfo,
                title = stringResource(R.string.engine_loading),
            )

            is EngineUiState.Ready -> SettingsItem(
                icon = Lucide.LuCircleCheck,
                title = stringResource(R.string.engine_running),
                subtitle = stringResource(
                    R.string.engine_details,
                    s.version.version,
                    s.version.goVersion,
                    "${s.version.os}/${s.version.arch}",
                ),
            )

            is EngineUiState.Error -> SettingsItem(
                icon = Lucide.LuCircleAlert,
                title = stringResource(R.string.engine_failed),
                subtitle = s.message,
            )
        }
    }
}
