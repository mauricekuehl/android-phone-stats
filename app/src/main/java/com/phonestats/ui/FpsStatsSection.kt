package com.phonestats.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    LaunchedEffect(fpsAnalyzer) {
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
