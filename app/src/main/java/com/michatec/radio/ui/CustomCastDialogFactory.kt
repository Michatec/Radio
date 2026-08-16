package com.michatec.radio.ui

import androidx.mediarouter.app.MediaRouteChooserDialogFragment
import androidx.mediarouter.app.MediaRouteControllerDialogFragment
import androidx.mediarouter.app.MediaRouteDialogFactory

/**
 * A custom MediaRouteDialogFactory that tells the MediaRouteButton to use
 * our custom chooser and controller dialogs.
 */
class CustomCastDialogFactory : MediaRouteDialogFactory() {

    override fun onCreateChooserDialogFragment(): MediaRouteChooserDialogFragment {
        return CustomCastChooserDialogFragment()
    }

    override fun onCreateControllerDialogFragment(): MediaRouteControllerDialogFragment {
        return CustomCastControllerDialogFragment()
    }
}