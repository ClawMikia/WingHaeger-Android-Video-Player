package com.winghaeger.app.ui

import android.app.Activity
import android.content.Intent
import androidx.core.view.ViewCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.winghaeger.app.R
import com.winghaeger.app.explore.ExploreActivity
import com.winghaeger.app.folder.FolderSelectActivity
import com.winghaeger.app.library.LibraryActivity
import com.winghaeger.app.memory.PlaybackMemoryActivity
import com.winghaeger.app.main.MainActivity

object BottomNavHelper {

    fun setup(activity: Activity, nav: BottomNavigationView, selectedId: Int) {
        nav.selectedItemId = selectedId
        ViewCompat.setOnApplyWindowInsetsListener(nav) { v, insets ->
            // Keep existing top padding from XML/Style
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, 0)
            insets
        }
        nav.setOnItemSelectedListener { item ->
            if (item.itemId == selectedId) return@setOnItemSelectedListener false
            when (item.itemId) {
                R.id.nav_home -> {
                    activity.startActivity(Intent(activity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    true
                }
                R.id.nav_library -> {
                    activity.startActivity(Intent(activity, LibraryActivity::class.java)
                        .putExtra(LibraryActivity.EXTRA_MODE, LibraryActivity.MODE_ALL))
                    true
                }
                R.id.nav_favorites -> {
                    activity.startActivity(Intent(activity, LibraryActivity::class.java)
                        .putExtra(LibraryActivity.EXTRA_MODE, LibraryActivity.MODE_FAVORITES))
                    true
                }
                R.id.nav_memory -> {
                    activity.startActivity(Intent(activity, PlaybackMemoryActivity::class.java))
                    true
                }
                R.id.nav_explore -> {
                    activity.startActivity(Intent(activity, ExploreActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    fun openFolderSelect(activity: Activity) {
        activity.startActivity(Intent(activity, FolderSelectActivity::class.java))
    }
}
