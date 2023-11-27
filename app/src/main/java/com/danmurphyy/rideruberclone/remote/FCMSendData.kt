package com.danmurphyy.rideruberclone.remote

data class FCMSendData(
    var to: String,
    var data: Map<String, String>,
)
