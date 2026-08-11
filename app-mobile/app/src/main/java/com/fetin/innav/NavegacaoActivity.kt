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

    private val noAtualUsuario = No(id = "ESP_PORTARIA", nomeLocal = "Portaria Principal", x = 0.0, y = 0.0)
    private lateinit var noDestinoLab: No

    private val filtroKalman = com.fetin.innav.filtering.FiltroKalmanRssi()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("SetTextI18n", "DefaultLocale", "MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val macAddress = result.device.address
            val deviceName = runCatching { result.device.name }.getOrNull() ?: result.scanRecord?.deviceName
            val rssiBruto = result.rssi

            if (noDestinoLab.correspondeAoDispositivo(macAddress, deviceName) && !chegouNoDestino) {
                val rssiFiltrado = filtroKalman.filtrar(rssiBruto.toDouble())
                val distanciaEstimada = CalculadoraAnguloNavegacao.estimarDistanciaMetros(rssiFiltrado)
                anguloAlvo = CalculadoraAnguloNavegacao.calcularAnguloAlvo(noAtualUsuario, noDestinoLab)

                val identificadorEncontrado = deviceName ?: macAddress
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        txtSinal.text = "Sinal [$identificadorEncontrado]: $rssiBruto dBm (Kalman: %.1f dBm | ~%.1fm) | Alvo: %.0f°".format(rssiFiltrado, distanciaEstimada, anguloAlvo)
                    }
                }

                hapticManager.adjustVibrationByAzimuth(
                    currentAzimuth = azimuteAtual,
                    targetAngle = anguloAlvo,
                    toleranceDegrees = 8f
                )

                // Verificação de chegada utilizando o RSSI filtrado (evita disparos falsos por picos de ruído)
                if (rssiFiltrado > -46.0) {
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

        val id = intent.getStringExtra("DESTINO_ID") ?: "ESP_LAB"
        val nome = intent.getStringExtra("DESTINO_NOME") ?: "Laboratório de Hardware"
        val mac = intent.getStringExtra("DESTINO_MAC") ?: "68:25:DD:48:1F:12"
        val devName = intent.getStringExtra("DESTINO_NAME") ?: "Tab S6 Lite de Jhonata"
        val x = intent.getDoubleExtra("DESTINO_X", 10.0)
        val y = intent.getDoubleExtra("DESTINO_Y", 10.0)

        noDestinoLab = No(id, nome, mac, devName, x, y)

        txtSinal = findViewById(R.id.txtSinalAoVivo)
        val btnAjuda = findViewById<Button>(R.id.btnAjuda)

        hapticManager = HapticManager(this)
        orientationManager = OrientationManager(this)

        anguloAlvo = CalculadoraAnguloNavegacao.calcularAnguloAlvo(noAtualUsuario, noDestinoLab)

        orientationManager.onAzimuthChanged = { azimuthDegrees ->
            azimuteAtual = azimuthDegrees
            if (!chegouNoDestino) {
                hapticManager.adjustVibrationByAzimuth(
                    currentAzimuth = azimuteAtual,
                    targetAngle = anguloAlvo,
                    toleranceDegrees = 8f
                )
            }
        }

        btnAjuda.setOnClickListener {
            encerrarEVoltar()
        }
    }

    @SuppressLint("MissingPermission")
    private fun iniciarRadarBLE() {
        if (!chegouNoDestino) {
            bleScanner?.startScan(scanCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun pararRadarBLE() {
        bleScanner?.stopScan(scanCallback)
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
    private fun finalizarNavegacaoComSucesso() {
        pararRadarBLE()
        orientationManager.stopListening()
        hapticManager.vibrateConfirmation()

        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                val intent = Intent(this, ChegadaActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun encerrarEVoltar() {
        pararRadarBLE()
        orientationManager.stopListening()
        hapticManager.stop()
        finish()
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        pararRadarBLE()
        orientationManager.stopListening()
        hapticManager.stop()
        super.onDestroy()
    }
}