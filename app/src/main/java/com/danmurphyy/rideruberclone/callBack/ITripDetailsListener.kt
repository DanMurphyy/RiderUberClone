package com.danmurphyy.rideruberclone.callBack

import com.danmurphyy.rideruberclone.model.TripPlanModel

interface ITripDetailsListener {
    fun onTripDetailsLoadSuccess(tripPlanModel: TripPlanModel)
}