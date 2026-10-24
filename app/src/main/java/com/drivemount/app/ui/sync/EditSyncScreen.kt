package com.drivemount.app.ui.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drivemount.app.R
import com.drivemount.app.data.AccountEntity
import com.drivemount.app.data.transfer.SyncMode
import com.drivemount.app.ui.components.DmCard
import com.drivemount.app.ui.components.SectionTitle
import com.drivemount.app.ui.components.SettingsItem
import com.drivemount.app.ui.theme.spacing

private enum class Picking { SOURCE, DESTINATION }

private val Intervals = listOf(0, 60, 360, 720, 1440)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSyncScreen(
    onBack: () -> Unit,
    vm: EditSyncViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val sp = MaterialTheme.spacing
    var picking by remember { mutableStateOf<Picking?>(null) }

    LaunchedEffect(state.done) {
        if (state.done) onBack()
    }

    fun describe(loc: Location?, accounts: List<AccountEntity>): String? {
        if (loc == null) return null
        val name = accounts.firstOrNull { it.id == loc.accountId }?.displayName ?: "?"
        return if (loc.path.isEmpty()) name else "$name/${loc.path}"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
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
            OutlinedTextField(
                value = state.name,
                onValueChange = { vm.setName(it) },
                label = { Text(stringResource(R.string.sync_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            DmCard(contentPadding = PaddingValues(0.dp)) {
                SettingsItem(
                    icon = Icons.Rounded.Folder,
                    title = stringResource(R.string.sync_source),
                    subtitle = describe(state.src, accounts) ?: stringResource(R.string.sync_choose),
                    onClick = { picking = Picking.SOURCE },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = sp.md),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                SettingsItem(
                    icon = Icons.Rounded.Folder,
                    title = stringResource(R.string.sync_destination),
                    subtitle = describe(state.dst, accounts) ?: stringResource(R.string.sync_choose),
                    onClick = { picking = Picking.DESTINATION },
                )
            }

            SectionTitle(stringResource(R.string.sync_mode))
            DmCard(contentPadding = PaddingValues(0.dp)) {
                val modes = listOf(
                    Triple(SyncMode.COPY, R.string.mode_copy, R.string.sync_mode_copy_desc),
                    Triple(SyncMode.MIRROR, R.string.mode_mirror, R.string.sync_mode_mirror_desc),
                    Triple(SyncMode.MOVE, R.string.mode_move, R.string.sync_mode_move_desc),
                )
                modes.forEachIndexed { index, (mode, title, desc) ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = sp.md),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    ChoiceRow(
                        selected = state.mode == mode,
                        title = stringResource(title),
                        subtitle = stringResource(desc),
                        onClick = { vm.setMode(mode) },
                    )
                }
            }

            SectionTitle(stringResource(R.string.sync_interval))
            DmCard(contentPadding = PaddingValues(0.dp)) {
                Intervals.forEachIndexed { index, minutes ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = sp.md),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    ChoiceRow(
                        selected = state.interval == minutes,
                        title = intervalLabel(minutes),
                        subtitle = null,
                        onClick = { vm.setInterval(minutes) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.sync_interval_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = sp.sm),
            )

            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = sp.sm),
                )
            }

            Button(
                onClick = { vm.save() },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.sync_save)) }
            Spacer(Modifier.height(sp.lg))
        }
    }

    picking?.let { target ->
        FolderPickerDialog(
            accounts = accounts,
            onDismiss = { picking = null },
            onPicked = { accountId, path ->
                val loc = Location(accountId, path)
                if (target == Picking.SOURCE) vm.setSrc(loc) else vm.setDst(loc)
                picking = null
            },
        )
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, subtitle: String?, onClick: () -> Unit) {
    val sp = MaterialTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = sp.md, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.md),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
