package com.md.qahelper.mgr

import android.content.Context
import com.md.qahelper.util.MyLogger
import java.io.File

/**
 *
 * Created on 2025. 12. 26..
 */
object FileMgr {

    private const val PARENT_DIR = "qahelper"
    private const val SCREENSHOT_DIR = "screenshot"

    fun init(ctx: Context) {
        try {
            clearQaDir(ctx)

            val qaDir = File(ctx.filesDir, PARENT_DIR)
            qaDir.mkdirs()
            MyLogger.log("FileMgr: QA directory initialized at ${qaDir.absolutePath}")
        } catch (e: Exception) {
            MyLogger.log("FileMgr: Error initializing QA directory - ${e.message}")
        }
    }

    private fun clearQaDir(ctx: Context) {
        try {
            val qaDir = File(ctx.filesDir, PARENT_DIR)
            if (qaDir.exists() && qaDir.isDirectory) {
                qaDir.deleteRecursively()
                MyLogger.log("FileMgr: QA directory cleared")
            }
        } catch (e: Exception) {
            MyLogger.log("FileMgr: Error clearing QA directory - ${e.message}")
        }
    }

    fun getQaDir(ctx: Context): File {
        val qaDir = File(ctx.filesDir, PARENT_DIR)
        if (!qaDir.exists()) {
            qaDir.mkdirs()
        }
        return qaDir
    }

    fun getScreenshotDir(ctx: Context): File {
        val screenshotDir = File(getQaDir(ctx), SCREENSHOT_DIR)
        if (!screenshotDir.exists()) {
            screenshotDir.mkdirs()
        }
        return screenshotDir
    }

    fun getScreenshotCount(ctx: Context): Int {
        val screenshotDir = File(getQaDir(ctx), SCREENSHOT_DIR)
        return if (!screenshotDir.exists()) {
            0
        } else {
            screenshotDir.listFiles()?.size ?: 0
        }
    }

    /**
     * 모든 스크린샷 파일을 삭제한다
     * @return 삭제된 파일 개수
     */
    fun deleteAllScreenshots(ctx: Context): Int {
        val screenshotDir = getScreenshotDir(ctx)
        val files = screenshotDir.listFiles() ?: return 0

        var deletedCount = 0
        files.forEach { file ->
            if (file.exists() && file.delete()) {
                deletedCount++
                MyLogger.log("FileMgr: Deleted screenshot: ${file.name}")
            }
        }

        MyLogger.log("FileMgr: Deleted $deletedCount screenshot(s)")
        return deletedCount
    }

    /**
     * 개별 스크린샷 파일을 삭제한다
     * @return 삭제 성공 여부
     */
    fun deleteScreenshotFile(ctx: Context, file: File): Boolean {
        if (!file.exists()) {
            MyLogger.log("FileMgr: File does not exist: ${file.name}")
            return false
        }

        val result = file.delete()
        if (result) {
            MyLogger.log("FileMgr: Deleted screenshot: ${file.name}")
        } else {
            MyLogger.loge("FileMgr: Failed to delete: ${file.name}")
        }
        return result
    }

}