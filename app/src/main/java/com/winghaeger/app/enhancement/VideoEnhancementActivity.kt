package com.winghaeger.app.enhancement

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.winghaeger.app.data.AppPrefs
import com.winghaeger.app.data.EnhancementMode
import com.winghaeger.app.databinding.ActivityVideoEnhancementBinding
import com.winghaeger.app.ui.setContentWithWingInsets

class VideoEnhancementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoEnhancementBinding
    private val prefs by lazy { AppPrefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoEnhancementBinding.inflate(layoutInflater)
        setContentWithWingInsets(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        var current = prefs.defaultEnhancement
        fun refresh() {
            binding.recycler.adapter = EnhancementListAdapter(current) { mode ->
                current = mode
                prefs.defaultEnhancement = mode
                refresh()
            }
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        refresh()
    }
}
