# ArmSwing Speed Tracker — Spezifikation

Referenzdokument. **Nicht automatisch geladen** — gezielt mit `@docs/SPEC.md` referenzieren oder vom `@explorer`-Subagenten lesen lassen.

## Hardware

| Komponente | Details |
|---|---|
| Board | Seeed Studio XIAO ESP32-C3 |
| Sensor | MPU6050 (I2C), mittig Oberarm |
| BLE-Adresse | `F0:08:D1:D3:20:A2`, Name: `MPU6050_Sensor` |
| Gyro-Range | 500 dps, Filter 21 Hz |
| Kalibrierung | 500 Samples × 5 ms beim Boot |
| Akku | 3,7V LiPo 350mAh, JST-Pins XIAO |

### Akku-Messung (Battery ADC)

- XIAO ESP32-C3: **keine** interne BAT+→ADC-Verbindung
- Geplant: 100k/100k Spannungsteiler BAT+-Pad → GPIO3 (A1) → GND
- **Aktuell deaktiviert** (`BATTERY_DIVIDER_INSTALLED false`) → sendet 100%
- Aktivieren: Widerstände anlöten + `#define BATTERY_DIVIDER_INSTALLED true`
- Formel: `vbat = analogReadMilliVolts(GPIO3) × 2`, Bereich 3000–4200 mV = 0–100%

## Tech-Stack

| Bereich | Technologie |
|---|---|
| Sprache | Kotlin |
| UI | Jetpack Compose |
| Min SDK | 26 |
| Target SDK | 34 |
| Async | Coroutines + Flow |
| Datenbank | Room |
| BLE | Android BLE API (gatt direkt) |
| Charts | Vico (`OmegaLineChart`) |
| DI | Hilt |
| Profil-Persistenz | DataStore |
| Firmware | C++ (Arduino IDE) |
| Python-Tool | Streamlit + Bleak (`scanner.py`) |

## ESP32 — Velocity-Berechnung

```
gyroMagnitudeDeg = sqrt(gyroX² + gyroY² + gyroZ²)
gyroMagnitudeRad = gyroMagnitudeDeg × π / 180
velocity_mps     = gyroMagnitudeRad × sensorRadius
```

`sensorRadius` kommt vom Android-Profil per BLE-Write beim Connect.

## BLE Protocol

| Rolle | UUID |
|---|---|
| Service | `12345678-1234-1234-1234-1234567890ab` |
| Notify (ESP→Android) | `87654321-4321-4321-4321-0987654321ba` |
| Config (Android→ESP) | `12345678-1234-1234-1234-1234567890cd` |

### Notify-Payload (ESP→Android)

Format: `"velocity_mps[,gyroMagnitudeDeg]"` (UTF-8)
- Pflicht: `velocity_mps` (float, 4 Dezimalstellen)
- Optional: `gyroMagnitudeDeg` (Debug, wenn aktiviert)
- Beispiel: `"1.2340"` oder `"1.2340,45.6789"`

> **Naming-Konvention:** Wire-Format (ESP32-Payload-String) verwendet `velocity_mps` (snake_case).
> Kotlin-Feld nach dem Parsen heißt `velocityMps` (camelCase). Beide Namen bezeichnen dieselbe Größe.

### Config-Write (Android→ESP)

- 4-Byte IEEE 754 float, little-endian = `sensorRadius` [m]
- ESP-Default (ohne Write): `sensorRadius = 0.35f`
- Config-Char: `PROPERTY_WRITE | PROPERTY_WRITE_NR`

### Handshake-Sequenz

```
connect → discoverServices → setCharacteristicNotification(notifyChar)
  → writeDescriptor(CCCD, ENABLE_NOTIFICATION)
  → onDescriptorWrite → DataStore lesen (spineToShoulder, shoulderToElbow)
  → sensorRadius berechnen → writeConfigToDevice
  → ConnectionState.Ready → Daten fließen
```

