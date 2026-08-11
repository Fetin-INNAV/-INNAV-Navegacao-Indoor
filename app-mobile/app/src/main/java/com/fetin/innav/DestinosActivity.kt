package com.fetin.innav

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.fetin.innav.models.No
import java.util.Locale

class DestinosActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())

    // 5 Destinos físicos representados pelos 5 ESPs
    private val espPortaria = No(
        id = "ESP_01",
        nomeLocal = "Portaria",
        deviceName = "INNAV_ESP_01",
        x = 0.0,
        y = 5.0
    )

    private val espCorredor = No(
        id = "ESP_02",
        nomeLocal = "Corredor",
        deviceName = "INNAV_ESP_02",
        x = 5.0,
        y = 5.0
    )

    private val espLabHardware = No(
        id = "ESP_03",
        nomeLocal = "Laboratório de Hardware",
        deviceName = "INNAV_ESP_03",
        x = 10.0,
        y = 10.0
    )

    private val espLabCircuitos = No(
        id = "ESP_04",
        nomeLocal = "Lab de Circuitos",
        deviceName = "INNAV_ESP_04",
        x = 0.0,
        y = 10.0
    )

    private val espCDG = No(
        id = "ESP_05",
        nomeLocal = "CDG",
        deviceName = "INNAV_ESP_05",
        x = 15.0,
        y = 10.0
    )

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            iniciarEscuta()
        } else {
            falar("Permissão de microfone negada. Não é possível usar o comando de voz.")
            Toast.makeText(this, "Permissão de Áudio Negada", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_destinos)

        tts = TextToSpeech(this, this)
        configurarReconhecimentoDeVoz()

        val btnPortaria = findViewById<Button>(R.id.btnDestinoPortaria)
        val btnCorredor = findViewById<Button>(R.id.btnDestinoCorredor)
        val btnHardware = findViewById<Button>(R.id.btnDestinoHardware)
        val btnCircuitos = findViewById<Button>(R.id.btnDestinoCircuitos)
        val btnCDG = findViewById<Button>(R.id.btnDestinoCDG)

        configurarBotaoDestino(
            button = btnPortaria,
            noDestino = espPortaria,
            falaNome = "Portaria"
        )

        configurarBotaoDestino(
            button = btnCorredor,
            noDestino = espCorredor,
            falaNome = "Corredor"
        )

        configurarBotaoDestino(
            button = btnHardware,
            noDestino = espLabHardware,
            falaNome = "Laboratório de Hardware"
        )

        configurarBotaoDestino(
            button = btnCircuitos,
            noDestino = espLabCircuitos,
            falaNome = "Lab de Circuitos"
        )

        configurarBotaoDestino(
            button = btnCDG,
            noDestino = espCDG,
            falaNome = "CDG"
        )

        val telaInteira = findViewById<View>(android.R.id.content)
        telaInteira.setOnClickListener {
            verificarPermissaoEOuvir()
        }
    }

    /**
     * Configura o comportamento de gestos para cada destino (lógica idêntica ao menu inicial):
     * - 1 toque (ou foco): a voz TTS fala o nome do destino.
     * - 2 toques rápidos (duplo toque): fala a confirmação e inicia a navegação para o destino.
     */
    private fun configurarBotaoDestino(
        button: Button,
        noDestino: No,
        falaNome: String
    ) {
        var ultimoClique = 0L
        val intervaloDuploClique = 500L

        button.setOnClickListener {
            val agora = System.currentTimeMillis()
            if (agora - ultimoClique <= intervaloDuploClique) {
                // DUPLO TOQUE (2 toques): Confirmação de seleção e início da navegação
                ultimoClique = 0L
                val falaConfirmacao = "Iniciando navegação para $falaNome"
                falar(falaConfirmacao)

                button.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        iniciarNavegacaoPara(noDestino)
                    }
                }, 350)
            } else {
                // UM TOQUE (1 toque): Anuncia o nome do destino
                ultimoClique = agora
                falar(falaNome)
            }
        }

        button.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                falar(falaNome)
            }
        }
    }

    private fun iniciarNavegacaoPara(noDestino: No) {
        pararRecursos()
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

    private fun configurarReconhecimentoDeVoz() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w("INNAV_VOZ", "Serviço de reconhecimento de voz indisponível neste dispositivo.")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(this@DestinosActivity, "Ouvindo...", Toast.LENGTH_SHORT).show()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                Log.e("INNAV_VOZ", "Erro ao ouvir: $error")
                falar("Não consegui entender. Por favor, toque na tela e repita o destino.")
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val textoFalado = matches[0].lowercase(Locale.getDefault())
                    Log.d("INNAV_VOZ", "Usuário disse: $textoFalado")

                    val destinoEncontrado = when {
                        textoFalado.contains("portaria") -> espPortaria
                        textoFalado.contains("corredor") -> espCorredor
                        textoFalado.contains("hardware") || textoFalado.contains("laboratório de hardware") || textoFalado.contains("laboratorio de hardware") -> espLabHardware
                        textoFalado.contains("circuito") || textoFalado.contains("circuitos") || textoFalado.contains("lab de circuitos") -> espLabCircuitos
                        textoFalado.contains("cdg") || textoFalado.contains("c d g") -> espCDG
                        else -> null
                    }

                    if (destinoEncontrado != null) {
                        falar("Rota para ${destinoEncontrado.nomeLocal} selecionada por voz. Iniciando navegação.")
                        handler.postDelayed({
                            if (!isFinishing && !isDestroyed) {
                                iniciarNavegacaoPara(destinoEncontrado)
                            }
                        }, 1500)
                    } else {
                        falar("Destino não encontrado. Você disse: $textoFalado. Tente novamente.")
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun verificarPermissaoEOuvir() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            iniciarEscuta()
        } else {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun iniciarEscuta() {
        falar("Diga o nome do local para onde deseja ir.")

        handler.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            if (::tts.isInitialized) {
                tts.stop()
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            }
            speechRecognizer?.startListening(intent)
        }, 2000)
    }

    private fun falar(texto: String) {
        if (::tts.isInitialized) {
            tts.stop()
            tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "DESTINOS_TTS_ID")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale("pt", "BR"))
            falar("Selecione o seu destino na lista ou toque na tela para falar.")
        }
    }

    private fun pararRecursos() {
        handler.removeCallbacksAndMessages(null)
        if (::tts.isInitialized) {
            tts.stop()
        }
        speechRecognizer?.cancel()
    }

    override fun onPause() {
        super.onPause()
        pararRecursos()
    }

    override fun onDestroy() {
        pararRecursos()
        if (::tts.isInitialized) {
            tts.shutdown()
        }
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }
}