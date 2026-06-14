package com.winghaeger.app.library

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import com.winghaeger.app.ui.showWingMessage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.recyclerview.widget.GridLayoutManager
import com.winghaeger.app.R
import com.winghaeger.app.data.VideoEntity
import com.winghaeger.app.data.VideoRepository
import com.winghaeger.app.databinding.ActivityLibraryBinding
import com.winghaeger.app.player.PlayerActivity
import com.winghaeger.app.service.PlaybackService
import com.winghaeger.app.ui.BottomNavHelper
import com.winghaeger.app.ui.LibraryAdapter
import com.winghaeger.app.ui.LibraryListItem
import com.winghaeger.app.ui.buildGroupedItems
import com.winghaeger.app.ui.setContentWithWingInsets

class LibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryBinding
    private val repo by lazy { VideoRepository(this) }
    private val expandedFolders = mutableSetOf<String>()
    private var allVideos = listOf<VideoEntity>()
    private var currentMode = MODE_ALL

    private lateinit var adapter: LibraryAdapter
    private var playlistId: Long = -1L

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            tickMiniPlayer()
            handler.postDelayed(this, 1000L)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            binding.btnMiniPlayPause.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            if (isPlaying) handler.post(tickRunnable) else handler.removeCallbacks(tickRunnable)
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            binding.miniTitle.text = mediaItem?.mediaMetadata?.title ?: "Playing Video"
            tickMiniPlayer()
        }
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
                binding.miniPlayerContainer.visibility = View.GONE
                handler.removeCallbacks(tickRunnable)
            } else {
                binding.miniPlayerContainer.visibility = View.VISIBLE
                if (playbackService?.getPlayer()?.isPlaying == true) handler.post(tickRunnable)
            }
        }
    }

    private var playbackService: PlaybackService? = null
    private var serviceBound = false
    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val lb = binder as PlaybackService.LocalBinder
            playbackService = lb.getService()
            serviceBound = true
            updateMiniPlayer()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            serviceBound = false
            playbackService = null
            binding.miniPlayerContainer.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentWithWingInsets(binding.root)

        currentMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_ALL
        playlistId = intent.getLongExtra(EXTRA_PLAYLIST_ID, -1L)
        
        val navSelected = when (currentMode) {
            MODE_FAVORITES        -> R.id.nav_favorites
            MODE_CONTINUE         -> R.id.nav_library
            MODE_RECENTLY_PLAYED  -> R.id.nav_memory
            else                  -> R.id.nav_library
        }
        binding.toolbar.title = getString(when (currentMode) {
            MODE_FAVORITES       -> R.string.favorites_title
            MODE_CONTINUE        -> R.string.section_continue_watching
            MODE_RECENTLY_PLAYED -> R.string.memory_title
            else                 -> R.string.library_title
        })
        BottomNavHelper.setup(this, binding.bottomNav, navSelected)
        
        if (currentMode == MODE_PLAYLIST) {
            binding.toolbar.title = intent.getStringExtra(EXTRA_PLAYLIST_NAME) ?: "Playlist"
            // Add "Add Video" button to toolbar
            val addItem = binding.toolbar.menu.add("Add Video")
            addItem.setIcon(android.R.drawable.ic_input_add)
            addItem.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            binding.toolbar.setOnMenuItemClickListener {
                if (it == addItem) { showAddVideoToPlaylistDialog(); true } else false
            }
        }

        adapter = LibraryAdapter(
            onHeaderClick = { folder ->
                val existing = expandedFolders.find { it.equals(folder, ignoreCase = true) }
                if (existing != null) {
                    expandedFolders.remove(existing)
                } else {
                    expandedFolders.add(folder)
                }
                updateList()
            },
            onRemove = if (currentMode == MODE_PLAYLIST) { videoId ->
                repo.removeVideoFromPlaylist(playlistId, videoId)
                refreshData()
            } else null,
            onOpen = { videoId, playlistIds, index ->
                binding.miniPlayerView.player = null
                startActivity(
                    Intent(this, PlayerActivity::class.java)
                        .putExtra(PlayerActivity.EXTRA_VIDEO_ID, videoId)
                        .putExtra(PlayerActivity.EXTRA_PLAYLIST_IDS, playlistIds)
                        .putExtra(PlayerActivity.EXTRA_PLAYLIST_INDEX, index)
                )
            }
        )
        val glm = GridLayoutManager(this, 2)
        glm.spanSizeLookup = adapter.spanSizeLookup()
        binding.recycler.layoutManager = glm
        binding.recycler.adapter = adapter

        allVideos = when (currentMode) {
            MODE_FAVORITES       -> repo.listFavorites()
            MODE_CONTINUE        -> repo.listContinueWatching()
            MODE_RECENTLY_PLAYED -> repo.listRecentlyPlayed()
            MODE_PLAYLIST        -> {
                val playlistId = intent.getLongExtra(EXTRA_PLAYLIST_ID, -1L)
                binding.toolbar.title = intent.getStringExtra(EXTRA_PLAYLIST_NAME) ?: "Playlist"
                repo.getVideosInPlaylist(playlistId)
            }
            else                 -> repo.listAllByFolder()
        }

        // Initially expand all
        if (currentMode == MODE_ALL) {
            expandedFolders.addAll(allVideos.map { it.folderGroup })
        }

        updateList()
        startAndBindService()
    }

    override fun onStart() {
        super.onStart()
        updateMiniPlayer()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(tickRunnable)
        playbackService?.getPlayer()?.removeListener(playerListener)
        binding.miniPlayerView.player = null
    }

    override fun onDestroy() {
        if (serviceBound) {
            unbindService(serviceConn)
            serviceBound = false
        }
        super.onDestroy()
    }

    private fun startAndBindService() {
        val intent = Intent(this, PlaybackService::class.java).apply { action = "com.winghaeger.BIND_LOCAL" }
        bindService(intent, serviceConn, BIND_AUTO_CREATE)
    }

    private fun updateMiniPlayer() {
        val player = playbackService?.getPlayer()
        val service = playbackService
        if (player != null && (player.isPlaying || player.playbackState != Player.STATE_IDLE) && service?.isPlayerActivityVisible == false) {
            binding.miniPlayerContainer.visibility = View.VISIBLE
            
            player.removeListener(playerListener)
            player.addListener(playerListener)
            binding.miniPlayerView.player = player
            
            val currentMediaItem = player.currentMediaItem
            binding.miniTitle.text = currentMediaItem?.mediaMetadata?.title ?: "Playing Video"
            
            binding.btnMiniPlayPause.setImageResource(
                if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            
            binding.btnMiniPlayPause.setOnClickListener {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    if (player.playbackState == Player.STATE_ENDED) {
                        player.seekTo(0L)
                    }
                    player.play()
                }
            }

            binding.btnMiniRewind.setOnClickListener {
                player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
                tickMiniPlayer()
            }

            binding.btnMiniForward.setOnClickListener {
                player.seekTo((player.currentPosition + 10000).coerceAtMost(player.duration))
                tickMiniPlayer()
            }
            
            binding.btnMiniClose.setOnClickListener {
                player.stop()
                binding.miniPlayerContainer.visibility = View.GONE
                handler.removeCallbacks(tickRunnable)
            }
            
            binding.miniPlayerContainer.setOnClickListener {
                binding.miniPlayerView.player = null
                val intent = Intent(this, PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_VIDEO_ID, currentMediaItem?.mediaId?.toLongOrNull() ?: -1L)
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
            }

            if (player.isPlaying) handler.post(tickRunnable)
            tickMiniPlayer()
        } else {
            binding.miniPlayerContainer.visibility = View.GONE
            handler.removeCallbacks(tickRunnable)
        }
    }

    private fun tickMiniPlayer() {
        val player = playbackService?.getPlayer() ?: return
        if (player.duration > 0) {
            val progress = (player.currentPosition * 1000 / player.duration).toInt()
            binding.miniProgress.progress = progress
        }
    }

    private fun refreshData() {
        allVideos = when (currentMode) {
            MODE_FAVORITES       -> repo.listFavorites()
            MODE_CONTINUE        -> repo.listContinueWatching()
            MODE_RECENTLY_PLAYED -> repo.listRecentlyPlayed()
            MODE_PLAYLIST        -> repo.getVideosInPlaylist(playlistId)
            else                 -> repo.listAllByFolder()
        }
        updateList()
    }

    private fun showAddVideoToPlaylistDialog() {
        val allVideosInLibrary = repo.listAllByFolder()
        val currentPlaylistVideoIds = allVideos.map { it.id }.toSet()
        val availableVideos = allVideosInLibrary.filter { it.id !in currentPlaylistVideoIds }
        
        if (availableVideos.isEmpty()) {
            showWingMessage("Playlist", "All videos are already in this playlist")
            return
        }

        val titles = availableVideos.map { it.title }.toTypedArray()
        val selected = BooleanArray(titles.size)

        MaterialAlertDialogBuilder(this)
            .setTitle("Add Videos to Playlist")
            .setMultiChoiceItems(titles, selected) { _, which, isChecked ->
                selected[which] = isChecked
            }
            .setPositiveButton("Add") { _, _ ->
                selected.forEachIndexed { index, isChecked ->
                    if (isChecked) {
                        repo.addVideoToPlaylist(playlistId, availableVideos[index].id)
                    }
                }
                refreshData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateList() {
        val items: List<LibraryListItem> = when (currentMode) {
            MODE_ALL -> buildGroupedItems(allVideos, expandedFolders, alwaysExpand = false)
            else     -> {
                if (allVideos.isEmpty()) emptyList()
                else buildList {
                    add(LibraryListItem.Header(binding.toolbar.title?.toString() ?: "", allVideos.size, true))
                    addAll(allVideos.map { LibraryListItem.VideoRow(it) })
                }
            }
        }

        adapter.submitList(items)
        binding.empty.visibility =
            if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.recycler.visibility =
            if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    companion object {
        const val EXTRA_MODE         = "mode"
        const val MODE_ALL           = "all"
        const val MODE_FAVORITES     = "favorites"
        const val MODE_CONTINUE      = "continue_watching"
        const val MODE_RECENTLY_PLAYED = "recently_played"
        const val MODE_PLAYLIST      = "playlist"
        const val EXTRA_PLAYLIST_ID  = "playlist_id"
        const val EXTRA_PLAYLIST_NAME = "playlist_name"
    }
}
