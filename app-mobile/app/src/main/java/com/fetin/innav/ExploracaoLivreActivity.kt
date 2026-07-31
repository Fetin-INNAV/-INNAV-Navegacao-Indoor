package com.fetin.innav

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
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
    private lateinit var btnDefinirDestino: Button
    private var azimuteAtual = 0f

    private var noSelecionadoNaMira: No? = null

    private val usuarioPosicao = No(id = "USER", nomeLocal = "Posição Atual", x = 0.0, y = 0.0)

    private val checkpointsConhecidos = listOf(
        No(
            id = "ESP_LAB",
            nomeLocal = "Laboratório de Hardware",
            macAddress = "68:25:DD:48:1F:12",
            deviceName = "Tab S6 Lite de Jhonata",
            x = 0.0,
            y = 5.0
        )
    )

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission", "SetTextI18n")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val macAddress = result.device.address
            val deviceName = runCatching { result.device.name }.getOrNull() ?: result.scanRecord?.deviceName
            val rssi = result.rssi

            val noEncontrado = checkpointsConhecidos.firstOrNull {
                it.correspondeAoDispositivo(macAddress, deviceName)
            } ?: return

            exploracaoLivreManager.registrarBeaconDetectado(
                noAtualUsuario = usuarioPosicao,
                noDetectado = noEncontrado,
                rssi = rssi
            )
        }
    }

    @SuppressLint("MissingPermission", "DefaultLocale")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exploracao_livre)

        txtStatus = findViewById(R.id.txtStatusExploracao)
        btnDefinirDestino = findViewById(R.id.btnDefinirDestino)
        val btnSair = findViewById<Button>(R.id.btnSairExploracao)
        val btnCalibrarBussola = findViewById<Button>(R.id.btnCalibrarBussola)
        val btnCalibrarMira = findViewById<Button>(R.id.btnCalibrarMira)

        hapticManager = HapticManager(this)
        orientationManager = OrientationManager(this)
        exploracaoLivreManager = ExploracaoLivreManager(this, hapticManager)

        exploracaoLivreManager.onTelemetriaUpdated = { telemetria ->
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    if (telemetria != null) {
                        val statusInversao = if (orientationManager.inverterAzimute) " [180° Inv]" else ""
                        val statusMira = if (telemetria.estaNaMira) "🎯 NA MIRA (<= 8°)" else if (telemetria.diferencaErro <= 20f) "🔥 QUENTE" else if (telemetria.diferencaErro <= 45f) "🌤️ MORNO" else "❄️ FRIO (> 45°)"

                        txtStatus.text = "Sinal [${telemetria.idBeacon}]: ${telemetria.rssi} dBm$statusInversao\n" +
                                "Bússola: %.0f° | Alvo: %.0f° | Erro: %.0f°\n$statusMira".format(
                                    telemetria.azimuteCelular,
                                    telemetria.anguloAlvo,
                                    telemetria.diferencaErro
                                )
                    } else {
                        txtStatus.text = "Procurando sinal BLE do ESP32..."
                    }
                }
            }
        }

        exploracaoLivreManager.onBeaconNaMiraChanged = { beaconAlvo ->
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    if (beaconAlvo != null) {
                        noSelecionadoNaMira = beaconAlvo.no
                        val nomeExibicao = beaconAlvo.no.deviceName ?: beaconAlvo.no.nomeLocal
                        btnDefinirDestino.text = "📍 DEFINIR $nomeExibicao COMO DESTINO"
                        btnDefinirDestino.visibility = View.VISIBLE
                    } else {
                        noSelecionadoNaMira = null
                        btnDefinirDestino.visibility = View.GONE
                    }
                }
            }
        }

        btnDefinirDestino.setOnClickListener {
            noSelecionadoNaMira?.let { noDestino ->
                iniciarNavegacaoOrientada(noDestino)
            }
        }

        btnCalibrarBussola.setOnClickListener {
            val estaInvertido = orientationManager.alternarInversao()
            val msg = if (estaInvertido) "Bússola invertida em 180 graus" else "Bússola na orientação padrão"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            exploracaoLivreManager.falarMensagem(msg)
        }

        btnCalibrarMira.setOnClickListener {
            val nome = exploracaoLivreManager.calibrarMiraDoBeacon(azimuteAtual)
            val msg = if (nome != null) "Mira calibrada 1:1 para $nome" else "Nenhum beacon detectado para calibrar"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            exploracaoLivreManager.falarMensagem(msg)
        }

        window.decorView.postDelayed({
            if (!isFinishing && !isDestroyed) {
                exploracaoLivreManager.falarMensagem("Modo de exploração livre ativado. Aponte o celular ao seu redor.")
            }
        }, 800)

        orientationManager.onAzimuthChanged = { azimuth ->
            azimuteAtual = azimuth
            exploracaoLivreManager.processarOrientacao(azimuteAtual, toleranceDegrees = 8f)
        }

        btnSair.setOnClickListener {
            encerrarExploracaoEVoltar()
        }
    }

    @SuppressLint("MissingPermission")
    private fun iniciarRadarBLE() {
        bleScanner?.startScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun pararRadarBLE() {
        bleScanner?.stopScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun iniciarNavegacaoOrientada(noDestino: No) {
        pararRadarBLE()
        exploracaoLivreManager.stop()
        orientationManager.stopListening()
        hapticManager.stop()

        val intent = Intent(this, NavegacaoActivity::class.java).apply {
            putExtra("DESTINO_ID", noDestino.id)
            putExtra("DESTINO_NOME", noDestino.nomeLocal)
            putExtra("DESTINO_MAC", noDestino.macAddress)
            putExtra("DESTINO_NAME", noDestino.deviceName)
            putExtra("DESTINO_X", noDestino.x)
            putExtra("DESTINO_Y", noDestino.y)
        }
        startActivity(intent)
        finish()
    }

    @SuppressLint("MissingPermission")
    private fun encerrarExploracaoEVoltar() {
        pararRadarBLE()
        exploracaoLivreManager.stop()
        orientationManager.stopListening()
        hapticManager.stop()
        finish()
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        orientationManager.startListening()
        iniciarRadarBLE()
    }

    @SuppressLint("MissingPermission")
    override fun onPause() {
        super.onPause()
        orientationManager.stopListening()
        pararRadarBLE()
        hapticManager.stop()
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        pararRadarBLE()
        exploracaoLivreManager.stop()
        orientationManager.stopListening()
        hapticManager.stop()
        super.onDestroy()
    }
}
