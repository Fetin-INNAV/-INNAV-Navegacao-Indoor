/*
 * ============================================================================
 * PROJETO INNAV - NAVEGAÇÃO INDOOR PARA DEFICIENTES VISUAIS
 * FIRMWARE PARA ESP32 (BEACON BLE iBEACON / GAP)
 * ============================================================================
 * 
 * Compatível com:
 *  - ESP32-WROOM-32 (4 unidades)
 *  - ESP32 Genérico / Dev Module / ESP32-CAM / ESP32-S/C (1 unidade)
 * 
 * Instruções:
 *  1. Abra o arquivo 'config.h' no mesmo diretório.
 *  2. Altere o valor de '#define BEACON_INDEX 1' para 1, 2, 3, 4 ou 5.
 *  3. Compile e envie para a placa ESP32 correspondente.
 * ============================================================================
 */

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLEBeacon.h>
#include "config.h"

BLEAdvertising *pAdvertising;

void setupIBeaconData(BLEBeacon &beacon, const BeaconConfig &cfg) {
    // Definir identificadores iBeacon
    BLEUUID uuid = BLEUUID(BEACON_UUID);
    beacon.setManufacturerId(0x004C); // Apple Inc. ID para iBeacon
    beacon.setProximityUUID(uuid);
    beacon.setMajor(cfg.major);
    beacon.setMinor(cfg.minor);
    beacon.setSignalPower(ESP32_TX_POWER_DBM);
}

void setup() {
    Serial.begin(115200);
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, HIGH);

    const BeaconConfig &currentBeacon = getSelectedBeacon();

    Serial.println("\n==============================================");
    Serial.println("  INNAV - ESP32 BLE Beacon Firmware Starting  ");
    Serial.println("==============================================");
    Serial.printf("Beacon Index: %d\n", BEACON_INDEX);
    Serial.printf("Device Name : %s\n", currentBeacon.deviceName);
    Serial.printf("Local Nome  : %s\n", currentBeacon.localNome);
    Serial.printf("Major / Minor: %d / %d\n", currentBeacon.major, currentBeacon.minor);
    Serial.printf("UUID        : %s\n", BEACON_UUID);
    Serial.println("==============================================\n");

    // Inicializar dispositivo Bluetooth LE com o Nome Customizado (ex: INNAV_ESP_01)
    BLEDevice::init(currentBeacon.deviceName);

    // Ajustar a potência de transmissão Bluetooth ao máximo (+9dBm para ESP32)
    esp_ble_tx_power_set(ESP_BLE_PWR_TYPE_ADV, ESP_PWR_LVL_P9);

    // Criar objeto de anúncio BLE
    pAdvertising = BLEDevice::getAdvertising();

    // Montar os dados do iBeacon
    BLEBeacon oBeacon = BLEBeacon();
    setupIBeaconData(oBeacon, currentBeacon);

    // Montar o pacote de anúncio BLE (Advertisement Data)
    BLEAdvertisementData oAdvertisementData;
    BLEAdvertisementData oScanResponseData;

    oAdvertisementData.setFlags(0x04); // BR_EDR_NOT_SUPPORTED
    
    String strServiceData = "";
    strServiceData += (char)16;     // Comprimento dos dados do fabricante
    strServiceData += (char)0xFF;   // Tipo de dado: Manufacturer Specific Data
    strServiceData += oBeacon.getData();

    oAdvertisementData.addData(strServiceData);
    oScanResponseData.setName(currentBeacon.deviceName);

    pAdvertising->setAdvertisementData(oAdvertisementData);
    pAdvertising->setScanResponseData(oScanResponseData);

    // Iniciar a transmissão contínua
    pAdvertising->start();

    digitalWrite(LED_PIN, LOW);
    Serial.println("Transmissão BLE ativada com sucesso! Aguardando varredura do App INNAV.");
}

void loop() {
    // LED Onboard pisca brevemente a cada 3 segundos indicando operação normal
    digitalWrite(LED_PIN, HIGH);
    delay(50);
    digitalWrite(LED_PIN, LOW);
    delay(2950);
}
