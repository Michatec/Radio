package com.michatec.radio

import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.preference.PreferenceFragmentCompat
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.michatec.radio.extensions.requestVisualizerData
import com.michatec.radio.helpers.ExtrasHelper

/*
 * VisualizerFragment class: Handles audio visualization
 */
@OptIn(UnstableApi::class)
class VisualizerFragment : PreferenceFragmentCompat() {

    private val TAG = "VisualizerFragment"
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private val controller: MediaController?
        get() = if (this::controllerFuture.isInitialized && controllerFuture.isDone) {
            try { controllerFuture.get() } catch (_: Exception) { null }
        } else null

    private var visualizerPref: ExtrasHelper.VisualizerPreference? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as AppCompatActivity).supportActionBar?.title = getString(R.string.pref_visualizer_title)
        (activity as AppCompatActivity).supportActionBar?.show()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)
        
        visualizerPref = ExtrasHelper.VisualizerPreference(context)
        visualizerPref?.key = "visualizer_key"
        screen.addPreference(visualizerPref!!)

        preferenceScreen = screen
    }

    override fun onStart() {
        super.onStart()
        initializeController()
    }

    override fun onStop() {
        super.onStop()
        releaseController()
    }

    override fun onResume() {
        super.onResume()
        startPolling()
    }

    override fun onPause() {
        super.onPause()
        stopPolling()
    }

    private fun initializeController() {
        controllerFuture = MediaController.Builder(
            requireContext(),
            SessionToken(requireContext(), ComponentName(requireContext(), PlayerService::class.java))
        ).buildAsync()
        controllerFuture.addListener({
            Log.d(TAG, "MediaController connected: ${controller != null}")
        }, MoreExecutors.directExecutor())
    }

    private fun releaseController() {
        if (this::controllerFuture.isInitialized) {
            MediaController.releaseFuture(controllerFuture)
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            val c = controller
            if (c != null && c.isPlaying) {
                val resultFuture = c.requestVisualizerData()
                resultFuture.addListener({
                    try {
                        val result = resultFuture.get()
                        if (result.resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS) {
                            val data = result.extras.getFloatArray(Keys.EXTRA_VISUALIZER_DATA)
                            if (data != null && data.isNotEmpty()) {
                                visualizerPref?.update(data)
                            }
                        } else {
                            Log.e(TAG, "Custom command failed with result code: ${result.resultCode}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching visualizer data", e)
                    }
                }, MoreExecutors.directExecutor())
            }
            handler.postDelayed(this, 25) // ~40 FPS
        }
    }

    private fun startPolling() {
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
    }

    private fun stopPolling() {
        handler.removeCallbacks(pollRunnable)
    }
}
