#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <Wire.h>

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

Adafruit_MPU6050 mpu;

// ---------------- BLE ----------------
#define SERVICE_UUID        "12345678-1234-1234-1234-1234567890ab"
#define CHARACTERISTIC_UUID "87654321-4321-4321-4321-0987654321ba"

BLEServer         *pServer         = nullptr;
BLECharacteristic *pCharacteristic = nullptr;

// volatile: written from BLE-Stack FreeRTOS task, read from main loop
volatile bool deviceConnected     = false;
volatile bool prevDeviceConnected = false;

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
  pService->start();

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);  // iOS-Kompatibilität
  pAdvertising->setMaxPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.println("[BLE] Advertising aktiv – warte auf Client");
}

// ---------------- SETUP ----------------
void setup() {

  Serial.begin(115200);

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

  Serial.println("[INIT] Bereit – sende Daten sobald Client verbunden");
}

// ---------------- LOOP ----------------
unsigned long packetCount = 0;

void loop() {

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
    delay(50);
    return;
  }

  // --- Sensor lesen ---
  sensors_event_t a, g, temp;
  mpu.getEvent(&a, &g, &temp);

  float gx = g.gyro.x - gyroBiasX;
  float gy = g.gyro.y - gyroBiasY;
  float gz = g.gyro.z - gyroBiasZ;
  float omega = sqrt(gx*gx + gy*gy + gz*gz);

  unsigned long t = millis();

  // char-Buffer statt String-Klasse: kein Heap-Overhead
  char buf[32];
  snprintf(buf, sizeof(buf), "%lu,%.4f", t, omega);

  pCharacteristic->setValue(buf);
  pCharacteristic->notify();

  packetCount++;
  if (packetCount % 250 == 0) {
    Serial.printf("[DATA] %lu Pakete – t=%lums omega=%.3f rad/s\n", packetCount, t, omega);
  }

  delay(20);
}
