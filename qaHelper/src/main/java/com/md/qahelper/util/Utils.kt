package com.md.qahelper.util

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding

/**
 *
 * Created on 2025. 12. 29..
 */
class Utils {

    companion object {

        fun applyWindowInsetsPadding(rootView: View) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
                val insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
                )
                view.updatePadding(insets.left, insets.top, insets.right, insets.bottom)
                windowInsets
            }
        }

        fun setSystemBarsBlack(act: Activity?) {
            act?.window?.let { window ->
                val controller = WindowInsetsControllerCompat(window, window.decorView)

                // 1. 상태바 설정 (배경 검정, 아이콘 흰색)
                window.statusBarColor = Color.BLACK
                controller.isAppearanceLightStatusBars = false

                // 2. 내비게이션 바 설정 (배경 검정, 아이콘 흰색)
                window.navigationBarColor = Color.BLACK
                controller.isAppearanceLightNavigationBars = false
            }
        }

        fun hideKeyboard(view: View) {
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }

        fun hideKeyboard(act: Activity) {
            val imm = act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val view = act.currentFocus ?: View(act)
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}