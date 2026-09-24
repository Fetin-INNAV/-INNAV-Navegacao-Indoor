package com.fetin.innav

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fetin.innav.haptic.CalculadoraAnguloNavegacao
import com.fetin.innav.haptic.HapticManager
import com.fetin.innav.models.No
import com.fetin.innav.haptic.ExploracaoLivreManager

class ExploracaoLivreActivity : AppCompatActivity() {

    private lateinit var gestureDetector: android.view.GestureDetector

    private val bluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val bleScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    private lateinit var hapticManager: HapticManager
    private lateinit var exploracaoLivreManager: ExploracaoLivreManager

    private lateinit var txtStatusVarredura: TextView
    private lateinit var cardMaisProximo: LinearLayout
    private lateinit var txtNomeMaisProximo: TextView
    private lateinit var txtDistanciaMaisProximo: TextView

    private var ultimoLocalFalado: String = ""
    private var tempoUltimaFala: Long = 0
    private var idExibidoAtualmente: String = ""

    private val sinaisAtuais = mutableMapOf<String, Int>()

    private val checkpointsConhecidos = listOf(
        No("ESP_01", "Banheiro", "20:43:A8:63:34:EE", "INNAV_ESP_01", 0.0, 5.0),
        No("ESP_02", "Corredor", "14:2B:2F:C1:FE:72", "INNAV_ESP_02", 5.0, 5.0),
        No("ESP_03", "CDG", "3C:8A:1F:A4:B3:82", "INNAV_ESP_03", 10.0, 10.0),
        No("ESP_04", "Mesa INNAV", "5C:01:3B:47:2A:B6", "INNAV_ESP_04", 0.0, 10.0),
        No("ESP_05", "...", "68:25:DD:48:1F:12", "INNAV_ESP_05", 15.0, 10.0)
    )

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission", "SetTextI18n")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val macAddress = result.device.address
            val deviceName = runCatching { result.device.name }.getOrNull() ?: result.scanRecord?.deviceName
            val rssi = result.rssi

            val noEncontrado = checkpointsConhecidos.firstOrNull { it.correspondeAoDispositivo(macAddress, deviceName) }

