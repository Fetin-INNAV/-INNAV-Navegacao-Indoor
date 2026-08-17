package com.fetin.innav.filtering

import java.util.concurrent.ConcurrentHashMap

/**
 * Filtro de Kalman 1D otimizado para filtragem de ruído de sinal RSSI em redes Bluetooth Low Energy (BLE).
 *
 * Suprime variações bruscas, interferências de múltiplos caminhos (multipath) e ruídos de amostragem,
 * garantindo uma leitura de RSSI estável para o modelo de perda de percurso (Log-Distance Path Loss).
 */
class FiltroKalmanRssi(
    // Reduzimos o Q (Assume que você anda devagar, logo o sinal não deve pular do nada)
    private val processNoiseQ: Double = 0.02,

    // Aumentamos muito o R (Assume que o rádio do ESP32 tem bastante ruído de eco)
    private val measurementNoiseR: Double = 15.0,

    private var estimatedCovarianceP: Double = 1.0,
    private var estimatedValueX: Double = -70.0
){
    private var isInitialized = false

    /**
     * Aplica uma nova amostragem de RSSI bruto e retorna a estimativa suavizada pelo Filtro de Kalman.
     */
    fun filtrar(rssiMedido: Double): Double {
        if (rssiMedido == 0.0 || rssiMedido.isNaN()) return estimatedValueX

        if (!isInitialized) {
            estimatedValueX = rssiMedido
            isInitialized = true
            return estimatedValueX
        }

        // 1. Etapa de Predição (Prediction step)
        estimatedCovarianceP += processNoiseQ

        // 2. Etapa de Atualização (Measurement Update step / Kalman Gain)
        val kalmanGain = estimatedCovarianceP / (estimatedCovarianceP + measurementNoiseR)
        estimatedValueX += kalmanGain * (rssiMedido - estimatedValueX)
        estimatedCovarianceP *= (1.0 - kalmanGain)

        return estimatedValueX
    }

    /**
     * Reinicia o estado do filtro.
     */
    fun reset() {
        isInitialized = false
        estimatedCovarianceP = 1.0
        estimatedValueX = -70.0
    }
}

/**
 * Gerenciador thread-safe de Filtros de Kalman por Beacon ID.
 * Mantém um filtro individualizado para cada ESP32 cadastrado.
 */
class GerenciadorFiltroKalman {
    private val filtros = ConcurrentHashMap<String, FiltroKalmanRssi>()

    /**
     * Processa a leitura bruta de RSSI de um beacon e retorna o valor filtrado pelo Filtro de Kalman.
     */
    fun filtrarRssi(idBeacon: String, rssiBruto: Int): Double {
        val idLimpo = idBeacon.ifBlank { "UNKNOWN" }
        val filtro = filtros.computeIfAbsent(idLimpo) { FiltroKalmanRssi() }
        return filtro.filtrar(rssiBruto.toDouble())
    }

    fun limpar() {
        filtros.clear()
    }
}
