package com.sbl.superboost.audio

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

data class BtDevice(
    val key: String,
    val name: String,
    val kind: String,
    val connected: Boolean,
    /** AudioDeviceInfo.id, nur wenn das Gerät gerade als Audio-Ausgang verbunden ist. */
    val deviceId: Int?
)

class BluetoothAudio(private val context: Context) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var callback: AudioDeviceCallback? = null

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private fun isBluetoothOutput(d: AudioDeviceInfo): Boolean = when (d.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> true
        AudioDeviceInfo.TYPE_HEARING_AID -> true
        AudioDeviceInfo.TYPE_BLE_HEADSET -> true
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> true
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> true
        else -> false
    }

    private fun typeLabel(d: AudioDeviceInfo): String = when (d.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth (A2DP)"
        AudioDeviceInfo.TYPE_HEARING_AID -> "Hörgerät"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE Audio (Kopfhörer)"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE Audio (Lautsprecher)"
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> "Bluetooth LE Broadcast"
        else -> "Bluetooth"
    }

    private fun classLabel(cls: BluetoothClass?): String = when (cls?.deviceClass) {
        BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> "Kopfhörer"
        BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> "Headset"
        BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> "Freisprecheinrichtung"
        BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> "Lautsprecher"
        BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO -> "Portabler Lautsprecher"
        BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> "HiFi-Audio"
        BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> "Auto-Audio"
        else -> "Audiogerät"
    }

    /** Liste: aktuell verbundene Audio-Ausgänge + gekoppelte Audiogeräte (falls Berechtigung vorhanden). */
    fun list(): List<BtDevice> {
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { isBluetoothOutput(it) }
        val result = mutableListOf<BtDevice>()
        val matchedIds = mutableSetOf<Int>()

        if (hasPermission()) {
            try {
                val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                val bonded = adapter?.bondedDevices
                if (bonded != null) {
                    for (b in bonded) {
                        val cls = b.bluetoothClass
                        if (cls != null && cls.majorDeviceClass != BluetoothClass.Device.Major.AUDIO_VIDEO) continue
                        val bName = b.name ?: b.address
                        val out = outputs.firstOrNull { o ->
                            (Build.VERSION.SDK_INT >= 28 && o.address.equals(b.address, ignoreCase = true)) ||
                                o.productName?.toString() == b.name
                        }
                        if (out != null) matchedIds.add(out.id)
                        result.add(
                            BtDevice(
                                key = b.address,
                                name = bName,
                                kind = if (out != null) typeLabel(out) + " · " + classLabel(cls) else classLabel(cls),
                                connected = out != null,
                                deviceId = out?.id
                            )
                        )
                    }
                }
            } catch (e: SecurityException) {
                // Berechtigung wurde zur Laufzeit entzogen
            }
        }

        for (o in outputs) {
            if (o.id in matchedIds) continue
            result.add(
                BtDevice(
                    key = "out-" + o.id,
                    name = o.productName?.toString()?.ifBlank { null } ?: "Bluetooth-Gerät",
                    kind = typeLabel(o),
                    connected = true,
                    deviceId = o.id
                )
            )
        }
        return result.sortedByDescending { it.connected }
    }

    fun register(onChange: () -> Unit) {
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = onChange()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = onChange()
        }
        callback = cb
        am.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper()))
    }

    fun unregister() {
        callback?.let { am.unregisterAudioDeviceCallback(it) }
        callback = null
    }
}
