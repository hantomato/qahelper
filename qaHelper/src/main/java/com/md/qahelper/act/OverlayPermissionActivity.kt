package com.md.qahelper.act

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.md.qahelper.service.QaOverlayService
import com.md.qahelper.util.MyLogger

/**
 *
 */
class OverlayPermissionActivity : ComponentActivity() {

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // 권한 설정에서 돌아옴
        if (Settings.canDrawOverlays(this)) {
            MyLogger.log("OverlayPermissionActivity: Permission granted, starting service")
            QaOverlayService.start(this)
        } else {
            MyLogger.log("OverlayPermissionActivity: Permission denied")
        }

        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MyLogger.log("OverlayPermissionActivity onCreate")

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)

            overlayPermissionLauncher.launch(intent)
        } else {
            // 이미 권한이 있음
            MyLogger.log("OverlayPermissionActivity: Permission already granted")
            QaOverlayService.start(this)
            finish()
        }
    }
}