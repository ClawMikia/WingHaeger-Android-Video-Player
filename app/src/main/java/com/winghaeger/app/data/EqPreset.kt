package com.winghaeger.app.data

enum class EqPreset(val storageKey: String, val displayName: String) {
    FLAT("flat", "Flat"),
    BASS_BOOST("bass_boost", "Bass Boost"),
    TREBLE("treble", "Treble");
    companion object { fun fromKey(key: String?) = entries.find { it.storageKey == key } ?: FLAT }
}
