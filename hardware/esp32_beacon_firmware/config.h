#ifndef INNAV_CONFIG_H
#define INNAV_CONFIG_H

#include <Arduino.h>

// ============================================================================
// CONFIGURAÇÃO DO ESP32 BEACON - PROJETO INNAV
// ============================================================================
// Altere a constante BEACON_INDEX de 1 a 5 para gravar cada um dos 5 ESP32:
// 1 -> ESP32 #1 (Portaria Principal)
// 2 -> ESP32 #2 (Corredor Central)
// 3 -> ESP32 #3 (Laboratório de Hardware)
// 4 -> ESP32 #4 (Hall de Entrada / Recepção)
// 5 -> ESP32 #5 (Auditório / Biblioteca)
// ============================================================================

#define BEACON_INDEX 1  // <<-- ALTERE AQUI PARA CADA PLACA (1, 2, 3, 4 ou 5)

// UUID Padrão do Projeto INNAV (iBeacon 128-bit)
#define BEACON_UUID "42696163-6f6e-494e-4e41-5620424c4531"

// Estrutura de perfil de cada nó
struct BeaconConfig {
    uint16_t major;
    uint16_t minor;
    const char* deviceName;
    const char* localNome;
};

// Tabela de parâmetros dos 5 Beacons ESP32
const BeaconConfig BEACON_PROFILES[5] = {
    { 1, 1, "INNAV_ESP_01", "Portaria Principal" },
    { 1, 2, "INNAV_ESP_02", "Corredor Central" },
    { 1, 3, "INNAV_ESP_03", "Laboratório de Hardware" },
    { 1, 4, "INNAV_ESP_04", "Hall de Entrada" },
    { 1, 5, "INNAV_ESP_05", "Auditório / Biblioteca" }
};

// Obter a configuração do beacon selecionado
inline const BeaconConfig& getSelectedBeacon() {
    int idx = BEACON_INDEX - 1;
    if (idx < 0 || idx >= 5) idx = 0;
    return BEACON_PROFILES[idx];
}

// Configurações de Potência de Sinal e Intervalo BLE
#define ESP32_TX_POWER_DBM -59 // RSSI de referência medido a 1 metro
#define ADV_INTERVAL_MS    100 // Intervalo de transmissão (100ms = 10 Hz para rápida localização)
#define LED_PIN            2   // LED Onboard GPIO2 (pisca indicando transmissão)

#endif // INNAV_CONFIG_H
