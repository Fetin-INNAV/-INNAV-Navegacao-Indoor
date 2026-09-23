package com.fetin.innav

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.fetin.innav.haptic.CalculadoraAnguloNavegacao
import com.fetin.innav.haptic.HapticManager
import com.fetin.innav.haptic.OrientationManager
import com.fetin.innav.models.No
import java.util.Locale

class NavegacaoActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var gestureDetector: android.view.GestureDetector

    private val bluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val bleScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    private lateinit var hapticManager: HapticManager
    private lateinit var orientationManager: OrientationManager
    private lateinit var tts: TextToSpeech

    private var chegouNoDestino = false
    private lateinit var txtSinal: TextView
    private lateinit var txtTituloDestino: TextView

    // LÓGICA ÍMÃ: Sistema de Checkpoints
    private var rotaCheckpoints = mutableListOf<No>()
    private var rotaInstrucoes = mutableListOf<String>()
    private var indiceCheckpointAtual = 0
    private lateinit var alvoAtual: No
    private var noAtualUsuario = No(id = "START", nomeLocal = "Posição Atual", x = 0.0, y = 0.0)

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

            // Verifica se o sinal é do ESP que é o NOSSO ALVO ATUAL (O Ímã)
            if (::alvoAtual.isInitialized && alvoAtual.correspondeAoDispositivo(macAddress, deviceName) && !chegouNoDestino) {

                ultimoRssiFiltrado = filtroKalman.filtrar(rssiBruto.toDouble())
                ultimaDistancia = CalculadoraAnguloNavegacao.estimarDistanciaMetros(ultimoRssiFiltrado)

                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        txtSinal.text = "Faltam\n%.1f metros".format(ultimaDistancia)
                    }
                }

                // GATILHO DE CHECKPOINT: Se chegar muito perto do ESP alvo
                if (ultimoRssiFiltrado >= -55.0) {
                    avancarParaProximoCheckpoint()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navegacao)

        txtSinal = findViewById(R.id.txtSinalAoVivo)
        txtTituloDestino = findViewById(R.id.txtTituloDestino)
        val btnAjuda = findViewById<Button>(R.id.btnAjuda)

        hapticManager = HapticManager(this)
        orientationManager = OrientationManager(this)
        tts = TextToSpeech(this, this)

        // 1. LÊ O BOTÃO QUE FOI CLICADO NA TELA ANTERIOR
        val id = intent.getStringExtra("DESTINO_ID") ?: ""
        val nome = intent.getStringExtra("DESTINO_NOME") ?: "Destino"
        val mac = intent.getStringExtra("DESTINO_MAC") ?: ""
        val devName = intent.getStringExtra("DESTINO_NAME") ?: ""
        val x = intent.getDoubleExtra("DESTINO_X", 0.0)
        val y = intent.getDoubleExtra("DESTINO_Y", 0.0)

        val destinoSelecionado = No(id, nome, mac, devName, x, y)

        txtTituloDestino.text = nome.uppercase()

        // 2. VERIFICA SE É A ROTA DO VÍDEO (Laboratório) OU UMA ROTA COMUM
        if (id == "ESP_03") {
            montarRotaDoVideo()
        } else {
            rotaCheckpoints.clear()
            rotaCheckpoints.add(destinoSelecionado)
            rotaInstrucoes.clear()
            rotaInstrucoes.add("Iniciando navegação para $nome.")

            alvoAtual = rotaCheckpoints[0]
            indiceCheckpointAtual = 0
            atualizarInterfaceEAngulo()
        }

        orientationManager.onAzimuthChanged = { azimuthDegrees ->
            var azimuteCorrigido = azimuthDegrees + 180f
            if (azimuteCorrigido >= 360f) azimuteCorrigido -= 360f
            azimuteAtual = azimuteCorrigido

            if (!chegouNoDestino && ultimaDistancia != 99.0 && ::alvoAtual.isInitialized) {
                val diferencaAngular = HapticManager.calculateAngularDifference(azimuteAtual, anguloAlvo)
                if (diferencaAngular <= 40f) {
                    if (ultimaDistancia <= 6.0) {
                        hapticManager.vibratePulse(durationMs = 50L, amplitude = 180)
                        txtSinal.setTextColor("#00FF00".toColorInt())
                    } else {
                        hapticManager.stop()
                        txtSinal.setTextColor("#FFFFFF".toColorInt())
                    }
                } else {
                    hapticManager.stop()
                    txtSinal.setTextColor("#FFFFFF".toColorInt())
                }
            }
        }

        btnAjuda.setOnClickListener { encerrarEVoltar() }

        // AQUI ESTAVA O ERRO! Restaurei o gestureDetector que eu tinha apagado sem querer
        gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null) {
                    val diffY = e2.y - e1.y
                    val diffX = e2.x - e1.x
                    if (Math.abs(diffY) > Math.abs(diffX) && Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY < 0) {
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

    private fun montarRotaDoVideo() {
        rotaCheckpoints.clear()
        rotaInstrucoes.clear()

        // Ajuste os MACs aqui se o ESP físico que vocês forem usar amanhã for diferente!
        rotaCheckpoints.add(No("ESP_02", "Corredor", "14:2B:2F:C1:FE:72", "INNAV_ESP_02", 0.0, 8.0))
        rotaInstrucoes.add("Siga em frente por 8 metros até o meio do corredor.")

        rotaCheckpoints.add(No("ESP_03", "Porta do Lab", "3C:8A:1F:A4:B3:82", "INNAV_ESP_03", 0.0, 15.0))
        rotaInstrucoes.add("Continue em frente por mais 7 metros até a porta do laboratório.")

        rotaCheckpoints.add(No("ESP_04", "Nossa Mesa", "5C:01:3B:47:2A:B6", "INNAV_ESP_04", 5.0, 15.0))
        rotaInstrucoes.add("Vire à direita e caminhe 5 metros para chegar à mesa. Você chegou ao seu destino.")

        alvoAtual = rotaCheckpoints[0]
        indiceCheckpointAtual = 0
        atualizarInterfaceEAngulo()
    }

    private fun avancarParaProximoCheckpoint() {
        hapticManager.vibrateConfirmation()

        noAtualUsuario = alvoAtual
        indiceCheckpointAtual++

        if (indiceCheckpointAtual < rotaCheckpoints.size) {
            alvoAtual = rotaCheckpoints[indiceCheckpointAtual]
            falar(rotaInstrucoes[indiceCheckpointAtual])
            atualizarInterfaceEAngulo()
        } else {
            chegouNoDestino = true
            finalizarNavegacaoComSucesso()
        }
    }



    private fun atualizarInterfaceEAngulo() {
        runOnUiThread {
            if(::txtTituloDestino.isInitialized) {

                txtSinal.text = "Buscando sinal..."
            }
        }
        ultimoRssiFiltrado = -100.0
        ultimaDistancia = 99.0
        anguloAlvo = CalculadoraAnguloNavegacao.calcularAnguloAlvo(noAtualUsuario, alvoAtual)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale("pt", "BR"))
            if (rotaInstrucoes.isNotEmpty()) {
                falar(rotaInstrucoes[indiceCheckpointAtual])
            }
        }
    }

    private fun falar(texto: String) {
        if (::tts.isInitialized) {
            tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "NAV_INSTRUCTION")
        }
    }

    @SuppressLint("MissingPermission")
    private fun iniciarRadarBLE() {
        if (!chegouNoDestino) {
            val configuracaoRadar = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            bleScanner?.startScan(null, configuracaoRadar, scanCallback)
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
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }

    override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
        // Correção aplicada: Verifica se o gestureDetector foi realmente construído antes de usar
        if (::gestureDetector.isInitialized) {
            event?.let { gestureDetector.onTouchEvent(it) }
        }
        return super.onTouchEvent(event)
    }
}