Live-Update: Profiländerungen pushen sofort neuen `sensorRadius` per BLE (`.drop(1)`).

### ConnectionState

`Disconnected → Connecting → Connected → Ready → Reconnecting`

Reconnect: exponentieller Backoff `[3s, 5s, 10s, 30s]`.

## Nutzer-Profil

DataStore-Felder:

| Feld | Typ | Bedeutung |
|---|---|---|
| `spineToShoulder` | Float (cm) | Wirbelsäule → Schulter |
| `shoulderToElbow` | Float (cm) | Schulter → Ellenbogen |
| `sensorRadius` | Float (m) | Berechnet, intern gespeichert |

Formel: `sensorRadius = (spineToShoulder + shoulderToElbow / 2) / 100`

## Datenmodell

```kotlin
VelocitySample(id, sessionId, timestampMs, velocityMps: Float)   // Room
Session(id, label, startedAt, endedAt, peakMps: Float, avgMps: Float, sampleCount: Int, note)  // Room
```

- DB: `arm_swing.db`, `ArmSwingDatabase`
- DB-Version 2 (Migration 1→2 für `VelocitySample`)
- `SettingsRepository`: Profil-Daten + BLE-Gerätepräferenz

## Screens

| Screen | Inhalt |
|---|---|
| Scan | Geräteliste, Verbindungsstatus, Auto-Reconnect |
| Live | Aktuelle m/s + km/h, Session-Peak, Rolling-Chart (300 Punkte) |
| History | Sessionliste, Statistik-Card, Swipe-to-Delete |
| Detail | Velocity-Chart, Statistiken, Notiz, Label-Edit |
| Profile | spineToShoulder, shoulderToElbow eingeben/bearbeiten |
| Settings | BLE-Gerätepräferenz, sonstige App-Einstellungen |

Navigation: `Scan → Live → History → Detail`, `Settings` + `Profile` als Tabs/Top-Level.

## BleManager — Implementierungsdetails

- API 33+: `gatt.writeCharacteristic(char, bytes, WRITE_TYPE_DEFAULT)` / `gatt.writeDescriptor`
- API <33: deprecated `.value` + `gatt.writeCharacteristic/writeDescriptor`
- Fake-Device: `FA:CE:00:00:00:01` — Sinuswelle 3 m/s, 100 ms-Intervalle
- Device-Address-Guard: Fremde Geräte in `onCharacteristicChanged` mit Warn-Log verwerfen

## Manifest

```xml
android:name=".ArmSwingApplication"
<service android:name=".service.ArmSwingRecordingService"
         android:foregroundServiceType="connectedDevice"/>
```

## Permissions

```
Android 12+: BLUETOOTH_SCAN, BLUETOOTH_CONNECT
< 12:        BLUETOOTH, BLUETOOTH_ADMIN, ACCESS_FINE_LOCATION
Immer:       FOREGROUND_SERVICE, FOREGROUND_SERVICE_CONNECTED_DEVICE, POST_NOTIFICATIONS
```

## Firmware — ArmSwingProject.ino

- `volatile float receivedRadius = 0.35f` — überschrieben durch Config-Write
- Reconnect-State-Machine in `loop()`: disconnect → 500 ms → `startAdvertising()`
- Data-Log alle 250 Pakete via `Serial.printf`
- Kalibrierung: 500 Samples × 5 ms beim Boot

## Python-Tool (scanner.py)

- Streamlit + Bleak, BLE-Thread → Queue → Main-Thread rendert ~10 Hz
- Singleton in `sys.modules['__arm_swing_ble__']`
- `width="stretch"` (Streamlit 1.58 API)
- Payload-Parsing: `velocity_mps[,gyroMagnitudeDeg]`

## Technische Ziele

- Minimale BLE-Datenmenge (nur `velocity_mps` als Pflicht)
- Berechnungen auf dem ESP32
- Android primär für UI, Speicherung, Analyse
- Geringer Stromverbrauch (LiPo 350mAh)
