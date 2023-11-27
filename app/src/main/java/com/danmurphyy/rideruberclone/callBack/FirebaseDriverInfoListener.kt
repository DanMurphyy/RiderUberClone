package com.danmurphyy.rideruberclone.callBack

import com.danmurphyy.rideruberclone.model.DriverGeoModel

interface FirebaseDriverInfoListener {
    fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?)
}