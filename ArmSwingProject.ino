#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <Wire.h>

// ESP32-C3 I2C-Pins (ggf. an dein Board anpassen)
#define SDA_PIN 6
#define SCL_PIN 7

// Akku-ADC: 100k/100k Spannungsteiler von VBAT nach GND, Mittelabgriff an GPIO3
// LiPo 3,7V: leer=3,0V → 1,5V am Pin  /  voll=4,2V → 2,1V am Pin
#define BATTERY_PIN 3
// Auf true setzen sobald 100k/100k Spannungsteiler an GPIO3 angeschlossen ist
#define BATTERY_DIVIDER_INSTALLED false

// Sleep-Taste auf GPIO5 (D5): Drücken → Deep Sleep, nochmals drücken → Aufwecken
// GPIO5 ist RTC-fähig (ESP32-C3 GPIO0–5), daher kein RST-Button nötig
#define SLEEP_BUTTON_PIN 5

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

Adafruit_MPU6050 mpu;

// Uncomment to include gyroMagnitudeDeg in BLE payload for debugging
// #define DEBUG_BLE

// ---------------- BLE ----------------
#define SERVICE_UUID        "12345678-1234-1234-1234-1234567890ab"
#define CHARACTERISTIC_UUID "87654321-4321-4321-4321-0987654321ba"
#define CONFIG_CHAR_UUID    "12345678-1234-1234-1234-1234567890cd"

// Standard BLE Battery Service (Bluetooth SIG)
#define BATTERY_SERVICE_UUID "0000180F-0000-1000-8000-00805F9B34FB"
#define BATTERY_CHAR_UUID    "00002A19-0000-1000-8000-00805F9B34FB"

BLEServer         *pServer          = nullptr;
BLECharacteristic *pCharacteristic  = nullptr;
BLECharacteristic *pConfigChar      = nullptr;
BLECharacteristic *pBatteryChar     = nullptr;

// volatile: written from BLE-Stack FreeRTOS task, read from main loop
volatile bool  deviceConnected      = false;
volatile bool  prevDeviceConnected  = false;
volatile float receivedSensorRadius = 0.35f;  // meters; updated via BLE write from Android

// ---------------- CALIB ----------------
float gyroBiasX = 0, gyroBiasY = 0, gyroBiasZ = 0;
const int CALIB_SAMPLES = 500;

// ---------------- CALLBACKS ----------------
class MyServerCallbacks : public BLEServerCallbacks {

  void onConnect(BLEServer* pServer) override {
    deviceConnected = true;
    Serial.println("[BLE] Client verbunden");
    // KEIN delay(), KEINE BLE-Calls hier — BLE-Stack-Task darf nicht blockiert werden
  }

  void onDisconnect(BLEServer* pServer) override {
    deviceConnected = false;
    Serial.println("[BLE] Client getrennt");
    // Advertising-Restart erfolgt in loop() nach Stack-Settle-Delay
  }
};

// ---------------- CONFIG CALLBACK ----------------
class MyConfigCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pChar) override {
    if (pChar->getLength() == 4) {
      float val;
      memcpy(&val, pChar->getData(), 4);
      if (val > 0.0f) {
        receivedSensorRadius = val;
        Serial.printf("[BLE] sensorRadius empfangen: %.4f m\n", val);
      }
    }
  }
};

// ---------------- CALIBRATION ----------------
void calibrateGyro() {

  Serial.printf("[CALIB] Starte Kalibrierung (%d Samples a 5ms = %.1fs)...\n",
                CALIB_SAMPLES, CALIB_SAMPLES * 5 / 1000.0f);

  float sx = 0, sy = 0, sz = 0;
  sensors_event_t a, g, temp;

  for (int i = 0; i < CALIB_SAMPLES; i++) {
    mpu.getEvent(&a, &g, &temp);
    sx += g.gyro.x;
    sy += g.gyro.y;
    sz += g.gyro.z;
    delay(5);
  }

  gyroBiasX = sx / CALIB_SAMPLES;
  gyroBiasY = sy / CALIB_SAMPLES;
  gyroBiasZ = sz / CALIB_SAMPLES;

  Serial.printf("[CALIB] Fertig – Bias X=%.4f Y=%.4f Z=%.4f rad/s\n",
                gyroBiasX, gyroBiasY, gyroBiasZ);
}

