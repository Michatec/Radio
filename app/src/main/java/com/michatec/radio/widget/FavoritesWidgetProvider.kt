package com.michatec.radio.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.michatec.radio.Keys
import com.michatec.radio.MainActivity
import com.michatec.radio.R
import com.michatec.radio.helpers.FileHelper
import com.michatec.radio.helpers.PreferencesHelper
import com.michatec.radio.helpers.PreferencesHelper.initPreferences
import java.util.Locale

class FavoritesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Keys.ACTION_COLLECTION_CHANGED || intent.action == Keys.ACTION_THEME_CHANGED) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, FavoritesWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            onUpdate(context, appWidgetManager, appWidgetIds)
            
            // For older versions, also explicitly notify the list that data has changed
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                @Suppress("DEPRECATION")
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.favorites_list)
            }
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            context.initPreferences()

            // Language and Theme-aware context for widgets
            val languageCode = PreferencesHelper.loadSelectedLanguage()
            val themeSelection = PreferencesHelper.loadThemeSelection()
            val configuration = Configuration(context.resources.configuration)

            // Apply language
            val locale = if (languageCode != "system") {
                Locale.forLanguageTag(languageCode)
            } else {
                context.resources.configuration.locales[0]
            }
            configuration.setLocale(locale)
            configuration.setLayoutDirection(locale)
            
            // Apply theme
            when (themeSelection) {
                Keys.STATE_THEME_LIGHT_MODE -> configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
                Keys.STATE_THEME_DARK_MODE -> configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
            val themedContext = context.createConfigurationContext(configuration)

            val views = RemoteViews(themedContext.packageName, R.layout.favorites_widget)
            val iconColor = ContextCompat.getColor(themedContext, R.color.icon_default)
            val textColor = ContextCompat.getColor(themedContext, R.color.text_default)

            // Explicitly set localized strings
            views.setTextViewText(R.id.widget_title, themedContext.resources.getString(R.string.widget_favorites_title))
            views.setTextViewText(R.id.empty_view, themedContext.resources.getString(R.string.widget_favorites_empty))

            views.setTextColor(R.id.widget_title, textColor)
            views.setInt(R.id.widget_icon, "setColorFilter", iconColor)
            views.setTextColor(R.id.empty_view, textColor)

            if (PreferencesHelper.loadCustomThemeEnabled()) {
                val customColor = PreferencesHelper.loadCustomThemeColor(themedContext)
                views.setViewVisibility(R.id.widget_background_image, View.VISIBLE)
                views.setImageViewResource(R.id.widget_background_image, R.drawable.widget_background_custom_base)
                views.setInt(R.id.widget_background_image, "setColorFilter", customColor)
                views.setInt(R.id.widget_root, "setBackgroundResource", 0)
            } else {
                views.setViewVisibility(R.id.widget_background_image, View.GONE)
                val backgroundRes = when (themeSelection) {
                    Keys.STATE_THEME_DARK_MODE -> R.drawable.widget_background_dark
                    else -> R.drawable.widget_background
                }
                views.setInt(R.id.widget_root, "setBackgroundResource", backgroundRes)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val collection = FileHelper.readCollection(themedContext)
                val favorites = collection.stations.filter { it.starred }
                
                val builder = RemoteViews.RemoteCollectionItems.Builder()
                for (station in favorites) {
                    val itemViews = RemoteViews(themedContext.packageName, R.layout.favorites_widget_item)
                    itemViews.setTextViewText(R.id.station_name, station.name)
                    itemViews.setTextColor(R.id.station_name, textColor)
                    itemViews.setInt(R.id.station_icon, "setColorFilter", iconColor)
                    
                    val fillInIntent = Intent().apply { putExtra(Keys.EXTRA_STATION_UUID, station.uuid) }
                    itemViews.setOnClickFillInIntent(R.id.station_name, fillInIntent)
                    itemViews.setOnClickFillInIntent(R.id.station_icon, fillInIntent)
                    
                    builder.addItem(station.uuid.hashCode().toLong(), itemViews)
                }
                views.setRemoteAdapter(R.id.favorites_list, builder.build())
            } else {
                val intent = Intent(themedContext, FavoritesWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                intent.data = Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME))
                @Suppress("DEPRECATION")
                views.setRemoteAdapter(R.id.favorites_list, intent)
            }

            views.setEmptyView(R.id.favorites_list, R.id.empty_view)

            val clickIntent = Intent(themedContext, MainActivity::class.java).apply {
                action = Keys.ACTION_START
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            views.setPendingIntentTemplate(R.id.favorites_list, PendingIntent.getActivity(themedContext, 0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))

            val mainIntent = Intent(themedContext, MainActivity::class.java).apply {
                action = Keys.ACTION_START
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val mainPendingIntent = PendingIntent.getActivity(
                context, 1, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, mainPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_header, mainPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
