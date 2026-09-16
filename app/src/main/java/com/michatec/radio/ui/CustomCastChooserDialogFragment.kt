package com.michatec.radio.ui

import android.app.Dialog
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.ComposeView
import androidx.mediarouter.app.MediaRouteChooserDialogFragment
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michatec.radio.ui.screens.CastChooserContent
import com.michatec.radio.ui.theme.RadioTheme
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * A custom DialogFragment that replaces the default Google Cast device selection dialog.
 */
class CustomCastChooserDialogFragment : MediaRouteChooserDialogFragment() {

    private lateinit var router: MediaRouter
    private lateinit var selector: MediaRouteSelector

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        router = MediaRouter.getInstance(context)

        selector = MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            .build()

        val composeView = ComposeView(context).apply {
            setContent {
                RadioTheme {
                    val routes by produceState(initialValue = emptyList<MediaRouter.RouteInfo>()) {
                        val flow = callbackFlow {
                            val updateList = {
                                val currentRoutes = router.routes.filter { 
                                    it.matchesSelector(selector) && !it.isDefault 
                                }
                                trySend(currentRoutes)
                            }

                            val callback = object : MediaRouter.Callback() {
                                override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) { updateList() }
                                override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) { updateList() }
                                override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) { updateList() }
                            }

                            router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
                            updateList()
                            
                            awaitClose {
                                router.removeCallback(callback)
                            }
                        }

                        flow.collectLatest { 
                            value = it
                        }
                    }

                    CastChooserContent(
                        routes = routes,
                        onRouteSelected = { route ->
                            route.select()
                            dismiss()
                        }
                    )
                }
            }
        }

        return MaterialAlertDialogBuilder(context)
            .setView(composeView)
            .create()
    }
}