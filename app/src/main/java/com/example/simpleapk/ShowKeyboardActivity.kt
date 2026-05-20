package com.example.simpleapk

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout

class ShowKeyboardActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        window.setDimAmount(0f)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        window.addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )

        val editText = EditText(this).apply {
            isSingleLine = true
            alpha = 0f
            width = 1
            height = 1
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTextColor(android.graphics.Color.TRANSPARENT)
            setHintTextColor(android.graphics.Color.TRANSPARENT)
            requestFocus()
        }

        val root = FrameLayout(this).apply {
            alpha = 0f
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            addView(
                editText,
                FrameLayout.LayoutParams(1, 1, Gravity.CENTER)
            )
        }

        setContentView(root)

        editText.postDelayed({
            editText.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_FORCED)
        }, 80)

        editText.postDelayed({
            finishAndRemoveTask()
            overridePendingTransition(0, 0)
        }, 450)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
