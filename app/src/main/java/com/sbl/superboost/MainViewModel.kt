package com.sbl.superboost

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.sbl.superboost.audio.AudioEngine
import com.sbl.superboost.audio.BluetoothAudio
import com.sbl.superboost.audio.BtDevice
import com.sbl.superboost.audio.EffectStatus
import com.sbl.superboost.audio.EqBandUi
import com.sbl.superboost.audio.EqPreset
import com.sbl.superboost.audio.VolumeController

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    private val vc = VolumeController(ctx)
    private val bluetooth = BluetoothAudio(ctx)
    private val prefs = ctx.getSharedPreferences("sbl_prefs", Context.MODE_PRIVATE)

    /** Zuletzt bekannter Systemlautstärke-Index (um eigene Änderungen von Tasten zu trennen). */
    private var expectedIndex = vc.currentIndex

    // ---- Lautstärke 0..200 (angezeigter Wert == tatsächlich angewendeter Wert)
    var volume by mutableIntStateOf(vc.percentOfIndex(vc.currentIndex))
        private set

    // ---- Equalizer
    var bass by mutableIntStateOf(prefs.getInt("bass", 0))
        private set
    var mid by mutableIntStateOf(prefs.getInt("mid", 0))
        private set
    var treble by mutableIntStateOf(prefs.getInt("treble", 0))
        private set
    var presetName by mutableStateOf(prefs.getString("preset", "Flat") ?: "Flat")
        private set
    var bands by mutableStateOf<List<EqBandUi>>(emptyList())
        private set
    var status by mutableStateOf(EffectStatus())
        private set

    // ---- Player
    var fileName by mutableStateOf<String?>(null)
        private set
    var duration by mutableIntStateOf(0)
        private set
    var position by mutableIntStateOf(0)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    // ---- Bluetooth
    var devices by mutableStateOf<List<BtDevice>>(emptyList())
        private set
    var btPermission by mutableStateOf(bluetooth.hasPermission())
        private set
    var selectedDeviceId by mutableStateOf<Int?>(null)
        private set

    private val engine = AudioEngine(
        context = ctx,
        onStatus = { s, b ->
            status = s
            bands = b
        },
        onCompleted = {
            isPlaying = false
            position = 0
        }
    )

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            syncFromSystem()
        }
    }

    init {
        ctx.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        bluetooth.register { refreshBluetooth() }
        engine.applyTone(bass, mid, treble)
        refreshBluetooth()
    }

    // ------------------------------------------------------------ Lautstärke

    fun updateVolume(percent: Int) {
        val v = percent.coerceIn(0, 200)
        if (v <= 100) {
            val idx = vc.setSystemPercent(v)
            expectedIndex = idx
            volume = vc.percentOfIndex(idx)
            engine.setBoostPercent(100)
        } else {
            expectedIndex = vc.setMax()
            volume = v
            engine.setBoostPercent(v)
        }
    }

    fun stepVolume(delta: Int) = updateVolume(volume + delta)

    /** Gleicht den Regler mit Änderungen über die Hardware-Tasten / das System-Panel ab. */
    fun syncFromSystem() {
        val idx = vc.currentIndex
        if (idx == expectedIndex) return
        expectedIndex = idx
        if (idx >= vc.maxIndex && volume >= 100) return
        volume = vc.percentOfIndex(idx)
        engine.setBoostPercent(100)
    }

    // ------------------------------------------------------------ Equalizer

    fun applyPreset(p: EqPreset) {
        bass = p.bass
        mid = p.mid
        treble = p.treble
        presetName = p.name
        engine.applyTone(bass, mid, treble)
        saveEq()
    }

    fun setTone(newBass: Int = bass, newMid: Int = mid, newTreble: Int = treble) {
        bass = newBass
        mid = newMid
        treble = newTreble
        presetName = "Manuell"
        engine.applyTone(bass, mid, treble)
        saveEq()
    }

    fun setBand(index: Int, db: Float) {
        presetName = "Manuell"
        engine.setBandDb(index, db)
    }

    private fun saveEq() {
        prefs.edit()
            .putInt("bass", bass)
            .putInt("mid", mid)
            .putInt("treble", treble)
            .putString("preset", presetName)
            .apply()
    }

    // --------------------------------------------------------------- Player

    fun loadFile(uri: Uri) {
        isPlaying = false
        position = 0
        duration = 0
        message = "Datei wird geladen …"
        fileName = displayName(uri)
        engine.load(
            uri,
            onPrepared = { d ->
                duration = d
                message = null
            },
            onError = { msg ->
                message = msg
                isPlaying = false
            }
        )
    }

    private fun displayName(uri: Uri): String {
        val name: String? = try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            null
        }
        return name ?: uri.lastPathSegment ?: "Audio"
    }

    fun togglePlay() {
        if (fileName == null) return
        if (engine.isPlaying()) {
            engine.pause()
            isPlaying = false
        } else if (engine.play()) {
            isPlaying = true
        } else {
            message = "Die Datei ist noch nicht bereit."
        }
    }

    fun pollPosition() {
        position = engine.position()
        isPlaying = engine.isPlaying()
    }

    fun seekTo(ms: Int) {
        engine.seekTo(ms)
        position = ms
    }

    // ------------------------------------------------------------ Bluetooth

    fun refreshBluetooth() {
        btPermission = bluetooth.hasPermission()
        val list = bluetooth.list()
        devices = list
        val sel = selectedDeviceId
        if (sel != null && list.none { it.deviceId == sel }) {
            selectedDeviceId = null
            engine.setPreferredDevice(null)
        }
    }

    /** null = automatische Systemauswahl. */
    fun selectDevice(device: BtDevice?) {
        val id = device?.deviceId
        selectedDeviceId = id
        engine.setPreferredDevice(id)
    }

    override fun onCleared() {
        ctx.contentResolver.unregisterContentObserver(observer)
        bluetooth.unregister()
        engine.release()
        super.onCleared()
    }
}
