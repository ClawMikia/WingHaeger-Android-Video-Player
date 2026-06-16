package com.winghaeger.app.player

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.winghaeger.app.R
import com.winghaeger.app.data.AppPrefs
import com.winghaeger.app.data.ChapterMarker
import com.winghaeger.app.data.EnhancementMode
import com.winghaeger.app.data.TimelineSkip
import com.winghaeger.app.data.VideoEntity
import com.winghaeger.app.data.VideoRepository
import com.winghaeger.app.databinding.ActivityPlayerBinding
import com.winghaeger.app.service.PlaybackService
import com.winghaeger.app.ui.setContentWithWingInsets
import com.winghaeger.app.ui.showWingMessage
import com.winghaeger.app.util.FormatUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private val repo by lazy { VideoRepository(this) }
    private val prefs by lazy { AppPrefs(this) }

    private var player: ExoPlayer? = null
    private lateinit var playlistIds: LongArray
    private var playlistIndex = 0
    private lateinit var working: VideoEntity

    private val handler = Handler(Looper.getMainLooper())
    private var userScrubbingPlayback = false
    private var playerReady = false
    private var isFullscreen = false
    private var isBindingUi = false
    private var isFsPanelsVisible = true
    private val hideFsPanelsRunnable = Runnable { if (isFullscreen && isFsPanelsVisible) toggleFsPanels() }

    private fun scheduleFsPanelsHide() {
        handler.removeCallbacks(hideFsPanelsRunnable)
        if (isFullscreen && isFsPanelsVisible && (player?.isPlaying == true)) {
            handler.postDelayed(hideFsPanelsRunnable, 3500L)
        }
    }
    private var chapters = listOf<ChapterMarker>()
    private var skips = listOf<TimelineSkip>()

    private var currentVolume = 1.0f
    private var isMuted = false
    private var preMuteVolume = 1.0f
    private var currentBrightness = 0.5f
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var currentZoom = 1f

    private var playbackService: PlaybackService? = null
    private var serviceBound = false
    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val lb = binder as PlaybackService.LocalBinder
            val service = lb.getService()
            playbackService = service
            serviceBound = true
            val sharedPlayer = service.getPlayer()
            if (sharedPlayer != null) {
                sharedPlayer.removeListener(playerListener)
                player = sharedPlayer
                sharedPlayer.addListener(playerListener)
                binding.playerView.player = sharedPlayer
                val currentMedia = sharedPlayer.currentMediaItem
                val workingUri = working.contentUri.toString()
                if (currentMedia == null || currentMedia.localConfiguration?.uri?.toString() != workingUri) {
                    attachCurrentMedia()
                } else {
                    isBindingUi = true
                    try { bindUiFromWorking(); updatePlayPauseIcon(); tickTimeline() } finally { isBindingUi = false }
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) { serviceBound = false; playbackService = null }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!userScrubbingPlayback) tickTimeline()
            handler.postDelayed(this, 250L)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            playerReady = state == Player.STATE_READY || state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY) {
                val contentDur = player?.contentDuration ?: C.TIME_UNSET
                if (contentDur != C.TIME_UNSET && contentDur > 0L) {
                    if (working.durationMs <= 0L || abs(working.durationMs - contentDur) > 1000) {
                        working = working.copy(durationMs = contentDur); persistPrefs()
                    }
                }
                isBindingUi = true; try { bindTrimSeekers() } finally { isBindingUi = false }
                applyZoom(working.zoomLevel); applyEnhancementMatrix(); tickTimeline()
            }
            if (state == Player.STATE_ENDED) handleTrimEndReached()
            updatePlayPauseIcon(); updateServiceNotification()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) { updatePlayPauseIcon(); if (isPlaying) tickTimeline() }
        override fun onPlayerError(error: PlaybackException) {
            showWingMessage("Playback Error", error.message ?: "code ${error.errorCode}")
            if (error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) attachCurrentMedia()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentWithWingInsets(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(enabled = true) {
                override fun handleOnBackPressed() {
                    if (isFullscreen) {
                        toggleFullscreen(enter = false)
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val startId = intent.getLongExtra(EXTRA_VIDEO_ID, -1L)
        var ids = intent.getLongArrayExtra(EXTRA_PLAYLIST_IDS)
        if ((ids == null || ids.isEmpty()) && startId > 0L) ids = longArrayOf(startId)
        playlistIds = ids ?: longArrayOf()
        if (playlistIds.isEmpty()) { showAndFinish("Missing video"); return }
        playlistIndex = intent.getIntExtra(EXTRA_PLAYLIST_INDEX, 0).coerceIn(0, playlistIds.lastIndex)
        if (startId > 0L) { val i = playlistIds.indexOf(startId); if (i >= 0) playlistIndex = i }
        val initial = repo.getById(playlistIds[playlistIndex])
        if (initial == null) { showAndFinish("Video not found in vault"); return }
        working = initial
        binding.playerView.useController = false
        currentVolume = working.volumeLevel.coerceIn(0f, 1f)
        currentZoom = working.zoomLevel.coerceIn(1f, 3f)
        currentBrightness = 0.5f
        chapters = repo.listChapters(working.id)
        skips = repo.listSkips(working.id)

        setupGestures()
        setupEnhancementSpinner()
        bindUiFromWorking()
        setupControls()
        startAndBindService()
        initPlayerIfNeeded()
        handler.post(tickRunnable)
    }

    override fun onStart() {
        super.onStart()
        playbackService?.isPlayerActivityVisible = true
        initPlayerIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        playbackService?.isPlayerActivityVisible = true
        if (!isInPictureInPictureMode) {
            restoreFullUI()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            window.insetsController?.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
        } else {
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.BLACK
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() { super.onPause(); persistProgress() }

    override fun onStop() {
        persistProgress()
        playbackService?.isPlayerActivityVisible = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        player?.removeListener(playerListener)
        binding.playerView.player = null
        binding.playerViewFs.player = null
        player = null
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.removeListener(playerListener)
        if (serviceBound) { unbindService(serviceConn); serviceBound = false }
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player?.isPlaying == true) enterPiP()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            binding.toolbar.visibility = View.GONE
            binding.scrollView.visibility = View.GONE
            binding.btnFullscreen.visibility = View.GONE
            binding.gestureHintOverlay.visibility = View.GONE
            binding.playerSurface.layoutParams = (binding.playerSurface.layoutParams as ViewGroup.MarginLayoutParams).apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT; topMargin = 0
            }
            val cs = androidx.constraintlayout.widget.ConstraintSet()
            cs.clone(binding.rootLayout)
            cs.connect(R.id.playerSurface, androidx.constraintlayout.widget.ConstraintSet.TOP,
                androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.TOP)
            cs.applyTo(binding.rootLayout)
        } else {
            restoreFullUI()
        }
    }

    private fun restoreFullUI() {
        if (isFullscreen) {
            toggleFullscreen(true)
        } else {
            binding.toolbar.visibility = View.VISIBLE
            binding.scrollView.visibility = View.VISIBLE
            binding.btnFullscreen.visibility = View.VISIBLE
            binding.playerSurface.layoutParams = (binding.playerSurface.layoutParams as ViewGroup.MarginLayoutParams).apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = (220 * resources.displayMetrics.density).toInt()
                topMargin = 0
            }
            val cs = androidx.constraintlayout.widget.ConstraintSet()
            cs.clone(binding.rootLayout)
            cs.connect(R.id.playerSurface, androidx.constraintlayout.widget.ConstraintSet.TOP,
                R.id.toolbar, androidx.constraintlayout.widget.ConstraintSet.BOTTOM)
            cs.applyTo(binding.rootLayout)
            
            binding.playerViewFs.visibility = View.GONE
            binding.playerViewFs.player = null
            binding.playerView.player = player
        }
        binding.playerSurface.requestLayout()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!isInPictureInPictureMode) {
            restoreFullUI()
        }
        val newId = intent.getLongExtra(EXTRA_VIDEO_ID, -1L)
        if (newId != -1L) {
            val fresh = repo.getById(newId)
            if (fresh != null) {
                val wasSame = (newId == working.id)
                working = fresh
                currentVolume = working.volumeLevel.coerceIn(0f, 1f)
                chapters = repo.listChapters(working.id)
                skips = repo.listSkips(working.id)
                if (wasSame) {
                    val startMs = working.trimStartMs.coerceAtLeast(0L)
                    player?.seekTo(startMs)
                    working = working.copy(positionMs = startMs)
                }
                bindUiFromWorking()
                attachCurrentMedia()
            }
        }
    }

    private fun startAndBindService() {
        val intent = Intent(this, PlaybackService::class.java).apply { action = "com.winghaeger.BIND_LOCAL" }
        startService(intent); bindService(intent, serviceConn, BIND_AUTO_CREATE)
    }

    private fun initPlayerIfNeeded() {
        if (player == null && serviceBound) {
            val sharedPlayer = playbackService?.getPlayer()
            if (sharedPlayer != null) {
                player = sharedPlayer
                sharedPlayer.removeListener(playerListener)
                sharedPlayer.addListener(playerListener)
                if (isFullscreen) {
                    binding.playerViewFs.player = sharedPlayer
                    binding.playerView.player = null
                } else {
                    binding.playerView.player = sharedPlayer
                    binding.playerViewFs.player = null
                }
                val currentMedia = sharedPlayer.currentMediaItem
                val workingUri = working.contentUri.toString()
                if (currentMedia == null || currentMedia.localConfiguration?.uri?.toString() != workingUri) {
                    attachCurrentMedia()
                } else {
                    isBindingUi = true
                    try { bindUiFromWorking(); updatePlayPauseIcon(); tickTimeline() } finally { isBindingUi = false }
                }
            }
        }
    }

    private fun mediaItemFor(e: VideoEntity): MediaItem = MediaItem.Builder()
        .setUri(e.contentUri)
        .setMediaId(e.id.toString())
        .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(e.title).build())
        .build()

    private fun attachCurrentMedia() {
        val exo = player ?: return
        val item = mediaItemFor(working)
        val currentUri = exo.currentMediaItem?.localConfiguration?.uri?.toString()
        val newUri = item.localConfiguration?.uri?.toString()
        if (currentUri != newUri) {
            exo.stop(); exo.clearMediaItems(); exo.setMediaItem(item); exo.prepare()
        }
        val targetPos = working.positionMs.coerceAtLeast(working.trimStartMs)
        exo.seekTo(targetPos)
        working = working.copy(positionMs = targetPos)
        exo.volume = currentVolume * working.audioBoost.coerceIn(1f, 2f)
        exo.repeatMode = Player.REPEAT_MODE_OFF
        exo.playWhenReady = true
        binding.playerView.postDelayed({ applyEnhancementMatrix() }, 150L)
        updatePlayPauseIcon()
        bindUiFromWorking()
        playbackService?.updateNotification(working.title, "Playing")
    }

    private fun applyEnhancementMatrix() {
        val o = binding.enhancementOverlay
        when (working.enhancement) {
            EnhancementMode.NONE -> o.visibility = View.GONE
            EnhancementMode.VIVID_HD -> { o.setBackgroundColor(Color.argb(22, 100, 180, 255)); o.visibility = View.VISIBLE }
            EnhancementMode.CINEMA_CONTRAST -> { o.setBackgroundColor(Color.argb(34, 0, 0, 0)); o.visibility = View.VISIBLE }
            EnhancementMode.WARM_FILM -> { o.setBackgroundColor(Color.argb(35, 255, 180, 80)); o.visibility = View.VISIBLE }
            EnhancementMode.COOL_HDR_SIM -> { o.setBackgroundColor(Color.argb(30, 80, 140, 255)); o.visibility = View.VISIBLE }
            EnhancementMode.AMOLED -> { o.setBackgroundColor(Color.argb(44, 0, 0, 0)); o.visibility = View.VISIBLE }
            EnhancementMode.NIGHT_MODE -> { o.setBackgroundColor(Color.argb(40, 255, 80, 0)); o.visibility = View.VISIBLE }
            EnhancementMode.ANIME -> { o.setBackgroundColor(Color.argb(24, 255, 255, 120)); o.visibility = View.VISIBLE }
            EnhancementMode.EYE_COMFORT -> { o.setBackgroundColor(Color.argb(30, 255, 200, 50)); o.visibility = View.VISIBLE }
            EnhancementMode.VIVID_OUTDOOR -> { o.setBackgroundColor(Color.argb(18, 0, 255, 150)); o.visibility = View.VISIBLE }
            EnhancementMode.CINEMATIC_DARK -> { o.setBackgroundColor(Color.argb(42, 16, 16, 32)); o.visibility = View.VISIBLE }
        }
    }

    private fun applyZoom(zoom: Float) { val z = zoom.coerceIn(1f, 3f); currentZoom = z; binding.playerView.scaleX = z; binding.playerView.scaleY = z }

    private fun setupGestures() {
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newZoom = (currentZoom * detector.scaleFactor).coerceIn(1f, 3f); applyZoom(newZoom); working = working.copy(zoomLevel = newZoom); return true
            }
        })
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isFullscreen) { toggleFsPanels(); return true }
                return false
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val v = if (isFullscreen) binding.playerViewFs else binding.playerView
                val screenWidth = v.width.toFloat()
                val jumpSec = prefs.defaultSeekJumpSec
                if (e.x < screenWidth / 3f) { jumpBy(-jumpSec); showGestureHint("⏪ -${jumpSec}s") }
                else if (e.x > screenWidth * 2f / 3f) { jumpBy(+jumpSec); showGestureHint("⏩ +${jumpSec}s") }
                else { player?.let { if (it.isPlaying) it.pause() else it.play() } }
                return true
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distX: Float, distY: Float): Boolean {
                val e1nn = e1 ?: return false
                val v = if (isFullscreen) binding.playerViewFs else binding.playerView
                val screenWidth = v.width.toFloat()
                if (abs(distX) > abs(distY)) {
                    val exo = player ?: return false; val dur = exo.duration; if (dur <= 0L) return false
                    val seekDeltaMs = (-distX / screenWidth * dur * 0.3f).toLong()
                    val newPos = (exo.currentPosition + seekDeltaMs).coerceIn(0L, dur); exo.seekTo(newPos); showGestureHint(FormatUtils.formatDuration(newPos))
                } else {
                    if (e1nn.x / screenWidth < 0.5f) {
                        val delta = -distY / v.height.coerceAtLeast(1); currentBrightness = (currentBrightness + delta * 0.5f).coerceIn(0.01f, 1f)
                        val lp = window.attributes; lp.screenBrightness = currentBrightness; window.attributes = lp; showGestureHint("☀ ${(currentBrightness * 100).toInt()}%")
                    } else {
                        val delta = -distY / v.height.coerceAtLeast(1); currentVolume = (currentVolume + delta * 0.5f).coerceIn(0f, 1f)
                        player?.volume = currentVolume * working.audioBoost.coerceIn(1f, 2f); working = working.copy(volumeLevel = currentVolume)
                        binding.seekVolume.progress = (currentVolume * 100).toInt()
                        val volText = "${(currentVolume * 100).toInt()}%"
                        binding.tvVolumeValue.text = volText
                        showGestureHint("🔊 $volText")
                    }
                }
                return true
            }
        })
        val tl = View.OnTouchListener { v, event ->
            scaleGestureDetector?.onTouchEvent(event)
            if (scaleGestureDetector?.isInProgress == false) {
                if (gestureDetector.onTouchEvent(event)) {
                    v.performClick()
                }
            }
            true
        }
        binding.playerView.setOnTouchListener(tl)
        binding.playerViewFs.setOnTouchListener(tl)
    }

    private fun showGestureHint(text: String?, iconRes: Int? = null) {
        binding.gestureHintText.text = text ?: ""
        binding.gestureHintText.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
        if (iconRes != null) { binding.gestureHintIcon.setImageResource(iconRes); binding.gestureHintIcon.visibility = View.VISIBLE }
        else { binding.gestureHintIcon.visibility = View.GONE }
        binding.gestureHintOverlay.visibility = View.VISIBLE
        binding.gestureHintOverlay.alpha = 1f
        handler.removeCallbacks(hideGestureHintRunnable)
        handler.postDelayed(hideGestureHintRunnable, 800L)
    }
    private val hideGestureHintRunnable = Runnable {
        binding.gestureHintOverlay.animate().alpha(0f).setDuration(300L).withEndAction { binding.gestureHintOverlay.visibility = View.GONE }.start()
    }

    private fun metaDurationMs(): Long { val contentDur = player?.contentDuration ?: C.TIME_UNSET; if (contentDur != C.TIME_UNSET && contentDur > 0L) return contentDur; return working.durationMs.takeIf { it > 0L } ?: 1L }

    private fun tickTimeline() {
        val exo = player ?: return; val fullDur = exo.duration; if (fullDur <= 0L) return; val pos = exo.currentPosition
        if (!userScrubbingPlayback) {
            val skip = skips.find { pos >= it.startMs && pos < it.endMs }
            if (skip != null) { exo.seekTo(skip.endMs); showGestureHint(null, R.drawable.ic_trim); return }
        }
        val start = working.trimStartMs
        val end = if (working.trimEndMs > 0) working.trimEndMs else fullDur
        if (!userScrubbingPlayback && working.trimEndMs > 0L && pos >= working.trimEndMs) { handleTrimEndReached(); return }
        val trimmedDur = max(end - start, 1L)
        val progress = (((pos - start).coerceAtLeast(0L) * 1000L) / trimmedDur).toInt().coerceIn(0, 1000)
        if (!userScrubbingPlayback) { binding.seekPlayback.progress = progress; binding.seekFs.progress = progress }
        updateTimeLabel(pos, fullDur); updatePlayPauseIcon()
    }

    private fun handleTrimEndReached() {
        val exo = player ?: return
        repo.savePlaybackPosition(working.id, working.trimStartMs)
        if (binding.switchLoop.isChecked) {
            val startMs = working.trimStartMs.coerceAtLeast(0L); exo.seekTo(startMs); exo.play()
            working = working.copy(positionMs = startMs); bindUiFromWorking()
        } else { exo.pause(); if (binding.switchAutoNext.isChecked) playAdjacent(1) }
    }

    private fun updateTimeLabel(absPos: Long, dur: Long) {
        val start = working.trimStartMs; val end = if (working.trimEndMs > 0) working.trimEndMs else dur
        val trimmedDur = max(end - start, 0L); val relativePos = max(absPos - start, 0L)
        val timeStr = FormatUtils.formatDuration(relativePos); val durStr = FormatUtils.formatDuration(trimmedDur)
        binding.tvPlaybackTime.text = timeStr; binding.timeLabel.text = durStr
        binding.tvFsTime.text = timeStr; binding.tvFsDuration.text = durStr
    }

    private fun updatePlayPauseIcon() {
        val isPlaying = player?.isPlaying == true
        val iconRes = if (isPlaying) R.drawable.ic_pause_custom else R.drawable.ic_play_custom
        binding.btnPlayPause.post { binding.btnPlayPause.setImageResource(iconRes) }
        val fsIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        binding.btnFsPlayPause.post { binding.btnFsPlayPause.setImageResource(fsIcon) }
    }

    private fun updateServiceNotification() { val state = if (player?.isPlaying == true) "Playing" else "Paused"; playbackService?.updateNotification(working.title, state) }

    private fun bindUiFromWorking() {
        try {
            isBindingUi = true
            binding.toolbar.title = working.title; binding.videoTitle.text = working.title
            binding.tvFsTitle.text = working.title
            binding.seekVolume.progress = (currentVolume * 100).toInt()
            val volText = "${(currentVolume * 100).toInt()}%"
            binding.tvVolumeValue.text = volText
            binding.switchAutoNext.isChecked = working.autoPlayNext
            binding.switchLoop.isChecked = working.loopPlayback
            binding.autoNextModeGroup.visibility = if (working.autoPlayNext) View.VISIBLE else View.GONE
            binding.rgAutoNextMode.check(if (working.shufflePlaylist) R.id.rbAutoNextRandom else R.id.rbAutoNextSequential)
            binding.btnFavorite.text = getString(if (working.favorite) R.string.favorite_off else R.string.favorite_on)
            binding.spinnerEnhancement.setSelection(EnhancementMode.entries.indexOf(working.enhancement).coerceAtLeast(0))
            bindTrimSeekers(); updateTimeLabel(working.positionMs.coerceAtLeast(0L), metaDurationMs())
            updateSkipsUi()
        } finally { isBindingUi = false }
    }

    private fun bindTrimSeekers() {
        val full = max(metaDurationMs(), 1L)
        binding.seekTrimStart.progress = ((working.trimStartMs * 1000L) / full).toInt().coerceIn(0, 1000)
        binding.seekTrimEnd.progress = if (working.trimEndMs <= 0L) 1000 else ((working.trimEndMs * 1000L) / full).toInt().coerceIn(0, 1000)
        binding.tvTrimStart.text = getString(R.string.trim_start_format, FormatUtils.formatDuration(working.trimStartMs))
        binding.tvTrimEnd.text = getString(R.string.trim_end_format, FormatUtils.formatDuration(if (working.trimEndMs <= 0L) full else working.trimEndMs))
    }

    private fun setupEnhancementSpinner() {
        val labels = EnhancementMode.entries.map { it.displayName }
        val ad = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            override fun getView(pos: Int, convert: View?, parent: ViewGroup) = super.getView(pos, convert, parent).also { (it as? android.widget.TextView)?.setTextColor(resources.getColor(R.color.wh_on_bg, theme)) }
            override fun getDropDownView(pos: Int, convert: View?, parent: ViewGroup) = super.getDropDownView(pos, convert, parent).also { (it as? android.widget.TextView)?.apply { setTextColor(resources.getColor(R.color.wh_on_bg, theme)); setBackgroundColor(resources.getColor(R.color.wh_surface_elevated, theme)); setPadding(32, 24, 32, 24) } }
        }
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); binding.spinnerEnhancement.adapter = ad
        binding.spinnerEnhancement.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { if (!isBindingUi) { working = working.copy(enhancement = EnhancementMode.entries[pos]); applyEnhancementMatrix(); persistPrefs() } }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun togglePlayPause() {
        val exo = player ?: return
        if (exo.isPlaying) { exo.pause(); updatePlayPauseIcon(); return }
        when (exo.playbackState) {
            Player.STATE_IDLE -> exo.prepare()
            Player.STATE_ENDED -> exo.seekTo(working.trimStartMs.coerceAtLeast(0L))
            Player.STATE_BUFFERING, Player.STATE_READY -> { /* already prepared */ }
        }
        exo.playWhenReady = true; exo.play(); updatePlayPauseIcon()
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener { togglePlayPause() }
        binding.btnStop.setOnClickListener { player?.pause(); player?.seekTo(working.trimStartMs); persistProgress(); tickTimeline() }
        binding.btnRestart.setOnClickListener { restartCurrentPlayback() }
        binding.btnRewind.setOnClickListener { jumpBy(-prefs.defaultSeekJumpSec) }
        binding.btnForward.setOnClickListener { jumpBy(prefs.defaultSeekJumpSec) }
        binding.btnPrev.setOnClickListener { playAdjacent(-1) }
        binding.btnNext.setOnClickListener { playAdjacent(1) }
        binding.btnPip.setOnClickListener { enterPiP() }
        binding.btnPlaylist.setOnClickListener { showPlaylistSelectionDialog() }
        binding.btnAddChapter.setOnClickListener { showAddChapterDialog(player?.currentPosition ?: 0L) }

        binding.seekPlayback.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    val exo = player ?: return; val fullDur = exo.duration; if (fullDur <= 0L) return
                    val start = working.trimStartMs; val end = if (working.trimEndMs > 0) working.trimEndMs else fullDur
                    val trimmedDur = max(end - start, 1L); exo.seekTo(start + (p * trimmedDur / 1000L))
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { userScrubbingPlayback = true }
            override fun onStopTrackingTouch(sb: SeekBar?) { userScrubbingPlayback = false; persistProgress() }
        })

        binding.btnVolumeDown.setOnClickListener { val np = ((currentVolume * 100).toInt() - prefs.defaultVolumeStepPercent).coerceIn(0, 100); binding.seekVolume.progress = np; applyVolumeStep(np); persistPrefs() }
        binding.btnVolumeUp.setOnClickListener { val np = ((currentVolume * 100).toInt() + prefs.defaultVolumeStepPercent).coerceIn(0, 100); binding.seekVolume.progress = np; applyVolumeStep(np); persistPrefs() }
        binding.btnVolumeReset.setOnClickListener { binding.seekVolume.progress = 100; applyVolumeStep(100); persistPrefs() }
        binding.btnMute.setOnClickListener { toggleMute() }
        binding.seekVolume.setOnSeekBarChangeListener(seekBarListener(
            onChange = { p, f -> if (f) applyVolumeStep(p) },
            onStop = { persistPrefs() }
        ))

        setupTrimControls()
        setupSkipControls()

        binding.btnFullscreen.setOnClickListener { toggleFullscreen(true) }
        binding.btnFsExit.setOnClickListener { toggleFullscreen(false) }
        binding.btnFsRotate.setOnClickListener { toggleFsOrientation(); scheduleFsPanelsHide() }
        binding.btnToggleFsPanels.setOnClickListener { toggleFsPanels() }
        binding.btnFsPlayPause.setOnClickListener { binding.btnPlayPause.performClick(); scheduleFsPanelsHide() }
        binding.btnFsPrev.setOnClickListener { binding.btnPrev.performClick(); scheduleFsPanelsHide() }
        binding.btnFsNext.setOnClickListener { binding.btnNext.performClick(); scheduleFsPanelsHide() }

        binding.seekFs.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    val exo = player ?: return; val fullDur = exo.duration; if (fullDur <= 0L) return
                    val start = working.trimStartMs; val end = if (working.trimEndMs > 0) working.trimEndMs else fullDur
                    exo.seekTo(start + (p * max(end - start, 1L) / 1000L))
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { userScrubbingPlayback = true; handler.removeCallbacks(hideFsPanelsRunnable) }
            override fun onStopTrackingTouch(sb: SeekBar?) { userScrubbingPlayback = false; persistProgress(); scheduleFsPanelsHide() }
        })

        binding.switchLoop.setOnCheckedChangeListener { _, c -> if (!isBindingUi) { working = working.copy(loopPlayback = c); player?.repeatMode = if (c) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF; persistPrefs() } }
        binding.switchAutoNext.setOnCheckedChangeListener { _, c ->
            if (!isBindingUi) { working = working.copy(autoPlayNext = c); binding.autoNextModeGroup.visibility = if (c) View.VISIBLE else View.GONE; persistPrefs() }
        }
        binding.rgAutoNextMode.setOnCheckedChangeListener { _, checkedId ->
            if (!isBindingUi) { working = working.copy(shufflePlaylist = checkedId == R.id.rbAutoNextRandom); persistPrefs() }
        }
        binding.btnFavorite.setOnClickListener { val next = !working.favorite; working = working.copy(favorite = next); repo.setFavorite(working.id, next); binding.btnFavorite.text = getString(if (next) R.string.favorite_off else R.string.favorite_on) }
        binding.btnResetAll.setOnClickListener { resetAllState() }
        binding.btnSavePrefs.setOnClickListener { readTrimFromSeekBars(); repo.savePreferences(working); showWingMessage("Config Saved") }
    }

    private fun setupTrimControls() {
        binding.seekTrimStart.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val full = max(metaDurationMs(), 1L); val startMs = (progress * full) / 1000L
                    working = working.copy(trimStartMs = startMs)
                    binding.tvTrimStart.text = getString(R.string.trim_start_format, FormatUtils.formatDuration(startMs))
                    player?.let { if (it.currentPosition < startMs) it.seekTo(startMs) }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) { persistPrefs(); tickTimeline() }
        })
        binding.seekTrimEnd.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val full = max(metaDurationMs(), 1L); val endMs = (progress * full) / 1000L
                    working = working.copy(trimEndMs = if (endMs >= full - 250L) 0L else endMs)
                    binding.tvTrimEnd.text = "End: ${FormatUtils.formatDuration(if (working.trimEndMs <= 0L) full else working.trimEndMs)}"
                    player?.let { if (working.trimEndMs > 0 && it.currentPosition > working.trimEndMs) it.seekTo(working.trimEndMs - 100) }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) { persistPrefs(); tickTimeline() }
        })
        binding.btnTrimStartMinus.setOnClickListener { adjustTrimStart(-prefs.defaultTrimStepMs) }
        binding.btnTrimStartPlus.setOnClickListener { adjustTrimStart(prefs.defaultTrimStepMs) }
        binding.btnTrimEndMinus.setOnClickListener { adjustTrimEnd(-prefs.defaultTrimStepMs) }
        binding.btnTrimEndPlus.setOnClickListener { adjustTrimEnd(prefs.defaultTrimStepMs) }
        binding.btnTrimReset.setOnClickListener {
            working = working.copy(trimStartMs = 0L, trimEndMs = 0L)
            isBindingUi = true; bindTrimSeekers(); isBindingUi = false; persistPrefs(); tickTimeline()
        }
    }

    private fun adjustTrimStart(deltaMs: Long) {
        val full = max(metaDurationMs(), 1L)
        val newStart = (working.trimStartMs + deltaMs).coerceIn(0L, if (working.trimEndMs > 0) working.trimEndMs - 500L else full - 500L)
        working = working.copy(trimStartMs = newStart)
        isBindingUi = true; bindTrimSeekers(); isBindingUi = false; persistPrefs()
        player?.let { if (it.currentPosition < newStart) it.seekTo(newStart) }; tickTimeline()
    }

    private fun adjustTrimEnd(deltaMs: Long) {
        val full = max(metaDurationMs(), 1L); val currentEnd = if (working.trimEndMs <= 0L) full else working.trimEndMs
        var newEnd = (currentEnd + deltaMs).coerceIn(working.trimStartMs + 500L, full)
        if (newEnd >= full - 250L) newEnd = 0L
        working = working.copy(trimEndMs = newEnd)
        isBindingUi = true; bindTrimSeekers(); isBindingUi = false; persistPrefs()
        player?.let { if (working.trimEndMs > 0 && it.currentPosition > working.trimEndMs) it.seekTo(working.trimEndMs - 100) }; tickTimeline()
    }

    private fun setupSkipControls() {
        binding.btnAddSkip.setOnClickListener { showAddSkipDialog() }
        binding.btnManageSkips.setOnClickListener { showManageSkipsDialog() }
    }

    private fun updateSkipsUi() {
        if (skips.isEmpty()) { binding.tvSkipsSummary.text = "No segments skipped"; binding.btnManageSkips.visibility = View.GONE }
        else { binding.tvSkipsSummary.text = "${skips.size} segments skipped"; binding.btnManageSkips.visibility = View.VISIBLE }
        drawSkipsOnSeekbars()
    }

    private fun drawSkipsOnSeekbars() {
        val full = max(metaDurationMs(), 1L)
        fun populate(overlay: FrameLayout) {
            overlay.removeAllViews()
            for (skip in skips) {
                val skipView = View(this)
                skipView.setBackgroundColor(resources.getColor(R.color.wh_neon_pink, theme))
                val startPercent = skip.startMs.toFloat() / full
                val widthPercent = (skip.endMs.toFloat() / full) - startPercent
                val lp = FrameLayout.LayoutParams(0, -1)
                overlay.addView(skipView, lp)
                overlay.post {
                    val pw = overlay.width
                    if (pw > 0) {
                        val nlp = skipView.layoutParams as FrameLayout.LayoutParams
                        nlp.width = (pw * widthPercent).toInt().coerceAtLeast(4)
                        nlp.leftMargin = (pw * startPercent).toInt()
                        skipView.layoutParams = nlp
                    }
                }
            }
        }
        populate(binding.skipsOverlay)
        populate(binding.fsSkipsOverlay)
    }

    private fun showAddSkipDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_skip, null)
        val etStart = dialogView.findViewById<TextInputEditText>(R.id.etSkipStart)
        val etEnd = dialogView.findViewById<TextInputEditText>(R.id.etSkipEnd)
        val currentMs = player?.currentPosition ?: 0L
        etStart.setText(FormatUtils.formatDuration(currentMs))
        etEnd.setText(FormatUtils.formatDuration(currentMs + 5000L))
        val dialog = MaterialAlertDialogBuilder(this).setView(dialogView).create()
        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btnSave).setOnClickListener {
            val startMs = parseTime(etStart.text.toString()); val endMs = parseTime(etEnd.text.toString())
            if (startMs < 0 || endMs < 0 || endMs <= startMs) { showWingMessage("Invalid Range", "Enter valid times (e.g. 1:30 or 90)"); return@setOnClickListener }
            repo.addSkip(working.id, startMs, endMs, "Skip ${FormatUtils.formatDuration(startMs)}")
            skips = repo.listSkips(working.id); updateSkipsUi(); dialog.dismiss()
        }
        dialog.show()
    }

    private fun showEditSkipDialog(skip: TimelineSkip) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_skip, null)
        dialogView.findViewById<android.widget.TextView>(R.id.tvSkipDialogTitle).text = "Edit Skip Segment"
        val etStart = dialogView.findViewById<TextInputEditText>(R.id.etSkipStart)
        val etEnd = dialogView.findViewById<TextInputEditText>(R.id.etSkipEnd)
        etStart.setText(FormatUtils.formatDuration(skip.startMs)); etEnd.setText(FormatUtils.formatDuration(skip.endMs))
        val dialog = MaterialAlertDialogBuilder(this).setView(dialogView).create()
        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btnSave).setOnClickListener {
            val startMs = parseTime(etStart.text.toString()); val endMs = parseTime(etEnd.text.toString())
            if (startMs < 0 || endMs < 0 || endMs <= startMs) { showWingMessage("Invalid Range", "Enter valid times"); return@setOnClickListener }
            repo.deleteSkip(skip.id)
            repo.addSkip(working.id, startMs, endMs, "Skip ${FormatUtils.formatDuration(startMs)}")
            skips = repo.listSkips(working.id); updateSkipsUi(); dialog.dismiss()
        }
        dialog.show()
    }

    private fun parseTime(input: String): Long {
        if (input.isBlank()) return -1L
        return try {
            if (input.contains(":")) {
                val parts = input.split(":")
                when (parts.size) {
                    2 -> (parts[0].toLong() * 60 + parts[1].toLong()) * 1000L
                    3 -> (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000L
                    else -> -1L
                }
            } else { input.toLong() * 1000L }
        } catch (_: Exception) { -1L }
    }

    private fun showManageSkipsDialog() {
        val skipStrings = skips.map { "${FormatUtils.formatDuration(it.startMs)} → ${FormatUtils.formatDuration(it.endMs)}" }
        MaterialAlertDialogBuilder(this)
            .setTitle("Skip Segments")
            .setItems(skipStrings.toTypedArray()) { _, which ->
                val skip = skips[which]
                MaterialAlertDialogBuilder(this)
                    .setTitle("Segment Options")
                    .setItems(arrayOf("Edit", "Delete")) { _, optIndex ->
                        if (optIndex == 0) showEditSkipDialog(skip)
                        else { repo.deleteSkip(skip.id); skips = repo.listSkips(working.id); updateSkipsUi() }
                    }.show()
            }
            .setPositiveButton("Close", null).show()
    }

    private fun toggleMute() {
        if (isMuted) { currentVolume = preMuteVolume; isMuted = false; binding.btnMute.setImageResource(android.R.drawable.ic_lock_silent_mode_off) }
        else { preMuteVolume = currentVolume; currentVolume = 0f; isMuted = true; binding.btnMute.setImageResource(android.R.drawable.ic_lock_silent_mode) }
        player?.volume = currentVolume * working.audioBoost; binding.seekVolume.progress = (currentVolume * 100).toInt(); binding.tvVolumeValue.text = "${(currentVolume * 100).toInt()}%"; persistPrefs()
    }

    private fun resetAllState() {
        val original = repo.getById(working.id) ?: return
        working = VideoEntity(id = original.id, uriString = original.uriString, title = original.title, folderGroup = original.folderGroup, durationMs = original.durationMs, sizeBytes = original.sizeBytes, thumbnail = original.thumbnail)
        repo.savePreferences(working); repo.savePlaybackPosition(working.id, 0L); repo.deleteAllChapters(working.id); repo.deleteAllSkips(working.id)
        player?.seekTo(0L); currentVolume = 1.0f; isMuted = false; skips = emptyList()
        player?.volume = 1.0f; isBindingUi = true; bindUiFromWorking(); applyEnhancementMatrix(); applyZoom(1.0f); isBindingUi = false
    }

    private fun wrapInMargin(view: View): View {
        val container = FrameLayout(this)
        val density = resources.displayMetrics.density
        val margin = (24 * density).toInt()
        val lp = FrameLayout.LayoutParams(-1, -2); lp.setMargins(margin, (16 * density).toInt(), margin, (16 * density).toInt())
        view.layoutParams = lp; container.addView(view); return container
    }

    private fun showPlaylistSelectionDialog() {
        val playlists = repo.listPlaylists()
        val titles = playlists.asSequence().map { it.second }.toMutableList()
        titles.add(0, "+ Create New Queue")
        MaterialAlertDialogBuilder(this).setTitle("Add to Queue").setItems(titles.toTypedArray()) { _, which ->
            if (which == 0) showCreatePlaylistDialog()
            else { repo.addVideoToPlaylist(playlists[which - 1].first, working.id); showWingMessage("Added", "Added to ${playlists[which - 1].second}") }
        }.show()
    }

    private fun showCreatePlaylistDialog() {
        val til = TextInputLayout(this).apply { hint = "Queue Name"; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE }
        val et = TextInputEditText(til.context); til.addView(et)
        MaterialAlertDialogBuilder(this).setTitle("New Queue").setView(wrapInMargin(til))
            .setPositiveButton("CREATE") { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotEmpty()) { val id = repo.createPlaylist(name); repo.addVideoToPlaylist(id, working.id); showWingMessage("Created", "Queue created and video added") }
            }.setNegativeButton("CANCEL", null).show()
    }

    private fun showAddChapterDialog(posMs: Long) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_chapter, null)
        dialogView.findViewById<android.widget.TextView>(R.id.tvTimestamp).text = "at ${FormatUtils.formatDuration(posMs)}"
        val dialog = MaterialAlertDialogBuilder(this).setView(dialogView).create()
        dialogView.findViewById<android.widget.ImageButton>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<android.widget.ImageButton>(R.id.btnAdd).setOnClickListener {
            val etName = dialogView.findViewById<TextInputEditText>(R.id.etChapterName)
            val label = etName.text.toString().trim().ifBlank { "Chapter" }
            repo.addChapter(working.id, posMs, label); chapters = repo.listChapters(working.id); dialog.dismiss()
        }
        dialog.show()
    }

    private fun applyVolumeStep(progress: Int) {
        currentVolume = progress / 100f
        val volText = "$progress%"
        binding.tvVolumeValue.text = volText
        working = working.copy(volumeLevel = currentVolume)
        player?.volume = currentVolume * working.audioBoost
    }
    private fun persistPrefs() { if (working.id > 0) repo.savePreferences(working) }
    private fun restartCurrentPlayback() {
        val exo = player ?: return; val startMs = working.trimStartMs.coerceAtLeast(0L)
        exo.seekTo(startMs); exo.play(); working = working.copy(positionMs = startMs); persistProgress(); tickTimeline()
    }
    private fun persistProgress() {
        val exo = player ?: return; if (working.id <= 0) return
        val pos = exo.currentPosition; val dur = exo.duration; val end = if (working.trimEndMs > 0) working.trimEndMs else dur
        val savePos = if (end > 0 && pos >= end - 800) working.trimStartMs else pos
        repo.savePlaybackPosition(working.id, savePos)
    }
    private fun jumpBy(deltaSec: Int) { val exo = player ?: return; val dur = exo.duration; if (dur <= 0L) return; val newPos = (exo.currentPosition + deltaSec * 1000L).coerceIn(0L, dur); exo.seekTo(newPos); tickTimeline() }
    private fun playAdjacent(delta: Int) {
        persistProgress(); if (playlistIds.isEmpty()) return
        val next = when {
            working.shufflePlaylist && playlistIds.size > 1 -> { var c: Int; do { c = Random.nextInt(playlistIds.size) } while (c == playlistIndex); c }
            else -> playlistIndex + delta
        }
        if (next < 0 || next >= playlistIds.size) return
        playlistIndex = next
        val fresh = repo.getById(playlistIds[playlistIndex]) ?: return
        working = fresh.copy(positionMs = fresh.trimStartMs, autoPlayNext = working.autoPlayNext, shufflePlaylist = working.shufflePlaylist)
        persistPrefs(); currentVolume = working.volumeLevel.coerceIn(0f, 1f)
        chapters = repo.listChapters(working.id); skips = repo.listSkips(working.id); attachCurrentMedia()
    }
    private fun readTrimFromSeekBars() {
        val full = max(metaDurationMs(), 1L); val start = binding.seekTrimStart.progress * full / 1000L
        var end = binding.seekTrimEnd.progress * full / 1000L; if (end <= start + 500L) end = min(start + 500L, full)
        working = working.copy(trimStartMs = start, trimEndMs = if (end >= full - 250L) 0L else end)
    }
    private fun toggleFullscreen(enter: Boolean) {
        isFullscreen = enter
        if (enter) {
            val videoSize = player?.videoSize
            if (videoSize != null && videoSize.width > 0 && videoSize.height > 0) {
                requestedOrientation = if (videoSize.height > videoSize.width) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else { if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
            binding.fullscreenOverlay.visibility = View.VISIBLE
            binding.toolbar.visibility = View.GONE; binding.scrollView.visibility = View.GONE
            binding.playerSurface.layoutParams = (binding.playerSurface.layoutParams as ViewGroup.MarginLayoutParams).apply { width = ViewGroup.LayoutParams.MATCH_PARENT; height = ViewGroup.LayoutParams.MATCH_PARENT; topMargin = 0 }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.insetsController?.hide(android.view.WindowInsets.Type.systemBars())
            else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            
            binding.playerViewFs.visibility = View.VISIBLE
            binding.playerViewFs.player = player
            binding.playerView.player = null
            isFsPanelsVisible = true
            binding.fsControlPanels.visibility = View.VISIBLE
            scheduleFsPanelsHide()
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            binding.fullscreenOverlay.visibility = View.GONE
            binding.toolbar.visibility = View.VISIBLE; binding.scrollView.visibility = View.VISIBLE
            binding.playerSurface.layoutParams = (binding.playerSurface.layoutParams as ViewGroup.MarginLayoutParams).apply { width = ViewGroup.LayoutParams.MATCH_PARENT; height = (220 * resources.displayMetrics.density).toInt() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.insetsController?.show(android.view.WindowInsets.Type.systemBars())
            else @Suppress("DEPRECATION") window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            
            binding.playerViewFs.visibility = View.GONE
            binding.playerViewFs.player = null
            binding.playerView.player = player
            handler.removeCallbacks(hideFsPanelsRunnable)
        }
        binding.playerSurface.requestLayout()
    }
    private fun toggleFsOrientation() {
        requestedOrientation = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }
    private fun toggleFsPanels() {
        isFsPanelsVisible = !isFsPanelsVisible
        binding.fsControlPanels.visibility = if (isFsPanelsVisible) View.VISIBLE else View.GONE
        if (isFsPanelsVisible) scheduleFsPanelsHide() else handler.removeCallbacks(hideFsPanelsRunnable)
    }
    private fun enterPiP() {
        val videoSize = player?.videoSize
        val rational = if (videoSize != null && videoSize.width > 0 && videoSize.height > 0) {
            val r = videoSize.width.toFloat() / videoSize.height.toFloat()
            if (r < 0.4184f) Rational(4184, 10000) else if (r > 2.39f) Rational(239, 100) else Rational(videoSize.width, videoSize.height)
        } else { Rational(16, 9) }
        val params = PictureInPictureParams.Builder().setAspectRatio(rational).build(); enterPictureInPictureMode(params)
    }
    private fun showAndFinish(msg: String) { showWingMessage("Error", msg) { finish() } }
    private fun seekBarListener(onChange: (Int, Boolean) -> Unit, onStop: () -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) = onChange(p, fromUser)
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) = onStop()
    }

    companion object {
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_PLAYLIST_IDS = "playlist_ids"
        const val EXTRA_PLAYLIST_INDEX = "playlist_index"
    }
}
