package com.danmurphyy.rideruberclone.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.danmurphyy.rideruberclone.R
import com.danmurphyy.rideruberclone.RiderHomeActivity
import com.danmurphyy.rideruberclone.model.DeclineRequestAndRemoveFromDriver
import com.danmurphyy.rideruberclone.model.DeclineRequestFromDriver
import com.danmurphyy.rideruberclone.model.DriverAcceptTripEvent
import com.danmurphyy.rideruberclone.model.DriverCompleteTripEvent
import com.danmurphyy.rideruberclone.utils.Constants
import com.danmurphyy.rideruberclone.utils.Constants.CHANNEL_ID
import com.danmurphyy.rideruberclone.utils.UserUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.greenrobot.eventbus.EventBus
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        if (FirebaseAuth.getInstance().currentUser != null)
            UserUtils.updateToken(this, token)
        super.onNewToken(token)
    }

    @SuppressLint("ObsoleteSdkInt")
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data

        if (data[Constants.NOTI_TITLE] != null) {
            if (data[Constants.NOTI_TITLE].equals(Constants.REQUEST_DRIVER_DECLINE)) {
                EventBus.getDefault().postSticky(DeclineRequestFromDriver())
            } else if (data[Constants.NOTI_TITLE].equals(Constants.REQUEST_DRIVER_ACCEPT)) {
                EventBus.getDefault().postSticky(DriverAcceptTripEvent(data[Constants.TRIP_KEY]!!))
            } else if (data[Constants.NOTI_TITLE].equals(Constants.REQUEST_DRIVER_DECLINE_AND_REMOVE_TRIP)) {
                EventBus.getDefault().postSticky(DeclineRequestAndRemoveFromDriver())
            } else if (data[Constants.NOTI_TITLE].equals(Constants.RIDER_REQUEST_COMPLETE_TRIP)) {
                val tripKey = data[Constants.TRIP_KEY]
                EventBus.getDefault().postSticky(DriverCompleteTripEvent(tripKey!!))
            }
        } else {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {

                val intent = Intent(this, RiderHomeActivity::class.java)
                val notificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notificationID = Random.nextInt()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    createNotificationChannel(notificationManager)
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                )
                // Play the custom sound
                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(message.data[Constants.NOTI_TITLE])
                    .setContentText(message.data[Constants.NOTI_BODY])
                    .setSmallIcon(R.drawable.ic_small_car)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()
                notificationManager.notify(notificationID, notification)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(notificationManager: NotificationManager) {
        val channelName = "channelNameRider"
        val channel = NotificationChannel(
            CHANNEL_ID,
            channelName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "New Orders"
            enableLights(true)
            lightColor = Color.GREEN
        }
        notificationManager.createNotificationChannel(channel)
    }
}