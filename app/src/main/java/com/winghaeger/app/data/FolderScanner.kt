package com.winghaeger.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FolderScanner {

    suspend fun scanTreeUri(context: Context, treeUri: Uri): List<VideoEntity> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val out = mutableListOf<VideoEntity>()
        walk(context, root, "", out)
        out
    }

    private fun walk(context: Context, node: DocumentFile, folderPath: String, out: MutableList<VideoEntity>) {
        val name = node.name ?: return
        if (node.isDirectory) {
            val nextPath = if (folderPath.isEmpty()) name else "$folderPath/$name"
            for (child in node.listFiles()) {
                walk(context, child, nextPath, out)
            }
            return
        }
        if (!node.isFile) return
        val mime = node.type
        if (!isVideoMime(mime, name)) return
        val uri = node.uri
        val duration = probeDurationMs(context, uri)
        val size = node.length().takeIf { it >= 0 } ?: 0L
        out.add(
            VideoEntity(
                uriString = uri.toString(),
                title = name,
                folderGroup = folderPath.ifEmpty { "—" },
                durationMs = duration,
                sizeBytes = size,
            )
        )
    }

    private fun probeDurationMs(context: Context, uri: Uri): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            try {
                r.release()
            } catch (_: Exception) { }
        }
    }

    private fun isVideoMime(mime: String?, fileName: String): Boolean {
        if (mime != null && mime.startsWith("video/")) return true
        val ext = MimeTypeMap.getFileExtensionFromUrl(fileName)
            .ifEmpty {
                val dot = fileName.lastIndexOf('.')
                if (dot >= 0) fileName.substring(dot + 1) else ""
            }
            .lowercase()
        val mapMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        return mapMime?.startsWith("video/") == true || ext in VIDEO_EXTENSIONS
    }

    suspend fun resolveUris(context: Context, uris: List<Uri>): List<VideoEntity> = withContext(Dispatchers.IO) {
        val out = mutableListOf<VideoEntity>()
        for (uri in uris) {
            val doc = DocumentFile.fromSingleUri(context, uri) ?: continue
            val name = doc.name ?: "Video ${System.currentTimeMillis()}"
            val duration = probeDurationMs(context, uri)
            val size = doc.length().takeIf { it >= 0 } ?: 0L
            out.add(
                VideoEntity(
                    uriString = uri.toString(),
                    title = name,
                    folderGroup = "Imported Files",
                    durationMs = duration,
                    sizeBytes = size,
                )
            )
        }
        out
    }

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp", "3g2", "wmv", "flv", "ts", "mts", "m2ts", "ogv", "mpeg", "mpg"
    )
}
