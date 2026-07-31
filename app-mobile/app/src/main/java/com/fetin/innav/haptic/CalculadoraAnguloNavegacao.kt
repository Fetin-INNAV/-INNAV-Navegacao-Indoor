package com.fetin.innav.haptic

import com.fetin.innav.models.No

/**
 * Utilitário para cálculos de navegação indoor: estimativa de distância por RSSI
 * e determinação dinâmica do azimute/ângulo alvo em direção ao ESP32.
 */
object CalculadoraAnguloNavegacao {

    /**
     * Estima a distância em metros até o ESP32 / Beacon utilizando o modelo de atenuação de sinal (Log-Distance Path Loss Model).
     *
     * @param rssi Intensidade do sinal recebido em dBm.
     * @param txPower RSSI de referência medido a 1 metro (default -59 dBm).
     * @param pathLossExponent Coeficiente de atenuação do ambiente indoor (default 2.0).
     * @return Distância estimada em metros.
     */
    fun estimarDistanciaMetros(rssi: Int, txPower: Int = -59, pathLossExponent: Double = 2.0): Double {
        if (rssi == 0) return -1.0
        val ratio = (txPower - rssi) / (10.0 * pathLossExponent)
        return Math.pow(10.0, ratio)
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
     *
     * @param xAtual Coordenada X atual do usuário.
     * @param yAtual Coordenada Y atual do usuário.
     * @param xAlvo Coordenada X do ESP32/Nó de destino.
     * @param yAlvo Coordenada Y do ESP32/Nó de destino.
     * @return Ângulo alvo em graus normalizado entre [0, 360).
     */
    fun calcularAnguloAlvo(xAtual: Double, yAtual: Double, xAlvo: Double, yAlvo: Double): Float {
        val dx = xAlvo - xAtual
        val dy = yAlvo - yAtual
        val radianos = Math.atan2(dx, dy)
        val graus = Math.toDegrees(radianos).toFloat()
        return (graus + 360f) % 360f
    }

    /**
     * Sobrecarga que aceita instâncias do modelo No.
     */
    fun calcularAnguloAlvo(noAtual: No, noDestino: No): Float {
        return calcularAnguloAlvo(noAtual.x, noAtual.y, noDestino.x, noDestino.y)
    }
}