// ---------------- BATTERY ----------------
uint8_t readBatteryPercent() {
#if !BATTERY_DIVIDER_INSTALLED
  // Kein Spannungsteiler an GPIO3 → Messung deaktiviert
  // Hardware: 100k/100k von BAT+ (XIAO-Rückseite) über GPIO3 nach GND anschließen,
  //           dann BATTERY_DIVIDER_INSTALLED auf true setzen
  Serial.println("[BATT] Spannungsteiler nicht verbunden – sende 100%");
  return 100;
#else
  uint32_t sumMv  = 0;
  uint32_t sumRaw = 0;
  for (int i = 0; i < 8; i++) {
    sumMv  += analogReadMilliVolts(BATTERY_PIN);
    sumRaw += analogRead(BATTERY_PIN);
  }
  float pinMv  = sumMv  / 8.0f;
  uint16_t raw = sumRaw / 8;
  float vbatMv = pinMv * 2.0f;  // 100k/100k Teiler → ×2
  float pct    = (vbatMv - 3000.0f) / (4200.0f - 3000.0f) * 100.0f;
  uint8_t result = (uint8_t)constrain((int)pct, 0, 100);
  Serial.printf("[BATT] raw=%u  pin=%.0fmV  vbat=%.0fmV  %d%%\n", raw, pinMv, vbatMv, result);
  return result;
#endif
}

// ---------------- BLE SETUP ----------------
void setupBLE() {

  BLEDevice::init("MPU6050_Sensor");
  Serial.println("[BLE] Device initialisiert als 'MPU6050_Sensor'");

  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);

  pCharacteristic = pService->createCharacteristic(
    CHARACTERISTIC_UUID,
    BLECharacteristic::PROPERTY_NOTIFY
  );
  pCharacteristic->addDescriptor(new BLE2902());

  pConfigChar = pService->createCharacteristic(
    CONFIG_CHAR_UUID,
    BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
  );
  pConfigChar->setCallbacks(new MyConfigCallbacks());

  pService->start();

  // Battery Service
  BLEService *pBatteryService = pServer->createService(BATTERY_SERVICE_UUID);
  pBatteryChar = pBatteryService->createCharacteristic(
    BATTERY_CHAR_UUID,
    BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
  );
  pBatteryChar->addDescriptor(new BLE2902());
  uint8_t initBatt = readBatteryPercent();
  pBatteryChar->setValue(&initBatt, 1);
  pBatteryService->start();

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);  // iOS-Kompatibilität
  pAdvertising->setMaxPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.println("[BLE] Advertising aktiv – warte auf Client");
}

// ---------------- DEEP SLEEP ----------------
void enterDeepSleep() {
  Serial.println("[SLEEP] Taste D5 – Deep Sleep aktiv");
  Serial.println("[SLEEP] Taste D5 nochmals drücken zum Aufwecken");
  Serial.flush();
  // BLEDevice::deinit() NICHT aufrufen während aktiver Verbindung:
  // laufende notify()-Calls im BLE-Stack-Task führen zu Assertion-Fail → Neustart.
  // Deep Sleep schaltet BLE-Hardware komplett ab; setup() nach Wakeup initialisiert neu.
  while (digitalRead(SLEEP_BUTTON_PIN) == LOW) delay(10);  // Taste loslassen abwarten
  delay(50);  // Entprellung
  // GPIO5 (RTC-Pin) als Wakeup-Quelle: Taste zieht auf LOW → Aufwecken
  // ESP32-C3 hat kein EXT1 – stattdessen esp_deep_sleep_enable_gpio_wakeup()
  esp_deep_sleep_enable_gpio_wakeup(1ULL << SLEEP_BUTTON_PIN, ESP_GPIO_WAKEUP_GPIO_LOW);
  esp_deep_sleep_start();
}

