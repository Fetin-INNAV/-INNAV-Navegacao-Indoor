package com.fetin.innav.haptic

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Classe responsável por ler a orientação do dispositivo através de Sensor.TYPE_ROTATION_VECTOR
 * e retornar o azimute em graus (0º a 360º).
 */
class OrientationManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationVectorSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var isListening = false

    /**
     * Listener para receber o azimute atualizado em graus [0, 360).
     */
    var onAzimuthChanged: ((azimuthDegrees: Float) -> Unit)? = null

    /**
     * Verifica se o sensor TYPE_ROTATION_VECTOR está presente no hardware.
     */
    fun isSensorAvailable(): Boolean {
        return rotationVectorSensor != null
    }

    /**
     * Inicia a escuta dos sensores de rotação.
     *
     * @param samplingPeriodUs Frequência de atualização (default: SensorManager.SENSOR_DELAY_UI).
     */
    fun startListening(samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_UI) {
        if (!isListening && rotationVectorSensor != null) {
            sensorManager?.registerListener(this, rotationVectorSensor, samplingPeriodUs)
            isListening = true
        }
    }

    /**
     * Para a escuta dos sensores de rotação para economizar bateria e recursos.
     */
    fun stopListening() {
        if (isListening) {
            sensorManager?.unregisterListener(this)
            isListening = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        // Extrai a matriz de rotação a partir dos valores do vetor de rotação
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // Calcula a orientação (azimute, pitch, roll) em radianos
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // Converte o azimute (orientationAngles[0]) em radianos para graus e normaliza entre 0º e 360º
        val azimuteEmGraus = (Math.toDegrees(orientationAngles[0].toDouble()).toFloat() + 360f) % 360f

        onAzimuthChanged?.invoke(azimuteEmGraus)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Não é necessário tratar para este uso
    }
}
