package com.danmurphyy.rideruberclone.remote

import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory


object RetrofitInstance {
    val instance: Retrofit? = null
        get() = if (field == null) Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build() else field
}