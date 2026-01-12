/*
 * Cloud Library Manager
 *
 * Handles uploading and downloading book files to/from the cloud server:
 * - Multipart file uploads with Retrofit
 * - Streaming downloads with progress tracking
 * - Cloud library listing
 * - Delete operations
 */

package org.readium.r2.testapp.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.upload
import io.github.jan.supabase.postgrest.from
import okhttp3.OkHttpClient
import okhttp3.Request
import org.readium.r2.testapp.BuildConfig

import io.github.jan.supabase.storage.storage
import org.readium.r2.testapp.data.model.CloudBookDto
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Manager class for cloud library operations.
 * Handles uploading, downloading, listing, and deleting book files from the server.
 */
class CloudLibraryManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val storageDir: File
) {
    companion object {
        private const val AUTH_TOKEN = SyncConfig.AUTH_TOKEN
        // private const val BASE_URL = SyncConfig.SYNC_SERVER_URL // Unused
    }

    private val supabase = SupabaseService.client
    private val bookBucket = supabase.storage.from("books")



    // Upload state
    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    // Download state
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    // Cloud library list
    private val _cloudBooks = MutableStateFlow<List<CloudBookDto>>(emptyList())
    val cloudBooks: StateFlow<List<CloudBookDto>> = _cloudBooks.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // ============================================
    // UPLOAD OPERATIONS
    // ============================================

    /**
     * Upload a book file to the cloud server.
     *
     * @param file The book file to upload
     * @param title Book title
     * @param author Book author
     * @param identifier Unique book identifier (e.g., ISBN)
     * @param mediaType MIME type of the file
     * @return Result containing the cloud book ID on success
     */
    suspend fun uploadBook(
        file: File,
        title: String,
        author: String,
        identifier: String,
        mediaType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            _isUploading.value = true
            _uploadProgress.value = 0f
            _lastError.value = null

            Timber.d("Starting upload: ${file.name} (${file.length()} bytes)")

            val filename = "${identifier}/${file.name}" // Store under identifier folder? Or just uuid
            // Using a simpler path structure: "user_id/identifier.epub" ?
            // Supabase auth user is implicit. RLS handles isolation.
            // Let's use "identifier_filename" or just "filename" if unique locally.
            // Identifier is safer.
            val storagePath = "$identifier/${file.name}"

            // 1. Upload to Storage
            bookBucket.upload(storagePath, file.readBytes()) {
                upsert = true
            }
            _uploadProgress.value = 0.5f

            // 2. Insert Metadata to DB
            val bookDto = CloudBookDto(
                id = java.util.UUID.randomUUID().toString(),
                identifier = identifier,
                title = title,
                author = author,
                filename = file.name, // Original name for display/download
                storedFilename = storagePath, // Path in bucket
                mediaType = mediaType
            )
            
            // Upsert metadata
            val result = supabase.from("cloud_books").upsert(bookDto) { select() }.decodeSingle<CloudBookDto>()
            
            _uploadProgress.value = 1f
            Timber.d("Upload successful: ${result.id}")
            fetchCloudLibrary()
            Result.success(result.id)

        } catch (e: Exception) {
            Timber.e(e, "Upload exception")
            _lastError.value = e.message
            Result.failure(e)
        } finally {
            _isUploading.value = false
        }
    }

    /**
     * Upload a book from the local library using its file path.
     */
    suspend fun uploadBookFromPath(
        filePath: String,
        title: String,
        author: String,
        identifier: String,
        mediaType: String
    ): Result<String> {
        val file = File(filePath)
        if (!file.exists()) {
            return Result.failure(Exception("File not found: $filePath"))
        }
        return uploadBook(file, title, author, identifier, mediaType)
    }

    // ============================================
    // DOWNLOAD OPERATIONS
    // ============================================

    /**
     * Download a book from the cloud server.
     *
     * @param bookId The cloud book ID
     * @param filename The original filename to save as
     * @return Result containing the downloaded file on success
     */
    suspend fun downloadBook(bookId: String, filename: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            _isDownloading.value = true
            _downloadProgress.value = 0f
            _lastError.value = null

            Timber.d("Starting download: $bookId -> $filename")

            // First fetch the book metadata to get the filename/path
            val book = _cloudBooks.value.find { it.id == bookId || it.identifier == bookId } 
                ?: throw Exception("Book metadata not found locally. Fetch library first.")
            
            val storagePath = book.storedFilename
            val localFilename = book.filename.substringAfterLast("/") // Clean filename for local save
            
            // Manual download with OkHttp as workaround for unresolved reference
            val bucketId = "books"
            val url = "${BuildConfig.SUPABASE_URL}/storage/v1/object/authenticated/$bucketId/$storagePath"
            
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}") 
                .addHeader("apikey", BuildConfig.SUPABASE_KEY)
                .build()
                
            val bytes = withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Download failed: ${response.code} ${response.message}")
                    response.body?.bytes() ?: throw Exception("Empty response body")
                }
            }
            _downloadProgress.value = 0.5f
            
            val targetFile = File(storageDir, localFilename)
            targetFile.writeBytes(bytes)
            
            _downloadProgress.value = 1f
            Timber.d("Download successful: ${targetFile.absolutePath}")
            Result.success(targetFile)

        } catch (e: Exception) {
            Timber.e(e, "Download exception")
            _lastError.value = e.message
            Result.failure(e)
        } finally {
            _isDownloading.value = false
        }
    }

    // ============================================
    // LIBRARY OPERATIONS
    // ============================================

    /**
     * Fetch the list of books stored in the cloud.
     */
    suspend fun fetchCloudLibrary(): Result<List<CloudBookDto>> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Fetching cloud library...")
            Timber.d("Fetching cloud library...")
            val books = supabase.from("cloud_books").select().decodeList<CloudBookDto>()
            
            _cloudBooks.value = books
            Timber.d("Cloud library fetched: ${books.size} books")
            Result.success(books)

        } catch (e: Exception) {
            Timber.e(e, "Fetch library exception")
            _lastError.value = e.message
            Result.failure(e)
        }
    }

    /**
     * Delete a book from the cloud server.
     */
    suspend fun deleteCloudBook(bookId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Deleting cloud book: $bookId")
            Timber.d("Deleting cloud book: $bookId")
             // 1. Delete from Storage (Need path)
             // We need to know the path. Assuming we have metadata locally.
             val book = _cloudBooks.value.find { it.id == bookId }
             if (book != null) {
                 bookBucket.delete(book.storedFilename)
             }
             
             // 2. Delete from DB
             supabase.from("cloud_books").delete {
                 filter { eq("id", bookId) }
             }
             
            Timber.d("Cloud book deleted: $bookId")
            fetchCloudLibrary()
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Delete exception")
            _lastError.value = e.message
            Result.failure(e)
        }
    }

    /**
     * Check if a book with the given identifier exists in the cloud.
     */
    fun isBookInCloud(identifier: String): Boolean {
        return _cloudBooks.value.any { it.identifier == identifier }
    }

    /**
     * Get a cloud book by its identifier.
     */
    fun getCloudBookByIdentifier(identifier: String): CloudBookDto? {
        return _cloudBooks.value.find { it.identifier == identifier }
    }

    /**
     * Clear any error state.
     */
    fun clearError() {
        _lastError.value = null
    }

    // ============================================
    // DIAGNOSTICS
    // ============================================

    sealed class ConnectionResult {
        object Success : ConnectionResult()
        data class ServerUnreachable(val message: String) : ConnectionResult()
        data class AuthError(val code: Int) : ConnectionResult()
        data class DatabaseError(val message: String) : ConnectionResult()
        data class UnknownError(val message: String) : ConnectionResult()
    }

    suspend fun checkConnection(): ConnectionResult = withContext(Dispatchers.IO) {
        try {
            // 1. Check Auth (Session)
            if (supabase.auth.currentUserOrNull() == null) {
                // Try refreshing?
                return@withContext ConnectionResult.AuthError(401)
            }
            
            // 2. Check Database Reachability
            try {
                // Simple count query
                 supabase.from("cloud_books").select {
                     count(io.github.jan.supabase.postgrest.query.Count.EXACT)
                     limit(1)
                 }
                 return@withContext ConnectionResult.Success
            } catch (e: Exception) {
                return@withContext ConnectionResult.DatabaseError("DB Check failed: ${e.message}")
            }
            
        } catch (e: Exception) {
            return@withContext ConnectionResult.UnknownError(e.message ?: "Unknown error")
        }
    }
}
