package com.danmurphyy.rideruberclone.remote

import io.reactivex.Observable
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface FCMService {
    @Headers(
        "Content-Type:application/json",
        "Authorization: key=AAAACK8pQzY:APA91bEFoKMhdpr5im9Pzm_b768RuJlHNhd1yvz6-CGIbLhdn5g-ep9ttX3hDryA8j-6fZ_3jyTVr6wojytKNogFPNk3jEtbcMcLgPyGZprnUfRaKSxF57yh9JRmfncKndp-IbZ0GWRz"
    )
    @POST("fcm/send")
    fun sendNotification(@Body body: FCMSendData?): Observable<FCMResponse>
}