package com.fetin.innav

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
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

    private lateinit var gestureDetector: android.view.GestureDetector

    private val bluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val bleScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    private lateinit var hapticManager: HapticManager
    private lateinit var orientationManager: OrientationManager

    private var chegouNoDestino = false
    private lateinit var txtSinal: TextView

    private lateinit var noDestinoLab: No
    private val noAtualUsuario = No(id = "USER", nomeLocal = "Posição Atual", x = 0.0, y = 0.0)

    private val filtroKalman = com.fetin.innav.filtering.FiltroKalmanRssi()

    // Variáveis voláteis atualizadas em tempo real
    private var anguloAlvo = 0f
    private var azimuteAtual = 0f
    private var ultimoRssiFiltrado = -100.0
    private var ultimaDistancia = 99.0

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("SetTextI18n", "DefaultLocale", "MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val macAddress = result.device.address
            val deviceName = runCatching { result.device.name }.getOrNull() ?: result.scanRecord?.deviceName
            val rssiBruto = result.rssi

            if (noDestinoLab.correspondeAoDispositivo(macAddress, deviceName) && !chegouNoDestino) {
                // Atualiza os dados matemáticos em segundo plano
                ultimoRssiFiltrado = filtroKalman.filtrar(rssiBruto.toDouble())
                ultimaDistancia = CalculadoraAnguloNavegacao.estimarDistanciaMetros(ultimoRssiFiltrado)
                anguloAlvo = CalculadoraAnguloNavegacao.calcularAnguloAlvo(noAtualUsuario, noDestinoLab)

                val identificadorEncontrado = deviceName ?: macAddress
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        txtSinal.text = "Alvo: $identificadorEncontrado\nSinal: %.1f dBm\nDistância: ~%.1fm\nMira Alvo: %.0f°".format(
                            ultimoRssiFiltrado, ultimaDistancia, anguloAlvo
                        )
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navegacao)

        val id = intent.getStringExtra("DESTINO_ID") ?: "ESP_02"
        val nome = intent.getStringExtra("DESTINO_NOME") ?: "Corredor Central"
        val mac = intent.getStringExtra("DESTINO_MAC") ?: "AA:BB:CC:DD:EE:FF"
        val devName = intent.getStringExtra("DESTINO_NAME") ?: "INNAV_ESP_02"
        val x = intent.getDoubleExtra("DESTINO_X", 5.0)
        val y = intent.getDoubleExtra("DESTINO_Y", 5.0)

        noDestinoLab = No(id, nome, mac, devName, x, y)

        txtSinal = findViewById(R.id.txtSinalAoVivo)
        val btnAjuda = findViewById<Button>(R.id.btnAjuda)

        hapticManager = HapticManager(this)
        orientationManager = OrientationManager(this)

        anguloAlvo = CalculadoraAnguloNavegacao.calcularAnguloAlvo(noAtualUsuario, noDestinoLab)

        orientationManager.onAzimuthChanged = { azimuthDegrees ->
            var azimuteCorrigido = azimuthDegrees + 180f
            if (azimuteCorrigido >= 360f) {
                azimuteCorrigido -= 360f
            }
            azimuteAtual = azimuteCorrigido

            if (!chegouNoDestino && ultimaDistancia != 99.0) {
                val diferencaAngular = HapticManager.calculateAngularDifference(azimuteAtual, anguloAlvo)

                if (diferencaAngular <= 15f) {
                    if (ultimoRssiFiltrado >= -55.0) {
                        chegouNoDestino = true
                        finalizarNavegacaoComSucesso()
                    }
                    else if (ultimaDistancia <= 6.0) {
                        hapticManager.vibratePulse(durationMs = 50L, amplitude = 180)
                    }
                    else {
                        hapticManager.stop()
                    }
                }
                else {
                    hapticManager.stop()
                }
            }
        }

        btnAjuda.setOnClickListener {
            encerrarEVoltar()
        }

        gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null) {
                    val diffY = e2.y - e1.y
                    val diffX = e2.x - e1.x
                    if (Math.abs(diffY) > Math.abs(diffX) && Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY < 0) { // Foi para cima
                            hapticManager.vibratePulse(50, 200)
                            encerrarEVoltar()
                            return true
                        }
                    }
                }
                return super.onFling(e1, e2, velocityX, velocityY)
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun iniciarRadarBLE() {
        if (!chegouNoDestino) {
            // 🚨 HACK SAMSUNG ADICIONADO AQUI: Protege contra o bloqueio invisível do Bluetooth
            val filtros = mutableListOf<ScanFilter>()
            val configuracaoRadar = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            bleScanner?.startScan(filtros, configuracaoRadar, scanCallback)
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

    // 🚨 TOQUE DA TELA ADICIONADO AQUI: Agora o detector de gestos consegue "ouvir" o dedo!
    override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
        event?.let { gestureDetector.onTouchEvent(it) }
        return super.onTouchEvent(event)
    }
}