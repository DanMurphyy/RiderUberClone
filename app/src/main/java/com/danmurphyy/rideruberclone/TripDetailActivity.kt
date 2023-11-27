package com.danmurphyy.rideruberclone

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import com.danmurphyy.rideruberclone.callBack.FirebaseFailedListener
import com.danmurphyy.rideruberclone.callBack.ITripDetailsListener
import com.danmurphyy.rideruberclone.databinding.ActivityTripDetailBinding
import com.danmurphyy.rideruberclone.model.LoadTripDetailsEvent
import com.danmurphyy.rideruberclone.model.TripPlanModel
import com.danmurphyy.rideruberclone.utils.Constants
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import io.reactivex.disposables.CompositeDisposable
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class TripDetailActivity : AppCompatActivity(), ITripDetailsListener, FirebaseFailedListener {
    private lateinit var binding: ActivityTripDetailBinding

    private val compositeDisposable = CompositeDisposable()
    lateinit var tripDetailsListener: ITripDetailsListener
    lateinit var firebaseFailedListener: FirebaseFailedListener

    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripDetailBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        init()

    }

    private fun init() {
        tripDetailsListener = this
        firebaseFailedListener = this

        binding.btnBack.setOnClickListener {
            binding.layoutDetails.visibility = View.GONE
            binding.progressRing.visibility = View.VISIBLE
            finish()
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onLoadTripDetailsEvent(event: LoadTripDetailsEvent) {
        Log.d("TripDetailActivityLog", "Received LoadTripDetailsEvent")

        FirebaseDatabase.getInstance()
            .getReference(Constants.TRIPS)
            .child(event.tripId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val model = snapshot.getValue(TripPlanModel::class.java)
                        tripDetailsListener.onTripDetailsLoadSuccess(model!!)
                        Log.d("TripDetailActivityLog", "$model")
                    } else {
                        firebaseFailedListener.onFirebaseFailed("Cannot find Trip key")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    firebaseFailedListener.onFirebaseFailed(error.message)
                }

            })
    }


    override fun onTripDetailsLoadSuccess(tripPlanModel: TripPlanModel) {
        Log.d("TripDetailActivityLog", "ovveride onTripDetailsLoadSuccess")
        binding.txtDate.text = tripPlanModel.timeText
        binding.txtPrice.text = StringBuilder("$").append(tripPlanModel.totalFee)
        binding.txtOriginTrip.text = tripPlanModel.originString
        binding.txtDestinationTrip.text = tripPlanModel.destinationString
        binding.txtBaseFare.text = StringBuilder("$").append(Constants.BASE_FARE)
        binding.txtDistanceTrip.text = tripPlanModel.distanceText
        binding.txtTimeTrip.text = tripPlanModel.durationText
        //show layout
        binding.layoutDetails.visibility = View.VISIBLE
        binding.progressRing.visibility = View.GONE
    }

    override fun onFirebaseFailed(message: String) {
        Snackbar.make(binding.detailMainLayout, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onStop() {
        compositeDisposable.clear()
        if (EventBus.getDefault().hasSubscriberForEvent(LoadTripDetailsEvent::class.java)) {
            EventBus.getDefault().removeStickyEvent(LoadTripDetailsEvent::class.java)
        }
        EventBus.getDefault().unregister(this)
        super.onStop()
    }
}