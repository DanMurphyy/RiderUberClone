package com.danmurphyy.rideruberclone.remote

import io.reactivex.Single
import retrofit2.http.GET
import retrofit2.http.Query

interface IGoogleAPI {
    @GET("maps/api/directions/json")
    fun getDirections(
        @Query("mode") mode: String?,
        @Query("transit_routing_preference") transitRouting: String?,
        @Query("origin") from: String?,
        @Query("destination") to: String?,
        @Query("key") key: String,
    ): Single<String>
}