package com.winghaeger.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class WingDbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_VIDEOS)
        db.execSQL("CREATE INDEX idx_videos_folder   ON $TABLE_VIDEOS($COL_FOLDER)")
        db.execSQL("CREATE INDEX idx_videos_favorite ON $TABLE_VIDEOS($COL_FAVORITE)")
        db.execSQL("CREATE INDEX idx_videos_played   ON $TABLE_VIDEOS($COL_LAST_PLAYED)")
        db.execSQL(CREATE_CHAPTERS)
        db.execSQL("CREATE INDEX idx_chapters_video  ON $TABLE_CHAPTERS($CHA_VIDEO_ID)")
        db.execSQL(CREATE_PLAYLISTS)
        db.execSQL(CREATE_PLAYLIST_VIDEOS)
        db.execSQL(CREATE_TIMELINE_SKIPS)
        db.execSQL("CREATE INDEX idx_skips_video     ON $TABLE_TIMELINE_SKIPS($SKI_VIDEO_ID)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) {
            runCatching { db.execSQL(CREATE_PLAYLISTS) }
            runCatching { db.execSQL(CREATE_PLAYLIST_VIDEOS) }
        }
        if (oldVersion < 6) {
            // Ensure timeline_skips table exists (fixing potential failed migration in v5)
            runCatching {
                db.execSQL(CREATE_TIMELINE_SKIPS)
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_skips_video ON $TABLE_TIMELINE_SKIPS($SKI_VIDEO_ID)")
            }
        }
        if (oldVersion < 7) {
            runCatching { db.execSQL("ALTER TABLE $TABLE_VIDEOS ADD COLUMN shuffle_playlist INTEGER NOT NULL DEFAULT 0") }
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Safe fallback: rebuild the schema so the app can launch instead of crashing on a version mismatch.
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TIMELINE_SKIPS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PLAYLIST_VIDEOS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PLAYLISTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CHAPTERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_VIDEOS")
        onCreate(db)
    }

    // ── Videos ────────────────────────────────────────────────────────────────

    fun insertOrMergeFromScan(entity: VideoEntity): Long {
        val db = writableDatabase
        val existingId = getIdByUri(entity.uriString)
        return existingId?.let { id ->
            val cv = ContentValues().apply {
                put(COL_TITLE, entity.title)
                put(COL_FOLDER, entity.folderGroup)
                put(COL_DURATION_MS, entity.durationMs)
                put(COL_SIZE_BYTES, entity.sizeBytes)
            }
            db.update(TABLE_VIDEOS, cv, "$COL_ID = ?", arrayOf(id.toString()))
            id
        } ?: db.insert(TABLE_VIDEOS, null, entity.toContentValues())
    }

    fun getIdByUri(uriString: String): Long? {
        readableDatabase.query(
            TABLE_VIDEOS, arrayOf(COL_ID), "$COL_URI = ?", arrayOf(uriString),
            null, null, null
        ).use { c -> if (c.moveToFirst()) return c.getLong(0) }
        return null
    }

    fun updateThumbnail(id: Long, bytes: ByteArray?) {
        val cv = ContentValues().apply { put(COL_THUMB, bytes) }
        writableDatabase.update(TABLE_VIDEOS, cv, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun updatePlaybackState(id: Long, positionMs: Long, lastPlayedAt: Long = System.currentTimeMillis()) {
        val cv = ContentValues().apply {
            put(COL_POSITION_MS, positionMs)
            put(COL_LAST_PLAYED, lastPlayedAt)
        }
        writableDatabase.update(TABLE_VIDEOS, cv, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun updatePreferences(entity: VideoEntity) {
        val cv = ContentValues().apply {
            put(COL_TITLE, entity.title)
            put(COL_DURATION_MS, entity.durationMs)
            put(COL_TRIM_START_MS, entity.trimStartMs)
            put(COL_TRIM_END_MS, entity.trimEndMs)
            put(COL_FAVORITE, if (entity.favorite) 1 else 0)
            put(COL_SEEK_JUMP_SEC, entity.seekJumpSec)
            put(COL_AUTO_NEXT, if (entity.autoPlayNext) 1 else 0)
            put(COL_LOOP, if (entity.loopPlayback) 1 else 0)
            put(COL_ENHANCEMENT, entity.enhancement.storageKey)
            put(COL_VOLUME, entity.volumeLevel)
            put(COL_BRIGHTNESS, entity.brightness)
            put(COL_CONTRAST, entity.contrast)
            put(COL_SATURATION, entity.saturation)
            put(COL_HUE, entity.hue)
            put(COL_SHARPNESS, entity.sharpness)
            put(COL_ZOOM, entity.zoomLevel)
            put(COL_CROP, entity.cropMode.storageKey)
            put(COL_AUDIO_BOOST, entity.audioBoost)
            put(COL_EQ_PRESET, entity.eqPreset.storageKey)
            put(COL_SUB_TRACK, entity.subtitleTrackIndex)
            put(COL_SUB_OFFSET, entity.subtitleOffsetMs)
            put(COL_SUB_SIZE, entity.subtitleSizeSp)
            put(COL_SUB_BOLD, if (entity.subtitleBold) 1 else 0)
            put(COL_SUB_BG_ALPHA, entity.subtitleBackgroundAlpha)
            put(COL_ORIENTATION, entity.preferredOrientation)
            put(COL_ACTIVE_PROFILE, entity.activeProfileId)
        }
        writableDatabase.update(TABLE_VIDEOS, cv, "$COL_ID = ?", arrayOf(entity.id.toString()))
    }

    fun setFavorite(id: Long, favorite: Boolean) {
        val cv = ContentValues().apply { put(COL_FAVORITE, if (favorite) 1 else 0) }
        writableDatabase.update(TABLE_VIDEOS, cv, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun getById(id: Long): VideoEntity? {
        readableDatabase.query(
            TABLE_VIDEOS, null, "$COL_ID = ?", arrayOf(id.toString()),
            null, null, null
        ).use { c -> if (c.moveToFirst()) return c.toEntity() }
        return null
    }

    fun listAllOrderedByFolder(): List<VideoEntity> {
        val list = mutableListOf<VideoEntity>()
        readableDatabase.query(
            TABLE_VIDEOS, null, null, null, null, null,
            "$COL_FOLDER COLLATE NOCASE ASC, $COL_TITLE COLLATE NOCASE ASC"
        ).use { c -> while (c.moveToNext()) list.add(c.toEntity()) }
        return list
    }

    fun listFavorites(): List<VideoEntity> {
        val list = mutableListOf<VideoEntity>()
        readableDatabase.query(
            TABLE_VIDEOS, null, "$COL_FAVORITE = 1", null, null, null, "$COL_LAST_PLAYED DESC"
        ).use { c -> while (c.moveToNext()) list.add(c.toEntity()) }
        return list
    }

    fun listContinueWatching(limit: Int = 50): List<VideoEntity> {
        val list = mutableListOf<VideoEntity>()
        // Videos with saved position > 5 seconds and < 95% complete
        readableDatabase.rawQuery(
            """SELECT * FROM $TABLE_VIDEOS 
               WHERE $COL_POSITION_MS > 5000 
               AND $COL_DURATION_MS > 0 
               AND CAST($COL_POSITION_MS AS REAL) / $COL_DURATION_MS < 0.95
               ORDER BY $COL_LAST_PLAYED DESC 
               LIMIT ?""",
            arrayOf(limit.toString())
        ).use { c -> while (c.moveToNext()) list.add(c.toEntity()) }
        return list
    }

    fun listRecentlyPlayed(limit: Int = 100): List<VideoEntity> {
        val list = mutableListOf<VideoEntity>()
        readableDatabase.query(
            TABLE_VIDEOS, null, "$COL_LAST_PLAYED > 0", null, null, null,
            "$COL_LAST_PLAYED DESC", limit.toString()
        ).use { c -> while (c.moveToNext()) list.add(c.toEntity()) }
        return list
    }

    fun listPlaybackMemory(limit: Int = 200): List<VideoEntity> = listRecentlyPlayed(limit)

    fun searchByTitle(query: String): List<VideoEntity> {
        if (query.isBlank()) return emptyList()
        val list = mutableListOf<VideoEntity>()
        readableDatabase.query(
            TABLE_VIDEOS, null, "$COL_TITLE LIKE ? COLLATE NOCASE", arrayOf("%${query.trim()}%"),
            null, null, "$COL_FOLDER COLLATE NOCASE ASC, $COL_TITLE COLLATE NOCASE ASC"
        ).use { c -> while (c.moveToNext()) list.add(c.toEntity()) }
        return list
    }

    fun deleteById(id: Long) {
        writableDatabase.delete(TABLE_VIDEOS, "$COL_ID = ?", arrayOf(id.toString()))
    }

    // ── Chapter Markers ────────────────────────────────────────────────────────

    fun insertChapter(c: ChapterMarker): Long =
        writableDatabase.insert(TABLE_CHAPTERS, null, c.toContentValues())

    fun updateChapter(c: ChapterMarker) {
        writableDatabase.update(TABLE_CHAPTERS, c.toContentValues(), "$CHA_ID = ?", arrayOf(c.id.toString()))
    }

    fun deleteChapter(id: Long) {
        writableDatabase.delete(TABLE_CHAPTERS, "$CHA_ID = ?", arrayOf(id.toString()))
    }

    fun deleteChaptersForVideo(videoId: Long) {
        writableDatabase.delete(TABLE_CHAPTERS, "$CHA_VIDEO_ID = ?", arrayOf(videoId.toString()))
    }

    fun listChaptersForVideo(videoId: Long): List<ChapterMarker> {
        val list = mutableListOf<ChapterMarker>()
        readableDatabase.query(
            TABLE_CHAPTERS, null, "$CHA_VIDEO_ID = ?", arrayOf(videoId.toString()),
            null, null, "$CHA_POSITION_MS ASC"
        ).use { c -> while (c.moveToNext()) list.add(c.toChapter()) }
        return list
    }

    // ── Timeline Skips ────────────────────────────────────────────────────────

    fun insertSkip(s: TimelineSkip): Long =
        writableDatabase.insert(TABLE_TIMELINE_SKIPS, null, s.toContentValues())

    fun deleteSkip(id: Long) {
        writableDatabase.delete(TABLE_TIMELINE_SKIPS, "$SKI_ID = ?", arrayOf(id.toString()))
    }

    fun deleteSkipsForVideo(videoId: Long) {
        writableDatabase.delete(TABLE_TIMELINE_SKIPS, "$SKI_VIDEO_ID = ?", arrayOf(videoId.toString()))
    }

    fun listSkipsForVideo(videoId: Long): List<TimelineSkip> {
        val list = mutableListOf<TimelineSkip>()
        readableDatabase.query(
            TABLE_TIMELINE_SKIPS, null, "$SKI_VIDEO_ID = ?", arrayOf(videoId.toString()),
            null, null, "$SKI_START_MS ASC"
        ).use { c -> while (c.moveToNext()) list.add(c.toSkip()) }
        return list
    }

    // ── Playlists ──────────────────────────────────────────────────────────────

    fun insertPlaylist(title: String): Long {
        val cv = ContentValues().apply {
            put(PLA_TITLE, title)
            put(PLA_CREATED_AT, System.currentTimeMillis())
        }
        return writableDatabase.insert(TABLE_PLAYLISTS, null, cv)
    }

    fun deletePlaylist(id: Long) {
        writableDatabase.delete(TABLE_PLAYLISTS, "$PLA_ID = ?", arrayOf(id.toString()))
        writableDatabase.delete(TABLE_PLAYLIST_VIDEOS, "$PLV_PLAYLIST_ID = ?", arrayOf(id.toString()))
    }

    fun renamePlaylist(id: Long, newTitle: String) {
        val cv = ContentValues().apply { put(PLA_TITLE, newTitle) }
        writableDatabase.update(TABLE_PLAYLISTS, cv, "$PLA_ID = ?", arrayOf(id.toString()))
    }

    fun addVideoToPlaylist(playlistId: Long, videoId: Long) {
        val cv = ContentValues().apply {
            put(PLV_PLAYLIST_ID, playlistId)
            put(PLV_VIDEO_ID, videoId)
            // Auto-increment position? 
            put(PLV_POSITION, System.currentTimeMillis())
        }
        writableDatabase.insert(TABLE_PLAYLIST_VIDEOS, null, cv)
    }

    fun removeVideoFromPlaylist(playlistId: Long, videoId: Long) {
        writableDatabase.delete(TABLE_PLAYLIST_VIDEOS, "$PLV_PLAYLIST_ID = ? AND $PLV_VIDEO_ID = ?",
            arrayOf(playlistId.toString(), videoId.toString()))
    }

    fun listPlaylists(): List<Pair<Long, String>> {
        val list = mutableListOf<Pair<Long, String>>()
        readableDatabase.query(TABLE_PLAYLISTS, null, null, null, null, null, "$PLA_TITLE ASC")
            .use { c -> while (c.moveToNext()) {
                list.add(c.getLong(c.getColumnIndex(PLA_ID)) to c.getString(c.getColumnIndex(PLA_TITLE)))
            }}
        return list
    }

    fun getVideosInPlaylist(playlistId: Long): List<VideoEntity> {
        val list = mutableListOf<VideoEntity>()
        val query = """
            SELECT v.* FROM $TABLE_VIDEOS v
            JOIN $TABLE_PLAYLIST_VIDEOS pv ON v.$COL_ID = pv.$PLV_VIDEO_ID
            WHERE pv.$PLV_PLAYLIST_ID = ?
            ORDER BY pv.$PLV_POSITION ASC
        """
        readableDatabase.rawQuery(query, arrayOf(playlistId.toString())).use { c ->
            while (c.moveToNext()) list.add(c.toEntity())
        }
        return list
    }

    companion object {
        const val DB_NAME    = "wing_haeger.db"
        const val DB_VERSION = 6

        // ── videos table ──────────────────────────────────────────────────────
        const val TABLE_VIDEOS          = "videos"
        const val COL_ID                = "id"
        const val COL_URI               = "uri"
        const val COL_TITLE             = "title"
        const val COL_FOLDER            = "folder_group"
        const val COL_DURATION_MS       = "duration_ms"
        const val COL_SIZE_BYTES        = "size_bytes"
        const val COL_THUMB             = "thumbnail"
        const val COL_POSITION_MS       = "position_ms"
        const val COL_PITCH_SEMITONES   = "pitch_semitones"
        const val COL_TRIM_START_MS     = "trim_start_ms"
        const val COL_TRIM_END_MS       = "trim_end_ms"
        const val COL_FAVORITE          = "favorite"
        const val COL_SEEK_JUMP_SEC     = "seek_jump_sec"
        const val COL_AUTO_NEXT         = "auto_play_next"
        const val COL_LOOP              = "loop_playback"
        const val COL_SHUFFLE           = "shuffle_playlist"
        const val COL_ENHANCEMENT       = "enhancement"
        const val COL_LAST_PLAYED       = "last_played_at"
        const val COL_VOLUME            = "volume_level"
        const val COL_BRIGHTNESS        = "brightness"
        const val COL_CONTRAST          = "contrast"
        const val COL_SATURATION        = "saturation"
        const val COL_HUE               = "hue"
        const val COL_SHARPNESS         = "sharpness"
        const val COL_ZOOM              = "zoom_level"
        const val COL_CROP              = "crop_mode"
        const val COL_AUDIO_BOOST       = "audio_boost"
        const val COL_EQ_PRESET         = "eq_preset"
        const val COL_SUB_TRACK         = "subtitle_track"
        const val COL_SUB_OFFSET        = "subtitle_offset_ms"
        const val COL_SUB_SIZE          = "subtitle_size_sp"
        const val COL_SUB_BOLD          = "subtitle_bold"
        const val COL_SUB_BG_ALPHA      = "subtitle_bg_alpha"
        const val COL_ORIENTATION       = "preferred_orientation"
        const val COL_ACTIVE_PROFILE    = "active_profile_id"

        private val CREATE_VIDEOS = """
            CREATE TABLE $TABLE_VIDEOS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_URI TEXT NOT NULL UNIQUE,
                $COL_TITLE TEXT NOT NULL,
                $COL_FOLDER TEXT NOT NULL,
                $COL_DURATION_MS INTEGER NOT NULL DEFAULT 0,
                $COL_SIZE_BYTES INTEGER NOT NULL DEFAULT 0,
                $COL_THUMB BLOB,
                $COL_POSITION_MS INTEGER NOT NULL DEFAULT 0,
                $COL_PITCH_SEMITONES INTEGER NOT NULL DEFAULT 0,
                $COL_TRIM_START_MS INTEGER NOT NULL DEFAULT 0,
                $COL_TRIM_END_MS INTEGER NOT NULL DEFAULT 0,
                $COL_FAVORITE INTEGER NOT NULL DEFAULT 0,
                $COL_SEEK_JUMP_SEC INTEGER NOT NULL DEFAULT 10,
                $COL_AUTO_NEXT INTEGER NOT NULL DEFAULT 0,
                $COL_LOOP INTEGER NOT NULL DEFAULT 0,
                $COL_ENHANCEMENT TEXT NOT NULL DEFAULT 'NONE',
                $COL_LAST_PLAYED INTEGER NOT NULL DEFAULT 0,

                $COL_VOLUME REAL NOT NULL DEFAULT 1.0,
                $COL_BRIGHTNESS REAL NOT NULL DEFAULT 0.0,
                $COL_CONTRAST REAL NOT NULL DEFAULT 1.0,
                $COL_SATURATION REAL NOT NULL DEFAULT 1.0,
                $COL_HUE REAL NOT NULL DEFAULT 0.0,
                $COL_SHARPNESS REAL NOT NULL DEFAULT 0.0,
                $COL_ZOOM REAL NOT NULL DEFAULT 1.0,
                $COL_CROP TEXT NOT NULL DEFAULT 'FIT',
                $COL_AUDIO_BOOST REAL NOT NULL DEFAULT 1.0,
                $COL_EQ_PRESET TEXT NOT NULL DEFAULT 'FLAT',
                $COL_SUB_TRACK INTEGER NOT NULL DEFAULT -1,
                $COL_SUB_OFFSET INTEGER NOT NULL DEFAULT 0,
                $COL_SUB_SIZE REAL NOT NULL DEFAULT 16.0,
                $COL_SUB_BOLD INTEGER NOT NULL DEFAULT 0,
                $COL_SUB_BG_ALPHA INTEGER NOT NULL DEFAULT 128,
                $COL_ORIENTATION INTEGER NOT NULL DEFAULT -1,
                $COL_ACTIVE_PROFILE INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()

        // ── chapters table ────────────────────────────────────────────────────
        const val TABLE_CHAPTERS        = "chapter_markers"
        const val CHA_ID                = "id"
        const val CHA_VIDEO_ID          = "video_id"
        const val CHA_POSITION_MS       = "position_ms"
        const val CHA_LABEL             = "label"
        const val CHA_AUTO              = "is_auto_detected"
        const val CHA_CREATED_AT        = "created_at"

        private val CREATE_CHAPTERS = """
            CREATE TABLE $TABLE_CHAPTERS (
                $CHA_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $CHA_VIDEO_ID INTEGER NOT NULL,
                $CHA_POSITION_MS INTEGER NOT NULL,
                $CHA_LABEL TEXT NOT NULL,
                $CHA_AUTO INTEGER NOT NULL DEFAULT 0,
                $CHA_CREATED_AT INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()

        // ── playlists table ────────────────────────────────────────────────────
        const val TABLE_PLAYLISTS       = "playlists"
        const val PLA_ID                = "id"
        const val PLA_TITLE             = "title"
        const val PLA_CREATED_AT        = "created_at"

        private val CREATE_PLAYLISTS = """
            CREATE TABLE $TABLE_PLAYLISTS (
                $PLA_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $PLA_TITLE TEXT NOT NULL,
                $PLA_CREATED_AT INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()

        // ── playlist_videos table (junction) ───────────────────────────────────
        const val TABLE_PLAYLIST_VIDEOS = "playlist_videos"
        const val PLV_PLAYLIST_ID       = "playlist_id"
        const val PLV_VIDEO_ID          = "video_id"
        const val PLV_POSITION          = "position"

        private val CREATE_PLAYLIST_VIDEOS = """
            CREATE TABLE $TABLE_PLAYLIST_VIDEOS (
                $PLV_PLAYLIST_ID INTEGER NOT NULL,
                $PLV_VIDEO_ID INTEGER NOT NULL,
                $PLV_POSITION INTEGER NOT NULL,
                PRIMARY KEY ($PLV_PLAYLIST_ID, $PLV_VIDEO_ID)
            )
        """.trimIndent()

        // ── timeline_skips table ──────────────────────────────────────────────
        const val TABLE_TIMELINE_SKIPS  = "timeline_skips"
        const val SKI_ID                = "id"
        const val SKI_VIDEO_ID          = "video_id"
        const val SKI_START_MS          = "start_ms"
        const val SKI_END_MS            = "end_ms"
        const val SKI_LABEL             = "label"
        const val SKI_CREATED_AT        = "created_at"

        private val CREATE_TIMELINE_SKIPS = """
            CREATE TABLE IF NOT EXISTS $TABLE_TIMELINE_SKIPS (
                $SKI_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $SKI_VIDEO_ID INTEGER NOT NULL,
                $SKI_START_MS INTEGER NOT NULL,
                $SKI_END_MS INTEGER NOT NULL,
                $SKI_LABEL TEXT NOT NULL,
                $SKI_CREATED_AT INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()
    }
}

// ── Extension: VideoEntity ↔ ContentValues ──────────────────────────────────

private fun VideoEntity.toContentValues(): ContentValues = ContentValues().apply {
    put(WingDbHelper.COL_URI,              uriString)
    put(WingDbHelper.COL_TITLE,            title)
    put(WingDbHelper.COL_FOLDER,           folderGroup)
    put(WingDbHelper.COL_DURATION_MS,      durationMs)
    put(WingDbHelper.COL_SIZE_BYTES,       sizeBytes)
    put(WingDbHelper.COL_THUMB,            thumbnail)
    put(WingDbHelper.COL_POSITION_MS,      positionMs)
    put(WingDbHelper.COL_TRIM_START_MS,    trimStartMs)
    put(WingDbHelper.COL_TRIM_END_MS,      trimEndMs)
    put(WingDbHelper.COL_FAVORITE,         if (favorite) 1 else 0)
    put(WingDbHelper.COL_SEEK_JUMP_SEC,    seekJumpSec)
    put(WingDbHelper.COL_AUTO_NEXT,        if (autoPlayNext) 1 else 0)
    put(WingDbHelper.COL_LOOP,             if (loopPlayback) 1 else 0)
    put(WingDbHelper.COL_ENHANCEMENT,      enhancement.storageKey)
    put(WingDbHelper.COL_LAST_PLAYED,      lastPlayedAt)
    put(WingDbHelper.COL_VOLUME,           volumeLevel)
    put(WingDbHelper.COL_BRIGHTNESS,       brightness)
    put(WingDbHelper.COL_CONTRAST,         contrast)
    put(WingDbHelper.COL_SATURATION,       saturation)
    put(WingDbHelper.COL_HUE,              hue)
    put(WingDbHelper.COL_SHARPNESS,        sharpness)
    put(WingDbHelper.COL_ZOOM,             zoomLevel)
    put(WingDbHelper.COL_CROP,             cropMode.storageKey)
    put(WingDbHelper.COL_AUDIO_BOOST,      audioBoost)
    put(WingDbHelper.COL_EQ_PRESET,        eqPreset.storageKey)
    put(WingDbHelper.COL_SUB_TRACK,        subtitleTrackIndex)
    put(WingDbHelper.COL_SUB_OFFSET,       subtitleOffsetMs)
    put(WingDbHelper.COL_SUB_SIZE,         subtitleSizeSp)
    put(WingDbHelper.COL_SUB_BOLD,         if (subtitleBold) 1 else 0)
    put(WingDbHelper.COL_SUB_BG_ALPHA,     subtitleBackgroundAlpha)
    put(WingDbHelper.COL_ORIENTATION,      preferredOrientation)
    put(WingDbHelper.COL_ACTIVE_PROFILE,   activeProfileId)
}

private fun Cursor.toEntity(): VideoEntity {
    fun str(col: String): String? = getColumnIndex(col).takeIf { it >= 0 }?.let { getString(it) }
    fun lng(col: String, def: Long = 0L): Long = getColumnIndex(col).takeIf { it >= 0 }?.let { getLong(it) } ?: def
    fun int(col: String, def: Int = 0): Int = getColumnIndex(col).takeIf { it >= 0 }?.let { getInt(it) } ?: def
    fun flt(col: String, def: Float = 0f): Float = getColumnIndex(col).takeIf { it >= 0 }?.let { getFloat(it) } ?: def
    val thumbIdx = getColumnIndex(WingDbHelper.COL_THUMB)
    val thumb = if (thumbIdx >= 0 && !isNull(thumbIdx)) {
        getBlob(thumbIdx)
    } else {
        null
    }
    return VideoEntity(
        id                     = lng(WingDbHelper.COL_ID),
        uriString              = str(WingDbHelper.COL_URI) ?: "",
        title                  = str(WingDbHelper.COL_TITLE) ?: "",
        folderGroup            = str(WingDbHelper.COL_FOLDER) ?: "",
        durationMs             = lng(WingDbHelper.COL_DURATION_MS),
        sizeBytes              = lng(WingDbHelper.COL_SIZE_BYTES),
        thumbnail              = thumb,
        positionMs             = lng(WingDbHelper.COL_POSITION_MS),
        trimStartMs            = lng(WingDbHelper.COL_TRIM_START_MS),
        trimEndMs              = lng(WingDbHelper.COL_TRIM_END_MS),
        favorite               = int(WingDbHelper.COL_FAVORITE) == 1,
        seekJumpSec            = int(WingDbHelper.COL_SEEK_JUMP_SEC, 10),
        autoPlayNext           = int(WingDbHelper.COL_AUTO_NEXT) == 1,
        loopPlayback           = int(WingDbHelper.COL_LOOP) == 1,
        shufflePlaylist        = int(WingDbHelper.COL_SHUFFLE) == 1,
        enhancement            = EnhancementMode.fromKey(str(WingDbHelper.COL_ENHANCEMENT)),
        lastPlayedAt           = lng(WingDbHelper.COL_LAST_PLAYED),
        volumeLevel            = flt(WingDbHelper.COL_VOLUME, 1f),
        brightness             = flt(WingDbHelper.COL_BRIGHTNESS, 0f),
        contrast               = flt(WingDbHelper.COL_CONTRAST, 1f),
        saturation             = flt(WingDbHelper.COL_SATURATION, 1f),
        hue                    = flt(WingDbHelper.COL_HUE, 0f),
        sharpness              = flt(WingDbHelper.COL_SHARPNESS, 0f),
        zoomLevel              = flt(WingDbHelper.COL_ZOOM, 1f),
        cropMode               = CropMode.fromKey(str(WingDbHelper.COL_CROP)),
        audioBoost             = flt(WingDbHelper.COL_AUDIO_BOOST, 1f),
        eqPreset               = EqPreset.fromKey(str(WingDbHelper.COL_EQ_PRESET)),
        subtitleTrackIndex     = int(WingDbHelper.COL_SUB_TRACK, -1),
        subtitleOffsetMs       = lng(WingDbHelper.COL_SUB_OFFSET),
        subtitleSizeSp         = flt(WingDbHelper.COL_SUB_SIZE, 16f),
        subtitleBold           = int(WingDbHelper.COL_SUB_BOLD) == 1,
        subtitleBackgroundAlpha = int(WingDbHelper.COL_SUB_BG_ALPHA, 128),
        preferredOrientation   = int(WingDbHelper.COL_ORIENTATION, -1),
        activeProfileId        = lng(WingDbHelper.COL_ACTIVE_PROFILE),
    )
}

// ── Extension: ChapterMarker ↔ ContentValues ─────────────────────────────────

private fun ChapterMarker.toContentValues(): ContentValues = ContentValues().apply {
    put(WingDbHelper.CHA_VIDEO_ID,    videoId)
    put(WingDbHelper.CHA_POSITION_MS, positionMs)
    put(WingDbHelper.CHA_LABEL,       label)
    put(WingDbHelper.CHA_AUTO,        if (isAutoDetected) 1 else 0)
    put(WingDbHelper.CHA_CREATED_AT,  createdAt)
}

private fun android.database.Cursor.toChapter(): ChapterMarker {
    fun str(col: String): String? = getColumnIndex(col).takeIf { it >= 0 }?.let { getString(it) }
    fun lng(col: String): Long = getColumnIndex(col).takeIf { it >= 0 }?.let { getLong(it) } ?: 0L
    fun int(col: String): Int = getColumnIndex(col).takeIf { it >= 0 }?.let { getInt(it) } ?: 0
    return ChapterMarker(
        id              = lng(WingDbHelper.CHA_ID),
        videoId         = lng(WingDbHelper.CHA_VIDEO_ID),
        positionMs      = lng(WingDbHelper.CHA_POSITION_MS),
        label           = str(WingDbHelper.CHA_LABEL) ?: "",
        isAutoDetected  = int(WingDbHelper.CHA_AUTO) == 1,
        createdAt       = lng(WingDbHelper.CHA_CREATED_AT),
    )
}

// ── Extension: TimelineSkip ↔ ContentValues ──────────────────────────────────

private fun TimelineSkip.toContentValues(): ContentValues = ContentValues().apply {
    put(WingDbHelper.SKI_VIDEO_ID,   videoId)
    put(WingDbHelper.SKI_START_MS,   startMs)
    put(WingDbHelper.SKI_END_MS,     endMs)
    put(WingDbHelper.SKI_LABEL,      label)
    put(WingDbHelper.SKI_CREATED_AT, createdAt)
}

private fun android.database.Cursor.toSkip(): TimelineSkip {
    fun str(col: String): String? = getColumnIndex(col).takeIf { it >= 0 }?.let { getString(it) }
    fun lng(col: String): Long = getColumnIndex(col).takeIf { it >= 0 }?.let { getLong(it) } ?: 0L
    return TimelineSkip(
        id              = lng(WingDbHelper.SKI_ID),
        videoId         = lng(WingDbHelper.SKI_VIDEO_ID),
        startMs         = lng(WingDbHelper.SKI_START_MS),
        endMs           = lng(WingDbHelper.SKI_END_MS),
        label           = str(WingDbHelper.SKI_LABEL) ?: "",
        createdAt       = lng(WingDbHelper.SKI_CREATED_AT),
    )
}
