package com.md.qahelper.util.ext

import android.content.Context
import android.content.Intent

/**
 *
 * Created on 2025. 12. 29..
 */
inline fun <reified T: Context> Context.myStart(block: Intent.() -> Unit = {}) {
    startActivity(Intent(this, T::class.java).apply(block))
}