package com.fetin.innav

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class SplashActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private val handler = Handler(Looper.getMainLooper())
    private val splashRunnable = Runnable { irParaMain() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Inicializa o motor de voz
        tts = TextToSpeech(this, this)

        // Temporizador gerenciado para transição para a MainActivity
        handler.postDelayed(splashRunnable, 4300)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val resultado = tts.setLanguage(Locale("pt", "BR"))

            if (resultado == TextToSpeech.LANG_MISSING_DATA || resultado == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("INNAV_TTS", "Idioma PT-BR não suportado no dispositivo.")
            } else {
                falar("Bem-vindo ao INNAV. O seu assistente de navegação indoor.")
            }
        }
    }

    private fun falar(texto: String) {
        if (::tts.isInitialized) {
            tts.stop()
            tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "SPLASH_TTS_ID")
        }
    }

    private fun irParaMain() {
        if (isFinishing || isDestroyed) return
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onPause() {
        super.onPause()
        if (::tts.isInitialized) {
            tts.stop()
        }
    }

    override fun onDestroy() {
        // Cancela o Handler para evitar vazamento de memória e exceção de Activity destruída
        handler.removeCallbacks(splashRunnable)
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}