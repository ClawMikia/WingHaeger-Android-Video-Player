package com.winghaeger.app.ui

import com.winghaeger.app.data.VideoEntity

sealed class LibraryListItem {
    data class Header(val folderName: String, val count: Int, val expanded: Boolean) : LibraryListItem()
    data class VideoRow(val entity: VideoEntity) : LibraryListItem()
}

fun buildGroupedItems(
    videos: List<VideoEntity>,
    expandedFolders: Set<String> = emptySet(),
    alwaysExpand: Boolean = false
): List<LibraryListItem> {
    // Group by folder name (case-insensitive)
    val grouped = mutableMapOf<String, MutableList<VideoEntity>>()
    val displayNames = mutableMapOf<String, String>()

    for (v in videos) {
        val key = v.folderGroup.lowercase()
        grouped.getOrPut(key) { mutableListOf() }.add(v)
        if (!displayNames.containsKey(key)) {
            displayNames[key] = v.folderGroup
        }
    }

    val result = mutableListOf<LibraryListItem>()
    for ((key, items) in grouped) {
        val folder = displayNames[key] ?: key
        val expanded = alwaysExpand || expandedFolders.any { it.equals(folder, ignoreCase = true) }
        result.add(LibraryListItem.Header(folder, items.size, expanded))
        if (expanded) items.forEach { result.add(LibraryListItem.VideoRow(it)) }
    }
    return result
}
