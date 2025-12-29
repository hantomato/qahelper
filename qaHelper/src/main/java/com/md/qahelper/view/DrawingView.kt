package com.md.qahelper.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * 스크린샷 위에 펜으로 그림을 그릴 수 있는 커스텀 뷰
 *
 * Created on 2025. 12. 23.
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * 드로잉 변경 리스너
     */
    var onDrawingChangeListener: (() -> Unit)? = null

    private val paint = Paint().apply {
        color = Color.RED
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val path = Path()
    private val paths = mutableListOf<Pair<Path, Paint>>()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 모든 그려진 경로 렌더링
        for ((drawPath, drawPaint) in paths) {
            canvas.drawPath(drawPath, drawPaint)
        }

        // 현재 그리고 있는 경로 렌더링
        canvas.drawPath(path, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(x, y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(x, y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                // 현재 경로를 저장
                paths.add(Pair(Path(path), Paint(paint)))
                path.reset()
                invalidate()
                onDrawingChangeListener?.invoke()
            }
        }
        return true
    }

    /**
     * 그려진 내용을 Bitmap으로 변환
     */
    fun getDrawingBitmap(): Bitmap? {
        if (paths.isEmpty()) {
            return null
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 모든 경로를 비트맵에 그리기
        for ((drawPath, drawPaint) in paths) {
            canvas.drawPath(drawPath, drawPaint)
        }

        return bitmap
    }

    /**
     * 모든 그림 지우기
     */
    fun clear() {
        paths.clear()
        path.reset()
        invalidate()
        onDrawingChangeListener?.invoke()
    }

    /**
     * 마지막으로 그린 선 제거 (Undo)
     */
    fun undo() {
        if (paths.isNotEmpty()) {
            paths.removeAt(paths.size - 1)
            invalidate()
            onDrawingChangeListener?.invoke()
        }
    }

    /**
     * 그림이 그려져 있는지 확인
     */
    fun hasDrawing(): Boolean = paths.isNotEmpty()
}
