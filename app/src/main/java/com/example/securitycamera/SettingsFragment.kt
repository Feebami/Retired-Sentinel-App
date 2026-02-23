package com.example.securitycamera

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.securitycamera.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    // We need access to IdentityDetector to validate the safe names
    private val identityDetector get() = (requireActivity() as SetupActivity).identityDetector

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fun snapToStep(value: Float, step: Float): Float {
            return Math.round(value / step) * step
        }

        fun validateTimings() {
            val timeout = binding.sliderIncidentTimeout.value
            val grace = binding.sliderGracePeriod.value

            if (timeout < grace) {
                binding.textTimingError.visibility = View.VISIBLE
            } else {
                binding.textTimingError.visibility = View.GONE
            }
        }

        // 1. Load initial values safely into UI
        try {
            binding.sliderConfidence.value = snapToStep(AppSettings.confThreshold, 0.05f)
            binding.sliderThreshold.value = snapToStep(AppSettings.recognitionThreshold, 0.05f)
            binding.sliderIncidentTimeout.value = snapToStep(AppSettings.incidentTimeoutSec.toFloat(), 5f)
            binding.sliderGracePeriod.value = snapToStep(AppSettings.gracePeriodSec.toFloat(), 3f)

            // Populate EditText with comma-separated list
            binding.editSafeIdentities.setText(AppSettings.safeIdentities.joinToString(", "))
        } catch (e: Exception) {
            // Fallback
            binding.sliderConfidence.value = AppSettings.DEFAULT_CONF_THRESHOLD
            binding.sliderThreshold.value = AppSettings.DEFAULT_RECOG_THRESHOLD
            binding.sliderIncidentTimeout.value = AppSettings.DEFAULT_INCIDENT_TIMEOUT.toFloat()
            binding.sliderGracePeriod.value = AppSettings.DEFAULT_GRACE_PERIOD.toFloat()
        }

        validateTimings()

        // 2. Setup Slider Listeners
        binding.sliderConfidence.addOnChangeListener { _, value, fromUser ->
            if (fromUser) AppSettings.saveConfThreshold(requireContext(), value)
        }

        binding.sliderThreshold.addOnChangeListener { _, value, fromUser ->
            if (fromUser) AppSettings.saveRecognitionThreshold(requireContext(), value)
        }

        binding.sliderIncidentTimeout.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                AppSettings.saveIncidentTimeout(requireContext(), value.toLong())
                validateTimings()
            }
        }

        binding.sliderGracePeriod.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                AppSettings.saveGracePeriod(requireContext(), value.toLong())
                validateTimings()
            }
        }

        // 3. Setup Safe Identities validation and saving
        binding.editSafeIdentities.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val input = s?.toString() ?: ""

                // Parse input: split by comma, trim whitespace, ignore empty strings
                val enteredNames = input.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()

                val enrolledNames = identityDetector.getKnownNames()

                // Find names that the user typed which are NOT in the database
                val invalidNames = enteredNames.filter { it !in enrolledNames }

                if (invalidNames.isNotEmpty()) {
                    binding.textSafeIdentitiesError.visibility = View.VISIBLE
                    binding.textSafeIdentitiesError.text =
                        "Warning: The following names are not enrolled: ${invalidNames.joinToString(", ")}"
                } else {
                    binding.textSafeIdentitiesError.visibility = View.GONE
                }

                // Save the entered names (you might only want to save the valid ones,
                // but saving all of them allows the user to enroll them later without retyping)
                AppSettings.saveSafeIdentities(requireContext(), enteredNames)
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
