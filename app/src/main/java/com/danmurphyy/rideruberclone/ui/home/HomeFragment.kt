package com.danmurphyy.rideruberclone.ui.home

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.danmurphyy.rideruberclone.R
import com.danmurphyy.rideruberclone.RequestDriverActivity
import com.danmurphyy.rideruberclone.callBack.FirebaseDriverInfoListener
import com.danmurphyy.rideruberclone.callBack.FirebaseFailedListener
import com.danmurphyy.rideruberclone.databinding.FragmentHomeBinding
import com.danmurphyy.rideruberclone.model.DriverGeoModel
import com.danmurphyy.rideruberclone.model.DriverInfoModel
import com.danmurphyy.rideruberclone.model.GeoQueryModel
import com.danmurphyy.rideruberclone.model.SelectedPlaceEvent
import com.danmurphyy.rideruberclone.remote.IGoogleAPI
import com.danmurphyy.rideruberclone.remote.RetrofitInstance
import com.danmurphyy.rideruberclone.utils.Constants
import com.danmurphyy.rideruberclone.utils.LocationUtils
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.maps.android.SphericalUtil
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class HomeFragment : Fragment(), OnMapReadyCallback, FirebaseDriverInfoListener {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var mMap: GoogleMap
    private var _mapFragment: SupportMapFragment? = null
    private var isNextLaunch: Boolean = false

    //location
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    //Load driver
    private var distance = 1.0
    private val LIMIT_RANGE = 10.0
    private var previousLocation: Location? = null
    private var currentLocation: Location? = null
    private var firstTime = true
    private var cityName = ""

    //listener
    lateinit var iFirebaseDriverLocationListener: FirebaseDriverInfoListener
    lateinit var iFirebaseFailedListener: FirebaseFailedListener
    private val compositeDisposable = CompositeDisposable()
    private lateinit var iGoogleAPI: IGoogleAPI

    //initView()
    private lateinit var slidingUpPanelLayout: SlidingUpPanelLayout
    private lateinit var textWelcome: TextView
    private lateinit var autocompleteSupportFragment: AutocompleteSupportFragment
    private val markerUpdateScope = CoroutineScope(Dispatchers.Default)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root
        _mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        _mapFragment!!.getMapAsync(this)
        init()
        initView(root)

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        if (isNextLaunch) {
            loadAvailableDrivers()
        } else
            isNextLaunch = true
    }

    private fun initView(root: View) {
        slidingUpPanelLayout = root.findViewById(R.id.sliding_layout)
        textWelcome = root.findViewById(R.id.text_welcome)

        Constants.setWelcomeMessage(textWelcome)
    }

    private fun init() {
        Places.initialize(requireContext(), getString(R.string.google_api))
        autocompleteSupportFragment =
            childFragmentManager.findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment
        autocompleteSupportFragment.setPlaceFields(
            listOf(
                Place.Field.ID,
                Place.Field.ADDRESS,
                Place.Field.LAT_LNG,
                Place.Field.NAME
            )
        )

        autocompleteSupportFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onError(p0: Status) {
                val errorMessage = p0.statusMessage ?: "Unknown error"
                Snackbar.make(requireView(), errorMessage, Snackbar.LENGTH_LONG).show()
            }

            override fun onPlaceSelected(destinationLocation: Place) {
                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Snackbar.make(
                        requireView(),
                        getString(R.string.permission_required),
                        Snackbar.LENGTH_LONG
                    ).show()
                    return
                }
                fusedLocationProviderClient.lastLocation
                    .addOnSuccessListener { location ->
                        val origin = LatLng(location.latitude, location.longitude)
                        val destination = LatLng(
                            destinationLocation.latLng!!.latitude,
                            destinationLocation.latLng!!.longitude
                        )
                        startActivity(Intent(requireContext(), RequestDriverActivity::class.java))
                        EventBus.getDefault().postSticky(
                            SelectedPlaceEvent(
                                origin,
                                destination, "",
                                destinationLocation.address!!
                            )
                        )
                    }
            }

        })

        iFirebaseDriverLocationListener = this
        iGoogleAPI = RetrofitInstance.instance!!.create(IGoogleAPI::class.java)
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000).apply {
            setWaitForAccurateLocation(false)
            setMinUpdateIntervalMillis(LocationRequest.Builder.IMPLICIT_MIN_UPDATE_INTERVAL)
            setMaxUpdateDelayMillis(5000)
            setMinUpdateDistanceMeters(10f)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                //set the location on map
                val newPos = LatLng(
                    locationResult.lastLocation!!.latitude, locationResult.lastLocation!!.longitude
                )
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos, 18f))
                //if user has change location, calculate and load driver again
                if (firstTime) {
                    previousLocation = locationResult.lastLocation
                    currentLocation = locationResult.lastLocation

                    firstTime = false
                } else {
                    previousLocation = currentLocation
                    currentLocation = locationResult.lastLocation
                }
                if (previousLocation!!.distanceTo(currentLocation!!) / 1000 <= LIMIT_RANGE) {
                    loadAvailableDrivers()
                }
            }
        }
        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireContext())
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest, locationCallback, Looper.myLooper()
            )
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 12
            )
        }
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest, locationCallback, Looper.myLooper()
        )
        loadAvailableDrivers()
    }

    private fun setRestrictPlacesInCountry(lastLocation: Location) {
        try {
            val geoCoder = Geocoder(requireContext(), Locale.getDefault())
            val addressList: List<Address>? =
                geoCoder.getFromLocation(lastLocation.latitude, lastLocation.longitude, 1)
            if (!addressList.isNullOrEmpty()) {
                autocompleteSupportFragment.setCountry(addressList[0].countryCode)
            }
            cityName = addressList!![0].locality
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun loadAvailableDrivers() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Snackbar.make(
                requireView(), getString(R.string.permission_required), Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        fusedLocationProviderClient.lastLocation.addOnFailureListener { e ->
            Snackbar.make(requireView(), e.message!!, Snackbar.LENGTH_SHORT).show()
            Log.e("DriverSearch", "Error getting last location: ${e.message}")
        }.addOnSuccessListener { location ->
            cityName = LocationUtils.getAddressFromLocation(requireContext(), location)
            Log.d("DriverSearch", "Searching for drivers in city: $cityName")
            if (!TextUtils.isEmpty(cityName)) {
                if (location != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // Query
                            if (!TextUtils.isEmpty(cityName)) {
                                val driverLocationRef = FirebaseDatabase.getInstance()
                                    .getReference(Constants.DRIVERS_LOCATION_REFERENCES)
                                    .child(cityName)
                                val gf = GeoFire(driverLocationRef)
                                val geoQuery = gf.queryAtLocation(
                                    GeoLocation(location.latitude, location.longitude), distance
                                )
                                geoQuery.removeAllListeners()

                                geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener {
                                    override fun onKeyEntered(
                                        key: String?,
                                        location: GeoLocation?,
                                    ) {
                                        //Constants.driversFound.add(DriverGeoModel(key, location))
                                        if (!Constants.driversFound.containsKey(key)) {
                                            Constants.driversFound[key!!] =
                                                DriverGeoModel(key, location!!)
                                        }
                                    }

                                    override fun onKeyExited(key: String?) {
                                        if (key != null) {
                                            Constants.driversFound.remove(key)
                                        }
                                    }

                                    override fun onKeyMoved(key: String?, location: GeoLocation?) {
                                        // You may need to update the driver's location in Constants.driversFound
                                        val movedDriver = Constants.driversFound[key]
                                        // Check if the driver is found
                                        movedDriver?.let {
                                            it.geoLocation = location
                                            // Update the marker position on the map
                                            updateDriverMarkerPosition(key, location)
                                        }
                                    }

                                    override fun onGeoQueryReady() {
                                        if (distance <= LIMIT_RANGE) {
                                            distance++
                                            loadAvailableDrivers()
                                        } else {
                                            distance = 0.0
                                            addDriverMarker()
                                        }
                                    }

                                    override fun onGeoQueryError(error: DatabaseError?) {
                                        Snackbar.make(
                                            requireView(), error!!.message, Snackbar.LENGTH_SHORT
                                        ).show()
                                    }
                                })

                                driverLocationRef.addChildEventListener(object :
                                    ChildEventListener {
                                    override fun onChildAdded(
                                        snapshot: DataSnapshot,
                                        previousChildName: String?,
                                    ) {
                                        // Have new Driver
                                        val geoQueryModel =
                                            snapshot.getValue(GeoQueryModel::class.java)
                                        val geoLocation =
                                            GeoLocation(
                                                geoQueryModel!!.l!![0],
                                                geoQueryModel.l!![1]
                                            )
                                        val driverGeoModel =
                                            DriverGeoModel(snapshot.key, geoLocation)
                                        // Check if the driver is already in the list
                                        val existingDriver = Constants.driversFound[snapshot.key]
                                        if (existingDriver == null) {
                                            // If the driver is not in the list, add it
                                            Constants.driversFound[snapshot.key!!] = driverGeoModel
                                        } else {
                                            // If the driver is already in the list, update the location
                                            existingDriver.geoLocation = geoLocation
                                        }

                                        val newDriverLocation = Location("")
                                        newDriverLocation.latitude = geoLocation.latitude
                                        newDriverLocation.longitude = geoLocation.longitude
                                        val newDistance =
                                            location.distanceTo(newDriverLocation) / 1000 //in km
                                        if (newDistance <= LIMIT_RANGE) {
                                            findDriverByKey(driverGeoModel)
                                        }
                                    }

                                    override fun onChildChanged(
                                        snapshot: DataSnapshot,
                                        previousChildName: String?,
                                    ) {
                                    }

                                    override fun onChildRemoved(snapshot: DataSnapshot) {
                                        val removedDriverKey = snapshot.key
//                                    Constants.driversFound.removeAll { it.key == removedDriverKey }
                                        Constants.driversFound.remove(removedDriverKey)
                                        // Remove the marker from the map
                                        if (Constants.markerList.containsKey(removedDriverKey)) {
                                            Constants.markerList[removedDriverKey]?.remove()
                                            Constants.markerList.remove(removedDriverKey)
                                        }
                                    }

                                    override fun onChildMoved(
                                        snapshot: DataSnapshot,
                                        previousChildName: String?,
                                    ) {
                                        val geoQueryModel =
                                            snapshot.getValue(GeoQueryModel::class.java)
                                        val geoLocation =
                                            GeoLocation(
                                                geoQueryModel!!.l!![0],
                                                geoQueryModel.l!![1]
                                            )
                                        val driverGeoModel =
                                            DriverGeoModel(snapshot.key, geoLocation)

                                        // Check if the driver is already in the list
                                        val existingDriver = Constants.driversFound[snapshot.key]
                                        if (existingDriver == null) {
                                            // If the driver is not in the list, add it
                                            Constants.driversFound[snapshot.key!!] = driverGeoModel
                                        } else {
                                            // If the driver is already in the list, update the location
                                            existingDriver.geoLocation = geoLocation
                                        }
                                    }

                                    override fun onCancelled(error: DatabaseError) {
                                        Snackbar.make(
                                            requireView(), error.message, Snackbar.LENGTH_SHORT
                                        ).show()
                                    }
                                })
                            } else {
                                Snackbar.make(
                                    requireView(),
                                    getString(R.string.permission_required),
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: IOException) {
                            Log.e("DriverSearch", "Error during geocoding: ${e.message}")
                            Snackbar.make(
                                requireView(),
                                getString(R.string.permission_required),
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    Snackbar.make(
                        requireView(),
                        getString(R.string.city_name_not_found),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
            Snackbar.make(
                requireView(), getString(R.string.city_name_not_found), Snackbar.LENGTH_SHORT
            ).show()

        }
    }

    private fun addDriverMarker() {
        markerUpdateScope.launch {
            val newDriverKeys = Constants.driversFound.map { it.key }
            val existingDriverKeys = Constants.markerList.keys

            // Remove markers for drivers that are no longer in the list
            val removedDriverKeys = existingDriverKeys.subtract(newDriverKeys.toSet())
            withContext(Dispatchers.Main) {
                for (key in removedDriverKeys) {
                    Constants.markerList[key]?.remove()
                    Constants.markerList.remove(key)
                }
            }

            // Add or update markers for new or existing drivers
            withContext(Dispatchers.Main) {
                Constants.driversFound.entries.forEach { entry ->
                    val driverGeoModel = entry.value
                    Constants.markerList[driverGeoModel.key]?.let { existingMarker ->
                        // Update the existing marker position
                        val geoLocation = driverGeoModel.geoLocation
                        existingMarker.position =
                            LatLng(geoLocation?.latitude ?: 0.0, geoLocation?.longitude ?: 0.0)
                    } ?: run {
                        // Add a new marker
                        findDriverByKey(driverGeoModel)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (Constants.driversFound.isNotEmpty()) {
                    Snackbar.make(
                        requireView(), getString(R.string.drivers_are_found), Snackbar.LENGTH_SHORT
                    ).show()
                } else {
                    // No drivers found, remove all markers from the map
                    for (marker in Constants.markerList.values) {
                        marker.remove()
                    }
                    Constants.markerList.clear()
                    Snackbar.make(
                        requireView(),
                        getString(R.string.drivers_are_not_found),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun findDriverByKey(driverGeoModel: DriverGeoModel?) {
        FirebaseDatabase.getInstance().getReference(Constants.DRIVER_INFO_REFERENCE)
            .child(driverGeoModel!!.key!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onCancelled(error: DatabaseError) {
                    iFirebaseFailedListener.onFirebaseFailed(error.message)
                }

                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.hasChildren()) {
                        driverGeoModel.driverInfoModel =
                            (snapshot.getValue(DriverInfoModel::class.java))
                        iFirebaseDriverLocationListener.onDriverInfoLoadSuccess(driverGeoModel)
                    } else {
                        iFirebaseFailedListener.onFirebaseFailed(getString(R.string.key_not_found) + driverGeoModel.key)
                    }
                }
            })
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        //enable zoom
        mMap.uiSettings.isZoomControlsEnabled = true
        //check the permission
        if (ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            //if we don't have permission
            ActivityCompat.requestPermissions(
                requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 12
            )
            return
        }
        //when we have permission
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) { //Enable button first
            mMap.isMyLocationEnabled = true
            mMap.uiSettings.isMyLocationButtonEnabled = true
            //when i click on button
            mMap.setOnMyLocationButtonClickListener {
                Toast.makeText(context, "clicked", Toast.LENGTH_LONG).show()
                refreshMap()
                //get last location
                fusedLocationProviderClient.lastLocation.addOnFailureListener {
                    Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                }.addOnSuccessListener { location ->
                    val userLatLng = LatLng(location.latitude, location.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 18f))
                }
                true
            }
            val locationButton = (_mapFragment!!.requireView()
                .findViewById<View>("1".toInt()).parent!! as View).findViewById<View>("2".toInt())
            val params = locationButton.layoutParams as RelativeLayout.LayoutParams
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
            params.addRule(RelativeLayout.ALIGN_PARENT_START, 0)
            params.marginStart = 15
            params.bottomMargin = 250
        }

        //try to set map style
        try {
            val success = mMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.uber_maps_style)
            )
            if (!success) {
                Log.e("ErrorMap", "Map Style is not parsing")
            }
        } catch (e: Exception) {
            Log.e("ErrorMap", e.message!!)
        }

        addDriverMarker()
        // Load available drivers
        loadAvailableDrivers()
    }

    override fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?) {
        val driverKey = driverGeoModel?.key

        if (driverKey != null) {
            if (Constants.markerList.containsKey(driverKey)) {
                // Update the existing marker position
                val marker = Constants.markerList[driverKey]
                val geoLocation = driverGeoModel.geoLocation
                marker?.position =
                    LatLng(geoLocation?.latitude ?: 0.0, geoLocation?.longitude ?: 0.0)
            } else {
                // Add a new marker
                val geoLocation = driverGeoModel.geoLocation
                if (geoLocation != null) {
                    val newMarker = mMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(geoLocation.latitude, geoLocation.longitude))
                            .title(
                                Constants.buildName(
                                    driverGeoModel.driverInfoModel?.firstName ?: "",
                                    driverGeoModel.driverInfoModel?.lastName ?: ""
                                )
                            )
                            .snippet(driverGeoModel.driverInfoModel?.phoneNumber ?: "")
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
                    )
                    Constants.markerList[driverKey] = newMarker!!
                }
            }
        }

        if (!TextUtils.isEmpty(cityName)) {
            val driverLocation = FirebaseDatabase.getInstance()
                .getReference(Constants.DRIVERS_LOCATION_REFERENCES)
                .child(cityName).child(driverKey ?: "")

            driverLocation.addValueEventListener(object : ValueEventListener {
                override fun onCancelled(error: DatabaseError) {
                    Snackbar.make(requireView(), error.message, Snackbar.LENGTH_SHORT).show()
                }

                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.hasChildren()) {
                        if (Constants.markerList.containsKey(driverKey)) {
                            // Remove the marker from the map
                            Constants.markerList[driverKey]?.remove()
                            Constants.markerList.remove(driverKey)
                            if (Constants.driversFound[driverGeoModel!!.key] != null)
                                Constants.driversFound.remove(driverGeoModel.key)
                            driverLocation.removeEventListener(this)

                        }
                    }
                }
            })
        }
    }

    private fun refreshMap() {
        // Remove all markers from the map
        for (marker in Constants.markerList.values) {
            marker.remove()
        }
        Constants.markerList.clear()
        // Reset any other marker-related objects or data as needed
        Constants.driversFound.clear()
        distance = 1.0 // Reset the distance for querying drivers
        firstTime = true // Reset the firstTime flag
        // Load available drivers again
        loadAvailableDrivers()
    }

    override fun onDestroy() {
        super.onDestroy()
        _mapFragment = null
        refreshMap()
    }

    private fun updateDriverMarkerPosition(driverKey: String?, newLocation: GeoLocation?) {
        markerUpdateScope.launch {
            withContext(Dispatchers.Main) {
                val marker = Constants.markerList[driverKey]
                val currentPosition = marker?.position

                // Retrofit request to get the route information from old to new position
                compositeDisposable.add(
                    iGoogleAPI.getDirections(
                        "driving",
                        "less_driving",
                        "${currentPosition?.latitude},${currentPosition?.longitude}",
                        "${newLocation?.latitude},${newLocation?.longitude}",
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
                                    _mapFragment!!.requireView(),
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

}