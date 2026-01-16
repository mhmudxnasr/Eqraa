package com.eqraa.reader.opds

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.eqraa.reader.databinding.FragmentOpdsFeedBinding
import com.eqraa.reader.domain.PublicationRetriever
// import com.eqraa.reader.reader.ReaderViewModelFactory (Unused)
import com.eqraa.reader.utils.viewLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.Link
import java.io.File
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs

class OpdsFeedFragment : Fragment() {

    private var binding: FragmentOpdsFeedBinding by viewLifecycle()
    
    // We need a factory that can provide OpdsRepository. 
    // For now assuming we can get it or inject it. 
    // In a real app we'd use Hilt or manual DI from Application.
    // Simplifying for this step: creating VM usage manually or assuming factory availability.
    // CHECK: OpdsViewModel needs OpdsRepository which needs HttpClient.
    
    // Placeholder VM creation - in reality we need to pass dependencies
    // private val viewModel: OpdsViewModel by viewModels { ... } 
    
    // Hack for now: accessing dependencies from global or similar if possible, 
    // or we'll define a simple Factory in the same file to make it work.
    
    private lateinit var viewModel: OpdsViewModel
    private lateinit var adapter: OpdsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOpdsFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Setup VM 
        val app = requireActivity().application as com.eqraa.reader.Application
        val repo = OpdsRepository(app.readium.httpClient) 
        viewModel = OpdsViewModel(repo)

        setupRecyclerView()
        
        // Load initial feed (arguments or default)
        val url = arguments?.getString("url") ?: "https://standardebooks.org/opds/all"
        timber.log.Timber.d("OPDS Fragment: Loading URL: $url")
        viewModel.loadFeed(url)
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when(state) {
                    is OpdsUiState.Loading -> {
                        // show loading
                    }
                    is OpdsUiState.Success -> {
                        adapter.submitList(state.items)
                    }
                    is OpdsUiState.Error -> {
                        Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = OpdsAdapter { item ->
            when(item) {
                is OpdsItem.Group -> {
                    val link = item.links.firstOrNull()
                    if (link != null) {
                        // Navigate to self with new URL
                        val bundle = Bundle().apply { putString("url", link.href.toString()) }
                        findNavController().navigate(com.eqraa.reader.R.id.action_opds_self, bundle) 
                        // Note: Need to define this action in nav graph
                    }
                }
                is OpdsItem.Book -> {
                    // Download
                    // We need access to PublicationRetriever here
                    downloadBook(item.publication)
                }
            }
        }
        binding.catalogFeedList.layoutManager = GridLayoutManager(context, 3) // Grid
        binding.catalogFeedList.adapter = adapter
    }
    
    private fun downloadBook(publication: Publication) {
        Toast.makeText(context, "Downloading ${publication.metadata.title}...", Toast.LENGTH_SHORT).show()
        val app = requireActivity().application as com.eqraa.reader.Application
        
        lifecycleScope.launch {
            // Ideally we used the bookshelf instance
            app.bookshelf.importPublicationFromOpds(publication)
        }
    }
}
