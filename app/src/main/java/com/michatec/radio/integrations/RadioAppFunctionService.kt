package com.michatec.radio.integrations

import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import androidx.media3.common.util.UnstableApi
import com.michatec.radio.PlayerService
import com.michatec.radio.api.RadioApiService
import com.michatec.radio.helpers.CollectionHelper
import com.michatec.radio.helpers.FileHelper
import com.michatec.radio.helpers.PreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
@AppFunctionServiceEntryPoint(
    serviceName = "RadioAppFunctionService",
    appFunctionXmlFileName = "radio_app_functions",
)
abstract class BaseRadioAppFunctionService : AppFunctionService() {

    @OptIn(UnstableApi::class)
    private fun startPlaybackForUuid(uuid: String, stationName: String): String {
        PreferencesHelper.saveCurrentStationId(uuid)
        val controller = RadioApiService.playbackController
        if (controller != null) {
            controller.playStationByUuid(uuid)
        } else {
            val intent = Intent(applicationContext, PlayerService::class.java).apply {
                action = "com.michatec.radio.action.PLAY_STATION"
            }
            applicationContext.startForegroundService(intent)
        }
        return "Playing $stationName."
    }

    /**
     * Searches saved radio stations matching [query] by name or genre.
     *
     * @param query The search query (station name or genre keyword).
     * @return A formatted list of matching saved radio stations.
     */
    @OptIn(UnstableApi::class)
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun searchStations(query: String): String = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return@withContext "Search query is required."

        val collection = FileHelper.readCollection(applicationContext)
        val matches = collection.stations.filter { station ->
            station.name.contains(cleanQuery, ignoreCase = true) ||
                    station.codec.contains(cleanQuery, ignoreCase = true) ||
                    station.homepage.contains(cleanQuery, ignoreCase = true)
        }

        if (matches.isEmpty()) {
            return@withContext "No saved stations matching \"$cleanQuery\" were found."
        }

