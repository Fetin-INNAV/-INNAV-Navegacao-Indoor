package com.fetin.innav

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

// Adicionamos o TextToSpeech.OnInitListener na assinatura da classe
class DestinosActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // Variável do motor de voz
    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_destinos)

        // Liga o motor de voz assim que a tela abre
        tts = TextToSpeech(this, this)

        val btnDestinoLab = findViewById<Button>(R.id.btnDestinoLab)

        btnDestinoLab.setOnClickListener {
            // O seu Toast original
            Toast.makeText(this, "Rota para o Laboratório selecionada!", Toast.LENGTH_LONG).show()

            // O telemóvel fala a confirmação para o utilizador
            falar("Rota para o laboratório selecionada. Iniciando navegação.")

            // O comando finish() encerra esta tela e devolve o utilizador para a MainActivity
            finish()
        }
    }

    // Função que transforma texto em áudio
    private fun falar(texto: String) {
        if (::tts.isInitialized) {
            tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "")
        }
    }

    // Configuração do idioma (Português do Brasil)
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("pt", "BR")
        } else {
            Log.e("INNAV_TTS", "Erro ao carregar a voz na tela de destinos")
        }
    }

    // Desliga a voz ao sair da tela para poupar memória do telemóvel
    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}