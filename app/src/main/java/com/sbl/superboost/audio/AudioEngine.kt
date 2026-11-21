package com.sbl.superboost.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.log10
import kotlin.math.roundToInt

data class EffectStatus(
    val equalizer: Boolean = false,
    val loudness: Boolean = false,
    val limiter: Boolean = false
)

data class EqBandUi(
    val index: Int,
    val freqHz: Int,
    val db: Float,
    val minDb: Float,
    val maxDb: Float
)

/**
 * Eigener Player mit Audioeffekten auf der eigenen Audio-Session.
 * Diese Effekte wirken NUR auf Audio, das diese App selbst abspielt.
 */
class AudioEngine(
    private val context: Context,
    private val onStatus: (EffectStatus, List<EqBandUi>) -> Unit,
    private val onCompleted: () -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var player: MediaPlayer? = null
    private var prepared = false

    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var dynamics: AudioEffect? = null

    private var gainMb = 0
    private var bass = 0f
    private var mid = 0f
    private var treble = 0f
    private val manual = HashMap<Int, Float>()
    private var preferredId: Int? = null

    // ---------------------------------------------------------------- Player

    fun load(uri: Uri, onPrepared: (Int) -> Unit, onError: (String) -> Unit) {
        releasePlayer()
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(context, uri)
            mp.setOnPreparedListener {
                prepared = true
                onPrepared(it.duration)
            }
            mp.setOnCompletionListener { onCompleted() }
            mp.setOnErrorListener { _, what, extra ->
                onError("Wiedergabefehler ($what/$extra)")
                true
            }
            player = mp
            attachEffects(mp.audioSessionId)
            applyPreferred()
            mp.prepareAsync()
        } catch (e: Exception) {
            try {
                mp.release()
            } catch (ignored: Exception) {
            }
            player = null
            releaseEffects()
            onError("Datei konnte nicht geladen werden: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun play(): Boolean {
        val mp = player ?: return false
        if (!prepared) return false
        return try {
            mp.start()
            true
        } catch (e: IllegalStateException) {
            false
        }
    }

    fun pause() {
        try {
            player?.pause()
        } catch (e: IllegalStateException) {
        }
    }

    fun isPlaying(): Boolean = try {
        player?.isPlaying == true
    } catch (e: IllegalStateException) {
        false
    }

    fun position(): Int = try {
        if (prepared) player?.currentPosition ?: 0 else 0
    } catch (e: IllegalStateException) {
        0
    }

    fun seekTo(ms: Int) {
        try {
            if (prepared) player?.seekTo(ms)
        } catch (e: IllegalStateException) {
        }
    }

    // ------------------------------------------------------ Ausgabegerät

    /** Bevorzugtes Ausgabegerät (AudioDeviceInfo.id) für den In-App-Player; null = System. */
    fun setPreferredDevice(id: Int?) {
        preferredId = id
        applyPreferred()
    }

    private fun applyPreferred() {
        val mp = player ?: return
        if (Build.VERSION.SDK_INT >= 28) {
            val id = preferredId
            val dev = if (id == null) null else
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id }
            try {
                mp.setPreferredDevice(dev)
            } catch (e: Exception) {
            }
        }
    }

    // --------------------------------------------------------------- Boost

    /** percent 100 = neutral (0 dB), 200 = doppelte Amplitude (+6,02 dB). */
    fun setBoostPercent(percent: Int) {
        gainMb = if (percent <= 100) 0 else (2000.0 * log10(percent / 100.0)).roundToInt()
        applyGain()
    }

    private fun applyGain() {
        val le = loudness ?: return
        try {
            le.setTargetGain(gainMb)
            le.setEnabled(gainMb > 0)
        } catch (e: Exception) {
        }
    }

    // ------------------------------------------------------------ Equalizer

    fun applyTone(bassDb: Int, midDb: Int, trebleDb: Int) {
        bass = bassDb.toFloat()
        mid = midDb.toFloat()
        treble = trebleDb.toFloat()
        manual.clear()
        applyEq()
        publish()
    }

    fun setBandDb(index: Int, db: Float) {
        manual[index] = db
        applyEq()
        publish()
    }

    private fun groupDb(freqHz: Int): Float {
        val x = log10(freqHz.coerceAtLeast(20).toDouble())
        val xb = log10(80.0)
        val xm = log10(1000.0)
        val xt = log10(10000.0)
        return when {
            x <= xb -> bass
            x <= xm -> lerp(bass, mid, ((x - xb) / (xm - xb)).toFloat())
            x <= xt -> lerp(mid, treble, ((x - xm) / (xt - xm)).toFloat())
            else -> treble
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun applyEq() {
        val e = equalizer ?: return
        try {
            val range = e.bandLevelRange
            val lo = range[0].toInt()
            val hi = range[1].toInt()
            for (i in 0 until e.numberOfBands.toInt()) {
                val freqHz = e.getCenterFreq(i.toShort()) / 1000
                val db = manual[i] ?: groupDb(freqHz)
                val mb = (db * 100f).roundToInt().coerceIn(lo, hi)
                e.setBandLevel(i.toShort(), mb.toShort())
            }
        } catch (ex: Exception) {
        }
    }

    fun bandInfos(): List<EqBandUi> {
        val e = equalizer ?: return emptyList()
        return try {
            val range = e.bandLevelRange
            (0 until e.numberOfBands.toInt()).map { i ->
                EqBandUi(
                    index = i,
                    freqHz = e.getCenterFreq(i.toShort()) / 1000,
                    db = e.getBandLevel(i.toShort()).toInt() / 100f,
                    minDb = range[0].toInt() / 100f,
                    maxDb = range[1].toInt() / 100f
                )
            }
        } catch (ex: Exception) {
            emptyList()
        }
    }

    // -------------------------------------------------------------- Effekte

    private fun attachEffects(session: Int) {
        releaseEffects()
        // Reihenfolge: Equalizer -> LoudnessEnhancer -> Limiter (zuletzt, fängt Spitzen ab)
        try {
            equalizer = Equalizer(0, session).also { it.setEnabled(true) }
        } catch (e: Exception) {
            equalizer = null
        }
        try {
            loudness = LoudnessEnhancer(session)
        } catch (e: Exception) {
            loudness = null
        }
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                dynamics = createLimiter(session)
            } catch (e: Exception) {
                dynamics = null
            }
        }
        applyEq()
        applyGain()
        publish()
    }

    @RequiresApi(28)
    private fun createLimiter(session: Int): DynamicsProcessing {
        val channels = 2
        val cfg = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            channels,
            false, 0,
            false, 0,
            false, 0,
            true
        ).build()
        val dp = DynamicsProcessing(0, session, cfg)
        val limiter = DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1.5f, 0f)
        for (ch in 0 until channels) {
            dp.setLimiterByChannelIndex(ch, limiter)
        }
        dp.setEnabled(true)
        return dp
    }

    private fun publish() {
        onStatus(
            EffectStatus(
                equalizer = equalizer != null,
                loudness = loudness != null,
                limiter = dynamics != null
            ),
            bandInfos()
        )
    }

    private fun releaseEffects() {
        try { equalizer?.release() } catch (e: Exception) { }
        try { loudness?.release() } catch (e: Exception) { }
        try { dynamics?.release() } catch (e: Exception) { }
        equalizer = null
        loudness = null
        dynamics = null
    }

    private fun releasePlayer() {
        prepared = false
        try { player?.release() } catch (e: Exception) { }
        player = null
        releaseEffects()
    }

    fun release() {
        releasePlayer()
    }
}