        matches.joinToString("\n") { station ->
            "- Name: ${station.name}, UUID: ${station.uuid}${if (station.starred) " (Favorite)" else ""}"
        }
    }

    /**
     * Plays a saved radio station whose name matches [stationName].
     *
     * @param stationName The name of the radio station to play.
     * @return A short status message describing the result.
     */
    @OptIn(UnstableApi::class)
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun playStation(stationName: String): String = withContext(Dispatchers.IO) {
        val query = stationName.trim()
        if (query.isEmpty()) return@withContext "Station name is required."

        val collection = FileHelper.readCollection(applicationContext)
        val station = CollectionHelper.findStationByName(collection, query)
            ?: return@withContext "No saved station matching \"$query\" was found."

        startPlaybackForUuid(station.uuid, station.name)
    }

    /**
     * Plays a saved radio station identified by [stationUuid].
     *
     * @param stationUuid The UUID of the saved radio station to play.
     * @return A short status message describing the result.
     */
    @OptIn(UnstableApi::class)
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun playStationByUuid(stationUuid: String): String = withContext(Dispatchers.IO) {
        val uuid = stationUuid.trim()
        if (uuid.isEmpty()) return@withContext "Station UUID is required."

        val collection = FileHelper.readCollection(applicationContext)
        val station = collection.stations.find { it.uuid == uuid }
            ?: return@withContext "No saved station with UUID \"$uuid\" was found."

        startPlaybackForUuid(station.uuid, station.name)
    }

    /**
     * Pauses the currently playing radio station.
     *
     * @return A short status message describing the result.
     */
    @OptIn(UnstableApi::class)
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun pauseRadio(): String = withContext(Dispatchers.IO) {
        val controller = RadioApiService.playbackController
        if (controller != null) {
            controller.pause()
        } else {
            PreferencesHelper.saveIsPlaying(false)
        }
        return@withContext "Radio paused."
    }

    /**
     * Resumes playback of the last played radio station.
     *
     * @return A short status message describing the result.
     */
    @OptIn(UnstableApi::class)
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun resumeRadio(): String = withContext(Dispatchers.IO) {
        val controller = RadioApiService.playbackController
        if (controller != null) {
            controller.play()
        } else {
            val intent = Intent(applicationContext, PlayerService::class.java).apply {
                action = "com.michatec.radio.action.PLAY"
            }
            applicationContext.startForegroundService(intent)
        }
        return@withContext "Resuming radio."
    }

    /**
     * Plays the next saved radio station in the collection.
     *
     * @return A short status message describing the result.
     */
    @OptIn(UnstableApi::class)
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun playNextStation(): String = withContext(Dispatchers.IO) {
        val collection = FileHelper.readCollection(applicationContext)
        if (collection.stations.isEmpty()) return@withContext "No saved radio stations in collection."

        val currentUuid = PreferencesHelper.loadPlayerState().stationUuid
        val currentIndex = CollectionHelper.getStationPosition(collection, currentUuid)
        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % collection.stations.size
        val nextStation = collection.stations[nextIndex]

        startPlaybackForUuid(nextStation.uuid, nextStation.name)
    }

    /**
     * Plays the previous saved radio station in the collection.
     *
     * @return A short status message describing the result.
     */
    @OptIn(UnstableApi::class)
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun playPreviousStation(): String = withContext(Dispatchers.IO) {
        val collection = FileHelper.readCollection(applicationContext)
        if (collection.stations.isEmpty()) return@withContext "No saved radio stations in collection."

        val currentUuid = PreferencesHelper.loadPlayerState().stationUuid
        val currentIndex = CollectionHelper.getStationPosition(collection, currentUuid)
        val prevIndex = if (currentIndex <= 0) collection.stations.size - 1 else currentIndex - 1
        val prevStation = collection.stations[prevIndex]

        startPlaybackForUuid(prevStation.uuid, prevStation.name)
    }

    /**
     * Retrieves the current playback state and active radio station metadata.
     *
     * @return A status message describing current playback state and active station.
     */
    @OptIn(UnstableApi::class)
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getPlaybackState(): String = withContext(Dispatchers.IO) {
        val controller = RadioApiService.playbackController
        val isPlaying = controller?.isPlaying() ?: PreferencesHelper.loadPlayerState().isPlaying
        val stationName = controller?.getCurrentStationName() ?: run {
            try {
                val collection = FileHelper.readCollection(applicationContext)
                val currentUuid = PreferencesHelper.loadPlayerState().stationUuid
                CollectionHelper.getStation(collection, currentUuid).name
            } catch (_: Exception) {
                ""
            }
        }
        val metadata = controller?.getCurrentMetadata() ?: ""

        val stateText = if (isPlaying) "Playing" else "Paused/Stopped"
        val stationText = if (stationName.isNotEmpty()) "Station: $stationName" else "No active station"
        val metadataText = if (metadata.isNotEmpty()) ", Track: $metadata" else ""

        "State: $stateText. $stationText$metadataText."
    }

    /**
     * Retrieves the currently active radio station details.
     *
     * @return A description of the current radio station.
     */
    @OptIn(UnstableApi::class)
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getCurrentStation(): String = withContext(Dispatchers.IO) {
        val currentUuid = PreferencesHelper.loadPlayerState().stationUuid
        if (currentUuid.isEmpty()) return@withContext "No active radio station selected."

        val collection = FileHelper.readCollection(applicationContext)
        val station = collection.stations.find { it.uuid == currentUuid }
            ?: return@withContext "Active station UUID \"$currentUuid\" was not found in saved collection."

        "Name: ${station.name}, UUID: ${station.uuid}, Favorite: ${station.starred}, Stream: ${station.getStreamUri()}"
    }

    /**
     * Retrieves all saved radio stations in the user's collection.
     *
     * @return A list of all saved radio stations with their names and UUIDs.
     */
    @OptIn(UnstableApi::class)
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getSavedStations(): String = withContext(Dispatchers.IO) {
        val collection = FileHelper.readCollection(applicationContext)
        if (collection.stations.isEmpty()) return@withContext "No saved radio stations."

        collection.stations.joinToString("\n") { station ->
            "- Name: ${station.name}, UUID: ${station.uuid}${if (station.starred) " (Favorite)" else ""}"
        }
    }

    /**
     * Retrieves favorite radio stations in the user's collection.
     *
     * @return A list of favorite radio stations with their names and UUIDs.
     */
    @OptIn(UnstableApi::class)
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getFavoriteStations(): String = withContext(Dispatchers.IO) {
        val collection = FileHelper.readCollection(applicationContext)
        val favorites = collection.stations.filter { it.starred }
        if (favorites.isEmpty()) return@withContext "No favorite radio stations."

        favorites.joinToString("\n") { station ->
            "- Name: ${station.name}, UUID: ${station.uuid}"
        }
    }
}
