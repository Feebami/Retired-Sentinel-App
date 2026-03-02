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
import kotlin.math.roundToInt

class PersonDetector(
    private val context: Context,
    private val modelName: String = "yolo26n_float32.tflite"
) {

    private var interpreter: Interpreter? = null
    private var inputImageWidth = 0
    private var inputImageHeight = 0
    private var outputShape = intArrayOf()

    // Configuration
    var confThreshold = AppSettings.DEFAULT_CONF_THRESHOLD

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
    }

    fun detect(bitmap: Bitmap): List<BoundingBox> {
        if (interpreter == null) return emptyList()

        // 1. Preprocess Image
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputImageHeight, inputImageWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .add(CastOp(DataType.FLOAT32))
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        // 2. Run Inference
        val outputBuffer = TensorBuffer.createFixedSize(outputShape, DataType.FLOAT32)
        interpreter!!.run(tensorImage.buffer, outputBuffer.buffer.rewind())

        // 3. Post-process
        val outputArray = outputBuffer.floatArray
        val numDetections = outputShape[1] // 300
        val numValues = outputShape[2]      // 6

        // CORRECTION: Scale to ORIGINAL bitmap size, not model size
        val originalWidth = bitmap.width.toFloat()
        val originalHeight = bitmap.height.toFloat()

        val results = mutableListOf<BoundingBox>()

        for (i in 0 until numDetections) {
            val baseIndex = i * numValues

            // Extract values [x1, y1, x2, y2, conf, class]
            val x1Norm = outputArray[baseIndex]
            val y1Norm = outputArray[baseIndex + 1]
            val x2Norm = outputArray[baseIndex + 2]
            val y2Norm = outputArray[baseIndex + 3]
            val confidence = outputArray[baseIndex + 4]
            val classId = outputArray[baseIndex + 5].roundToInt()

            if (classId != 0 || confidence < confThreshold) continue

            // Scale normalized coordinates (0..1) to full image size (e.g. 640x480)
            val x1 = x1Norm * originalWidth
            val y1 = y1Norm * originalHeight
            val x2 = x2Norm * originalWidth
            val y2 = y2Norm * originalHeight

            val cx = (x1 + x2) / 2
            val cy = (y1 + y2) / 2
            val w = x2 - x1
            val h = y2 - y1

            results.add(BoundingBox(x1, y1, x2, y2, cx, cy, w, h, confidence, "Person"))
        }

        return results
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
