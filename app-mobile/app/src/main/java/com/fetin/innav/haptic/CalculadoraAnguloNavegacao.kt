package com.fetin.innav.haptic

import com.fetin.innav.models.No
import kotlin.math.abs

/**
 * Utilitário para cálculos de navegação indoor: estimativa de distância por RSSI,
 * azimute alvo dinâmico e diferença angular normalizada no intervalo [-180º, 180º].
 *
 * Inclui tratamento estrito contra divisão por zero, valores NaN e de atenuação.
 */
object CalculadoraAnguloNavegacao {

    /**
     * Estima a distância em metros até o ESP32 / Beacon utilizando o modelo de atenuação de sinal (Log-Distance Path Loss Model).
     *
     * @param rssi Intensidade do sinal recebido em dBm (bruto ou filtrado pelo Kalman).
     * @param txPower RSSI de referência medido a 1 metro (default -59 dBm).
     * @param pathLossExponent Coeficiente de atenuação do ambiente indoor (default 2.0).
     * @return Distância estimada em metros (-1.0 em caso de valores inválidos).
     */
    fun estimarDistanciaMetros(rssi: Double, txPower: Int = -59, pathLossExponent: Double = 2.0): Double {
        if (rssi == 0.0 || rssi.isNaN()) return -1.0
        val exponenteSeguro = if (pathLossExponent <= 0.0 || pathLossExponent.isNaN()) 2.0 else pathLossExponent
        val ratio = (txPower - rssi) / (10.0 * exponenteSeguro)
        val resultado = Math.pow(10.0, ratio)
        return if (resultado.isNaN() || resultado.isInfinite()) -1.0 else resultado
    }

    fun estimarDistanciaMetros(rssi: Int, txPower: Int = -59, pathLossExponent: Double = 2.0): Double {
        return estimarDistanciaMetros(rssi.toDouble(), txPower, pathLossExponent)
    }

    /**
     * Calcula o azimute/ângulo alvo em graus (0º a 360º) a partir da posição atual do usuário (x1, y1)
     * em direção à localização do ESP32 / Nó de destino (x2, y2).
     *
     * Convenção de Bússola/Navegação:
     * 0º = Norte (+Y)
     * 90º = Leste (+X)
     * 180º = Sul (-Y)
     * 270º = Oeste (-X)
     */
    fun calcularAnguloAlvo(xAtual: Double, yAtual: Double, xAlvo: Double, yAlvo: Double): Float {
        if (xAtual.isNaN() || yAtual.isNaN() || xAlvo.isNaN() || yAlvo.isNaN()) return 0f
        val dx = xAlvo - xAtual
        val dy = yAlvo - yAtual

        if (dx == 0.0 && dy == 0.0) return 0f

        val radianos = Math.atan2(dx, dy)
        val graus = Math.toDegrees(radianos).toFloat()
        val anguloNormalizado = (graus + 360f) % 360f

        return if (anguloNormalizado.isNaN()) 0f else anguloNormalizado
    }

    /**
     * Sobrecarga que aceita instâncias do modelo No.
     */
    fun calcularAnguloAlvo(noAtual: No, noDestino: No): Float {
        return calcularAnguloAlvo(noAtual.x, noAtual.y, noDestino.x, noDestino.y)
    }

    /**
     * Calcula a diferença angular com sinal entre o azimute do alvo e o azimute do dispositivo.
     * Normaliza estritamente no intervalo [-180, 180] graus usando a fórmula:
     * diferenca = (anguloAlvo - azimuthDispositivo + 540) % 360 - 180
     */
    fun calcularDiferencaAngularComSinal(anguloAlvo: Float, azimuthDispositivo: Float): Float {
        if (anguloAlvo.isNaN() || azimuthDispositivo.isNaN()) return 0f
        val diferenca = ((anguloAlvo - azimuthDispositivo + 540f) % 360f) - 180f
        return if (diferenca.isNaN()) 0f else diferenca
    }

    /**
     * Retorna a menor distância angular absoluta entre o azimute do dispositivo e o azimute do alvo [0º, 180º].
     */
    fun calcularDistanciaAngularAbsoluta(anguloAlvo: Float, azimuthDispositivo: Float): Float {
        return abs(calcularDiferencaAngularComSinal(anguloAlvo, azimuthDispositivo))
    }
}
