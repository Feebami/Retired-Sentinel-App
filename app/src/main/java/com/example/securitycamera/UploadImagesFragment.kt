package com.example.securitycamera

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.securitycamera.databinding.FragmentUploadImagesBinding
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UploadImagesFragment : Fragment() {

    private var _binding: FragmentUploadImagesBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: EnrolledPersonAdapter

    private val identityDetector get() =
        (requireActivity() as SetupActivity).identityDetector

    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { showNameDialog(it) }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUploadImagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = EnrolledPersonAdapter(
            onDelete = { name ->
                identityDetector.removeKnownFace(name)
                refreshList()
                Toast.makeText(requireContext(), "Removed '$name'", Toast.LENGTH_SHORT).show()
            }
        )
        binding.recyclerView.adapter = adapter

        binding.fabAdd.setOnClickListener {
            imagePicker.launch("image/*")
        }

        refreshList()
    }

    fun refreshList() {
        val persons = identityDetector.getKnownNames().map { name ->
            EnrolledPerson(name, identityDetector.getVectorCount(name))
        }
        adapter.submitList(persons)
        binding.emptyState.isVisible = persons.isEmpty()
        binding.recyclerView.isVisible = persons.isNotEmpty()
    }

    private fun showNameDialog(uri: Uri) {
        val dp16 = (16 * resources.displayMetrics.density).toInt()
        val editText = EditText(requireContext()).apply {
            hint = "Person's name"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val container = FrameLayout(requireContext()).apply {
            setPadding(dp16 * 2, dp16, dp16 * 2, 0)
            addView(editText)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Name this person")
            .setView(container)
            .setPositiveButton("Enroll") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) enrollPerson(name, uri)
                else Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun enrollPerson(name: String, uri: Uri) {
        binding.progressBar.isVisible = true
        binding.fabAdd.isEnabled = false

        lifecycleScope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    val fullBitmap = loadBitmap(uri)
                        ?: return@withContext "Failed to load image"

                    val faceBitmap = detectAndCropFace(fullBitmap)
                    fullBitmap.recycle()

                    if (faceBitmap == null)
                        return@withContext "No face detected. Please use a clear portrait photo."

                    val embedding = identityDetector.generateEmbedding(faceBitmap)
                    faceBitmap.recycle()

                    if (embedding == null)
                        return@withContext "Failed to generate face embedding"

                    identityDetector.addKnownFace(name, embedding)
                    null // null = success
                } catch (e: Exception) {
                    e.message ?: "Unknown error during enrollment"
                }
            }

            binding.progressBar.isVisible = false
            binding.fabAdd.isEnabled = true

            if (error == null) {
                refreshList()
                Toast.makeText(requireContext(), "Enrolled '$name'", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Runs ML Kit face detection on the full image and returns a cropped face bitmap. */
    private fun detectAndCropFace(bitmap: Bitmap): Bitmap? {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()
        val detector = FaceDetection.getClient(options)
        return detector.use { detector ->
            val faces = Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)))
            if (faces.isEmpty()) return null

            val rect = faces[0].boundingBox
            val margin = (minOf(rect.width(), rect.height()) * 0.2f).toInt()
            val x1 = (rect.left  - margin).coerceAtLeast(0)
            val y1 = (rect.top   - margin).coerceAtLeast(0)
            val x2 = (rect.right + margin).coerceAtMost(bitmap.width)
            val y2 = (rect.bottom + margin).coerceAtMost(bitmap.height)
            Bitmap.createBitmap(bitmap, x1, y1, x2 - x1, y2 - y1)
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(requireContext().contentResolver, uri)
                )
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
            }
            // TFLite requires ARGB_8888
            if (bmp.config == Bitmap.Config.ARGB_8888) bmp
            else bmp.copy(Bitmap.Config.ARGB_8888, false).also { bmp.recycle() }
        } catch (_: Exception) {
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
