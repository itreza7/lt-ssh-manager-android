package com.larateam.sshmanager.ui.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larateam.sshmanager.data.model.AuthMethod
import com.larateam.sshmanager.data.model.Connection
import com.larateam.sshmanager.ui.components.ConsoleCard
import com.larateam.sshmanager.ui.components.Eyebrow
import com.larateam.sshmanager.ui.components.Tag
import com.larateam.sshmanager.ui.theme.BrandType
import com.larateam.sshmanager.ui.theme.StatusKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsListScreen(
    onAddConnection: () -> Unit,
    onEditConnection: (Long) -> Unit,
    onOpenDashboard: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: ConnectionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Connection?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Connections") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddConnection,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New host") },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                ConnectionsUiState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                ConnectionsUiState.Empty -> EmptyState()

                is ConnectionsUiState.Content -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.connections, key = { it.id }) { connection ->
                        ConnectionCard(
                            connection = connection,
                            onClick = { onOpenDashboard(connection.id) },
                            onEdit = { onEditConnection(connection.id) },
                            onDelete = { pendingDelete = connection },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete connection?") },
            text = { Text("Delete \"${target.displayName}\"? This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ConnectionCard(
    connection: Connection,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    // Saved-but-not-live reads as idle; the spine keeps the status vocabulary present everywhere.
    ConsoleCard(modifier = Modifier.fillMaxWidth(), spine = StatusKind.IDLE, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(connection.displayName, style = MaterialTheme.typography.titleMedium)
                    AuthChip(connection.authMethod)
                }
                Text(
                    connection.endpoint,
                    style = BrandType.data,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit ${connection.displayName}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${connection.displayName}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AuthChip(method: AuthMethod) {
    val (label, color) = when (method) {
        AuthMethod.KEY -> "key" to MaterialTheme.colorScheme.primary
        AuthMethod.IN_APP_KEY -> "vault" to MaterialTheme.colorScheme.tertiary
        AuthMethod.PASSWORD -> "password" to MaterialTheme.colorScheme.secondary
    }
    Tag(label, color = color)
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Eyebrow("no hosts yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = "Add your first SSH host",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = "Tap “New host” to save a connection. Keys and passwords are resolved at connect time — passwords are never stored.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
