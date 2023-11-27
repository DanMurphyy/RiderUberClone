package com.danmurphyy.rideruberclone

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.danmurphyy.rideruberclone.databinding.ActivityRequestDriverBinding
import com.danmurphyy.rideruberclone.model.DeclineRequestAndRemoveFromDriver
import com.danmurphyy.rideruberclone.model.DriverGeoModel
import com.danmurphyy.rideruberclone.model.DeclineRequestFromDriver
import com.danmurphyy.rideruberclone.model.DriverAcceptTripEvent
import com.danmurphyy.rideruberclone.model.SelectedPlaceEvent
import com.danmurphyy.rideruberclone.model.TripPlanModel
import com.danmurphyy.rideruberclone.model.DriverCompleteTripEvent
import com.danmurphyy.rideruberclone.model.LoadTripDetailsEvent
import com.danmurphyy.rideruberclone.remote.IGoogleAPI
import com.danmurphyy.rideruberclone.remote.RetrofitInstance
import com.danmurphyy.rideruberclone.utils.Constants
import com.danmurphyy.rideruberclone.utils.UserUtils
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.SquareCap
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.maps.android.SphericalUtil
import com.google.maps.android.ui.IconGenerator
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject

class RequestDriverActivity : AppCompatActivity(), OnMapReadyCallback {

    private var driverOldPosition: String = ""
    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityRequestDriverBinding
    private var selectedPlaceEvent: SelectedPlaceEvent? = null
    private var declineRequestFromDriver: DeclineRequestFromDriver? = null
    private lateinit var mapFragment: SupportMapFragment
    private val markerUpdateScope = CoroutineScope(Dispatchers.Default)

    //Spinning animation
    private var animator: ValueAnimator? = null
    private val DESIRED_NUM_OF_SPINS = 5
    private val DESIRED_SECONDS_PER_ONE_FULL_360_SPIN = 40

    //Effect
    private var lastUserCircle: Circle? = null
    private val duration = 1000
    private var lastPulseAnimator: ValueAnimator? = null

    //Routes
    private val compositeDisposable = CompositeDisposable()
    private lateinit var googleAPI: IGoogleAPI
    private var blackPolyLine: Polyline? = null
    private var greyPolyLine: Polyline? = null
    private var polylineOptions: PolylineOptions? = null
    private var blackPolyLineOptions: PolylineOptions? = null
    private var polylineList: MutableList<LatLng>? = null
    private var originMarker: Marker? = null
    private var destinationMarker: Marker? = null

    private lateinit var textOrigin: TextView
    private lateinit var textAddressPickup: TextView

    private var lastDriverCall: DriverGeoModel? = null

    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onSelectedPlaceEvent(event: SelectedPlaceEvent) {
        selectedPlaceEvent = event
    }

