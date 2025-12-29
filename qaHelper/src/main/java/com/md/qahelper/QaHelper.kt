package com.md.qahelper

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.md.qahelper.act.OverlayPermissionActivity
import com.md.qahelper.mgr.FileMgr
import com.md.qahelper.service.QaOverlayService
import com.md.qahelper.util.MyLogger
import com.md.qahelper.mgr.ShakeLifecycleMgr
import com.md.qahelper.util.ext.myStart

/**
 *
 * minSdk 24 임
 *
 * Created on 2025. 12. 22..
 */
class QaHelper {

    companion object {

        var appVersion: String = ""
            private set

        var environment: String = ""
            private set

        var descPrefix: String = ""
            private set

        var jiraBaseUrl: String = ""
            private set

        var serverUrl: String = ""
            private set

        /**
         * 앱 시작 시 호출하여 QA 기능을 초기화한다.
         * - 앱공간/qa 폴더 초기화 (기존 파일 삭제)
         *
         */
        fun init(ctx: Context, appVersion: String, environment: String, jiraBaseUrl: String, serverUrl: String, useShakeToStart: Boolean = true) {
            MyLogger.log("QaHelper: Initializing qa helper (version: $appVersion, env: $environment, jira: $jiraBaseUrl, serverUrl: ${Companion.serverUrl})")

            // 앱 버전 및 환경 정보 저장
            this.appVersion = appVersion
            this.environment = environment
            this.jiraBaseUrl = jiraBaseUrl.trimEnd('/')
            this.serverUrl = serverUrl

            this.descPrefix = "버전: $appVersion\n환경: $environment\n\n"

            // 이전 QA 파일들 초기화 (삭제)
            FileMgr.init(ctx)

            if (useShakeToStart) {
                (ctx.applicationContext as? Application)?.let { app ->
                    ShakeLifecycleMgr.enable(app)
                }
            }

        }

        fun start(ctx: Context) {
            if (Settings.canDrawOverlays(ctx)) {
                MyLogger.log("QaHelper: Overlay permission granted, starting floating button")
                QaOverlayService.start(ctx)
            } else {
                MyLogger.log("QaHelper: Overlay permission not granted, starting permission activity")
                ctx.myStart<OverlayPermissionActivity> {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }

        fun stop(ctx: Context) {
            MyLogger.log("QaHelper: stop call")
            QaOverlayService.stop(ctx)
        }

    }
}