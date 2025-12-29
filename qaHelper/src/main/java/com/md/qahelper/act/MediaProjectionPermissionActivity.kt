package com.md.qahelper.act

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.md.qahelper.service.MediaProjectionService
import com.md.qahelper.util.MyLogger
import com.md.qahelper.util.ShowToast

/**
 *
 * Created on 2025. 12. 23.
 */
class MediaProjectionPermissionActivity : ComponentActivity() {

    private val mediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val requestMediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            MyLogger.log("MediaProjectionPermissionActivity: Permission granted")
            MediaProjectionService.start(this, result.resultCode, result.data!!)

            ShowToast(this, "MediaProjection이 준비되었습니다. 버튼을 눌러 스크린샷을 캡처하세요", Toast.LENGTH_LONG)
        } else {
            MyLogger.log("MediaProjectionPermissionActivity: Permission denied")
            ShowToast(this, "MediaProjection 권한이 거부되었습니다")
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MyLogger.log("MediaProjectionPermissionActivity: onCreate")

        // MediaProjection 권한 요청
        requestMediaProjectionPermission()
    }

    private fun requestMediaProjectionPermission() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= 34) {
                // [Android 14 이상]
                // AX Recorder처럼 '단일 앱' 옵션을 비활성화하고 '전체 화면'을 강제합니다.
                val config = MediaProjectionConfig.createConfigForDefaultDisplay()
                mediaProjectionManager.createScreenCaptureIntent(config)
            } else {
                // [Android 13 이하]
                // 기존과 동일하게 동작합니다.
                mediaProjectionManager.createScreenCaptureIntent()
            }

            requestMediaProjectionLauncher.launch(intent)
            MyLogger.log("MediaProjectionPermissionActivity: Permission request launched")

        } catch (e: Exception) {
            // 예외 발생 시 로그 및 사용자 알림
            MyLogger.log("MediaProjectionPermissionActivity: Error requesting permission - ${e.message}")
            ShowToast(this, "권한 요청 실패: ${e.message}")
            finish()
        }
    }
}