package com.sbl.superboost.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbl.superboost.MainViewModel
import com.sbl.superboost.audio.EQ_PRESETS
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.log10
import kotlin.math.roundToInt

@Composable
fun MainScreen(vm: MainViewModel) {
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshBluetooth()
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.loadFile(uri)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 31 && !vm.btPermission) {
            permLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
    LaunchedEffect(vm.isPlaying) {
        while (vm.isPlaying) {
            vm.pollPosition()
            delay(250)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Header()
            VolumeCard(vm)
            LimitsCard()
            PlayerCard(vm) { pickLauncher.launch(arrayOf("audio/*")) }
            BluetoothCard(vm) { permLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }
            EqualizerCard(vm)
            StatusCard(vm)
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Box(Modifier.width(6.dp).height(32.dp).background(SblRed, RoundedCornerShape(3.dp)))
        Text(
            text = "  SBL SUPER BOOST ",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Text(text = "V2", fontSize = 22.sp, fontWeight = FontWeight.Black, color = SblRed)
    }
}

@Composable
private fun SblCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SblCardColor),
        border = BorderStroke(1.dp, SblOutline),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SblRed
                )
            }
            content()
        }
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = SblRed,
    activeTrackColor = SblRed,
    inactiveTrackColor = Color(0xFF333333),
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent
)

private fun fmt(value: Float, pattern: String): String = String.format(Locale.getDefault(), pattern, value)

private fun fmtTime(ms: Int): String {
    val s = ms / 1000
    return String.format(Locale.getDefault(), "%d:%02d", s / 60, s % 60)
}

// ------------------------------------------------------------------ Lautstärke

@Composable
private fun VolumeCard(vm: MainViewModel) {
    val v = vm.volume
    val boost = v > 100
    val accent by animateColorAsState(if (boost) SblRedHot else SblRed, tween(300), label = "accent")
    val sweep by animateFloatAsState(v / 200f * 270f, tween(200), label = "sweep")
    val gainDb = if (boost) 20f * log10(v / 100f) else 0f

    SblCard("Lautstärke") {
        Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(220.dp)) {
                val stroke = 18.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = Color(0xFF2A2A2A),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = accent,
                    startAngle = 135f,
                    sweepAngle = sweep.coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "$v %", fontSize = 60.sp, fontWeight = FontWeight.Black, color = accent)
                Text(
                    text = if (boost) fmt(gainDb, "+%.1f dB digital") else "Android-Medienlautstärke",
                    color = SblMuted,
                    fontSize = 13.sp
                )
            }
        }
        Slider(
            value = v.toFloat(),
            onValueChange = { vm.updateVolume(it.roundToInt()) },
            valueRange = 0f..200f,
            colors = sliderColors()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0 %", color = SblMuted, fontSize = 12.sp)
            Text("100 %", color = SblMuted, fontSize = 12.sp)
            Text("200 %", color = SblMuted, fontSize = 12.sp)
        }
        Text(
            text = if (boost) {
                "Systemlautstärke auf Maximum. Zusätzliche Verstärkung wirkt nur auf den In-App-Player."
            } else {
                "0–100 % steuern die Android-Medienlautstärke (alle Apps, auch Bluetooth)."
            },
            color = SblMuted,
            fontSize = 13.sp
        )
        if (v > 150) {
            Text("Hohe Lautstärke – Gehör schützen.", color = SblRedHot, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LimitsCard() {
    SblCard("Wichtig: Was Android erlaubt") {
        Text(
            "Die Verstärkung über 100 % sowie der Equalizer wirken nur auf Audio, das diese App selbst " +
                "abspielt. YouTube, Spotify, TikTok und Spiele kann eine normale App nicht digital " +
                "verstärken – Android blockiert globale Effekte für Drittanbieter-Apps. Für diese Apps " +
                "wirkt nur der Bereich 0–100 % (Systemlautstärke).",
            color = SblMuted,
            fontSize = 13.sp
        )
    }
}

// ---------------------------------------------------------------------- Player

@Composable
private fun PlayerCard(vm: MainViewModel, onPick: () -> Unit) {
    SblCard("In-App-Player (Verstärkung + Equalizer wirken hier)") {
        Button(onClick = onPick, colors = ButtonDefaults.buttonColors(containerColor = SblRed)) {
            Text("Audiodatei wählen")
        }
        Text(vm.fileName ?: "Keine Datei geladen", color = SblMuted, fontSize = 14.sp)
        val msg = vm.message
        if (msg != null) {
            Text(msg, color = SblRedHot, fontSize = 13.sp)
        }
        var drag by remember { mutableStateOf<Float?>(null) }
        val dur = vm.duration.coerceAtLeast(1).toFloat()
        Slider(
            value = (drag ?: vm.position.toFloat()).coerceIn(0f, dur),
            onValueChange = { drag = it },
            onValueChangeFinished = {
                val d = drag
                if (d != null) vm.seekTo(d.toInt())
                drag = null
            },
            valueRange = 0f..dur,
            enabled = vm.fileName != null && vm.duration > 0,
            colors = sliderColors()
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(fmtTime(vm.position) + " / " + fmtTime(vm.duration), color = SblMuted, fontSize = 13.sp)
            Button(
                onClick = { vm.togglePlay() },
                enabled = vm.fileName != null && vm.duration > 0,
                colors = ButtonDefaults.buttonColors(containerColor = SblRed)
            ) {
                Text(if (vm.isPlaying) "Pause" else "Abspielen")
            }
        }
    }
}

// ------------------------------------------------------------------- Bluetooth

private fun openSystemOutputPicker(ctx: Context) {
    val intent = if (Build.VERSION.SDK_INT >= 29) {
        Intent(Settings.Panel.ACTION_VOLUME)
    } else {
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
    }
    try {
        ctx.startActivity(intent)
    } catch (e: Exception) {
        openBluetoothSettings(ctx)
    }
}

private fun openBluetoothSettings(ctx: Context) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    } catch (e: Exception) {
        // Kein passendes Einstellungs-Fenster auf diesem Gerät
    }
}

