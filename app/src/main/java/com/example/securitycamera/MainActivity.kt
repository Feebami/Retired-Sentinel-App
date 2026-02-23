package com.example.securitycamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.securitycamera.databinding.ActivityMainBinding
import org.jcodec.api.android.AndroidSequenceEncoder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class BoundingBox(
    val x1: Float, val y1: Float, val x2: Float, val y2: Float,
    val cx: Float, val cy: Float, val w: Float, val h: Float,
    val confidence: Float?, val label: String
)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var videoEncoderExecutor: ExecutorService
    private lateinit var personDetector: PersonDetector
    private lateinit var faceDetector: FaceDetector
    private lateinit var identityDetector: IdentityDetector
    private lateinit var securityState: SecurityState

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    @Volatile private var shuttingDown = false

    private val frameBuffer = mutableListOf<Bitmap>()

    private val frameTimestamps = ArrayDeque<Long>()
    private var currentFps = 0.0
    private var maxFramesToKeep = 30
    private var lastAnalyzedTimestamp = 0L
    private val targetFps = 3.0
    private val frameIntervalMs = (1000 / targetFps).toLong()

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewFinder.scaleType = PreviewView.ScaleType.FIT_CENTER

        cameraExecutor = Executors.newSingleThreadExecutor()
        videoEncoderExecutor = Executors.newSingleThreadExecutor()
        personDetector = PersonDetector(this)
        faceDetector = FaceDetector()
        identityDetector = IdentityDetector(this)
        val safeNames = AppSettings.safeIdentities.toList()
        securityState = SecurityState(
            safeIdentities = safeNames,
            incidentTimeoutSec = AppSettings.incidentTimeoutSec,
            gracePeriodSec = AppSettings.gracePeriodSec,
            safeThreshold = AppSettings.safeThresholdFrames
        )

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(cameraProvider!!)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases(provider: ProcessCameraProvider) {
        // 1. Define how we want the camera resolution to behave
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO))
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .build()

        // 2. Set up the Preview (what the user sees on screen)
        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
        preview.surfaceProvider = binding.viewFinder.surfaceProvider

        // 3. Set up the Analyzer (the background stream of frames we process)
        imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalysis?.setAnalyzer(cameraExecutor, ::processImageFrame)

        // 4. Bind everything to the camera
        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
        } catch (exc: Exception) {
            Log.e("MainActivity", "Camera binding failed", exc)
        }
    }

    private fun processImageFrame(imageProxy: ImageProxy) {
        if (shuttingDown) {
            imageProxy.close()
            return
        }

        val currentTimestamp = System.currentTimeMillis()
        if (currentTimestamp - lastAnalyzedTimestamp < frameIntervalMs) {
            imageProxy.close()
            return
        }
        lastAnalyzedTimestamp = currentTimestamp

        var src: Bitmap? = null
        var rotated: Bitmap? = null

        try {
            val now = System.currentTimeMillis()
            frameTimestamps.addLast(now)
            while (now - frameTimestamps.first() > 10_000) {
                frameTimestamps.removeFirst()
            }
            currentFps = frameTimestamps.size / 10.0
            maxFramesToKeep = (AppSettings.gracePeriodSec * currentFps).toInt().coerceAtLeast(10)

            // 1. Get the bitmap and fix its rotation if necessary
            src = imageProxy.toBitmap()
            val rotation = imageProxy.imageInfo.rotationDegrees

            rotated = if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
            } else {
                src
            }

            val bufferCopy = rotated.copy(Bitmap.Config.ARGB_8888, false)
            frameBuffer.add(bufferCopy)
            while (frameBuffer.size > maxFramesToKeep) {
                val toRecycle = frameBuffer.removeAt(0)
                toRecycle.recycle()
            }

            // 2. Run detectors sequentially
            val personBoxes = personDetector.detect(rotated)
            val allBoxes = mutableListOf<BoundingBox>()
            val identities = mutableSetOf<String>()
            for (personBox in personBoxes) {
                val faces = faceDetector.detect(rotated, personBox)
                if (faces.isEmpty()) {
                    allBoxes.add(personBox)
                    identities.add("Unknown")
                } else {
                    val faceBox = faces.first()
                    val result = identityDetector.identify(rotated, faceBox)
                    allBoxes.add(personBox)
                    allBoxes.add(faceBox.copy(label = result.name, confidence = result.bestScore))
                    identities.add(result.name)
                }
            }

            val alert = securityState.update(identities)

            if (alert && frameBuffer.size > currentFps * 2) {
                val framesToEncode = frameBuffer.toList()
                frameBuffer.clear()

                runOnUiThread {
                    Toast.makeText(this@MainActivity, "ALERT! Saving video...", Toast.LENGTH_SHORT).show()
                }
                Log.i("MainActivity", "Intruder Alert triggered! Encoding video from ${framesToEncode.size} frames.")

                videoEncoderExecutor.execute {
                    encodeVideoFromFrames(framesToEncode)
                }
            }
            // 3. Draw to the screen
            if (!isFinishing && !isDestroyed) {
                val w = rotated.width
                val h = rotated.height
                val fpsDisplay = currentFps
                runOnUiThread {
                    binding.overlayView.setBoxes(allBoxes, w, h, fpsDisplay)
                }
            }

        } catch (t: Throwable) {
            Log.e("MainActivity", "Analyzer error", t)
        } finally {
            // 4. Clean up memory for this frame
            if (rotated !== src) rotated?.recycle()
            src?.recycle()
            imageProxy.close()
        }
    }

    private fun encodeVideoFromFrames(frames: List<Bitmap>) {
        if (frames.isEmpty()) return

        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val videoFile = File(
                getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "Intruder_Alert_$timeStamp.mp4"
            )
            val encoder = AndroidSequenceEncoder.createSequenceEncoder(videoFile, currentFps.toInt())

            for (frame in frames) {
                encoder.encodeImage(frame)
                frame.recycle()
            }

            encoder.finish()
            Log.i("MainActivity", "Video saved: ${videoFile.absolutePath}")

            runOnUiThread {
                Toast.makeText(this@MainActivity, "Video saved: ${videoFile.absolutePath}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Video encoding failed", e)
            frames.forEach { it.recycle() }
        }
    }

    override fun onResume() {
        super.onResume()
        personDetector.confThreshold = AppSettings.confThreshold
        identityDetector.recognitionThreshold = AppSettings.recognitionThreshold
    }

    override fun onStop() {
        super.onStop()
        shuttingDown = true
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        shuttingDown = true
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()

        cameraExecutor.execute {
            personDetector.close()
            faceDetector.close()
            identityDetector.close()
            frameBuffer.forEach { it.recycle() }
            frameBuffer.clear()
        }
        cameraExecutor.shutdown()
    }
}
