package com.fetin.innav

import android.Manifest
import android.annotation.SuppressLint
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
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.fetin.innav.haptic.HapticManager
import com.fetin.innav.models.No
import java.util.Locale

class DestinosActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var hapticManager: HapticManager
    private lateinit var gestureDetector: GestureDetector

    // 5 Destinos físicos representados pelos 5 ESPs
    private val espPortaria = No("ESP_01", "Portaria", "20:43:A8:63:34:EE", "INNAV_ESP_01", 0.0, 5.0)
    private val espCorredor = No("ESP_02", "Corredor", "14:2B:2F:C1:FE:72", "INNAV_ESP_02", 5.0, 5.0)
    private val espLabHardware = No("ESP_03", "Laboratório de Hardware", "3C:8A:1F:A4:B3:82", "INNAV_ESP_03", 10.0, 10.0)
    private val espLabCircuitos = No("ESP_04", "Lab de Circuitos", "5C:01:3B:47:2A:B6", "INNAV_ESP_04", 0.0, 10.0)
    private val espCDG = No("ESP_05", "CDG", "68:25:DD:48:1F:12", "INNAV_ESP_05", 15.0, 10.0)

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            iniciarEscuta()
        } else {
            falar("Permissão de microfone negada.")
            Toast.makeText(this, "Permissão de Áudio Negada", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_destinos)

        tts = TextToSpeech(this, this)
        hapticManager = HapticManager(this)
        configurarReconhecimentoDeVoz()

        val btnPortaria = findViewById<Button>(R.id.btnDestinoPortaria)
        val btnCorredor = findViewById<Button>(R.id.btnDestinoCorredor)
        val btnHardware = findViewById<Button>(R.id.btnDestinoHardware)
        val btnCircuitos = findViewById<Button>(R.id.btnDestinoCircuitos)
        val btnCDG = findViewById<Button>(R.id.btnDestinoCDG)

        configurarBotaoDestino(btnPortaria, espPortaria, "Portaria")
        configurarBotaoDestino(btnCorredor, espCorredor, "Corredor")
        configurarBotaoDestino(btnHardware, espLabHardware, "Laboratório de Hardware")
        configurarBotaoDestino(btnCircuitos, espLabCircuitos, "Lab de Circuitos")
        configurarBotaoDestino(btnCDG, espCDG, "CDG")

        // 🚨 DETECTOR DE GESTOS UNIFICADO (Voz + Voltar)
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            // O gesto de arrastar para voltar (que já estava funcionando perfeitamente)
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null && e2 != null) {
                    val diffY = e2.y - e1.y
                    val diffX = e2.x - e1.x
                    if (Math.abs(diffY) > Math.abs(diffX) && Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY < 0) { // Arrastou para cima
                            hapticManager.vibratePulse(50, 200)
                            finish()
                            return true
                        }
                    }
                }
                return false
            }

            // 🚨 NOVO COMANDO DE VOZ: Segurar o dedo na tela
            override fun onLongPress(e: MotionEvent) {
                // Dá um tranco bem forte na mão para avisar que o celular percebeu o "Segurar"
                hapticManager.vibratePulse(60, 250)
                verificarPermissaoEOuvir()
            }
        })
    }

    private fun configurarBotaoDestino(button: Button, noDestino: No, falaNome: String) {
        var ultimoClique = 0L
        val intervaloDuploClique = 500L

        button.setOnClickListener {
            val agora = System.currentTimeMillis()
            if (agora - ultimoClique <= intervaloDuploClique) {
                ultimoClique = 0L
                val falaConfirmacao = "Iniciando navegação para $falaNome"
                falar(falaConfirmacao)
                hapticManager.vibrateConfirmation()

                button.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        iniciarNavegacaoPara(noDestino)
                    }
                }, 1000)
            } else {
                ultimoClique = agora
                falar(falaNome)
                hapticManager.vibratePulse(30, 100)
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
            Log.w("INNAV_VOZ", "Serviço de voz indisponível.")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(this@DestinosActivity, "Ouvindo...", Toast.LENGTH_SHORT).show()
                hapticManager.vibratePulse(40, 150)
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                falar("Não consegui entender. Segure o dedo na tela e repita.")
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val textoFalado = matches[0].lowercase(Locale.getDefault())

                    val destinoEncontrado = when {
                        textoFalado.contains("portaria") -> espPortaria
                        textoFalado.contains("corredor") -> espCorredor
                        textoFalado.contains("hardware") || textoFalado.contains("laboratório") -> espLabHardware
                        textoFalado.contains("circuito") || textoFalado.contains("circuitos") -> espLabCircuitos
                        textoFalado.contains("cdg") || textoFalado.contains("c d g") -> espCDG
                        else -> null
                    }

                    if (destinoEncontrado != null) {
                        falar("Rota para ${destinoEncontrado.nomeLocal} selecionada. Iniciando.")
                        hapticManager.vibrateConfirmation()
                        handler.postDelayed({
                            if (!isFinishing && !isDestroyed) {
                                iniciarNavegacaoPara(destinoEncontrado)
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
            if (::tts.isInitialized) tts.stop()

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            }
            speechRecognizer?.startListening(intent)
        }, 1500)
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
            // 🚨 Aviso atualizado para o gesto correto
            falar("Selecione o seu destino na lista, ou segure o dedo na tela para falar no microfone.")
        }
    }

    private fun pararRecursos() {
        handler.removeCallbacksAndMessages(null)
        if (::tts.isInitialized) tts.stop()
        speechRecognizer?.cancel()
    }

    override fun onPause() {
        super.onPause()
        pararRecursos()
    }

    override fun onDestroy() {
        pararRecursos()
        if (::tts.isInitialized) tts.shutdown()
        speechRecognizer?.destroy()
        speechRecognizer = null
        hapticManager.stop()
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let { gestureDetector.onTouchEvent(it) }
        return super.dispatchTouchEvent(ev)
    }
}