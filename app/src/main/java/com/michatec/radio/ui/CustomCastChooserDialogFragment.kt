package com.michatec.radio.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.mediarouter.app.MediaRouteChooserDialogFragment
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michatec.radio.R

/**
 * A custom DialogFragment that replaces the default Google Cast device selection dialog.
 */
class CustomCastChooserDialogFragment : MediaRouteChooserDialogFragment() {

    private lateinit var router: MediaRouter
    private lateinit var selector: MediaRouteSelector
    private var adapter: CastDeviceAdapter? = null
    private var recyclerView: RecyclerView? = null
    private var noDevicesText: TextView? = null

    private val callback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateRouteList()
        }

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateRouteList()
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateRouteList()
        }
    }

    /**
     * Data class to hold route information safely for background diffing.
     * MediaRouter.RouteInfo must only be accessed on the main thread.
     */
    private data class RouteItem(
        val id: String,
        val name: String,
        val description: String?,
        val isSelected: Boolean,
        val originalRoute: MediaRouter.RouteInfo
    )

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        router = MediaRouter.getInstance(context)

        selector = MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            .build()

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_cast_chooser, null)
        recyclerView = view.findViewById(R.id.recyclerViewDevices)
        noDevicesText = view.findViewById(R.id.textViewNoDevices)

        recyclerView?.layoutManager = LinearLayoutManager(context)
        adapter = CastDeviceAdapter { routeItem ->
            routeItem.originalRoute.select()
            dismiss()
        }
        recyclerView?.adapter = adapter

        updateRouteList()

        return MaterialAlertDialogBuilder(context)
            .setView(view)
            .create()
    }

    override fun onStart() {
        super.onStart()
        router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
    }

    override fun onStop() {
        router.removeCallback(callback)
        super.onStop()
    }

    private fun updateRouteList() {
        val routes = router.routes
            .filter { it.matchesSelector(selector) && !it.isDefault }
            .map { route ->
                RouteItem(
                    id = route.id,
                    name = route.name,
                    description = route.description,
                    isSelected = route.isSelected,
                    originalRoute = route
                )
            }
        
        adapter?.submitList(routes)
        
        val hasDevices = routes.isNotEmpty()
        recyclerView?.isVisible = hasDevices
        noDevicesText?.isVisible = !hasDevices
    }

    /**
     * Inner adapter for the Cast device list
     */
    private class CastDeviceAdapter(private val onRouteSelected: (RouteItem) -> Unit) :
        ListAdapter<RouteItem, CastDeviceAdapter.ViewHolder>(RouteDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cast_device, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            holder.nameText.text = item.name

            val description = item.description
            if (!description.isNullOrEmpty()) {
                holder.descriptionText.text = description
                holder.descriptionText.isVisible = true
            } else {
                holder.descriptionText.isVisible = false
            }
            
            holder.itemView.setOnClickListener { onRouteSelected(item) }
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.textViewName)
            val descriptionText: TextView = view.findViewById(R.id.textViewDescription)
        }

        private class RouteDiffCallback : DiffUtil.ItemCallback<RouteItem>() {
            override fun areItemsTheSame(oldItem: RouteItem, newItem: RouteItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: RouteItem, newItem: RouteItem): Boolean {
                return oldItem.name == newItem.name && 
                       oldItem.description == newItem.description &&
                       oldItem.isSelected == newItem.isSelected
            }
        }
    }
}