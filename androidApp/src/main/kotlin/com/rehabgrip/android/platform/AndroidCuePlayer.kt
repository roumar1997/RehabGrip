package com.rehabgrip.android.platform

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import com.rehabgrip.core.domain.port.CuePlayer

/** Beeps on the alarm/music stream and short vibrations for the guided session. */
class AndroidCuePlayer(context: Context) : CuePlayer {
    private val tones = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }.getOrNull()
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    override fun countdownTick() = beep(ToneGenerator.TONE_PROP_BEEP, 120)

    override fun pullStart() {
        beep(ToneGenerator.TONE_PROP_ACK, 300)
        vibrate(120)
    }

    override fun pullEnd() {
        beep(ToneGenerator.TONE_PROP_BEEP2, 300)
        vibrate(60)
    }

    override fun aboveMax() {
        beep(ToneGenerator.TONE_CDMA_HIGH_L, 250)
        vibrate(250)
    }

    override fun belowMin() = beep(ToneGenerator.TONE_PROP_PROMPT, 120)

    override fun finished() {
        beep(ToneGenerator.TONE_CDMA_CONFIRM, 600)
        vibrate(400)
    }

    private fun beep(tone: Int, ms: Int) {
        runCatching { tones?.startTone(tone, ms) }
    }

    private fun vibrate(ms: Long) {
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }
}
