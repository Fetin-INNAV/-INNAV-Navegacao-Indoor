package com.fetin.innav.filtering

import com.fetin.innav.models.No
import kotlin.math.max
import kotlin.math.pow

/**
 * Dados de um beacon para posicionamento por Trilateração Ponderada.
 */
data class BeaconPosicionamento(
    val no: No,
    val rssiFiltrado: Double,
    val distanciaEstimadaMetros: Double
)

/**
 * Algoritmo de Trilateração Ponderada (Weighted Centroid Localization - WCL)
 * combinado com Suavização Exponencial de Coordenadas (Low-Pass Filter).
 *
 * Utiliza os 3 ESP32s mais próximos (maior RSSI / menor distância) para determinar
 * a posição 2D (X, Y) do usuário no mapa, atribuindo pesos inversamente proporcionais
 * à distância $w_i = \frac{1}{d_i^g}$.
 */
object TrilateracaoWCL {

    /**
     * Calcula as coordenadas 2D (x, y) do usuário usando até 3 ESP32s mais próximos com maior força de sinal.
     *
     * @param beacons Lista de beacons detectados recentemente com RSSI filtrado pelo Kalman.
     * @param weightingFactor Expoente de ponderação $g$ (default 2.0).
     * @return Par de coordenadas (x, y) estimada. Retorna null se nenhum beacon for fornecido.
     */
    fun calcularPosicaoUsuario(
        beacons: List<BeaconPosicionamento>,
        weightingFactor: Double = 2.0
    ): Pair<Double, Double>? {
        if (beacons.isEmpty()) return null

        // Ordena por menor distância (ou maior RSSI filtrado) e seleciona até os 3 mais próximos
        val top3Beacons = beacons
            .sortedBy { it.distanciaEstimadaMetros }
            .take(3)

        if (top3Beacons.size == 1) {
            val b = top3Beacons.first()
            return Pair(b.no.x, b.no.y)
        }

        var somaPesos = 0.0
        var xPonderado = 0.0
        var yPonderado = 0.0

        for (beacon in top3Beacons) {
            // Evita divisão por zero ou distâncias extremamente pequenas
            val dEfetiva = max(beacon.distanciaEstimadaMetros, 0.1)
            val peso = 1.0 / dEfetiva.pow(weightingFactor)

            somaPesos += peso
            xPonderado += peso * beacon.no.x
            yPonderado += peso * beacon.no.y
        }

        if (somaPesos <= 0.0 || somaPesos.isNaN()) {
            val b = top3Beacons.first()
            return Pair(b.no.x, b.no.y)
        }

        val xEstimado = xPonderado / somaPesos
        val yEstimado = yPonderado / somaPesos

        return Pair(xEstimado, yEstimado)
    }

    /**
     * Aplica um filtro passa-baixas / suavização exponencial nas coordenadas (X, Y)
     * para evitar saltos repentinos na posição estimada do usuário.
     *
     * @param novaPosicao Posição (X, Y) calculada instantaneamente pelo WCL.
     * @param posicaoAnterior Posição (X, Y) anterior do usuário.
     * @param alpha Fator de suavização (0.0 a 1.0, quanto menor, mais suave). Default 0.35.
     */
    fun suavizarCoordenadas(
        novaPosicao: Pair<Double, Double>,
        posicaoAnterior: Pair<Double, Double>?,
        alpha: Double = 0.35
    ): Pair<Double, Double> {
        if (posicaoAnterior == null) return novaPosicao

        val xSuave = alpha * novaPosicao.first + (1.0 - alpha) * posicaoAnterior.first
        val ySuave = alpha * novaPosicao.second + (1.0 - alpha) * posicaoAnterior.second

        return Pair(xSuave, ySuave)
    }
}