    override fun onStop() {
        compositeDisposable.clear()
        if (EventBus.getDefault().hasSubscriberForEvent(SelectedPlaceEvent::class.java)) {
            EventBus.getDefault().removeStickyEvent(SelectedPlaceEvent::class.java)
        }
        if (EventBus.getDefault().hasSubscriberForEvent(DeclineRequestFromDriver::class.java)) {
            EventBus.getDefault().removeStickyEvent(DeclineRequestFromDriver::class.java)
        }
        if (EventBus.getDefault().hasSubscriberForEvent(DriverAcceptTripEvent::class.java)) {
            EventBus.getDefault().removeStickyEvent(DriverAcceptTripEvent::class.java)
        }
        if (EventBus.getDefault()
                .hasSubscriberForEvent(DeclineRequestAndRemoveFromDriver::class.java)
        ) {
            EventBus.getDefault().removeStickyEvent(DeclineRequestAndRemoveFromDriver::class.java)
        }
        if (EventBus.getDefault()
                .hasSubscriberForEvent(DriverCompleteTripEvent::class.java)
        ) {
            EventBus.getDefault().removeStickyEvent(DriverCompleteTripEvent::class.java)
        }
        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onDriverAcceptTripEvent(event: DriverAcceptTripEvent) {
        FirebaseDatabase.getInstance().getReference(Constants.TRIPS)
            .child(event.tripId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val tripPlanModel = snapshot.getValue(TripPlanModel::class.java)
                        mMap.clear()
                        binding.fillMaps.visibility = View.GONE
                        if (animator != null) animator!!.end()
                        val cameraPos = CameraPosition.Builder().target(mMap.cameraPosition.target)
                            .tilt(0f).zoom(mMap.cameraPosition.zoom).build()
                        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPos))

                        //get routes
                        val driverLocation = StringBuilder()
                            .append(tripPlanModel!!.currentLat)
                            .append(",")
                            .append(tripPlanModel.currentLng)
                            .toString()

                        compositeDisposable.add(
                            googleAPI.getDirections(
                                "driving",
                                "less_driving",
                                tripPlanModel.origin, driverLocation, getString(R.string.google_api)
                            )
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe { returnResult ->
                                    val blackPolyLineOptions: PolylineOptions?
                                    var polylineList: List<LatLng?>? = null
                                    var blackPolyLine: Polyline? = null
                                    try {

                                        val jsonObject = JSONObject(returnResult)
                                        val jsonArray = jsonObject.getJSONArray("routes")
                                        for (i in 0 until jsonArray.length()) {
                                            val route = jsonArray.getJSONObject(i)
                                            val poly = route.getJSONObject("overview_polyline")
                                            val polyLine = poly.getString("points")
                                            polylineList = Constants.decodePoly(polyLine)
                                        }

                                        blackPolyLineOptions = PolylineOptions()
                                        blackPolyLineOptions.color(Color.BLACK)
                                        blackPolyLineOptions.width(5f)
                                        blackPolyLineOptions.startCap(SquareCap())
                                        blackPolyLineOptions.jointType(JointType.ROUND)
                                        blackPolyLineOptions.addAll(polylineList!!)
                                        blackPolyLine = mMap.addPolyline(blackPolyLineOptions)

                                        //add car icon for origin
                                        val objects = jsonArray.getJSONObject(0)
                                        val legs = objects.getJSONArray("legs")
                                        val legsObject = legs.getJSONObject(0)
                                        val time = legsObject.getJSONObject("duration")
                                        val duration = time.getString("text")
                                        val origin = LatLng(
                                            tripPlanModel.origin!!.split(",")[0].toDouble(),
                                            tripPlanModel.origin!!.split(",")[1].toDouble()
                                        )
                                        val destination = LatLng(
                                            tripPlanModel.currentLat,
                                            tripPlanModel.currentLng
                                        )
                                        val latLngBound = LatLngBounds.Builder()
                                            .include(origin)
                                            .include(destination)
                                            .build()
                                        addPickupMarkerWithDuration(duration, origin)
                                        addDriverMarker(destination)
                                        mMap.moveCamera(
                                            CameraUpdateFactory.newLatLngBounds(
                                                latLngBound,
                                                160
                                            )
                                        )
                                        mMap.moveCamera(CameraUpdateFactory.zoomTo(mMap.cameraPosition.zoom - 1))
                                        initDriverForMoving(event.tripId, tripPlanModel)

                                        //load driver avatar
                                        Glide.with(this@RequestDriverActivity)
                                            .load(tripPlanModel.driverInfoModel!!.avatar)
                                            .into(binding.imgDriver)
                                        binding.txtDriverName.text =
                                            tripPlanModel.driverInfoModel!!.firstName
                                        binding.confirmUberLayout.visibility = View.GONE
                                        binding.confirmPickupLayout.visibility = View.GONE
                                        binding.findingYourRiderLayout.visibility = View.GONE
                                        binding.driverInfoLayout.visibility = View.VISIBLE

                                    } catch (e: Exception) {
                                        Snackbar.make(
                                            mapFragment.requireView(),
                                            e.message!!,
                                            Snackbar.LENGTH_LONG
                                        )
                                            .show()
                                    }
                                }
                        )


                    } else {
                        Snackbar.make(
                            binding.mainLayout,
                            getString(R.string.trip_not_found) + event.tripId,
                            Snackbar.LENGTH_LONG
                        ).show()

                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Snackbar.make(binding.mainLayout, error.message, Snackbar.LENGTH_LONG).show()
                }

            })
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onDriverCompleteTripEvent(event: DriverCompleteTripEvent) {
        Constants.showNotification(
            applicationContext,
            "Thank you",
            "Your trip ${event.tripId} has been completed",
            null
        )
        EventBus.getDefault().postSticky(LoadTripDetailsEvent(event.tripId))
        startActivity(Intent(this, TripDetailActivity::class.java))
        finish()
    }

    private fun initDriverForMoving(tripId: String, tripPlanModel: TripPlanModel) {
        driverOldPosition = StringBuilder()
            .append(tripPlanModel.currentLat)
            .append(",")
            .append(tripPlanModel.currentLng)
            .toString()

        FirebaseDatabase.getInstance().getReference(Constants.TRIPS)
            .child(tripId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val newData = snapshot.getValue(TripPlanModel::class.java)
                    if (newData != null) {
                        val driverNewPosition = StringBuilder()
                            .append(newData.currentLat)
                            .append(",")
                            .append(newData.currentLng)
                            .toString()
                        if (driverOldPosition != driverNewPosition) //not equals
                            moveMarkerAnimation(
                                destinationMarker!!,
                                driverOldPosition,
                                driverNewPosition
                            )
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Snackbar.make(binding.mainLayout, error.message, Snackbar.LENGTH_LONG).show()

                }

            })

    }

    private fun moveMarkerAnimation(
        marker: Marker,
        from: String,
        to: String,
    ) {
        markerUpdateScope.launch {
            withContext(Dispatchers.Main) {

                // Retrofit request to get the route information from old to new position
                compositeDisposable.add(
                    googleAPI.getDirections(
                        "driving",
                        "less_driving",
                        from,
                        to,
                        getString(R.string.google_api)
                    )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { returnResult ->
                            try {
                                val jsonObject = JSONObject(returnResult)
                                val jsonArray = jsonObject.getJSONArray("routes")
                                if (jsonArray.length() > 0) {
                                    val route = jsonArray.getJSONObject(0)
                                    val poly = route.getJSONObject("overview_polyline")
                                    val polyLine = poly.getString("points")
                                    val decodedPolyline = Constants.decodePoly(polyLine)

                                    // Animate the driver's marker along the route
                                    animateMarkerAlongRoute(marker, decodedPolyline)
                                }
                            } catch (e: Exception) {
                                // Handle the exception
                                Snackbar.make(
                                    mapFragment.requireView(),
                                    e.message!!,
                                    Snackbar.LENGTH_LONG
                                )
                                    .show()
                            }
                        }
                )
            }
        }
    }

    private fun animateMarkerAlongRoute(marker: Marker?, decodedPolyline: List<LatLng>) {
        val valueAnimator = ValueAnimator.ofFloat(0f, 1f)
        valueAnimator.duration = 3000
        valueAnimator.interpolator = LinearInterpolator()

        valueAnimator.addUpdateListener { animation ->
            val v = animation.animatedValue as Float
            val index = (decodedPolyline.size * v).toInt()
            if (index < decodedPolyline.size - 1) {
                val startPosition = decodedPolyline[index]
                val endPosition = decodedPolyline[index + 1]
                val interpolatedPosition = SphericalUtil.interpolate(
                    startPosition,
                    endPosition,
                    v.toDouble()
                )
                marker?.position = interpolatedPosition
                marker?.rotation = Constants.getBearing(startPosition, endPosition)
            }
        }
        valueAnimator.start()
    }

    private fun addDriverMarker(destination: LatLng) {
        destinationMarker = mMap.addMarker(
            MarkerOptions().position(destination).flat(true)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
        )

    }

    private fun addPickupMarkerWithDuration(duration: String, origin: LatLng) {
        val icon = Constants.createIconWithDuration(this@RequestDriverActivity, duration)!!
        originMarker = mMap.addMarker(
            MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(icon)).position(origin)
        )
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onDeclineReceived(event: DeclineRequestFromDriver) {
        declineRequestFromDriver = event

        if (lastDriverCall != null) {
            Constants.driversFound[lastDriverCall!!.key]!!.isDecline = true
            findNearbyDriver(selectedPlaceEvent!!)
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onDeclineAndRemoveReceived(event: DeclineRequestAndRemoveFromDriver) {
        if (lastDriverCall != null) {
            if (Constants.driversFound[lastDriverCall!!.key] != null) {
                Constants.driversFound[lastDriverCall!!.key]!!.isDecline = true
            }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRequestDriverBinding.inflate(layoutInflater)
        setContentView(binding.root)
        init()
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun init() {
        googleAPI = RetrofitInstance.instance!!.create(IGoogleAPI::class.java)

        binding.btnConfirmUber.setOnClickListener {
            textAddressPickup =
                binding.confirmPickupLayout.findViewById(R.id.text_view_address_pickup)
            binding.confirmPickupLayout.visibility = View.VISIBLE
            binding.confirmUberLayout.visibility = View.GONE
            setDataPickup()
        }

        binding.btnConfirmPickup.setOnClickListener {
            if (selectedPlaceEvent == null) return@setOnClickListener
            mMap.clear()

            //Tilt
            val cameraPos = CameraPosition.Builder().target(selectedPlaceEvent!!.origin)
                .tilt(45f)
                .zoom(16f)
                .build()

            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPos))

            //Start animation
            addMarkerWithPulseAnimation()
        }

    }

    private fun addMarkerWithPulseAnimation() {
        binding.confirmPickupLayout.visibility = View.GONE
        binding.fillMaps.visibility = View.VISIBLE
        binding.findingYourRiderLayout.visibility = View.VISIBLE

        originMarker = mMap.addMarker(
            MarkerOptions().icon(BitmapDescriptorFactory.defaultMarker())
                .position(selectedPlaceEvent!!.origin)
        )

        addPulsatingEffect(selectedPlaceEvent!!)
    }

    private fun addPulsatingEffect(selectedPlaceEvent: SelectedPlaceEvent?) {
        if (lastPulseAnimator != null) lastPulseAnimator!!.cancel()
        if (lastUserCircle != null) lastUserCircle!!.center = selectedPlaceEvent!!.origin
        lastPulseAnimator = Constants.valueAnimate(duration.toLong()) { animation ->
            if (lastUserCircle != null) lastUserCircle!!.radius =
                animation.animatedValue.toString().toDouble() else {
                lastUserCircle = mMap.addCircle(
                    CircleOptions()
                        .center(selectedPlaceEvent!!.origin)
                        .radius(animation.animatedValue.toString().toDouble())
                        .strokeColor(Color.WHITE)
                        .fillColor(
                            ContextCompat.getColor(
                                this@RequestDriverActivity,
                                R.color.map_darker
                            )
                        )
                )
            }
        }

        //Start rotating camera
        startMapCameraSpinningAnimation(selectedPlaceEvent!!)
    }

    private fun startMapCameraSpinningAnimation(selectedPlaceEvent: SelectedPlaceEvent?) {
        if (animator != null) animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, (DESIRED_NUM_OF_SPINS * 360.toFloat()))
        animator!!.duration = (DESIRED_SECONDS_PER_ONE_FULL_360_SPIN * 2000).toLong()
        animator!!.interpolator = LinearInterpolator()
        animator!!.startDelay = 100
        animator!!.addUpdateListener { valueAnimator ->
            val newBearingValue = valueAnimator.animatedValue as Float
            mMap.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(selectedPlaceEvent!!.origin)
                        .zoom(16f)
                        .tilt(45f)
                        .bearing(newBearingValue)
                        .build()
                )
            )
        }

        animator!!.start()
        findNearbyDriver(selectedPlaceEvent)

    }

    private fun findNearbyDriver(selectedPlaceEvent: SelectedPlaceEvent?) {
        if (Constants.driversFound.isEmpty()) {
            Snackbar.make(
                mapFragment.requireView(),
                getString(R.string.drivers_are_not_found),
                Snackbar.LENGTH_LONG
            ).show()
            lastDriverCall = null
            finish()
            return
        }

        val currentRiderLocation = Location("").apply {
            latitude = selectedPlaceEvent!!.origin.latitude
            longitude = selectedPlaceEvent.origin.longitude
        }

        var minDistance = Float.MAX_VALUE
        var foundDriver: DriverGeoModel? = null

        for ((key, driver) in Constants.driversFound) {
            if (!driver.isDecline) {
                val driverLocation = Location("").apply {
                    latitude = driver.geoLocation?.latitude ?: 0.0
                    longitude = driver.geoLocation?.longitude ?: 0.0
                }

                val distance = driverLocation.distanceTo(currentRiderLocation)

                if (distance < minDistance) {
                    minDistance = distance
                    foundDriver = driver
                }
            }
        }

        if (foundDriver != null) {
            UserUtils.sendRequestToDriver(
                this@RequestDriverActivity,
                binding.mainLayout,
                foundDriver,
                selectedPlaceEvent!!
            )
            lastDriverCall = foundDriver
        } else {
            Toast.makeText(this, getString(R.string.no_driver_accepted), Toast.LENGTH_LONG).show()
            lastDriverCall = null
            finish()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        drawPath(selectedPlaceEvent!!)
        //layoutButton
        //try to set map style
        try {
            val success = mMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this, R.raw.uber_maps_style)
            )
            if (!success) {
                Log.e("ErrorMap", "Map Style is not parsing")
            }
        } catch (e: Exception) {
            Log.e("ErrorMap", e.message!!)
        }
    }

    private fun drawPath(selectedPlaceEvent: SelectedPlaceEvent) {
        compositeDisposable.add(googleAPI.getDirections(
            "driving",
            "less_driving",
            selectedPlaceEvent.originString,
            selectedPlaceEvent.destinationString,
            getString(R.string.google_api)
        ).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe { returnResult ->
                Log.d("API_RETURN", returnResult)
                try {

                    val jsonObject = JSONObject(returnResult)
                    val jsonArray = jsonObject.getJSONArray("routes")
                    for (i in 0 until jsonArray.length()) {
                        val route = jsonArray.getJSONObject(i)
                        val poly = route.getJSONObject("overview_polyline")
                        val polyLine = poly.getString("points")
                        polylineList = Constants.decodePoly(polyLine)
                    }

                    polylineOptions = PolylineOptions()
                    polylineOptions!!.color(Color.GRAY)
                    polylineOptions!!.width(12f)
                    polylineOptions!!.startCap(SquareCap())
                    polylineOptions!!.jointType(JointType.ROUND)
                    polylineOptions!!.addAll(polylineList!!)
                    greyPolyLine = mMap.addPolyline(polylineOptions!!)

                    blackPolyLineOptions = PolylineOptions()
                    blackPolyLineOptions!!.color(Color.BLACK)
                    blackPolyLineOptions!!.width(5f)
                    blackPolyLineOptions!!.startCap(SquareCap())
                    blackPolyLineOptions!!.jointType(JointType.ROUND)
                    blackPolyLineOptions!!.addAll(polylineList!!)
                    blackPolyLine = mMap.addPolyline(blackPolyLineOptions!!)

                    //Animator
                    val valueAnimator = ValueAnimator.ofInt(0, 100)
                    valueAnimator.duration = 1100
                    valueAnimator.repeatCount = ValueAnimator.INFINITE
                    valueAnimator.interpolator = LinearInterpolator()
                    valueAnimator.addUpdateListener { _ ->
                        val points = greyPolyLine!!.points
                        val percentValue = valueAnimator.animatedValue.toString().toInt()
                        val size = points.size
                        val newPoints = (size * (percentValue / 100f)).toInt()
                        val p = points.subList(0, newPoints)
                        blackPolyLine!!.points = p
                    }
                    valueAnimator.start()

                    val latLngBound = LatLngBounds.Builder().include(selectedPlaceEvent.origin)
                        .include(selectedPlaceEvent.destination).build()

                    val objects = jsonArray.getJSONObject(0)
                    val legs = objects.getJSONArray("legs")
                    val legsObject = legs.getJSONObject(0)

                    val time = legsObject.getJSONObject("duration")
                    val duration = time.getString("text")
                    val durationValue = time.getInt("value")
                    val distance = legsObject.getJSONObject("distance")
                    val distanceText = distance.getString("text")
                    val distanceValue = distance.getInt("value")

                    val startAddress = legsObject.getString("start_address")
                    val endAddress = legsObject.getString("end_address")

                    val startLocation = legsObject.getJSONObject("start_location")
                    val endLocation = legsObject.getJSONObject("end_location")

                    //setValue
                    binding.txtDistance.text = distanceText
                    binding.txtTime.text = duration

                    //update here new info
                    selectedPlaceEvent.originAddress = startAddress
                    selectedPlaceEvent.origin =
                        LatLng(startLocation.getDouble("lat"), startLocation.getDouble("lng"))
                    selectedPlaceEvent.destinationAddress = endAddress
                    selectedPlaceEvent.destination =
                        LatLng(endLocation.getDouble("lat"), endLocation.getDouble("lng"))
                    selectedPlaceEvent.durationValue = durationValue
                    selectedPlaceEvent.distanceValue = distanceValue
                    selectedPlaceEvent.durationText = duration
                    selectedPlaceEvent.distanceText = distanceText
                    //CalculateFee
                    val fee = Constants.calculateFeeBaseOnMetres(distanceValue)
                    selectedPlaceEvent.totalFee = fee
                    binding.txtTotalFee.text = StringBuilder("$").append(fee)

                    addOriginMarker(duration, startAddress)
                    addDestinationMarker(endAddress)

                    mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(latLngBound, 160))
                    mMap.moveCamera(CameraUpdateFactory.zoomTo(mMap.cameraPosition.zoom - 1))

                } catch (e: Exception) {
                    Snackbar.make(mapFragment.requireView(), e.message!!, Snackbar.LENGTH_LONG)
                        .show()
                }
            })
    }

    private fun addDestinationMarker(endAddress: String) {
        val view = layoutInflater.inflate(R.layout.destination_info_window, null, false)

        val textDestination = view.findViewById<View>(R.id.text_destination) as TextView
        textDestination.text = Constants.formatAddress(endAddress)

        val generator = IconGenerator(this)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon = generator.makeIcon()

        destinationMarker = mMap.addMarker(
            MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(icon))
                .position(selectedPlaceEvent!!.destination)
        )
    }

    private fun addOriginMarker(duration: String, startAddress: String) {
        val view = layoutInflater.inflate(R.layout.origin_info_window, null, false)
        val textTime = view.findViewById<View>(R.id.text_time) as TextView
        textOrigin = view.findViewById<View>(R.id.text_origin) as TextView

        textTime.text = Constants.formatDuration(duration)
        textOrigin.text = Constants.formatAddress(startAddress)

        val generator = IconGenerator(this)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon = generator.makeIcon()

        originMarker = mMap.addMarker(
            MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(icon))
                .position(selectedPlaceEvent!!.origin)
        )
    }

    private fun setDataPickup() {
        binding.textViewAddressPickup.text = textOrigin.text
        mMap.clear()
        addPickupMarker()
    }

    private fun addPickupMarker() {
        val view = layoutInflater.inflate(R.layout.pickup_info_window, null, false)
        val generator = IconGenerator(this)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon = generator.makeIcon()
        originMarker = mMap.addMarker(
            MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(icon))
                .position(selectedPlaceEvent!!.origin)
        )
    }

}