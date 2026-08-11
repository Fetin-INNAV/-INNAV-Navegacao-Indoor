package com.fetin.innav.models

/**
 * Representa um Checkpoint / Nó de navegação indoor com suporte a identificação BLE e coordenadas 2D.
 */
data class No(
    val id: String,
    val nomeLocal: String,
    val macAddress: String = "",
    val deviceName: String? = null,
    val x: Double = 0.0,
    val y: Double = 0.0
) {
    /**
     * Verifica se o dispositivo BLE escaneado corresponde a este Nó.
     * Prioriza a correspondência por Nome do Dispositivo (deviceName) e faz fallback para o Endereço MAC.
     */
    fun correspondeAoDispositivo(macScaneado: String?, nomeScaneado: String?): Boolean {
        // 1. Prioridade: Se o nó tem deviceName e o scanner detectou um nome válido
        if (!deviceName.isNullOrBlank() && !nomeScaneado.isNullOrBlank()) {
            if (nomeScaneado.equals(deviceName, ignoreCase = true) || nomeScaneado.contains(deviceName, ignoreCase = true)) {
                return true
            }
        }

        // 2. Fallback: Se o nó tem macAddress e o scanner detectou o MAC correspondente
        if (macAddress.isNotBlank() && !macScaneado.isNullOrBlank()) {
            if (macScaneado.equals(macAddress, ignoreCase = true)) {
                return true
            }
        }

        return false
    }
}