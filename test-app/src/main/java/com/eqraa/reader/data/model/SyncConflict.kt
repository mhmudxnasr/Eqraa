package com.eqraa.reader.data.model

import com.eqraa.reader.data.model.ReadingPosition

data class SyncConflict(
    val bookId: String,
    val localPosition: ReadingPosition,
    val remotePosition: ReadingPosition,
    val bookTitle: String? = null
)
