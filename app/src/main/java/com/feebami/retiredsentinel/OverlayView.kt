package com.feebami.retiredsentinel

import android.annotation.SuppressLint
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

    private val personPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val facePaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val personTextPaint = Paint().apply {
        color = Color.RED
        textSize = 50f
        style = Paint.Style.FILL
    }

    private val faceTextPaint = Paint().apply {
        color = Color.BLUE
        textSize = 50f
        style = Paint.Style.FILL
    }

    private val fpsTextPaint = Paint().apply {
        color = Color.GREEN
        textSize = 60f
        style = Paint.Style.FILL
        textAlign = Paint.Align.RIGHT
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private val thermTextPaint = Paint().apply {
        color = Color.GREEN
        textSize = 60f
        style = Paint.Style.FILL
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private val logTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        style = Paint.Style.FILL
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private var boxes: List<BoundingBox> = emptyList()
    private var imageWidth = 0
    private var imageHeight = 0
    private var logList: List<String> = emptyList()
    private var currentFps = 0.0
    private var thermalHeadroom: Int? = null

    fun setThermalHeadroom(value: Int?) {
        thermalHeadroom = value
        invalidate()
    }

    fun setOverlay(newBoxes: List<BoundingBox>, imgWidth: Int, imgHeight: Int, fps: Double = 0.0) {
        boxes = newBoxes
        imageWidth = imgWidth
        imageHeight = imgHeight
        currentFps = fps
        invalidate()
    }

    fun addLogEntry(entry: String) {
        // Prepend entry with timestamp
        val timestamp = android.text.format.DateFormat.format("HH:mm:ss", java.util.Date())
        val logEntry = "[$timestamp] $entry"
        logList = logList + logEntry
        if (logList.size > 10) {
            logList = logList.takeLast(10)
        }
    }

    @SuppressLint("DefaultLocale")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width > 0 && currentFps > 0) {
            canvas.drawText(String.format("%.1f FPS", currentFps), width - 20f, 60f, fpsTextPaint)
        }
        val th = thermalHeadroom
        val thermText = if (th == null) "Thermal: N/A" else String.format("Thermal: %d", th)
        canvas.drawText(thermText, 20f, 60f, thermTextPaint)

        if (logList.isNotEmpty()) {
            val logStartY = height - 20f
            for ((index, log) in logList.reversed().withIndex()) {
                canvas.drawText(log, 20f, logStartY - index * 50f, logTextPaint)
            }
        }

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

            val boxPaint = if (box.label == "Person") personPaint else facePaint
            val textPaint = if (box.label == "Person") personTextPaint else faceTextPaint

            canvas.drawRect(displayX1, displayY1, displayX2, displayY2, boxPaint)

            // Draw confidence score
            val label = if (box.confidence != null) {
                "${box.label} ${(box.confidence * 100).toInt()}%"
            } else {
                box.label
            }
            canvas.drawText(label, displayX1, displayY1 - 10, textPaint)
        }
    }
}
