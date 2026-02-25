package com.example.securitycamera

import android.os.SystemClock
import android.util.Log

class SecurityState(
    private val safeIdentities: List<String>,
    incidentTimeoutSec: Long,
    gracePeriodSec: Long,
) {
    companion object {
        private const val TAG = "SecurityState"
    }

    private val incidentTimeoutMs = incidentTimeoutSec * 1000L
    private val gracePeriodMs = gracePeriodSec * 1000L
    private var incidentStartTime = 0L
    private var lastSightTime = 0L
    private val safeThreshold = 3
    private var safeIdentitySeen = false
    private var alertedThisIncident = false

    // Tracks how many times each identity has been seen during the current incident
    private val identityCounts = mutableMapOf<String, Int>()

    init {
        resetIncident()
    }

    private fun resetIncident() {
        incidentStartTime = 0L
        lastSightTime = 0L
        safeIdentitySeen = false
        identityCounts.clear()
        alertedThisIncident = false
    }

    /**
     * Updates state based on current frame identities.
     * Returns true if an Intruder Alert should be triggered right now.
     */
    fun update(currentIdentities: Set<String>): Boolean {
        val now = SystemClock.elapsedRealtime()

        // 1. Incident Timeout check
        if (lastSightTime > 0 && currentIdentities.isEmpty()) {
            if ((now - lastSightTime) > incidentTimeoutMs) {
                Log.i(TAG, "Room empty for ${incidentTimeoutMs / 1000}s. Ending incident.")
                resetIncident()
                return false
            }
        }

        // 2. Update Timings & Check for Safe People
        if (currentIdentities.isNotEmpty()) {
            lastSightTime = now
            if (incidentStartTime == 0L) {
                Log.i(TAG, "New incident started.")
                incidentStartTime = now
            }

            // If we haven't confirmed a safe identity yet, update counts and check threshold
            if (!safeIdentitySeen) {
                for (identity in currentIdentities) {
                    if (identity in safeIdentities) {
                        // Increment the count for this specific identity
                        val currentCount = (identityCounts[identity] ?: 0) + 1
                        identityCounts[identity] = currentCount

                        // Check if they have reached the required robustness threshold
                        if (currentCount >= safeThreshold) {
                            Log.i(TAG, "Safe identity '$identity' confirmed ($currentCount frames). Incident marked safe.")
                            safeIdentitySeen = true
                            break
                        }
                    }
                }
            }
        }

        // 3. Grace Period
        if ((now - incidentStartTime) < gracePeriodMs) {
            return false
        }

        // 4. Intruder Alert Logic
        // We are past the grace period. If no safe identity has reached the threshold, it's an intruder.
        if (!safeIdentitySeen && incidentStartTime > 0L && !alertedThisIncident) {
            Log.w(TAG, "!!! Intruder Alert: No safe identity confirmed within grace period !!!")
            alertedThisIncident = true
            return true
        }
        return false
    }
}
