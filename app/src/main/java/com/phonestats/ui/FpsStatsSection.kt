package com.phonestats.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import com.phonestats.camera.FpsAnalyzer
import com.phonestats.camera.FpsStats
import kotlinx.coroutines.delay

@Composable
fun FpsStatsSection(fpsAnalyzer: FpsAnalyzer) {
    var stats by remember { mutableStateOf<FpsStats?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("camera_settings", android.content.Context.MODE_PRIVATE) }
    
    var autoAF by remember { mutableStateOf(prefs.getBoolean("auto_af", false)) }
    var autoAE by remember { mutableStateOf(prefs.getBoolean("auto_ae", false)) }
    var exposureTimeMs by remember { mutableStateOf(prefs.getLong("exposure_time_ns", 10_000_000L) / 1_000_000f) }
    var iso by remember { mutableStateOf(prefs.getInt("iso", 400)) }
    
    val exposureRange = remember { fpsAnalyzer.getCameraExposureRange() }
    val isoRange = remember { fpsAnalyzer.getCameraIsoRange() }

    LaunchedEffect(fpsAnalyzer) {
        fpsAnalyzer.autoAF = autoAF
        fpsAnalyzer.autoAE = autoAE
        fpsAnalyzer.exposureTimeNs = (exposureTimeMs * 1_000_000f).toLong()
        fpsAnalyzer.iso = iso
        
        while (true) {
            stats = fpsAnalyzer.getStats()
            isRunning = fpsAnalyzer.isRunning()
            delay(1000)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "FPS Statistics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Camera Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        SurfaceView(context).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    fpsAnalyzer.setPreviewSurface(holder.surface)
                                }

                                override fun surfaceChanged(
                                    holder: SurfaceHolder,
                                    format: Int,
                                    width: Int,
                                    height: Int
                                ) {}

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    fpsAnalyzer.setPreviewSurface(null)
                                }
                            })
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Camera Controls
            Text(
                text = "Camera Controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // AF Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Auto Focus",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = autoAF,
                    onCheckedChange = { enabled ->
                        autoAF = enabled
                        fpsAnalyzer.autoAF = enabled
                        fpsAnalyzer.updateCameraSettings()
                        prefs.edit().putBoolean("auto_af", enabled).apply()
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // AE Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Auto Exposure",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = autoAE,
                    onCheckedChange = { enabled ->
                        autoAE = enabled
                        fpsAnalyzer.autoAE = enabled
                        fpsAnalyzer.updateCameraSettings()
                        prefs.edit().putBoolean("auto_ae", enabled).apply()
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Exposure Time Slider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Exposure Time",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (autoAE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = String.format("%.1f ms", exposureTimeMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (autoAE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                }
                
                exposureRange?.let { range ->
                    val minMs = range.first / 1_000_000f
                    val maxMs = range.last / 1_000_000f
                    
                    Slider(
                        value = exposureTimeMs,
                        onValueChange = { value ->
                            exposureTimeMs = value
                        },
                        onValueChangeFinished = {
                            fpsAnalyzer.exposureTimeNs = (exposureTimeMs * 1_000_000f).toLong()
                            fpsAnalyzer.updateCameraSettings()
                            prefs.edit().putLong("exposure_time_ns", (exposureTimeMs * 1_000_000f).toLong()).apply()
                        },
                        valueRange = minMs..maxMs,
                        enabled = !autoAE
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // ISO Slider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ISO",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (autoAE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = iso.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (autoAE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                }
                
                isoRange?.let { range ->
                    Slider(
                        value = iso.toFloat(),
                        onValueChange = { value ->
                            iso = value.toInt()
                        },
                        onValueChangeFinished = {
                            fpsAnalyzer.iso = iso
                            fpsAnalyzer.updateCameraSettings()
                            prefs.edit().putInt("iso", iso).apply()
                        },
                        valueRange = range.first.toFloat()..range.last.toFloat(),
                        enabled = !autoAE
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!isRunning) {
                Text(
                    text = "Camera not running. Grant camera permission to start.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (stats == null) {
                Text(
                    text = "Collecting data...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val s = stats!!
                Text(
                    text = "Frame count: ${s.frameCount}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                FpsWindowStats("Last 1 min", s.oneMinStats)
                FpsWindowStats("Last 5 min", s.fiveMinStats)
                FpsWindowStats("Last 20 min", s.twentyMinStats)
            }
        }
    }
}

@Composable
fun FpsWindowStats(label: String, stats: FpsAnalyzer.WindowStats?) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (stats == null) {
            Text(
                text = "  Not enough data yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem("Min", String.format("%.6f fps", stats.min))
                    StatItem("Mean", String.format("%.6f fps", stats.mean))
                    StatItem("Max", String.format("%.6f fps", stats.max))
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Interval: min %.0f ns | mean %.0f ns | max %.0f ns".format(
                        1_000_000_000.0 / stats.max,
                        1_000_000_000.0 / stats.mean,
                        1_000_000_000.0 / stats.min
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
