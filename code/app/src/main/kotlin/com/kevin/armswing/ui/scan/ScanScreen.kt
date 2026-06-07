package com.kevin.armswing.ui.scan

import android.Manifest
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.kevin.armswing.ble.ConnectionState
import com.kevin.armswing.ble.ConnectionState.Reconnecting
import com.kevin.armswing.domain.DiscoveredDevice
import com.kevin.armswing.domain.SavedDevice

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(
    viewModel: ScanViewModel = hiltViewModel(),
    onSessionStarted: (label: String) -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val savedDevices by viewModel.savedDevices.collectAsStateWithLifecycle()
    val autoConnect by viewModel.autoConnect.collectAsStateWithLifecycle()

    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permissionState = rememberMultiplePermissionsState(requiredPermissions)

    LaunchedEffect(permissionState.allPermissionsGranted) {
        if (permissionState.allPermissionsGranted) viewModel.startScan()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Arm Swing Tracker", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onNavigateToHistory) { Text("Verlauf") }
            TextButton(onClick = onNavigateToSettings) { Text("⚙") }
        }

        val statusText = when (connectionState) {
            is Reconnecting -> "Verbindung verloren — reconnecting…"
            else -> connectionState::class.simpleName ?: ""
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text("Status: $statusText", style = MaterialTheme.typography.bodyMedium)
                if (connectionState !is ConnectionState.Disconnected) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { viewModel.disconnect() }) { Text("Trennen") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-Connect", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(4.dp))
                Switch(
                    checked = autoConnect,
                    onCheckedChange = { viewModel.toggleAutoConnect() }
                )
            }
        }

        if (!permissionState.allPermissionsGranted) {
            Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                Text("Bluetooth-Berechtigung gewähren")
            }
        } else {
            if (connectionState is ConnectionState.Ready) {
                HorizontalDivider()
                if (activeSessionId == null) {
                    Button(
                        onClick = { onSessionStarted("Training") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Aufzeichnung starten")
                    }
                } else {
                    Button(
                        onClick = { viewModel.stopSession() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Session stoppen  (ID: $activeSessionId)")
                    }
                }
                HorizontalDivider()
            }

            if (savedDevices.isNotEmpty()) {
                Text("Gemerkte Geräte", style = MaterialTheme.typography.titleSmall)
                savedDevices.forEach { device ->
                    SavedDeviceItem(
                        device = device,
                        onClick = { viewModel.connectToSaved(device) },
                        onForget = { viewModel.forgetDevice(device) }
                    )
                }
                HorizontalDivider()
            }

            Text("Verfügbare Sensoren:", style = MaterialTheme.typography.titleSmall)
            if (discoveredDevices.none { it is DiscoveredDevice.Real }) {
                Text("Scan läuft…", style = MaterialTheme.typography.bodySmall)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(discoveredDevices, key = { it.address }) { device ->
                    DeviceItem(device = device) { viewModel.connectToDiscovered(device) }
                }
            }
        }
    }
}

@Composable
private fun SavedDeviceItem(device: SavedDevice, onClick: () -> Unit, onForget: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).clickable(onClick = onClick)) {
                Text(device.name, style = MaterialTheme.typography.bodyLarge)
                Text(device.address, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onForget) { Text("Vergessen") }
        }
    }
}

@Composable
private fun DeviceItem(device: DiscoveredDevice, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(device.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (device is DiscoveredDevice.Fake) "Simuliertes Testgerät" else device.address,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
