package com.fetin.innav

import com.fetin.innav.haptic.CalculadoraAnguloNavegacao
import com.fetin.innav.haptic.HapticManager
import com.fetin.innav.models.No
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticManagerTest {

    @Test
    fun testarCalculoDiferencaAngular() {
        // Testes da fórmula: (anguloAlvo - azimuthDispositivo + 540) % 360 - 180
        assertEquals(0f, CalculadoraAnguloNavegacao.calcularDiferencaAngularComSinal(90f, 90f), 0.01f)
        assertEquals(-10f, CalculadoraAnguloNavegacao.calcularDiferencaAngularComSinal(90f, 100f), 0.01f)
        assertEquals(10f, CalculadoraAnguloNavegacao.calcularDiferencaAngularComSinal(10f, 355f), 0.01f)
        assertEquals(-10f, CalculadoraAnguloNavegacao.calcularDiferencaAngularComSinal(355f, 5f), 0.01f)
        assertEquals(180f, Math.abs(CalculadoraAnguloNavegacao.calcularDiferencaAngularComSinal(0f, 180f)), 0.01f)

        // Distância angular absoluta em HapticManager
        assertEquals(0f, HapticManager.calculateAngularDifference(90f, 90f), 0.01f)
        assertEquals(10f, HapticManager.calculateAngularDifference(355f, 5f), 0.01f)
        assertEquals(10f, HapticManager.calculateAngularDifference(5f, 355f), 0.01f)
        assertEquals(180f, HapticManager.calculateAngularDifference(0f, 180f), 0.01f)
        assertEquals(90f, HapticManager.calculateAngularDifference(45f, 135f), 0.01f)
    }

    @Test
    fun testarIntensidadeProporcionalEConfirmacao() {
        // Dentro da tolerância (10º) -> Intensidade máxima (255)
        val intExact = HapticManager.calculateIntensityForDifference(5f, toleranceDegrees = 10f)
        assertEquals(255, intExact)

        val intToleranceLimit = HapticManager.calculateIntensityForDifference(10f, toleranceDegrees = 10f)
        assertEquals(255, intToleranceLimit)

        // Fora do alcance (>= 90º) -> Intensidade 0
        val intFar = HapticManager.calculateIntensityForDifference(90f, toleranceDegrees = 10f, maxAngleDifference = 90f)
        assertEquals(0, intFar)

        // Meio do caminho (~50º de diferença com tolerancia 10º e max 90º -> metade da escala)
        val intMid = HapticManager.calculateIntensityForDifference(50f, toleranceDegrees = 10f, maxAngleDifference = 90f)
        assertEquals(127, intMid)
    }

    @Test
    fun testarCalculoAnguloAlvoEDistancia() {
        val noOrigem = No("P1", "Portaria", x = 0.0, y = 0.0)
        val noLabNorte = No("P2", "Lab Norte", x = 0.0, y = 10.0)
        val noLabLeste = No("P3", "Lab Leste", x = 10.0, y = 0.0)

        // Ângulo para Norte (0º)
        assertEquals(0f, CalculadoraAnguloNavegacao.calcularAnguloAlvo(noOrigem, noLabNorte), 0.01f)

        // Ângulo para Leste (90º)
        assertEquals(90f, CalculadoraAnguloNavegacao.calcularAnguloAlvo(noOrigem, noLabLeste), 0.01f)

        // Distância estimada por RSSI (-59 dBm -> ~1.0m)
        val dist1m = CalculadoraAnguloNavegacao.estimarDistanciaMetros(-59, txPower = -59)
        assertEquals(1.0, dist1m, 0.01)
    }

    @Test
    fun testarCorrespondenciaPorNomeEMac() {
        val noTablet = No(
            id = "ESP_LAB",
            nomeLocal = "Laboratório",
            macAddress = "68:25:DD:48:1F:12",
            deviceName = "Tab S6 Lite de Jhonata"
        )

        // 1. Deve dar match por Nome do Dispositivo mesmo se o MAC for dinâmico/diferente
        assertTrue(noTablet.correspondeAoDispositivo("AA:BB:CC:DD:EE:FF", "Tab S6 Lite de Jhonata"))
        assertTrue(noTablet.correspondeAoDispositivo(null, "Tab S6 Lite de Jhonata"))

        // 2. Fallback: Deve dar match por MAC se o Nome for nulo/diferente
        assertTrue(noTablet.correspondeAoDispositivo("68:25:DD:48:1F:12", null))
        assertTrue(noTablet.correspondeAoDispositivo("68:25:DD:48:1F:12", "Nome Desconhecido"))

        // 3. Não deve dar match se ambos forem diferentes
        assertFalse(noTablet.correspondeAoDispositivo("11:22:33:44:55:66", "Outro Dispositivo"))
    }
}
