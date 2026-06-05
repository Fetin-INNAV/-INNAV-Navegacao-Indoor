package com.fetin.innav

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class ChegadaActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chegada)

        // Liga o motor de voz (isso aciona o onInit automaticamente)
        tts = TextToSpeech(this, this)

        val btnNovoDestino = findViewById<Button>(R.id.btnNovoDestino)
        val btnMenuPrincipal = findViewById<Button>(R.id.btnMenuPrincipal)

        btnNovoDestino.setOnClickListener {
            val intent = Intent(this, DestinosActivity::class.java)
            startActivity(intent)
            finish()
        }

        btnMenuPrincipal.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val resultado = tts.setLanguage(Locale("pt", "BR"))

            if (resultado == TextToSpeech.LANG_MISSING_DATA || resultado == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("INNAV_TTS", "Idioma não suportado.")
            } else {
                // A instrução espacial detalhada, sem cortes!
                val mensagemDeChegada = "Você chegou ao destino. A navegação foi encerrada. " +
                        "A tela possui dois botões. " +
                        "No meio da tela, aperte para Escolher Novo Destino. " +
                        "Na parte inferior da tela, aperte para Voltar ao Menu Principal."
                falar(mensagemDeChegada)
            }
        }
    }

    private fun falar(texto: String) {
        if (::tts.isInitialized) {
            tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "")
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}