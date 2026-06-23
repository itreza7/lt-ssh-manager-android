package com.larateam.sshmanager.ui.debug

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larateam.sshmanager.data.crypto.BiometricGate
import com.larateam.sshmanager.data.model.ConnState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshDebugScreen(
    onBack: () -> Unit,
    viewModel: SshDebugViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findFragmentActivity()
    val scope = rememberCoroutineScope()
    val gate = remember { BiometricGate() }
    var bioMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SSH debug") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Connection", style = MaterialTheme.typography.titleMedium)
            ui.connections.forEach { c ->
                Row(
                    selected = ui.selectedId == c.id,
                    onSelect = { viewModel.select(c.id) },
                    label = "${c.displayName}  (${c.endpoint})  [${c.authMethod}]",
                )
            }
            if (ui.connections.isEmpty()) Text("No saved connections — add one first.")

            HorizontalDivider()

            StatusCard(ui.conn)
            bioMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = ui.selected != null && ui.conn !is ConnState.Connected,
                    onClick = { viewModel.onConnectClicked() },
                ) { Text("Connect") }

                OutlinedButton(
                    enabled = ui.conn is ConnState.Connected,
                    onClick = { viewModel.runUname() },
                ) { Text("Run uname -a") }

                OutlinedButton(
                    enabled = ui.conn is ConnState.Connected,
                    onClick = { viewModel.startStream() },
                ) { Text("Start stream") }

                OutlinedButton(
                    enabled = ui.conn is ConnState.Connected,
                    onClick = { viewModel.disconnect() },
                ) { Text("Disconnect") }
            }

            Text("Active sessions (service runs while > 0): ${ui.activeCount}", style = MaterialTheme.typography.bodySmall)

            if (ui.output.isNotBlank()) {
                Text("Command output", style = MaterialTheme.typography.titleSmall)
                Text(ui.output, fontFamily = FontFamily.Monospace)
            }

            if (ui.stream.isNotBlank()) {
                Text("Stream (app-scoped, survives backgrounding)", style = MaterialTheme.typography.titleSmall)
                Text(ui.stream, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider()
            Text("Host-key demo (debug only)", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = ui.selected != null, onClick = { viewModel.simulateKeyChange() }) {
                    Text("Tamper pinned key (simulate MITM)")
                }
                OutlinedButton(enabled = ui.selected != null, onClick = { viewModel.forgetHostKey() }) {
                    Text("Forget host key")
                }
            }

            HorizontalDivider()
            Text("Log", style = MaterialTheme.typography.titleSmall)
            ui.log.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }

    if (ui.needsPassword) {
        PasswordDialog(
            onConfirm = { viewModel.connectWithPassword(it.toCharArray()) },
            onDismiss = { viewModel.cancelPrompts() },
        )
    }

    if (ui.needsBiometric) {
        val act = activity
        if (act == null) {
            viewModel.cancelPrompts()
            bioMessage = "Cannot authenticate in this context."
        } else {
            // Trigger the biometric gate, then reveal + connect on success.
            LaunchedAuth {
                bioMessage = null
                when (val r = gate.authenticate(act, "Unlock key", "Authenticate to use the stored key")) {
                    BiometricGate.Outcome.Success -> viewModel.connectWithStoredKeyAfterAuth()
                    BiometricGate.Outcome.Unavailable -> {
                        viewModel.cancelPrompts(); bioMessage = "Set up a screen lock or biometric first."
                    }
                    is BiometricGate.Outcome.Error -> {
                        viewModel.cancelPrompts(); bioMessage = "Authentication failed: ${r.message}"
                    }
                }
            }
        }
    }
}

/** Runs the auth block once when the biometric prompt is requested. */
@Composable
private fun LaunchedAuth(block: suspend () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { block() }
}

@Composable
private fun Row(selected: Boolean, onSelect: () -> Unit, label: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}

@Composable
private fun StatusCard(state: ConnState) {
    val (text, color) = when (state) {
        ConnState.Disconnected -> "Disconnected" to MaterialTheme.colorScheme.onSurfaceVariant
        ConnState.Connecting -> "Connecting…" to MaterialTheme.colorScheme.primary
        is ConnState.Connected ->
            ("Connected\n${if (state.hostKey.firstContact) "Trusted NEW host key" else "Host key verified"}\n${state.hostKey.fingerprintSha256}") to Color(0xFF2E7D32)
        is ConnState.Error ->
            ("${if (state.error.name == "HOST_KEY_CHANGED") "⚠ BLOCKED — HOST KEY CHANGED (possible MITM)" else "Error: ${state.error}"}\n${state.message}") to MaterialTheme.colorScheme.error
    }
    Card(colors = CardDefaults.cardColors()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("Status", style = MaterialTheme.typography.labelMedium)
            Text(text, color = color, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun PasswordDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Password") },
        text = {
            OutlinedTextField(
                value = pw,
                onValueChange = { pw = it },
                label = { Text("Password (not stored)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(pw) }) { Text("Connect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
