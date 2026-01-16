package com.eqraa.reader.opds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.readium.r2.shared.opds.Feed
import org.readium.r2.shared.opds.ParseData
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Try
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OpdsViewModel(
    private val repository: OpdsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<OpdsUiState>(OpdsUiState.Loading)
    val uiState: StateFlow<OpdsUiState> = _uiState.asStateFlow()

    fun loadFeed(url: String) {
        viewModelScope.launch {
            _uiState.value = OpdsUiState.Loading
            
            when (val result = repository.loadFeed(url)) {
                is Try.Success -> {
                    val feed = result.value.feed
                    val items = mutableListOf<OpdsItem>()
                    
                    if (feed != null) {
                        // Add Groups/Navigation
                        // Note: Readium OPDS model structure might vary slightly between 1 & 2
                        // For OPDS 1, navigation links are often in 'navigation'
                        // For this simple implementation, we map all 'navigation' links to Groups
                        
                        // Add Facets (Search, Sort)
                        feed.facets.forEach { facet ->
                            items.add(OpdsItem.Group("Filter: ${facet.metadata.title}", facet.links))
                        }

                        feed.navigation.forEach { link ->
                            items.add(OpdsItem.Group(link.title ?: link.href.toString(), listOf(link)))
                        }
                        
                        feed.groups.forEach { group ->
                            items.add(OpdsItem.Group(group.title ?: "Group", group.links))
                            
                            // Also add publications inside groups if any (Calibre sometimes puts featured books in groups)
                            group.publications.forEach { pub ->
                                items.add(OpdsItem.Book(pub)) 
                            }
                        }

                        // Add Publications
                        feed.publications.forEach { pub ->
                            items.add(OpdsItem.Book(pub))
                        }
                    }
                    
                    _uiState.value = OpdsUiState.Success(items, result.value.feed, result.value.publication)
                }
                is Try.Failure -> {
                    _uiState.value = OpdsUiState.Error(result.value.message ?: "Unknown error")
                }
            }
        }
    }
}

sealed class OpdsUiState {
    object Loading : OpdsUiState()
    data class Success(val items: List<OpdsItem>, val feed: Feed?, val publication: Publication?) : OpdsUiState()
    data class Error(val message: String) : OpdsUiState()
}