@Composable
private fun BluetoothCard(vm: MainViewModel, onRequestPermission: () -> Unit) {
    val ctx = LocalContext.current
    SblCard("Bluetooth-Audiogeräte") {
        if (Build.VERSION.SDK_INT >= 31 && !vm.btPermission) {
            Text(
                "Für Gerätenamen und gekoppelte Geräte braucht Android die Berechtigung „Geräte in der Nähe“.",
                color = SblMuted,
                fontSize = 13.sp
            )
            Button(onClick = onRequestPermission, colors = ButtonDefaults.buttonColors(containerColor = SblRed)) {
                Text("Berechtigung erteilen")
            }
        }
        if (vm.devices.isEmpty()) {
            Text("Keine Bluetooth-Audiogeräte gefunden.", color = SblMuted, fontSize = 14.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = vm.selectedDeviceId == null,
                onClick = { vm.selectDevice(null) },
                colors = RadioButtonDefaults.colors(selectedColor = SblRed)
            )
            Text("Automatisch (Systemauswahl)", color = Color.White, fontSize = 14.sp)
        }
        vm.devices.forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = d.deviceId != null && vm.selectedDeviceId == d.deviceId,
                    onClick = { vm.selectDevice(d) },
                    enabled = d.connected,
                    colors = RadioButtonDefaults.colors(selectedColor = SblRed)
                )
                Column(Modifier.weight(1f)) {
                    Text(d.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(d.kind, color = SblMuted, fontSize = 12.sp)
                }
                Text(
                    text = if (d.connected) "Verbunden" else "Nicht verbunden",
                    color = if (d.connected) SblRed else SblMuted,
                    fontSize = 12.sp
                )
            }
        }
        Text(
            "Die Auswahl gilt für den In-App-Player. Für andere Apps wechselt man das Ausgabegerät im Android-System.",
            color = SblMuted,
            fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openSystemOutputPicker(ctx) }) { Text("System-Ausgabe", fontSize = 13.sp) }
            OutlinedButton(onClick = { openBluetoothSettings(ctx) }) { Text("BT-Einstellungen", fontSize = 13.sp) }
        }
    }
}

// ------------------------------------------------------------------- Equalizer

private fun signed(v: Int): String = if (v > 0) "+$v dB" else "$v dB"

@Composable
private fun ToneSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(signed(value), color = SblRed, fontSize = 15.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = -12f..12f,
            steps = 23,
            colors = sliderColors()
        )
    }
}

@Composable
private fun EqualizerCard(vm: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    SblCard("Equalizer (nur In-App-Player)") {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EQ_PRESETS.forEach { p ->
                FilterChip(
                    selected = vm.presetName == p.name,
                    onClick = { vm.applyPreset(p) },
                    label = { Text(p.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SblRed,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
        Text("Einstellung: ${vm.presetName}", color = SblMuted, fontSize = 12.sp)
        ToneSlider("Bass", vm.bass) { vm.setTone(newBass = it) }
        ToneSlider("Mitten", vm.mid) { vm.setTone(newMid = it) }
        ToneSlider("Höhen", vm.treble) { vm.setTone(newTreble = it) }
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Einzelne Bänder ausblenden" else "Einzelne Bänder (manuell)", color = SblRed)
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (vm.bands.isEmpty()) {
                    Text(
                        "Die Bänder des Geräte-Equalizers erscheinen, sobald eine Datei geladen ist.",
                        color = SblMuted,
                        fontSize = 12.sp
                    )
                }
                vm.bands.forEach { b ->
                    val label = if (b.freqHz >= 1000) fmt(b.freqHz / 1000f, "%.1f kHz") else "${b.freqHz} Hz"
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, color = Color.White, fontSize = 13.sp)
                        Text(fmt(b.db, "%+.1f dB"), color = SblRed, fontSize = 13.sp)
                    }
                    Slider(
                        value = b.db.coerceIn(b.minDb, b.maxDb),
                        onValueChange = { vm.setBand(b.index, it) },
                        valueRange = b.minDb..b.maxDb,
                        colors = sliderColors()
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- Status

@Composable
private fun StatusRow(label: String, ok: Boolean, okText: String = "aktiv", noText: String = "nicht verfügbar") {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White, fontSize = 13.sp)
        Text(if (ok) okText else noText, color = if (ok) SblRed else SblMuted, fontSize = 13.sp)
    }
}

@Composable
private fun StatusCard(vm: MainViewModel) {
    SblCard("Technischer Status") {
        Text(
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}",
            color = SblMuted,
            fontSize = 12.sp
        )
        val loaded = vm.fileName != null
        StatusRow("Equalizer (AudioEffect)", vm.status.equalizer, noText = if (loaded) "nicht verfügbar" else "wartet auf Datei")
        StatusRow("LoudnessEnhancer (Verstärkung)", vm.status.loudness, noText = if (loaded) "nicht verfügbar" else "wartet auf Datei")
        StatusRow(
            "Limiter (DynamicsProcessing, ab Android 9)",
            vm.status.limiter,
            noText = if (Build.VERSION.SDK_INT < 28) "nicht unterstützt" else if (loaded) "nicht verfügbar" else "wartet auf Datei"
        )
        Text(
            "Clipping-Schutz: ab Android 9 zusätzlicher Limiter; sonst nur die interne Begrenzung des LoudnessEnhancers.",
            color = SblMuted,
            fontSize = 12.sp
        )
    }
}
