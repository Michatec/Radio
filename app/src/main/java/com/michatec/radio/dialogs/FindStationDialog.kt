package com.michatec.radio.dialogs

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michatec.radio.Keys
import com.michatec.radio.R
import com.michatec.radio.core.Station
import com.michatec.radio.databinding.DialogFindStationBinding
import com.michatec.radio.search.DirectInputCheck
import com.michatec.radio.search.RadioBrowserResult
import com.michatec.radio.search.RadioBrowserSearch
import com.michatec.radio.search.SearchResultAdapter


/*
 * FindStationDialog class
 */
class FindStationDialog (
    private val context: Context,
    private val listener: FindStationDialogListener):
    SearchResultAdapter.SearchResultAdapterListener,
    RadioBrowserSearch.RadioBrowserSearchListener,
    DirectInputCheck.DirectInputCheckListener {

    /* Interface used to communicate back to activity */
    interface FindStationDialogListener {
        fun onFindStationDialog(station: Station) {
        }
    }


    /* Main class variables */
    private lateinit var binding: DialogFindStationBinding
    private lateinit var dialog: AlertDialog
    private lateinit var searchResultAdapter: SearchResultAdapter
    private lateinit var radioBrowserSearch: RadioBrowserSearch
    private lateinit var directInputCheck: DirectInputCheck
    private var currentSearchString: String = String()
    private val handler: Handler = Handler(Looper.getMainLooper())
    private var station: Station = Station()


    /* Overrides onSearchResultTapped from SearchResultAdapterListener */
    override fun onSearchResultTapped(result: Station) {
        station = result
        // hide keyboard
        val imm: InputMethodManager =
            context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.stationSearchBoxView.windowToken, 0)
        // make add button clickable
        activateAddButton()
    }


    /* Overrides onRadioBrowserSearchResults from RadioBrowserSearchListener */
    override fun onRadioBrowserSearchResults(results: Array<RadioBrowserResult>) {
        if (results.isNotEmpty()) {
            val stationList: List<Station> = results.map {it.toStation()}
            searchResultAdapter.updateSearchResults(stationList)
            resetLayout(clearAdapter = false)
        } else {
            showNoResultsError()
        }
    }


    /* Overrides onDirectInputCheck from DirectInputCheck */
    override fun onDirectInputCheck(stationList: MutableList<Station>) {
        if (stationList.isNotEmpty()) {
            searchResultAdapter.updateSearchResults(stationList)
            resetLayout(clearAdapter = false)
        } else {
            showNoResultsError()
        }
    }


    /* Construct and show dialog */
    fun show() {
        // initialize a radio browser search and direct url input check
        radioBrowserSearch = RadioBrowserSearch(this)
        directInputCheck = DirectInputCheck(this)

        // prepare dialog builder
        val builder = MaterialAlertDialogBuilder(context)

        // set title
        builder.setTitle(R.string.dialog_find_station_title)

        // get binding
        binding = DialogFindStationBinding.inflate(LayoutInflater.from(context))
        binding.noResultsTextView.isGone = true

        // set up list of search results
        setupRecyclerView(context)

        // add okay ("Add") button
        builder.setPositiveButton(R.string.dialog_find_station_button_add) { _, _ ->
            // listen for click on add button
            listener.onFindStationDialog(station)
            searchResultAdapter.stopPrePlayback()
        }
        // add cancel button
        builder.setNegativeButton(R.string.dialog_generic_button_cancel) { _, _ ->
            // listen for click on cancel button
            radioBrowserSearch.stopSearchRequest()
            searchResultAdapter.stopPrePlayback()
        }
        // handle outside-click as "no"
        builder.setOnCancelListener {
            radioBrowserSearch.stopSearchRequest()
            searchResultAdapter.stopPrePlayback()
        }

        // set up custom buttons if they exist (TV layout)
        binding.dialogPositiveButton?.setOnClickListener {
            listener.onFindStationDialog(station)
            searchResultAdapter.stopPrePlayback()
            dialog.dismiss()
        }
        binding.dialogNegativeButton?.setOnClickListener {
            radioBrowserSearch.stopSearchRequest()
            searchResultAdapter.stopPrePlayback()
            dialog.dismiss()
        }

        // listen for input
        binding.stationSearchBoxView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(query: String): Boolean {
                handleSearchBoxLiveInput(context, query)
                searchResultAdapter.stopPrePlayback()
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                handleSearchBoxInput(context, query)
                searchResultAdapter.stopPrePlayback()
                return true
            }
        })

        // set dialog view
        builder.setView(binding.root)

        // create and display dialog
        dialog = builder.create()
        dialog.show()

        // handle button visibility and state
        if (binding.dialogPositiveButton != null) {
            // hide default buttons if custom ones are used
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isGone = true
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isGone = true
            binding.dialogPositiveButton?.isEnabled = false
        } else {
            // initially disable default "Add" button
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isAllCaps = true
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isAllCaps = true
        }
    }


    /* Sets up list of results (RecyclerView) */
    private fun setupRecyclerView(context: Context) {
        searchResultAdapter = SearchResultAdapter(this, listOf())
        binding.stationSearchResultList.adapter = searchResultAdapter
        val layoutManager: LinearLayoutManager = object : LinearLayoutManager(context) {
            override fun supportsPredictiveItemAnimations(): Boolean {
                return true
            }
        }
        binding.stationSearchResultList.layoutManager = layoutManager
        binding.stationSearchResultList.itemAnimator = DefaultItemAnimator()
    }


    /* Handles user input into search box - user has to submit the search */
    private fun handleSearchBoxInput(context: Context, query: String) {
        when {
            // handle empty search box input
            query.isEmpty() -> {
                resetLayout(clearAdapter = true)
            }
            // handle direct URL input
            query.startsWith("http") -> {
                directInputCheck.checkStationAddress(context, query)
            }
            // handle search string input
            else -> {
                showProgressIndicator()
                radioBrowserSearch.searchStation(context, query, Keys.SEARCH_TYPE_BY_KEYWORD)
            }
        }
    }


    /* Handles live user input into search box */
    private fun handleSearchBoxLiveInput(context: Context, query: String) {
        currentSearchString = query
        if (query.startsWith("htt")) {
            // handle direct URL input
            directInputCheck.checkStationAddress(context, query)
        } else if (query.contains(" ") || query.length > 2) {
            // show progress indicator
            showProgressIndicator()
            // handle search string input - delay request to manage server load (not sure if necessary)
            handler.postDelayed({
                // only start search if query is the same as one second ago
                if (currentSearchString == query) radioBrowserSearch.searchStation(
                    context,
                    query,
                    Keys.SEARCH_TYPE_BY_KEYWORD
                )
            }, 100)
        } else if (query.isEmpty()) {
            resetLayout(clearAdapter = true)
        }
    }


    /* Makes the "Add" button clickable */
    override fun activateAddButton() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
        binding.dialogPositiveButton?.isEnabled = true
        binding.searchRequestProgressIndicator.isGone = true
        binding.noResultsTextView.isGone = true
    }

    override fun deactivateAddButton() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        binding.dialogPositiveButton?.isEnabled = false
        binding.searchRequestProgressIndicator.isGone = true
        binding.noResultsTextView.isGone = true
    }


    /* Resets the dialog layout to default state */
    private fun resetLayout(clearAdapter: Boolean = false) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        binding.dialogPositiveButton?.isEnabled = false
        binding.searchRequestProgressIndicator.isGone = true
        binding.noResultsTextView.isGone = true
        searchResultAdapter.resetSelection(clearAdapter)
    }


    /* Display the "No Results" error - hide other unneeded views */
    private fun showNoResultsError() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        binding.dialogPositiveButton?.isEnabled = false
        binding.searchRequestProgressIndicator.isGone = true
        binding.noResultsTextView.isVisible = true
    }


    /* Display the "No Results" error - hide other unneeded views */
    private fun showProgressIndicator() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        binding.dialogPositiveButton?.isEnabled = false
        binding.searchRequestProgressIndicator.isVisible = true
        binding.noResultsTextView.isGone = true
    }

}
