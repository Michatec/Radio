package com.michatec.radio.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import com.michatec.radio.Keys
import com.michatec.radio.R
import com.michatec.radio.core.Station
import com.michatec.radio.helpers.FileHelper
import com.michatec.radio.helpers.PreferencesHelper

class FavoritesWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return FavoritesWidgetFactory(this.applicationContext)
    }
}

class FavoritesWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var favorites: List<Station> = listOf()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val collection = FileHelper.readCollection(context)
        favorites = collection.stations.filter { it.starred }
    }

    override fun onDestroy() {}

    override fun getCount(): Int = favorites.size

    override fun getViewAt(position: Int): RemoteViews {
        val station = favorites[position]

        val themeSelection = PreferencesHelper.loadThemeSelection()
        val configuration = Configuration(context.resources.configuration)
        when (themeSelection) {
            Keys.STATE_THEME_LIGHT_MODE -> configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
            Keys.STATE_THEME_DARK_MODE -> configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }
        val themedContext = context.createConfigurationContext(configuration)

        val views = RemoteViews(themedContext.packageName, R.layout.favorites_widget_item)
        views.setTextViewText(R.id.station_name, station.name)

        val iconColor = ContextCompat.getColor(themedContext, R.color.icon_default)
        val textColor = ContextCompat.getColor(themedContext, R.color.text_default)
        views.setTextColor(R.id.station_name, textColor)
        views.setInt(R.id.station_icon, "setColorFilter", iconColor)
        
        // Handle click
        val fillInIntent = Intent().apply {
            putExtra(Keys.EXTRA_STATION_UUID, station.uuid)
        }
        views.setOnClickFillInIntent(R.id.station_name, fillInIntent)
        views.setOnClickFillInIntent(R.id.station_icon, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
