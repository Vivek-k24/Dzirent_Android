package com.dzirent.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** An approximate place: coordinates already rounded to 2 decimals, plus a city name if known. */
data class ApproxPlace(val lat: Double, val lng: Double, val city: String?)

/**
 * Coarse device location and Geocoder lookups. Every call returns null on failure
 * instead of throwing; failures are logged (first line only) under the "Dzirent" tag.
 */
class LocationService(context: Context) {
    private val appContext = context.applicationContext
    private val fused = LocationServices.getFusedLocationProviderClient(appContext)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Current approximate location, rounded, with the city reverse-geocoded when possible. */
    @SuppressLint("MissingPermission") // checked by hasPermission() first
    suspend fun currentPlace(): ApproxPlace? {
        if (!hasPermission()) return null
        val tokenSource = CancellationTokenSource()
        val location = try {
            withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                suspendCancellableCoroutine<Location?> { cont ->
                    fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { e ->
                            log("getCurrentLocation", e)
                            cont.resume(null)
                        }
                    cont.invokeOnCancellation { tokenSource.cancel() }
                }
            }
        } catch (e: Exception) {
            log("getCurrentLocation", e)
            null
        } ?: return null

        val lat = roundCoord(location.latitude)
        val lng = roundCoord(location.longitude)
        return ApproxPlace(lat, lng, cityFor(lat, lng))
    }

    /** City name for the given coordinates, or null if the Geocoder can't tell. */
    suspend fun cityFor(lat: Double, lng: Double): String? {
        val address = geocode { geocoder, onResult ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(lat, lng, 1, listener(onResult))
                null
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lng, 1).orEmpty()
            }
        }
        return address?.cityName()
    }

    /** Coordinates for a typed city name, rounded, or null if it can't be found. */
    suspend fun placeForCity(name: String): ApproxPlace? {
        val address = geocode { geocoder, onResult ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocationName(name, 1, listener(onResult))
                null
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(name, 1).orEmpty()
            }
        } ?: return null
        if (!address.hasLatitude() || !address.hasLongitude()) return null
        return ApproxPlace(
            lat = roundCoord(address.latitude),
            lng = roundCoord(address.longitude),
            city = address.cityName() ?: name
        )
    }

    /**
     * Runs a Geocoder lookup. [lookup] either returns results directly (API < 33, runs on IO)
     * or returns null and later calls onResult from the async listener (API 33+).
     */
    private suspend fun geocode(
        lookup: (Geocoder, (List<Address>) -> Unit) -> List<Address>?
    ): Address? {
        if (!Geocoder.isPresent()) {
            Log.w("Dzirent", "Geocoder not available on this device")
            return null
        }
        val geocoder = Geocoder(appContext, Locale.getDefault())
        return try {
            withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    suspendCancellableCoroutine<List<Address>> { cont ->
                        val direct = lookup(geocoder) { results -> if (cont.isActive) cont.resume(results) }
                        if (direct != null) cont.resume(direct)
                    }
                }
            }?.firstOrNull()
        } catch (e: Exception) {
            log("geocode", e)
            null
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun listener(onResult: (List<Address>) -> Unit) = object : Geocoder.GeocodeListener {
        override fun onGeocode(addresses: MutableList<Address>) = onResult(addresses)
        override fun onError(errorMessage: String?) {
            Log.w("Dzirent", "Geocoder error: ${errorMessage?.lineSequence()?.firstOrNull().orEmpty()}")
            onResult(emptyList())
        }
    }

    private fun Address.cityName(): String? =
        (locality ?: subAdminArea ?: adminArea)?.takeIf { it.isNotBlank() }

    private fun log(operation: String, e: Throwable) {
        val firstLine = e.message?.lineSequence()?.firstOrNull().orEmpty()
        Log.w("Dzirent", "Location $operation failed: ${e.javaClass.simpleName} - $firstLine")
    }

    private companion object {
        const val LOCATION_TIMEOUT_MS = 15_000L
        const val GEOCODE_TIMEOUT_MS = 10_000L
    }
}
