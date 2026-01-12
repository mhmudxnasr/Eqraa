/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.reader

import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentResultListener
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.toUri
import org.readium.r2.testapp.Application
import org.readium.r2.testapp.R
import org.readium.r2.testapp.databinding.ActivityReaderBinding
import org.readium.r2.testapp.drm.DrmManagementContract
import org.readium.r2.testapp.drm.DrmManagementFragment
import org.readium.r2.testapp.outline.OutlineContract
import org.readium.r2.testapp.outline.OutlineFragment
import org.readium.r2.testapp.utils.launchWebBrowser
import timber.log.Timber
import org.readium.r2.testapp.ui.sync.SyncStatusViewModel
import org.readium.r2.testapp.ui.sync.ConflictResolutionDialog
import org.readium.r2.testapp.data.auth.AuthRepository

/*
 * An activity to read a publication
 *
 * This class can be used as it is or be inherited from.
 */
open class ReaderActivity : AppCompatActivity() {

    private val model: ReaderViewModel by viewModels()
    private val app: Application get() = application as Application

    private val syncViewModel: SyncStatusViewModel by viewModels {
        SyncStatusViewModel.Factory(
            applicationContext,
            app.cloudLibraryManager,
            app.realtimeSyncManager,
            app.readingSyncManager,
            AuthRepository()
        )
    }

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = ReaderViewModel.createFactory(
            application as Application,
            ReaderActivityContract.parseIntent(this)
        )

    private lateinit var binding: ActivityReaderBinding
    private lateinit var readerFragment: BaseReaderFragment
    
    // Session tracking
    private var sessionStartTime: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        this.binding = binding

        val readerFragment = supportFragmentManager.findFragmentByTag(READER_FRAGMENT_TAG)
            ?.let { it as BaseReaderFragment }
            ?: run { createReaderFragment(model.readerInitData) }

        if (readerFragment is VisualReaderFragment) {
            val fullscreenDelegate = FullscreenReaderActivityDelegate(this, readerFragment, binding)
            lifecycle.addObserver(fullscreenDelegate)
        }

        readerFragment?.let { this.readerFragment = it }

        model.activityChannel.receive(this) { handleReaderFragmentEvent(it) }

        reconfigureActionBar()

        supportFragmentManager.setFragmentResultListener(
            OutlineContract.REQUEST_KEY,
            this,
            FragmentResultListener { _, result ->
                val locator = OutlineContract.parseResult(result).destination
                closeOutlineFragment(locator)
            }
        )

        supportFragmentManager.setFragmentResultListener(
            DrmManagementContract.REQUEST_KEY,
            this,
            FragmentResultListener { _, result ->
                if (DrmManagementContract.parseResult(result).hasReturned) {
                    finish()
                }
            }
        )

        supportFragmentManager.addOnBackStackChangedListener {
            reconfigureActionBar()
        }

