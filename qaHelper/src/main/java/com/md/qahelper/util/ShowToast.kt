package com.md.qahelper.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 *
 * Created on 2025. 12. 29..
 */
object ShowToast {

    private val mainHandler = Handler(Looper.getMainLooper())

    operator fun invoke(ctx: Context, msg: String, duration: Int = Toast.LENGTH_SHORT) {
        show(ctx, msg, duration)
    }

    private fun show(ctx: Context, msg: String, duration: Int = Toast.LENGTH_SHORT) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showInternal(ctx, msg, duration)
        } else {
            mainHandler.post {
                showInternal(ctx, msg, duration)
            }
        }
    }

    private fun showInternal(ctx: Context, msg: String, duration: Int = Toast.LENGTH_SHORT) {
        try {
            Toast.makeText(ctx, msg, duration).show()
        } catch (e: Throwable) {
        }
    }
}