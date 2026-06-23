package com.larateam.sshmanager.ui.connections

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larateam.sshmanager.data.crypto.BiometricGate
import com.larateam.sshmanager.data.model.AuthMethod
import com.larateam.sshmanager.ui.components.Eyebrow
import com.larateam.sshmanager.ui.components.StatusDot
import com.larateam.sshmanager.ui.theme.StatusKind
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionEditScreen(
    onDone: () -> Unit,
    viewModel: ConnectionEditViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findFragmentActivity()
    val scope = rememberCoroutineScope()
    val gate = remember { BiometricGate() }
    var authMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(form.saved) {
        if (form.saved) onDone()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (form.isEditing) "Edit connection" else "Add connection") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.host,
                onValueChange = viewModel::onHostChange,
                label = { Text("Host") },
                singleLine = true,
                isError = form.hostError != null,
                supportingText = form.hostError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text("Username") },
                singleLine = true,
                isError = form.usernameError != null,
                supportingText = form.usernameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.port,
                onValueChange = viewModel::onPortChange,
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = form.portError != null,
                supportingText = form.portError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Eyebrow("Authentication", color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AuthMethod.entries.forEachIndexed { index, method ->
                    SegmentedButton(
                        selected = form.authMethod == method,
                        onClick = { viewModel.onAuthMethodChange(method) },
                        shape = SegmentedButtonDefaults.itemShape(index, AuthMethod.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            activeBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(method.label)
                    }
                }
            }

            when (form.authMethod) {
                AuthMethod.KEY -> OutlinedTextField(
                    value = form.keyAlias,
                    onValueChange = viewModel::onKeyAliasChange,
                    label = { Text("Key alias / reference (optional)") },
                    singleLine = true,
                    supportingText = { Text("A reference only — no key material is stored for this method.") },
                    modifier = Modifier.fillMaxWidth(),
                )

                AuthMethod.IN_APP_KEY -> {
                    OutlinedTextField(
                        value = form.keyAlias,
                        onValueChange = viewModel::onKeyAliasChange,
                        label = { Text("Key name") },
                        singleLine = true,
                        isError = form.keyAliasError != null,
                        supportingText = form.keyAliasError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.keyMaterial,
                        onValueChange = viewModel::onKeyMaterialChange,
                        label = { Text("Private key (paste to import)") },
                        minLines = 3,
                        // Key material is case-sensitive base64 — never autocorrect/auto-capitalize it.
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            autoCorrectEnabled = false,
                            capitalization = KeyboardCapitalization.None,
                        ),
                        supportingText = {
                            Text("Encrypted with the Android Keystore (AES-GCM) and stored as ciphertext only — never in plaintext.")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (form.hasStoredKey) {
                        Text("A key is stored for \"${form.keyAlias}\" (encrypted).")
                        OutlinedButton(
                            onClick = {
                                val act = activity
                                if (act == null) {
                                    authMessage = "Cannot authenticate in this context."
                                    return@OutlinedButton
                                }
                                scope.launch {
                                    authMessage = null
                                    when (val r = gate.authenticate(
                                        act,
                                        title = "Unlock stored key",
                                        subtitle = "Authenticate to reveal this key",
                                    )) {
                                        BiometricGate.Outcome.Success -> viewModel.revealAfterAuth()
                                        BiometricGate.Outcome.Unavailable ->
                                            authMessage = "Set up a screen lock or biometric to reveal stored keys."
                                        is BiometricGate.Outcome.Error ->
                                            authMessage = "Authentication failed: ${r.message}"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Reveal stored key (requires unlock)")
                        }
                    }
                    authMessage?.let { Text(it) }
                }

                AuthMethod.PASSWORD -> {
                    OutlinedTextField(
                        value = form.password,
                        onValueChange = viewModel::onPasswordChange,
                        label = { Text(if (form.hasSavedPassword) "New password (replace saved)" else "Password (optional — saved encrypted)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false,
                            capitalization = KeyboardCapitalization.None,
                        ),
                        supportingText = {
                            Text(
                                "Leave blank to be asked at connect time. If set, it's encrypted with the Android " +
                                    "Keystore (AES-GCM) and unlocked with your fingerprint or device PIN each connect — " +
                                    "never stored in plaintext.",
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (form.hasSavedPassword) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusDot(StatusKind.LIVE)
                            Text(
                                "A password is saved (encrypted) — unlock required to connect.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = viewModel::removeSavedPassword, modifier = Modifier.fillMaxWidth()) {
                            Text("Remove saved password")
                        }
                    }
                }
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }

    form.revealedKey?.let { key ->
        AlertDialog(
            onDismissRequest = viewModel::clearRevealed,
            title = { Text("Stored key (decrypted)") },
            text = { Text(text = key, fontFamily = FontFamily.Monospace) },
            confirmButton = { TextButton(onClick = viewModel::clearRevealed) { Text("Close") } },
        )
    }
}

private val AuthMethod.label: String
    get() = when (this) {
        AuthMethod.KEY -> "Key"
        AuthMethod.PASSWORD -> "Password"
        AuthMethod.IN_APP_KEY -> "In-app key"
    }

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
