package com.fetin.innav

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
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
import android.content.Intent

// Importando a sua pasta de modelos onde estão o Grafo e os Nós
import com.fetin.innav.models.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val bluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    // Motor de Voz do Android (Text-to-Speech)
    private lateinit var tts: TextToSpeech

    // Variável de segurança: impede que o sistema calcule a rota repetidamente
    private var ultimoCheckpointVisitado = ""

    // Controle da Fase 2: Modo Rota vs Modo Exploração Livre
    private var modoExploracaoLivre = false

    // Instanciamos o mapa físico
    private val mapaInatel = Grafo()
    private lateinit var portaria: No
    private lateinit var corredor: No
    private lateinit var labHardware: No

    // O Radar com a Lógica de Navegação
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val macAddress = result.device.address
            val rssi = result.rssi

            // O MAC oficial do seu ESP32 (Altere para o MAC real do ESP32 que está com você)
            val macEspPortaria = "68:25:DD:48:1F:12"

            // Se encontrou o ESP32 com um sinal forte (Zona de Gatilho)
            if (macAddress == macEspPortaria && rssi > -60) {

                // Verifica se já estávamos parados aqui
                if (ultimoCheckpointVisitado != macEspPortaria) {
                    ultimoCheckpointVisitado = macEspPortaria

                    Log.d("INNAV_ROTA", "===================================================")
                    Log.d("INNAV_ROTA", "📍 ALVO DETECTADO! Você está na: Portaria Principal")
                    Log.d("INNAV_ROTA", "Força do Sinal: $rssi dBm")

                    if (modoExploracaoLivre) {
                        // FASE 2.1: Modo de Exploração (Dijkstra desativado)
                        val aviso = "Você está passando pela Portaria Principal."
                        Log.d("INNAV_ROTA", aviso)
                        falar(aviso)
                        runOnUiThread { Toast.makeText(this@MainActivity, aviso, Toast.LENGTH_LONG).show() }
                    } else {
                        // FASE 2.2: Rota Específica (Aciona Dijkstra)
                        Log.d("INNAV_ROTA", "Acionando o algoritmo de Dijkstra...")

                        val calculadora = CalculadoraRota(mapaInatel)
                        val rotaCalculada = calculadora.calcularCaminhoMaisCurto(portaria, labHardware)

                        Log.d("INNAV_ROTA", "✅ Caminho traçado com sucesso!")
                        falar("Checkpoint detectado. Rota traçada. Siga as instruções.")

                        rotaCalculada?.forEach { aresta ->
                            Log.d("INNAV_ROTA", "-> ${aresta.instrucao}")
                        }

                        runOnUiThread { Toast.makeText(this@MainActivity, "📍 Rota Calculada!", Toast.LENGTH_LONG).show() }
                    }
                    Log.d("INNAV_ROTA", "===================================================")
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

        // Inicializa o motor de voz nativo do Android
        tts = TextToSpeech(this, this)

        montarMapaFisico()
        pedirPermissoesBluetooth()
        configurarBotoesDaInterface()
    }

    private fun configurarBotoesDaInterface() {
        val btnDestino = findViewById<Button>(R.id.btnEscolherDestino)
        val btnExploracao = findViewById<Button>(R.id.btnExploracaoLivre)

        btnDestino.setOnClickListener {
            modoExploracaoLivre = false
            val mensagem = "Selecione o seu destino na tela."
            falar(mensagem)

            // A mágica acontece aqui: O Intent abre a DestinosActivity
            val intent = Intent(this, DestinosActivity::class.java)
            startActivity(intent)
        }

        btnExploracao.setOnClickListener {
            modoExploracaoLivre = true
            val mensagem = "Modo de exploração ativado. Caminhe livremente."
            falar(mensagem)
            Toast.makeText(this, "Modo Exploração Ativado", Toast.LENGTH_SHORT).show()
        }
    }

    // Função auxiliar para converter Texto em Voz
    private fun falar(texto: String) {
        if (::tts.isInitialized) {
            tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Configura o idioma da voz para Português do Brasil
            val result = tts.setLanguage(Locale("pt", "BR"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("INNAV_TTS", "Idioma não suportado ou faltando dados.")
            } else {
                Log.d("INNAV_TTS", "Sistema de voz inicializado com sucesso.")
            }
        } else {
            Log.e("INNAV_TTS", "Falha ao inicializar o TextToSpeech.")
        }
    }

    private fun montarMapaFisico() {
        portaria = No(id = "ESP_01", nomeLocal = "Portaria Principal")
        corredor = No(id = "ESP_02", nomeLocal = "Corredor Central")
        labHardware = No(id = "ESP_03", nomeLocal = "Laboratório de Hardware")

        mapaInatel.adicionarAresta(origem = portaria, destino = corredor, distancia = 10.0, instrucao = "Siga 10 metros em frente pelo corredor principal.")
        mapaInatel.adicionarAresta(origem = corredor, destino = labHardware, distancia = 5.0, instrucao = "Vire à direita e ande 5 metros para chegar ao laboratório.")

        Log.d("INNAV_ROTA", "Mapa carregado na memória. Pronto para navegar.")
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

    override fun onDestroy() {
        // É importante desligar a voz quando o app fechar para não travar a memória
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}