package com.fetin.innav.models

import java.util.PriorityQueue

class CalculadoraRota(private val grafo: Grafo) {

    // Retorna a lista de Arestas (instruções) que formam o caminho mais curto
    fun calcularCaminhoMaisCurto(origem: No, destino: No): List<Aresta> {

        // 1. Tabela de Distâncias: Guarda a menor distância conhecida até cada Nó
        val distancias = mutableMapOf<No, Double>()

        // 2. Tabela de Caminhos: Guarda de onde viemos para chegar a um Nó.
        // Armazena um Pair(Nó Anterior, Aresta Usada) para reconstruirmos a rota depois.
        val caminhos = mutableMapOf<No, Pair<No, Aresta>>()

        // Inicializa todas as distâncias do mapa como "Infinito"
        grafo.mapaAdjacencia.keys.forEach { no ->
            distancias[no] = Double.MAX_VALUE
        }
        distancias[origem] = 0.0 // A distância para o ponto onde estamos é zero

        // 3. Fila de Prioridade: O coração do Dijkstra.
        // Ela organiza automaticamente os Nós, sempre deixando o mais próximo no topo.
        val fila = PriorityQueue<Pair<No, Double>>(compareBy { it.second })
        fila.add(Pair(origem, 0.0))

        val visitados = mutableSetOf<No>()

        // 4. Execução da Busca
        while (fila.isNotEmpty()) {
            val (noAtual, distanciaAtual) = fila.poll() ?: break

            // Se o nó retirado da fila for o destino final, a busca matemática acabou!
            if (noAtual == destino) break

            // Ignora se já analisamos esse ponto com um caminho mais eficiente antes
            if (!visitados.add(noAtual)) continue

            // Olha para todos os corredores que saem do ponto atual
            val vizinhos = grafo.mapaAdjacencia[noAtual] ?: emptyList()

            for (aresta in vizinhos) {
                val vizinho = aresta.destino
                if (vizinho in visitados) continue

                // Calcula a distância total se formos por este caminho
                val novaDistancia = distanciaAtual + aresta.distanciaMetros

                // Se encontramos um atalho (distância menor do que a registrada), atualizamos a tabela
                if (novaDistancia < distancias.getOrDefault(vizinho, Double.MAX_VALUE)) {
                    distancias[vizinho] = novaDistancia
                    caminhos[vizinho] = Pair(noAtual, aresta) // Registra que viemos daqui
                    fila.add(Pair(vizinho, novaDistancia))
                }
            }
        }

        // 5. Reconstrução da Rota (De trás para frente)
        val rotaFinal = mutableListOf<Aresta>()
        var noPasso = destino

        // Vai voltando do destino até a origem, guardando as arestas usadas
        while (caminhos.containsKey(noPasso)) {
            val (noAnterior, arestaUsada) = caminhos[noPasso]!!
            rotaFinal.add(arestaUsada)
            noPasso = noAnterior // Dá um passo para trás
        }

        // Como lemos de trás para frente, precisamos inverter a lista para o aplicativo falar na ordem certa
        rotaFinal.reverse()

        return rotaFinal
    }
}