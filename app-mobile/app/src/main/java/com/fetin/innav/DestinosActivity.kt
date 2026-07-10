package com.fetin.innav

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
    private lateinit var speechRecognizer: SpeechRecognizer

    // Pedido de permissão do microfone em tempo real
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

        // 1. RESTAURADO: Seu botão original do Laboratório funcionando perfeitamente
        val btnDestinoLab = findViewById<Button>(R.id.btnDestinoLab)
        btnDestinoLab.setOnClickListener {
            Toast.makeText(this, "Rota para o Laboratório selecionada!", Toast.LENGTH_LONG).show()
            falar("Rota para o laboratório selecionada. Iniciando navegação.")

            window.decorView.postDelayed({
                val intent = Intent(this, NavegacaoActivity::class.java)
                startActivity(intent)
                finish()
            }, 3500)
        }

        // 2. NOVO: Tocar em qualquer lugar vazio da tela ativa o microfone
        val telaInteira = findViewById<View>(android.R.id.content)
        telaInteira.setOnClickListener {
            verificarPermissaoEOuvir()
        }
    }

    private fun configurarReconhecimentoDeVoz() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
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

                        window.decorView.postDelayed({
                            val intent = Intent(this@DestinosActivity, NavegacaoActivity::class.java)
                            startActivity(intent)
                            finish()
                        }, 4000)
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

        window.decorView.postDelayed({
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            }
            speechRecognizer.startListening(intent)
        }, 2500)
    }

    private fun falar(texto: String) {
        if (::tts.isInitialized) {
            tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale("pt", "BR"))
            falar("Toque para escolher seu destino, ou toque na parte de baixow' da tela para falar o seu destino.")
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
        super.onDestroy()
    }
}