            if (noEncontrado != null) {
                sinaisAtuais[noEncontrado.id] = rssi
                atualizarInterfaceVisual()
            }
        }
    }

    @SuppressLint("MissingPermission", "DefaultLocale")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exploracao_livre)

        txtStatusVarredura = findViewById(R.id.txtStatusVarredura)
        cardMaisProximo = findViewById(R.id.cardMaisProximo)
        txtNomeMaisProximo = findViewById(R.id.txtNomeMaisProximo)
        txtDistanciaMaisProximo = findViewById(R.id.txtDistanciaMaisProximo)

        txtStatusVarredura.setTextColor(android.graphics.Color.parseColor("#FFE500"))

        val btnSair = findViewById<Button>(R.id.btnSairExploracao)

        hapticManager = HapticManager(this)
        exploracaoLivreManager = ExploracaoLivreManager(this, hapticManager)

        window.decorView.postDelayed({
            if (!isFinishing && !isDestroyed) {
            }
        }, 800)

        btnSair.setOnClickListener {
            encerrarExploracaoEVoltar()
        }

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
                            encerrarExploracaoEVoltar()
                            return true
                        }
                    }
                }
                return super.onFling(e1, e2, velocityX, velocityY)
            }
        })
    }

    @SuppressLint("SetTextI18n")
    private fun atualizarInterfaceVisual() {
        val rankeados = sinaisAtuais.toList().sortedByDescending { (_, rssi) -> rssi }

        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread

            if (rankeados.isNotEmpty()) {
                val idMaisProximo = rankeados[0].first
                val rssiMaisProximo = rankeados[0].second

                // 🚨 LÓGICA ANTI PING-PONG DE TELA
                val rssiAtualNaTela = sinaisAtuais[idExibidoAtualmente] ?: -100

                // 1. ZONA MORTA: Se o local que está na tela ficar muito fraco (menor que -75), limpamos a tela.
                if (rssiAtualNaTela < -75) {
                    idExibidoAtualmente = ""
                }

                // 2. REGRA DE ROUBO DE TELA:
                if (idExibidoAtualmente.isEmpty()) {
                    // Se a tela está vazia, aceitamos o sinal mais forte se ele for no mínimo razoável (>= -70)
                    if (rssiMaisProximo >= -70) {
                        idExibidoAtualmente = idMaisProximo
                    }
                } else if (idMaisProximo != idExibidoAtualmente) {
                    // 🚨 A MÁGICA ACONTECE AQUI: Para um novo ESP roubar a tela, ele precisa ser
                    // 10 dBm MAIS FORTE que o ESP que já está na tela. Isso mata a "piscada" de nomes!
                    if (rssiMaisProximo > (rssiAtualNaTela + 10)) {
                        idExibidoAtualmente = idMaisProximo
                    }
                }

                // 3. ATUALIZAR A INTERFACE COM O VENCEDOR
                if (idExibidoAtualmente.isNotEmpty()) {
                    val rssiDoExibido = sinaisAtuais[idExibidoAtualmente] ?: -100
                    val noExibido = checkpointsConhecidos.first { it.id == idExibidoAtualmente }
                    val estimativaMetros = CalculadoraAnguloNavegacao.estimarDistanciaMetros(rssiDoExibido.toDouble())

                    cardMaisProximo.visibility = View.VISIBLE
                    txtNomeMaisProximo.text = noExibido.nomeLocal
                    txtDistanciaMaisProximo.text = "Aprox. %.1fm".format(estimativaMetros)

                    // GATILHO DE ÁUDIO EXTREMO (-40 dBm = encostar no ESP)
                    if (rssiDoExibido >= -50) {
                        val tempoAtual = System.currentTimeMillis()

                        if (ultimoLocalFalado != noExibido.nomeLocal && (tempoAtual - tempoUltimaFala > 8000)) {
                            hapticManager.vibrateConfirmation()

                            val fraseVoz = when (noExibido.id) {
                                "ESP_01" -> "Você está no Banheiro."
                                "ESP_02" -> "Você está no Corredor."
                                "ESP_03" -> "Você está no CDG."
                                "ESP_04" -> "Você está na Mesa INNAV."
                                else -> "Você está perto de ${noExibido.nomeLocal}."
                            }

                            exploracaoLivreManager.falarMensagem(fraseVoz)
                            ultimoLocalFalado = noExibido.nomeLocal
                            tempoUltimaFala = tempoAtual
                        }
                    }
                } else {
                    // Esconde a caixa e mostra texto de caminhada
                    cardMaisProximo.visibility = View.GONE
                    txtStatusVarredura.text = "Caminhando... Buscando sinal"
                }

            } else {
                cardMaisProximo.visibility = View.GONE
                txtStatusVarredura.text = "Buscando locais próximos..."
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun iniciarRadarBLE() {
        val filtros = mutableListOf<ScanFilter>()
        val configuracaoRadar = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner?.startScan(filtros, configuracaoRadar, scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun pararRadarBLE() {
        bleScanner?.stopScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun encerrarExploracaoEVoltar() {
        pararRadarBLE()
        exploracaoLivreManager.stop()
        hapticManager.stop()
        finish()
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        iniciarRadarBLE()
    }

    @SuppressLint("MissingPermission")
    override fun onPause() {
        super.onPause()
        pararRadarBLE()
        hapticManager.stop()
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        pararRadarBLE()
        exploracaoLivreManager.stop()
        hapticManager.stop()
        super.onDestroy()
    }

    override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
        event?.let { gestureDetector.onTouchEvent(it) }
        return super.onTouchEvent(event)
    }
}