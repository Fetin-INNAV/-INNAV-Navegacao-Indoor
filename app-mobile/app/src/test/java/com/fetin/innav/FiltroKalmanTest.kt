package com.fetin.innav

import org.junit.Assert.assertEquals
import org.junit.Test
import com.fetin.innav.filtering.FiltroKalmanRssi // 🚨 Importação correta da tua classe!

class FiltroKalmanTest {

    @Test
    fun `deve suavizar um pico de ruido extremo no sinal de bluetooth`() {
        val filtro = FiltroKalmanRssi() // Instancia a classe com o nome exato

        // Simular sinais estáveis de aproximação (-50 dBm)
        filtro.filtrar(-50.0)
        filtro.filtrar(-51.0)
        val sinalEstavel = filtro.filtrar(-50.0)

        // Injetar um ruído absurdo (alguém passou na frente do módulo ESP32)
        val sinalComRuido = filtro.filtrar(-95.0)

        // O filtro não deve deixar o valor afundar diretamente para -95
        assertEquals(-50.0, sinalEstavel, 2.0) // margem de tolerância de 2 dBm
        assert(sinalComRuido > -80.0) { "O filtro falhou e aceitou a queda abrupta de ruído" }
    }
}