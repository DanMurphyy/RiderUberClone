package com.danmurphyy.rideruberclone.remote

class FCMResponse {
    var multicastId: Long = 0
    var success = 0
    var failure = 0
    var canonicalIds = 0
    var results: List<FCMResult>? = null
    var messageId: Long = 0
}