/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.notifications

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jamal2367.styx.IncognitoActivity
import com.jamal2367.styx.R

/**
 * A notification helper that displays the current number of tabs open in a notification as a
 * warning. When the notification is pressed, the incognito browser will open.
 */

class IncognitoNotification(
    private val context: Context,
    private val notificationManager: NotificationManager,
) {

    private val incognitoNotificationId = 1
    private val channelId = "channel_incognito"

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channelName = context.getString(R.string.notification_incognito_running_description)
        val channel =
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        channel.enableVibration(false)
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Shows the notification for the provided [number] of tabs. If a notification already exists,
     * it will be updated.
     *
     * @param number the number of tabs, must be > 0.
     */
    fun show(number: Int) {
        require(number > 0)
        val incognitoIntent = IncognitoActivity.createIntent(context)

        val incognitoNotification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_incognito_24)
            .setContentTitle(context.resources.getQuantityString(R.plurals.notification_incognito_running_title,
                number,
                number))
            .setContentIntent(PendingIntent.getActivity(context,
                0,
                incognitoIntent,
                FLAG_IMMUTABLE))
            .setContentText(context.getString(R.string.notification_incognito_running_message))
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        notificationManager.notify(incognitoNotificationId, incognitoNotification)
    }

    /**
     * Hides the current notification if there is one.
     */
    fun hide() = notificationManager.cancel(incognitoNotificationId)

}
