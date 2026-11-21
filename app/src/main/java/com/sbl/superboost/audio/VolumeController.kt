package com.sbl.superboost.audio

import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt

/** Steuert die Android-Medienlautstärke (STREAM_MUSIC), entspricht dem Bereich 0-100 %. */
class VolumeController(context: Context) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val maxIndex: Int get() = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val currentIndex: Int get() = am.getStreamVolume(AudioManager.STREAM_MUSIC)

    fun percentOfIndex(index: Int): Int {
        val max = maxIndex
        return if (max <= 0) 0 else (index * 100f / max).roundToInt().coerceIn(0, 100)
    }

    /** Setzt die Systemlautstärke; gibt den tatsächlich eingestellten Index zurück. */
    fun setSystemPercent(percent: Int): Int {
        val max = maxIndex
        val target = (percent.coerceIn(0, 100) * max / 100f).roundToInt().coerceIn(0, max)
        return setIndex(target)
    }

    fun setMax(): Int = setIndex(maxIndex)

    private fun setIndex(index: Int): Int {
        try {
            if (currentIndex != index) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
            }
        } catch (e: SecurityException) {
            // Gerät erlaubt die Änderung nicht (z. B. feste Lautstärke)
        }
        return currentIndex
    }
}
