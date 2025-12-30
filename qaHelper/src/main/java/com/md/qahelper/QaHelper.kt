package com.md.qahelper

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
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

        var appInfo: String = ""
            private set

        var descPrefix: String = ""
            private set

        var jiraBaseUrl: String = ""
            private set

        var serverUrl: String = ""
            private set

        var preShakeAction: (() -> Unit)? = null
            private set

        /**
         * 앱 시작 시 호출하여 QA 기능을 초기화한다.
         * - 앱공간/qa 폴더 초기화 (기존 파일 삭제)
         *
         */
        fun init(
            ctx: Context,
            appInfo: String,
            jiraBaseUrl: String,
            serverUrl: String,
            useShakeToStart: Boolean = true
        ) {
            MyLogger.log("QaHelper: Initializing qa helper (appInfo: $appInfo, jiraBaseUrl: $jiraBaseUrl, serverUrl: $serverUrl, useShakeToStart: $useShakeToStart)")

            // 앱 버전 및 환경 정보 저장
            this.jiraBaseUrl = jiraBaseUrl.trimEnd('/')
            this.serverUrl = serverUrl
            setAppInfo(appInfo)

            FileMgr.init(ctx)

            if (useShakeToStart) {
                (ctx.applicationContext as? Application)?.let { app ->
                    ShakeLifecycleMgr.enable(app)
                }
            }

        }

        fun setAppInfo(appInfo: String) {
            this.appInfo = appInfo
            this.descPrefix = "== 디바이스 정보 ==\n" +
                    "• OS: ${Build.VERSION.RELEASE}\n" +
                    "• 모델명: ${Build.MODEL}\n" +
                    "\n" +
                    "== 앱 정보 ==\n" +
                    "$appInfo\n" +
                    "\n"
        }

        fun start(ctx: Context, appInfo: String? = null) {
            appInfo?.let {
                setAppInfo(it)
            }

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

        /**
         * Shake 이벤트로 QaHelper.start()가 호출되기 전에 실행될 사전 작업을 설정한다.
         *
         * @param action Shake 감지 후 QaHelper.start() 호출 전에 실행될 함수
         */
        fun setPreShakeAction(action: (() -> Unit)?) {
            preShakeAction = action
        }

    }
}