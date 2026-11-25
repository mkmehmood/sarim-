package com.gullzubair.sarim.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import kotlin.math.exp
import kotlin.math.sin

enum class HapticLevel(val key: String, val label: String, val detail: String, val durationMs: Long, val amplitude: Int) {
    OFF("off", "Off", "Haptic vibration disabled", 0L, 0),
    LIGHT("light", "Light", "Subtle micro-tap", 12L, 70),
    MEDIUM("medium", "Medium", "Balanced standard tactile click", 22L, 145),
    STRONG("strong", "Strong", "Pronounced mechanical pulse", 36L, 255);

    companion object {
        fun fromKey(key: String): HapticLevel = entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

enum class SoundLevel(val key: String, val label: String, val detail: String, val gain: Float) {
    MUTED("muted", "Muted", "Silent mode without audio clicks", 0f),
    SOFT("soft", "Soft (Discreet)", "Quiet acoustic tick sound", 0.18f),
    STANDARD("standard", "Standard (Crisp)", "Crisp digital audio click", 0.42f),
    LOUD("loud", "Loud (Resonant)", "High-resonance tactile chime", 0.72f),
    EXTREME("extreme", "Extreme Boost (Maximum)", "100% volume acoustic burst - clears freely & loudly", 1.0f);

    companion object {
        fun fromKey(key: String): SoundLevel = entries.firstOrNull { it.key == key } ?: STANDARD
    }
}

object FeedbackHelper {
    private const val PREFS_NAME = "sarim_feedback_prefs"
    private const val KEY_HAPTIC = "haptic_level"
    private const val KEY_SOUND = "sound_level"

    fun getHapticLevel(context: Context): HapticLevel {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_HAPTIC, HapticLevel.MEDIUM.key) ?: HapticLevel.MEDIUM.key
        return HapticLevel.fromKey(raw)
    }

    fun setHapticLevel(context: Context, level: HapticLevel) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_HAPTIC, level.key).apply()
    }

    fun getSoundLevel(context: Context): SoundLevel {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SOUND, SoundLevel.STANDARD.key) ?: SoundLevel.STANDARD.key
        return SoundLevel.fromKey(raw)
    }

    fun setSoundLevel(context: Context, level: SoundLevel) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SOUND, level.key).apply()
    }

    fun isSystemTouchVibrationEnabled(context: Context): Boolean {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
        } catch (_: Exception) {
            true
        }
    }

    @Suppress("DEPRECATION")
    fun triggerHaptic(context: Context, level: HapticLevel = getHapticLevel(context)) {
        if (level == HapticLevel.OFF) return
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(level.durationMs, level.amplitude))
            } else {
                vibrator.vibrate(level.durationMs)
            }
        } catch (_: Exception) {
        }
    }

    fun triggerSound(context: Context, level: SoundLevel = getSoundLevel(context)) {
        if (level == SoundLevel.MUTED || level.gain <= 0f) return
        Thread {
            try {
                val sampleRate = 22050
                val durationSamples = (sampleRate * 0.032).toInt()
                val pcm = ShortArray(durationSamples)
                val freq = if (level == SoundLevel.LOUD || level == SoundLevel.EXTREME) 1420.0 else 1180.0
                for (i in 0 until durationSamples) {
                    val t = i.toDouble() / sampleRate
                    val env = exp(-t * 140.0)
                    val wave = sin(2.0 * Math.PI * freq * t) * 0.7 + sin(2.0 * Math.PI * (freq * 1.5) * t) * 0.3
                    val sample = (wave * env * Short.MAX_VALUE * level.gain).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    pcm[i] = sample.toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(60L)
                track.stop()
                track.release()
            } catch (_: Exception) {
            }
        }.start()
    }
}
