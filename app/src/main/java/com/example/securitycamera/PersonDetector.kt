package com.example.securitycamera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.roundToInt
import android.graphics.Matrix

data class BoundingBox(
    val x1: Float, val y1: Float, val x2: Float, val y2: Float,
    val cx: Float, val cy: Float, val w: Float, val h: Float,
    val confidence: Float, val classId: Int
)

class PersonDetector(
    private val context: Context,
    private val modelName: String = "yolo26n_float32.tflite"
) {

    private var interpreter: Interpreter? = null
    private var inputImageWidth = 0
    private var inputImageHeight = 0
    private var outputShape = intArrayOf()

    // Configuration
    private val confThreshold = 0.3f
    private val iouThreshold = 0.5f

    init {
        setupInterpreter()
    }

    private fun setupInterpreter() {
        val options = Interpreter.Options()
        options.setNumThreads(4)

        val modelFile = loadModelFile(context.assets, modelName)
        interpreter = Interpreter(modelFile, options)

        val inputShape = interpreter!!.getInputTensor(0).shape() // [1, 320, 320, 3]
        inputImageWidth = inputShape[1]
        inputImageHeight = inputShape[2]

        val outputTensor = interpreter!!.getOutputTensor(0)
        outputShape = outputTensor.shape() // [1, 300, 6]

        Log.d("PersonDetector", "Input: ${inputShape.contentToString()}, Output: ${outputShape.contentToString()}")
    }

    fun detect(bitmap: Bitmap, rotationDegrees: Int): List<BoundingBox> {
        if (interpreter == null) return emptyList()

        val rotatedBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }

        // 1. Preprocess Image
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputImageHeight, inputImageWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .add(CastOp(DataType.FLOAT32))
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(rotatedBitmap)
        tensorImage = imageProcessor.process(tensorImage)

        // 2. Run Inference
        val outputBuffer = TensorBuffer.createFixedSize(outputShape, DataType.FLOAT32)
        interpreter!!.run(tensorImage.buffer, outputBuffer.buffer.rewind())

        // 3. Post-process
        val outputArray = outputBuffer.floatArray
        val numDetections = outputShape[1] // 300
        val numValues = outputShape[2]      // 6

        val boxes = mutableListOf<BoundingBox>()

        // CORRECTION: Scale to ORIGINAL bitmap size, not model size
        val originalWidth = rotatedBitmap.width.toFloat()
        val originalHeight = rotatedBitmap.height.toFloat()

        for (i in 0 until numDetections) {
            val baseIndex = i * numValues

            // Extract values [x1, y1, x2, y2, conf, class]
            val x1Norm = outputArray[baseIndex]
            val y1Norm = outputArray[baseIndex + 1]
            val x2Norm = outputArray[baseIndex + 2]
            val y2Norm = outputArray[baseIndex + 3]
            val confidence = outputArray[baseIndex + 4]
            val classId = outputArray[baseIndex + 5].roundToInt()

            if (classId != 0) continue
            if (confidence <= confThreshold) continue

            // Scale normalized coordinates (0..1) to full image size (e.g. 640x480)
            val x1 = x1Norm * originalWidth
            val y1 = y1Norm * originalHeight
            val x2 = x2Norm * originalWidth
            val y2 = y2Norm * originalHeight

            val cx = (x1 + x2) / 2
            val cy = (y1 + y2) / 2
            val w = x2 - x1
            val h = y2 - y1

            boxes.add(BoundingBox(x1, y1, x2, y2, cx, cy, w, h, confidence, classId))
        }

        if (rotatedBitmap != bitmap) {
            rotatedBitmap.recycle()
        }

        return applyNMS(boxes)
    }

    private fun applyNMS(boxes: List<BoundingBox>): List<BoundingBox> {
        val sortedBoxes = boxes.sortedByDescending { it.confidence }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBox>()

        while (sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes.removeAt(0)
            selectedBoxes.add(first)

            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (calculateIoU(first, next) >= iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return selectedBoxes
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)

        val intersectionArea = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val box1Area = box1.w * box1.h
        val box2Area = box2.w * box2.h
        val unionArea = box1Area + box2Area - intersectionArea

        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }

    private fun loadModelFile(assetManager: android.content.res.AssetManager, modelPath: String): MappedByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
