package com.commutecheck.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
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
    private var locationType: String = ""
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocationPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        locationType = intent.getStringExtra(SettingsActivity.EXTRA_LOCATION_TYPE) ?: ""
        val title = if (locationType == SettingsActivity.LOCATION_TYPE_HOME) {
            getString(R.string.set_home_location)
        } else {
            getString(R.string.set_work_location)
        }
        supportActionBar?.title = title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.btnConfirmLocation.setOnClickListener {
            confirmLocation()
        }

        binding.btnUseCurrentLocation.setOnClickListener {
            useCurrentLocation()
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

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
        }

        // Default to a zoomed-out view of the US
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(39.8283, -98.5795), 4f))

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
            val address = reverseGeocode(latLng.latitude, latLng.longitude)
            binding.tvSelectedAddress.text = address ?: getString(
                R.string.coordinates_format,
                latLng.latitude,
                latLng.longitude
            )
            binding.etLocationName.setText(
                address?.split(",")?.firstOrNull()?.trim() ?: ""
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

        val name = binding.etLocationName.text.toString().trim().ifEmpty {
            if (locationType == SettingsActivity.LOCATION_TYPE_HOME) "Home" else "Work"
        }

        val resultIntent = Intent().apply {
            putExtra(SettingsActivity.EXTRA_LOCATION_NAME, name)
            putExtra(SettingsActivity.EXTRA_LATITUDE, latLng.latitude)
            putExtra(SettingsActivity.EXTRA_LONGITUDE, latLng.longitude)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
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
