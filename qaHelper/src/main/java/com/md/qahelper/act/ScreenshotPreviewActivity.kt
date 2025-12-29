package com.md.qahelper.act

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.md.qahelper.R
import com.md.qahelper.databinding.QalActivityScreenshotPreviewBinding
import com.md.qahelper.databinding.QalItemScreenshotPreviewBinding
import com.md.qahelper.mgr.FileMgr
import com.md.qahelper.util.MyLogger
import com.md.qahelper.util.ShowToast
import com.md.qahelper.util.Utils
import com.md.qahelper.util.ext.myStart
import com.md.qahelper.view.DrawingView
import java.io.File
import java.io.FileOutputStream

/**
 *
 * Created on 2025. 12. 23.
 */
class ScreenshotPreviewActivity : ComponentActivity() {

    private lateinit var adapter: ScreenshotPagerAdapter
    private val screenshots = mutableListOf<File>()
    private val binding by lazy { QalActivityScreenshotPreviewBinding.inflate(layoutInflater) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        Utils.applyWindowInsetsPadding(binding.root)
        Utils.setSystemBarsBlack(this)

        loadScreenshots()
        setupViewPager()
        setListeners()
    }

    private fun loadScreenshots() {
        val files = FileMgr.getScreenshotDir(this).listFiles()
        screenshots.clear() // 기존 데이터 초기화

        if (files != null && files.isNotEmpty()) {
            val sortedFiles = files.sortedBy { it.lastModified() }  // 가장 먼저 생성된게 먼저 표시되도록
            screenshots.addAll(sortedFiles)
        }

        if (screenshots.isEmpty()) {
            myStart<CreateJiraActivity>()
            finish()
        }
    }

