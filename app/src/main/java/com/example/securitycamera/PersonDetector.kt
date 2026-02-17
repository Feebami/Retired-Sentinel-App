package com.example.securitycamera

import android.content.Context
import android.graphics.Bitmap
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
    private val confThreshold = 0.5f
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

        // YOLOv8 output is usually [1, 4+classes, 2100] (transposed) or [1, 2100, 4+classes]
        // We will check this dynamically.
        val outputTensor = interpreter!!.getOutputTensor(0)
        outputShape = outputTensor.shape()
    }

    fun detect(bitmap: Bitmap): List<BoundingBox> {
        if (interpreter == null) return emptyList()

        // 1. Preprocess Image
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputImageHeight, inputImageWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f)) // Normalize to [0,1]
            .add(CastOp(DataType.FLOAT32))
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        // 2. Run Inference
        // Output buffer: [1, 5, 2100] for 1 class (x, y, w, h, conf)
        // Adjust '5' depending on your class count (4 box + N classes)
        // If shape is [1, 5, 2100], we need to create buffer based on outputShape
        val outputBuffer = TensorBuffer.createFixedSize(outputShape, DataType.FLOAT32)
        interpreter!!.run(tensorImage.buffer, outputBuffer.buffer.rewind())

        // 3. Post-process (The hard part)
        val outputArray = outputBuffer.floatArray
        // Reshape to access easily: standard YOLOv8 export is [Batch, Channels, Anchors] -> [1, 5, 2100]
        val channels = outputShape[1] // 5 (x,y,w,h,conf)
        val anchors = outputShape[2]  // 2100

        val boxes = mutableListOf<BoundingBox>()

        for (i in 0 until anchors) {
            // In the flattened array, accessing [0, channel_j, anchor_i]
            // Stride is anchors. index = c * anchors + i

            // Confidence (Assuming class 0 is Person and it's at index 4)
            // Format: x, y, w, h, class0_conf, class1_conf...
            val confidence = outputArray[4 * anchors + i]

            if (confidence > confThreshold) {
                val cx = outputArray[0 * anchors + i]
                val cy = outputArray[1 * anchors + i]
                val w = outputArray[2 * anchors + i]
                val h = outputArray[3 * anchors + i]

                val x1 = cx - w / 2
                val y1 = cy - h / 2
                val x2 = cx + w / 2
                val y2 = cy + h / 2

                boxes.add(BoundingBox(x1, y1, x2, y2, cx, cy, w, h, confidence, 0))
            }
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
}
