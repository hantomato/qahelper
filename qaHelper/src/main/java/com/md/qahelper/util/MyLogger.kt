package com.md.qahelper.util

import android.util.Log

/**
 *
 * Created on 2025. 12. 26..
 */
class MyLogger {
    companion object {
        private const val LOG_TAG = "ql2"

        fun log(msg: String) {
            val posInfo = getPositionInfo("log")
            Log.d(posInfo.first, posInfo.second + msg)
        }

        fun loge(msg: String) {
            val posInfo = getPositionInfo("loge")
            Log.e(posInfo.first, posInfo.second + msg)
        }

        private fun getPositionInfo(methodName: String): Pair<String, String> {
            val maxLen = 80
            val ellipsis = "..." // 말줄임표
            var tag: String = LOG_TAG
            var prefix: String = ""
            val ste = Thread.currentThread().stackTrace
            for (i in ste.indices) {
                if (ste[i].methodName == methodName && i < ste.size - 1) {
                    val nextSte = ste[i + 1]
                    // example
                    // LOG_TAG (Filename.java:104) TextTool.getFirstCharIdxOnScreen: FirstCharIdxOnScreen : 0
                    // <---------------------- TAG ------------------------>
                    val fileStr = " (" + nextSte.fileName + ":" + nextSte.lineNumber + ")"
                    val classStr = nextSte.className.substring(nextSte.className.lastIndexOf('.') + 1)
                    val methodStr = "." + nextSte.methodName

                    tag = tag + fileStr
                    prefix = "[$classStr$methodStr] "

                    if (tag.length > maxLen) {
                        tag = tag.substring(0, maxLen - ellipsis.length) + ellipsis
                    }
                    break
                }
            }
            return tag to prefix
        }
    }
}