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

}