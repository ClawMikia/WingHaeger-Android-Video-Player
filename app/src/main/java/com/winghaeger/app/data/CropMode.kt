package com.winghaeger.app.data

enum class CropMode(val storageKey: String, val displayName: String) {
    FIT("fit", "Fit"),
    FILL("fill", "Fill"),
    STRETCH("stretch", "Stretch"),
    CROP_4_3("crop_4_3", "4:3"),
    CROP_16_9("crop_16_9", "16:9");
    companion object { fun fromKey(key: String?) = entries.find { it.storageKey == key } ?: FIT }
}
