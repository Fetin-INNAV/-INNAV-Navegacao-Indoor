
package com.fetin.innav.models

class Grafo {

    val mapaAdjacencia: MutableMap<No, MutableList<Aresta>> = mutableMapOf()


    fun adicionarNo(no: No) {
        if (!mapaAdjacencia.containsKey(no)) {
            mapaAdjacencia.put(no, mutableListOf<Aresta>())
        }
    }


    fun adicionarAresta(origem: No, destino: No, distancia: Double, instrucao: String) {

        adicionarNo(origem)
        adicionarNo(destino)


        val novaAresta = Aresta(destino, distancia, instrucao)
        mapaAdjacencia[origem]?.add(novaAresta)


    }
}