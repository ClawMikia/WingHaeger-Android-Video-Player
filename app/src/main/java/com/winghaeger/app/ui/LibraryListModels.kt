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
    val grouped = videos.groupBy { it.folderGroup }
    val result = mutableListOf<LibraryListItem>()
    for ((folder, items) in grouped) {
        val expanded = alwaysExpand || expandedFolders.contains(folder)
        result.add(LibraryListItem.Header(folder, items.size, expanded))
        if (expanded) items.forEach { result.add(LibraryListItem.VideoRow(it)) }
    }
    return result
}
