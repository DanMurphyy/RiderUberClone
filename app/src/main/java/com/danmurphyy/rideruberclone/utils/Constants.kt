package com.danmurphyy.rideruberclone.utils

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.danmurphyy.rideruberclone.R
import com.danmurphyy.rideruberclone.model.AnimationModel
import com.danmurphyy.rideruberclone.model.DriverGeoModel
import com.danmurphyy.rideruberclone.model.RiderModel
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.firebase.auth.FirebaseAuth
import com.google.maps.android.ui.IconGenerator
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.atan
import kotlin.random.Random


object Constants {

    const val BASE_FARE: Double = 2.0
    const val RIDER_TOTAL_FEE = "TotalFeeRider"
    const val RIDER_DISTANCE_TEXT = "DistanceRider"
    const val RIDER_DURATION_TEXT = "DurationRider"
    const val RIDER_DISTANCE_VALUE = "DistanceRiderValue"
    const val RIDER_DURATION_VALUE = "DurationRiderValue"
    const val RIDER_REQUEST_COMPLETE_TRIP: String = "RequestCompleteTripToRider"
    const val REQUEST_DRIVER_DECLINE_AND_REMOVE_TRIP: String = "DeclineAndRemoveTrip"
    const val TRIP_KEY: String = "TripKey"
    const val TRIPS: String = "Trips"
    const val REQUEST_DRIVER_ACCEPT: String = "Accept"
    const val DESTINATION_LOCATION: String = "DestinationLocation"
    const val DESTINATION_LOCATION_STRING: String = "DestinationLocationString"
    const val PICKUP_LOCATION_STRING: String = "PickupLocationString"
    val driverSubscribe = HashMap<String, AnimationModel>()
    const val REQUEST_DRIVER_DECLINE = "Decline"
    const val RIDER_KEY: String = "RiderKey"
    const val PICKUP_LOCATION: String = "PickIupLocation"
    const val REQUEST_DRIVER_TITLE: String = "RequestDriver"
    const val NOTI_BODY: String = "body"
    const val NOTI_TITLE: String = "title"
    var markerList = HashMap<String, Marker>()
    val driversFound: HashMap<String, DriverGeoModel> = HashMap()
    const val DRIVER_INFO_REFERENCE = "DriverInfo"
    const val RIDER_INFO_REFERENCE: String = "Riders"
    const val CHANNEL_ID = "myChannelRider"
    const val DRIVERS_LOCATION_REFERENCES: String = "DriversLocation"
    var currentRider: RiderModel? = null
    const val TOKEN_REFERENCE: String = "Token"

    fun buildWelcomeMessage(): String {
        return if (currentRider != null) {
            StringBuilder("Welcome, ")
                .append(currentRider!!.firstName)
                .append(" ")
                .append(currentRider!!.lastName)
                .toString()
        } else {
            "Welcome"
        }
    }

    fun buildName(firstName: String, lastName: String): String {
        return StringBuilder(firstName).append("").append(lastName).toString()
    }

    //GET BEARING
    fun getBearing(begin: LatLng, end: LatLng): Float {
        //You can copy this function by link at description
        val lat: Double = abs(begin.latitude - end.latitude)
        val lng: Double = abs(begin.longitude - end.longitude)
        if (begin.latitude < end.latitude && begin.longitude < end.longitude) return Math.toDegrees(
            atan(lng / lat)
        )
            .toFloat() else if (begin.latitude >= end.latitude && begin.longitude < end.longitude) return (90 - Math.toDegrees(
            atan(lng / lat)
        ) + 90).toFloat() else if (begin.latitude >= end.latitude && begin.longitude >= end.longitude) return (Math.toDegrees(
            atan(lng / lat)
        ) + 180).toFloat() else if (begin.latitude < end.latitude && begin.longitude >= end.longitude) return (90 - Math.toDegrees(
            atan(lng / lat)
        ) + 270).toFloat()
        return (-1).toFloat()
    }

    //DECODE POLY
    fun decodePoly(encoded: String): MutableList<LatLng> {
        val poly: MutableList<LatLng> = ArrayList()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }
        return poly
    }

    fun setWelcomeMessage(textWelcome: TextView?) {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 1..12 -> {
                textWelcome?.text = StringBuilder("Good morning")
            }

            in 13..17 -> {
                textWelcome?.text = java.lang.StringBuilder("Good afternoon")
            }

            else -> {
                textWelcome?.text = StringBuilder("Good evening")
            }
        }
    }

    fun formatDuration(duration: String): CharSequence {
        return if (duration.contains("mins")) {
            duration.substring(0, duration.length - 1)
        } else {
            duration
        }
    }

    fun formatAddress(address: String): String {
        val commaIndex = address.indexOf(',')
        val secondCommaIndex = address.indexOf(',', commaIndex + 1)
        if (commaIndex != -1) {
            return address.substring(commaIndex + 1, secondCommaIndex).trim()
        }
        return address
    }

    fun valueAnimate(
        duration: Long,
        listener: ValueAnimator.AnimatorUpdateListener?,
    ): ValueAnimator {
        val va = ValueAnimator.ofFloat(0f, 100f)
        va.duration = duration
        va.addUpdateListener(listener)
        va.repeatCount = ValueAnimator.INFINITE
        va.repeatMode = ValueAnimator.RESTART
        va.start()

        return va
    }

    @SuppressLint("InflateParams")
    fun createIconWithDuration(
        context: Context,
        duration: String,
    ): Bitmap? {
        val view =
            LayoutInflater.from(context).inflate(R.layout.pick_info_with_duration_windows, null)
        val txtTime = view.findViewById<View>(R.id.txt_duration) as TextView
        txtTime.text = getNumberFromText(duration)
        val generator = IconGenerator(context)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        return generator.makeIcon()
    }

    private fun getNumberFromText(s: String): String {
        return s.substring(0, s.indexOf(""))
    }

    @SuppressLint("ObsoleteSdkInt")
    @RequiresApi(Build.VERSION_CODES.O)
    fun showNotification(context: Context, title: String?, body: String?, intent: Intent?) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val notificationManager =
                context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val notificationID = Random.nextInt()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Create notification channel
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

            // Check if intent is not null to create PendingIntent
            val pendingIntent = if (intent != null) {
                PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                )
            } else {
                null // Set pendingIntent to null if intent is null
            }

            // Play the custom sound
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(R.drawable.ic_small_car)
                .setAutoCancel(true)
                .apply {
                    if (pendingIntent != null) {
                        setContentIntent(pendingIntent)
                    }
                }
                .build()

            notificationManager.notify(notificationID, notification)
        }
    }

    fun calculateFeeBaseOnMetres(metres: Int): Double {
        return if (metres <= 1000) //less than or equal 1km
            BASE_FARE
        else
            (BASE_FARE / 1000) * metres
    }

}
