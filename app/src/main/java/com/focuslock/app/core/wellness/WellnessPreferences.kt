package com.focuslock.app.core.wellness

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri

/**
 * Manages user preferences for the 30-minute continuous screen wellness alert,
 * custom audio/song files, custom video files, vibration, and volume.
 */
class WellnessPreferences(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isBreakAlertEnabled: Boolean
        get() = prefs.getBoolean(KEY_BREAK_ALERT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_BREAK_ALERT_ENABLED, value).apply()

    var isAudioEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO_ENABLED, value).apply()

    var isVibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, value).apply()

    var audioVolume: Float
        get() = prefs.getFloat(KEY_AUDIO_VOLUME, 0.8f)
        set(value) = prefs.edit().putFloat(KEY_AUDIO_VOLUME, value.coerceIn(0.0f, 1.0f)).apply()

    var customAudioUriString: String?
        get() = prefs.getString(KEY_CUSTOM_AUDIO_URI, null)
        set(value) = prefs.edit().putString(KEY_CUSTOM_AUDIO_URI, value).apply()

    var customAudioTitle: String?
        get() = prefs.getString(KEY_CUSTOM_AUDIO_TITLE, null)
        set(value) = prefs.edit().putString(KEY_CUSTOM_AUDIO_TITLE, value).apply()

    var customVideoUriString: String?
        get() = prefs.getString(KEY_CUSTOM_VIDEO_URI, null)
        set(value) = prefs.edit().putString(KEY_CUSTOM_VIDEO_URI, value).apply()

    var customVideoTitle: String?
        get() = prefs.getString(KEY_CUSTOM_VIDEO_TITLE, null)
        set(value) = prefs.edit().putString(KEY_CUSTOM_VIDEO_TITLE, value).apply()

    fun setCustomAudio(uri: Uri, title: String) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            // Some file providers do not support persistable permissions
        }
        customAudioUriString = uri.toString()
        customAudioTitle = title
    }

    fun clearCustomAudio() {
        customAudioUriString = null
        customAudioTitle = null
    }

    fun setCustomVideo(uri: Uri, title: String) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            // Ignore if persistable not supported
        }
        customVideoUriString = uri.toString()
        customVideoTitle = title
    }

    fun clearCustomVideo() {
        customVideoUriString = null
        customVideoTitle = null
    }

    companion object {
        const val PREFS_NAME = "focuslock_app_prefs"
        const val KEY_BREAK_ALERT_ENABLED = "continuous_break_alert"
        const val KEY_AUDIO_ENABLED = "wellness_audio_enabled"
        const val KEY_VIBRATION_ENABLED = "wellness_vibration_enabled"
        const val KEY_AUDIO_VOLUME = "wellness_audio_volume"
        const val KEY_CUSTOM_AUDIO_URI = "wellness_custom_audio_uri"
        const val KEY_CUSTOM_AUDIO_TITLE = "wellness_custom_audio_title"
        const val KEY_CUSTOM_VIDEO_URI = "wellness_custom_video_uri"
        const val KEY_CUSTOM_VIDEO_TITLE = "wellness_custom_video_title"

        @Volatile
        private var instance: WellnessPreferences? = null

        fun getInstance(context: Context): WellnessPreferences {
            return instance ?: synchronized(this) {
                instance ?: WellnessPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
