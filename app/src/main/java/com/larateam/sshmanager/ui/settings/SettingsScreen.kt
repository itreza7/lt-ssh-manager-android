package com.larateam.sshmanager.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larateam.sshmanager.data.model.AppSettings
import com.larateam.sshmanager.data.model.AppTheme
import com.larateam.sshmanager.ui.components.ConsoleCard
import com.larateam.sshmanager.ui.components.Eyebrow
import com.larateam.sshmanager.ui.components.StatusDot
import com.larateam.sshmanager.ui.components.Tag
import com.larateam.sshmanager.ui.theme.BrandType
import com.larateam.sshmanager.ui.theme.StatusKind
import com.larateam.sshmanager.util.BatteryOptimization

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hosts by viewModel.pinnedHosts.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var batteryExempt by remember { mutableStateOf(BatteryOptimization.isIgnoring(context)) }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        batteryExempt = BatteryOptimization.isIgnoring(context)
        viewModel.markBatteryPromptDismissed()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Eyebrow("Appearance")
            Spacer(Modifier.height(8.dp))
            ConsoleCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
                    AppTheme.entries.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(selected = settings.theme == t, onClick = { viewModel.setTheme(t) })
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.theme == t, onClick = { viewModel.setTheme(t) })
                            Text(t.label(), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Eyebrow("Terminal")
            Spacer(Modifier.height(8.dp))
            ConsoleCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Font size", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Tag("${settings.terminalFontSizeSp} sp", color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = settings.terminalFontSizeSp.toFloat(),
                        onValueChange = { viewModel.setTerminalFontSize(it.toInt()) },
                        valueRange = AppSettings.MIN_TERMINAL_FONT_SP.toFloat()..AppSettings.MAX_TERMINAL_FONT_SP.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                    Text(
                        "user@host:~\$ echo sample",
                        fontFamily = FontFamily.Monospace,
                        fontSize = settings.terminalFontSizeSp.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Eyebrow("Terminal keys")
            Spacer(Modifier.height(8.dp))
            ConsoleCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    var draft by remember(settings.terminalKeys) { mutableStateOf(settings.terminalKeys) }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("Extra-keys row layout") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        minLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "One key per line. “Label = sequence”. Sequences: C-b (Ctrl-b), <esc> <tab> <cr> " +
                            "<up> <down> <left> <right> <space> <home> <end> <pgup> <pgdn>, or literal text. " +
                            "Special lines: :kbd :ctrl :alt :fn. " +
                            "Example — “⏎ = <cr>” is Enter; “scroll = C-b [” enters tmux scroll mode in one tap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.setTerminalKeys(draft) }) { Text("Apply") }
                        TextButton(onClick = {
                            draft = AppSettings.DEFAULT_TERMINAL_KEYS
                            viewModel.setTerminalKeys(AppSettings.DEFAULT_TERMINAL_KEYS)
                        }) { Text("Reset to default") }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Eyebrow("Background operation")
            Spacer(Modifier.height(8.dp))
            ConsoleCard(modifier = Modifier.fillMaxWidth(), spine = if (batteryExempt) StatusKind.LIVE else StatusKind.IDLE) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusDot(if (batteryExempt) StatusKind.LIVE else StatusKind.IDLE)
                        Text(
                            if (batteryExempt) "Exempt — long sessions stay alive" else "Battery optimization is on",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Text(
                        if (batteryExempt) "The OS won't suspend SSH sessions while you're away."
                        else "Android may kill long-running sessions in the background.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (!batteryExempt) {
                        Button(
                            onClick = { runCatching { batteryLauncher.launch(BatteryOptimization.requestExemptionIntent(context)) } },
                            modifier = Modifier.padding(top = 12.dp),
                        ) { Text("Allow background operation") }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Eyebrow("Known hosts")
            Spacer(Modifier.height(8.dp))
            if (hosts.isEmpty()) {
                ConsoleCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No pinned hosts yet. The first time you connect to a host, its key fingerprint is pinned here (TOFU).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                Text(
                    "Forget a host only if it legitimately rotated its key — the next connect re-pins it. A changed key on a host you didn't rotate is blocked as a possible attack.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    hosts.forEach { h ->
                        ConsoleCard(modifier = Modifier.fillMaxWidth(), spine = StatusKind.LIVE) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(h.hostPort, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                                    Text(
                                        h.fingerprintSha256,
                                        style = BrandType.data,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                IconButton(onClick = { viewModel.forgetHost(h.hostPort) }) {
                                    Icon(Icons.Filled.Delete, "Forget host", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                "SSH Manager · sessions stay alive via a foreground service",
                style = BrandType.tag,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

private fun AppTheme.label(): String = when (this) {
    AppTheme.SYSTEM -> "Follow system"
    AppTheme.LIGHT -> "Light"
    AppTheme.DARK -> "Dark"
}
