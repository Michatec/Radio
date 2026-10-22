package com.michatec.radio.collection

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.michatec.radio.core.Collection
import com.michatec.radio.helpers.AppEvent
import com.michatec.radio.helpers.AppEventBus
import com.michatec.radio.helpers.FileHelper
import kotlinx.coroutines.launch
import java.util.Date


/*
 * CollectionViewModel.class
 */
class CollectionViewModel(application: Application) : AndroidViewModel(application) {

    /* Define log tag */
    private val TAG: String = CollectionViewModel::class.java.simpleName

    /* Main class variables */
    val collectionLiveData: MutableLiveData<Collection> = MutableLiveData<Collection>()
    val collectionSizeLiveData: MutableLiveData<Int> = MutableLiveData<Int>()
    private var modificationDateViewModel: Date = Date()


    /* Init constructor */
    init {
        // load collection
        loadCollection()
        // observe collection changed events
        viewModelScope.launch {
            AppEventBus.events.collect { event ->
                if (event is AppEvent.CollectionChanged) {
                    val date = Date(event.modificationDateMillis)
                    if (date.after(modificationDateViewModel)) {
                        Log.v(
                            TAG,
                            "CollectionViewModel - reload collection after event received."
                        )
                        loadCollection()
                    }
                }
            }
        }
    }


    /* Reads collection of radio stations from storage using GSON */
    private fun loadCollection() {
        Log.v(TAG, "Loading collection of stations from storage")
        viewModelScope.launch {
            // load collection on background thread
            val collection: Collection = FileHelper.readCollectionSuspended(getApplication())
            // get updated modification date
            modificationDateViewModel = collection.modificationDate
            // update collection view model
            collectionLiveData.value = collection
            // update collection sie
            collectionSizeLiveData.value = collection.stations.size
        }
    }

}
