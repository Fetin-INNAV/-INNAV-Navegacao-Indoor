package com.fetin.innav.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.abs

/**
 * Gerenciador de haptic feedback (vibração) para o aplicativo.
 * Permite ajustar amplitude (0-255), emitir pulsos curtos, gradiente quente/frio e vibração de confirmação.
 */
class HapticManager(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var isInToleranceZone = false

    /**
     * Emite um pulso curto de vibração com duração e amplitude/intensidade customizadas.
     *
     * @param durationMs Duração da vibração em milissegundos.
     * @param amplitude Intensidade da vibração no intervalo de 0 a 255.
     */
    fun vibratePulse(durationMs: Long = 100L, amplitude: Int) {
        val safeAmplitude = amplitude.coerceIn(0, 255)
        if (safeAmplitude <= 0) return

        vibrator?.let { vib ->
            if (!vib.hasVibrator()) return@let

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (vib.hasAmplitudeControl()) {
                    VibrationEffect.createOneShot(durationMs, safeAmplitude)
                } else {
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        }
    }

    /**
     * Dispara uma vibração de confirmação quando o usuário atinge o ângulo correto.
     */
    fun vibrateConfirmation() {
        vibrator?.let { vib ->
            if (!vib.hasVibrator()) return@let

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 100, 50, 200)
                val amplitudes = intArrayOf(0, 255, 0, 255)
                val effect = if (vib.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                } else {
                    VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(350)
            }
        }
    }

    /**
     * Aplica o gradiente tátil "Quente ou Frio" baseado no desvio angular em graus:
     * - Desvio > 45º: Silêncio (Frio).
     * - Desvio 20º a 45º: Pulsos leves e espaçados (Morno).
     * - Desvio 8º a 20º: Pulsos rápidos/frequentes (Quente).
     * - Desvio <= 8º: Vibração de confirmação / alinhamento exato (Muito Quente / Foco).
     */
    fun processarHapticQuenteFrio(diferencaAngular: Float, toleranceDegrees: Float = 8f) {
        when {
            diferencaAngular <= toleranceDegrees -> {
                if (!isInToleranceZone) {
                    isInToleranceZone = true
                    vibrateConfirmation()
                }
            }
            diferencaAngular <= 20f -> {
                isInToleranceZone = false
                vibratePulse(durationMs = 60L, amplitude = 220)
            }
            diferencaAngular <= 45f -> {
                isInToleranceZone = false
                vibratePulse(durationMs = 40L, amplitude = 110)
            }
            else -> {
                isInToleranceZone = false
                stop()
            }
        }
    }

    /**
     * Compara o azimute atual com o ângulo alvo, ajustando a intensidade da vibração:
     * Quanto mais próximo do alvo, maior a intensidade (0 a 255).
     * Quando estiver no ângulo exato (dentro da tolerância de 8º), dispara uma vibração de confirmação.
     */
    fun adjustVibrationByAzimuth(
        currentAzimuth: Float,
        targetAngle: Float,
        toleranceDegrees: Float = 8f,
        maxAngleDifference: Float = 45f
    ) {
        val diff = calculateAngularDifference(currentAzimuth, targetAngle)
        processarHapticQuenteFrio(diff, toleranceDegrees)
    }

    /**
     * Interrompe qualquer vibração ativa e reseta o estado de tolerância.
     */
    fun stop() {
        vibrator?.cancel()
        isInToleranceZone = false
    }

    companion object {
        /**
         * Comparação angular: calcula a menor distância angular em graus entre dois azimutes.
         * Garante a normalização estrita no intervalo [-180, 180] graus usando a fórmula:
         * diferenca = (targetAngle - currentAzimuth + 540) % 360 - 180
         * Retorna a distância angular em módulo [0, 180].
         */
        fun calculateAngularDifference(currentAzimuth: Float, targetAngle: Float): Float {
            val diferencaComSinal = ((targetAngle - currentAzimuth + 540f) % 360f) - 180f
            return abs(diferencaComSinal)
        }

        /**
         * Ajusta/calcula a intensidade de vibração (0 a 255) baseada na distância do alvo.
         */
        fun calculateIntensityForDifference(
            angularDifference: Float,
            toleranceDegrees: Float = 8f,
            maxAngleDifference: Float = 45f
        ): Int {
            if (angularDifference <= toleranceDegrees) {
                return 255
            }
            if (angularDifference >= maxAngleDifference) {
                return 0
            }
            val range = maxAngleDifference - toleranceDegrees
            val relativeDiff = maxAngleDifference - angularDifference
            val normalized = (relativeDiff / range).coerceIn(0f, 1f)
            return (normalized * 255).toInt()
        }
    }
}
