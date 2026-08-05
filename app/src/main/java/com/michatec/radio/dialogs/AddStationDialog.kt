package com.michatec.radio.dialogs

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michatec.radio.R
import com.michatec.radio.core.Station
import com.michatec.radio.databinding.DialogAddStationBinding
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
    private lateinit var binding: DialogAddStationBinding
    private lateinit var dialog: AlertDialog
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

        // get binding
        binding = DialogAddStationBinding.inflate(LayoutInflater.from(context))

        // set up list of search results
        setupRecyclerView(context)

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
        binding.dialogPositiveButton?.setOnClickListener {
            listener.onAddStationDialog(station)
            searchResultAdapter.stopPrePlayback()
            dialog.dismiss()
        }
        binding.dialogNegativeButton?.setOnClickListener {
            searchResultAdapter.stopPrePlayback()
            dialog.dismiss()
        }

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
        }
    }


    /* Sets up list of results (RecyclerView) */
    private fun setupRecyclerView(context: Context) {
        searchResultAdapter = SearchResultAdapter(this, stationList)
        binding.stationList.adapter = searchResultAdapter
        val layoutManager: LinearLayoutManager = object: LinearLayoutManager(context) {
            override fun supportsPredictiveItemAnimations(): Boolean {
                return true
            }
        }
        binding.stationList.layoutManager = layoutManager
        binding.stationList.itemAnimator = DefaultItemAnimator()
    }


    /* Implement activateAddButton to enable the "Add" button */
    override fun activateAddButton() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
        binding.dialogPositiveButton?.isEnabled = true
    }


    /* Implement deactivateAddButton to disable the "Add" button */
    override fun deactivateAddButton() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        binding.dialogPositiveButton?.isEnabled = false
    }


}