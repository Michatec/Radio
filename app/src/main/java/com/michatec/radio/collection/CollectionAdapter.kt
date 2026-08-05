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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.michatec.radio.Keys
import com.michatec.radio.R
import com.michatec.radio.core.Collection
import com.michatec.radio.core.Station
import com.michatec.radio.databinding.CardAddNewStationBinding
import com.michatec.radio.databinding.CardStationBinding
import com.michatec.radio.helpers.CollectionHelper
import com.michatec.radio.helpers.FileHelper
import com.michatec.radio.helpers.ImageHelper
import com.michatec.radio.helpers.NetworkHelper
import com.michatec.radio.helpers.PreferencesHelper
import com.michatec.radio.helpers.ShortcutHelper
import com.michatec.radio.helpers.UiHelper
import com.michatec.radio.helpers.UpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.Locale


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
                AddNewViewHolder(
                    CardAddNewStationBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
            }
            else -> {
                StationViewHolder(
                    CardStationBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
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
                val binding = holder.binding
                binding.cardAddNewStation.setOnClickListener {
                    // show the add station dialog
                    collectionAdapterListener.onAddNewButtonTapped()
                }
                binding.cardSettings.setOnClickListener {
                    it.findNavController().navigate(R.id.settings_destination)
                }
                binding.cardVisualizer.setOnClickListener {
                    it.findNavController().navigate(R.id.visualizer_destination)
                }
                binding.playerSearchButton.setOnClickListener {
                    collectionAdapterListener.onSearchButtonTapped()
                }
                binding.playerSearchButton.isVisible = collection.stations.isNotEmpty()
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
                    stationViewHolder.binding.reorderCheckbox.isVisible = true
                    stationViewHolder.binding.reorderCheckbox.isChecked = true
                } else {
                    stationViewHolder.binding.reorderCheckbox.isGone = true
                    stationViewHolder.binding.reorderCheckbox.isChecked = false
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
        stationViewHolder.binding.stationName.text = station.name
    }


    /* Sets the playback progress view */
    private fun setPlaybackProgress(stationViewHolder: StationViewHolder, station: Station) {
        if (station.bufferingProgress > 0) {
            stationViewHolder.binding.bufferingProgress.progress = station.bufferingProgress
            stationViewHolder.binding.bufferingProgress.isVisible = true
        } else {
            stationViewHolder.binding.bufferingProgress.isGone = true
        }
    }


    /* Sets the download progress view */
    private fun setDownloadProgress(stationViewHolder: StationViewHolder, station: Station) {
        if (station.downloadProgress > 0) {
            stationViewHolder.binding.downloadProgress.progress = station.downloadProgress
            stationViewHolder.binding.downloadProgress.isVisible = true
        } else {
            stationViewHolder.binding.downloadProgress.isGone = true
        }
    }


    /* Sets the edit views */
    private fun setEditViews(stationViewHolder: StationViewHolder, station: Station) {
        val binding = stationViewHolder.binding
        binding.editStationName.setText(station.name, TextView.BufferType.EDITABLE)
        binding.editStreamUri.setText(
            station.getStreamUri(),
            TextView.BufferType.EDITABLE
        )
        
        // Remove existing TextWatcher to prevent leaks and redundant updates
        stationViewHolder.textWatcher?.let {
            binding.editStreamUri.removeTextChangedListener(it)
        }
        
        val newTextWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                handleStationUriInput(stationViewHolder, s, station.getStreamUri())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        stationViewHolder.textWatcher = newTextWatcher
        binding.editStreamUri.addTextChangedListener(newTextWatcher)

        binding.cancelButton.setOnClickListener {
            val position: Int = stationViewHolder.bindingAdapterPosition
            toggleEditViews(position, station.uuid)
            UiHelper.hideSoftKeyboard(context, binding.editStationName)
        }
        binding.saveButton.setOnClickListener {
            val position: Int = stationViewHolder.bindingAdapterPosition
            toggleEditViews(position, station.uuid)
            saveStation(
                station,
                binding.editStationName.text.toString(),
                binding.editStreamUri.text.toString()
            )
            UiHelper.hideSoftKeyboard(context, binding.editStationName)
        }
        binding.placeOnHomeScreenButton.setOnClickListener {
            val position: Int = stationViewHolder.bindingAdapterPosition
            ShortcutHelper.placeShortcut(context, station)
            toggleEditViews(position, station.uuid)
            UiHelper.hideSoftKeyboard(context, binding.editStationName)
        }
        binding.changeImageView.setOnClickListener {
            val position: Int = stationViewHolder.bindingAdapterPosition
            collectionAdapterListener.onChangeImageButtonTapped(station.uuid)
            stationViewHolder.bindingAdapterPosition
            toggleEditViews(position, station.uuid)
            UiHelper.hideSoftKeyboard(context, binding.editStationName)
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
        val binding = stationViewHolder.binding
        val isExpanded = expandedStationUuid == station.uuid
        if (isExpanded) {
            binding.stationName.isVisible = false
            binding.playbackButton.isGone = true
            binding.starredIcon.isGone = true
            binding.defaultEditViews.isVisible = true
            if (editStationStreamsEnabled) {
                binding.editStreamUri.isVisible = true
                binding.editStreamUri.imeOptions = EditorInfo.IME_ACTION_DONE
            } else {
                binding.editStreamUri.isGone = true
                binding.editStationName.imeOptions = EditorInfo.IME_ACTION_DONE
            }
            // Allow internal focus
            binding.stationCard.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        } else {
            binding.stationName.isVisible = true
            binding.starredIcon.isVisible = station.starred
            binding.defaultEditViews.isGone = true
            binding.editStreamUri.isGone = true
            // Block internal focus
            binding.stationCard.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
    }


    /* Toggles the starred icon */
    private fun setStarredIcon(stationViewHolder: StationViewHolder, station: Station) {
        val binding = stationViewHolder.binding
        val isExpanded = expandedStationUuid == station.uuid
        when (station.starred) {
            true -> {
                if (station.imageColor != -1) {
                    binding.starredIcon.setColorFilter(station.imageColor)
                } else {
                    binding.starredIcon.clearColorFilter()
                }
                binding.starredIcon.isVisible = !isExpanded
            }
            false -> {
                binding.starredIcon.clearColorFilter()
                binding.starredIcon.isGone = true
            }
        }
    }


    /* Sets the station image view */
    private fun setStationImage(stationViewHolder: StationViewHolder, station: Station) {
        val binding = stationViewHolder.binding
        if (station.imageColor != -1) {
            binding.stationIcon.setBackgroundColor(station.imageColor)
        }
        binding.stationIcon.setImageBitmap(
            ImageHelper.getStationImage(
                context,
                station.smallImage
            )
        )
        binding.stationIcon.contentDescription =
            "${context.getString(R.string.descr_player_station_image)}: ${station.name}"
    }


    /* Sets up a station's play and edit buttons */
    private fun setStationButtons(stationViewHolder: StationViewHolder, station: Station) {
        val binding = stationViewHolder.binding
        when (station.isPlaying) {
            true -> binding.playbackButton.visibility = View.VISIBLE
            false -> binding.playbackButton.visibility = View.INVISIBLE
        }
        binding.stationCard.setOnClickListener {
            if (reorderStationUuid.isNotEmpty()) return@setOnClickListener
            if (expandedStationPosition == stationViewHolder.bindingAdapterPosition) return@setOnClickListener
            collectionAdapterListener.onPlayButtonTapped(station.uuid)
        }
        binding.playbackButton.setOnClickListener {
            collectionAdapterListener.onPlayButtonTapped(station.uuid)
        }
        binding.stationName.setOnClickListener {
            collectionAdapterListener.onPlayButtonTapped(station.uuid)
        }
        binding.starredIcon.setOnClickListener {
            collectionAdapterListener.onPlayButtonTapped(station.uuid)
        }
        binding.stationIcon.setOnClickListener {
            collectionAdapterListener.onPlayButtonTapped(station.uuid)
        }

        // TV improvement: Allow reordering with DPAD
        binding.stationCard.setOnKeyListener { _, keyCode, event ->
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

        binding.playbackButton.setOnLongClickListener {
            if (editStationsEnabled) {
                val position: Int = stationViewHolder.bindingAdapterPosition
                toggleEditViews(position, station.uuid)
                return@setOnLongClickListener true
            } else {
                return@setOnLongClickListener false
            }
        }
        binding.stationName.setOnLongClickListener {
            if (editStationsEnabled) {
                val position: Int = stationViewHolder.bindingAdapterPosition
                toggleEditViews(position, station.uuid)
                return@setOnLongClickListener true
            } else {
                return@setOnLongClickListener false
            }
        }
        binding.starredIcon.setOnLongClickListener {
            if (editStationsEnabled) {
                val position: Int = stationViewHolder.bindingAdapterPosition
                toggleEditViews(position, station.uuid)
                return@setOnLongClickListener true
            } else {
                return@setOnLongClickListener false
            }
        }
        binding.stationIcon.setOnLongClickListener {
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
        val binding = stationViewHolder.binding
        if (editStationStreamsEnabled) {
            val input: String = s.toString()
            if (input == streamUri) {
                // enable save button
                binding.saveButton.isEnabled = true
                stationViewHolder.validationJob?.cancel()
            } else {
                // 1. disable save button
                binding.saveButton.isEnabled = false
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
                                binding.saveButton.isEnabled = true
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
    private class AddNewViewHolder(val binding: CardAddNewStationBinding) :
        RecyclerView.ViewHolder(binding.root)
    /*
     * End of inner class
     */


    /*
     * Inner class: ViewHolder for a station
     */
    private class StationViewHolder(val binding: CardStationBinding) :
        RecyclerView.ViewHolder(binding.root) {
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
