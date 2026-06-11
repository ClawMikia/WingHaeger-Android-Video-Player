package com.winghaeger.app.settings

import android.text.InputType
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.winghaeger.app.R
import com.winghaeger.app.data.AppPrefs
import com.winghaeger.app.databinding.ActivitySettingsBinding
import com.winghaeger.app.ui.showWingMessage
import com.winghaeger.app.ui.setContentWithWingInsets
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val prefs by lazy { AppPrefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentWithWingInsets(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        bindValues()

        binding.seekBarInterval.max = 59
        binding.seekBarInterval.progress = (prefs.defaultSeekJumpSec - 1).coerceIn(0, 58)

        binding.seekValue.setOnClickListener {
            editNumber("Skip Interval (sec)", prefs.defaultSeekJumpSec.toDouble(), false) {
                prefs.defaultSeekJumpSec = it.toInt()
            }
        }
        binding.trimValue.setOnClickListener {
            editNumber("Trim Step (ms)", prefs.defaultTrimStepMs.toDouble(), false) {
                prefs.defaultTrimStepMs = it.toLong()
            }
        }
        binding.volumeValue.setOnClickListener {
            editNumber("Volume Step (%)", prefs.defaultVolumeStepPercent.toDouble(), false) {
                prefs.defaultVolumeStepPercent = it.toInt()
            }
        }
        binding.ffrwValue.setOnClickListener {
            editNumber("Fast Fwd/Rwd (sec)", prefs.defaultSeekJumpSec.toDouble(), false) {
                prefs.defaultSeekJumpSec = it.toInt()
            }
        }

        binding.seekBarInterval.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                prefs.defaultSeekJumpSec = (progress + 1).coerceAtLeast(1)
                bindValues()
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        binding.btnResetPlaybackDefaults.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Restore Defaults?")
                .setMessage("Reset all system config to factory values?")
                .setPositiveButton("CONFIRM") { _, _ -> resetDefaults() }
                .setNegativeButton("ABORT", null)
                .show()
        }
    }

    private fun bindValues() {
        binding.seekValue.text = prefs.defaultSeekJumpSec.toString()
        binding.trimValue.text = prefs.defaultTrimStepMs.toString() + " ms"
        binding.volumeValue.text = prefs.defaultVolumeStepPercent.toString() + " %"
        binding.ffrwValue.text = prefs.defaultSeekJumpSec.toString() + " sec"
    }

    private fun editNumber(title: String, current: Double, allowDecimal: Boolean, onSave: (Double) -> Unit) {
        val layout = TextInputLayout(this).apply {
            hint = title
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val input = TextInputEditText(layout.context).apply {
            setText(if (allowDecimal) current.toString() else current.toLong().toString())
            inputType = if (allowDecimal) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            else InputType.TYPE_CLASS_NUMBER
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            addView(layout, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        layout.addView(input)
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("SAVE") { _, _ ->
                val parsed = input.text?.toString()?.trim()?.toDoubleOrNull()
                if (parsed == null || parsed <= 0.0) {
                    showWingMessage("Invalid Input", "Enter a positive number.")
                    return@setPositiveButton
                }
                onSave(parsed)
                bindValues()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun resetDefaults() {
        prefs.defaultSeekJumpSec = 10
        prefs.defaultTrimStepMs = 10000L
        prefs.defaultVolumeStepPercent = 5
        bindValues()
        binding.seekBarInterval.progress = (prefs.defaultSeekJumpSec - 1).coerceIn(0, 58)
        showWingMessage("Restored", "Config reset to factory defaults.")
    }
}
