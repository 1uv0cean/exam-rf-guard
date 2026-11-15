package com.classroom.rfdetector.core

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/** 회전벡터(방위) + 걸음 검출. 엔진에는 '상대 이동량'만 전달한다 */
class MotionSensor(private val context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotVec = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val step = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    /** 자북 기준 방위(도, 0=북, 시계방향) */
    @Volatile
    var headingDeg: Double = 0.0
        private set

    var stepLengthM: Double = 0.65

    var onStep: (() -> Unit)? = null
    var onHeadingDelta: ((Double) -> Unit)? = null

    private var lastHeading = 0.0
    private var headingInitialized = false
    private val rm = FloatArray(9)
    private val ori = FloatArray(3)

    fun start() {
        headingInitialized = false
        rotVec?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        if (ContextCompat.checkSelfPermission(context, "android.permission.ACTIVITY_RECOGNITION") == PackageManager.PERMISSION_GRANTED) {
            step?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    fun stop() {
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rm, e.values)
                SensorManager.getOrientation(rm, ori)
                var az = Math.toDegrees(ori[0].toDouble())
                if (az < 0) az += 360.0
                headingDeg = az
                if (!headingInitialized) {
                    lastHeading = az
                    headingInitialized = true
                    return
                }
                var d = az - lastHeading
                if (d > 180) d -= 360.0
                if (d < -180) d += 360.0
                if (abs(d) >= 2.0) {
                    lastHeading = az
                    onHeadingDelta?.invoke(d)
                }
            }
            Sensor.TYPE_STEP_DETECTOR -> onStep?.invoke()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
