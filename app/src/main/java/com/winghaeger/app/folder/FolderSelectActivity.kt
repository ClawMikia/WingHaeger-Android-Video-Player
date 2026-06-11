package com.winghaeger.app.folder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.winghaeger.app.data.FolderScanner
import com.winghaeger.app.data.VideoRepository
import com.winghaeger.app.databinding.ActivityFolderSelectBinding
import com.winghaeger.app.ui.BottomNavHelper
import com.winghaeger.app.ui.setContentWithWingInsets
import com.winghaeger.app.ui.showWingMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FolderSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFolderSelectBinding
    private val repo by lazy { VideoRepository(this) }

    private val openTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) {
            finish()
            return@registerForActivityResult
        }
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            showWingMessage("Access Denied", "Could not keep folder access.") { finish() }
            return@registerForActivityResult
        }
        scanAndImport(uri)
    }

    private val openFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri>? ->
        if (uris == null || uris.isEmpty()) {
            return@registerForActivityResult
        }
        uris.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}
        }
        importFiles(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFolderSelectBinding.inflate(layoutInflater)
        setContentWithWingInsets(binding.root)

        binding.btnPickFolder.setOnClickListener { openTree.launch(null) }
        binding.btnPickFiles.setOnClickListener { openFiles.launch(arrayOf("video/*")) }

        binding.btnDeepScan.setOnClickListener {
            binding.progressBar.visibility = View.VISIBLE
            binding.tvStatus.text = "Deep scanning system sectors..."
            binding.btnPickFolder.isEnabled = false
            binding.btnPickFiles.isEnabled = false
            binding.btnDeepScan.isEnabled = false

            lifecycleScope.launch {
                val videos = withContext(Dispatchers.IO) {
                    FolderScanner.scanMediaStore(this@FolderSelectActivity)
                }
                if (videos.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        repo.importScanResults(videos, withThumbnails = true)
                    }
                    showWingMessage("Deep Scan Complete", "Discovered ${videos.size} items across all sectors.") { finish() }
                } else {
                    binding.progressBar.visibility = View.GONE
                    binding.btnPickFolder.isEnabled = true
                    binding.btnPickFiles.isEnabled = true
                    binding.btnDeepScan.isEnabled = true
                    binding.tvStatus.text = "No additional items found."
                }
            }
        }

        BottomNavHelper.setup(this, binding.bottomNav, -1)
    }

    private fun scanAndImport(treeUri: Uri) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = getString(com.winghaeger.app.R.string.scanning)
        binding.btnPickFolder.isEnabled = false
        binding.btnPickFiles.isEnabled = false
        lifecycleScope.launch {
            val videos = withContext(Dispatchers.IO) {
                FolderScanner.scanTreeUri(this@FolderSelectActivity, treeUri)
            }
            if (videos.isEmpty()) {
                binding.progressBar.visibility = View.GONE
                binding.btnPickFolder.isEnabled = true
                binding.btnPickFiles.isEnabled = true
                binding.tvStatus.text = "No video files found in that folder."
                showWingMessage("No Videos Found", binding.tvStatus.text.toString())
                return@launch
            }
            withContext(Dispatchers.IO) {
                repo.importScanResults(videos, withThumbnails = true)
            }
            showWingMessage("Import Complete", "Imported ${videos.size} videos") { finish() }
        }
    }

    private fun importFiles(uris: List<Uri>) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "Importing videos…"
        binding.btnPickFolder.isEnabled = false
        binding.btnPickFiles.isEnabled = false
        lifecycleScope.launch {
            val videos = withContext(Dispatchers.IO) {
                FolderScanner.resolveUris(this@FolderSelectActivity, uris)
            }
            withContext(Dispatchers.IO) {
                repo.importScanResults(videos, withThumbnails = true)
            }
            showWingMessage("Import Complete", "Imported ${videos.size} videos") { finish() }
        }
    }
}
