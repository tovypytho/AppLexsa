package com.tovypytho.applexsa.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class FloatingControl(private val service: AccessibilityService, private val click: () -> Unit, private val close: () -> Unit) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private var button: Button? = null
    private var calibrationView: View? = null
    private val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
        gravity = Gravity.TOP or Gravity.START; x = 12; y = 200
    }
    fun show(label: String) {
        if (calibrationView != null) return
        if (root == null) {
            val layout = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                background = GradientDrawable().apply { setColor(Color.rgb(5, 83, 74)); cornerRadius = 36f }
                setPadding(8, 4, 8, 4)
            }
            button = Button(service).apply { textSize = 14f; setOnClickListener { click() } }
            layout.addView(button)
            layout.addView(Button(service).apply { text = "×"; contentDescription = "Hentikan AppLexsa"; setOnClickListener { close() } }, LinearLayout.LayoutParams(110, -1))
            root = layout
            installDrag(button!!)
            wm.addView(layout, params)
        }
        button?.text = label
    }
    private fun installDrag(view: View) {
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawX - downX) + abs(event.rawY - downY) > 16) moved = true
                    if (moved) {
                        val bounds = wm.currentWindowMetrics.bounds
                        params.x = (startX + event.rawX - downX).toInt().coerceIn(0, (bounds.width() - v.width).coerceAtLeast(0))
                        params.y = (startY + event.rawY - downY).toInt().coerceIn(0, (bounds.height() - v.height).coerceAtLeast(0))
                        root?.let { wm.updateViewLayout(it, params) }
                    }; true
                }
                MotionEvent.ACTION_UP -> { if (!moved) v.performClick(); true }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }
    fun calibrate(onPoint: (Int, Int) -> Unit) {
        hide()
        val capture = TextView(service).apply {
            text = "Ketuk tepat di tengah ikon teruskan postingan terakhir.\n\nTampilan harus tetap di ujung saluran.\nTombol Back membatalkan."
            textSize = 18f; setTextColor(Color.WHITE); setBackgroundColor(0x33000000)
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; setPadding(20, 110, 20, 0)
            setOnTouchListener { v, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) { v.performClick(); onPoint(event.rawX.toInt(), event.rawY.toInt()) }
                true
            }
        }
        calibrationView = capture
        wm.addView(capture, WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT))
    }
    fun hide() { root?.let { wm.removeView(it) }; root = null; button = null }
    fun dismissCalibration() { calibrationView?.let { wm.removeView(it) }; calibrationView = null }
    fun destroy() { hide(); dismissCalibration() }
}
