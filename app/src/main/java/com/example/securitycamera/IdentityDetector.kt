package com.example.securitycamera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
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
import kotlin.math.sqrt

data class IdentityResult(
    val name: String,
    val bestScore: Float?,               // cosine similarity to the best match
    val allScores: Map<String, Float>?   // similarity to every known identity
)

class IdentityDetector(
    private val context: Context,
    private val modelName: String = "facenet_512.tflite"
) {

    companion object {
        private const val TAG = "IdentityDetector"
        private const val INPUT_SIZE = 160
        // Margin added around the ML Kit face box before cropping.
        // ML Kit boxes are tight; FaceNet accuracy improves with a little context.
        private const val FACE_MARGIN = 0.2f
        private const val VECTORS_FILE = "face_vectors.json"
    }

    private var interpreter: Interpreter? = null
    // Read dynamically from model output shape — works for both 128-dim and 512-dim models
    private var embeddingSize: Int = 512
    private var knownFaces: MutableMap<String, MutableList<FloatArray>> = mutableMapOf()

    var recognitionThreshold: Float = AppSettings.DEFAULT_RECOG_THRESHOLD

    init {
        setupInterpreter()
        loadVectors()
    }

    private fun setupInterpreter() {
        try {
            val options = Interpreter.Options().apply { setNumThreads(4) }
            interpreter = Interpreter(loadModelFile(modelName), options)
            embeddingSize = interpreter!!.getOutputTensor(0).shape()[1]
            Log.d(TAG, "Loaded '$modelName': input=${interpreter!!.getInputTensor(0).shape().contentToString()}, embedding_dims=$embeddingSize")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load FaceNet model '$modelName'. Did you add it to assets?", e)
        }
    }

    // ---- Public API ----

    /**
     * Identify a person given the full rotated bitmap and a face BoundingBox
     * (in full-image coordinates, as returned by FaceDetector).
     */
    fun identify(bitmap: Bitmap, faceBox: BoundingBox): IdentityResult {
        val faceBitmap = cropFaceWithMargin(bitmap, faceBox)
        val embedding = generateEmbedding(faceBitmap)
        if (faceBitmap != bitmap) faceBitmap.recycle()
        if (embedding == null) return IdentityResult("Unknown", null, null)
        return matchEmbedding(embedding)
    }

    /**
     * Generate an L2-normalized FaceNet embedding from a pre-cropped face bitmap.
     * Exposed publicly for enrollment: capture a face, call this, then addKnownFace().
     */
    fun generateEmbedding(faceBitmap: Bitmap): FloatArray? {
        val interpreter = this@IdentityDetector.interpreter ?: return null

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(127.5f, 128.0f)) // (pixel − 127.5) / 128 → [−1, 1]
            .add(CastOp(DataType.FLOAT32))
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(faceBitmap)
        tensorImage = imageProcessor.process(tensorImage)

        val outputBuffer = TensorBuffer.createFixedSize(
            intArrayOf(1, embeddingSize), DataType.FLOAT32
        )
        interpreter.run(tensorImage.buffer, outputBuffer.buffer.rewind())

        return l2Normalize(outputBuffer.floatArray)
    }

    /**
     * Register a new embedding under a given name.
     * Multiple embeddings per person are supported — more = better accuracy.
     * Persists to internal storage automatically.
     */
    fun addKnownFace(name: String, embedding: FloatArray) {
        knownFaces.getOrPut(name) { mutableListOf() }.add(embedding)
        saveVectors()
        Log.d(TAG, "Enrolled '$name': ${knownFaces[name]?.size} vector(s) total")
    }

    fun removeKnownFace(name: String) {
        knownFaces.remove(name)
        saveVectors()
    }

    fun getKnownNames(): List<String> = knownFaces.keys.toList()
    fun getVectorCount(name: String): Int = knownFaces[name]?.size ?: 0

    // ---- Matching ----

    private fun matchEmbedding(embedding: FloatArray): IdentityResult {
        if (knownFaces.isEmpty()) return IdentityResult("Unknown", null, null)

        val scores = mutableMapOf<String, Float>()
        var bestName = "Unknown"
        var bestScore = -1f

        for ((name, vectors) in knownFaces) {
            // Best cosine similarity across all reference vectors for this person
            val maxSim = vectors.maxOf { cosineSimilarity(embedding, it) }
            scores[name] = maxSim
            if (maxSim > bestScore) {
                bestScore = maxSim
                bestName = name
            }
        }

        return IdentityResult(
            name = if (bestScore >= recognitionThreshold) bestName else "Unknown",
            bestScore = bestScore,
            allScores = scores
        )
    }

    // ---- Cropping ----

    private fun cropFaceWithMargin(bitmap: Bitmap, faceBox: BoundingBox): Bitmap {
        val mx = faceBox.w * FACE_MARGIN
        val my = faceBox.h * FACE_MARGIN
        val x1 = (faceBox.x1 - mx).toInt().coerceAtLeast(0)
        val y1 = (faceBox.y1 - my).toInt().coerceAtLeast(0)
        val x2 = (faceBox.x2 + mx).toInt().coerceAtMost(bitmap.width)
        val y2 = (faceBox.y2 + my).toInt().coerceAtMost(bitmap.height)
        val w = (x2 - x1).coerceAtLeast(1)
        val h = (y2 - y1).coerceAtLeast(1)
        return Bitmap.createBitmap(bitmap, x1, y1, w, h)
    }

    // ---- Persistence ----

    private fun saveVectors() {
        try {
            val root = JSONObject()
            for ((name, vectors) in knownFaces) {
                val vecArray = JSONArray()
                for (vec in vectors) {
                    val floatArr = JSONArray()
                    vec.forEach { floatArr.put(it.toDouble()) }
                    vecArray.put(floatArr)
                }
                root.put(name, vecArray)
            }
            context.openFileOutput(VECTORS_FILE, Context.MODE_PRIVATE).use {
                it.write(root.toString().toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save face vectors", e)
        }
    }

    private fun loadVectors() {
        try {
            val json = context.openFileInput(VECTORS_FILE).bufferedReader().readText()
            val root = JSONObject(json)
            knownFaces.clear()
            for (name in root.keys()) {
                val vecArray = root.getJSONArray(name)
                val vectors = mutableListOf<FloatArray>()
                for (i in 0 until vecArray.length()) {
                    val arr = vecArray.getJSONArray(i)
                    vectors.add(FloatArray(arr.length()) { j -> arr.getDouble(j).toFloat() })
                }
                knownFaces[name] = vectors
            }
            Log.d(TAG, "Loaded known faces: ${knownFaces.keys.toList()}")
        } catch (_: java.io.FileNotFoundException) {
            Log.w(TAG, "No saved face vectors found — starting fresh")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load face vectors", e)
        }
    }

    // ---- Math ----

    private fun l2Normalize(vec: FloatArray): FloatArray {
        val norm = sqrt(vec.sumOf { (it * it).toDouble() }.toFloat())
        return if (norm > 0f) FloatArray(vec.size) { i -> vec[i] / norm } else vec
    }

    /** Cosine similarity of two already-L2-normalized vectors = their dot product. */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float =
        a.indices.sumOf { (a[it] * b[it]).toDouble() }.toFloat()

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fd = context.assets.openFd(modelPath)
        return FileInputStream(fd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength
        )
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
