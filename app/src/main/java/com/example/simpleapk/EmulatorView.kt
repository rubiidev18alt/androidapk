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
    private val screenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(80, 255, 120)
        typeface = android.graphics.Typeface.MONOSPACE
    }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(25, 105, 40)
        typeface = android.graphics.Typeface.MONOSPACE
    }
    private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(12, 12, 12) }
    private val phosphorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 18, 4) }

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

    fun start() {
        handler.removeCallbacks(frame)
        handler.post(frame)
    }

    fun stop() {
        handler.removeCallbacks(frame)
    }

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

        val pad = min(width, height) * 0.035f
        canvas.drawRoundRect(pad, pad, width - pad, height - pad, 24f, 24f, bezelPaint)
        canvas.drawRoundRect(pad * 1.6f, pad * 1.6f, width - pad * 1.6f, height - pad * 1.6f, 18f, 18f, phosphorPaint)

        val left = pad * 2.15f
        val top = pad * 2.3f
        val usableW = width - left * 2
        val usableH = height - top * 2.2f
        val cellW = usableW / 80f
        val cellH = usableH / 27f
        screenPaint.textSize = min(cellW * 1.08f, cellH * 0.85f)
        dimPaint.textSize = screenPaint.textSize
        val baselineShift = cellH * 0.78f

        canvas.drawText("IBM PC Model 5150  |  Intel 8088 @ 4.77 MHz  |  RAM 16 KB  |  BIOS: ${emu.biosName}", left, top + baselineShift, dimPaint)
        canvas.drawText("Tap screen for keyboard. This is a tiny educational emulator, not a full 86Box replacement.", left, top + cellH + baselineShift, dimPaint)

        val rows = emu.video.snapshot()
        for (row in 0 until 25) {
            val text = rows[row]
            canvas.drawText(text, left, top + (row + 2) * cellH + baselineShift, screenPaint)
        }
    }
}
