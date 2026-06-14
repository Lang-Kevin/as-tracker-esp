# ArmSwing Speed Tracker — Changelog

Chronologische Historie. **Nicht automatisch geladen** — gezielt lesen wenn historischer Kontext einer Entscheidung gebraucht wird.

---

## Prototype (2026-05-01)

Commit: `5607d4e`

Einfacher Prototyp: ESP32 streamt Rohwerte, Android-App zeigt diese an.

---

## Initial-Feature: BLE + Android-App (2026-05-10)

Commit: `6f86966`

- Erste vollständige Android-App (von HR-Tracker geklont)
- ESP32 Firmware mit Basis-BLE-Kommunikation
- Python-Plotter (Streamlit + Bleak) für Desktop-Visualisierung

---

## Icon + Umbenennung (2026-05-12)

Commit: `6766e97`

- Neues App-Icon (ChatGPT-generiert)
- App-Name von "HRTracker" auf "Arm Swing Tracker" geändert

---

## BLE Threshold Handshake (2026-05-20)

Commit: `a6304c2`

- BLE Config-Write Mechanismus implementiert
- Konfigurierbarer Omega-Filter
- Threshold-Flag-Handshake zwischen Android und ESP32

---

## Paketname-Refactor (2026-05-22)

Commit: `4f79057`

- Package `hrtracker` → `armswing` umbenannt
- Firmware: vereinfachter Threshold-Flag-Mechanismus

---

## Firmware M1 — Velocity-Berechnung (2026-05-25)

Commit: `d414d8c`

ESP32-Firmware berechnet nun Geschwindigkeit direkt:

- `gyroMagnitudeDeg = sqrt(gyroX² + gyroY² + gyroZ²)`
- `velocity_mps = gyroMagnitudeRad × sensorRadius`
- `sensorRadius` per Config-Char empfangbar (BLE-Write)
- Notify-Payload: `"velocity_mps"` (UTF-8 float)
- Default: `sensorRadius = 0.35f`

---

## Android M2 — Spielerprofil + sensorRadius (2026-05-28)

Commit: `76d4338`

- DataStore-Felder: `spineToShoulder`, `shoulderToElbow`, `sensorRadius`
- `ProfileScreen` + `ProfileViewModel` neu
- BleManager: Config-Write sendet `sensorRadius` statt `omegaThreshold`
- `omegaThreshold` vollständig entfernt

---

## Android M3 — VelocityReading + BleManager Refactor (2026-06-01)

Commit: `57a27f3`

- `OmegaReading` → `VelocityReading` (Feld: `velocityMps`)
- BleManager: Payload-Parsing auf `velocityMps`
- Fake-Device Sinuswelle als `velocityMps` (3 m/s)
- Handshake-Sequenz: CCCD → onDescriptorWrite → sensorRadius-Write → Ready
- `ConnectionState.Ready` als expliziter Zustand nach Config-Write

---

## Android M4 — VelocitySample + Room DB v2 + Session-Stats (2026-06-05)

Commit: `e0730dc`

- `OmegaSample` → `VelocitySample` (Feld: `velocityMps`)
- `OmegaSampleDao` → `VelocitySampleDao`
- `Session` erweitert: `peakMps`, `avgMps`, `sampleCount`
- Room-Migration 1→2 für neues Schema
- `ArmSwingDatabase` v2

---

## Android M5 — Live-Screen (2026-06-08)

Commit: `9aea6fd`

- Live-Anzeige: aktuelle m/s + km/h
- Session-Peak-Anzeige
- Rolling-Chart (300 Punkte, Vico) auf `velocityMps`
- `LiveViewModel` aktualisiert: `velocityMps`, `velocityKmh`, `peakMps`

---

## Android M6 — Export + Cleanup (omega → velocity) (2026-06-10)

Commit: `703ee71`

- `SessionExporter`: CSV-Header angepasst (omega → velocity)
- History-Screen: Statistiken aus neuen Session-Feldern (`peakMps`, `avgMps`)
- Detail-Screen: Velocity-Chart, Statistiken
- Verbleibende `omega`-Referenzen bereinigt

---

## Shared-Android-Lib Migration (2026-06-12)

Commit: `7a12863`

- Code in `code/`-Unterverzeichnis verschoben (shared-android-lib-Konvention)
- Projekt-Struktur vereinheitlicht mit TrackerApp-Referenz

---

## Offen

- **M7 — Python-Plotter** (`scanner.py`): Payload-Parsing auf neues Format anpassen
- **Akku-ADC**: Spannungsteiler anlöten + `BATTERY_DIVIDER_INSTALLED true` setzen
- **Wear-Companion**: Optional — kein konkretes Datum
