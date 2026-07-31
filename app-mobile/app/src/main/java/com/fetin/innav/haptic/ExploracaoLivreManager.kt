package com.fetin.innav.haptic

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.fetin.innav.models.No
import java.util.Locale

/**
 * Gerenciador do modo "Exploração Livre":
 * Varre continuamente os beacons/ESP32 detectados, escuta a orientação do dispositivo,
 * dispara vibração tátil de confirmação e anúncio por voz (TTS) exclusivamente quando
 * o dispositivo estiver apontando diretamente para o nó (diferença de azimute <= 10º).
 */
class ExploracaoLivreManager(
    private val context: Context,
    private val hapticManager: HapticManager
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var ttsPronto = false

    // Registra os beacons detectados recentemente (ID -> Dados do Beacon)
    private val beaconsDetectados = mutableMapOf<String, BeaconAlvo>()

    // Trava de Debounce: ID do último beacon anunciado para evitar repetições contínuas
    private var ultimoBeaconFocadoId: String? = null

    data class BeaconAlvo(
        val no: No,
        val rssi: Int,
        val distanciaEstimada: Double,
        val anguloAlvo: Float,
        val timestampMs: Long = System.currentTimeMillis()
    )

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("pt", "BR"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("INNAV_EXPLORACAO", "Idioma PT-BR não suportado no TTS.")
            } else {
                ttsPronto = true
                Log.d("INNAV_EXPLORACAO", "TTS inicializado com sucesso para Exploração Livre.")
            }
        } else {
            Log.e("INNAV_EXPLORACAO", "Falha ao inicializar TextToSpeech.")
        }
    }

    /**
     * Atualiza ou adiciona um beacon detectado na varredura BLE.
     * Funciona tanto com o MAC físico do ESP32 (68:25:DD:48:1F:12) quanto por Nome do Dispositivo.
     */
    fun registrarBeaconDetectado(
        noAtualUsuario: No,
        noDetectado: No,
        rssi: Int
    ) {
        val distancia = CalculadoraAnguloNavegacao.estimarDistanciaMetros(rssi)
        val anguloAlvo = CalculadoraAnguloNavegacao.calcularAnguloAlvo(noAtualUsuario, noDetectado)
        val idUnico = noDetectado.deviceName ?: noDetectado.macAddress.ifBlank { noDetectado.id }

        beaconsDetectados[idUnico] = BeaconAlvo(
            no = noDetectado,
            rssi = rssi,
            distanciaEstimada = distancia,
            anguloAlvo = anguloAlvo
        )
    }

    /**
     * Processa o azimute atual do dispositivo (0º a 360º).
     * SÓ aciona fala (TTS) e pulso tátil quando o celular estiver EFETIVAMENTE apontando
     * para a direção do ESP32 (diferença de azimute <= 10º). Caso contrário, permanece em silêncio.
     *
     * @param azimuteAtual Azimute atual fornecido pelo OrientationManager.
     * @param toleranceDegrees Tolerância angular de mira (padrão: 10º).
     */
    fun processarOrientacao(azimuteAtual: Float, toleranceDegrees: Float = 10f) {
        // Limpa beacons antigos não vistos há mais de 8 segundos
        val agora = System.currentTimeMillis()
        beaconsDetectados.entries.removeIf { agora - it.value.timestampMs > 8000 }

        if (beaconsDetectados.isEmpty()) {
            ultimoBeaconFocadoId = null
            hapticManager.stop()
            return
        }

        // Procura se há algum beacon no campo de mira direto (diferença <= toleranceDegrees)
        var beaconNaMira: BeaconAlvo? = null
        var menorDiferenca = Float.MAX_VALUE

        for ((_, beacon) in beaconsDetectados) {
            val diff = HapticManager.calculateAngularDifference(azimuteAtual, beacon.anguloAlvo)
            if (diff <= toleranceDegrees && diff < menorDiferenca) {
                menorDiferenca = diff
                beaconNaMira = beacon
            }
        }

        if (beaconNaMira != null) {
            val idBeaconAtual = beaconNaMira.no.deviceName ?: beaconNaMira.no.macAddress.ifBlank { beaconNaMira.no.id }

            // TRAVA DE DEBOUNCE: Pergunta por TTS e pulso tátil SÓ são acionados se for um novo foco de mira
            if (ultimoBeaconFocadoId != idBeaconAtual) {
                ultimoBeaconFocadoId = idBeaconAtual

                // 1. Pulso tátil de confirmação
                hapticManager.vibrateConfirmation()

                // 2. Pergunta via TTS
                val nomePonto = beaconNaMira.no.deviceName ?: beaconNaMira.no.nomeLocal
                falarPerguntaDestino(nomePonto)
            }
        } else {
            // Se NÃO estiver apontando para nenhum ESP32 (diferença > 10º):
            // O app fica em silêncio total e reseta a trava para futuros alinhamentos
            ultimoBeaconFocadoId = null
            hapticManager.stop()
        }
    }

    /**
     * Fala a pergunta no formato: "Deseja definir [Nome] como seu destino?"
     */
    fun falarPerguntaDestino(nomePonto: String) {
        val frase = "Deseja definir $nomePonto como seu destino?"
        Log.d("INNAV_EXPLORACAO", "TTS Pergunta: $frase")
        if (ttsPronto) {
            tts?.speak(frase, TextToSpeech.QUEUE_FLUSH, null, "EXPLORACAO_PERGUNTA_TTS_ID")
        }
    }

    /**
     * Enuncia uma mensagem informativa avulsa (ex: abertura de tela).
     */
    fun falarMensagem(mensagem: String) {
        Log.d("INNAV_EXPLORACAO", "TTS Mensagem: $mensagem")
        if (ttsPronto) {
            tts?.speak(mensagem, TextToSpeech.QUEUE_FLUSH, null, "EXPLORACAO_MSG_TTS_ID")
        }
    }

    /**
     * Encerra recursos do TTS e limpa estados internos.
     */
    fun stop() {
        tts?.stop()
        tts?.shutdown()
        ttsPronto = false
        beaconsDetectados.clear()
        ultimoBeaconFocadoId = null
    }
}
