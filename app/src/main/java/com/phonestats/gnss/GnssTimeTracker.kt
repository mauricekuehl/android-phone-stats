package com.phonestats.gnss

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import java.util.concurrent.CopyOnWriteArrayList

class GnssTimeTracker(private val context: Context) {
    data class DriftPoint(
        val elapsedSeconds: Double,
        val driftMs: Double
    )

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val driftData = CopyOnWriteArrayList<DriftPoint>()

    private var isRunning = false
    private var hasGpsFix = false
    private var startTimeNanos: Long = 0

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            processLocation(location)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return

        try {
            startTimeNanos = SystemClock.elapsedRealtimeNanos()

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000, // 1 second
                0f,   // 0 meters
                locationListener
            )

            isRunning = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processLocation(location: Location) {
        // Only process if we have valid GPS time
        if (location.time == 0L) return

        hasGpsFix = true

        // System monotonic time when this location was determined
        val elapsedRealtimeNanos = location.elapsedRealtimeNanos

        // GPS time (UTC milliseconds since epoch)
        val gpsTimeMs = location.time

        // Convert elapsed realtime to milliseconds
        val elapsedRealtimeMs = elapsedRealtimeNanos / 1_000_000.0

        // Calculate what GPS time would be if system clock were perfect
        // We need a reference point - use the first measurement
        if (driftData.isEmpty()) {
            // First measurement - store as reference
            val elapsedSinceStart = (elapsedRealtimeNanos - startTimeNanos) / 1_000_000_000.0
            driftData.add(DriftPoint(elapsedSinceStart, 0.0))
            return
        }

        // Calculate drift relative to first measurement
        // Drift = difference between expected GPS time progression and actual
        val firstPoint = driftData.first()
        val elapsedSinceStartSec = (elapsedRealtimeNanos - startTimeNanos) / 1_000_000_000.0

        // The drift is how much the system clock has drifted relative to GPS time
        // We compare the delta in system time vs delta in GPS time
        val currentSystemTimeMs = System.currentTimeMillis()

        // Simple drift calculation: GPS time - System time
        // A positive drift means system clock is behind GPS time
        val driftMs = gpsTimeMs.toDouble() - currentSystemTimeMs.toDouble()

        // Normalize relative to first measurement's drift
        val firstDrift = if (driftData.size > 0) {
            val firstLoc = driftData.first()
            firstLoc.driftMs
        } else 0.0

        val relativeDrift = driftMs - firstDrift

        driftData.add(DriftPoint(elapsedSinceStartSec, relativeDrift))

        // Keep last 30 minutes of data
        val cutoffSeconds = elapsedSinceStartSec - 1800
        driftData.removeIf { it.elapsedSeconds < cutoffSeconds }
    }

    fun getDriftData(): List<DriftPoint> = driftData.toList()

    data class DriftRate(
        val driftMsPerMin: Double,
        val windowSeconds: Double
    )

    fun getDriftRate(windowSeconds: Double): DriftRate? {
        if (driftData.size < 2) return null
        val latest = driftData.last()
        val cutoff = latest.elapsedSeconds - windowSeconds
        val startPoint = driftData.firstOrNull { it.elapsedSeconds >= cutoff } ?: return null
        if (startPoint == latest) return null

        val driftDelta = latest.driftMs - startPoint.driftMs
        val timeDelta = latest.elapsedSeconds - startPoint.elapsedSeconds
        if (timeDelta < 1.0) return null

        val driftPerMin = (driftDelta / timeDelta) * 60.0
        return DriftRate(driftPerMin, timeDelta)
    }

    fun isRunning(): Boolean = isRunning

    fun hasGpsFix(): Boolean = hasGpsFix

    fun stop() {
        isRunning = false
        hasGpsFix = false
        try {
            locationManager.removeUpdates(locationListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        stop()
        driftData.clear()
        startTimeNanos = 0
    }
}
