package com.michatec.radio.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michatec.radio.R
import com.michatec.radio.core.Station
import com.michatec.radio.search.SearchResultAdapter


/*
 * AddStationDialog class
 */
class AddStationDialog (
    private val context: Context,
    private val stationList: List<Station>,
    private val listener: AddStationDialogListener) :
    SearchResultAdapter.SearchResultAdapterListener {


    /* Interface used to communicate back to activity */
    interface AddStationDialogListener {
        fun onAddStationDialog(station: Station)
    }


    /* Main class variables */
    private lateinit var dialog: AlertDialog
    private lateinit var stationSearchResultList: RecyclerView
    private var customPositiveButton: Button? = null
    private var customNegativeButton: Button? = null
    private lateinit var searchResultAdapter: SearchResultAdapter
    private var station: Station = Station()


    /* Overrides onSearchResultTapped from SearchResultAdapterListener */
    override fun onSearchResultTapped(result: Station) {
        station = result
        // make add button clickable
        activateAddButton()
    }


    /* Construct and show dialog */
    fun show() {
        // prepare dialog builder
        val builder = MaterialAlertDialogBuilder(context)

        // set title
        builder.setTitle(R.string.dialog_add_station_title)

        // get views
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_add_station, null)
        stationSearchResultList = view.findViewById(R.id.station_list)

        // set up list of search results
        setupRecyclerView(context)

        // find custom buttons (for TV layout)
        customPositiveButton = view.findViewById(R.id.dialog_positive_button)
        customNegativeButton = view.findViewById(R.id.dialog_negative_button)

        // add okay ("Add") button
        builder.setPositiveButton(R.string.dialog_find_station_button_add) { _, _ ->
            // listen for click on add button
            listener.onAddStationDialog(station)
            searchResultAdapter.stopPrePlayback()
        }
        // add cancel button
        builder.setNegativeButton(R.string.dialog_generic_button_cancel) { _, _ ->
            searchResultAdapter.stopPrePlayback()
        }
        // handle outside-click as "no"
        builder.setOnCancelListener {
            searchResultAdapter.stopPrePlayback()
        }

        // set up custom buttons if they exist (TV layout)
        customPositiveButton?.setOnClickListener {
            listener.onAddStationDialog(station)
            searchResultAdapter.stopPrePlayback()
            dialog.dismiss()
        }
        customNegativeButton?.setOnClickListener {
            searchResultAdapter.stopPrePlayback()
            dialog.dismiss()
        }

        // set dialog view
        builder.setView(view)

        // create and display dialog
        dialog = builder.create()
        dialog.show()

        // handle button visibility and state
        if (customPositiveButton != null) {
            // hide default buttons if custom ones are used
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isGone = true
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isGone = true
            customPositiveButton?.isEnabled = false
        } else {
            // initially disable default "Add" button
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        }
    }


    /* Sets up list of results (RecyclerView) */
    private fun setupRecyclerView(context: Context) {
        searchResultAdapter = SearchResultAdapter(this, stationList)
        stationSearchResultList.adapter = searchResultAdapter
        val layoutManager: LinearLayoutManager = object: LinearLayoutManager(context) {
            override fun supportsPredictiveItemAnimations(): Boolean {
                return true
            }
        }
        stationSearchResultList.layoutManager = layoutManager
        stationSearchResultList.itemAnimator = DefaultItemAnimator()
    }


    /* Implement activateAddButton to enable the "Add" button */
    override fun activateAddButton() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
        customPositiveButton?.isEnabled = true
    }


    /* Implement deactivateAddButton to disable the "Add" button */
    override fun deactivateAddButton() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        customPositiveButton?.isEnabled = false
    }


}