package com.winghaeger.app.data

import android.net.Uri

data class VideoEntity(
    val id: Long = 0L,
    val uriString: String,
    val title: String,
    val folderGroup: String,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val thumbnail: ByteArray? = null,
    val positionMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val favorite: Boolean = false,
    val seekJumpSec: Int = 10,
    val autoPlayNext: Boolean = false,
    val shufflePlaylist: Boolean = false,
    val loopPlayback: Boolean = false,
    val enhancement: EnhancementMode = EnhancementMode.NONE,
    val lastPlayedAt: Long = 0L,
    val volumeLevel: Float = 1.0f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val hue: Float = 0f,
    val sharpness: Float = 0f,
    val zoomLevel: Float = 1f,
    val cropMode: CropMode = CropMode.FIT,
    val audioBoost: Float = 1f,
    val eqPreset: EqPreset = EqPreset.FLAT,
    val subtitleTrackIndex: Int = -1,
    val subtitleOffsetMs: Long = 0L,
    val subtitleSizeSp: Float = 16f,
    val subtitleBold: Boolean = false,
    val subtitleBackgroundAlpha: Int = 128,
    val preferredOrientation: Int = -1,
    val activeProfileId: Long = 0L,
) {
    val contentUri: Uri get() = Uri.parse(uriString)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VideoEntity
        if (id != other.id) return false
        if (uriString != other.uriString) return false
        if (thumbnail != null) {
            if (other.thumbnail == null) return false
            if (!thumbnail.contentEquals(other.thumbnail)) return false
        } else if (other.thumbnail != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + uriString.hashCode()
        result = 31 * result + (thumbnail?.contentHashCode() ?: 0)
        return result
    }
}
