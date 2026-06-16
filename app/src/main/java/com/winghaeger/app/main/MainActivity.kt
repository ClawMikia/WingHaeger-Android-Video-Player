package com.winghaeger.app.main

import android.content.Intent
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import com.winghaeger.app.R
import com.winghaeger.app.data.WingDbHelper
import com.winghaeger.app.databinding.ActivityMainBinding
import com.winghaeger.app.enhancement.VideoEnhancementActivity
import com.winghaeger.app.library.LibraryActivity
import com.winghaeger.app.memory.PlaybackMemoryActivity
import com.winghaeger.app.settings.SettingsActivity
import com.winghaeger.app.ui.BottomNavHelper
import com.winghaeger.app.ui.setContentWithWingInsets

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentWithWingInsets(binding.root)

        BottomNavHelper.setup(this, binding.bottomNav, R.id.nav_home)

        binding.bottomNav.setOnItemReselectedListener { item ->
            if (item.itemId == R.id.nav_home) BottomNavHelper.openFolderSelect(this)
        }

        binding.btnPickFolder.setOnClickListener { BottomNavHelper.openFolderSelect(this) }

        binding.btnPlaylists.setOnClickListener {
            startActivity(Intent(this, com.winghaeger.app.playlist.PlaylistListActivity::class.java))
        }

        binding.btnOpenLibrary.setOnClickListener {
            startActivity(
                Intent(this, LibraryActivity::class.java)
                    .putExtra(LibraryActivity.EXTRA_MODE, LibraryActivity.MODE_ALL),
            )
        }

        binding.btnContinueWatching.setOnClickListener {
            startActivity(
                Intent(this, LibraryActivity::class.java)
                    .putExtra(LibraryActivity.EXTRA_MODE, LibraryActivity.MODE_CONTINUE)
            )
        }

        binding.btnResetLibrary.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Wipe Vault?")
                .setMessage("This erases all video records, chapters, and playback data. Cannot be undone.")
                .setPositiveButton("WIPE") { _, _ ->
                    val db = WingDbHelper(this)
                    db.writableDatabase.execSQL("DELETE FROM ${WingDbHelper.TABLE_VIDEOS}")
                    db.writableDatabase.execSQL("DELETE FROM ${WingDbHelper.TABLE_CHAPTERS}")
                    db.close()
                    BottomNavHelper.openFolderSelect(this)
                }
                .setNegativeButton("ABORT", null)
                .show()
        }

        binding.btnPlaybackMemory.setOnClickListener {
            startActivity(Intent(this, PlaybackMemoryActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnEnhancement.setOnClickListener {
            startActivity(Intent(this, VideoEnhancementActivity::class.java))
        }
    }
}
