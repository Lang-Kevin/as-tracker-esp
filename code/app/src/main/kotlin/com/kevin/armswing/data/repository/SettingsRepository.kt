package com.kevin.armswing.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kevin.shared.domain.SavedDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val SAVED_DEVICE_MAC   = stringPreferencesKey("saved_device_mac")
        val SAVED_DEVICES      = stringPreferencesKey("saved_devices")
        val AUTO_CONNECT       = booleanPreferencesKey("auto_connect")
        val SPINE_TO_SHOULDER  = floatPreferencesKey("spine_to_shoulder")
        val SHOULDER_TO_ELBOW  = floatPreferencesKey("shoulder_to_elbow")
    }

    val savedDeviceAddress: Flow<String?> = dataStore.data.map { it[Keys.SAVED_DEVICE_MAC] }

    suspend fun saveDeviceAddress(address: String) {
        dataStore.edit { it[Keys.SAVED_DEVICE_MAC] = address }
    }

    suspend fun clearSavedDevice() {
        dataStore.edit { it.remove(Keys.SAVED_DEVICE_MAC) }
    }

    private fun Preferences.decodeDevices(): List<SavedDevice> =
        get(Keys.SAVED_DEVICES)?.let {
            runCatching { Json.decodeFromString<List<SavedDevice>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()

    val savedDevices: Flow<List<SavedDevice>> = dataStore.data.map { it.decodeDevices() }

    suspend fun addSavedDevice(device: SavedDevice) {
        dataStore.edit { prefs ->
            val current = prefs.decodeDevices()
            prefs[Keys.SAVED_DEVICES] = Json.encodeToString(
                listOf(device) + current.filter { it.address != device.address }
            )
        }
    }

    suspend fun removeSavedDevice(address: String) {
        dataStore.edit { prefs ->
            prefs[Keys.SAVED_DEVICES] = Json.encodeToString(
                prefs.decodeDevices().filter { it.address != address }
            )
        }
    }

    val autoConnect: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_CONNECT] ?: false }

    suspend fun setAutoConnect(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_CONNECT] = enabled }
    }

    val spineToShoulder: Flow<Float> = dataStore.data.map { it[Keys.SPINE_TO_SHOULDER] ?: 20f }

    suspend fun setSpineToShoulder(cm: Float) {
        dataStore.edit { it[Keys.SPINE_TO_SHOULDER] = cm }
    }

    val shoulderToElbow: Flow<Float> = dataStore.data.map { it[Keys.SHOULDER_TO_ELBOW] ?: 30f }

    suspend fun setShoulderToElbow(cm: Float) {
        dataStore.edit { it[Keys.SHOULDER_TO_ELBOW] = cm }
    }

    // sensorRadius in meters: spineâ†’shoulder + shoulderâ†’elbow/2, converted from cm
    val sensorRadius: Flow<Float> = dataStore.data.map { prefs ->
        val spine = prefs[Keys.SPINE_TO_SHOULDER] ?: 20f
        val elbow = prefs[Keys.SHOULDER_TO_ELBOW] ?: 30f
        (spine + elbow / 2f) / 100f
    }
}
