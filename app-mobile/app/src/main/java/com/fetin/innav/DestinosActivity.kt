package com.fetin.innav

import android.content.Intent
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
            Toast.makeText(this, "Rota para o Laboratório selecionada!", Toast.LENGTH_LONG).show()

            // O celular fala a confirmação para o usuário
            falar("Rota para o laboratório selecionada. Iniciando navegação.")

            // AQUI ESTAVA O ERRO: Faltou o Intent para chamar a 3ª tela!
            val intent = Intent(this, NavegacaoActivity::class.java)
            startActivity(intent)

            // O comando finish() encerra esta tela para ela não ficar consumindo memória no fundo
            finish()
        }
    }

    // Função que transforma texto em áudio
    private fun falar(texto: String) {
        if (::tts.isInitialized) {
            tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "")
        }
    }

    // Configuração do idioma (Português do Brasil) e narração inicial
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Configura para Português do Brasil
            val resultado = tts.setLanguage(Locale("pt", "BR"))

            // Verifica se o celular suporta o idioma
            if (resultado == TextToSpeech.LANG_MISSING_DATA || resultado == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("INNAV_TTS", "Idioma não suportado pelo sistema.")
            } else {
                // AQUI ESTÁ A MÁGICA: Assim que a voz carregar, ele lê a tela!
                falar("Tela de destinos. Selecione o destino desejado. Opção disponível: Laboratório de Hardware.")
            }
        } else {
            Log.e("INNAV_TTS", "Erro ao carregar a voz na tela de destinos")
        }
    }

    // Desliga a voz ao sair da tela para poupar memória
    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}