package com.winghaeger.app.data

import android.content.Context
import com.winghaeger.app.util.ThumbnailExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoRepository(context: Context) {
    private val app = context.applicationContext
    private val db  = WingDbHelper(app)
    private val prefs = AppPrefs(app)

    // ── Video scanning ────────────────────────────────────────────────────────

    suspend fun importScanResults(entities: List<VideoEntity>, withThumbnails: Boolean = true) =
        withContext(Dispatchers.IO) {
            for (e in entities) {
                val existingId = db.getIdByUri(e.uriString)
                val prepared = if (existingId == null) {
                    e.copy(
                        seekJumpSec = prefs.defaultSeekJumpSec,
                        enhancement = prefs.defaultEnhancement,
                    )
                } else e
                val id = db.insertOrMergeFromScan(prepared)
                if (withThumbnails && id > 0) {
                    val existing = db.getById(id)
                    if (existing?.thumbnail == null) {
                        val jpeg = ThumbnailExtractor.extractJpeg(app, e.contentUri)
                        if (jpeg != null) db.updateThumbnail(id, jpeg)
                    }
                }
            }
        }

    // ── Video queries ─────────────────────────────────────────────────────────

    fun getById(id: Long): VideoEntity?               = db.getById(id)
    fun listAllByFolder(): List<VideoEntity>          = db.listAllOrderedByFolder()
    fun listFavorites(): List<VideoEntity>            = db.listFavorites()
    fun listPlaybackMemory(): List<VideoEntity>       = db.listPlaybackMemory()
    fun listContinueWatching(): List<VideoEntity>     = db.listContinueWatching()
    fun listRecentlyPlayed(): List<VideoEntity>       = db.listRecentlyPlayed()
    fun search(query: String): List<VideoEntity>      = db.searchByTitle(query)

    fun savePlaybackPosition(id: Long, positionMs: Long) =
        db.updatePlaybackState(id, positionMs)

    fun savePreferences(entity: VideoEntity) = db.updatePreferences(entity)
    fun setFavorite(id: Long, favorite: Boolean) = db.setFavorite(id, favorite)

    // ── Chapter Markers ───────────────────────────────────────────────────────

    fun addChapter(videoId: Long, positionMs: Long, label: String, auto: Boolean = false): Long =
        db.insertChapter(ChapterMarker(
            videoId = videoId, positionMs = positionMs, label = label,
            isAutoDetected = auto, createdAt = System.currentTimeMillis()
        ))

    fun updateChapter(chapter: ChapterMarker) = db.updateChapter(chapter)
    fun deleteChapter(id: Long) = db.deleteChapter(id)
    fun deleteAllChapters(videoId: Long) = db.deleteChaptersForVideo(videoId)
    fun listChapters(videoId: Long): List<ChapterMarker> = db.listChaptersForVideo(videoId)

    // ── Timeline Skips ────────────────────────────────────────────────────────

    fun addSkip(videoId: Long, startMs: Long, endMs: Long, label: String = "Skip"): Long =
        db.insertSkip(TimelineSkip(videoId = videoId, startMs = startMs, endMs = endMs, label = label))

    fun deleteSkip(id: Long) = db.deleteSkip(id)
    fun deleteAllSkips(videoId: Long) = db.deleteSkipsForVideo(videoId)
    fun listSkips(videoId: Long): List<TimelineSkip> = db.listSkipsForVideo(videoId)

    // ── Playlists ─────────────────────────────────────────────────────────────

    fun createPlaylist(title: String): Long = db.insertPlaylist(title)
    fun deletePlaylist(id: Long) = db.deletePlaylist(id)
    fun renamePlaylist(id: Long, newTitle: String) = db.renamePlaylist(id, newTitle)
    fun listPlaylists(): List<Pair<Long, String>> = db.listPlaylists()
    fun addVideoToPlaylist(playlistId: Long, videoId: Long) = db.addVideoToPlaylist(playlistId, videoId)
    fun removeVideoFromPlaylist(playlistId: Long, videoId: Long) = db.removeVideoFromPlaylist(playlistId, videoId)
    fun getVideosInPlaylist(playlistId: Long): List<VideoEntity> = db.getVideosInPlaylist(playlistId)
}