        // Add support for display cutout.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Observe conflicts and show dialog
        lifecycleScope.launch {
            syncViewModel.conflicts.collect { conflicts ->
                conflicts.firstOrNull()?.let { conflict ->
                    ConflictResolutionDialog(
                        context = this@ReaderActivity,
                        conflict = conflict,
                        onKeepLocal = { 
                            syncViewModel.resolveConflict(conflict, keepLocal = true)
                        },
                        onKeepCloud = { 
                            syncViewModel.resolveConflict(conflict, keepLocal = false)
                        }
                    ).show()
                }
            }
        }
    }

    private fun createReaderFragment(readerData: ReaderInitData): BaseReaderFragment? {
        val readerClass: Class<out Fragment>? = when (readerData) {
            is EpubReaderInitData -> EpubReaderFragment::class.java
            is ImageReaderInitData -> ImageReaderFragment::class.java
            is MediaReaderInitData -> AudioReaderFragment::class.java
            is PdfReaderInitData -> PdfReaderFragment::class.java
            is DummyReaderInitData -> null
        }

        readerClass?.let { it ->
            supportFragmentManager.commitNow {
                replace(R.id.activity_container, it, Bundle(), READER_FRAGMENT_TAG)
            }
        }

        return supportFragmentManager.findFragmentByTag(READER_FRAGMENT_TAG) as BaseReaderFragment?
    }

    override fun onStart() {
        super.onStart()
        reconfigureActionBar()
        // Start tracking reading session
        sessionStartTime = System.currentTimeMillis()
    }
    
    override fun onResume() {
        super.onResume()
        // Check for remote changes on resume
        model.checkRemoteProgress()
    }

    override fun onPause() {
        super.onPause()
        // Determine if we need to sync:
        // The Repository queues actions, but triggering the worker explicitly ensures an immediate attempt
        org.readium.r2.testapp.data.SyncWorker.enqueue(applicationContext)
    }
    
    override fun onStop() {
        super.onStop()
        // End tracking reading session
        if (sessionStartTime > 0) {
            val endTime = System.currentTimeMillis()
            val bookId = model.bookId
            if (bookId != null && (endTime - sessionStartTime) >= 5000) { // Only save if > 5 seconds
                lifecycleScope.launch {
                    try {
                        app.statsRepository.insertSession(bookId, sessionStartTime, endTime)
                    } catch (e: Exception) {
                        // Ignore errors
                    }
                }
            }
            sessionStartTime = 0
        }
    }

    private fun reconfigureActionBar() {
        val currentFragment = supportFragmentManager.fragments.lastOrNull()

        title = when (currentFragment) {
            is OutlineFragment -> model.publication.metadata.title
            is DrmManagementFragment -> getString(R.string.title_fragment_drm_management)
            else -> null
        }

        supportActionBar!!.setDisplayHomeAsUpEnabled(
            when (currentFragment) {
                is OutlineFragment, is DrmManagementFragment -> true
                else -> false
            }
        )
    }

    private fun handleReaderFragmentEvent(command: ReaderViewModel.ActivityCommand) {
        when (command) {
            is ReaderViewModel.ActivityCommand.OpenOutlineRequested ->
                showOutlineFragment()
            is ReaderViewModel.ActivityCommand.OpenDrmManagementRequested ->
                showDrmManagementFragment()
            is ReaderViewModel.ActivityCommand.OpenExternalLink ->
                launchWebBrowser(this, command.url.toUri())
            is ReaderViewModel.ActivityCommand.ToastError ->
                command.error.show(this)
            is ReaderViewModel.ActivityCommand.SyncDetected ->
                showSyncPrompt(command.cfi, command.percentage)
            is ReaderViewModel.ActivityCommand.ShowConflictDialog ->
                showConflictDialog(command.local, command.remote)
        }
    }

    private fun showConflictDialog(local: org.readium.r2.testapp.data.model.ReadingPosition?, remote: org.readium.r2.testapp.data.model.ReadingPosition) {
        val dialog = ConflictResolutionDialogFragment.newInstance(local, remote)
        dialog.setListener(object : ConflictResolutionDialogFragment.ConflictResolutionListener {
            override fun onKeepLocal() {
                model.forceLocalProgress()
            }

            override fun onJumpToSynced(targetPosition: org.readium.r2.testapp.data.model.ReadingPosition) {
                // 1. Update DB
                model.applyRemoteProgress(targetPosition)
                
                // 2. Navigate
                try {
                     val locator = Locator.fromJSON(org.json.JSONObject(targetPosition.cfi))
                     if (locator != null) {
                         readerFragment.go(locator, animated = true)
                     }
                } catch (e: Exception) {
                     Timber.e(e, "Failed to navigate to synced position")
                }
            }
        })
        dialog.show(supportFragmentManager, ConflictResolutionDialogFragment.TAG)
    }

    private fun showSyncPrompt(cfi: String, percentage: Float) {
        val newPercent = (percentage * 100).toInt()
        
        // Get current local progress
        val currentPercent = try {
            val locator = readerFragment.currentLocator
            ((locator.locations.totalProgression ?: 0.0) * 100).toInt()
        } catch (e: Exception) {
            0
        }

        val message = getString(R.string.sync_progress_message)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sync_progress_title)
            .setMessage("$message\n\nCurrent: $currentPercent%\nNew: $newPercent%")
            .setPositiveButton("Jump") { _, _ ->
                try {
                    val locator = Locator.fromJSON(org.json.JSONObject(cfi))
                    if (locator != null) {
                        readerFragment.go(locator, animated = true)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse sync locator")
                }
            }
            .setNegativeButton("Stay here", null)
            .show()
    }

    private fun showOutlineFragment() {
        supportFragmentManager.commit {
            add(
                R.id.activity_container,
                OutlineFragment::class.java,
                Bundle(),
                OUTLINE_FRAGMENT_TAG
            )
            hide(readerFragment)
            addToBackStack(null)
        }
    }

    private fun closeOutlineFragment(locator: Locator) {
        readerFragment.go(locator, true)
        supportFragmentManager.popBackStack()
    }

    private fun showDrmManagementFragment() {
        supportFragmentManager.commit {
            add(
                R.id.activity_container,
                DrmManagementFragment::class.java,
                Bundle(),
                DRM_FRAGMENT_TAG
            )
            hide(readerFragment)
            addToBackStack(null)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                supportFragmentManager.popBackStack()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val READER_FRAGMENT_TAG = "reader"
        const val OUTLINE_FRAGMENT_TAG = "outline"
        const val DRM_FRAGMENT_TAG = "drm"
    }
}

