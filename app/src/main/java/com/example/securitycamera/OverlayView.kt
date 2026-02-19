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
        strokeWidth = 8f
    }

    private val textPaint = Paint().apply {
        color = Color.RED
        textSize = 40f
        style = Paint.Style.FILL
    }

    private var boxes: List<BoundingBox> = emptyList()
    private var imageWidth = 0
    private var imageHeight = 0

    fun setBoxes(newBoxes: List<BoundingBox>, imgWidth: Int, imgHeight: Int) {
        boxes = newBoxes
        imageWidth = imgWidth
        imageHeight = imgHeight
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (boxes.isEmpty() || imageWidth == 0 || imageHeight == 0) return

        // Calculate how the image is scaled in the view
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
        val viewAspect = viewWidth / viewHeight

        val scaleFactor: Float
        val offsetX: Float
        val offsetY: Float

        if (imageAspect > viewAspect) {
            // Image is wider - fit to width, letterbox top/bottom
            scaleFactor = viewWidth / imageWidth
            offsetX = 0f
            offsetY = (viewHeight - imageHeight * scaleFactor) / 2f
        } else {
            // Image is taller - fit to height, letterbox left/right
            scaleFactor = viewHeight / imageHeight
            offsetX = (viewWidth - imageWidth * scaleFactor) / 2f
            offsetY = 0f
        }

        for (box in boxes) {
            // Boxes are in 320x320 coordinate space
            // Scale them to the displayed image size
            val displayX1 = box.x1 * scaleFactor + offsetX
            val displayY1 = box.y1 * scaleFactor + offsetY
            val displayX2 = box.x2 * scaleFactor + offsetX
            val displayY2 = box.y2 * scaleFactor + offsetY

            canvas.drawRect(displayX1, displayY1, displayX2, displayY2, boxPaint)

            // Draw confidence score
            val label = "%.2f".format(box.confidence)
            canvas.drawText(label, displayX1, displayY1 - 10, textPaint)
        }
    }
}
