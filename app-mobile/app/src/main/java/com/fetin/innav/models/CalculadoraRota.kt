package com.fetin.innav.models

import java.util.PriorityQueue

class CalculadoraRota(private val grafo: Grafo) {

    /**
     * Retorna a lista de Arestas (instruções) que formam o caminho mais curto entre dois Nós.
     * Trata grafos vazios, nós inexistentes e rotas inalcançáveis sem lançar exceções de runtime.
     */
    fun calcularCaminhoMaisCurto(origem: No, destino: No): List<Aresta> {
        if (grafo.mapaAdjacencia.isEmpty()) return emptyList()
        if (origem == destino) return emptyList()

        // 1. Tabela de Distâncias: Guarda a menor distância conhecida até cada Nó
        val distancias = mutableMapOf<No, Double>()

        // 2. Tabela de Caminhos: Guarda de onde viemos para chegar a um Nó.
        val caminhos = mutableMapOf<No, Pair<No, Aresta>>()

        // Inicializa todas as distâncias do mapa como "Infinito"
        grafo.mapaAdjacencia.keys.forEach { no ->
            distancias[no] = Double.MAX_VALUE
        }
        distancias[origem] = 0.0

        // 3. Fila de Prioridade: Organiza automaticamente os Nós pelo menor custo
        val fila = PriorityQueue<Pair<No, Double>>(compareBy { it.second })
        fila.add(Pair(origem, 0.0))

        val visitados = mutableSetOf<No>()

        // 4. Execução do Algoritmo de Dijkstra
        while (fila.isNotEmpty()) {
            val (noAtual, distanciaAtual) = fila.poll() ?: break

            if (noAtual == destino) break

            if (!visitados.add(noAtual)) continue

            val vizinhos = grafo.mapaAdjacencia[noAtual] ?: emptyList()

            for (aresta in vizinhos) {
                val vizinho = aresta.destino
                if (vizinho in visitados) continue

                val novaDistancia = distanciaAtual + aresta.distanciaMetros

                if (novaDistancia < distancias.getOrDefault(vizinho, Double.MAX_VALUE)) {
                    distancias[vizinho] = novaDistancia
                    caminhos[vizinho] = Pair(noAtual, aresta)
                    fila.add(Pair(vizinho, novaDistancia))
                }
            }
        }

        // 5. Reconstrução da Rota (De trás para frente)
        val rotaFinal = mutableListOf<Aresta>()
        var noPasso = destino

        while (caminhos.containsKey(noPasso)) {
            val par = caminhos[noPasso] ?: break
            val (noAnterior, arestaUsada) = par
            rotaFinal.add(arestaUsada)
            noPasso = noAnterior
        }

        rotaFinal.reverse()
        return rotaFinal
    }
}