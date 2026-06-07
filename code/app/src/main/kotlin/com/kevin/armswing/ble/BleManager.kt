package com.kevin.armswing.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.kevin.armswing.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    object Ready : ConnectionState()
    object Reconnecting : ConnectionState()
}

@Singleton
class BleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "ArmSwing"
        val MPU_SERVICE_UUID: UUID    = UUID.fromString("12345678-1234-1234-1234-1234567890ab")
        val MPU_CHAR_UUID: UUID       = UUID.fromString("87654321-4321-4321-4321-0987654321ba")
        val THRESHOLD_CHAR_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-1234567890cd")
        val CCCD_UUID: UUID           = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        private val RECONNECT_DELAYS_MS = listOf(3_000L, 5_000L, 10_000L, 30_000L)
        const val FAKE_DEVICE_ADDRESS = "FA:CE:00:00:00:01"
        const val FAKE_DEVICE_NAME = "Pseudo-MPU6050 [Test]"
        private const val REAL_DEVICE_NAME = "MPU6050_Sensor"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Push threshold changes to device immediately when already connected.
        // drop(1) skips the initial emission — the connect handshake already sends it.
        scope.launch {
            settingsRepository.omegaThreshold.drop(1).collect { threshold ->
                val gatt = bluetoothGatt ?: return@collect
                if (_connectionState.value is ConnectionState.Ready) {
                    writeThresholdToDevice(gatt, threshold)
                }
            }
        }
    }

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _omegaReadings = MutableSharedFlow<OmegaReading>(replay = 0, extraBufferCapacity = 64)
    val omegaReadings: SharedFlow<OmegaReading> = _omegaReadings.asSharedFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null
    private var lastDevice: BluetoothDevice? = null
    private var reconnectEnabled = false
    private var reconnectJob: Job? = null

    @Volatile private var isFakeActive = false
    private var fakeJob: Job? = null

    @SuppressLint("MissingPermission")
    fun startScan() {
        _scanResults.value = emptyList()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val scanner = adapter.bluetoothLeScanner ?: run {
            Log.e(TAG, "BLE scanner not available — Bluetooth off?")
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val hasService = result.scanRecord?.serviceUuids
                    ?.any { it.uuid == MPU_SERVICE_UUID } == true
                val hasName = result.device.name == REAL_DEVICE_NAME
                if (!hasService && !hasName) return
                val current = _scanResults.value
                if (current.none { it.device.address == result.device.address }) {
                    _scanResults.value = current + result
                }
            }
            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: errorCode=$errorCode")
            }
        }
        scanCallback = cb
        scanner.startScan(emptyList(), settings, cb)
        Log.d(TAG, "BLE scan started (MPU_SERVICE_UUID or device name filter)")
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        scanCallback?.let { adapter.bluetoothLeScanner?.stopScan(it) }
        scanCallback = null
    }

    @SuppressLint("MissingPermission")
    fun connectToAddress(address: String) {
        if (address == FAKE_DEVICE_ADDRESS) {
            connectFake()
            return
        }
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        connect(adapter.getRemoteDevice(address))
    }

    fun connectFake() {
        reconnectEnabled = false
        reconnectJob?.cancel()
        reconnectJob = null
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        isFakeActive = false
        fakeJob?.cancel()
        isFakeActive = true
        _connectionState.value = ConnectionState.Ready
        startFakeEmission()
        Log.d(TAG, "Fake MPU6050 device connected")
    }

    private fun startFakeEmission() {
        fakeJob = scope.launch {
            var t = 0
            val startMs = System.currentTimeMillis()
            while (isFakeActive) {
                val omega = (3.0 * sin(t * 2 * PI / 20)).toFloat()
                val tsMs = startMs + t * 100L
                _omegaReadings.emit(OmegaReading(tsMs, omega))
                delay(100L)
                t++
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        lastDevice = device
        reconnectEnabled = true
        reconnectJob?.cancel()
        _connectionState.value = ConnectionState.Connecting
        bluetoothGatt = device.connectGatt(
            context, false, gattCallback, BluetoothDevice.TRANSPORT_LE
        )
        Log.d(TAG, "Connecting to ${device.address}")
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        isFakeActive = false
        fakeJob?.cancel()
        fakeJob = null
        reconnectEnabled = false
        reconnectJob?.cancel()
        reconnectJob = null
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = ConnectionState.Disconnected
        Log.d(TAG, "Disconnected (user-initiated)")
    }

    @SuppressLint("MissingPermission")
    private fun scheduleReconnect() {
        val device = lastDevice ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            for ((attempt, delayMs) in RECONNECT_DELAYS_MS.withIndex()) {
                if (!reconnectEnabled) break
                Log.d(TAG, "Reconnect attempt ${attempt + 1}/${RECONNECT_DELAYS_MS.size} in ${delayMs}ms")
                _connectionState.value = ConnectionState.Reconnecting
                delay(delayMs)
                if (!reconnectEnabled) break
                bluetoothGatt?.close()
                bluetoothGatt = device.connectGatt(
                    context, false, gattCallback, BluetoothDevice.TRANSPORT_LE
                )
                var waited = 0
                while (waited < 8_000 && reconnectEnabled &&
                    _connectionState.value is ConnectionState.Reconnecting
                ) {
                    delay(500)
                    waited += 500
                }
                if (_connectionState.value is ConnectionState.Ready) {
                    Log.d(TAG, "Reconnected successfully after attempt ${attempt + 1}")
                    return@launch
                }
            }
            if (_connectionState.value !is ConnectionState.Ready) {
                Log.w(TAG, "Reconnect failed after all attempts — giving up")
                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "GATT connected — discovering services")
                    _connectionState.value = ConnectionState.Connected
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "GATT disconnected (status=$status)")
                    gatt.close()
                    bluetoothGatt = null
                    if (reconnectEnabled) {
                        Log.d(TAG, "Unexpected disconnect — scheduling reconnect")
                        scheduleReconnect()
                    } else {
                        _connectionState.value = ConnectionState.Disconnected
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                return
            }
            val allServices = gatt.services.map { it.uuid.toString().uppercase().take(8) }
            Log.d(TAG, "Services discovered: $allServices")
            val mpuChar = gatt.getService(MPU_SERVICE_UUID)
                ?.getCharacteristic(MPU_CHAR_UUID)
            if (mpuChar == null) {
                Log.e(TAG, "MPU characteristic not found")
                return
            }
            gatt.setCharacteristicNotification(mpuChar, true)
            val cccd = mpuChar.getDescriptor(CCCD_UUID)
            if (cccd == null) {
                Log.e(TAG, "CCCD descriptor (0x2902) not found")
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(cccd)
            }
            Log.d(TAG, "CCCD written — MPU notifications enabled")
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int
        ) {
            Log.d(TAG, "Descriptor write ${descriptor.uuid} status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) return
            scope.launch {
                val threshold = settingsRepository.omegaThreshold.first()
                writeThresholdToDevice(gatt, threshold)
                _connectionState.value = ConnectionState.Ready
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            if (characteristic.uuid == THRESHOLD_CHAR_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Threshold write confirmed by device")
                } else {
                    Log.e(TAG, "Threshold write FAILED: status=$status")
                }
            }
        }

        // API 33+
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == MPU_CHAR_UUID) handleOmegaData(value)
        }

        // API < 33
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                && characteristic.uuid == MPU_CHAR_UUID
            ) {
                handleOmegaData(characteristic.value ?: return)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeThresholdToDevice(gatt: BluetoothGatt, threshold: Float) {
        val char = gatt.getService(MPU_SERVICE_UUID)
            ?.getCharacteristic(THRESHOLD_CHAR_UUID)
            ?: run { Log.e(TAG, "Threshold char not found"); return }
        val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(threshold).array()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            char.value = bytes
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }
        Log.d(TAG, "Threshold $threshold rad/s → device")
    }

    private fun handleOmegaData(value: ByteArray) {
        val raw = String(value, Charsets.UTF_8).trim()
        val parts = raw.split(",")
        if (parts.size < 2) return
        val tsMs = parts[0].toLongOrNull() ?: return
        val omega = parts[1].toFloatOrNull() ?: return
        Log.d(TAG, "omega=$omega rad/s  ts=$tsMs")
        scope.launch { _omegaReadings.emit(OmegaReading(tsMs, omega)) }
    }
}
