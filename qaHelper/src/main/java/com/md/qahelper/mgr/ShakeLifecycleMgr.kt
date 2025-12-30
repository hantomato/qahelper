package com.md.qahelper.mgr

import android.app.Activity
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import com.md.qahelper.QaHelper
import com.md.qahelper.util.ShakeDetector

/**
 *
 * Created on 2025. 12. 29..
 */
internal object ShakeLifecycleMgr : Application.ActivityLifecycleCallbacks {

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var shakeDetector: ShakeDetector? = null
    private var currentActivity: Activity? = null
    private var isEnabled = false

    fun enable(application: Application) {
        if (isEnabled) return

        // 흔들렸을 때 동작 정의
        shakeDetector = ShakeDetector {
            // 사전 작업이 있다면 먼저 실행
            QaHelper.preShakeAction?.invoke()
            // QaHelper 시작
            QaHelper.start(application)
        }

        // 앱의 모든 액티비티 상태 변화를 감지 등록
        application.registerActivityLifecycleCallbacks(this)
        isEnabled = true
    }

    // 화면이 켜졌을 때 (Resume) -> 센서 ON
    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity

        if (sensorManager == null) {
            sensorManager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }

        accelerometer?.let {
            sensorManager?.registerListener(shakeDetector, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    // 화면이 꺼지거나 다른 앱으로 갔을 때 (Pause) -> 센서 OFF (배터리 보호)
    override fun onActivityPaused(activity: Activity) {
        currentActivity = null
        sensorManager?.unregisterListener(shakeDetector)
    }

    // 나머지 미사용 구현체
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}