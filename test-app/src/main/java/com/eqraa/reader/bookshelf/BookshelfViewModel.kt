/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package com.eqraa.reader.bookshelf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.toUrl
import com.eqraa.reader.data.model.Book
import com.eqraa.reader.reader.OpeningError
import com.eqraa.reader.reader.ReaderActivityContract
import com.eqraa.reader.utils.EventChannel

class BookshelfViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() =
        getApplication<com.eqraa.reader.Application>()

    val channel = EventChannel(Channel<Event>(Channel.BUFFERED), viewModelScope)
    val books = app.bookRepository.books()

    fun deletePublication(book: Book) =
        viewModelScope.launch {
            app.bookshelf.deleteBook(book)
        }

    fun importPublicationFromStorage(uri: Uri) {
        app.bookshelf.importPublicationFromStorage(uri)
    }

    fun addPublicationFromStorage(uri: Uri) {
        app.bookshelf.addPublicationFromStorage(uri.toUrl()!! as AbsoluteUrl)
    }

    fun addPublicationFromWeb(url: AbsoluteUrl) {
        app.bookshelf.addPublicationFromWeb(url)
    }

    fun openPublication(
        bookId: Long,
    ) {
        viewModelScope.launch {
            app.readerRepository
                .open(bookId)
                .onFailure {
                    channel.send(Event.OpenPublicationError(it))
                }
                .onSuccess {
                    val arguments = ReaderActivityContract.Arguments(bookId)
                    channel.send(Event.LaunchReader(arguments))
                }
        }
    }

    fun toggleBookSync(book: Book) {
        viewModelScope.launch {
            val database = com.eqraa.reader.data.db.AppDatabase.getDatabase(app)
            val newStatus = book.isSynced // Already toggled in fragment? No, fragment toggled the object.
            // Wait, Fragment did: 
            // val newStatus = !book.isSynced
            // book.isSynced = newStatus
            // viewModel.toggleBookSync(book)
            
            // So 'book.isSynced' here is already the NEW status. 
            // We should use book.id
            book.id?.let { id ->
                 database.booksDao().updateBookSyncStatus(id, book.isSynced)
            }
        }
    }

    sealed class Event {

        class OpenPublicationError(
            val error: OpeningError,
        ) : Event()

        class LaunchReader(
            val arguments: ReaderActivityContract.Arguments,
        ) : Event()
    }
}
