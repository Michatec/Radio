# Radio App - AIDL API Documentation

This documentation describes the Android Interface Definition Language (AIDL) API provided by the Radio app. External applications can use this API to control audio playback, retrieve saved station lists, inspect current station metadata, and interact with the radio player service via Inter-Process Communication (IPC).

---

## Service Information

- **Package Name:** `com.michatec.radio`
- **Service Class:** `com.michatec.radio.api.RadioApiService`
- **AIDL Interface:** `com.michatec.radio.api.IRadioApi`

---

## AIDL Interface Specification (`IRadioApi.aidl`)

```aidl
package com.michatec.radio.api;

interface IRadioApi {
    void play();
    void pause();
    void stop();
    boolean isPlaying();
    String getCurrentStationName();
    String getCurrentMetadata();
    void playStationByUuid(String uuid);
    String getSavedStationsJson();
    String getFavoriteStationsJson();
}
```

### Method Summary

| Method                           | Return Type | Description                                                           |
|:---------------------------------|:------------|:----------------------------------------------------------------------|
| `play()`                         | `void`      | Resumes or starts audio playback.                                     |
| `pause()`                        | `void`      | Pauses audio playback.                                                |
| `stop()`                         | `void`      | Stops audio playback and releases player resources.                   |
| `isPlaying()`                    | `boolean`   | Returns `true` if audio is currently playing, `false` otherwise.      |
| `getCurrentStationName()`        | `String`    | Returns the display name of the currently active radio station.       |
| `getCurrentMetadata()`           | `String`    | Returns current track metadata/artist info (if available).            |
| `playStationByUuid(String uuid)` | `void`      | Switches to and plays the radio station matching the specified UUID.  |
| `getSavedStationsJson()`         | `String`    | Returns a JSON array string containing all saved radio stations.      |
| `getFavoriteStationsJson()`      | `String`    | Returns a JSON array string containing all starred/favorite stations. |

---

## Integrating the API in External Apps

To control the Radio app from another Android application, follow these steps:

### 1. Copy the AIDL File
Copy the `IRadioApi.aidl` file from the Radio app into your client app maintaining the exact package directory structure:
```
your-client-app/src/main/aidl/com/michatec/radio/api/IRadioApi.aidl
```
*(Android Studio will automatically compile the stub classes during the next build).*

---

### 2. Connect to the Service (Kotlin Example)

Implement a `ServiceConnection` in your client app to bind to `RadioApiService`:

```kotlin
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.michatec.radio.api.IRadioApi

class RadioRemoteController(private val context: Context) {

    private var radioApi: IRadioApi? = null
    private var isBound = false
    private val gson = Gson()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            radioApi = IRadioApi.Stub.asInterface(service)
            isBound = true
            Log.d("RadioRemote", "Connected to Radio App API Service")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            radioApi = null
            isBound = false
            Log.d("RadioRemote", "Disconnected from Radio App API Service")
        }
    }

    fun connect() {
        if (!isBound) {
            val intent = Intent().apply {
                setComponent(
                    ComponentName(
                        "com.michatec.radio",
                        "com.michatec.radio.api.RadioApiService"
                    )
                )
            }
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun disconnect() {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
            radioApi = null
        }
    }

    // Playback Controls
    fun play() {
        radioApi?.play()
    }

    fun pause() {
        radioApi?.pause()
    }

    fun stop() {
        radioApi?.stop()
    }

    fun isPlaying(): Boolean {
        return try {
            radioApi?.isPlaying() ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun getCurrentStationName(): String {
        return try {
            radioApi?.getCurrentStationName() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun getCurrentMetadata(): String {
        return try {
            radioApi?.getCurrentMetadata() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun playStation(uuid: String) {
        try {
            radioApi?.playStationByUuid(uuid)
        } catch (e: Exception) {
            Log.e("RadioRemote", "Failed to play station by UUID", e)
        }
    }

    // Fetch Saved and Favorite Stations as JSON lists
    fun getSavedStations(): List<StationModel> {
        return try {
            val json = radioApi?.getSavedStationsJson() ?: "[]"
            val type = object : TypeToken<List<StationModel>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getFavoriteStations(): List<StationModel> {
        return try {
            val json = radioApi?.getFavoriteStationsJson() ?: "[]"
            val type = object : TypeToken<List<StationModel>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// Data class representation for client apps
data class StationModel(
    val uuid: String,
    val name: String,
    val starred: Boolean,
    val streamUris: List<String>,
    val stream: Int
)
```

---

### 3. Usage Example in an Activity or ViewModel

```kotlin
class MyActivity : AppCompatActivity() {
    private lateinit var radioController: RadioRemoteController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        radioController = RadioRemoteController(this)
        radioController.connect()

        // Example: Play button click
        findViewById<Button>(id.btnPlay).setOnClickListener {
            radioController.play()
        }

        // Example: Fetch and log all saved stations
        findViewById<Button>(id.btnFetchStations).setOnClickListener {
            val savedStations = radioController.getSavedStations()
            val favoriteStations = radioController.getFavoriteStations()
            Log.d("RadioApp", "Saved stations count: ${savedStations.size}")
            Log.d("RadioApp", "Favorites count: ${favoriteStations.size}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        radioController.disconnect()
    }
}
```
