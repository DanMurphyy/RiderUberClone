package com.danmurphyy.rideruberclone.model

import com.google.android.gms.maps.model.LatLng

class SelectedPlaceEvent(
    var origin: LatLng,
    var destination: LatLng,
    var originAddress: String,
    var destinationAddress: String,
) {
    var distanceText: String? = ""
    var durationText: String? = ""
    var distanceValue: Int = 0
    var durationValue: Int = 0
    var totalFee: Double = 0.0

    val originString: String
        get() = StringBuilder()
            .append(origin.latitude)
            .append(",")
            .append(origin.longitude)
            .toString()

    val destinationString: String
        get() = StringBuilder()
            .append(destination.latitude)
            .append(",")
            .append(destination.longitude)
            .toString()
}