package com.fetin.innav

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fetin.innav.haptic.ExploracaoLivreManager
import com.fetin.innav.haptic.HapticManager
import com.fetin.innav.haptic.OrientationManager
import com.fetin.innav.models.No

class ExploracaoLivreActivity : AppCompatActivity() {

    private val bluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val bleScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    private lateinit var hapticManager: HapticManager
    private lateinit var orientationManager: OrientationManager
    private lateinit var exploracaoLivreManager: ExploracaoLivreManager

    private lateinit var txtStatus: TextView
    private var azimuteAtual = 0f

    private val usuarioPosicao = No(id = "USER", nomeLocal = "Posição Atual", x = 0.0, y = 0.0)

    // Checkpoints cadastrados para busca (Compatíveis tanto com o ESP32 físico via MAC quanto Tablet via Nome)
    private val checkpointsConhecidos = listOf(
        No(
            id = "ESP_01",
            nomeLocal = "Portaria Principal",
            macAddress = "68:25:DD:48:1F:12",
            deviceName = "Tab S6 Lite de Jhonata",
            x = 0.0,
            y = 5.0
        ),
        No(
            id = "ESP_03",
            nomeLocal = "Laboratório de Hardware",
            macAddress = "68:25:DD:48:1F:12",
            deviceName = "Tab S6 Lite de Jhonata",
            x = 10.0,
            y = 10.0
        )
    )

    // Scanner BLE dedicado da Exploração Livre
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission", "SetTextI18n")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val macAddress = result.device.address
            val deviceName = runCatching { result.device.name }.getOrNull() ?: result.scanRecord?.deviceName
            val rssi = result.rssi

            // Localiza o nó correspondente priorizando o MAC oficial do ESP32 (68:25:DD:48:1F:12) ou Nome do Dispositivo
            val noEncontrado = checkpointsConhecidos.firstOrNull {
                it.correspondeAoDispositivo(macAddress, deviceName)
            } ?: No(
                id = deviceName ?: macAddress,
                nomeLocal = deviceName ?: "ESP32 Laboratório",
                macAddress = macAddress,
                deviceName = deviceName,
                x = 5.0,
                y = 5.0
            )

            exploracaoLivreManager.registrarBeaconDetectado(
                noAtualUsuario = usuarioPosicao,
                noDetectado = noEncontrado,
                rssi = rssi
            )

            val identificador = deviceName ?: macAddress
            val statusInversao = if (orientationManager.inverterAzimute) " [180° Invertido]" else ""
            runOnUiThread {
                txtStatus.text = "Sinal [$identificador]: $rssi dBm$statusInversao\nAponte o topo do celular para explorar."
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exploracao_livre)

        txtStatus = findViewById(R.id.txtStatusExploracao)
        val btnSair = findViewById<Button>(R.id.btnSairExploracao)
        val btnCalibrar = findViewById<Button>(R.id.btnCalibrarBussola)

        // Inicializa gerenciadores
        hapticManager = HapticManager(this)
        orientationManager = OrientationManager(this)
        exploracaoLivreManager = ExploracaoLivreManager(this, hapticManager)

        // Botão para alternar a inversão de 180° em tempo real (salva no dispositivo)
        btnCalibrar.setOnClickListener {
            val estaInvertido = orientationManager.alternarInversao()
            val msg = if (estaInvertido) "Bússola invertida em 180 graus" else "Bússola na orientação padrão"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            exploracaoLivreManager.falarMensagem(msg)
        }

        // 1. Mensagem inicial de voz informativa ao entrar na tela
        window.decorView.postDelayed({
            exploracaoLivreManager.falarMensagem("Modo de exploração livre ativado. Aponte o celular ao seu redor.")
        }, 800)

        // 2. Conecta o sensor de orientação
        orientationManager.onAzimuthChanged = { azimuth ->
            azimuteAtual = azimuth
            exploracaoLivreManager.processarOrientacao(azimuteAtual, toleranceDegrees = 10f)
        }

        // 3. Inicia o radar BLE exclusivo
        bleScanner?.startScan(scanCallback)

        // 4. Ação do Botão "Sair": Encerra imediatamente varredura, áudio e navega de volta
        btnSair.setOnClickListener {
            encerrarExploracaoEVoltar()
        }
    }

    @SuppressLint("MissingPermission")
    private fun encerrarExploracaoEVoltar() {
        // Interrompe imediatamente o scanner BLE e qualquer áudio/vibração ativa
        bleScanner?.stopScan(scanCallback)
        exploracaoLivreManager.stop()
        orientationManager.stopListening()
        hapticManager.stop()

        finish()
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
    override fun onDestroy() {
        bleScanner?.stopScan(scanCallback)
        exploracaoLivreManager.stop()
        orientationManager.stopListening()
        hapticManager.stop()
        super.onDestroy()
    }
}
