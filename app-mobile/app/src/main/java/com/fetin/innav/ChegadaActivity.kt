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

        tts = TextToSpeech(this, this)

        val btnNovoDestino = findViewById<Button>(R.id.btnNovoDestino)
        val btnMenuPrincipal = findViewById<Button>(R.id.btnMenuPrincipal)

        configurarBotaoComDuploToque(
            button = btnNovoDestino,
            nomeBotao = "Escolher Novo Destino",
            falaConfirmacao = "Iniciando Escolha de Novo Destino",
            acaoDuploClique = {
                val intent = Intent(this, DestinosActivity::class.java)
                startActivity(intent)
                finish()
            }
        )

        configurarBotaoComDuploToque(
            button = btnMenuPrincipal,
            nomeBotao = "Voltar ao Menu Principal",
            falaConfirmacao = "Voltando ao Menu Principal",
            acaoDuploClique = {
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
        )
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
                button.postDelayed({
                    if (::tts.isInitialized) tts.stop()
                    acaoDuploClique()
                }, 350)
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

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val resultado = tts.setLanguage(Locale("pt", "BR"))

            if (resultado == TextToSpeech.LANG_MISSING_DATA || resultado == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("INNAV_TTS", "Idioma PT-BR não suportado.")
            } else {
                val mensagemDeChegada = "Você chegou ao destino. Navegação encerrada."
                falar(mensagemDeChegada)
            }
        }
    }

    private fun falar(texto: String) {
        if (::tts.isInitialized) {
            tts.stop()
            tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "CHEGADA_TTS_ID")
        }
    }

    override fun onPause() {
        super.onPause()
        if (::tts.isInitialized) {
            tts.stop()
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