# 📡 Hardware Firmware - ESP32 BLE Beacons (Projeto INNAV)

Este diretório contém o firmware em C++ para transformar seus módulos **ESP32** em Beacons BLE (Bluetooth Low Energy) de alta precisão para o sistema de navegação indoor **INNAV**.

---

## 📋 Módulos Atendidos (5 Unidades)

O código suporta os 5 ESP32s da sua infraestrutura:
- **4x ESP32-WROOM-32**
- **1x ESP32 Genérico** (Dev Module / ESP32-CAM / ESP32-S3 / ESP32-C3)

| Beacon Index | Nome Transmitido BLE (`deviceName`) | Local Mapeado no App | Major | Minor |
| :---: | :---: | :--- | :---: | :---: |
| **1** | `INNAV_ESP_01` | Portaria Principal | 1 | 1 |
| **2** | `INNAV_ESP_02` | Corredor Central | 1 | 2 |
| **3** | `INNAV_ESP_03` | Laboratório de Hardware | 1 | 3 |
| **4** | `INNAV_ESP_04` | Hall de Entrada | 1 | 4 |
| **5** | `INNAV_ESP_05` | Auditório / Biblioteca | 1 | 5 |

---

## 🛠️ Como Gravar os ESP32s

### Método 1: Usando a Arduino IDE

1. Instale a extensão do ESP32 na Arduino IDE:
   - Acesse **Arquivo > Preferências**.
   - Em *URLs Adicionais do Gerenciador de Placas*, adicione: `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json`
   - Vá em **Ferramentas > Placa > Gerenciador de Placas**, pesquise por `esp32` e instale.

2. Abra o projeto:
   - Abra a pasta `hardware/esp32_beacon_firmware/` na Arduino IDE.
   - Abra o arquivo `config.h`.

3. **Para gravar a Placa 1**:
   - Deixe o `#define BEACON_INDEX 1` no `config.h`.
   - Conecte o ESP32 #1 na USB.
   - Selecione a placa **ESP32 Dev Module** e a Porta COM correspondente.
   - Clique em **Carregar (Upload)**.

4. **Para gravar as Placas 2, 3, 4 e 5**:
   - Altere `#define BEACON_INDEX 2` no `config.h`, grave no ESP32 #2.
   - Altere `#define BEACON_INDEX 3` no `config.h`, grave no ESP32 #3.
   - Altere `#define BEACON_INDEX 4` no `config.h`, grave no ESP32 #4.
   - Altere `#define BEACON_INDEX 5` no `config.h`, grave no ESP32 #5.

---

### Método 2: Usando o PlatformIO (VS Code)

1. Abra a pasta `/hardware` no VS Code com a extensão PlatformIO instalada.
2. No arquivo `esp32_beacon_firmware/config.h`, selecione o `BEACON_INDEX` de 1 a 5.
3. Clique em **Upload** no rodapé do VS Code.

---

## 🔍 Como Testar
- Conecte a placa à alimentação (USB ou Fonte 5V).
- O LED Onboard (GPIO2) dará piscadas curtas a cada 3 segundos indicando que o beacon está ativo.
- Abra o aplicativo **INNAV** no smartphone Android. O app irá detectar automaticamente os beacons pelo nome de transmissão (`INNAV_ESP_01` até `INNAV_ESP_05`).
