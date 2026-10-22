package com.focuslock.app.core.wellness

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin

/**
 * Handles pleasant audio alarms, custom songs, voice guidance, and gentle vibration
 * for the 30-minute continuous screen wellness reminder.
 */
class WellnessSoundManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val preferences = WellnessPreferences.getInstance(appContext)

    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private var audioTrack: AudioTrack? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private var chimeJob: Job? = null
    private var isPlaying = false

    init {
        try {
            tts = TextToSpeech(appContext, this)
        } catch (e: Exception) {
            // TTS unavailable on some minimal ROMs
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            tts?.setPitch(1.05f)
            tts?.setSpeechRate(0.95f)
        }
    }

    /**
     * Triggers the 30-minute wellness alarm.
     * Plays custom audio if configured, otherwise plays a pleasant chime + speaks the reminder.
     */
    @Synchronized
    fun startWellnessAlarm() {
        if (isPlaying) return
        isPlaying = true

        val audioEnabled = preferences.isAudioEnabled
        val vibrationEnabled = preferences.isVibrationEnabled
        val volume = preferences.audioVolume

        if (vibrationEnabled) {
            triggerGentleVibration()
        }

        if (!audioEnabled) return

        val customUriStr = preferences.customAudioUriString
        if (!customUriStr.isNullOrBlank()) {
            val playedCustom = playCustomAudio(Uri.parse(customUriStr), volume, loop = true)
            if (playedCustom) return
        }

        // Fallback: Pleasant harmonic chime and warm spoken prompt
        playPleasantChime(volume)
        speakWellnessPrompt()
    }

    /**
     * Plays preview of custom or default audio in Settings screen.
     */
    @Synchronized
    fun playPreview(uri: Uri?, volume: Float = preferences.audioVolume) {
        stop()
        isPlaying = true
        if (uri != null) {
            val success = playCustomAudio(uri, volume, loop = false)
            if (!success) {
                playPleasantChime(volume)
                speakWellnessPrompt()
            }
        } else {
            playPleasantChime(volume)
            speakWellnessPrompt()
        }
    }

    private fun playCustomAudio(uri: Uri, volume: Float, loop: Boolean): Boolean {
        return try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                setDataSource(appContext, uri)
                setVolume(volume, volume)
                isLooping = loop
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            mediaPlayer?.release()
            mediaPlayer = null
            false
        }
    }

    /**
     * Synthesizes a soft, harmonic ambient chime sequence (F4 -> A4 -> C5 -> E5 arpeggio).
     */
    private fun playPleasantChime(volume: Float) {
        chimeJob?.cancel()
        chimeJob = scope.launch {
            try {
                val sampleRate = 44100
                val durationSec = 1.8
                val numSamples = (durationSec * sampleRate).toInt()
                val samples = ShortArray(numSamples)

                val chordFrequencies = doubleArrayOf(349.23, 440.00, 523.25, 659.25) // F major 7 ambient chord

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    // Exponential gentle fade envelope
                    val envelope = kotlin.math.exp(-2.2 * t) * (1.0 - kotlin.math.exp(-15.0 * t))

                    var wave = 0.0
                    for ((idx, freq) in chordFrequencies.withIndex()) {
                        val noteDelay = idx * 0.15
                        if (t >= noteDelay) {
                            val noteT = t - noteDelay
                            val noteEnv = kotlin.math.exp(-2.5 * noteT)
                            wave += sin(2.0 * Math.PI * freq * noteT) * noteEnv * 0.25
                        }
                    }

                    val sampleValue = (wave * envelope * volume * Short.MAX_VALUE).coerceIn(
                        Short.MIN_VALUE.toDouble(),
                        Short.MAX_VALUE.toDouble()
                    ).toInt().toShort()

                    samples[i] = sampleValue
                }

                audioTrack?.release()
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack?.write(samples, 0, samples.size)
                audioTrack?.play()
            } catch (e: Exception) {
                // AudioTrack fallback
            }
        }
    }

    private fun speakWellnessPrompt() {
        scope.launch {
            delay(1200L) // Wait slightly for the ambient chime to ring
            if (!isPlaying) return@launch

            val promptText = "Please, sir! It's time to take care of yourself. Go for a short walk, rest your eyes, and drink a glass of water."
            if (isTtsReady && tts != null) {
                try {
                    tts?.speak(promptText, TextToSpeech.QUEUE_FLUSH, null, "wellness_prompt_id")
                } catch (e: Exception) {}
            }
        }
    }

    private fun triggerGentleVibration() {
        try {
            val timings = longArrayOf(0, 250, 150, 250, 150, 350)
            val amplitudes = intArrayOf(0, 160, 0, 180, 0, 200)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(timings, -1)
                }
            }
        } catch (e: Exception) {
            // Missing VIBRATE permission or device without vibration motor
        }
    }

    @Synchronized
    fun stop() {
        isPlaying = false
        chimeJob?.cancel()

        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {}

        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {}

        try {
            tts?.stop()
        } catch (e: Exception) {}
    }

    fun release() {
        stop()
        try {
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {}
    }

    companion object {
        @Volatile
        private var instance: WellnessSoundManager? = null

        fun getInstance(context: Context): WellnessSoundManager {
            return instance ?: synchronized(this) {
                instance ?: WellnessSoundManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
