package com.larateam.sshmanager.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.larateam.sshmanager.ui.components.BrandHeader
import com.larateam.sshmanager.ui.components.ConsoleCard
import com.larateam.sshmanager.ui.components.Eyebrow
import com.larateam.sshmanager.ui.components.Tag
import com.larateam.sshmanager.ui.theme.SshManagerTheme

@Composable
fun HomeScreen(
    onOpenConnections: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenTerminalDemo: () -> Unit = {},
    onOpenTerminals: () -> Unit = {},
    onOpenSessions: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            BrandHeader(
                eyebrow = "secure shell client",
                title = "SSH Manager",
                subtitle = "Connect, operate, and keep sessions alive.",
            )

            Spacer(Modifier.height(32.dp))
            Eyebrow("Destinations", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            Destination("hosts", "Connections", "Saved hosts, keys & passwords",
                MaterialTheme.colorScheme.primary, onOpenConnections)
            Spacer(Modifier.height(10.dp))
            Destination("live", "Open sessions", "Resume running terminals & files",
                MaterialTheme.colorScheme.tertiary, onOpenSessions)
            Spacer(Modifier.height(10.dp))
            Destination("pty", "Terminals", "Full-color shells with an extra-keys row",
                MaterialTheme.colorScheme.secondary, onOpenTerminals)
            Spacer(Modifier.height(10.dp))
            Destination("config", "Settings", "Theme, terminal & known hosts",
                MaterialTheme.colorScheme.onSurfaceVariant, onOpenSettings)

            Spacer(Modifier.height(28.dp))
            Eyebrow("Developer", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onOpenDebug) { Text("SSH debug") }
                TextButton(onClick = onOpenTerminalDemo) { Text("Terminal demo") }
            }
        }
    }
}

@Composable
private fun Destination(
    tag: String,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
) {
    ConsoleCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Fixed-width tag slot so every title starts at the same x regardless of tag length
            // ("hosts" vs "live" vs "config") — the labels line up in one column.
            Box(Modifier.width(64.dp)) {
                Tag(tag, color = accent)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    SshManagerTheme(darkTheme = true) {
        HomeScreen(onOpenConnections = {}, onOpenDebug = {})
    }
}
