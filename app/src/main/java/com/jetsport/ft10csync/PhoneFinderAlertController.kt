package com.jetsport.ft10csync

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.jetsport.ft10csync.data.SettingsRepository

/** Phone-side alert triggered only by the observed incoming FT-10C D1 0A notification. */
internal class PhoneFinderAlertController(
    context: Context,
    private val settings: SettingsRepository,
    private val mainHandler: Handler,
    private val onStateChanged: (Boolean) -> Unit,
    private val onLog: (String) -> Unit
) : AutoCloseable {
    private val appContext = context.applicationContext
    private var ringtone: Ringtone? = null
    private var active = false
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    val isActive: Boolean get() = active

    private fun startRingtone() {
        val stored = settings.phoneFinderRingtoneUri
        if (stored == "__SILENT__") return
        val uri = if (stored.isNullOrBlank()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) else Uri.parse(stored)
        if (uri == null) {
            onLog("PHONE FINDER: no alarm ringtone URI is available")
            return
        }
        runCatching {
            ringtone = RingtoneManager.getRingtone(appContext, uri)?.also { sound ->
                sound.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) sound.isLooping = true
                sound.play()
            }
            if (ringtone == null) onLog("PHONE FINDER: selected ringtone could not be loaded")
        }.onFailure { onLog("PHONE FINDER: ringtone playback failed: ${it.javaClass.simpleName}") }
    }

    fun start() {
        if (active) return
        if (!settings.phoneFinderSoundEnabled && !settings.phoneFinderVibrationEnabled) {
            onLog("PHONE FINDER: D1 0A received, but sound and vibration are disabled")
            return
        }
        active = true
        if (settings.phoneFinderSoundEnabled) startRingtone()
        if (settings.phoneFinderVibrationEnabled) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 350, 500, 900), 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 500, 350, 500, 900), 0)
                }
            }.onFailure { onLog("PHONE FINDER: vibration start failed: ${it.javaClass.simpleName}") }
        }
        onStateChanged(true)
        onLog("PHONE FINDER: alert started (sound=${settings.phoneFinderSoundEnabled}, vibration=${settings.phoneFinderVibrationEnabled})")
    }

    fun stop(reason: String = "stopped by user") {
        if (!active) return
        active = false
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
        onStateChanged(false)
        onLog("PHONE FINDER: alert stopped ($reason)")
    }

    override fun close() = stop("BLE runtime closed")
}
