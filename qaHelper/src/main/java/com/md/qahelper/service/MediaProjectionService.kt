package com.md.qahelper.service

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.md.qahelper.mgr.FileMgr
import com.md.qahelper.util.MyLogger
import com.md.qahelper.util.ShowToast
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MediaProjection을 사용하여 스크린샷을 캡처하는 서비스
 *
 * 화면 캡처는 크게 준비(Setup) → 연결(Connect) → 찰칵(Capture) → 마무리(Close) 4단계로 진행됩니다.
 *
 * 0 사용자로부터 권한 획득
 *
 * 1. 준비 : 안드로이드 시스템으로부터 "화면 녹화 도구"를 받아와야 합니다.
 *  - getMediaProjection
 *
 * 2. 화면 연결 : 리모컨만 있다고 화면이 찍히지 않습니다. "화면 데이터를 받아낼 그릇"을 만들고 연결해야 합니다. VirtualDisplay 사용
 *  - 화면 픽셀 데이터를 담을 **빈 그릇(버퍼)**을 만듭니다.
 *    - ImageReader.newInstance(width, height, format, maxImages)
 *  - "내 핸드폰 화면을 저기 그릇(ImageReader.surface)에 계속 쏴라!"라고 명령합니다.
 *    - mediaProjection.createVirtualDisplay(...)
 *    - 이 메서드가 호출되는 순간부터, 핸드폰 화면이 실시간으로 ImageReader 안으로 전송되기 시작합니다.
 *
 * 3. 찰칵 : 사용자가 "캡처!" 버튼을 눌렀을 때 호출하는 메서드입니다.
 *  - 그릇에 담겨있는 화면 프레임 중 가장 최신 장면 1장을 꺼냅니다.
- imageReader.acquireLatestImage() 이걸 호출하면 Image 객체가 튀어나옵니다.
 *  - 꺼낸 이미지에서 **실제 픽셀 데이터(byte)**를 가져옵니다.
 *    - image.planes[0].buffer
 *  - 픽셀 데이터를 우리가 눈으로 볼 수 있는 이미지 파일(Bitmap) 형태로 바꿉니다.
 *    - Bitmap.createBitmap(...) & copyPixelsFromBuffer(...)
 *
 * 4. 뒷정리
 *  - 방금 찍은 사진을 다 썼으면 버퍼를 비워줘야 합니다. 이걸 안 하면 그릇(ImageReader)이 꽉 차서 다음 사진이 안 들어옵니다.
 *    - image.close()
 *
 * 요약
getMediaProjection: 녹화 도구 획득

createVirtualDisplay: 화면 → ImageReader 연결 (수도꼭지 틀기)

acquireLatestImage: 컵에 물 한 잔 받기 (캡처)

bitmap 변환: 마실 수 있게 정수하기 (파일 저장)

