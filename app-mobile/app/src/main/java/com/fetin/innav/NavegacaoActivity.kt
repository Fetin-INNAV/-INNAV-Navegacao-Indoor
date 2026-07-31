package com.fetin.innav

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fetin.innav.haptic.CalculadoraAnguloNavegacao
import com.fetin.innav.haptic.HapticManager
import com.fetin.innav.haptic.OrientationManager
import com.fetin.innav.models.No

class NavegacaoActivity : AppCompatActivity() {

    private val bluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val bleScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    private lateinit var hapticManager: HapticManager
    private lateinit var orientationManager: OrientationManager

    private var chegouNoDestino = false
    private lateinit var txtSinal: TextView

    private var azimuteAtual: Float = 0f
    private var anguloAlvo: Float = 0f

    // Configuração dos Nós / Beacons ESP32 (ou Tablet Emulador) com coordenadas 2D (x, y)
    private val noAtualUsuario = No(id = "ESP_PORTARIA", nomeLocal = "Portaria Principal", x = 0.0, y = 0.0)
    private val noDestinoLab = No(
        id = "ESP_LAB",
        nomeLocal = "Laboratório de Hardware",
        macAddress = "68:25:DD:48:1F:12",
        deviceName = "Tab S6 Lite de Jhonata",
        x = 10.0,
        y = 10.0
    )

    // Scanner BLE em tempo real
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("SetTextI18n", "DefaultLocale", "MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val macAddress = result.device.address
            val deviceName = runCatching { result.device.name }.getOrNull() ?: result.scanRecord?.deviceName
            val rssi = result.rssi

            // Filtra o beacon alvo priorizando o Nome do Dispositivo (deviceName) com fallback para o MAC
            if (noDestinoLab.correspondeAoDispositivo(macAddress, deviceName) && !chegouNoDestino) {
                // 1. Estima a distância até o beacon/ESP32 usando o modelo logarítmico de propagação
                val distanciaEstimada = CalculadoraAnguloNavegacao.estimarDistanciaMetros(rssi)

                // 2. Calcula dinamicamente o azimute/ângulo alvo do destino em relação ao usuário
                anguloAlvo = CalculadoraAnguloNavegacao.calcularAnguloAlvo(noAtualUsuario, noDestinoLab)

                // 3. Atualiza o texto na tela com o sinal, distância estimada e ângulo alvo
                val identificadorEncontrado = deviceName ?: macAddress
                runOnUiThread {
                    txtSinal.text = "Sinal [$identificadorEncontrado]: $rssi dBm (~%.1fm) | Alvo: %.0f°".format(distanciaEstimada, anguloAlvo)
                }

                // 4. Atualiza a intensidade do feedback tátil com base na orientação atual e no ângulo alvo
                hapticManager.adjustVibrationByAzimuth(
                    currentAzimuth = azimuteAtual,
                    targetAngle = anguloAlvo,
                    toleranceDegrees = 10f
                )

                // 5. Gatilho de Chegada: Sinal forte (RSSI > -45 dBm / muito próximo)
                if (rssi > -45) {
                    chegouNoDestino = true
                    finalizarNavegacaoComSucesso()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navegacao)

        txtSinal = findViewById(R.id.txtSinalAoVivo)
        val btnAjuda = findViewById<Button>(R.id.btnAjuda)

        // Inicializa gerenciadores de tátil e orientação
        hapticManager = HapticManager(this)
        orientationManager = OrientationManager(this)

        // Calcula o ângulo alvo inicial em direção ao nó de destino
        anguloAlvo = CalculadoraAnguloNavegacao.calcularAnguloAlvo(noAtualUsuario, noDestinoLab)

        // Conecta a leitura contínua de azimute do sensor ao feedback haptic em tempo real
        orientationManager.onAzimuthChanged = { azimuthDegrees ->
            azimuteAtual = azimuthDegrees
            if (!chegouNoDestino) {
                hapticManager.adjustVibrationByAzimuth(
                    currentAzimuth = azimuteAtual,
                    targetAngle = anguloAlvo,
                    toleranceDegrees = 10f
                )
            }
        }

        // Liga o radar BLE
        bleScanner?.startScan(scanCallback)

        btnAjuda.setOnClickListener {
            bleScanner?.stopScan(scanCallback)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        orientationManager.startListening()
    }

    override fun onPause() {
        super.onPause()
        orientationManager.stopListening()
        hapticManager.stop()
    }

    @SuppressLint("MissingPermission")
    private fun finalizarNavegacaoComSucesso() {
        // 1. Desliga o radar e sensores
        bleScanner?.stopScan(scanCallback)
        orientationManager.stopListening()

        // 2. Dispara a vibração tátil de confirmação de destino
        hapticManager.vibrateConfirmation()

        // 3. Transiciona para a ChegadaActivity
        runOnUiThread {
            val intent = Intent(this, ChegadaActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        bleScanner?.stopScan(scanCallback)
        orientationManager.stopListening()
        hapticManager.stop()
        super.onDestroy()
    }
}