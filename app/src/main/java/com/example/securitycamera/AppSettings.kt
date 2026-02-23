package com.example.securitycamera

import android.content.Context

object AppSettings {
    private const val PREFS_NAME = "security_camera_prefs"
    private const val KEY_CONF_THRESHOLD = "conf_threshold"
    private const val KEY_RECOG_THRESHOLD = "recognition_threshold"

    // New Keys for Security State
    private const val KEY_INCIDENT_TIMEOUT = "incident_timeout"
    private const val KEY_GRACE_PERIOD = "grace_period"
    private const val KEY_SAFE_THRESHOLD = "safe_threshold"
    private const val KEY_SAFE_IDENTITIES = "safe_identities"
    private const val KEY_TELEGRAM_ENABLED = "telegram_enabled"
    private const val KEY_TELEGRAM_TOKEN = "telegram_token"
    private const val KEY_TELEGRAM_CHAT_ID = "telegram_chat_id"

    const val DEFAULT_CONF_THRESHOLD = 0.3f
    const val DEFAULT_RECOG_THRESHOLD = 0.6f

    // Defaults for Security State
    const val DEFAULT_INCIDENT_TIMEOUT = 10L // seconds
    const val DEFAULT_GRACE_PERIOD = 3L      // seconds
    const val DEFAULT_SAFE_THRESHOLD = 5     // frames

    var confThreshold: Float = DEFAULT_CONF_THRESHOLD
        private set
    var recognitionThreshold: Float = DEFAULT_RECOG_THRESHOLD
        private set

    // New In-memory values
    var incidentTimeoutSec: Long = DEFAULT_INCIDENT_TIMEOUT
        private set
    var gracePeriodSec: Long = DEFAULT_GRACE_PERIOD
        private set
    var safeThresholdFrames: Int = DEFAULT_SAFE_THRESHOLD
        private set
    var safeIdentities: Set<String> = emptySet()
        private set
    var telegramEnabled: Boolean = false
        private set
    var telegramToken: String = ""
        private set
    var telegramChatId: String = ""
        private set

    fun load(context: Context) {
        val p = prefs(context)
        try {
            var conf = p.getFloat(KEY_CONF_THRESHOLD, DEFAULT_CONF_THRESHOLD)
            var recog = p.getFloat(KEY_RECOG_THRESHOLD, DEFAULT_RECOG_THRESHOLD)
            conf = Math.round(conf * 100) / 100f
            recog = Math.round(recog * 100) / 100f
            confThreshold = conf.coerceIn(0.1f, 0.9f)
            recognitionThreshold = recog.coerceIn(0.1f, 1.0f)

            // Load Security Settings
            incidentTimeoutSec = p.getLong(KEY_INCIDENT_TIMEOUT, DEFAULT_INCIDENT_TIMEOUT)
            gracePeriodSec = p.getLong(KEY_GRACE_PERIOD, DEFAULT_GRACE_PERIOD)
            safeThresholdFrames = p.getInt(KEY_SAFE_THRESHOLD, DEFAULT_SAFE_THRESHOLD)
            safeIdentities = p.getStringSet(KEY_SAFE_IDENTITIES, emptySet()) ?: emptySet()

            // Load Telegram Settings
            telegramEnabled = p.getBoolean(KEY_TELEGRAM_ENABLED, false)
            telegramToken = p.getString(KEY_TELEGRAM_TOKEN, "") ?: ""
            telegramChatId = p.getString(KEY_TELEGRAM_CHAT_ID, "") ?: ""

        } catch (e: Exception) {
            confThreshold = DEFAULT_CONF_THRESHOLD
            recognitionThreshold = DEFAULT_RECOG_THRESHOLD
            incidentTimeoutSec = DEFAULT_INCIDENT_TIMEOUT
            gracePeriodSec = DEFAULT_GRACE_PERIOD
            safeThresholdFrames = DEFAULT_SAFE_THRESHOLD
            safeIdentities = emptySet()
        }
    }

    fun saveConfThreshold(context: Context, value: Float) {
        confThreshold = value
        prefs(context).edit().putFloat(KEY_CONF_THRESHOLD, value).apply()
    }

    fun saveRecognitionThreshold(context: Context, value: Float) {
        recognitionThreshold = value
        prefs(context).edit().putFloat(KEY_RECOG_THRESHOLD, value).apply()
    }

    // New Save Methods
    fun saveIncidentTimeout(context: Context, valueSec: Long) {
        incidentTimeoutSec = valueSec
        prefs(context).edit().putLong(KEY_INCIDENT_TIMEOUT, valueSec).apply()
    }

    fun saveGracePeriod(context: Context, valueSec: Long) {
        gracePeriodSec = valueSec
        prefs(context).edit().putLong(KEY_GRACE_PERIOD, valueSec).apply()
    }

    fun saveSafeThreshold(context: Context, frames: Int) {
        safeThresholdFrames = frames
        prefs(context).edit().putInt(KEY_SAFE_THRESHOLD, frames).apply()
    }

    fun saveSafeIdentities(context: Context, identities: Set<String>) {
        safeIdentities = identities
        prefs(context).edit().putStringSet(KEY_SAFE_IDENTITIES, identities).apply()
    }

    fun saveTelegramEnabled(context: Context, enabled: Boolean) {
        telegramEnabled = enabled
        prefs(context).edit().putBoolean(KEY_TELEGRAM_ENABLED, enabled).apply()
    }

    fun saveTelegramToken(context: Context, token: String) {
        telegramToken = token
        prefs(context).edit().putString(KEY_TELEGRAM_TOKEN, token).apply()
    }

    fun saveTelegramChatId(context: Context, chatId: String) {
        telegramChatId = chatId
        prefs(context).edit().putString(KEY_TELEGRAM_CHAT_ID, chatId).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
