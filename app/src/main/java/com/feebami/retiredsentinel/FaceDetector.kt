package com.feebami.retiredsentinel

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceDetector as MLKitFaceDetector

class FaceDetector {

    private val detector: MLKitFaceDetector

    init {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.1f)
            .build()
        detector = FaceDetection.getClient(options)
    }

    /**
     * Detects faces within the person-cropped region of the full rotatedBitmap.
     * Returns BoundingBoxes in full-image coordinate space, labeled "Face".
     */
    fun detect(bitmap: Bitmap, personBox: BoundingBox): BoundingBox? {
        val (croppedBitmap, offset) = cropPersonSquare(bitmap, personBox)
        val offsetX = offset[0]
        val offsetY = offset[1]

        val inputImage = InputImage.fromBitmap(croppedBitmap, 0)
        // Tasks.await() is safe here because we're always called from a background thread
        val faces = Tasks.await(detector.process(inputImage))

        val largestFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        if (largestFace == null) return null
        val rect = largestFace.boundingBox
        val x1 = (rect.left + offsetX).toFloat()
        val y1 = (rect.top + offsetY).toFloat()
        val x2 = (rect.right + offsetX).toFloat()
        val y2 = (rect.bottom + offsetY).toFloat()
        val w = x2 - x1
        val h = y2 - y1
        val cx = (x1 + x2) / 2
        val cy = (y1 + y2) / 2
        if (croppedBitmap != bitmap) croppedBitmap.recycle()
        return BoundingBox(x1, y1, x2, y2, cx, cy, w, h, null, "Face")
    }

    /**
     * Replicates the Python square-crop logic:
     *   - Portrait person box (h > w): keep width, shrink height to width
     *   - Landscape person box (w >= h): keep height, center-crop width to height
     *
     * Returns the cropped Bitmap and the (x1, y1) offset into the original bitmap.
     */
    private fun cropPersonSquare(bitmap: Bitmap, personBox: BoundingBox): Pair<Bitmap, IntArray> {
        var x1 = personBox.x1.toInt()
        var y1 = personBox.y1.toInt()
        var x2 = personBox.x2.toInt()
        var y2 = personBox.y2.toInt()

        val boxW = x2 - x1
        val boxH = y2 - y1

        if (boxH > boxW) {
            // Portrait: square = width × width, anchored at top
            y2 = y1 + boxW
        } else {
            // Landscape/square: square = height × height, centered horizontally
            val side = boxH
            val cx   = x1 + boxW / 2
            x1 = cx - side / 2
            x2 = cx + side / 2
        }

        // Clamp to valid bitmap bounds
        x1 = x1.coerceAtLeast(0)
        y1 = y1.coerceAtLeast(0)
        x2 = x2.coerceAtMost(bitmap.width)
        y2 = y2.coerceAtMost(bitmap.height)

        val cropW = x2 - x1
        val cropH = y2 - y1
        if (cropW <= 0 || cropH <= 0) return Pair(bitmap, intArrayOf(0, 0))

        return Pair(
            Bitmap.createBitmap(bitmap, x1, y1, cropW, cropH),
            intArrayOf(x1, y1)
        )
    }

    fun close() {
        detector.close()
    }
}
