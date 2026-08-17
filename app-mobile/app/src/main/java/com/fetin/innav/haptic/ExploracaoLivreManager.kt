package com.fetin.innav.haptic

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.fetin.innav.filtering.BeaconPosicionamento
import com.fetin.innav.filtering.GerenciadorFiltroKalman
import com.fetin.innav.filtering.TrilateracaoWCL
import com.fetin.innav.models.No
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Gerenciador do modo "Exploração Livre":
 * Varre continuamente os beacons/ESP32 detectados, aplica filtragem de sinal por Filtro de Kalman no RSSI,
 * estima a posição 2D do usuário via Trilateração Ponderada (WCL), escuta a orientação do dispositivo,
 * aplica a lógica tátil "Quente ou Frio" e anuncia por voz (TTS) o nó selecionado com precisão de mira de ±8º.
 *
 * Utiliza ConcurrentHashMap para garantir segurança concorrente entre as threads do BLE e dos Sensores.
 */
class ExploracaoLivreManager(
    context: Context,
    private val hapticManager: HapticManager
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ttsPronto = false

    // Gerenciador de Filtros de Kalman para suavização do sinal RSSI de cada ESP32
    private val gerenciadorKalman = GerenciadorFiltroKalman()

    // Registra os beacons detectados recentemente em um mapa thread-safe
    private val beaconsDetectados = ConcurrentHashMap<String, BeaconAlvo>()

    // Posição 2D (X, Y) estimada do usuário via Trilateração Ponderada (WCL)
    var posicaoUsuarioEstimada: Pair<Double, Double>? = null
        private set

    // Trava de Debounce: ID do último beacon anunciado para evitar repetições contínuas
    private var ultimoBeaconFocadoId: String? = null

    /**
     * Callback acionado quando um beacon entra (<= 8º) ou sai (> 8º) do foco direto de mira.
     */
    var onBeaconNaMiraChanged: ((BeaconAlvo?) -> Unit)? = null

    data class BeaconAlvo(
        val no: No,
        val rssiBruto: Int,
        val rssiFiltrado: Double,
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
        val rssiBruto: Int,
        val rssiFiltrado: Double,
        val posicaoUsuario: Pair<Double, Double>? = null
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
     * Aplica o Filtro de Kalman no RSSI e recalcula a posição 2D do usuário por Trilateração Ponderada (WCL).
     */
    fun registrarBeaconDetectado(
        noAtualUsuario: No,
        noDetectado: No,
        rssiBruto: Int
    ) {
        val idUnico = noDetectado.deviceName ?: noDetectado.macAddress.ifBlank { noDetectado.id }

        // 1. Filtragem do RSSI recebido via Filtro de Kalman
        val rssiFiltrado = gerenciadorKalman.filtrarRssi(idUnico, rssiBruto)

        // 2. Estimativa de distância em metros utilizando o modelo Log-Distance Path Loss
        val distancia = CalculadoraAnguloNavegacao.estimarDistanciaMetros(rssiFiltrado)

        // 3. Atualização temporária dos beacons ativos para cálculo da Trilateração Ponderada (WCL)
        val agora = System.currentTimeMillis()
        val beaconsAtivos = beaconsDetectados.values
            .filter { agora - it.timestampMs <= 8000 }
            .map { BeaconPosicionamento(it.no, it.rssiFiltrado, it.distanciaEstimada) }
            .toMutableList()

        // Adiciona/atualiza o beacon corrente no cálculo WCL
        beaconsAtivos.removeAll { (it.no.deviceName ?: it.no.macAddress.ifBlank { it.no.id }) == idUnico }
        beaconsAtivos.add(BeaconPosicionamento(noDetectado, rssiFiltrado, distancia))

        // 4. Recalcula a posição (X, Y) do usuário utilizando WCL com os 3 ESP32s mais próximos
        val novaPosicaoWcl = TrilateracaoWCL.calcularPosicaoUsuario(beaconsAtivos)
        if (novaPosicaoWcl != null) {
            posicaoUsuarioEstimada = TrilateracaoWCL.suavizarCoordenadas(novaPosicaoWcl, posicaoUsuarioEstimada)
        }

        // 5. Calcula o azimute alvo baseado na posição WCL estimada (ou no nó do usuário como fallback)
        val posBaseX = posicaoUsuarioEstimada?.first ?: noAtualUsuario.x
        val posBaseY = posicaoUsuarioEstimada?.second ?: noAtualUsuario.y

        val anguloAlvo = CalculadoraAnguloNavegacao.calcularAnguloAlvo(posBaseX, posBaseY, noDetectado.x, noDetectado.y)

        beaconsDetectados[idUnico] = BeaconAlvo(
            no = noDetectado,
            rssiBruto = rssiBruto,
            rssiFiltrado = rssiFiltrado,
            distanciaEstimada = distancia,
            anguloAlvo = anguloAlvo,
            timestampMs = agora
        )
    }

    /**
     * Re-calibra a posição relativa do beacon mais próximo para coincidir exatamente com a direção atual do celular.
     */
    fun calibrarMiraDoBeacon(azimuteAtual: Float): String? {
        val entry = beaconsDetectados.entries.firstOrNull() ?: return null
        val idUnico = entry.key
        val beacon = entry.value

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
     */
    /**
     * Processa o azimute atual do dispositivo (0º a 360º).
     * Aplica o gradiente tátil "Quente ou Frio" e aciona pergunta TTS + foco de mira
     * quando a tolerância for <= 8º.
     */
    fun processarOrientacao(azimuteAtual: Float, toleranceDegrees: Float = 8f) {
        val agora = System.currentTimeMillis()
        beaconsDetectados.entries.removeIf { agora - it.value.timestampMs > 8000 }

        // 🚨 A MÁGICA ACONTECE AQUI: A TRAVA DE DISTÂNCIA FÍSICA!
        // Filtramos a lista para o radar ignorar completamente qualquer ESP32 que esteja a mais de 1.5 metros.
        // Assim, ecos do Bluetooth que vêm do final do corredor não vão acionar o motor de vibração.
        val beaconsProximos = beaconsDetectados.values.filter { it.distanciaEstimada <= 1.5 }

        // Mudamos a verificação para olhar apenas para a nova lista filtrada
        if (beaconsProximos.isEmpty()) {
            if (ultimoBeaconFocadoId != null) {
                ultimoBeaconFocadoId = null
                onBeaconNaMiraChanged?.invoke(null)
            }
            onTelemetriaUpdated?.invoke(null)
            hapticManager.stop() // Garante que o celular fique em silêncio se não houver nada perto
            return
        }

        // O celular só vai procurar o alvo com a bússola entre as placas que já passaram no teste dos 1.5m
        val beaconMaisProximo = beaconsProximos.minByOrNull {
            HapticManager.calculateAngularDifference(azimuteAtual, it.anguloAlvo)
        }

        if (beaconMaisProximo != null) {
            val diferencaMinima = HapticManager.calculateAngularDifference(azimuteAtual, beaconMaisProximo.anguloAlvo)
            val estaNaMira = diferencaMinima <= toleranceDegrees

            val identificador = beaconMaisProximo.no.deviceName ?: beaconMaisProximo.no.nomeLocal
            onTelemetriaUpdated?.invoke(
                TelemetriaMira(
                    azimuteCelular = azimuteAtual,
                    anguloAlvo = beaconMaisProximo.anguloAlvo,
                    diferencaErro = diferencaMinima,
                    estaNaMira = estaNaMira,
                    idBeacon = identificador,
                    rssiBruto = beaconMaisProximo.rssiBruto,
                    rssiFiltrado = beaconMaisProximo.rssiFiltrado,
                    posicaoUsuario = posicaoUsuarioEstimada
                )
            )

            // Como só placas próximas passam no filtro, a vibração será cirúrgica
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
            tts?.stop()
            tts?.speak(frase, TextToSpeech.QUEUE_FLUSH, null, "EXPLORACAO_PERGUNTA_TTS_ID")
        }
    }

    fun falarMensagem(mensagem: String) {
        Log.d("INNAV_EXPLORACAO", "TTS Mensagem: $mensagem")
        if (ttsPronto) {
            tts?.stop()
            tts?.speak(mensagem, TextToSpeech.QUEUE_FLUSH, null, "EXPLORACAO_MSG_TTS_ID")
        }
    }

    fun stop() {
        tts?.stop()
        tts?.shutdown()
        ttsPronto = false
        beaconsDetectados.clear()
        gerenciadorKalman.limpar()
        posicaoUsuarioEstimada = null
        ultimoBeaconFocadoId = null
    }
}



