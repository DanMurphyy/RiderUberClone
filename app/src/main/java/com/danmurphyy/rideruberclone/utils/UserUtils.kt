package com.danmurphyy.rideruberclone.utils

import android.content.Context
import android.view.View
import android.widget.Toast
import com.danmurphyy.rideruberclone.R
import com.danmurphyy.rideruberclone.model.DriverGeoModel
import com.danmurphyy.rideruberclone.model.SelectedPlaceEvent
import com.danmurphyy.rideruberclone.model.TokenModel
import com.danmurphyy.rideruberclone.remote.FCMSendData
import com.danmurphyy.rideruberclone.remote.FCMService
import com.danmurphyy.rideruberclone.remote.RetrofitFCM
import com.danmurphyy.rideruberclone.utils.Constants.RIDER_INFO_REFERENCE
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers

object UserUtils {
    fun updateUse(context: Context, updateData: HashMap<String, Any>) {
        FirebaseDatabase.getInstance().getReference(RIDER_INFO_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .updateChildren(updateData)
            .addOnFailureListener {
                Toast.makeText(context, it.message!!, Toast.LENGTH_LONG).show()
            }
            .addOnSuccessListener {
                Toast.makeText(context, "Update information success", Toast.LENGTH_LONG).show()
            }
    }

    fun updateToken(context: Context, token: String) {
        val tokenModel = TokenModel()
        tokenModel.token = token

        FirebaseDatabase.getInstance()
            .getReference(Constants.TOKEN_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .setValue(tokenModel)
            .addOnFailureListener { e ->
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            }
            .addOnSuccessListener { }
    }

    fun sendRequestToDriver(
        context: Context,
        mainLayout: View?,
        foundDriver: DriverGeoModel?,
        selectedPlaceEvent: SelectedPlaceEvent,
    ) {
        val compositeDisposable = CompositeDisposable()
        val fcmService = RetrofitFCM.instance!!.create(FCMService::class.java)
        //Get token
        FirebaseDatabase.getInstance()
            .getReference(Constants.TOKEN_REFERENCE)
            .child(foundDriver!!.key!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val tokenModel = snapshot.getValue(TokenModel::class.java)
                        val notificationData: MutableMap<String, String> = HashMap()
                        notificationData[Constants.NOTI_TITLE] = Constants.REQUEST_DRIVER_TITLE
                        notificationData[Constants.NOTI_BODY] = "This is the notification body"
                        notificationData[Constants.RIDER_KEY] =
                            FirebaseAuth.getInstance().currentUser!!.uid
                        notificationData[Constants.PICKUP_LOCATION_STRING] =
                            selectedPlaceEvent.originAddress
                        notificationData[Constants.PICKUP_LOCATION] = StringBuilder()
                            .append(selectedPlaceEvent.origin.latitude)
                            .append(",")
                            .append(selectedPlaceEvent.origin.longitude)
                            .toString()
                        notificationData[Constants.DESTINATION_LOCATION_STRING] =
                            selectedPlaceEvent.originAddress
                        notificationData[Constants.DESTINATION_LOCATION] = StringBuilder()
                            .append(selectedPlaceEvent.destination.latitude)
                            .append(",")
                            .append(selectedPlaceEvent.destination.longitude)
                            .toString()

                        //NewInfo
                        notificationData[Constants.RIDER_DISTANCE_TEXT] =
                            selectedPlaceEvent.distanceText!!
                        notificationData[Constants.RIDER_DISTANCE_VALUE] =
                            selectedPlaceEvent.distanceValue.toString()
                        notificationData[Constants.RIDER_DURATION_TEXT] =
                            selectedPlaceEvent.durationText!!
                        notificationData[Constants.RIDER_DURATION_VALUE] =
                            selectedPlaceEvent.durationValue.toString()
                        notificationData[Constants.RIDER_TOTAL_FEE] =
                            selectedPlaceEvent.totalFee.toString()

                        val fcmSendData = FCMSendData(tokenModel!!.token, notificationData)
                        compositeDisposable.add(fcmService.sendNotification(fcmSendData)
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({ fcmResponse ->
                                if (fcmResponse.success == 0) {
                                    compositeDisposable.clear()
                                    Snackbar.make(
                                        mainLayout!!,
                                        context.getString(R.string.send_request_to_driver_failed),
                                        Snackbar.LENGTH_LONG
                                    ).show()
                                }
                            },
                                { t: Throwable? ->
                                    compositeDisposable.clear()
                                    Snackbar.make(
                                        mainLayout!!,
                                        t!!.message!!,
                                        Snackbar.LENGTH_LONG
                                    ).show()
                                }
                            ))

                    } else {
                        Snackbar.make(
                            mainLayout!!,
                            context.getString(R.string.token_not_found),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Snackbar.make(mainLayout!!, error.message, Snackbar.LENGTH_LONG).show()
                }

            })
    }
}