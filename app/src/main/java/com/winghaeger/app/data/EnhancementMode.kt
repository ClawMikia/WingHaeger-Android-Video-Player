package com.winghaeger.app.data

enum class EnhancementMode(val storageKey: String, val displayName: String) {
    NONE("none", "None"),
    VIVID_HD("vivid_hd", "Vivid HD"),
    CINEMA_CONTRAST("cinema_contrast", "Cinema Contrast"),
    WARM_FILM("warm_film", "Warm Film"),
    COOL_HDR_SIM("cool_hdr", "Cool HDR"),
    AMOLED("amoled", "AMOLED"),
    NIGHT_MODE("night_mode", "Night Mode"),
    ANIME("anime", "Anime"),
    EYE_COMFORT("eye_comfort", "Eye Comfort"),
    VIVID_OUTDOOR("vivid_outdoor", "Vivid Outdoor"),
    CINEMATIC_DARK("cinematic_dark", "Cinematic Dark");
    companion object { fun fromKey(key: String?) = entries.find { it.storageKey == key } ?: NONE }
}
