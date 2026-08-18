package com.commutecheck.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.commutecheck.app.R
import com.commutecheck.app.databinding.ActivityLocationPickerBinding
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

class LocationPickerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityLocationPickerBinding
    private var googleMap: GoogleMap? = null
    private var selectedLatLng: LatLng? = null
    private var selectedAddress: String = ""
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocationPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.pick_location)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Name field removed — name is set in the parent dialog.
        binding.etLocationName.visibility = android.view.View.GONE
        (binding.etLocationName.parent as? android.view.View)?.visibility = android.view.View.GONE

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        installWheelZoomHandler(binding.map)
        mapFragment.view?.let { installWheelZoomHandler(it) }

        binding.btnConfirmLocation.setOnClickListener {
            confirmLocation()
        }

        binding.btnUseCurrentLocation.setOnClickListener {
            useCurrentLocation()
        }

        binding.btnZoomIn.setOnClickListener {
            googleMap?.animateCamera(CameraUpdateFactory.zoomIn())
        }

        binding.btnZoomOut.setOnClickListener {
            googleMap?.animateCamera(CameraUpdateFactory.zoomOut())
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        return handleWheelZoom(event) || super.dispatchGenericMotionEvent(event)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        binding.map.post { installWheelZoomHandler(binding.map) }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
        }

        if (intent.hasExtra(EXTRA_INITIAL_LATITUDE) && intent.hasExtra(EXTRA_INITIAL_LONGITUDE)) {
            val latLng = LatLng(
                intent.getDoubleExtra(EXTRA_INITIAL_LATITUDE, 0.0),
                intent.getDoubleExtra(EXTRA_INITIAL_LONGITUDE, 0.0)
            )
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
            selectLocation(latLng)
        } else {
            // Default to a zoomed-out view of the US
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(39.8283, -98.5795), 4f))
        }

        map.setOnMapClickListener { latLng ->
            selectLocation(latLng)
        }

        map.setOnMapLongClickListener { latLng ->
            selectLocation(latLng)
        }
    }

    private fun selectLocation(latLng: LatLng) {
        selectedLatLng = latLng
        googleMap?.clear()
        googleMap?.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Selected Location")
        )

        scope.launch {
            val addr = reverseGeocode(latLng.latitude, latLng.longitude)
            selectedAddress = addr ?: ""
            binding.tvSelectedAddress.text = addr ?: getString(
                R.string.coordinates_format,
                latLng.latitude,
                latLng.longitude
            )
        }
    }

    private fun useCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(
                    this@LocationPickerActivity
                )
                val location = fusedClient.lastLocation.await()
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    googleMap?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(latLng, 16f)
                    )
                    selectLocation(latLng)
                } else {
                    Toast.makeText(
                        this@LocationPickerActivity,
                        "Could not get current location",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@LocationPickerActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun confirmLocation() {
        val latLng = selectedLatLng
        if (latLng == null) {
            Toast.makeText(this, "Tap the map to select a location", Toast.LENGTH_SHORT).show()
            return
        }

        val resultIntent = Intent().apply {
            putExtra(EXTRA_LATITUDE, latLng.latitude)
            putExtra(EXTRA_LONGITUDE, latLng.longitude)
            putExtra(EXTRA_ADDRESS, selectedAddress)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_INITIAL_NAME = "initial_name"
        const val EXTRA_INITIAL_LATITUDE = "initial_latitude"
        const val EXTRA_INITIAL_LONGITUDE = "initial_longitude"
        const val EXTRA_LOCATION_NAME = "location_name"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_ADDRESS = "address"
    }

    private fun installWheelZoomHandler(view: View) {
        view.setOnGenericMotionListener { _, event -> handleWheelZoom(event) }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                installWheelZoomHandler(view.getChildAt(index))
            }
        }
    }

    private fun handleWheelZoom(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_SCROLL ||
            !event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)
        ) {
            return false
        }

        val scroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        if (scroll == 0f) {
            return false
        }

        googleMap?.animateCamera(
            CameraUpdateFactory.zoomBy(if (scroll > 0f) 1f else -1f)
        )
        return true
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocode(lat: Double, lng: Double): String? {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            addresses?.firstOrNull()?.let { addr ->
                buildString {
                    if (!addr.thoroughfare.isNullOrEmpty()) {
                        append(addr.thoroughfare)
                        if (!addr.subThoroughfare.isNullOrEmpty()) {
                            append(" ${addr.subThoroughfare}")
                        }
                        append(", ")
                    }
                    if (!addr.locality.isNullOrEmpty()) {
                        append(addr.locality)
                    } else if (!addr.subAdminArea.isNullOrEmpty()) {
                        append(addr.subAdminArea)
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
