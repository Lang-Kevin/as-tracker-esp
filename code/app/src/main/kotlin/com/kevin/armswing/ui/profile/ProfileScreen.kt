package com.kevin.armswing.ui.profile

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
fun ProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val storedSpine by viewModel.spineToShoulder.collectAsStateWithLifecycle()
    val storedElbow by viewModel.shoulderToElbow.collectAsStateWithLifecycle()
    val sensorRadius by viewModel.sensorRadius.collectAsStateWithLifecycle()

    var spineInput by remember(storedSpine) { mutableStateOf("%.1f".format(storedSpine)) }
    var elbowInput by remember(storedElbow) { mutableStateOf("%.1f".format(storedElbow)) }

    val spineValue = spineInput.toFloatOrNull()
    val elbowValue = elbowInput.toFloatOrNull()
    val isValid = spineValue != null && spineValue > 0f && elbowValue != null && elbowValue > 0f

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
            Text("Spielerprofil", style = MaterialTheme.typography.headlineMedium)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Körpermaße", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                Text(
                    "Der Sensor sitzt mittig auf dem Oberarm. Aus diesen Maßen wird der Hebelarm (sensorRadius) berechnet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = spineInput,
                    onValueChange = { spineInput = it },
                    label = { Text("Wirbelsäule → Schulter") },
                    suffix = { Text("cm") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    isError = spineValue == null || spineValue <= 0f,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = elbowInput,
                    onValueChange = { elbowInput = it },
                    label = { Text("Schulter → Ellenbogen") },
                    suffix = { Text("cm") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                    }),
                    isError = elbowValue == null || elbowValue <= 0f,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Berechneter Sensor-Radius", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()

                val previewRadius = if (isValid) {
                    (spineValue!! + elbowValue!! / 2f) / 100f
                } else {
                    sensorRadius
                }
                Text(
                    "%.3f m".format(previewRadius),
                    style = MaterialTheme.typography.displaySmall
                )
                Text(
                    "= (Wirbelsäule→Schulter + Schulter→Ellenbogen / 2) / 100",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = {
                if (isValid) {
                    viewModel.save(spineValue!!, elbowValue!!)
                    focusManager.clearFocus()
                    onBack()
                }
            },
            enabled = isValid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Speichern")
        }
    }
}