// ---------------- SETUP ----------------
void setup() {

  Serial.begin(115200);
  unsigned long t0 = millis();
  while (!Serial && millis() - t0 < 3000) {}  // ESP32-C3 USB-CDC: bis zu 3s warten

  analogSetPinAttenuation(BATTERY_PIN, ADC_11db);  // 0–2,5V Eingangsbereich (ESP32-C3)
  Wire.begin(SDA_PIN, SCL_PIN);

  if (!mpu.begin()) {
    Serial.println("[ERROR] MPU6050 nicht gefunden – Programm gestoppt");
    while (1) delay(100);
  }
  Serial.println("[MPU] MPU6050 erkannt");
  mpu.setGyroRange(MPU6050_RANGE_500_DEG);
  mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);
  Serial.println("[MPU] Gyro Range=500dps, Filter=21Hz");

  delay(500);
  calibrateGyro();
  setupBLE();
  readBatteryPercent();  // einmalig beim Start für Diagnose

  pinMode(SLEEP_BUTTON_PIN, INPUT_PULLUP);
  Serial.println("[SLEEP] Sleep-Button auf GPIO5 (D5) aktiv – D5 drücken zum Aufwecken");

  Serial.println("[INIT] Bereit – sende Daten sobald Client verbunden");
}

// ---------------- LOOP ----------------
unsigned long packetCount        = 0;
unsigned long lastBattMs         = 0;
unsigned long lastSleepBtnMs     = 0;
const unsigned long BATT_INTERVAL_MS = 30000UL;

void loop() {

  // --- Sleep-Button: Polling statt ISR (Entprellung 500ms) ---
  if (digitalRead(SLEEP_BUTTON_PIN) == LOW && millis() - lastSleepBtnMs > 500) {
    lastSleepBtnMs = millis();
    enterDeepSleep();
  }

  // --- Reconnect-Handling (Zustandsübergänge, nicht im Callback!) ---
  if (!deviceConnected && prevDeviceConnected) {
    // Verbindung gerade verloren: BLE-Stack Zeit zum Aufräumen geben
    delay(500);
    pServer->startAdvertising();
    Serial.println("[BLE] Advertising neu gestartet – warte auf Reconnect");
    prevDeviceConnected = false;
  }

  if (deviceConnected && !prevDeviceConnected) {
    Serial.printf("[BLE] Verbindung aktiv (gesamt: %lu Pakete bisher)\n", packetCount);
    prevDeviceConnected = true;
  }

  if (!deviceConnected) {
    if (millis() % 2000 < 50) {
      Serial.println("[BLE] Warte auf Client...");
    }
    delay(50);
    return;
  }

  // --- Sensor lesen ---
  sensors_event_t a, g, temp;
  mpu.getEvent(&a, &g, &temp);

  float gx = g.gyro.x - gyroBiasX;
  float gy = g.gyro.y - gyroBiasY;
  float gz = g.gyro.z - gyroBiasZ;

  float gyroMagnitudeRad = sqrt(gx*gx + gy*gy + gz*gz);  // rad/s (Adafruit library)
  float velocityMps = gyroMagnitudeRad * receivedSensorRadius;

  // char-Buffer statt String-Klasse: kein Heap-Overhead
  char buf[32];
#ifdef DEBUG_BLE
  float gyroMagnitudeDeg = gyroMagnitudeRad * 180.0f / PI;
  snprintf(buf, sizeof(buf), "%.4f,%.4f", velocityMps, gyroMagnitudeDeg);
#else
  snprintf(buf, sizeof(buf), "%.4f", velocityMps);
#endif

  pCharacteristic->setValue(buf);
  pCharacteristic->notify();

  packetCount++;
  if (packetCount % 250 == 0) {
    Serial.printf("[DATA] %lu Pakete – vel=%.4f m/s  radius=%.3f m\n",
                  packetCount, velocityMps, receivedSensorRadius);
  }

  // Akku-Level alle 30s aktualisieren
  if (millis() - lastBattMs >= BATT_INTERVAL_MS) {
    lastBattMs = millis();
    uint8_t batt = readBatteryPercent();
    pBatteryChar->setValue(&batt, 1);
    if (deviceConnected) pBatteryChar->notify();
    Serial.printf("[BATT] %d%%\n", batt);
  }

  delay(20);
}
