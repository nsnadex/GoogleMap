package nsnade.ssss.googlemap

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SensorManagerHelper(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _bearing = MutableStateFlow(0f)
    val bearing: StateFlow<Float> = _bearing.asStateFlow()

    private val _tilt = MutableStateFlow(0f)
    val tilt: StateFlow<Float> = _tilt.asStateFlow()

    private var gravityValues: FloatArray? = null
    private var geomagneticValues: FloatArray? = null

    private var lastBearing = 0f
    private var lastTilt = 0f

    fun startListening() {
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelerometerSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
            magnetometerSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        val rotationMatrix = FloatArray(9)

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            processOrientationWithDisplayRotation(rotationMatrix)
        } else {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                gravityValues = event.values.clone()
            } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                geomagneticValues = event.values.clone()
            }

            val gravity = gravityValues
            val geo = geomagneticValues
            if (gravity != null && geo != null) {
                val i = FloatArray(9)
                if (SensorManager.getRotationMatrix(rotationMatrix, i, gravity, geo)) {
                    processOrientationWithDisplayRotation(rotationMatrix)
                }
            }
        }
    }

    private fun processOrientationWithDisplayRotation(rotationMatrix: FloatArray) {
        val displayRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display?.rotation ?: Surface.ROTATION_0
            } catch (e: Exception) {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.rotation
            }
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }

        var axisX = SensorManager.AXIS_X
        var axisY = SensorManager.AXIS_Y

        when (displayRotation) {
            Surface.ROTATION_90 -> {
                axisX = SensorManager.AXIS_Y
                axisY = SensorManager.AXIS_MINUS_X
            }
            Surface.ROTATION_270 -> {
                axisX = SensorManager.AXIS_MINUS_Y
                axisY = SensorManager.AXIS_X
            }
            Surface.ROTATION_180 -> {
                axisX = SensorManager.AXIS_MINUS_X
                axisY = SensorManager.AXIS_MINUS_Y
            }
        }

        val remappedMatrix = FloatArray(9)
        SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)

        val orientation = FloatArray(3)
        SensorManager.getOrientation(remappedMatrix, orientation)

        // 方位角 (0 ~ 360度)
        val azimuthInDegrees = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360) % 360
        updateBearing(azimuthInDegrees)

        // ピッチ角 (上下の傾き)
        val pitchInDegrees = Math.toDegrees(orientation[1].toDouble()).toFloat()
        val rawTilt = pitchInDegrees + 90f
        val clampedTilt = rawTilt.coerceIn(-85f, 85f)
        updateTilt(clampedTilt)
    }

    private fun updateBearing(newBearing: Float) {
        val diff = Math.abs(newBearing - lastBearing)
        if (diff > 0.8f) {
            lastBearing = newBearing
            _bearing.value = newBearing
        }
    }

    private fun updateTilt(newTilt: Float) {
        val diff = Math.abs(newTilt - lastTilt)
        if (diff > 0.8f) {
            lastTilt = newTilt
            _tilt.value = newTilt
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
}
