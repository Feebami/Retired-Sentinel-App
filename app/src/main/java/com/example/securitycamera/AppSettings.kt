package com.example.securitycamera

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlin.math.roundToInt

object AppSettings {
    private const val PREFS_NAME = "security_camera_prefs"
    private const val KEY_CONF_THRESHOLD = "conf_threshold"
    private const val KEY_RECOG_THRESHOLD = "recognition_threshold"

    // New Keys for Security State
    private const val KEY_INCIDENT_TIMEOUT = "incident_timeout"
    private const val KEY_GRACE_PERIOD = "grace_period"
    private const val KEY_SAFE_IDENTITIES = "safe_identities"
    private const val KEY_TELEGRAM_ENABLED = "telegram_enabled"
    private const val KEY_TELEGRAM_TOKEN = "telegram_token"
    private const val KEY_TELEGRAM_CHAT_ID = "telegram_chat_id"
    private const val KEY_TARGET_FPS = "target_fps"
    private const val KEY_RESOLUTION = "resolution"

    const val DEFAULT_CONF_THRESHOLD = 0.3f
    const val DEFAULT_RECOG_THRESHOLD = 0.6f

    // Defaults for Security State
    const val DEFAULT_INCIDENT_TIMEOUT = 10L // seconds
    const val DEFAULT_GRACE_PERIOD = 3L      // seconds

    // Defaults for Camera
    const val DEFAULT_TARGET_FPS = 3
    const val DEFAULT_RESOLUTION = "480p"

    var confThreshold: Float = DEFAULT_CONF_THRESHOLD
        private set
    var recognitionThreshold: Float = DEFAULT_RECOG_THRESHOLD
        private set

    // New In-memory values
    var incidentTimeoutSec: Long = DEFAULT_INCIDENT_TIMEOUT
        private set
    var gracePeriodSec: Long = DEFAULT_GRACE_PERIOD
        private set
    var safeIdentities: Set<String> = emptySet()
        private set
    var telegramEnabled: Boolean = false
        private set
    var targetFps: Int = DEFAULT_TARGET_FPS
        private set
    var resolution: String = DEFAULT_RESOLUTION
        private set

    fun load(context: Context) {
        val p = prefs(context)
        try {
            var conf = p.getFloat(KEY_CONF_THRESHOLD, DEFAULT_CONF_THRESHOLD)
            var recog = p.getFloat(KEY_RECOG_THRESHOLD, DEFAULT_RECOG_THRESHOLD)
            conf = (conf * 100).roundToInt() / 100f
            recog = (recog * 100).roundToInt() / 100f
            confThreshold = conf.coerceIn(0.1f, 0.9f)
            recognitionThreshold = recog.coerceIn(0.1f, 1.0f)

            // Load Security Settings
            incidentTimeoutSec = p.getLong(KEY_INCIDENT_TIMEOUT, DEFAULT_INCIDENT_TIMEOUT)
            gracePeriodSec = p.getLong(KEY_GRACE_PERIOD, DEFAULT_GRACE_PERIOD)
            safeIdentities = p.getStringSet(KEY_SAFE_IDENTITIES, emptySet()) ?: emptySet()

            // Load Telegram Settings
            telegramEnabled = p.getBoolean(KEY_TELEGRAM_ENABLED, false)

            // Load Camera Settings
            targetFps = p.getInt(KEY_TARGET_FPS, DEFAULT_TARGET_FPS)
            resolution = p.getString(KEY_RESOLUTION, DEFAULT_RESOLUTION) ?: DEFAULT_RESOLUTION

        } catch (_: Exception) {
            confThreshold = DEFAULT_CONF_THRESHOLD
            recognitionThreshold = DEFAULT_RECOG_THRESHOLD
            incidentTimeoutSec = DEFAULT_INCIDENT_TIMEOUT
            gracePeriodSec = DEFAULT_GRACE_PERIOD
            safeIdentities = emptySet()
            targetFps = DEFAULT_TARGET_FPS
            resolution = DEFAULT_RESOLUTION
        }
    }

    fun saveConfThreshold(context: Context, value: Float) {
        confThreshold = value
        prefs(context).edit { putFloat(KEY_CONF_THRESHOLD, value) }
    }

    fun saveRecognitionThreshold(context: Context, value: Float) {
        recognitionThreshold = value
        prefs(context).edit { putFloat(KEY_RECOG_THRESHOLD, value) }
    }

    // New Save Methods
    fun saveIncidentTimeout(context: Context, valueSec: Long) {
        incidentTimeoutSec = valueSec
        prefs(context).edit { putLong(KEY_INCIDENT_TIMEOUT, valueSec) }
    }

    fun saveGracePeriod(context: Context, valueSec: Long) {
        gracePeriodSec = valueSec
        prefs(context).edit { putLong(KEY_GRACE_PERIOD, valueSec) }
    }

    fun saveSafeIdentities(context: Context, identities: Set<String>) {
        safeIdentities = identities
        prefs(context).edit { putStringSet(KEY_SAFE_IDENTITIES, identities) }
    }

    fun saveTelegramEnabled(context: Context, enabled: Boolean) {
        telegramEnabled = enabled
        prefs(context).edit { putBoolean(KEY_TELEGRAM_ENABLED, enabled) }
    }

    fun saveTelegramToken(context: Context, token: String) {
        prefs(context).edit { putString(KEY_TELEGRAM_TOKEN, token) }
    }

    fun saveTelegramChatId(context: Context, chatId: String) {
        prefs(context).edit { putString(KEY_TELEGRAM_CHAT_ID, chatId) }
    }

    fun saveTargetFps(context: Context, fps: Int) {
        targetFps = fps
        prefs(context).edit { putInt(KEY_TARGET_FPS, fps) }
    }

    fun saveResolution(context: Context, res: String) {
        resolution = res
        prefs(context).edit { putString(KEY_RESOLUTION, res) }
    }

    fun getTelegramToken(context: Context): String {
        return prefs(context).getString(KEY_TELEGRAM_TOKEN, "") ?: ""
    }

    fun getTelegramChatId(context: Context): String {
        return prefs(context).getString(KEY_TELEGRAM_CHAT_ID, "") ?: ""
    }

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
