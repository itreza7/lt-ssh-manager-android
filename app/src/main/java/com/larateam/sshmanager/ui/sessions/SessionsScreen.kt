package com.larateam.sshmanager.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larateam.sshmanager.data.model.ConnState
import com.larateam.sshmanager.session.ViewKind
import com.larateam.sshmanager.ui.components.ConsoleCard
import com.larateam.sshmanager.ui.components.Eyebrow
import com.larateam.sshmanager.ui.components.StatusDot
import com.larateam.sshmanager.ui.theme.BrandType
import com.larateam.sshmanager.ui.theme.statusKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    onBack: () -> Unit,
    onOpenTerminals: () -> Unit,
    onOpenDashboard: (Long) -> Unit,
    onOpenSftp: (Long) -> Unit,
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Open sessions") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Eyebrow("nothing running", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "No saved sessions yet",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    "Open a host's dashboard, terminal, or files and it'll show up here — ready to resume.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.viewId }) { item ->
                ConsoleCard(
                    modifier = Modifier.fillMaxWidth(),
                    spine = item.state.statusKind(),
                    onClick = {
                        when (item.kind) {
                            ViewKind.SHELL, ViewKind.TMUX -> onOpenTerminals()
                            ViewKind.DASHBOARD -> onOpenDashboard(item.connectionId)
                            ViewKind.SFTP -> onOpenSftp(item.connectionId)
                        }
                    },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatusDot(item.state.statusKind())
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${item.subtitle}  ·  ${statusLabel(item.state)}",
                                style = BrandType.data,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        IconButton(onClick = { viewModel.remove(item) }) {
                            Icon(Icons.Filled.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun statusLabel(state: ConnState): String = when (state) {
    is ConnState.Connected -> "connected"
    ConnState.Connecting -> "connecting…"
    ConnState.Disconnected -> "disconnected — tap to reconnect"
    is ConnState.Error -> "error"
}

// statusColor lived here before; the status vocabulary now lives in the theme (see statusKind /
// BrandPalette) so connections, sessions, and the dashboard all speak it identically.
