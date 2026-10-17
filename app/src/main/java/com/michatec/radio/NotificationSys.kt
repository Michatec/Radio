package com.michatec.radio

import android.R.id.content
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.google.android.material.snackbar.Snackbar

object NotificationSys {
    private const val CHANNEL_ID = "com.michatec.radio.channel_messages"

    fun createNotificationChannel(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            val soundUri = "android.resource://${context.packageName}/${R.raw.notification}".toUri()

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotification(context: Context, title: String, content: String, intent: Intent? = null, id: Int = 5000) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(context)

        val targetIntent = intent ?: Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_app_icon_white_24dp)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id, notification)
    }

    fun showSnackbar(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                if (context is Activity) {
                    val rootView = context.findViewById<View>(content)
                    if (rootView != null) {
                        Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show()
                        return@post
                    }
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
