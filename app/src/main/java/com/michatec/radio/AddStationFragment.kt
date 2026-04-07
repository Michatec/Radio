package com.michatec.radio

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.michatec.radio.collection.CollectionViewModel
import com.michatec.radio.core.Station
import com.michatec.radio.helpers.CollectionHelper
import com.michatec.radio.helpers.NetworkHelper
import com.michatec.radio.search.DirectInputCheck
import com.michatec.radio.search.RadioBrowserResult
import com.michatec.radio.search.RadioBrowserSearch
import com.michatec.radio.search.SearchResultAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddStationFragment : Fragment(),
    SearchResultAdapter.SearchResultAdapterListener,
    RadioBrowserSearch.RadioBrowserSearchListener,
    DirectInputCheck.DirectInputCheckListener {

    private lateinit var collectionViewModel: CollectionViewModel
    private lateinit var stationSearchBoxView: SearchView
    private lateinit var searchRequestProgressIndicator: ProgressBar
    private lateinit var noSearchResultsTextView: MaterialTextView
    private lateinit var stationSearchResultList: RecyclerView
    private lateinit var positiveButton: Button
    private lateinit var negativeButton: Button
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
        val view = inflater.inflate(R.layout.dialog_find_station, container, false)
        
        collectionViewModel = ViewModelProvider(requireActivity())[CollectionViewModel::class.java]
        radioBrowserSearch = RadioBrowserSearch(this)
        directInputCheck = DirectInputCheck(this)

        stationSearchBoxView = view.findViewById(R.id.station_search_box_view)
        searchRequestProgressIndicator = view.findViewById(R.id.search_request_progress_indicator)
        stationSearchResultList = view.findViewById(R.id.station_search_result_list)
        noSearchResultsTextView = view.findViewById(R.id.no_results_text_view)
        positiveButton = view.findViewById(R.id.dialog_positive_button)
        negativeButton = view.findViewById(R.id.dialog_negative_button)

        setupRecyclerView()
        setupSearchView()

        positiveButton.setOnClickListener {
            addStationAndExit()
        }

        negativeButton.setOnClickListener {
            searchResultAdapter.stopPrePlayback()
            findNavController().navigateUp()
        }

        stationSearchBoxView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(query: String): Boolean {
                handleSearch(query)
                return true
            }
            override fun onQueryTextSubmit(query: String): Boolean {
                handleSearch(query)
                return true
            }
        })

        return view
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
        stationSearchResultList.adapter = searchResultAdapter
        stationSearchResultList.layoutManager = LinearLayoutManager(context)
        stationSearchResultList.itemAnimator = DefaultItemAnimator()
    }

    private fun setupSearchView() {
        // TV specific: ensure keyboard opens when search view gets focus
        stationSearchBoxView.setOnQueryTextFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                // Find the internal EditText of the SearchView
                val searchEditText = v.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
                if (searchEditText != null) {
                    searchEditText.requestFocus()
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }

        // Make the SearchView always expanded and ready for input
        stationSearchBoxView.isIconified = false
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
            CoroutineScope(IO).launch {
                val contentType = NetworkHelper.detectContentType(station.getStreamUri())
                station.streamContent = contentType.type
                withContext(Main) {
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
        positiveButton.isEnabled = true
    }

    override fun deactivateAddButton() {
        positiveButton.isEnabled = false
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onRadioBrowserSearchResults(results: Array<RadioBrowserResult>) {
        if (results.isNotEmpty()) {
            searchResultAdapter.searchResults = results.map { it.toStation() }
            searchResultAdapter.notifyDataSetChanged()
            resetLayout(false)
        } else {
            showNoResultsError()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onDirectInputCheck(stationList: MutableList<Station>) {
        if (stationList.isNotEmpty()) {
            searchResultAdapter.searchResults = stationList
            searchResultAdapter.notifyDataSetChanged()
            resetLayout(false)
        } else {
            showNoResultsError()
        }
    }

    private fun resetLayout(clear: Boolean) {
        positiveButton.isEnabled = false
        searchRequestProgressIndicator.isGone = true
        noSearchResultsTextView.isGone = true
        if (clear) searchResultAdapter.resetSelection(true)
    }

    private fun showProgressIndicator() {
        searchRequestProgressIndicator.isVisible = true
        noSearchResultsTextView.isGone = true
    }

    private fun showNoResultsError() {
        searchRequestProgressIndicator.isGone = true
        noSearchResultsTextView.isVisible = true
    }
}
