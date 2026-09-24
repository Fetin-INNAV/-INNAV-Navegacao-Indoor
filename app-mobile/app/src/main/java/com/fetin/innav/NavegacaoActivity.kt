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
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fetin.innav.haptic.CalculadoraAnguloNavegacao
import com.fetin.innav.haptic.HapticManager
import com.fetin.innav.haptic.OrientationManager
import com.fetin.innav.models.No
import androidx.core.graphics.toColorInt
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

    // LÓGICA DE CHECKPOINTS
    private var rotaCheckpoints = mutableListOf<No>()
    private var rotaInstrucoes = mutableListOf<String>()
    private var indiceCheckpointAtual = 0
    private lateinit var alvoAtual: No
    private val noAtualUsuario = No(id = "USER", nomeLocal = "Posição Atual", x = 0.0, y = 0.0)

    private val filtroKalman = com.fetin.innav.filtering.FiltroKalmanRssi()

    // Variáveis voláteis atualizadas em tempo real
    private var anguloAlvo = 0f
    private var azimuteAtual = 0f
    private var ultimoRssiFiltrado = -100.0
    private var ultimaDistancia = 99.0
    private var tempoUltimoSinal = 0L // 🚨 TRAVA DE SEGURANÇA (TIMEOUT)
    private var primeiraInstrucaoFalada = false // 🚨 NOVA TRAVA DE ÁUDIO

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("SetTextI18n", "DefaultLocale", "MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val macAddress = result.device.address
            val deviceName = runCatching { result.device.name }.getOrNull() ?: result.scanRecord?.deviceName
            val rssiBruto = result.rssi

            // Verifica se o sinal é do ESP alvo do checkpoint atual
            if (::alvoAtual.isInitialized && alvoAtual.correspondeAoDispositivo(macAddress, deviceName) && !chegouNoDestino) {

                // 🚨 GATILHO INICIAL: Fala a 1ª frase APENAS quando recebe o sinal do ESP!
                if (!primeiraInstrucaoFalada) {
                    falar(rotaInstrucoes[indiceCheckpointAtual])
                    primeiraInstrucaoFalada = true
                }

                ultimoRssiFiltrado = filtroKalman.filtrar(rssiBruto.toDouble())
                ultimaDistancia = CalculadoraAnguloNavegacao.estimarDistanciaMetros(ultimoRssiFiltrado)
                anguloAlvo = CalculadoraAnguloNavegacao.calcularAnguloAlvo(noAtualUsuario, alvoAtual)

                // Registra o momento exato em que o radar "viu" o ESP
                tempoUltimoSinal = System.currentTimeMillis()

                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        txtSinal.text = "Faltam\n%.1f metros".format(ultimaDistancia)
                    }
                }

                // GATILHO DE ÁUDIO: Quando passa perto fisicamente do ESP
                if (ultimoRssiFiltrado >= -52.0) {
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

        // LÊ O BOTÃO QUE FOI CLICADO NA TELA ANTERIOR
        val id = intent.getStringExtra("DESTINO_ID") ?: "ESP_02"
        val nome = intent.getStringExtra("DESTINO_NOME") ?: "Corredor Central"
        val mac = intent.getStringExtra("DESTINO_MAC") ?: "AA:BB:CC:DD:EE:FF"
        val devName = intent.getStringExtra("DESTINO_NAME") ?: "INNAV_ESP_02"
        val x = intent.getDoubleExtra("DESTINO_X", 5.0)
        val y = intent.getDoubleExtra("DESTINO_Y", 5.0)

        // SE O USUÁRIO ESCOLHER A MESA, ATIVA A ROTA DOS 4 PASSOS
        if (id == "ESP_04") {
            montarRotaDaMesa()
            txtTituloDestino?.text = "MESA"
        } else {
            // SE FOR OUTRO LUGAR, MANTÉM O PADRÃO SIMPLES
            val destinoSelecionado = No(id, nome, mac, devName, x, y)
            rotaCheckpoints.clear()
            rotaCheckpoints.add(destinoSelecionado)
            rotaInstrucoes.clear()
            rotaInstrucoes.add("Iniciando navegação para $nome.")

            alvoAtual = rotaCheckpoints[0]
            indiceCheckpointAtual = 0
            atualizarInterfaceEAngulo()
            txtTituloDestino?.text = alvoAtual.nomeLocal.uppercase()
        }

        orientationManager.onAzimuthChanged = { azimuthDegrees ->
            var azimuteCorrigido = azimuthDegrees + 180f
            if (azimuteCorrigido >= 360f) {
                azimuteCorrigido -= 360f
            }
            azimuteAtual = azimuteCorrigido

            // Verifica se recebemos sinal nos últimos 3 segundos (3000ms)
            val sinalAtivo = (System.currentTimeMillis() - tempoUltimoSinal) < 3000L

            if (!chegouNoDestino && ultimaDistancia != 99.0 && ::alvoAtual.isInitialized && sinalAtivo) {
                val diferencaAngular = HapticManager.calculateAngularDifference(azimuteAtual, anguloAlvo)

                if (diferencaAngular <= 40f) {
                    if (ultimaDistancia <= 6.0) {
                        hapticManager.vibratePulse(durationMs = 50L, amplitude = 180)
                        txtSinal.setTextColor("#00FF00".toColorInt()) // Verde
                    } else {
                        hapticManager.stop()
                        txtSinal.setTextColor("#FFFFFF".toColorInt()) // Branco
                    }
                } else {
                    hapticManager.stop()
                    txtSinal.setTextColor("#FFFFFF".toColorInt()) // Branco
                }
            } else {
                // Se perder o sinal, desliga a vibração por segurança
                hapticManager.stop()
                txtSinal.setTextColor("#FFFFFF".toColorInt())
            }
        }

        btnAjuda.setOnClickListener { encerrarEVoltar() }

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

    // 🚨 A ROTA COM OS 4 ESPS QUE VOCÊ PEDIU
    private fun montarRotaDaMesa() {
        rotaCheckpoints.clear()
        rotaInstrucoes.clear()

        // 1º Checkpoint
        rotaCheckpoints.add(No(id = "ESP_01", nomeLocal = "Banheiro", macAddress = "20:43:A8:63:34:EE", deviceName = "INNAV_ESP_01", x = 0.0, y = 5.0))
        rotaInstrucoes.add("Iniciando navegação. O Banheiro está do seu lado direito.Siga o piso tátil e vire para a direita")

        // 2º Checkpoint
        rotaCheckpoints.add(No(id = "ESP_02", nomeLocal = "Corredor", macAddress = "14:2B:2F:C1:FE:72", deviceName = "INNAV_ESP_02", x = 0.0, y = 10.0))
        rotaInstrucoes.add("você está no corredor.")

        // 3º Checkpoint
        rotaCheckpoints.add(No(id = "ESP_03", nomeLocal = "CDG", macAddress = "3C:8A:1F:A4:B3:82", deviceName = "INNAV_ESP_03", x = 0.0, y = 15.0))
        rotaInstrucoes.add("Você passou pela Copa. Continue em frente até o CDG.")

        // 4º Destino Final
        rotaCheckpoints.add(No(id = "ESP_04", nomeLocal = "Mesa INNAV", macAddress = "5C:01:3B:47:2A:B6", deviceName = "INNAV_ESP_04", x = 5.0, y = 15.0))
        rotaInstrucoes.add("Você passou pelo CDG. Vire à direita e caminhe até a Mesa.")

        alvoAtual = rotaCheckpoints[0]
        indiceCheckpointAtual = 0
        atualizarInterfaceEAngulo()
    }

    private fun avancarParaProximoCheckpoint() {
        hapticManager.vibrateConfirmation()
        indiceCheckpointAtual++

        if (indiceCheckpointAtual < rotaCheckpoints.size) {
            // Ainda tem módulos pela frente
            alvoAtual = rotaCheckpoints[indiceCheckpointAtual]
            falar(rotaInstrucoes[indiceCheckpointAtual])
            atualizarInterfaceEAngulo()
        } else {
            // Chegou no último (Mesa - ESP4)
            chegouNoDestino = true
            falar("Você chegou à Mesa. Navegação concluída com sucesso.")
            finalizarNavegacaoComSucesso()
        }
    }

    private fun atualizarInterfaceEAngulo() {
        runOnUiThread {
            if(::txtSinal.isInitialized) {
                txtSinal.text = "Buscando caminho para:\n${alvoAtual.nomeLocal}..."
            }
        }
        ultimoRssiFiltrado = -100.0
        ultimaDistancia = 99.0
        anguloAlvo = CalculadoraAnguloNavegacao.calcularAnguloAlvo(noAtualUsuario, alvoAtual)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale("pt", "BR"))
            // Removido o gatilho de fala daqui. Agora o app abre em silêncio.
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
            // HACK SAMSUNG ESTÁ PROTEGIDO NESTA BRANCH!
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
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }

    override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
        if (::gestureDetector.isInitialized) {
            event?.let { gestureDetector.onTouchEvent(it) }
        }
        return super.onTouchEvent(event)
    }
}
