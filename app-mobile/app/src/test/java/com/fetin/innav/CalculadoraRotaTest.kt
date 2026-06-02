package com.fetin.innav

import com.fetin.innav.models.CalculadoraRota
import com.fetin.innav.models.Grafo
import com.fetin.innav.models.No
import org.junit.Test
import org.junit.Assert.*

class CalculadoraRotaTest {

    @Test
    fun testarCaminhoMaisCurto() {
        // 1. Instanciamos o nosso mapa vazio
        val mapaInatel = Grafo()

        // 2. Criamos 3 checkpoints virtuais
        val portaria = No("ESP_01", "Portaria Principal")
        val corredor = No("ESP_02", "Corredor Central")
        val labHardware = No("ESP_03", "Laboratório de Hardware")

        // 3. Desenhamos os corredores conectando os pontos
        mapaInatel.adicionarAresta(portaria, corredor, 10.0, "Siga 10 metros em frente pelo corredor principal.")
        mapaInatel.adicionarAresta(corredor, labHardware, 5.0, "Vire à direita e ande 5 metros para chegar ao laboratório.")

        // 4. Ligamos o motor matemático
        val gps = CalculadoraRota(mapaInatel)

        // 5. Calculamos a rota da Portaria até o Laboratório
        val rotaCalculada = gps.calcularCaminhoMaisCurto(portaria, labHardware)

        // 6. Imprimimos no console para você ver funcionando
        println("Calculando rota de: ${portaria.nomeLocal} -> ${labHardware.nomeLocal}")
        rotaCalculada.forEachIndexed { index, aresta ->
            println("Passo ${index + 1}: ${aresta.instrucao}")
        }

        // 7. A Prova Real (Asserção): O teste só passa se a rota tiver exatamente 2 passos
        assertEquals(2, rotaCalculada.size)
    }
}