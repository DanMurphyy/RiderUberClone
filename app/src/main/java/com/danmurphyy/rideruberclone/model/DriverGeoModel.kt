package com.danmurphyy.rideruberclone.model

import com.firebase.geofire.GeoLocation

data class DriverGeoModel(
    var key: String? = null,
    var geoLocation: GeoLocation? = null,
    var driverInfoModel: DriverInfoModel? = null,
    var isDecline: Boolean = false
) {
    constructor(key: String?, geoLocation: GeoLocation) : this() {
        this.key = key
        this.geoLocation = geoLocation
    }
}
