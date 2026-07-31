package com.fetin.innav.haptic

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.fetin.innav.models.No
import java.util.Locale

/**
 * Gerenciador do modo "Exploração Livre":
 * Varre continuamente os beacons/ESP32 detectados, escuta a orientação do dispositivo,
 * aplica a lógica tátil "Quente ou Frio" e anuncia por voz (TTS) o nó selecionado
 * com precisão de mira de ±8º.
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

    /**
     * Callback acionado quando um beacon entra (<= 8º) ou sai (> 8º) do foco direto de mira.
     */
    var onBeaconNaMiraChanged: ((BeaconAlvo?) -> Unit)? = null

    data class BeaconAlvo(
        val no: No,
        val rssi: Int,
        val distanciaEstimada: Double,
        val anguloAlvo: Float,
        val timestampMs: Long = System.currentTimeMillis()
    )

    data class TelemetriaMira(
        val azimuteCelular: Float,
        val anguloAlvo: Float,
        val diferencaErro: Float,
        val estaNaMira: Boolean,
        val idBeacon: String,
        val rssi: Int
    )

    var onTelemetriaUpdated: ((TelemetriaMira?) -> Unit)? = null

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
     */
    fun registrarBeaconDetectado(
        noAtualUsuario: No,
        noDetectado: No,
        rssi: Int
    ) {
        val distancia = CalculadoraAnguloNavegacao.estimarDistanciaMetros(rssi)
        val anguloAlvo = CalculadoraAnguloNavegacao.calcularAnguloAlvo(noAtualUsuario, noDetectado)
        val idUnico = noDetectado.deviceName ?: noDetectado.macAddress.ifBlank { noDetectado.id }

        // Mantém a calibração manual do ângulo se já tiver sido ajustado
        val anguloFinal = beaconsDetectados[idUnico]?.anguloAlvo ?: anguloAlvo

        beaconsDetectados[idUnico] = BeaconAlvo(
            no = noDetectado,
            rssi = rssi,
            distanciaEstimada = distancia,
            anguloAlvo = anguloFinal
        )
    }

    /**
     * Re-calibra a posição relativa do beacon mais próximo para coincidir exatamente com a direção atual do celular.
     */
    fun calibrarMiraDoBeacon(azimuteAtual: Float): String? {
        val entry = beaconsDetectados.entries.firstOrNull() ?: return null
        val idUnico = entry.key
        val beacon = entry.value

        // Ajusta as coordenadas virtuais para bater 1:1 com a direção que o celular está apontando
        val rad = Math.toRadians(azimuteAtual.toDouble())
        val novoNo = beacon.no.copy(
            x = Math.sin(rad) * 5.0,
            y = Math.cos(rad) * 5.0
        )
        val novoAngulo = CalculadoraAnguloNavegacao.calcularAnguloAlvo(0.0, 0.0, novoNo.x, novoNo.y)

        beaconsDetectados[idUnico] = beacon.copy(no = novoNo, anguloAlvo = novoAngulo)
        ultimoBeaconFocadoId = null
        return novoNo.deviceName ?: novoNo.nomeLocal
    }

    /**
     * Processa o azimute atual do dispositivo (0º a 360º).
     * Aplica o gradiente tátil "Quente ou Frio" e aciona pergunta TTS + foco de mira
     * quando a tolerância for <= 8º.
     *
     * @param azimuteAtual Azimute atual fornecido pelo OrientationManager.
     * @param toleranceDegrees Tolerância de mira exata (padrão: 8º).
     */
    fun processarOrientacao(azimuteAtual: Float, toleranceDegrees: Float = 8f) {
        val agora = System.currentTimeMillis()
        beaconsDetectados.entries.removeIf { agora - it.value.timestampMs > 8000 }

        if (beaconsDetectados.isEmpty()) {
            if (ultimoBeaconFocadoId != null) {
                ultimoBeaconFocadoId = null
                onBeaconNaMiraChanged?.invoke(null)
            }
            onTelemetriaUpdated?.invoke(null)
            hapticManager.stop()
            return
        }

        val beaconMaisProximo = beaconsDetectados.values.minByOrNull {
            HapticManager.calculateAngularDifference(azimuteAtual, it.anguloAlvo)
        }

        if (beaconMaisProximo != null) {
            val diferencaMinima = HapticManager.calculateAngularDifference(azimuteAtual, beaconMaisProximo.anguloAlvo)
            val estaNaMira = diferencaMinima <= toleranceDegrees

            // Telemetria ao vivo para exibição clara na tela
            val identificador = beaconMaisProximo.no.deviceName ?: beaconMaisProximo.no.nomeLocal
            onTelemetriaUpdated?.invoke(
                TelemetriaMira(
                    azimuteCelular = azimuteAtual,
                    anguloAlvo = beaconMaisProximo.anguloAlvo,
                    diferencaErro = diferencaMinima,
                    estaNaMira = estaNaMira,
                    idBeacon = identificador,
                    rssi = beaconMaisProximo.rssi
                )
            )

            // Aplica o gradiente tátil Quente ou Frio
            hapticManager.processarHapticQuenteFrio(diferencaMinima, toleranceDegrees)

            if (estaNaMira) {
                val idBeaconAtual = beaconMaisProximo.no.deviceName ?: beaconMaisProximo.no.macAddress.ifBlank { beaconMaisProximo.no.id }

                if (ultimoBeaconFocadoId != idBeaconAtual) {
                    ultimoBeaconFocadoId = idBeaconAtual

                    val nomePonto = beaconMaisProximo.no.deviceName ?: beaconMaisProximo.no.nomeLocal
                    falarPerguntaDestino(nomePonto)
                    onBeaconNaMiraChanged?.invoke(beaconMaisProximo)
                }
            } else {
                if (ultimoBeaconFocadoId != null) {
                    ultimoBeaconFocadoId = null
                    onBeaconNaMiraChanged?.invoke(null)
                }
            }
        } else {
            if (ultimoBeaconFocadoId != null) {
                ultimoBeaconFocadoId = null
                onBeaconNaMiraChanged?.invoke(null)
            }
            onTelemetriaUpdated?.invoke(null)
            hapticManager.stop()
        }
    }

    fun falarPerguntaDestino(nomePonto: String) {
        val frase = "Deseja definir $nomePonto como seu destino?"
        Log.d("INNAV_EXPLORACAO", "TTS Pergunta: $frase")
        if (ttsPronto) {
            tts?.speak(frase, TextToSpeech.QUEUE_FLUSH, null, "EXPLORACAO_PERGUNTA_TTS_ID")
        }
    }

    fun falarMensagem(mensagem: String) {
        Log.d("INNAV_EXPLORACAO", "TTS Mensagem: $mensagem")
        if (ttsPronto) {
            tts?.speak(mensagem, TextToSpeech.QUEUE_FLUSH, null, "EXPLORACAO_MSG_TTS_ID")
        }
    }

    fun stop() {
        tts?.stop()
        tts?.shutdown()
        ttsPronto = false
        beaconsDetectados.clear()
        ultimoBeaconFocadoId = null
    }
}
