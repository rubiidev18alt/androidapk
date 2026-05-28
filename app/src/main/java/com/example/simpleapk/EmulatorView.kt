package com.example.simpleapk

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import kotlin.math.min

class EmulatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var emulator: IbmPc5150Emulator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val textPaint = Paint().apply { color = Color.rgb(236, 236, 236); isAntiAlias = false }
    private val dimPaint = Paint().apply { color = Color.rgb(170, 170, 170); isAntiAlias = false }
    private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(10, 10, 10) }
    private val screenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(2, 2, 2) }

    private val frame = object : Runnable {
        override fun run() {
            emulator?.runFrame()
            invalidate()
            handler.postDelayed(this, 16L)
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun attach(emulator: IbmPc5150Emulator) {
        this.emulator = emulator
        requestFocus()
        invalidate()
    }

    fun start() { handler.removeCallbacks(frame); handler.post(frame) }
    fun stop() { handler.removeCallbacks(frame) }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            return true
        }
        return true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val c = event.unicodeChar
        if (c > 0) {
            emulator?.keyboard?.push(c.toChar())
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> emulator?.keyboard?.push('\r')
            KeyEvent.KEYCODE_DEL -> emulator?.keyboard?.push('\b')
            KeyEvent.KEYCODE_SPACE -> emulator?.keyboard?.push(' ')
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val emu = emulator ?: return
        canvas.drawColor(Color.BLACK)

        val pad = min(width, height) * 0.03f
        canvas.drawRoundRect(pad, pad, width - pad, height - pad, 18f, 18f, bezelPaint)

        val left = pad * 1.8f
        val top = pad * 1.8f
        val usableW = width - left * 2
        val usableH = height - top * 2
        val cellW = usableW / 80f
        val cellH = usableH / 25f
        val pixelW = cellW / 8f
        val pixelH = cellH / 8f

        canvas.drawRect(left - pixelW, top - pixelH, left + cellW * 80f + pixelW, top + cellH * 25f + pixelH, screenPaint)

        val rows = emu.video.snapshot()
        for (row in 0 until 25) {
            val text = rows[row]
            for (col in 0 until 80) {
                CgaBitmapFont.drawChar(canvas, text[col], left + col * cellW, top + row * cellH, pixelW, pixelH, textPaint)
            }
        }
    }
}
