package com.phonestats

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.phonestats.camera.CameraCapabilities
import com.phonestats.camera.FpsAnalyzer
import com.phonestats.gnss.GnssTimeTracker
import com.phonestats.ui.*

class MainActivity : ComponentActivity() {
    private var cameraCapabilities: CameraCapabilities? = null
    private var fpsAnalyzer: FpsAnalyzer? = null
    private var gnssTimeTracker: GnssTimeTracker? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

        if (cameraGranted) {
            fpsAnalyzer?.start()
        }
        if (locationGranted) {
            gnssTimeTracker?.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraCapabilities = CameraCapabilities(this)
        fpsAnalyzer = FpsAnalyzer(this)
        gnssTimeTracker = GnssTimeTracker(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PhoneStatsApp(
                        cameraCapabilities = cameraCapabilities!!,
                        fpsAnalyzer = fpsAnalyzer!!,
                        gnssTimeTracker = gnssTimeTracker!!
                    )
                }
            }
        }

        requestPermissions()
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        } else {
            fpsAnalyzer?.start()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            gnssTimeTracker?.start()
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            fpsAnalyzer?.start()
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            gnssTimeTracker?.start()
        }
    }

    override fun onPause() {
        super.onPause()
        fpsAnalyzer?.stop()
        gnssTimeTracker?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        fpsAnalyzer?.release()
        gnssTimeTracker?.release()
    }
}

@Composable
fun PhoneStatsApp(
    cameraCapabilities: CameraCapabilities,
    fpsAnalyzer: FpsAnalyzer,
    gnssTimeTracker: GnssTimeTracker
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        PhoneInfoSection()
        Spacer(modifier = Modifier.height(16.dp))
        CameraInfoSection(cameraCapabilities)
        Spacer(modifier = Modifier.height(16.dp))
        FpsStatsSection(fpsAnalyzer)
        Spacer(modifier = Modifier.height(16.dp))
        ClockDriftSection(gnssTimeTracker)
        Spacer(modifier = Modifier.height(32.dp))
    }
}
