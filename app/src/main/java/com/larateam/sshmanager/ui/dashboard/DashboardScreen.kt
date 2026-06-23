package com.larateam.sshmanager.ui.dashboard

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import com.larateam.sshmanager.dashboard.DashboardVitals
import com.larateam.sshmanager.dashboard.TmuxSession
import com.larateam.sshmanager.data.crypto.BiometricGate
import com.larateam.sshmanager.ui.components.ConsoleCard
import com.larateam.sshmanager.ui.components.Eyebrow
import com.larateam.sshmanager.ui.components.MetricBar
import com.larateam.sshmanager.ui.components.StatusDot
import com.larateam.sshmanager.ui.components.Tag
import com.larateam.sshmanager.ui.theme.BrandType
import com.larateam.sshmanager.ui.theme.StatusKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onBack: () -> Unit,
    onOpenTerminals: () -> Unit,
    onOpenSftp: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findFragmentActivity()
    val gate = remember { BiometricGate() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(ui.title.ifBlank { "Dashboard" }) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !ui.connecting) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                ui.connecting -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusDot(StatusKind.BUSY)
                    Text("Connecting…", style = MaterialTheme.typography.bodyMedium)
                }
                // Connected, probe in flight (no result yet, no error) — loading, not "no data".
                ui.vitals == null && ui.error == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusDot(StatusKind.BUSY)
                    Text("Fetching server data…", style = MaterialTheme.typography.bodyMedium)
                }
                ui.vitals == null -> ConsoleCard(modifier = Modifier.fillMaxWidth(), spine = StatusKind.DOWN) {
                    Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
                        Eyebrow("unreachable", color = MaterialTheme.colorScheme.error)
                        Text(
                            ui.error ?: "No data",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                else -> DashboardBody(ui.vitals!!, ui.asOf, ui.refreshing)
            }

            if (ui.vitals != null || ui.tmux.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.openPlainTerminal(); onOpenTerminals() }) { Text("Open terminal") }
                    OutlinedButton(onClick = onOpenSftp) { Text("Files (SFTP)") }
                }
                TmuxSection(ui.tmux, onAttach = { viewModel.attachTmux(it); onOpenTerminals() })
            }
        }
    }

    if (ui.needsPassword) {
        PasswordDialog(
            onConfirm = { pw, save -> viewModel.submitPassword(pw.toCharArray(), save) },
            onDismiss = { viewModel.cancelPrompts(); onBack() },
        )
    }
    if (ui.needsBiometric) {
        val act = activity
        if (act == null) {
            viewModel.cancelPrompts(); onBack()
        } else {
            LaunchedEffect(Unit) {
                when (gate.authenticate(act, "Unlock to connect", "Authenticate to use your saved credential")) {
                    BiometricGate.Outcome.Success -> viewModel.submitStoredKeyAfterAuth()
                    else -> { viewModel.cancelPrompts(); onBack() }
                }
            }
        }
    }
}

@Composable
private fun DashboardBody(v: DashboardVitals, asOf: String?, refreshing: Boolean) {
    // Host plate — identity in monospace, hairline-separated key/value readouts.
    ConsoleCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            Eyebrow("host")
            Column(Modifier.padding(top = 8.dp)) {
                InfoRow("host", v.hostname ?: "unknown")
                InfoRow("os", v.os ?: "unknown")
                InfoRow("kernel", v.kernel ?: "unknown")
                InfoRow("uptime", v.uptimeSeconds?.let { formatUptime(it) } ?: "unknown")
                InfoRow("load", v.load?.let { "%.2f  %.2f  %.2f".format(it.one, it.five, it.fifteen) } ?: "unknown", last = true)
            }
        }
    }

    ConsoleCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Meter("cpu", v.cpuBusyPercent, v.cpuBusyPercent?.let { "$it%" } ?: "unknown")
            Meter(
                "memory",
                v.memUsedPercent,
                if (v.memTotalBytes != null && v.memUsedBytes != null)
                    "${v.memUsedPercent}%  ·  ${humanBytes(v.memUsedBytes)} / ${humanBytes(v.memTotalBytes)}"
                else "unknown",
            )
            if (v.disks.isEmpty()) {
                Meter("disk", null, "unknown")
            } else {
                for (d in v.disks) {
                    Meter("disk ${d.mount}", d.usedPercent, "${d.usedPercent}%  ·  ${humanBytes(d.usedBytes)} / ${humanBytes(d.totalBytes)}")
                }
            }
        }
    }

    Text(
        text = (if (refreshing) "refreshing… " else "") + (asOf?.let { "as of $it" } ?: ""),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TmuxSection(sessions: List<TmuxSession>, onAttach: (String) -> Unit) {
    Eyebrow("tmux sessions", color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (sessions.isEmpty()) {
        Text("No tmux sessions", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        sessions.forEach { s ->
            ConsoleCard(modifier = Modifier.fillMaxWidth(), spine = StatusKind.LIVE, onClick = { onAttach(s.name) }) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(StatusKind.LIVE)
                    Text(s.name, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                    s.windows?.let { Text("$it win", style = BrandType.tag, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (s.attached) Tag("attached", color = MaterialTheme.colorScheme.primary)
                    Box(Modifier.weight(1f))
                    Text("attach", style = BrandType.tag, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun Meter(label: String, percent: Int?, detail: String) {
    val brand = com.larateam.sshmanager.ui.theme.Brand.palette
    val color = when {
        percent == null -> brand.idle
        percent >= 85 -> brand.down
        percent >= 60 -> brand.busy
        else -> brand.live
    }
    MetricBar(
        label = label,
        reading = detail,
        fraction = (percent ?: 0).coerceIn(0, 100) / 100f,
        color = color,
    )
}

@Composable
private fun InfoRow(label: String, value: String, last: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = BrandType.tag,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Text(value, style = BrandType.data, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
    if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun PasswordDialog(onConfirm: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var save by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Password") },
        text = {
            Column {
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                SavePasswordCheckbox(save) { save = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pw, save) }) { Text("Connect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Shared "remember this password" opt-in for connect prompts — encrypted, unlock-gated. */
@Composable
private fun SavePasswordCheckbox(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.padding(top = 6.dp).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(
            "Save password (encrypted; unlock to reuse)",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

private fun formatUptime(seconds: Long): String {
    val d = seconds / 86400
    val h = (seconds % 86400) / 3600
    val m = (seconds % 3600) / 60
    return buildString {
        if (d > 0) append("${d}d ")
        if (d > 0 || h > 0) append("${h}h ")
        append("${m}m")
    }
}

private fun humanBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB", "PB")
    var value = bytes.toDouble()
    var i = -1
    while (value >= 1024 && i < units.size - 1) { value /= 1024; i++ }
    return "%.1f %s".format(value, units[i])
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
