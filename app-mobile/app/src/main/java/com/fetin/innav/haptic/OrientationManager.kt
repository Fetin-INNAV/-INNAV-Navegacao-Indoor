package com.fetin.innav.haptic

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build

/**
 * Classe responsável por ler a orientação do dispositivo através de Sensor.TYPE_ROTATION_VECTOR
 * e retornar o azimute em graus (0º a 360º) alinhado com a borda superior (topo) do smartphone.
 *
 * Suporta persistência por SharedPreferences para que cada aparelho (ex: Samsung S20 FE)
 * salve sua calibração individual de 180º.
 */
class OrientationManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationVectorSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val prefs = context.getSharedPreferences("innav_sensor_prefs", Context.MODE_PRIVATE)

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var isListening = false

    /**
     * Flag de inversão de azimute (180º).
     * Salva automaticamente no SharedPreferences do celular.
     */
    var inverterAzimute: Boolean
        get() = prefs.getBoolean("inverter_azimute", checarInversaoPadraoPorHardware())
        set(value) = prefs.edit().putBoolean("inverter_azimute", value).apply()

    /**
     * Flag para utilizar o remapeamento de coordenadas do SensorManager (Eixo Y no topo do aparelho).
     */
    var usarRemapeamentoCoordenadas: Boolean = false

    /**
     * Listener para receber o azimute atualizado em graus [0, 360).
     */
    var onAzimuthChanged: ((azimuthDegrees: Float) -> Unit)? = null

    /**
     * Detecção automática para modelos conhecidos com inversão no fusor de sensores (ex: Samsung S20 FE).
     */
    private fun checarInversaoPadraoPorHardware(): Boolean {
        val model = Build.MODEL ?: ""
        val manufacturer = Build.MANUFACTURER ?: ""
        return (manufacturer.contains("samsung", ignoreCase = true) &&
                (model.contains("G780", ignoreCase = true) ||
                 model.contains("G781", ignoreCase = true) ||
                 model.contains("S20", ignoreCase = true)))
    }

    /**
     * Alterna a inversão de 180º em tempo real e salva no dispositivo.
     */
    fun alternarInversao(): Boolean {
        val novoEstado = !inverterAzimute
        inverterAzimute = novoEstado
        return novoEstado
    }

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

        // Extrai a matriz de rotação a partir do vetor de rotação
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // Se ativado, remapa o sistema de coordenadas para garantir que o Eixo Y aponte para o topo do celular
        val matrixFinal = if (usarRemapeamentoCoordenadas) {
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                remappedMatrix
            )
            remappedMatrix
        } else {
            rotationMatrix
        }

        // Calcula a orientação [azimute, pitch, roll] em radianos
        SensorManager.getOrientation(matrixFinal, orientationAngles)

        // Converte o azimute em radianos para graus e normaliza no intervalo 0º..360º
        var azimuteEmGraus = (Math.toDegrees(orientationAngles[0].toDouble()).toFloat() + 360f) % 360f

        // Aplica a inversão de 180º se ativada
        if (inverterAzimute) {
            azimuteEmGraus = (azimuteEmGraus + 180f) % 360f
        }

        onAzimuthChanged?.invoke(azimuteEmGraus)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Não é necessário tratar para este uso
    }
}
