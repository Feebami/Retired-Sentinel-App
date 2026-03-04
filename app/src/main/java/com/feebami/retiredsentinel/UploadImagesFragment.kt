package com.feebami.retiredsentinel

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
import com.feebami.retiredsentinel.databinding.FragmentUploadImagesBinding
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

    private var targetNameForAdd: String? = null

    private val multiImagePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val targetName = targetNameForAdd
            if (targetName != null) {
                // Appending photos to an EXISTING person
                enrollPersons(targetName, uris)
            } else {
                // Creating a NEW person
                showNameDialog(uris)
            }
        }
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
            },
            onAdd = { name ->
                targetNameForAdd = name
                multiImagePicker.launch("image/*")
            }
        )
        binding.recyclerView.adapter = adapter

        binding.fabAdd.setOnClickListener {
            targetNameForAdd = null
            multiImagePicker.launch("image/*")
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

    private fun showNameDialog(uris: List<Uri>) {
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
                if (name.isNotEmpty()) enrollPersons(name, uris)
                else Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun enrollPersons(name: String, uris: List<Uri>) {
        binding.progressBar.isVisible = true
        binding.fabAdd.isEnabled = false

        lifecycleScope.launch {
            var successCount = 0
            var failCount = 0

            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    try {
                        val fullBitmap = loadBitmap(uri)
                        if (fullBitmap != null) {
                            val faceBitmap = detectAndCropFace(fullBitmap)
                            fullBitmap.recycle()

                            if (faceBitmap != null) {
                                val embedding = identityDetector.generateEmbedding(faceBitmap)
                                faceBitmap.recycle()

                                if (embedding != null) {
                                    identityDetector.addKnownFace(name, embedding)
                                    successCount++
                                } else {
                                    failCount++
                                }
                            } else {
                                failCount++
                            }
                        } else {
                            failCount++
                        }
                    } catch (_: Exception) {
                        failCount++
                    }
                }
            }

            binding.progressBar.isVisible = false
            binding.fabAdd.isEnabled = true
            refreshList()

            if (failCount == 0) {
                Toast.makeText(requireContext(), "Enrolled $successCount photo(s) for '$name'", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Success: $successCount, Failed: $failCount. Make sure faces are clear.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Runs ML Kit face detection on the full image and returns a cropped face bitmap. */
    private fun detectAndCropFace(bitmap: Bitmap): Bitmap? {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        val detector = FaceDetection.getClient(options)
        return detector.use { detector ->
            val faces = Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)))
            if (faces.isEmpty()) return null

            val largestFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: faces[0]
            val rect = largestFace.boundingBox
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
