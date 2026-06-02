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
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

// Importando a sua pasta de modelos onde estão o Grafo e os Nós
import com.fetin.innav.models.*

class MainActivity : AppCompatActivity() {

    private val bluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    // Variável de segurança: impede que o sistema calcule a rota repetidamente
    private var ultimoCheckpointVisitado = ""

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

            // O MAC oficial do seu ESP32
            val macEspPortaria = "68:25:DD:48:1F:12"

            // Se encontrou o ESP32 com um sinal forte
            if (macAddress == macEspPortaria && rssi > -60) {

                // Verifica se já estávamos parados aqui
                if (ultimoCheckpointVisitado != macEspPortaria) {
                    ultimoCheckpointVisitado = macEspPortaria

                    Log.d("INNAV_ROTA", "===================================================")
                    Log.d("INNAV_ROTA", "📍 ALVO DETETADO! Você está na: Portaria Principal")
                    Log.d("INNAV_ROTA", "Força do Sinal: $rssi dBm")
                    Log.d("INNAV_ROTA", "Acionando o algoritmo de Dijkstra...")

                    // A Mágica Matemática com a correção de Orientação a Objetos:
                    // Passamos o mapa direto no construtor da classe
                    val calculadora = CalculadoraRota(mapaInatel)

                    // Calculamos a rota da origem ao destino
                    val rotaCalculada = calculadora.calcularCaminhoMaisCurto(portaria, labHardware)

                    Log.d("INNAV_ROTA", "✅ Caminho traçado com sucesso! Siga as instruções:")

                    // Imprime o passo a passo da rota no Logcat
                    rotaCalculada?.forEach { aresta ->
                        Log.d("INNAV_ROTA", "-> ${aresta.instrucao}")
                    }

                    Log.d("INNAV_ROTA", "===================================================")

                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "📍 Checkpoint: Portaria!", Toast.LENGTH_LONG).show()
                    }
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

        montarMapaFisico()
        pedirPermissoesBluetooth()
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
}