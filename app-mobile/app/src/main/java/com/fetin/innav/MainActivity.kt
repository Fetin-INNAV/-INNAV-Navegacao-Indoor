package com.fetin.innav

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

// Importação explícita de todos os modelos necessários
import com.fetin.innav.models.Aresta
import com.fetin.innav.models.CalculadoraRota
import com.fetin.innav.models.Grafo
import com.fetin.innav.models.No

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val bluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    private lateinit var tts: TextToSpeech

    private var ultimoCheckpointVisitado = ""

    // Mapa físico de nós
    private val mapaInatel = Grafo()
    private lateinit var portaria: No
    private lateinit var corredor: No
    private lateinit var labHardware: No
    private lateinit var labCircuitos: No
    private lateinit var cdg: No

    private val filtroKalman = com.fetin.innav.filtering.FiltroKalmanRssi()

    // Scanner BLE do Menu Principal
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val macAddress = result.device.address
            val deviceName = runCatching { result.device.name }.getOrNull() ?: result.scanRecord?.deviceName
            val rssiBruto = result.rssi

            val rssiFiltrado = filtroKalman.filtrar(rssiBruto.toDouble())

            if (portaria.correspondeAoDispositivo(macAddress, deviceName) && rssiFiltrado > -60.0) {
                val idDispositivo = deviceName ?: macAddress

                if (ultimoCheckpointVisitado != idDispositivo) {
                    ultimoCheckpointVisitado = idDispositivo

                    Log.d("INNAV_ROTA", "📍 ALVO DETECTADO! Você está na: Portaria Principal ($idDispositivo)")

                    val calculadora = CalculadoraRota(mapaInatel)
                    val rotaCalculada: List<Aresta> = calculadora.calcularCaminhoMaisCurto(portaria, labHardware)

                    Log.d("INNAV_ROTA", "✅ Caminho traçado com sucesso!")
                    rotaCalculada.forEach { aresta: Aresta ->
                        Log.d("INNAV_ROTA", "-> ${aresta.instrucao}")
                    }

                    runOnUiThread { Toast.makeText(this@MainActivity, "📍 Rota Calculada!", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            iniciarScannerBluetooth()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tts = TextToSpeech(this, this)

        montarMapaFisico()
        pedirPermissoesBluetooth()
        configurarBotoesDaInterface()
    }

    private fun configurarBotoesDaInterface() {
        val btnDestino = findViewById<Button>(R.id.btnEscolherDestino)
        val btnExploracao = findViewById<Button>(R.id.btnExploracaoLivre)

        configurarBotaoComDuploToque(
            button = btnDestino,
            nomeBotao = "Definir Destino",
            falaConfirmacao = "Iniciando Escolha de Destino",
            acaoDuploClique = {
                val intent = Intent(this, DestinosActivity::class.java)
                startActivity(intent)
            }
        )

        configurarBotaoComDuploToque(
            button = btnExploracao,
            nomeBotao = "Exploração Livre",
            falaConfirmacao = "Iniciando Exploração Livre",
            acaoDuploClique = {
                val intent = Intent(this, ExploracaoLivreActivity::class.java)
                startActivity(intent)
            }
        )
    }

    /**
     * Configura o comportamento de foco (primeiro clique = apenas anuncia nome)
     * e seleção por duplo clique (dois toques rápidos = confirma e executa a ação).
     */
    private fun configurarBotaoComDuploToque(
        button: Button,
        nomeBotao: String,
        falaConfirmacao: String,
        acaoDuploClique: () -> Unit
    ) {
        var ultimoClique = 0L
        val intervaloDuploClique = 500L // 500ms de tolerância para o segundo toque

        button.setOnClickListener {
            val agora = System.currentTimeMillis()
            if (agora - ultimoClique <= intervaloDuploClique) {
                // SEGUNDO CLIQUE (DUPLO TOQUE): Confirmação de seleção
                ultimoClique = 0L
                falar(falaConfirmacao)

                button.postDelayed({
                    pararAudioETerminarScanner()
                    acaoDuploClique()
                }, 350)
            } else {
                // PRIMEIRA CLIQUE / FOCO: Anuncia apenas o nome curto do botão
                ultimoClique = agora
                falar(nomeBotao)
            }
        }

        // Anuncia o nome do botão quando o foco de acessibilidade mudar
        button.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                falar(nomeBotao)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun pararAudioETerminarScanner() {
        if (::tts.isInitialized) {
            tts.stop()
        }
        bleScanner?.stopScan(scanCallback)
    }

    /**
     * Emite áudio via TTS cancelando e interrompendo qualquer fala anterior imediatamente (QUEUE_FLUSH).
     */
    private fun falar(texto: String) {
        if (::tts.isInitialized) {
            tts.stop() // Garante parada imediata da fala anterior
            tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "MAIN_TTS_ID")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("pt", "BR"))
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                // Mensagem de abertura curta e objetiva
                falar("InNav aberto. Toque na parte de cima da tela para escolher o seu destino, ou na parte de baixo para o radar de ambiente. Dê dois toques na tela para selecionar.")
            }
        }
    }

    private fun montarMapaFisico() {
        portaria = No(
            id = "ESP_01",
            nomeLocal = "Portaria Principal",
            deviceName = "INNAV_ESP_01",
            x = 0.0,
            y = 5.0
        )
        corredor = No(
            id = "ESP_02",
            nomeLocal = "Corredor Central",
            deviceName = "INNAV_ESP_02",
            x = 5.0,
            y = 5.0
        )
        labHardware = No(
            id = "ESP_03",
            nomeLocal = "Laboratório de Hardware",
            deviceName = "INNAV_ESP_03",
            x = 10.0,
            y = 10.0
        )
        labCircuitos = No(
            id = "ESP_04",
            nomeLocal = "Lab de Circuitos",
            deviceName = "INNAV_ESP_04",
            x = 0.0,
            y = 10.0
        )
        cdg = No(
            id = "ESP_05",
            nomeLocal = "CDG",
            deviceName = "INNAV_ESP_05",
            x = 15.0,
            y = 10.0
        )

        mapaInatel.adicionarAresta(origem = portaria, destino = corredor, distancia = 10.0, instrucao = "Siga 10 metros em frente pelo corredor principal.")
        mapaInatel.adicionarAresta(origem = corredor, destino = labHardware, distancia = 5.0, instrucao = "Vire à direita e ande 5 metros para chegar ao laboratório de hardware.")
        mapaInatel.adicionarAresta(origem = portaria, destino = labCircuitos, distancia = 5.0, instrucao = "Siga à esquerda por 5 metros até o laboratório de circuitos.")
        mapaInatel.adicionarAresta(origem = labHardware, destino = cdg, distancia = 7.0, instrucao = "Continue pelo corredor por 7 metros até o CDG.")
    }

    private fun pedirPermissoesBluetooth() {
        val permissoes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (permissoes.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissionLauncher.launch(permissoes)
        } else {
            iniciarScannerBluetooth()
        }
    }

    @SuppressLint("MissingPermission")
    private fun iniciarScannerBluetooth() {
        if (bleScanner != null) {
            bleScanner?.startScan(scanCallback)
            Toast.makeText(this, "Radar INNAV Ativado!", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        iniciarScannerBluetooth()
    }

    @SuppressLint("MissingPermission")
    override fun onPause() {
        super.onPause()
        bleScanner?.stopScan(scanCallback)
        if (::tts.isInitialized) {
            tts.stop()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        bleScanner?.stopScan(scanCallback)
        super.onDestroy()
    }
}