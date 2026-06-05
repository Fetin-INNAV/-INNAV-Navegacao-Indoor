package com.fetin.innav

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class NavegacaoActivity : AppCompatActivity() {

    private val bluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val bleScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    private var chegouNoDestino = false
    private lateinit var txtSinal: TextView

    // O Scanner que atualiza a tela ao vivo
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("SetTextI18n")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val macAddress = result.device.address
            val rssi = result.rssi

            // TODO: Coloque o MAC Address do SEU ESP32 aqui (ex: "A1:B2:C3:D4:E5:F6")
            val macEspLaboratorio = "68:25:DD:48:1F:12"

            if (macAddress == macEspLaboratorio && !chegouNoDestino) {
                // Atualiza o texto verde na tela ao vivo
                runOnUiThread {
                    txtSinal.text = "Sinal do Lab: $rssi dBm"
                }

                // Gatilho de Chegada: Se o sinal for mais forte que -45 dBm (muito perto)
                if (rssi > -45) {
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

        txtSinal = findViewById(R.id.txtSinalAoVivo)
        val btnAjuda = findViewById<Button>(R.id.btnAjuda)

        // Liga o radar ao entrar nesta tela
        bleScanner?.startScan(scanCallback)

        // Se clicar em Ajuda, para de escanear e volta uma tela (Destinos)
        btnAjuda.setOnClickListener {
            bleScanner?.stopScan(scanCallback)
            finish()
        }
    }

    @SuppressLint("MissingPermission")
    private fun finalizarNavegacaoComSucesso() {
        // 1. Desliga o radar
        bleScanner?.stopScan(scanCallback)

        // 2. Dá um feedback físico (Vibração longa)
        vibrarCelular()

        // 3. Mostra um aviso claro na tela
        runOnUiThread {
            // Alterado para ir para a ChegadaActivity
            val intent = Intent(this, ChegadaActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    // Função para fazer o celular vibrar
    private fun vibrarCelular() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE)) // Vibra por 1 segundo
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(1000)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        // Garantia de que o radar desliga se o app for fechado
        bleScanner?.stopScan(scanCallback)
        super.onDestroy()
    }
}