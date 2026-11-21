package com.sbl.superboost.audio

data class EqPreset(val name: String, val bass: Int, val mid: Int, val treble: Int)

val EQ_PRESETS: List<EqPreset> = listOf(
    EqPreset("Flat", 0, 0, 0),
    EqPreset("Bass+", 8, 0, -1),
    EqPreset("Vocal", -2, 4, 2),
    EqPreset("Klar", -1, 1, 6),
    EqPreset("Rock", 6, -2, 5),
    EqPreset("Elektro", 7, -1, 4)
)
