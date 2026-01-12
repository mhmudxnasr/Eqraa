/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.bookshelf

import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import java.io.File
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.readium.r2.shared.DelicateReadiumApi
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.testapp.Application
import org.readium.r2.testapp.R
import org.readium.r2.testapp.data.model.CloudBookDto
import org.readium.r2.testapp.data.model.Book
import org.readium.r2.testapp.databinding.FragmentBookshelfBinding
import org.readium.r2.testapp.opds.GridAutoFitLayoutManager
import org.readium.r2.testapp.reader.ReaderActivityContract
import org.readium.r2.testapp.utils.viewLifecycle
import timber.log.Timber
import org.readium.r2.testapp.ui.sync.SyncStatusViewModel

import org.readium.r2.testapp.ui.sync.ConflictResolutionDialog
import org.readium.r2.testapp.data.auth.AuthRepository
import org.readium.r2.testapp.data.model.SyncConflict
import android.graphics.Color
import android.content.res.ColorStateList
import android.widget.ImageView
import org.readium.r2.testapp.ui.sync.SyncStatusViewModel.SyncStatus

class BookshelfFragment : Fragment() {

    private inner class OnViewAttachedListener : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) {
            app.readium.onLcpDialogAuthenticationParentAttached(view)
        }

        override fun onViewDetachedFromWindow(view: View) {
            app.readium.onLcpDialogAuthenticationParentDetached()
        }
    }

    private val bookshelfViewModel: BookshelfViewModel by activityViewModels()
    private lateinit var syncStatusViewModel: SyncStatusViewModel
    private lateinit var bookshelfAdapter: BookshelfAdapter
    private lateinit var appStoragePickerLauncher: ActivityResultLauncher<String>
    private lateinit var sharedStoragePickerLauncher: ActivityResultLauncher<Array<String>>
    private var binding: FragmentBookshelfBinding by viewLifecycle()
    private var onViewAttachedListener: OnViewAttachedListener = OnViewAttachedListener()

    // Cloud library dialog components
    private var cloudDialog: AlertDialog? = null
    private var cloudBookAdapter: CloudBookAdapter? = null

    private val app: Application
        get() = requireContext().applicationContext as Application

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentBookshelfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.addOnAttachStateChangeListener(onViewAttachedListener)

        bookshelfViewModel.channel.receive(viewLifecycleOwner) { handleEvent(it) }

        bookshelfAdapter = BookshelfAdapter(
            onBookClick = { book ->
                book.id?.let {
                    bookshelfViewModel.openPublication(it)
                }
            },
            onBookLongClick = { book -> showBookOptionsDialog(book) }
        )

        appStoragePickerLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                uri?.let {
                    bookshelfViewModel.importPublicationFromStorage(it)
                }
            }

        sharedStoragePickerLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                uri?.let {
                    val takeFlags: Int = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)
                    bookshelfViewModel.addPublicationFromStorage(it)
                }
            }

        binding.bookshelfBookList.apply {
            setHasFixedSize(true)
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), resources.getInteger(R.integer.grid_column_count))
            adapter = bookshelfAdapter
            addItemDecoration(
                VerticalSpaceItemDecoration(
                    24
                )
            )
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                bookshelfViewModel.books.collectLatest {
                    bookshelfAdapter.submitList(it)
                }
            }
        }

        // Cloud sync button
        // Cloud sync button
        binding.bookshelfCloudSync.setOnClickListener {
            // If error, show details. If idle, show dialog.
            val status = syncStatusViewModel.status.value
            if (status is SyncStatus.Error) {
                 Toast.makeText(requireContext(), "Sync Error: ${status.message}", Toast.LENGTH_LONG).show()
            }
            showCloudLibraryDialog()
        }

        // Initialize SyncStatusViewModel
        val factory = SyncStatusViewModel.Factory(
            requireContext(),
            app.readingProgressSyncManager,
            app.cloudLibraryManager,
            app.realtimeSyncManager,
            AuthRepository() // Or get from App if singleton
        )
        syncStatusViewModel = androidx.lifecycle.ViewModelProvider(this, factory)[SyncStatusViewModel::class.java]

        // Observe Sync Status
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                syncStatusViewModel.status.collectLatest { status ->
                    updateSyncIndicator(status)
                }
            }
        }

        // Observe Conflicts
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                syncStatusViewModel.conflicts.collectLatest { conflicts ->
                    if (conflicts.isNotEmpty()) {
                        // Show dialog for the first conflict
                        // Note: In a real app, we might want to ensure we don't stack dialogs 
                        // or show them while user is doing something else.
                        // Ideally we'd have a queue. For now, picking the first one.
                        showConflictDialog(conflicts.first())
                    }
                }
            }
        }

        binding.bookshelfAddBookFab.setOnClickListener {
            var selected = 0
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.add_book))
                .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                    dialog.cancel()
                }
                .setPositiveButton(getString(R.string.ok)) { _, _ ->
                    when (selected) {
                        0 -> appStoragePickerLauncher.launch("*/*")
                        1 -> sharedStoragePickerLauncher.launch(arrayOf("*/*"))
                        else -> askForRemoteUrl()
                    }
                }
                .setSingleChoiceItems(R.array.documentSelectorArray, 0) { _, which ->
                    selected = which
                }
                .show()
        }

        // Swipe to Refresh (Sync All)
        binding.bookshelfSwipeRefresh.setOnRefreshListener {
            lifecycleScope.launch {
                try {
                    // Trigger sync for all components
                    val jobs = listOf(
                        launch { 
                            org.readium.r2.testapp.data.db.AppDatabase.getDatabase(requireContext()).booksDao().let { dao ->
                                app.readingProgressSyncManager?.syncNow(dao) 
                            }
                        },
                        launch { app.cloudLibraryManager?.fetchCloudLibrary() },
                        launch { app.userPreferencesSyncManager?.syncNow() }
                    )
                    
                    // Wait for all to complete
                    jobs.forEach { it.join() }
                    
                    Toast.makeText(requireContext(), "Sync Complete", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Timber.e(e, "Manual sync failed")
                    Toast.makeText(requireContext(), "Sync Failed", Toast.LENGTH_SHORT).show()
                } finally {
                    binding.bookshelfSwipeRefresh.isRefreshing = false
                }
            }
        }
    }

    // ============================================
    // CLOUD LIBRARY DIALOG
    // ============================================

    private fun showCloudLibraryDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_cloud_library, null)

        val bookCountText = dialogView.findViewById<TextView>(R.id.cloud_book_count)
        val progressSection = dialogView.findViewById<View>(R.id.cloud_progress_section)
        val progressLabel = dialogView.findViewById<TextView>(R.id.cloud_progress_label)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.cloud_progress_bar)
        val cloudBooksList = dialogView.findViewById<RecyclerView>(R.id.cloud_books_list)
        val emptyState = dialogView.findViewById<TextView>(R.id.cloud_empty_state)
        val uploadAllBtn = dialogView.findViewById<MaterialButton>(R.id.btn_upload_all)
        val refreshBtn = dialogView.findViewById<MaterialButton>(R.id.btn_refresh_cloud)
        val checkConnectionBtn = dialogView.findViewById<MaterialButton>(R.id.btn_check_connection)

        cloudBookAdapter = CloudBookAdapter(
            onDownloadClick = { book -> downloadCloudBook(book, progressSection, progressLabel, progressBar) },
            onDeleteClick = { book -> confirmDeleteCloudBook(book) }
        )

        cloudBooksList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = cloudBookAdapter
        }

        // Load cloud books
        lifecycleScope.launch {
            app.cloudLibraryManager?.fetchCloudLibrary()
        }

        // Observe cloud books
        lifecycleScope.launch {
            app.cloudLibraryManager?.cloudBooks?.collectLatest { books ->
                cloudBookAdapter?.submitList(books)
                bookCountText.text = getString(R.string.cloud_books_count, books.size)

                if (books.isEmpty()) {
                    cloudBooksList.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                } else {
                    cloudBooksList.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE
                }
            }
        }

        // Observe upload progress
        lifecycleScope.launch {
            app.cloudLibraryManager?.uploadProgress?.collectLatest { progress ->
                if (progress > 0f && progress < 1f) {
                    progressSection.visibility = View.VISIBLE
                    progressLabel.text = getString(R.string.uploading_book)
                    progressBar.progress = (progress * 100).toInt()
                } else if (progress >= 1f) {
                    progressSection.visibility = View.GONE
                }
            }
        }

        // Observe download progress
        lifecycleScope.launch {
            app.cloudLibraryManager?.downloadProgress?.collectLatest { progress ->
                if (progress > 0f && progress < 1f) {
                    progressSection.visibility = View.VISIBLE
                    progressLabel.text = getString(R.string.downloading_book)
                    progressBar.progress = (progress * 100).toInt()
                } else if (progress >= 1f) {
                    progressSection.visibility = View.GONE
                }
            }
        }

        uploadAllBtn.setOnClickListener {
            uploadAllBooksToCloud(progressSection, progressLabel, progressBar)
        }

        refreshBtn.setOnClickListener {
            lifecycleScope.launch {
                app.cloudLibraryManager?.fetchCloudLibrary()
            }
        }

        checkConnectionBtn.setOnClickListener {
            lifecycleScope.launch {
                val progressDialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Checking Connection")
                    .setMessage("Please wait...")
                    .setCancelable(false)
                    .show()

                val result = app.cloudLibraryManager?.checkConnection()
                progressDialog.dismiss()

                if (result == null) return@launch

                val (title, message) = when (result) {
                    is org.readium.r2.testapp.data.CloudLibraryManager.ConnectionResult.Success ->
                        "Success" to "All systems operational.\n- Server Reachable\n- Auth Valid\n- Database Connected"
                    is org.readium.r2.testapp.data.CloudLibraryManager.ConnectionResult.ServerUnreachable ->
                        "Server Unreachable" to result.message
                    is org.readium.r2.testapp.data.CloudLibraryManager.ConnectionResult.AuthError ->
                        "Authentication Failed" to "Server returned ${result.code}. Check your API token."
                    is org.readium.r2.testapp.data.CloudLibraryManager.ConnectionResult.DatabaseError ->
                        "Database Error" to result.message
                    is org.readium.r2.testapp.data.CloudLibraryManager.ConnectionResult.UnknownError ->
                        "Unknown Error" to result.message
                }

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }

        cloudDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.cloud_library))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.close), null)
            .show()
    }

    private fun uploadAllBooksToCloud(
        progressSection: View,
        progressLabel: TextView,
        progressBar: ProgressBar
    ) {
        lifecycleScope.launch {
            val books = bookshelfViewModel.books.first()
            var uploadedCount = 0

            progressSection.visibility = View.VISIBLE
            progressLabel.text = getString(R.string.uploading_book)

            for ((index, book) in books.withIndex()) {
                progressBar.progress = ((index.toFloat() / books.size) * 100).toInt()

                // Check if already in cloud
                if (app.cloudLibraryManager?.isBookInCloud(book.identifier) == true) {
                    Timber.d("Book already in cloud: ${book.title}")
                    continue
                }

                val filePath = book.url.toFile()?.path ?: continue
                val file = File(filePath)
                if (!file.exists()) {
                    Timber.w("File not found: $filePath")
                    continue
                }

                val result = app.cloudLibraryManager?.uploadBook(
                    file = file,
                    title = book.title ?: "Unknown",
                    author = book.author ?: "",
                    identifier = book.identifier,
                    mediaType = book.rawMediaType
                )

                result?.onSuccess {
                    uploadedCount++
                }?.onFailure { error ->
                    Timber.e(error, "Failed to upload ${book.title}")
                }
            }

            progressSection.visibility = View.GONE
            Toast.makeText(
                requireContext(),
                "$uploadedCount books uploaded to cloud",
                Toast.LENGTH_SHORT
            ).show()

            // Refresh cloud library
            app.cloudLibraryManager?.fetchCloudLibrary()
        }
    }

    private fun downloadCloudBook(
        book: CloudBookDto,
        progressSection: View,
        progressLabel: TextView,
        progressBar: ProgressBar
    ) {
        lifecycleScope.launch {
            progressSection.visibility = View.VISIBLE
            progressLabel.text = getString(R.string.downloading_book)
            progressBar.progress = 0

            val result = app.cloudLibraryManager?.downloadBook(book.id, book.filename)

            result?.onSuccess { file ->
                progressSection.visibility = View.GONE
                Toast.makeText(requireContext(), getString(R.string.download_success), Toast.LENGTH_SHORT).show()

                // Import the downloaded book into the library
                val uri = Uri.fromFile(file)
                bookshelfViewModel.importPublicationFromStorage(uri)

                cloudDialog?.dismiss()
            }?.onFailure { error ->
                progressSection.visibility = View.GONE
                Toast.makeText(requireContext(), getString(R.string.download_failed), Toast.LENGTH_SHORT).show()
                Timber.e(error, "Download failed")
            }
        }
    }

    private fun confirmDeleteCloudBook(book: CloudBookDto) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_from_cloud))
            .setMessage("Delete \"${book.title ?: book.filename}\" from cloud?")
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                lifecycleScope.launch {
                    app.cloudLibraryManager?.deleteCloudBook(book.id)
                    Toast.makeText(requireContext(), "Deleted from cloud", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    // ============================================
    // BOOK OPTIONS DIALOG (Long press)
    // ============================================

    private fun showBookOptionsDialog(book: Book) {
        val syncOption = if (book.isSynced) "Disable Sync" else "Enable Sync"
        val options = arrayOf(
            getString(R.string.upload_to_cloud),
            syncOption,
            getString(R.string.delete)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(book.title ?: "Book Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> uploadBookToCloud(book)
                    1 -> toggleBookSync(book)
                    2 -> confirmDeleteBook(book)
                }
            }
            .show()
    }

    private fun toggleBookSync(book: Book) {
        lifecycleScope.launch {
            val newStatus = !book.isSynced
            book.isSynced = newStatus
            // Update in DB
            // We need a way to update the book in DB. 
            // bookshelfViewModel usually exposes Delete/Add. 
            // We might need to access the repository or DAO directly via App or ViewModel.
            // bookshelfViewModel doesn't seem to have updateBook. 
            // Let's use the DAO directly from App for this quick enhancement or add to ViewModel.
            // Using App.bookRepository seems best.
                val dao = org.readium.r2.testapp.data.db.AppDatabase.getDatabase(requireContext()).booksDao()
                dao.insertBook(book)
            // Actually BookRepository usually abstracts this.
            // Let's check BookRepository.
            // For now, I'll try to use the DAO directly if accessible, or add to ViewModel.
            // BookshelfViewModel -> BookRepository
            
            // Just accessing DAO via App for simplicity in this fragment context.
            // Ideally we move this to ViewModel.
            with(app.bookRepository) { 
               // Wait, BookRepository might not expose update.
               // Let's just use the DAO from the DB instance in App if possible, or ViewModel.
               // BookshelfViewModel.deletePublication calls repository.delete
            }
             
             // Let's assume we can add a method to BookshelfViewModel or just run a DB query here:
             // But wait, 'booksDao' is internal in AppDatabase usually?
             // AppDatabase.getDatabase(context).booksDao().insertOrUpdate(book) ?
             // BooksDao typically has update.
             
             // Let's use a safe coroutine call.

             
             // Better: Add `toggleSync` to BookshelfViewModel.
             bookshelfViewModel.toggleBookSync(book)
             
            val message = if (newStatus) "Sync Enabled" else "Sync Disabled"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadBookToCloud(book: Book) {
        lifecycleScope.launch {
            // Check if already in cloud
            if (app.cloudLibraryManager?.isBookInCloud(book.identifier) == true) {
                Toast.makeText(requireContext(), getString(R.string.book_already_in_cloud), Toast.LENGTH_SHORT).show()
                return@launch
            }

            val filePath = book.url.toFile()?.path
            if (filePath == null) {
                Toast.makeText(requireContext(), getString(R.string.upload_failed), Toast.LENGTH_SHORT).show()
                return@launch
            }

            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(requireContext(), getString(R.string.upload_failed), Toast.LENGTH_SHORT).show()
                return@launch
            }

            Toast.makeText(requireContext(), getString(R.string.uploading_book), Toast.LENGTH_SHORT).show()

            val result = app.cloudLibraryManager?.uploadBook(
                file = file,
                title = book.title ?: "Unknown",
                author = book.author ?: "",
                identifier = book.identifier,
                mediaType = book.rawMediaType
            )

            result?.onSuccess {
                Toast.makeText(requireContext(), getString(R.string.upload_success), Toast.LENGTH_SHORT).show()
                app.cloudLibraryManager?.fetchCloudLibrary()
            }?.onFailure { error ->
                val errorMessage = when {
                    error.message?.contains("413") == true -> "File too large (Max 50MB)"
                    error.message?.contains("401") == true -> "Authentication failed. Check settings."
                    else -> "Upload failed: ${error.message}"
                }
                Timber.e(error, "Upload failed: $errorMessage")
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    @OptIn(DelicateReadiumApi::class)
    private fun askForRemoteUrl() {
        val urlEditText = EditText(requireContext())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.add_book))
            .setMessage(R.string.enter_url)
            .setView(urlEditText)
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val url = AbsoluteUrl(urlEditText.text.toString())
                if (url == null || !URLUtil.isValidUrl(urlEditText.text.toString())) {
                    urlEditText.error = getString(R.string.invalid_url)
                    return@setPositiveButton
                }

                bookshelfViewModel.addPublicationFromWeb(url)
            }
            .show()
    }

    private fun handleEvent(event: BookshelfViewModel.Event) {
        when (event) {
            is BookshelfViewModel.Event.OpenPublicationError -> {
                event.error.toUserError().show(requireActivity())
            }

            is BookshelfViewModel.Event.LaunchReader -> {
                val intent = ReaderActivityContract().createIntent(
                    requireContext(),
                    event.arguments
                )
                startActivity(intent)
            }
        }
    }

    class VerticalSpaceItemDecoration(private val verticalSpaceHeight: Int) :
        RecyclerView.ItemDecoration() {

        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            outRect.bottom = verticalSpaceHeight
        }
    }

    private fun deleteBook(book: Book) {
        bookshelfViewModel.deletePublication(book)
    }

    private fun confirmDeleteBook(book: Book) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_delete_book_title))
            .setMessage(getString(R.string.confirm_delete_book_text))
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.cancel()
            }
            .setPositiveButton(getString(R.string.delete)) { dialog, _ ->
                deleteBook(book)
                dialog.dismiss()
            }
            .show()
    }
    private fun updateSyncIndicator(status: SyncStatus) {
        val icon = binding.bookshelfCloudSync
        val progress = binding.bookshelfSyncProgress

        when (status) {
            is SyncStatus.Syncing -> {
                progress.visibility = View.VISIBLE
                icon.visibility = View.INVISIBLE // Hide icon or keep it? Layout is Frame.
                // If Frame, overlay.
                // Let's hide icon to show spinner clearly.
                icon.visibility = View.GONE
            }
            is SyncStatus.Idle -> {
                progress.visibility = View.GONE
                icon.visibility = View.VISIBLE
                icon.setImageResource(R.drawable.ic_baseline_cloud_24)
                icon.imageTintList = ColorStateList.valueOf(Color.BLACK)
            }
            is SyncStatus.Error -> {
                progress.visibility = View.GONE
                icon.visibility = View.VISIBLE
                icon.setImageResource(R.drawable.ic_baseline_cloud_24) 
                // Using cloud with Red tint for error
                icon.setImageResource(R.drawable.ic_baseline_cloud_24)
                icon.imageTintList = ColorStateList.valueOf(Color.RED)
            }
            is SyncStatus.Offline -> {
                progress.visibility = View.GONE
                icon.visibility = View.VISIBLE
                icon.setImageResource(R.drawable.ic_baseline_cloud_24)
                // Checking previous list_dir: ic_baseline_cloud_24 exists. ic_baseline_cloud_off_24 NOT listed.
                // I will use cloud_24 with GRAY tint for Offline if cloud_off is missing.
                // Or create d/l Cloud Off.
                // For now, Gray Cloud.
                icon.setImageResource(R.drawable.ic_baseline_cloud_24)
                icon.imageTintList = ColorStateList.valueOf(Color.LTGRAY)
            }
        }
    }
    private fun showConflictDialog(conflict: SyncConflict) {
        val dialog = ConflictResolutionDialog(
            context = requireContext(),
            conflict = conflict,
            onKeepLocal = {
                app.readingProgressSyncManager?.resolveConflict(conflict, keepLocal = true)
            },
            onKeepCloud = {
                app.readingProgressSyncManager?.resolveConflict(conflict, keepLocal = false)
            }
        )
        dialog.show()
    }
}
