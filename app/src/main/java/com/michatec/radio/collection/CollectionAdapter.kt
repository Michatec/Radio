package com.michatec.radio.collection

import android.content.Context
import android.content.SharedPreferences
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.michatec.radio.Keys
import com.michatec.radio.R
import com.michatec.radio.core.Collection
import com.michatec.radio.core.Station
import com.michatec.radio.helpers.*
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import java.util.*


/*
 * CollectionAdapter class
 */
class CollectionAdapter(
    private val context: Context,
    private val collectionAdapterListener: CollectionAdapterListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), UpdateHelper.UpdateHelperListener {

    /* Main class variables */
    private lateinit var collectionViewModel: CollectionViewModel
    private var collection: Collection = Collection()
    private var filteredStations: MutableList<Station> = mutableListOf()
    private var editStationsEnabled: Boolean = PreferencesHelper.loadEditStationsEnabled(context)
    private var editStationStreamsEnabled: Boolean = PreferencesHelper.loadEditStreamUrisEnabled(context)
    private var expandedStationUuid: String = PreferencesHelper.loadStationListStreamUuid()
    private var expandedStationPosition: Int = -1
    var isExpandedForEdit: Boolean = false
    private var reorderStationUuid: String = ""
    private var currentSearchQuery: String = ""
    private var currentOnlyFavorites: Boolean = false


    /* Listener Interface */
    interface CollectionAdapterListener {
        fun onPlayButtonTapped(stationUuid: String)
        fun onAddNewButtonTapped()
        fun onChangeImageButtonTapped(stationUuid: String)
        fun onSearchButtonTapped()
    }


    /* Overrides onAttachedToRecyclerView */
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        // create view model and observe changes in collection view model
        collectionViewModel =
            ViewModelProvider(context as AppCompatActivity)[CollectionViewModel::class.java]
        observeCollectionViewModel(context as LifecycleOwner)
        // start listening for changes in shared preferences
        PreferencesHelper.registerPreferenceChangeListener(sharedPreferenceChangeListener)
    }


    /* Overrides onDetachedFromRecyclerView */
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        // stop listening for changes in shared preferences
        PreferencesHelper.unregisterPreferenceChangeListener(sharedPreferenceChangeListener)
    }


    /* Overrides onCreateViewHolder */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        return when (viewType) {
            Keys.VIEW_TYPE_ADD_NEW -> {
                // get view, put view into holder and return
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.card_add_new_station, parent, false)
                AddNewViewHolder(v)
            }
            else -> {
                // get view, put view into holder and return
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.card_station, parent, false)
                StationViewHolder(v)
            }
        }
    }


    /* Implement the method to handle item move */
    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (isExpandedForEdit || filteredStations.size != collection.stations.size) {
            return
        }

        val stationCount = filteredStations.size

        if (fromPosition !in 0 until stationCount || toPosition !in 0 until stationCount) {
            return
        }

        val fromStation = filteredStations[fromPosition]
        val toStation = filteredStations[toPosition]

        if (fromStation.starred != toStation.starred) {
            // Prevent moving a starred item into non-starred area or vice versa
            return
        }

        // Move within both lists to keep them in sync
        Collections.swap(collection.stations, fromPosition, toPosition)
        Collections.swap(filteredStations, fromPosition, toPosition)

        // Update the value of expandedStationPosition if necessary
        if (expandedStationUuid == fromStation.uuid) {
            expandedStationPosition = toPosition
        } else if (expandedStationUuid == toStation.uuid) {
            expandedStationPosition = fromPosition
        }

        // Notify the adapter about the item move
        notifyItemMoved(fromPosition, toPosition)
    }


    /* Implement the method to handle item dismissal */
    fun onItemDismiss(position: Int) {
        val station = filteredStations[position]
        val originalPosition = collection.stations.indexOfFirst { it.uuid == station.uuid }
        if (originalPosition != -1) {
            collection.stations.removeAt(originalPosition)
        }
        filteredStations.removeAt(position)
        notifyItemRemoved(position)
    }


    /* Method for saving the collection after the drag-and-drop operation */
    fun saveCollectionAfterDragDrop() {
        // Save the collection after the dragging is completed
        CollectionHelper.saveCollection(context, collection)
    }


    /* Filters the station list based on query and favorite status */
    fun filter(query: String, onlyFavorites: Boolean) {
        currentSearchQuery = query
        currentOnlyFavorites = onlyFavorites
        applyFilter()
    }

    private fun applyFilter() {
        val oldStations = filteredStations.toList()
        filteredStations = if (currentSearchQuery.isEmpty() && !currentOnlyFavorites) {
            collection.stations.toMutableList()
        } else {
            collection.stations.filter { station ->
                val matchesQuery = station.name.contains(currentSearchQuery, ignoreCase = true)
                val matchesFavorite = !currentOnlyFavorites || station.starred
                matchesQuery && matchesFavorite
            }.toMutableList()
        }
        val diffResult = DiffUtil.calculateDiff(CollectionDiffCallback(oldStations, filteredStations))
        diffResult.dispatchUpdatesTo(this)
    }

    /* Overrides onBindViewHolder */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            // CASE ADD NEW CARD
            is AddNewViewHolder -> {
                // get reference to StationViewHolder
                val addNewViewHolder: AddNewViewHolder = holder
                addNewViewHolder.addNewStationView.setOnClickListener {
                    // show the add station dialog
                    collectionAdapterListener.onAddNewButtonTapped()
                }
                addNewViewHolder.settingsButtonView.setOnClickListener {
                    it.findNavController().navigate(R.id.settings_destination)
                }
                addNewViewHolder.visualizerButtonView.setOnClickListener {
                    it.findNavController().navigate(R.id.visualizer_destination)
                }
                addNewViewHolder.playerSearchButtonView.setOnClickListener {
                    collectionAdapterListener.onSearchButtonTapped()
                }
                addNewViewHolder.playerSearchButtonView.isVisible = collection.stations.isNotEmpty()
            }
            // CASE STATION CARD
            is StationViewHolder -> {
                // get station from position
                val station: Station = filteredStations[position]

                // get reference to StationViewHolder
                val stationViewHolder: StationViewHolder = holder
                // set up station views
                setStarredIcon(stationViewHolder, station)
                setStationName(stationViewHolder, station)
                setStationImage(stationViewHolder, station)
                setStationButtons(stationViewHolder, station)
                setEditViews(stationViewHolder, station)
                setPlaybackProgress(stationViewHolder, station)
                setDownloadProgress(stationViewHolder, station)

                if (reorderStationUuid == station.uuid) {
                    stationViewHolder.reorderCheckbox.isVisible = true
                    stationViewHolder.reorderCheckbox.isChecked = true
                } else {
                    stationViewHolder.reorderCheckbox.isGone = true
                    stationViewHolder.reorderCheckbox.isChecked = false
                }

                updateVisibility(stationViewHolder, station)
            }
        }
    }


    /* Overrides onStationUpdated from UpdateHelperListener */
    override fun onStationUpdated(
        collection: Collection,
        positionPriorUpdate: Int,
        positionAfterUpdate: Int
    ) {
        this.collection = collection
        applyFilter()
    }


    /* Sets the station name view */
    private fun setStationName(stationViewHolder: StationViewHolder, station: Station) {
        stationViewHolder.stationNameView.text = station.name
    }


    /* Sets the playback progress view */
    private fun setPlaybackProgress(stationViewHolder: StationViewHolder, station: Station) {
        if (station.bufferingProgress > 0) {
            stationViewHolder.bufferingProgress.progress = station.bufferingProgress
            stationViewHolder.bufferingProgress.isVisible = true
        } else {
            stationViewHolder.bufferingProgress.isGone = true
        }
    }


    /* Sets the download progress view */
    private fun setDownloadProgress(stationViewHolder: StationViewHolder, station: Station) {
        if (station.downloadProgress > 0) {
            stationViewHolder.downloadProgress.progress = station.downloadProgress
            stationViewHolder.downloadProgress.isVisible = true
        } else {
            stationViewHolder.downloadProgress.isGone = true
        }
    }


    /* Sets the edit views */
    private fun setEditViews(stationViewHolder: StationViewHolder, station: Station) {
        stationViewHolder.stationNameEditView.setText(station.name, TextView.BufferType.EDITABLE)
        stationViewHolder.stationUriEditView.setText(
            station.getStreamUri(),
            TextView.BufferType.EDITABLE
        )
        
        // Remove existing TextWatcher to prevent leaks and redundant updates
        stationViewHolder.textWatcher?.let {
            stationViewHolder.stationUriEditView.removeTextChangedListener(it)
        }
        
        val newTextWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                handleStationUriInput(stationViewHolder, s, station.getStreamUri())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        stationViewHolder.textWatcher = newTextWatcher
        stationViewHolder.stationUriEditView.addTextChangedListener(newTextWatcher)

        stationViewHolder.cancelButton.setOnClickListener {
            val position: Int = stationViewHolder.bindingAdapterPosition
            toggleEditViews(position, station.uuid)
            UiHelper.hideSoftKeyboard(context, stationViewHolder.stationNameEditView)
        }
        stationViewHolder.saveButton.setOnClickListener {
            val position: Int = stationViewHolder.bindingAdapterPosition
            toggleEditViews(position, station.uuid)
            saveStation(
                station,
                stationViewHolder.stationNameEditView.text.toString(),
                stationViewHolder.stationUriEditView.text.toString()
            )
            UiHelper.hideSoftKeyboard(context, stationViewHolder.stationNameEditView)
        }
        stationViewHolder.placeOnHomeScreenButton.setOnClickListener {
            val position: Int = stationViewHolder.bindingAdapterPosition
            ShortcutHelper.placeShortcut(context, station)
            toggleEditViews(position, station.uuid)
            UiHelper.hideSoftKeyboard(context, stationViewHolder.stationNameEditView)
        }
        stationViewHolder.stationImageChangeView.setOnClickListener {
            val position: Int = stationViewHolder.bindingAdapterPosition
            collectionAdapterListener.onChangeImageButtonTapped(station.uuid)
            stationViewHolder.bindingAdapterPosition
            toggleEditViews(position, station.uuid)
            UiHelper.hideSoftKeyboard(context, stationViewHolder.stationNameEditView)
        }
    }

    /* Shows / hides the reorder highlight for a station */
    private fun toggleReorderMode(position: Int, stationUuid: String) {
        if (reorderStationUuid == stationUuid) {
            reorderStationUuid = ""
            saveCollectionAfterDragDrop()
        } else {
            // collapse edit views if necessary
            if (isExpandedForEdit) {
                toggleEditViews(expandedStationPosition, expandedStationUuid)
            }
            reorderStationUuid = stationUuid
        }
        notifyItemChanged(position)
    }


    /* Shows / hides the edit view for a station */
    private fun toggleEditViews(position: Int, stationUuid: String) {
        if (expandedStationUuid == stationUuid) {
            // CASE: this station's edit view is already expanded -> collapse it
            isExpandedForEdit = false
            saveStationListExpandedState()
            notifyItemChanged(position, Keys.HOLDER_UPDATE_EXPANSION)
        } else {
            // CASE: this station's edit view is not yet expanded -> expand it
            isExpandedForEdit = true
            
            // Collapse previously expanded station if it exists and is visible
            val previousUuid = expandedStationUuid
            if (previousUuid.isNotEmpty()) {
                val previousPosition = filteredStations.indexOfFirst { it.uuid == previousUuid }
                if (previousPosition != -1) {
                    notifyItemChanged(previousPosition, Keys.HOLDER_UPDATE_EXPANSION)
                }
            }
            
            // Store current station as the expanded one
            saveStationListExpandedState(position, stationUuid)
            // Update the newly expanded station
            notifyItemChanged(position, Keys.HOLDER_UPDATE_EXPANSION)
        }
    }


    /* Updates the visibility of a station's views based on its expanded state */
    private fun updateVisibility(stationViewHolder: StationViewHolder, station: Station) {
        val isExpanded = expandedStationUuid == station.uuid
        if (isExpanded) {
            stationViewHolder.stationNameView.isVisible = false
            stationViewHolder.playButtonView.isGone = true
            stationViewHolder.stationStarredView.isGone = true
            stationViewHolder.editViews.isVisible = true
            if (editStationStreamsEnabled) {
                stationViewHolder.stationUriEditView.isVisible = true
                stationViewHolder.stationUriEditView.imeOptions = EditorInfo.IME_ACTION_DONE
            } else {
                stationViewHolder.stationUriEditView.isGone = true
                stationViewHolder.stationNameEditView.imeOptions = EditorInfo.IME_ACTION_DONE
            }
            // Allow internal focus
            stationViewHolder.stationCardView.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        } else {
            stationViewHolder.stationNameView.isVisible = true
            stationViewHolder.stationStarredView.isVisible = station.starred
            stationViewHolder.editViews.isGone = true
            stationViewHolder.stationUriEditView.isGone = true
            // Block internal focus
            stationViewHolder.stationCardView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
    }


    /* Toggles the starred icon */
    private fun setStarredIcon(stationViewHolder: StationViewHolder, station: Station) {
        val isExpanded = expandedStationUuid == station.uuid
        when (station.starred) {
            true -> {
                if (station.imageColor != -1) {
                    stationViewHolder.stationStarredView.setColorFilter(station.imageColor)
                } else {
                    stationViewHolder.stationStarredView.clearColorFilter()
                }
                stationViewHolder.stationStarredView.isVisible = !isExpanded
            }
            false -> {
                stationViewHolder.stationStarredView.clearColorFilter()
                stationViewHolder.stationStarredView.isGone = true
            }
        }
    }


    /* Sets the station image view */
    private fun setStationImage(stationViewHolder: StationViewHolder, station: Station) {
        if (station.imageColor != -1) {
            stationViewHolder.stationImageView.setBackgroundColor(station.imageColor)
        }
        stationViewHolder.stationImageView.setImageBitmap(
            ImageHelper.getStationImage(
                context,
                station.smallImage
            )
        )
        stationViewHolder.stationImageView.contentDescription =
            "${context.getString(R.string.descr_player_station_image)}: ${station.name}"
    }


    /* Sets up a station's play and edit buttons */
    private fun setStationButtons(stationViewHolder: StationViewHolder, station: Station) {
        when (station.isPlaying) {
            true -> stationViewHolder.playButtonView.visibility = View.VISIBLE
            false -> stationViewHolder.playButtonView.visibility = View.INVISIBLE
        }
        stationViewHolder.stationCardView.setOnClickListener {
            if (reorderStationUuid.isNotEmpty()) return@setOnClickListener
            if (expandedStationPosition == stationViewHolder.bindingAdapterPosition) return@setOnClickListener
            collectionAdapterListener.onPlayButtonTapped(station.uuid)
        }
        stationViewHolder.playButtonView.setOnClickListener {
            collectionAdapterListener.onPlayButtonTapped(station.uuid)
        }
        stationViewHolder.stationNameView.setOnClickListener {
            collectionAdapterListener.onPlayButtonTapped(station.uuid)
        }
        stationViewHolder.stationStarredView.setOnClickListener {
            collectionAdapterListener.onPlayButtonTapped(station.uuid)
        }
        stationViewHolder.stationImageView.setOnClickListener {
            collectionAdapterListener.onPlayButtonTapped(station.uuid)
        }

        // TV improvement: Allow reordering with DPAD
        stationViewHolder.stationCardView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                // Reorder mode handling
                if (reorderStationUuid == station.uuid) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            val currentPos = stationViewHolder.bindingAdapterPosition
                            if (currentPos > 0) {
                                onItemMove(currentPos, currentPos - 1)
                            }
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            val currentPos = stationViewHolder.bindingAdapterPosition
                            if (currentPos < collection.stations.size - 1) {
                                onItemMove(currentPos, currentPos + 1)
                            }
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_NUMPAD_2, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_PLUS,
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                            toggleReorderMode(stationViewHolder.bindingAdapterPosition, station.uuid)
                            return@setOnKeyListener true
                        }
                        else -> return@setOnKeyListener true // Consume other keys in reorder mode
                    }
                }

                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (editStationsEnabled && expandedStationPosition != stationViewHolder.bindingAdapterPosition) {
                            val position: Int = stationViewHolder.bindingAdapterPosition
                            toggleEditViews(position, station.uuid)
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_NUMPAD_0, KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_DEL -> {
                        removeStation(context, stationViewHolder.bindingAdapterPosition)
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_NUMPAD_1, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_SPACE -> {
                        toggleStarredStation(context, stationViewHolder.bindingAdapterPosition)
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_NUMPAD_2, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_PLUS -> {
                        toggleReorderMode(stationViewHolder.bindingAdapterPosition, station.uuid)
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }

        stationViewHolder.playButtonView.setOnLongClickListener {
            if (editStationsEnabled) {
                val position: Int = stationViewHolder.bindingAdapterPosition
                toggleEditViews(position, station.uuid)
                return@setOnLongClickListener true
            } else {
                return@setOnLongClickListener false
            }
        }
        stationViewHolder.stationNameView.setOnLongClickListener {
            if (editStationsEnabled) {
                val position: Int = stationViewHolder.bindingAdapterPosition
                toggleEditViews(position, station.uuid)
                return@setOnLongClickListener true
            } else {
                return@setOnLongClickListener false
            }
        }
        stationViewHolder.stationStarredView.setOnLongClickListener {
            if (editStationsEnabled) {
                val position: Int = stationViewHolder.bindingAdapterPosition
                toggleEditViews(position, station.uuid)
                return@setOnLongClickListener true
            } else {
                return@setOnLongClickListener false
            }
        }
        stationViewHolder.stationImageView.setOnLongClickListener {
            if (editStationsEnabled) {
                val position: Int = stationViewHolder.bindingAdapterPosition
                toggleEditViews(position, station.uuid)
                return@setOnLongClickListener true
            } else {
                return@setOnLongClickListener false
            }
        }
    }


    /* Checks if stream uri input is valid */
    private fun handleStationUriInput(
        stationViewHolder: StationViewHolder,
        s: Editable?,
        streamUri: String
    ) {
        if (editStationStreamsEnabled) {
            val input: String = s.toString()
            if (input == streamUri) {
                // enable save button
                stationViewHolder.saveButton.isEnabled = true
                stationViewHolder.validationJob?.cancel()
            } else {
                // 1. disable save button
                stationViewHolder.saveButton.isEnabled = false
                // 2. check for valid station uri - and re-enable button
                if (input.length > 10 && input.startsWith("http")) {
                    // cancel previous validation job
                    stationViewHolder.validationJob?.cancel()
                    // detect content type on background thread
                    stationViewHolder.validationJob = CoroutineScope(IO).launch {
                        val deferred: Deferred<NetworkHelper.ContentType> =
                            async(Dispatchers.Default) {
                                NetworkHelper.detectContentTypeSuspended(input)
                            }
                        // wait for result
                        val contentType: String =
                            deferred.await().type.lowercase(Locale.getDefault())
                        // CASE: stream address detected
                        if (Keys.MIME_TYPES_MPEG.contains(contentType) or
                            Keys.MIME_TYPES_OGG.contains(contentType) or
                            Keys.MIME_TYPES_AAC.contains(contentType) or
                            Keys.MIME_TYPES_HLS.contains(contentType)
                        ) {
                            // re-enable save button
                            withContext(Main) {
                                stationViewHolder.saveButton.isEnabled = true
                            }
                        }
                    }
                }
            }
        }
    }


    /* Overrides onBindViewHolder */
    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<Any>
    ) {

        if (payloads.isEmpty()) {
            // call regular onBindViewHolder method
            onBindViewHolder(holder, position)

        } else if (holder is StationViewHolder) {
            // get station from position
            val station: Station = filteredStations[holder.bindingAdapterPosition]

            for (data in payloads) {
                when (data as Int) {
                    Keys.HOLDER_UPDATE_COVER -> {
                        setStationImage(holder, station)
                        setStarredIcon(holder, station)
                    }
                    Keys.HOLDER_UPDATE_NAME -> {
                        setStationName(holder, station)
                    }
                    Keys.HOLDER_UPDATE_PLAYBACK_STATE -> {
                        setStationButtons(holder, station)
                    }
                    Keys.HOLDER_UPDATE_PLAYBACK_PROGRESS -> {
                        setPlaybackProgress(holder, station)
                    }
                    Keys.HOLDER_UPDATE_DOWNLOAD_STATE -> {
                        setDownloadProgress(holder, station)
                    }
                    Keys.HOLDER_UPDATE_EXPANSION -> {
                        setEditViews(holder, station)
                        updateVisibility(holder, station)
                    }
                }
            }
        }
    }


    /* Overrides getItemViewType */
    override fun getItemViewType(position: Int): Int {
        return when (isPositionFooter(position)) {
            true -> Keys.VIEW_TYPE_ADD_NEW
            false -> Keys.VIEW_TYPE_STATION
        }
    }


    /* Overrides getItemCount */
    override fun getItemCount(): Int {
        // +1 ==> the add station card
        return filteredStations.size + 1
    }


    /* Removes a station from collection */
    fun removeStation(context: Context, position: Int) {
        val station = filteredStations[position]
        val originalPosition = collection.stations.indexOfFirst { it.uuid == station.uuid }
        if (originalPosition == -1) return

        val newCollection = collection.deepCopy()
        // delete images assets
        CollectionHelper.deleteStationImages(context, newCollection.stations[originalPosition])
        // remove station from collection
        newCollection.stations.removeAt(originalPosition)
        collection = newCollection
        // update list
        filteredStations.removeAt(position)
        notifyItemRemoved(position)
        // save collection and broadcast changes
        CollectionHelper.saveCollection(context, newCollection)
    }


    /* Toggles starred status of a station */
    fun toggleStarredStation(context: Context, position: Int) {
        val station = filteredStations[position]
        val originalIndex = collection.stations.indexOfFirst { it.uuid == station.uuid }
        if (originalIndex == -1) return

        // Create a copy of the station with toggled starred status
        val updatedStation = collection.stations[originalIndex].deepCopy().apply {
            starred = !starred
        }

        // Update master list with the new instance
        collection.stations[originalIndex] = updatedStation

        // Sort collection
        collection = CollectionHelper.sortCollection(collection)

        // Trigger immediate UI update via DiffUtil
        applyFilter()

        // Save collection and broadcast changes
        CollectionHelper.saveCollection(context, collection)
    }


    /* Saves edited station */
    private fun saveStation(
        station: Station,
        stationName: String,
        streamUri: String
    ) {
        // update station name and stream uri in master list
        val originalIndex = collection.stations.indexOfFirst { it.uuid == station.uuid }
        if (originalIndex != -1) {
            val updatedStation = collection.stations[originalIndex].deepCopy().apply {
                if (stationName.isNotEmpty()) {
                    name = stationName
                    nameManuallySet = true
                }
                if (streamUri.isNotEmpty()) {
                    streamUris[0] = streamUri
                }
            }
            collection.stations[originalIndex] = updatedStation
        }

        // sort and save collection
        collection = CollectionHelper.sortCollection(collection)

        // Trigger immediate UI update
        applyFilter()

        // save collection and broadcast changes
        CollectionHelper.saveCollection(context, collection)
    }


    /* Determines if position is last */
    private fun isPositionFooter(position: Int): Boolean {
        return position == filteredStations.size
    }


    /* Updates the station list - redraws the views with changed content */
    private fun updateRecyclerView(newCollection: Collection) {
        collection = newCollection
        applyFilter()
    }


    /* Updates and saves state of expanded station edit view in list */
    private fun saveStationListExpandedState(
        position: Int = -1,
        stationStreamUri: String = String()
    ) {
        expandedStationUuid = stationStreamUri
        expandedStationPosition = position
        PreferencesHelper.saveStationListStreamUuid(expandedStationUuid)
    }


    /* Observe view model of station collection*/
    private fun observeCollectionViewModel(owner: LifecycleOwner) {
        collectionViewModel.collectionLiveData.observe(owner) { newCollection ->
            updateRecyclerView(newCollection)
        }
    }


    /*
     * Defines the listener for changes in shared preferences
     */
    private val sharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                Keys.PREF_EDIT_STATIONS -> editStationsEnabled =
                    PreferencesHelper.loadEditStationsEnabled(context)
                Keys.PREF_EDIT_STREAMS_URIS -> editStationStreamsEnabled =
                    PreferencesHelper.loadEditStreamUrisEnabled(context)
            }
        }
    /*
     * End of declaration
     */


    /*
     * Inner class: ViewHolder for the Add New Station action
     */
    private class AddNewViewHolder(listItemAddNewLayout: View) :
        RecyclerView.ViewHolder(listItemAddNewLayout) {
        val addNewStationView: ExtendedFloatingActionButton =
            listItemAddNewLayout.findViewById(R.id.card_add_new_station)
        val settingsButtonView: ExtendedFloatingActionButton =
            listItemAddNewLayout.findViewById(R.id.card_settings)
        val visualizerButtonView: ExtendedFloatingActionButton =
            listItemAddNewLayout.findViewById(R.id.card_visualizer)
        val playerSearchButtonView: ExtendedFloatingActionButton =
            listItemAddNewLayout.findViewById(R.id.player_search_button)
    }
    /*
     * End of inner class
     */


    /*
     * Inner class: ViewHolder for a station
     */
    private class StationViewHolder(stationCardLayout: View) :
        RecyclerView.ViewHolder(stationCardLayout) {
        val stationCardView: MaterialCardView = stationCardLayout.findViewById(R.id.station_card)
        val stationImageView: ImageView = stationCardLayout.findViewById(R.id.station_icon)
        val stationNameView: TextView = stationCardLayout.findViewById(R.id.station_name)
        val stationStarredView: ImageView = stationCardLayout.findViewById(R.id.starred_icon)
        val reorderCheckbox: CheckBox = stationCardLayout.findViewById(R.id.reorder_checkbox)
        val bufferingProgress: ProgressBar = stationCardLayout.findViewById(R.id.buffering_progress)
        val downloadProgress: ProgressBar = stationCardLayout.findViewById(R.id.download_progress)

        val playButtonView: ImageView = stationCardLayout.findViewById(R.id.playback_button)
        val editViews: Group = stationCardLayout.findViewById(R.id.default_edit_views)
        val stationImageChangeView: ImageView =
            stationCardLayout.findViewById(R.id.change_image_view)
        val stationNameEditView: TextInputEditText =
            stationCardLayout.findViewById(R.id.edit_station_name)
        val stationUriEditView: TextInputEditText =
            stationCardLayout.findViewById(R.id.edit_stream_uri)
        val placeOnHomeScreenButton: MaterialButton =
            stationCardLayout.findViewById(R.id.place_on_home_screen_button)
        val cancelButton: MaterialButton = stationCardLayout.findViewById(R.id.cancel_button)
        val saveButton: MaterialButton = stationCardLayout.findViewById(R.id.save_button)
        
        var textWatcher: TextWatcher? = null
        var validationJob: Job? = null
    }
    /*
     * End of inner class
     */


    /*
     * Inner class: DiffUtil.Callback that determines changes in data - improves list performance
     */
    private inner class CollectionDiffCallback(
        private val oldStations: List<Station>,
        private val newStations: List<Station>
    ) : DiffUtil.Callback() {

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val isOldFooter = oldItemPosition == oldStations.size
            val isNewFooter = newItemPosition == newStations.size

            if (isOldFooter && isNewFooter) return true
            if (isOldFooter || isNewFooter) return false

            val oldStation: Station = oldStations[oldItemPosition]
            val newStation: Station = newStations[newItemPosition]
            return oldStation.uuid == newStation.uuid
        }

        override fun getOldListSize(): Int {
            return oldStations.size + 1
        }

        override fun getNewListSize(): Int {
            return newStations.size + 1
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val isOldFooter = oldItemPosition == oldStations.size
            val isNewFooter = newItemPosition == newStations.size

            if (isOldFooter && isNewFooter) return true
            if (isOldFooter || isNewFooter) return false

            val oldStation: Station = oldStations[oldItemPosition]
            val newStation: Station = newStations[newItemPosition]

            // Compare expanded state using UUID
            val wasExpanded = oldStation.uuid == expandedStationUuid
            val isExpanded = newStation.uuid == expandedStationUuid
            if (wasExpanded != isExpanded) return false

            // compare relevant contents of a station
            if (oldStation.isPlaying != newStation.isPlaying) return false
            if (oldStation.uuid != newStation.uuid) return false
            if (oldStation.starred != newStation.starred) return false
            if (oldStation.name != newStation.name) return false
            if (oldStation.stream != newStation.stream) return false
            if (oldStation.remoteImageLocation != newStation.remoteImageLocation) return false
            if (oldStation.remoteStationLocation != newStation.remoteStationLocation) return false
            if (!oldStation.streamUris.containsAll(newStation.streamUris)) return false
            if (oldStation.imageColor != newStation.imageColor) return false
            if (FileHelper.getFileSize(context, oldStation.image.toUri()) != FileHelper.getFileSize(
                    context,
                    newStation.image.toUri()
                )
            ) return false
            if (FileHelper.getFileSize(
                    context,
                    oldStation.smallImage.toUri()
                ) != FileHelper.getFileSize(context, newStation.smallImage.toUri())
            ) return false

            // none of the above -> contents are the same
            return true
        }
    }
    /*
     * End of inner class
     */
}
