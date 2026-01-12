package org.readium.r2.testapp.data.model

import org.readium.r2.testapp.data.model.ReadingPosition

data class SyncConflict(
    val bookId: String,
    val localPosition: ReadingPosition,
    val remotePosition: ReadingPosition,
    val bookTitle: String? = null
)
