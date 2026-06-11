package com.winghaeger.app.playlist

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.winghaeger.app.R
import com.winghaeger.app.data.VideoRepository
import com.winghaeger.app.library.LibraryActivity
import com.winghaeger.app.ui.setContentWithWingInsets

class PlaylistListActivity : AppCompatActivity() {

    private val repo by lazy { VideoRepository(this) }
    private lateinit var binding: com.winghaeger.app.databinding.ActivityPlaylistListBinding
    private var playlists = listOf<Pair<Long, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = com.winghaeger.app.databinding.ActivityPlaylistListBinding.inflate(layoutInflater)
        setContentWithWingInsets(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.recycler.layoutManager = LinearLayoutManager(this)
        
        binding.btnNewPlaylist.setOnClickListener { showCreatePlaylistDialog() }

        com.winghaeger.app.ui.BottomNavHelper.setup(this, binding.bottomNav, -1)

        loadData()
    }

    private fun loadData() {
        playlists = repo.listPlaylists()
        binding.recycler.adapter = object : RecyclerView.Adapter<PlaylistVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistVH {
                val view = android.widget.LinearLayout(parent.context).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    setPadding(32, 32, 32, 32)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    layoutParams = RecyclerView.LayoutParams(-1, -2)
                    setBackgroundResource(android.R.drawable.list_selector_background)
                }
                val title = android.widget.TextView(parent.context).apply {
                    id = android.R.id.text1
                    textSize = 18f
                    setTextColor(resources.getColor(R.color.wh_on_bg, theme))
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, -2, 1f)
                }
                view.addView(title)
                
                val options = android.widget.ImageView(parent.context).apply {
                    id = android.R.id.icon1
                    setImageResource(android.R.drawable.ic_menu_more)
                    layoutParams = android.widget.LinearLayout.LayoutParams(96, 96)
                    setPadding(16, 16, 16, 16)
                    setColorFilter(resources.getColor(R.color.wh_neon_cyan, theme))
                }
                view.addView(options)
                
                return PlaylistVH(view)
            }

            override fun onBindViewHolder(holder: PlaylistVH, position: Int) {
                val (id, title) = playlists[position]
                val tv = holder.itemView.findViewById<android.widget.TextView>(android.R.id.text1)
                tv.text = title
                
                val btnOptions = holder.itemView.findViewById<android.widget.ImageView>(android.R.id.icon1)
                btnOptions.setOnClickListener { showOptions(id, title) }

                holder.itemView.setOnClickListener {
                    startActivity(Intent(this@PlaylistListActivity, LibraryActivity::class.java)
                        .putExtra(LibraryActivity.EXTRA_MODE, LibraryActivity.MODE_PLAYLIST)
                        .putExtra(LibraryActivity.EXTRA_PLAYLIST_ID, id)
                        .putExtra(LibraryActivity.EXTRA_PLAYLIST_NAME, title))
                }
                holder.itemView.setOnLongClickListener {
                    showOptions(id, title)
                    true
                }
            }

            override fun getItemCount() = playlists.size
        }
    }

    private fun wrapInMargin(view: android.view.View): android.view.View {
        val container = android.widget.FrameLayout(this)
        val density = resources.displayMetrics.density
        val marginH = (24 * density).toInt()
        val marginV = (16 * density).toInt()
        val lp = android.widget.FrameLayout.LayoutParams(-1, -2)
        lp.setMargins(marginH, marginV, marginH, marginV)
        view.layoutParams = lp
        container.addView(view)
        return container
    }

    private fun showCreatePlaylistDialog() {
        val til = TextInputLayout(this).apply {
            hint = "Playlist Name"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val et = TextInputEditText(til.context)
        til.addView(et)

        MaterialAlertDialogBuilder(this)
            .setTitle("New Playlist")
            .setView(wrapInMargin(til))
            .setPositiveButton("Create") { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotEmpty()) {
                    repo.createPlaylist(name)
                    loadData()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showOptions(id: Long, title: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(arrayOf("Rename", "Delete")) { _, which ->
                if (which == 0) showRenameDialog(id, title)
                else confirmDelete(id, title)
            }.show()
    }

    private fun showRenameDialog(id: Long, title: String) {
        val til = TextInputLayout(this).apply {
            hint = "Rename"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val et = TextInputEditText(til.context).apply { setText(title) }
        til.addView(et)

        MaterialAlertDialogBuilder(this)
            .setTitle("Rename")
            .setView(wrapInMargin(til))
            .setPositiveButton("Save") { _, _ ->
                repo.renamePlaylist(id, et.text.toString().trim())
                loadData()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun confirmDelete(id: Long, title: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete \"$title\"?")
            .setMessage("Are you sure you want to delete this playlist?")
            .setPositiveButton("Delete") { _, _ ->
                repo.deletePlaylist(id)
                loadData()
            }.setNegativeButton("Cancel", null).show()
    }

    class PlaylistVH(v: android.view.View) : RecyclerView.ViewHolder(v)
}
