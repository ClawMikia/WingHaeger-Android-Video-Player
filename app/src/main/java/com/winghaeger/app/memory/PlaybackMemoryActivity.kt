package com.winghaeger.app.memory

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.winghaeger.app.R
import com.winghaeger.app.data.VideoRepository
import com.winghaeger.app.databinding.ActivityPlaybackMemoryBinding
import com.winghaeger.app.player.PlayerActivity
import com.winghaeger.app.ui.BottomNavHelper
import com.winghaeger.app.ui.LibraryAdapter
import com.winghaeger.app.ui.LibraryListItem
import com.winghaeger.app.ui.setContentWithWingInsets

class PlaybackMemoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaybackMemoryBinding
    private val repo by lazy { VideoRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaybackMemoryBinding.inflate(layoutInflater)
        setContentWithWingInsets(binding.root)

        BottomNavHelper.setup(this, binding.bottomNav, R.id.nav_memory)

        val adapter = LibraryAdapter { videoId, playlistIds, index ->
            startActivity(
                Intent(this, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_VIDEO_ID, videoId)
                    .putExtra(PlayerActivity.EXTRA_PLAYLIST_IDS, playlistIds)
                    .putExtra(PlayerActivity.EXTRA_PLAYLIST_INDEX, index)
            )
        }
        val glm = GridLayoutManager(this, 2)
        glm.spanSizeLookup = adapter.spanSizeLookup()
        binding.recycler.layoutManager = glm
        binding.recycler.adapter = adapter

        val videos = repo.listPlaybackMemory()
        val flat = videos.map { LibraryListItem.VideoRow(it) }
        adapter.submitList(flat)
        binding.empty.visibility =
            if (flat.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.recycler.visibility =
            if (flat.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }
}
