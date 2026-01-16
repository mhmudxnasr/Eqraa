/*
 * Compose Outline Fragment
 *
 * Replaces the old OutlineFragment.
 * Hosts ComposeOutlineScreen and connects it to ReaderViewModel.
 */

package com.eqraa.reader.reader.outline

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.eqraa.reader.outline.OutlineContract
import com.eqraa.reader.reader.ReaderViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Publication

class ComposeOutlineFragment : Fragment() {

    private lateinit var publication: Publication
    private lateinit var viewModel: ReaderViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewModelProvider(requireActivity())[ReaderViewModel::class.java].let {
            publication = it.publication
            viewModel = it
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val bookmarks by viewModel.getBookmarks().collectAsState(initial = emptyList())
                val highlights by viewModel.highlights.collectAsState(initial = emptyList())
                
                ComposeOutlineScreen(
                    title = publication.metadata.title ?: "Outline",
                    tableOfContents = publication.tableOfContents,
                    bookmarks = bookmarks,
                    highlights = highlights,
                    onLinkSelected = { link ->
                        val locator = publication.locatorFromLink(link)
                        if (locator != null) {
                            setFragmentResult(
                                OutlineContract.REQUEST_KEY,
                                OutlineContract.createResult(locator)
                            )
                            parentFragmentManager.popBackStack()
                        }
                    },
                    onBookmarkSelected = { bookmark ->
                        setFragmentResult(
                            OutlineContract.REQUEST_KEY,
                            OutlineContract.createResult(bookmark.locator)
                        )
                        parentFragmentManager.popBackStack()
                    },
                    onBookmarkDelete = { bookmark ->
                        bookmark.id?.let { viewModel.deleteBookmark(it) }
                    },
                    onHighlightSelected = { highlight ->
                        setFragmentResult(
                            OutlineContract.REQUEST_KEY,
                            OutlineContract.createResult(highlight.locator)
                        )
                        parentFragmentManager.popBackStack()
                    },
                    onHighlightDelete = { highlight ->
                        viewModel.deleteHighlight(highlight.id)
                    },
                    onClose = {
                        parentFragmentManager.popBackStack()
                    }
                )
            }
        }
    }
}
