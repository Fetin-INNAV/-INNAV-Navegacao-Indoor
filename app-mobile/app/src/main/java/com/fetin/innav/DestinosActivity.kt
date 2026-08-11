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
import java.util.Locale

class DestinosActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())

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

        val btnDestinoLab = findViewById<Button>(R.id.btnDestinoLab)
        configurarBotaoComDuploToque(
            button = btnDestinoLab,
            nomeBotao = "Laboratório de Hardware",
            falaConfirmacao = "Rota para o laboratório selecionada. Iniciando navegação.",
            acaoDuploClique = {
                val intent = Intent(this, NavegacaoActivity::class.java)
                startActivity(intent)
                finish()
            }
        )

        val telaInteira = findViewById<View>(android.R.id.content)
        telaInteira.setOnClickListener {
            verificarPermissaoEOuvir()
        }
    }

    private fun configurarBotaoComDuploToque(
        button: Button,
        nomeBotao: String,
        falaConfirmacao: String,
        acaoDuploClique: () -> Unit
    ) {
        var ultimoClique = 0L
        val intervaloDuploClique = 500L

        button.setOnClickListener {
            val agora = System.currentTimeMillis()
            if (agora - ultimoClique <= intervaloDuploClique) {
                ultimoClique = 0L
                falar(falaConfirmacao)
                handler.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        pararRecursos()
                        acaoDuploClique()
                    }
                }, 1500)
            } else {
                ultimoClique = agora
                falar(nomeBotao)
            }
        }

        button.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                falar(nomeBotao)
            }
        }
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

                    if (textoFalado.contains("laboratório") || textoFalado.contains("laboratorio")) {
                        falar("Rota para o laboratório selecionada por voz. Iniciando navegação.")

                        handler.postDelayed({
                            if (!isFinishing && !isDestroyed) {
                                pararRecursos()
                                val intent = Intent(this@DestinosActivity, NavegacaoActivity::class.java)
                                startActivity(intent)
                                finish()
                            }
                        }, 2000)
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
                tts.stop() // Interrompe o TTS para o microfone não ouvir a própria voz do alto-falante
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