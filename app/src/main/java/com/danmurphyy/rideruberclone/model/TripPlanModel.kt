package com.danmurphyy.rideruberclone.model

data class TripPlanModel(
    var rider: String? = null,
    var driver: String? = null,
    var driverInfoModel: DriverInfoModel? = null,
    var riderModel: RiderModel? = null,
    var origin: String? = null,
    var originString: String? = null,
    var destination: String? = null,
    var destinationString: String? = null,
    var distancePickUp: String? = null,
    var distanceDestination: String? = null,
    var durationDestination: String? = null,
    var durationPickUp: String? = null,
    var currentLat: Double = -1.0,
    var currentLng: Double = -1.0,
    var isDone: Boolean = false,
    var isCancel: Boolean = false,

    //value and fee
    var totalFee: Double = 0.0,
    var distanceValue: Int = 0,
    var durationValue: Int = 0,
    var distanceText: String = "",
    var durationText: String = "",
    var timeText: String = "",

    )
