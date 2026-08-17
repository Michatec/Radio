package com.michatec.radio.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.R
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.michatec.radio.Keys
import com.michatec.radio.collection.CollectionViewModel
import com.michatec.radio.core.Station
import com.michatec.radio.databinding.DialogFindStationBinding
import com.michatec.radio.helpers.CollectionHelper
import com.michatec.radio.helpers.NetworkHelper
import com.michatec.radio.search.DirectInputCheck
import com.michatec.radio.search.RadioBrowserResult
import com.michatec.radio.search.RadioBrowserSearch
import com.michatec.radio.search.SearchResultAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddStationFragment : Fragment(),
    SearchResultAdapter.SearchResultAdapterListener,
    RadioBrowserSearch.RadioBrowserSearchListener,
    DirectInputCheck.DirectInputCheckListener {

    private var _binding: DialogFindStationBinding? = null
    private val binding get() = _binding!!
    private lateinit var collectionViewModel: CollectionViewModel
    private lateinit var searchResultAdapter: SearchResultAdapter
    private lateinit var radioBrowserSearch: RadioBrowserSearch
    private lateinit var directInputCheck: DirectInputCheck
    private var station: Station = Station()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // We reuse the dialog layout as it's already optimized for TV in layout-television
        _binding = DialogFindStationBinding.inflate(inflater, container, false)

        collectionViewModel = ViewModelProvider(requireActivity())[CollectionViewModel::class.java]
        radioBrowserSearch = RadioBrowserSearch(this)
        directInputCheck = DirectInputCheck(this)

        setupRecyclerView()
        setupSearchView()

        binding.dialogPositiveButton?.setOnClickListener {
            addStationAndExit()
        }

        binding.dialogNegativeButton?.setOnClickListener {
            searchResultAdapter.stopPrePlayback()
            findNavController().navigateUp()
        }

        binding.stationSearchBoxView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(query: String): Boolean {
                handleSearch(query)
                return true
            }
            override fun onQueryTextSubmit(query: String): Boolean {
                handleSearch(query)
                return true
            }
        })

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop playback when fragment is destroyed (e.g. via back button)
        if (this::searchResultAdapter.isInitialized) {
            searchResultAdapter.stopPrePlayback()
        }
    }

    private fun setupRecyclerView() {
        searchResultAdapter = SearchResultAdapter(this, listOf())
        binding.stationSearchResultList.adapter = searchResultAdapter
        binding.stationSearchResultList.layoutManager = LinearLayoutManager(context)
        binding.stationSearchResultList.itemAnimator = DefaultItemAnimator()
    }

    private fun setupSearchView() {
        // TV specific: ensure keyboard opens when search view gets focus
        binding.stationSearchBoxView.setOnQueryTextFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                // Find the internal EditText of the SearchView
                val searchEditText = v.findViewById<EditText>(R.id.search_src_text)
                if (searchEditText != null) {
                    searchEditText.requestFocus()
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(searchEditText, 0)
                }
            }
        }

        // Make the SearchView always expanded and ready for input
        binding.stationSearchBoxView.isIconified = false
    }

    private fun handleSearch(query: String) {
        if (query.isEmpty()) {
            resetLayout(true)
            return
        }
        showProgressIndicator()
        if (query.startsWith("http")) {
            directInputCheck.checkStationAddress(requireContext(), query)
        } else {
            radioBrowserSearch.searchStation(requireContext(), query, Keys.SEARCH_TYPE_BY_KEYWORD)
        }
    }

    private fun addStationAndExit() {
        searchResultAdapter.stopPrePlayback()
        val currentCollection = collectionViewModel.collectionLiveData.value ?: return
        if (station.streamContent.isNotEmpty() && station.streamContent != Keys.MIME_TYPE_UNSUPPORTED) {
            CollectionHelper.addStation(requireContext(), currentCollection, station)
            findNavController().navigateUp()
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                val contentType = NetworkHelper.detectContentType(station.getStreamUri())
                station.streamContent = contentType.type
                withContext(Dispatchers.Main) {
                    CollectionHelper.addStation(requireContext(), currentCollection, station)
                    findNavController().navigateUp()
                }
            }
        }
    }

    override fun onSearchResultTapped(result: Station) {
        station = result
        activateAddButton()
    }

    override fun activateAddButton() {
        binding.dialogPositiveButton?.isEnabled = true
    }

    override fun deactivateAddButton() {
        binding.dialogPositiveButton?.isEnabled = false
    }

    override fun onRadioBrowserSearchResults(results: Array<RadioBrowserResult>) {
        if (results.isNotEmpty()) {
            searchResultAdapter.updateSearchResults(results.map { it.toStation() })
            resetLayout(false)
        } else {
            showNoResultsError()
        }
    }

    override fun onDirectInputCheck(stationList: MutableList<Station>) {
        if (stationList.isNotEmpty()) {
            searchResultAdapter.updateSearchResults(stationList)
            resetLayout(false)
        } else {
            showNoResultsError()
        }
    }

    private fun resetLayout(clear: Boolean) {
        binding.dialogPositiveButton?.isEnabled = false
        binding.searchRequestProgressIndicator.isGone = true
        binding.noResultsTextView.isGone = true
        if (clear) searchResultAdapter.resetSelection(true)
    }

    private fun showProgressIndicator() {
        binding.searchRequestProgressIndicator.isVisible = true
        binding.noResultsTextView.isGone = true
    }

    private fun showNoResultsError() {
        binding.searchRequestProgressIndicator.isGone = true
        binding.noResultsTextView.isVisible = true
    }
}