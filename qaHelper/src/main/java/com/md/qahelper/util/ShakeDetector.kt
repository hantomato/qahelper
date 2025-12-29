package com.md.qahelper.util

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 *
 * Created on 2025. 12. 29..
 */
internal class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {
    private var lastShakeTime: Long = 0
    private val SHAKE_THRESHOLD = 2.7F // 감도 (2.5 ~ 3.0 추천)
//    private val SHAKE_THRESHOLD = 1.7F // for test
    private val SHAKE_SLOP_TIME_MS = 500 // 0.5초 딜레이

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0] / SensorManager.GRAVITY_EARTH
            val y = event.values[1] / SensorManager.GRAVITY_EARTH
            val z = event.values[2] / SensorManager.GRAVITY_EARTH

            val gForce = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

            if (gForce > SHAKE_THRESHOLD) {
                val now = System.currentTimeMillis()
                if (lastShakeTime + SHAKE_SLOP_TIME_MS > now) return
                lastShakeTime = now
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}