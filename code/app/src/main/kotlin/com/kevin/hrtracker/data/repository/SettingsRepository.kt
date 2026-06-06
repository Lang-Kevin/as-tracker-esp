package com.kevin.armswing.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kevin.armswing.domain.SavedDevice
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
        val SAVED_DEVICE_MAC = stringPreferencesKey("saved_device_mac")
        val SAVED_DEVICES    = stringPreferencesKey("saved_devices")
        val AUTO_CONNECT     = booleanPreferencesKey("auto_connect")
    }

    val savedDeviceAddress: Flow<String?> = dataStore.data.map { it[Keys.SAVED_DEVICE_MAC] }

    suspend fun saveDeviceAddress(address: String) {
        dataStore.edit { it[Keys.SAVED_DEVICE_MAC] = address }
    }

    suspend fun clearSavedDevice() {
        dataStore.edit { it.remove(Keys.SAVED_DEVICE_MAC) }
    }

    val savedDevices: Flow<List<SavedDevice>> = dataStore.data.map { prefs ->
        prefs[Keys.SAVED_DEVICES]?.let { json ->
            runCatching { Json.decodeFromString<List<SavedDevice>>(json) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun addSavedDevice(device: SavedDevice) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.SAVED_DEVICES]?.let {
                runCatching { Json.decodeFromString<List<SavedDevice>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[Keys.SAVED_DEVICES] = Json.encodeToString(
                listOf(device) + current.filter { it.address != device.address }
            )
        }
    }

    suspend fun removeSavedDevice(address: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.SAVED_DEVICES]?.let {
                runCatching { Json.decodeFromString<List<SavedDevice>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[Keys.SAVED_DEVICES] = Json.encodeToString(current.filter { it.address != address })
        }
    }

    val autoConnect: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_CONNECT] ?: false }

    suspend fun setAutoConnect(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_CONNECT] = enabled }
    }
}
