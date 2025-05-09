/*
 * PreferencesHelper.kt
 * Implements the PreferencesHelper object
 * A PreferencesHelper provides helper methods for the saving and loading values from shared preferences
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.michatec.radio.helpers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.michatec.radio.Keys
import com.michatec.radio.ui.PlayerState
import java.util.*


/*
 * PreferencesHelper object
 */
object PreferencesHelper {


    /* Define log tag */
    private val TAG: String = PreferencesHelper::class.java.simpleName


    /* The sharedPreferences object to be initialized */
    private lateinit var sharedPreferences: SharedPreferences

    /* Initialize a single sharedPreferences object when the app is launched */
    fun Context.initPreferences() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
    }


    /* Loads address of radio-browser.info API from shared preferences */
    fun loadRadioBrowserApiAddress(): String {
        return sharedPreferences.getString(
            Keys.PREF_RADIO_BROWSER_API,
            Keys.RADIO_BROWSER_API_DEFAULT
        ) ?: Keys.RADIO_BROWSER_API_DEFAULT
    }


    /* Saves address of radio-browser.info API to shared preferences */
    fun saveRadioBrowserApiAddress(radioBrowserApi: String) {
        sharedPreferences.edit {
            putString(Keys.PREF_RADIO_BROWSER_API, radioBrowserApi)
        }
    }


    /* Saves state of playback for player to shared preferences */
    fun saveIsPlaying(isPlaying: Boolean) {
        sharedPreferences.edit {
            putBoolean(Keys.PREF_PLAYER_STATE_IS_PLAYING, isPlaying)
        }
    }


    /* Load uuid of the station in the station list which is currently expanded */
    fun loadStationListStreamUuid(): String {
        return sharedPreferences.getString(Keys.PREF_STATION_LIST_EXPANDED_UUID, String())
            ?: String()
    }


    /* Save uuid of the station in the station list which is currently expanded  */
    fun saveStationListStreamUuid(stationUuid: String = String()) {
        sharedPreferences.edit {
            putString(Keys.PREF_STATION_LIST_EXPANDED_UUID, stationUuid)
        }
    }


    /* Saves last update to shared preferences */
    fun saveLastUpdateCollection(lastUpdate: Date = Calendar.getInstance().time) {
        sharedPreferences.edit {
            putString(Keys.PREF_LAST_UPDATE_COLLECTION, DateTimeHelper.convertToRfc2822(lastUpdate))
        }
    }


    /* Loads size of collection from shared preferences */
    fun loadCollectionSize(): Int {
        return sharedPreferences.getInt(Keys.PREF_COLLECTION_SIZE, -1)
    }


    /* Saves site of collection to shared preferences */
    fun saveCollectionSize(size: Int) {
        sharedPreferences.edit {
            putInt(Keys.PREF_COLLECTION_SIZE, size)
        }
    }


    /* Saves state of sleep timer to shared preferences */
    fun saveSleepTimerRunning(isRunning: Boolean) {
        sharedPreferences.edit {
            putBoolean(Keys.PREF_PLAYER_STATE_SLEEP_TIMER_RUNNING, isRunning)
        }
    }


    /* Loads date of last save operation from shared preferences */
    fun loadCollectionModificationDate(): Date {
        val modificationDateString: String =
            sharedPreferences.getString(Keys.PREF_COLLECTION_MODIFICATION_DATE, "") ?: String()
        return DateTimeHelper.convertFromRfc2822(modificationDateString)
    }


    /* Saves date of last save operation to shared preferences */
    fun saveCollectionModificationDate(lastSave: Date = Calendar.getInstance().time) {
        sharedPreferences.edit {
            putString(
                Keys.PREF_COLLECTION_MODIFICATION_DATE,
                DateTimeHelper.convertToRfc2822(lastSave)
            )
        }
    }


    /* Loads active downloads from shared preferences */
    fun loadActiveDownloads(): String {
        val activeDownloadsString: String =
            sharedPreferences.getString(Keys.PREF_ACTIVE_DOWNLOADS, Keys.ACTIVE_DOWNLOADS_EMPTY)
                ?: Keys.ACTIVE_DOWNLOADS_EMPTY
        Log.v(TAG, "IDs of active downloads: $activeDownloadsString")
        return activeDownloadsString
    }


    /* Saves active downloads to shared preferences */
    fun saveActiveDownloads(activeDownloadsString: String = String()) {
        sharedPreferences.edit {
            putString(Keys.PREF_ACTIVE_DOWNLOADS, activeDownloadsString)
        }
    }


    /* Loads state of player user interface from shared preferences */
    fun loadPlayerState(): PlayerState {
        return PlayerState().apply {
            stationUuid = sharedPreferences.getString(Keys.PREF_PLAYER_STATE_STATION_UUID, String())
                ?: String()
            isPlaying = sharedPreferences.getBoolean(Keys.PREF_PLAYER_STATE_IS_PLAYING, false)
            sleepTimerRunning =
                sharedPreferences.getBoolean(Keys.PREF_PLAYER_STATE_SLEEP_TIMER_RUNNING, false)
        }
    }


    /* Saves Uuid if currently playing station to shared preferences */
    fun saveCurrentStationId(stationUuid: String) {
        sharedPreferences.edit {
            putString(Keys.PREF_PLAYER_STATE_STATION_UUID, stationUuid)
        }
    }


    /* Loads uuid of last played station from shared preferences */
    fun loadLastPlayedStationUuid(): String {
        return sharedPreferences.getString(Keys.PREF_PLAYER_STATE_STATION_UUID, String())
            ?: String()
    }


    /* Saves history of metadata in shared preferences */
    fun saveMetadataHistory(metadataHistory: MutableList<String>) {
        val gson = Gson()
        val json = gson.toJson(metadataHistory)
        sharedPreferences.edit {
            putString(Keys.PREF_PLAYER_METADATA_HISTORY, json)
        }
    }


    /* Loads history of metadata from shared preferences */
    fun loadMetadataHistory(): MutableList<String> {
        var metadataHistory: MutableList<String> = mutableListOf()
        val json: String =
            sharedPreferences.getString(Keys.PREF_PLAYER_METADATA_HISTORY, String()) ?: String()
        if (json.isNotEmpty()) {
            val gson = Gson()
            metadataHistory = gson.fromJson(json, metadataHistory::class.java)
        }
        return metadataHistory
    }


    /* Start watching for changes in shared preferences - context must implement OnSharedPreferenceChangeListener */
    fun registerPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }


    /* Stop watching for changes in shared preferences - context must implement OnSharedPreferenceChangeListener */
    fun unregisterPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }


    /* Checks if housekeeping work needs to be done - used usually in DownloadWorker "REQUEST_UPDATE_COLLECTION" */
    fun isHouseKeepingNecessary(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_ONE_TIME_HOUSEKEEPING_NECESSARY, true)
    }


    /* Saves state of housekeeping */
    fun saveHouseKeepingNecessaryState(state: Boolean = false) {
        sharedPreferences.edit {
            putBoolean(Keys.PREF_ONE_TIME_HOUSEKEEPING_NECESSARY, state)
        }
    }


    /* Load currently selected app theme */
    fun loadThemeSelection(): String {
        return sharedPreferences.getString(
            Keys.PREF_THEME_SELECTION,
            Keys.STATE_THEME_FOLLOW_SYSTEM
        ) ?: Keys.STATE_THEME_FOLLOW_SYSTEM
    }


    /* Loads value of the option: Edit Stations */
    fun loadEditStationsEnabled(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_EDIT_STATIONS, true)
    }


    /* Saves value of the option: Edit Stations (only needed for migration) */
    fun saveEditStationsEnabled(enabled: Boolean = false) {
        sharedPreferences.edit {
            putBoolean(Keys.PREF_EDIT_STATIONS, enabled)
        }
    }


    /* Loads value of the option: Edit Station Streams */
    fun loadEditStreamUrisEnabled(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_EDIT_STREAMS_URIS, true)
    }


    /* Loads value of the option: Buffer Size */
    fun loadLargeBufferSize(): Boolean {
        return sharedPreferences.getBoolean(Keys.PREF_LARGE_BUFFER_SIZE, false)
    }


    /* Loads a multiplier value for constructing the load control */
    fun loadBufferSizeMultiplier(): Int {
        return if (sharedPreferences.getBoolean(Keys.PREF_LARGE_BUFFER_SIZE, false)) {
            Keys.LARGE_BUFFER_SIZE_MULTIPLIER
        } else {
            1
        }
    }


    /* Return whether to download over mobile */
    fun downloadOverMobile(): Boolean {
        return sharedPreferences.getBoolean(
            Keys.PREF_DOWNLOAD_OVER_MOBILE,
            Keys.DEFAULT_DOWNLOAD_OVER_MOBILE
        )
    }

}
