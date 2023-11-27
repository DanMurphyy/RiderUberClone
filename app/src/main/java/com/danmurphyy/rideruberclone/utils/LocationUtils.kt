package com.danmurphyy.rideruberclone.utils

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.text.TextUtils
import java.io.IOException
import java.util.Locale

object LocationUtils {
    fun getAddressFromLocation(context: Context?, location: Location): String {
        val result = StringBuilder()
        val geoCoder = Geocoder(context!!, Locale.getDefault())
        val addressList: List<Address>?

        return try {
            addressList = geoCoder.getFromLocation(location.latitude, location.longitude, 1)
            if (!addressList.isNullOrEmpty()) {
                if (addressList[0].locality != null && !TextUtils.isEmpty(addressList[0].locality)) {
                    // If the address has a city field, use locality
                    result.append(addressList[0].locality)
                } else if (addressList[0].subAdminArea != null && !TextUtils.isEmpty(addressList[0].subAdminArea)) {
                    // If it doesn't have a city field, look for subAdminArea
                    result.append(addressList[0].subAdminArea)
                } else if (addressList[0].adminArea != null && !TextUtils.isEmpty(addressList[0].adminArea)) {
                    // If it doesn't have subAdminArea, look for adminArea
                    result.append(addressList[0].adminArea)
                } else {
                    // If it doesn't have subAdminArea or adminArea, use countryName
                    result.append(addressList[0].countryName)
                }
                result.append(addressList[0].countryCode)
            }
            result.toString()
        } catch (e: IOException) {
            e.printStackTrace() // Handle the exception appropriately in your application
            result.toString()
        }
    }
}