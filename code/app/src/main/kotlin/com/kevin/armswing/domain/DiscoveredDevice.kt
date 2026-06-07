package com.kevin.armswing.domain

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import com.kevin.armswing.ble.BleManager

sealed class DiscoveredDevice {
    abstract val address: String
    abstract val displayName: String

    data class Real(val scanResult: ScanResult) : DiscoveredDevice() {
        @get:SuppressLint("MissingPermission")
        override val address: String get() = scanResult.device.address
        @get:SuppressLint("MissingPermission")
        override val displayName: String get() = scanResult.device.name ?: "Unbekanntes Gerät"
    }

    object Fake : DiscoveredDevice() {
        override val address: String = BleManager.FAKE_DEVICE_ADDRESS
        override val displayName: String = BleManager.FAKE_DEVICE_NAME
    }
}
