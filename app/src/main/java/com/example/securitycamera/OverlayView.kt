package com.example.securitycamera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val boxPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private var boxes: List<BoundingBox> = emptyList()

    fun setBoxes(newBoxes: List<BoundingBox>) {
        boxes = newBoxes
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val scaleX = width / 320f
        val scaleY = height / 320f

        for (box in boxes) {
            val left = box.x1 * scaleX
            val top = box.y1 * scaleY
            val right = box.x2 * scaleX
            val bottom = box.y2 * scaleY
            canvas.drawRect(left, top, right, bottom, boxPaint)
        }
    }
}
