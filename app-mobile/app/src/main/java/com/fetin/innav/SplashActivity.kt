package com.fetin.innav

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
//import
class SplashActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Inicializa o motor de voz
        tts = TextToSpeech(this, this)

        // Cria o temporizador: Espera 4000 milissegundos (4 segundos) e roda a função irParaMain()
        Handler(Looper.getMainLooper()).postDelayed({
            irParaMain()
        }, 4000)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val resultado = tts.setLanguage(Locale("pt", "BR"))

            if (resultado == TextToSpeech.LANG_MISSING_DATA || resultado == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("INNAV_TTS", "Idioma não suportado.")
            } else {
                // A voz de Onboarding da sua documentação!
                falar("Bem-vindo ao INNAV. O seu assistente de navegação indoor.")
            }
        }
    }

    private fun falar(texto: String) {
        if (::tts.isInitialized) {
            tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "")
        }
    }

    private fun irParaMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)

        // Finaliza a Splash Screen para o usuário não conseguir voltar para ela apertando "Voltar"
        finish()
    }

    override fun onDestroy() {
        // Desliga a voz se fechar o app no meio do caminho
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}