    private fun setupViewPager() {
        adapter = ScreenshotPagerAdapter(screenshots)
        binding.viewPager.adapter = adapter

        // 스와이프 비활성화 (드로잉과 충돌 방지)
        binding.viewPager.isUserInputEnabled = false

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            private var previousPosition = 0

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                // 이전 페이지의 드로잉 저장
                saveCurrentDrawing(previousPosition)

                previousPosition = position
                updatePageIndicator(position)
                updateNavigationButtons(position)
                updateUndoButtonState()
            }
        })

        updatePageIndicator(0)
        updateNavigationButtons(0)
        updateUndoButtonState()
    }

    private fun setListeners() {
        binding.tvPreviousPage.setOnClickListener {
            val currentPosition = binding.viewPager.currentItem
            if (currentPosition > 0) {
                // 현재 드로잉 저장
                saveCurrentDrawing(currentPosition)
                // 이전 페이지로 이동
                binding.viewPager.setCurrentItem(currentPosition - 1, true)
            }
        }

        binding.tvNextPage.setOnClickListener {
            val currentPosition = binding.viewPager.currentItem
            if (currentPosition < adapter.getScreenshots().size - 1) {
                // 현재 드로잉 저장
                saveCurrentDrawing(currentPosition)
                // 다음 페이지로 이동
                binding.viewPager.setCurrentItem(currentPosition + 1, true)
            }
        }

        binding.ivUndo.setOnClickListener {
            val currentPosition = binding.viewPager.currentItem
            val drawingView = getCurrentDrawingView(currentPosition)
            drawingView?.undo()
            // Undo 후 현재 상태를 Adapter에 저장 (비어있으면 null 저장)
            saveCurrentDrawing(currentPosition)
            updateUndoButtonState()
        }

        binding.ivDelete.setOnClickListener {
            deleteCurrentScreenshot()
        }

        binding.tvSave.setOnClickListener {
            navigateToCreateJira()
        }

        binding.ivClose.setOnClickListener {
            finish()
        }


    }

    private fun deleteCurrentScreenshot() {
        val currentPosition = binding.viewPager.currentItem
        if (currentPosition < screenshots.size) {
            val file = screenshots[currentPosition]

            // Delete file
            if (file.exists() && file.delete()) {
                // Remove from adapter
                adapter.removeItem(currentPosition)

                // Check if any screenshots remain
                if (adapter.isEmpty()) {
                    ShowToast(this, "모든 이미지가 삭제되었습니다")
                    finish()
                } else {
                    // Update page indicator for remaining items
                    val newPosition = if (currentPosition >= adapter.getScreenshots().size) {
                        adapter.getScreenshots().size - 1
                    } else {
                        currentPosition
                    }
                    binding.viewPager.setCurrentItem(newPosition, false)
                    updatePageIndicator(newPosition)
                    updateNavigationButtons(newPosition)
                    ShowToast(this, "이미지가 삭제되었습니다")
                }
            } else {
                ShowToast(this, "이미지 삭제 실패")
            }
        }
    }

    private fun updatePageIndicator(position: Int) {
        val total = adapter.getScreenshots().size
        binding.tvPageIndicator.text = "${position + 1} / $total"
    }

    /**
     * 페이지 위치에 따라 좌우 이동 버튼 활성화/비활성화 (색상으로 표시)
     */
    private fun updateNavigationButtons(position: Int) {
        val total = adapter.getScreenshots().size

        // 첫 페이지에서는 이전 버튼 회색, 아니면 흰색
        binding.tvPreviousPage.setTextColor(
            if (position > 0) Color.WHITE else Color.GRAY
        )

        // 마지막 페이지에서는 다음 버튼 회색, 아니면 흰색
        binding.tvNextPage.setTextColor(
            if (position < total - 1) Color.WHITE else Color.GRAY
        )
    }

    private fun navigateToCreateJira() {
        try {
            // 현재 페이지의 드로잉 저장
            saveCurrentDrawing(binding.viewPager.currentItem)

            // 모든 드로잉을 이미지에 합성
            val screenshots = adapter.getScreenshots()
            val allDrawings = adapter.getAllDrawings()

            for (i in screenshots.indices) {
                val file = screenshots[i]
                val drawing = allDrawings[i]

                if (drawing != null) {
                    // 드로잉이 있으면 이미지에 합성
                    mergeDrawingToImage(file, drawing)
                    MyLogger.log("ScreenshotPreviewActivity: Drawing merged to image at position $i")
                }
            }

            myStart<CreateJiraActivity>()
            finish()

        } catch (e: Exception) {
            MyLogger.log("ScreenshotPreviewActivity: Error merging drawings - ${e.message}")
            ShowToast(this, "드로잉 저장 실패: ${e.message}")
        }
    }

    /**
     * 현재 페이지의 드로잉을 Adapter에 저장
     */
    private fun saveCurrentDrawing(position: Int) {
        try {
            val drawingView = getCurrentDrawingView(position)
            val imageRect = getImageDisplayRect(position)

            if (drawingView != null && imageRect != null) {
                val drawingInfo = if (drawingView.hasDrawing()) {
                    DrawingInfo(
                        bitmap = drawingView.getDrawingBitmap(),
                        imageRect = imageRect
                    )
                } else {
                    null // 그림이 없으면 null 저장
                }
                adapter.saveDrawing(position, drawingInfo)
                MyLogger.log("ScreenshotPreviewActivity: Drawing saved for position $position (hasDrawing: ${drawingView.hasDrawing()})")
            }
        } catch (e: Exception) {
            MyLogger.log("ScreenshotPreviewActivity: Error saving drawing - ${e.message}")
        }
    }

    /**
     * 현재 페이지의 DrawingView 가져오기
     */
    private fun getCurrentDrawingView(position: Int): DrawingView? {
        try {
            val recyclerView = binding.viewPager.getChildAt(0) as? RecyclerView
            val viewHolder = recyclerView?.findViewHolderForAdapterPosition(position)
            return (viewHolder as? ScreenshotPagerAdapter.ScreenshotViewHolder)?.drawingView
        } catch (e: Exception) {
            MyLogger.log("ScreenshotPreviewActivity: Error getting DrawingView - ${e.message}")
            return null
        }
    }

    /**
     * ImageView에서 이미지가 실제로 표시되는 영역 계산
     */
    private fun getImageDisplayRect(position: Int): RectF? {
        try {
            val recyclerView = binding.viewPager.getChildAt(0) as? RecyclerView
            val viewHolder =
                recyclerView?.findViewHolderForAdapterPosition(position) as? ScreenshotPagerAdapter.ScreenshotViewHolder
            val imageView = viewHolder?.binding?.ivScreenshot ?: return null
            val drawable = imageView.drawable ?: return null

            val imageMatrix = imageView.imageMatrix
            val values = FloatArray(9)
            imageMatrix.getValues(values)

            val scaleX = values[Matrix.MSCALE_X]
            val scaleY = values[Matrix.MSCALE_Y]
            val transX = values[Matrix.MTRANS_X]
            val transY = values[Matrix.MTRANS_Y]

            val drawableWidth = drawable.intrinsicWidth.toFloat()
            val drawableHeight = drawable.intrinsicHeight.toFloat()

            val scaledWidth = drawableWidth * scaleX
            val scaledHeight = drawableHeight * scaleY

            return RectF(transX, transY, transX + scaledWidth, transY + scaledHeight)
        } catch (e: Exception) {
            MyLogger.log("ScreenshotPreviewActivity: Error getting image display rect - ${e.message}")
            return null
        }
    }

    /**
     * Undo 버튼 상태 업데이트 (그림이 있으면 빨간색, 없으면 회색)
     */
    private fun updateUndoButtonState() {
        val currentPosition = binding.viewPager.currentItem

        // 현재 DrawingView에 그림이 있는지 확인 (지금 그리고 있는 선)
        val drawingView = getCurrentDrawingView(currentPosition)
        val hasCurrentDrawing = drawingView?.hasDrawing() ?: false

        // Adapter에 저장된 드로잉이 있는지 확인 (이전에 그린 선)
        val hasSavedDrawing = adapter.getDrawing(currentPosition)?.bitmap != null

        // 둘 중 하나라도 있으면 Undo 가능
        val hasDrawing = hasCurrentDrawing || hasSavedDrawing

        binding.ivUndo.imageTintList = if (hasDrawing) {
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.qal_red_medium))
        } else {
            ColorStateList.valueOf(Color.parseColor("#ffffff"))
        }
    }

    /**
     * 드로잉을 이미지에 합성하여 파일에 저장 (좌표 보정 적용)
     */
    private fun mergeDrawingToImage(imageFile: File, drawingInfo: DrawingInfo) {
        try {
            val drawingBitmap = drawingInfo.bitmap ?: return
            val imageRect = drawingInfo.imageRect

            // 원본 이미지 로드
            val originalBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                ?: throw Exception("Failed to load image: ${imageFile.name}")

            // 합성용 비트맵 생성
            val mergedBitmap = Bitmap.createBitmap(
                originalBitmap.width,
                originalBitmap.height,
                Bitmap.Config.ARGB_8888
            )

            val canvas = Canvas(mergedBitmap)

            // 원본 이미지 그리기
            canvas.drawBitmap(originalBitmap, 0f, 0f, null)

            // ImageView에서 이미지가 표시된 영역의 드로잉만 크롭
            val croppedDrawing = Bitmap.createBitmap(
                drawingBitmap,
                imageRect.left.toInt().coerceAtLeast(0),
                imageRect.top.toInt().coerceAtLeast(0),
                imageRect.width().toInt().coerceAtMost(drawingBitmap.width),
                imageRect.height().toInt().coerceAtMost(drawingBitmap.height)
            )

            // 크롭된 드로잉을 원본 이미지 크기에 맞게 스케일링
            val scaledDrawing = Bitmap.createScaledBitmap(
                croppedDrawing,
                originalBitmap.width,
                originalBitmap.height,
                true
            )

            // 스케일링된 드로잉을 이미지에 합성
            canvas.drawBitmap(scaledDrawing, 0f, 0f, null)

            // 파일에 저장
            FileOutputStream(imageFile).use { out ->
                mergedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // 비트맵 리소스 해제
            originalBitmap.recycle()
            mergedBitmap.recycle()
            croppedDrawing.recycle()
            scaledDrawing.recycle()

            MyLogger.log("ScreenshotPreviewActivity: Drawing merged successfully to ${imageFile.name} (imageRect: $imageRect)")

        } catch (e: Exception) {
            MyLogger.log("ScreenshotPreviewActivity: Error merging drawing to image - ${e.message}")
            throw e
        }
    }

    /**
     * 드로잉 정보 (비트맵 + 이미지 표시 영역)
     */
    data class DrawingInfo(
        val bitmap: Bitmap?,
        val imageRect: RectF  // ImageView에서 이미지가 실제로 표시되는 영역
    )

    inner class ScreenshotPagerAdapter(private var screenshots: MutableList<File>) :
        RecyclerView.Adapter<ScreenshotPagerAdapter.ScreenshotViewHolder>() {

        // 각 페이지의 드로잉 정보 저장
        private val drawingInfos = mutableMapOf<Int, DrawingInfo?>()

        inner class ScreenshotViewHolder(val binding: QalItemScreenshotPreviewBinding) :
            RecyclerView.ViewHolder(binding.root) {
            val drawingView: DrawingView
                get() = binding.drawingView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScreenshotViewHolder {
            val binding = QalItemScreenshotPreviewBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ScreenshotViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ScreenshotViewHolder, position: Int) {
            val file = screenshots[position]

            // 간단하게 URI로 이미지 로드 (Glide 불필요)
            holder.binding.ivScreenshot.setImageURI(file.toUri())

            // 이전에 저장된 드로잉이 있으면 복원 (현재는 비트맵만 저장하므로 새로 그리기는 불가)
            holder.drawingView.clear()

            // 드로잉 변경 시 Undo 버튼 상태 업데이트
            holder.drawingView.onDrawingChangeListener = {
                updateUndoButtonState()
            }
        }

        override fun getItemCount(): Int = screenshots.size

        /**
         * Remove item at position and update adapter
         */
        fun removeItem(position: Int) {
            if (position in screenshots.indices) {
                screenshots.removeAt(position)
                notifyItemRemoved(position)
                notifyItemRangeChanged(position, screenshots.size)
            }
        }

        /**
         * Get current screenshot list
         */
        fun getScreenshots(): List<File> = screenshots

        /**
         * Check if adapter has any screenshots
         */
        fun isEmpty(): Boolean = screenshots.isEmpty()

        /**
         * 특정 위치의 드로잉 정보 저장
         */
        fun saveDrawing(position: Int, drawingInfo: DrawingInfo?) {
            drawingInfos[position] = drawingInfo
        }

        /**
         * 특정 위치의 드로잉 정보 가져오기
         */
        fun getDrawing(position: Int): DrawingInfo? {
            return drawingInfos[position]
        }

        /**
         * 모든 드로잉 정보 가져오기
         */
        fun getAllDrawings(): Map<Int, DrawingInfo?> {
            return drawingInfos.toMap()
        }
    }

}