image.close: 컵 비우기 (다음 캡처 준비)
 *
 * https://developer.android.com/media/grow/media-projection?hl=ko
 *
 * * Android 14부터는 getMediaProjection()을 호출하기 전에 반드시 포그라운드 서비스가 먼저
 * * 실행되어 있어야 합니다. 그렇지 않으면 앱이 죽습니다(SecurityException).
 *
 *
 *
 *
 * public MediaProjection MediaProjectionManager.getMediaProjection(int resultCode, @NonNull Intent resultData
 * * 사용자가 허용한 권한 정보(티켓)을 시스템에 제출하고, 실제 화면을 제어할 수 있는 객체(리모컨)를 받아오는 기능을 수행합니다.
 * * 서비스 내부의 onStartCommand 에서 아래 호출해야 함.
 *
 *
 * Created on 2025. 12. 23.
 */
class MediaProjectionService : Service() {

    private var mediaProjection: MediaProjection? = null    // 핵심
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    // 캡처 요청 플래그 (OnImageAvailableListener에서 사용)
    private var captureRequested = false

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "media_projection_service"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "data"

        private var instance: MediaProjectionService? = null
        private var isServiceRunning = false

        fun start(context: Context, resultCode: Int, data: Intent, autoCapture: Boolean = false) {
            val intent = Intent(context, MediaProjectionService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MediaProjectionService::class.java)
            context.stopService(intent)
        }

        fun captureScreenshot(context: Context) {
            instance?.captureScreen(context)
        }

        fun isRunning(): Boolean = isServiceRunning
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //region override fun
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MyLogger.log("MediaProjectionService: onStartCommand")

        intent?.let {
            val resultCode = it.getIntExtra(EXTRA_RESULT_CODE, 0)
            val data = it.getParcelableExtra<Intent>(EXTRA_DATA)

            MyLogger.log("MediaProjectionService: resultCode=$resultCode, data=$data")

            // Activity.RESULT_OK는 -1이므로, data가 null이 아니면 정상
            if (data != null) {
                setupMediaProjection(resultCode, data)
            } else {
                MyLogger.log("MediaProjectionService: Intent data is null, cannot setup MediaProjection")
            }
        }

        return START_NOT_STICKY // START_STICKY: 서비스가 죽으면, 자동으로 되살려줍니다. START_NOT_STICKY: 죽으면 끝. 사용자가 다시 켤 때까지 안 살아납니다.
    }

    override fun onDestroy() {
        super.onDestroy()
        MyLogger.log("MediaProjectionService: onDestroy")

        cleanupMediaProjection()

        instance = null
        isServiceRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        MyLogger.log("MediaProjectionService: onCreate")
        instance = this
        isServiceRunning = true

        // Foreground notification 설정
        createNotificationChannel()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 화면 크기 및 밀도 가져오기
        getScreenMetrics()
    }
    //endregion override fun
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "MediaProjection 스크린샷",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "MediaProjection을 사용한 스크린샷 캡처"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("MediaProjection 스크린샷")
            .setContentText("스크린샷 캡처 준비 완료")
            .setSmallIcon(R.drawable.ic_menu_camera)
            .build()
    }

    private fun getScreenMetrics() {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android R 이상: WindowMetrics 사용
            val bounds = windowManager.currentWindowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
            screenDensity = resources.displayMetrics.densityDpi
        } else {
            // Android R 미만: deprecated API 사용
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val metrics = DisplayMetrics()
            display.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDensity = metrics.densityDpi
        }

        MyLogger.log("MediaProjectionService: Screen metrics - ${screenWidth}x${screenHeight}, density: $screenDensity")
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        try {
            val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

            // MediaProjection 콜백 등록 (API 34+에서 필수)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    MyLogger.log("MediaProjectionService: MediaProjection stopped by system")
                    // 시스템에 의해 중단되었을 때 리소스 정리
                    mainHandler.post {
                        cleanupMediaProjection()
                    }
                }
            }, mainHandler)

            // ImageReader 설정
            imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2
            )

            // OnImageAvailableListener 등록 - 이미지가 준비되면 즉시 호출됨
            imageReader?.setOnImageAvailableListener({ reader ->
                if (captureRequested) {
                    captureRequested = false

                    try {
                        val image = reader.acquireLatestImage()
                        if (image != null) {
                            MyLogger.log("MediaProjectionService: Image available, capturing now")
                            val bitmap = imageToBitmap(image)
                            image.close()

                            saveScreenshot(bitmap)

                            ShowToast(this, "MediaProjection 스크린샷이 저장되었습니다")
                            MyLogger.log("MediaProjectionService: Screenshot captured successfully")
                        } else {
                            MyLogger.log("MediaProjectionService: Failed to acquire image")
                            mainHandler.post {
                                ShowToast(this, "스크린샷 캡처 실패")
                            }
                        }
                    } catch (e: Exception) {
                        MyLogger.log("MediaProjectionService: Error capturing screenshot - ${e.message}")
                        ShowToast(this, "스크린샷 캡처 실패: ${e.message}")
                    }
                }
            }, mainHandler)

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "MediaProjectionScreenCapture",       // 1. 이름 (아무거나 상관없음)
                screenWidth,                          // 2. 너비
                screenHeight,                         // 3. 높이
                screenDensity,                        // 4. 밀도 (DPI)
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, // 5. ★ 핵심 플래그
                imageReader?.surface,                 // 6. ★ 핵심 타겟 (어디에 그릴 것인가)
                null,                                 // 7. 콜백 (멈춤/재개 감지용, 안 쓰면 null)
                mainHandler                           // 8. 핸들러 (콜백 실행 스레드)
            )


            MyLogger.log("MediaProjectionService: MediaProjection setup completed")

        } catch (e: Exception) {
            MyLogger.log("MediaProjectionService: Error setting up MediaProjection - ${e.message}")
            ShowToast(this, "MediaProjection 설정 실패: ${e.message}")
            stopSelf()
        }
    }

    fun captureScreen(context: Context) {
        MyLogger.log("MediaProjectionService: Capture screen requested")

        if (mediaProjection == null || imageReader == null) {
            MyLogger.log("MediaProjectionService: MediaProjection not ready")
            ShowToast(this, "MediaProjection이 준비되지 않았습니다")
            return
        }

        // 캡처 요청 플래그 설정
        captureRequested = true

        // 현재 사용 가능한 이미지를 즉시 시도
        mainHandler.post {
            if (captureRequested) {
                captureRequested = false

                try {
                    val image = imageReader?.acquireLatestImage()
                    if (image != null) {
                        MyLogger.log("MediaProjectionService: Image available, capturing now")
                        val bitmap = imageToBitmap(image)
                        image.close()

                        saveScreenshot(bitmap)

                        ShowToast(this, "MediaProjection 스크린샷이 저장되었습니다")
                        MyLogger.log("MediaProjectionService: Screenshot captured successfully")
                    } else {
                        MyLogger.log("MediaProjectionService: No image available yet, waiting for listener")
                        captureRequested = true // 다시 true로 설정하여 리스너에서 재시도
                    }
                } catch (e: Exception) {
                    MyLogger.log("MediaProjectionService: Error capturing screenshot - ${e.message}")
                    ShowToast(this, "스크린샷 캡처 실패: ${e.message}")
                    captureRequested = false
                }
            }
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * screenWidth

        val bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride,
            screenHeight,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        return bitmap
    }

    private fun saveScreenshot(bitmap: Bitmap) {
        try {
            // 파일명 생성 (mp_screenshot_yyyyMMdd_HHmmss.png)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "screenshot_$timestamp.png"

            // 스크린샷 디렉토리 가져오기
            val screenshotDir = FileMgr.getScreenshotDir(this)
            val file = File(screenshotDir, fileName)

            // Bitmap을 파일로 저장
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            MyLogger.log("MediaProjectionService: Screenshot saved to ${file.absolutePath}")

            // FloatingButtonService의 스크린샷 카운트 업데이트
            // (FloatingButtonService가 주기적으로 확인하므로 별도 처리 불필요)

        } catch (e: Exception) {
            MyLogger.log("MediaProjectionService: Error saving screenshot - ${e.message}")
            throw e
        }
    }

    private fun cleanupMediaProjection() {
        MyLogger.log("MediaProjectionService: Cleaning up MediaProjection resources")

        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()

        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }
}
