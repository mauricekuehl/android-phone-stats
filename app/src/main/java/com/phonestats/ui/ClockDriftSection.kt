package com.phonestats.ui

import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.phonestats.gnss.GnssTimeTracker
import kotlinx.coroutines.delay

@Composable
fun ClockDriftSection(gnssTimeTracker: GnssTimeTracker) {
    var driftData by remember { mutableStateOf<List<GnssTimeTracker.DriftPoint>>(emptyList()) }
    var isRunning by remember { mutableStateOf(false) }
    var hasGpsFix by remember { mutableStateOf(false) }
    var drift1min by remember { mutableStateOf<GnssTimeTracker.DriftRate?>(null) }
    var drift5min by remember { mutableStateOf<GnssTimeTracker.DriftRate?>(null) }
    var drift20min by remember { mutableStateOf<GnssTimeTracker.DriftRate?>(null) }
    var driftTotal by remember { mutableStateOf<GnssTimeTracker.DriftRate?>(null) }
    var rangeMinutes by remember { mutableFloatStateOf(5f) }

    LaunchedEffect(gnssTimeTracker) {
        while (true) {
            driftData = gnssTimeTracker.getDriftData()
            isRunning = gnssTimeTracker.isRunning()
            hasGpsFix = gnssTimeTracker.hasGpsFix()
            drift1min = gnssTimeTracker.getDriftRate(60.0)
            drift5min = gnssTimeTracker.getDriftRate(300.0)
            drift20min = gnssTimeTracker.getDriftRate(1200.0)
            driftTotal = gnssTimeTracker.getDriftRate(Double.MAX_VALUE)
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
                text = "GNSS Clock Drift",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (!isRunning) {
                Text(
                    text = "Location not available. Grant location permission to start.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (!hasGpsFix) {
                Text(
                    text = "Waiting for GPS fix... (go outdoors for better signal)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (driftData.isEmpty()) {
                Text(
                    text = "Collecting drift data...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Data points: ${driftData.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val latestDrift = driftData.lastOrNull()
                if (latestDrift != null) {
                    Text(
                        text = "Current drift: ${String.format("%.6f", latestDrift.driftMs)} ms",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Drift Rate (ms/min):",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DriftRateItem("1m", drift1min)
                    DriftRateItem("5m", drift5min)
                    DriftRateItem("20m", drift20min)
                    DriftRateItem("Total", driftTotal)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Graph Range: ${rangeMinutes.toInt()} min",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = rangeMinutes,
                    onValueChange = { rangeMinutes = it },
                    valueRange = 1f..30f,
                    steps = 28,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                val filteredData = remember(driftData, rangeMinutes) {
                    if (driftData.isEmpty()) emptyList()
                    else {
                        val latest = driftData.last().elapsedSeconds
                        val cutoff = latest - (rangeMinutes * 60)
                        driftData.filter { it.elapsedSeconds >= cutoff }
                    }
                }

                DriftChart(
                    driftData = filteredData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }
    }
}

@Composable
private fun DriftRateItem(label: String, rate: GnssTimeTracker.DriftRate?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (rate != null) String.format("%.6f", rate.driftMsPerMin) else "—",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun DriftChart(
    driftData: List<GnssTimeTracker.DriftPoint>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            LineChart(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                description.isEnabled = false
                legend.isEnabled = true
                legend.textColor = textColor

                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.textColor = textColor
                xAxis.setDrawGridLines(true)

                axisLeft.textColor = textColor
                axisLeft.setDrawGridLines(true)
                axisRight.isEnabled = false

                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
            }
        },
        update = { chart ->
            val entries = driftData.map { point ->
                Entry(point.elapsedSeconds.toFloat(), point.driftMs.toFloat())
            }

            if (entries.isNotEmpty()) {
                val dataSet = LineDataSet(entries, "Drift (ms)").apply {
                    color = primaryColor
                    setCircleColor(primaryColor)
                    lineWidth = 2f
                    circleRadius = 2f
                    setDrawCircleHole(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.LINEAR
                }

                chart.data = LineData(dataSet)
                chart.invalidate()
            }
        }
    )
}
