package com.example.simpleapk

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.EditText
import android.widget.FrameLayout

class ShowKeyboardActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val editText = EditText(this).apply {
            hint = "Keyboard trigger"
            isSingleLine = true
            alpha = 0.01f
            requestFocus()
        }

        val root = FrameLayout(this).apply {
            addView(
                editText,
                FrameLayout.LayoutParams(1, 1, Gravity.CENTER)
            )
        }

        setContentView(root)

        editText.postDelayed({
            editText.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }
}
