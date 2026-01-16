/*
 * Module: r2-testapp-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. European Digital Reading Lab. All rights reserved.
 * Licensed to the Readium Foundation under one or more contributor license agreements.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package com.eqraa.reader.utils

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat
import org.readium.r2.shared.util.toAbsoluteUrl
import com.eqraa.reader.Application
import com.eqraa.reader.MainActivity
import timber.log.Timber

class ImportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        importPublication(intent)

        val newIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(newIntent)

        finish()
    }

    private fun importPublication(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && "text/plain" == intent.type) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                val app = application as Application
                // Check if it's a URL
                if (android.util.Patterns.WEB_URL.matcher(text).matches()) {
                    val url = Uri.parse(text).toAbsoluteUrl()
                    if (url != null) {
                        app.bookshelf.importPublicationFromHttp(url)
                    }
                } else {
                    // It's raw text, convert to EPUB
                    val title = text.lines().firstOrNull()?.take(30)?.trim() ?: "Quick Note"
                    // Generate in a background thread to avoid ANR, although it's short
                    Thread {
                        try {
                            val generator = EpubGenerator(this)
                            val file = generator.createEpub(title, text)
                            runOnUiThread {
                                app.bookshelf.importPublicationFromStorage(Uri.fromFile(file))
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to generate EPUB from text")
                        }
                    }.start()
                }
                return
            }
        }

        val uri = uriFromIntent(intent)
            ?: run {
                Timber.d("Got an empty intent.")
                return
            }

        val app = application as Application
        when {
            uri.scheme == ContentResolver.SCHEME_CONTENT -> {
                app.bookshelf.importPublicationFromStorage(uri)
            }
            else -> {
                val url = uri.toAbsoluteUrl()
                    ?: run {
                        Timber.d("Uri is not an Url.")
                        return
                    }
                app.bookshelf.importPublicationFromHttp(url)
            }
        }
    }

    private fun uriFromIntent(intent: Intent): Uri? =
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if ("text/plain" == intent.type) {
                    intent.getStringExtra(Intent.EXTRA_TEXT).let { Uri.parse(it) }
                } else {
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                }
            }
            else -> {
                intent.data
            }
        }
}
