package com.winghaeger.app.explore

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import com.winghaeger.app.R
import com.winghaeger.app.data.VideoRepository
import com.winghaeger.app.databinding.ActivityExploreBinding
import com.winghaeger.app.player.PlayerActivity
import com.winghaeger.app.ui.BottomNavHelper
import com.winghaeger.app.ui.LibraryAdapter
import com.winghaeger.app.ui.setContentWithWingInsets
import com.winghaeger.app.ui.buildGroupedItems

class ExploreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExploreBinding
    private val repo by lazy { VideoRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExploreBinding.inflate(layoutInflater)
        setContentWithWingInsets(binding.root)

        BottomNavHelper.setup(this, binding.bottomNav, R.id.nav_explore)

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

        fun applyQuery(raw: String) {
            val q = raw.trim()
            val results = if (q.isEmpty()) emptyList() else repo.search(q)
            val items = buildGroupedItems(results)
            adapter.submitList(items)
            binding.recycler.visibility =
                if (items.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        }

        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applyQuery(binding.searchInput.text?.toString().orEmpty())
                true
            } else false
        }

        binding.searchInput.doAfterTextChanged { text ->
            applyQuery(text?.toString().orEmpty())
        }

        applyQuery("")
    }
}
