package com.md.qahelper.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.md.qahelper.act.MediaProjectionPermissionActivity
import com.md.qahelper.act.ScreenshotPreviewActivity
import com.md.qahelper.databinding.QalViewOverlayServiceBinding
import com.md.qahelper.mgr.FileMgr
import com.md.qahelper.util.MyLogger
import com.md.qahelper.util.ShowToast
import com.md.qahelper.util.ext.myStart

/**
 * 앱 전역에 떠있는 플로팅 버튼 서비스
 * WindowManager를 사용하여 모든 Activity 위에 버튼을 표시합니다.
 */
class QaOverlayService : Service() {

    companion object {
        private const val CLICK_THRESHOLD = 20 // 픽셀 이동 임계값 (터치 오차 허용)
        private var isServiceRunning = false

        fun start(context: Context) {
            if (isServiceRunning) {
                MyLogger.log("QaOverlayService is already running")
                return
            }
   
            try {
                val intent = Intent(context, QaOverlayService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                MyLogger.log("QaOverlayService start error: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, QaOverlayService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                MyLogger.log("QaOverlayService stop error: ${e.message}")
            }
        }

        fun isRunning(): Boolean = isServiceRunning
    }

    private var windowManager: WindowManager? = null
    private var isFloatingViewAttached = false

    // 이동 처리
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var isExpanded = false // 메뉴 펼침 상태

    private val binding by lazy { QalViewOverlayServiceBinding.inflate(LayoutInflater.from(this)) }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //region override fun
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MyLogger.log("QaOverlayService: onStartCommand")
        return START_NOT_STICKY // START_STICKY: 서비스가 죽으면, 자동으로 되살려줍니다. START_NOT_STICKY: 죽으면 끝. 사용자가 다시 켤 때까지 안 살아납니다.
    }

    override fun onDestroy() {
        super.onDestroy()
        MyLogger.log("QaOverlayService onDestroy")

        try {
            if (isFloatingViewAttached) {
                windowManager?.removeView(binding.root)
                isFloatingViewAttached = false
            }
        } catch (e: Exception) {
            MyLogger.log("QaOverlayService: Error removing floating view - ${e.message}")
        }
        windowManager = null
        isServiceRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        MyLogger.log("QaOverlayService onCreate")
        isServiceRunning = true

        try {
            initialize()
        } catch (e: Exception) {
            MyLogger.log("QaOverlayService setup error: ${e.message}")
            stopSelf()
        }
    }
    //endregion override fun
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    private fun initialize() {
        if (!Settings.canDrawOverlays(this)) {
            MyLogger.log("QaOverlayService: Overlay permission not granted!")
            ShowToast(this, "오버레이 권한이 필요합니다.")
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 윈도우 파라미터 설정
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, // 1. 너비 (width)
            WindowManager.LayoutParams.WRAP_CONTENT, // 2. 높이 (height)
            layoutFlag,                              // 3. 윈도우 타입 (type)
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // 4. 동작 플래그 (flags)
            PixelFormat.TRANSLUCENT                  // 5. 그래픽 포맷 (format)
        )

        // 초기 위치 설정
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 200

        setMenuState(false)
        var moved = false

        // 무빙 처리
        binding.ivExpanded.setOnTouchListener { _, event ->
            val p = params

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 터치 시작: 초기 좌표 저장
                    initialX = p.x
                    initialY = p.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    // 1. 먼저 이동 거리 체크
                    if (!moved) {
                        val deltaX = Math.abs(event.rawX - initialTouchX)
                        val deltaY = Math.abs(event.rawY - initialTouchY)
                        // THRESHOLD를 넘으면 드래그로 인정
                        if (deltaX > CLICK_THRESHOLD || deltaY > CLICK_THRESHOLD) {
                            moved = true
                        }
                    }

                    // 2. moved가 true일 때만 위치 업데이트 (드래그 모드)
                    if (moved) {
                        p.x = initialX + (event.rawX - initialTouchX).toInt()
                        p.y = initialY + (event.rawY - initialTouchY).toInt()

                        if (isFloatingViewAttached) {
                            windowManager?.updateViewLayout(binding.root, p)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    // 드래그가 아니었다면(moved == false) 클릭으로 처리
                    if (!moved) {
                        MyLogger.log("FloatingButton: Icon Clicked")
                        setMenuState(true)
                    }
                    true
                }

                else -> false
            }
        }

        binding.ivClose.setOnClickListener {
            onCloseClick()
        }
        binding.ivCollapse.setOnClickListener {
            onCollapseClick()
        }

        binding.layoutScreenshot.setOnClickListener {
            onScreenShotClick()
        }

        binding.ivUpload.setOnClickListener {
            onPostButtonClick()
        }

        // 뷰를 WindowManager에 추가
        try {
            windowManager?.addView(binding.root, params)
            isFloatingViewAttached = true
            MyLogger.log("QaOverlayService: Floating button attached")

            // 초기 스크린샷 개수 표시
            updateScreenshotCount()
        } catch (e: Exception) {
            MyLogger.log("QaOverlayService: Failed to attach floating button - ${e.message}")
            isFloatingViewAttached = false
        }
    }

    // 메뉴 상태 전환 함수
    private fun setMenuState(expand: Boolean) {
        isExpanded = expand
        if (expand) {
            binding.layoutCollapsed.visibility = View.GONE
            binding.llExpanded.visibility = View.VISIBLE
        } else {
            binding.layoutCollapsed.visibility = View.VISIBLE
            binding.llExpanded.visibility = View.GONE
        }
    }

    private fun updateScreenshotCount() {
        try {
            val count = FileMgr.getScreenshotCount(this)
            binding.tvScreenshotCount.let { tvCount ->
                if (count > 0) {
                    tvCount.text = count.toString()
                    tvCount.visibility = View.VISIBLE
                } else {
                    tvCount.visibility = View.GONE
                }
            }

            MyLogger.log("QaOverlayService: Screenshot count updated - $count")
        } catch (e: Exception) {
            MyLogger.log("QaOverlayService: Error updating screenshot count - ${e.message}")
        }
    }

    private fun onCloseClick() {
        stopSelf()
    }

    private fun onCollapseClick() {
        setMenuState(false)
    }

    private fun onScreenShotClick() {
        MyLogger.log("QaOverlayService: onScreenShotClick button clicked!")

        try {
            // MediaProjectionService가 실행 중인지 확인
            if (MediaProjectionService.isRunning()) {
                // 이미 실행 중이면 스크린샷 캡처
                MediaProjectionService.captureScreenshot(this)

                Handler(Looper.getMainLooper()).postDelayed({
                    updateScreenshotCount()
                }, 500)
            } else {
                // 실행 중이 아니면 권한 요청 Activity 시작
                MyLogger.log("QaOverlayService: Starting MediaProjectionPermissionActivity")
                ShowToast(this, "MediaProjection 권한을 요청합니다")
                myStart<MediaProjectionPermissionActivity>() {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

        } catch (e: Exception) {
            MyLogger.log("QaOverlayService: Error in MediaProjection button - ${e.message}")
            ShowToast(this, "MediaProjection 실행 실패: ${e.message}")
        }
    }

    private fun onPostButtonClick() {
        stopSelf()

        myStart<ScreenshotPreviewActivity> {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

}