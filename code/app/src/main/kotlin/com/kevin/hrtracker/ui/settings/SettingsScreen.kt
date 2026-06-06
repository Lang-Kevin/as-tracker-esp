package com.kevin.armswing.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val savedDeviceAddress by viewModel.savedDeviceAddress.collectAsStateWithLifecycle()
    val autoConnect by viewModel.autoConnect.collectAsStateWithLifecycle()
    val omegaThreshold by viewModel.omegaThreshold.collectAsStateWithLifecycle()
    var thresholdInput by remember(omegaThreshold) { mutableStateOf("%.2f".format(omegaThreshold)) }
    val isThresholdValid = thresholdInput.toFloatOrNull()?.let { it > 0f } == true
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Zurück") }
            Text("Einstellungen", style = MaterialTheme.typography.headlineMedium)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Gerät", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()

                if (savedDeviceAddress != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Gespeicherte Adresse",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(savedDeviceAddress!!, style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = { viewModel.clearSavedDevice() }) {
                            Text("Vergessen")
                        }
                    }
                } else {
                    Text(
                        "Kein Gerät gespeichert",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-Connect", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = autoConnect,
                        onCheckedChange = { viewModel.toggleAutoConnect() }
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Aufzeichnung", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                Text(
                    "Samples unterhalb des Schwellenwerts werden nicht gespeichert oder angezeigt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = thresholdInput,
                        onValueChange = { thresholdInput = it },
                        label = { Text("Schwellenwert") },
                        suffix = { Text("rad/s") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            thresholdInput.toFloatOrNull()?.takeIf { it > 0f }
                                ?.let { viewModel.setOmegaThreshold(it) }
                            focusManager.clearFocus()
                        }),
                        isError = !isThresholdValid,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            thresholdInput.toFloatOrNull()?.takeIf { it > 0f }
                                ?.let { viewModel.setOmegaThreshold(it) }
                            focusManager.clearFocus()
                        },
                        enabled = isThresholdValid
                    ) { Text("Speichern") }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Sensor-Info", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                InfoRow("Gerätename", "MPU6050_Sensor")
                InfoRow("Service UUID", "12345678-1234-1234-1234-1234567890ab")
                InfoRow("Char UUID", "87654321-4321-4321-4321-0987654321ba")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
