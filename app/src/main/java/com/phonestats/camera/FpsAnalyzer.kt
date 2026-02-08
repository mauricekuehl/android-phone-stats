package com.phonestats.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executor

data class FpsStats(
    val frameCount: Long,
    val tenSecStats: FpsAnalyzer.WindowStats?,
    val oneMinStats: FpsAnalyzer.WindowStats?,
    val fiveMinStats: FpsAnalyzer.WindowStats?,
    val twentyMinStats: FpsAnalyzer.WindowStats?
)

class FpsAnalyzer(private val context: Context) {
    data class WindowStats(
        val min: Double,
        val max: Double,
        val mean: Double
    )

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    private val timestamps = ConcurrentLinkedDeque<Long>()
    private var frameCount = 0L
    private var isRunning = false

    private var previewSurface: Surface? = null
    private var previewSize: Size = Size(640, 480)

    var autoAE: Boolean = false
    var autoAF: Boolean = false
    var exposureTimeNs: Long = 10_000_000L
    var iso: Int = 400

    private val tenSecWindowNs = 10_000_000_000L
    private val oneMinWindowNs = 60_000_000_000L
    private val fiveMinWindowNs = 300_000_000_000L
    private val twentyMinWindowNs = 1_200_000_000_000L

    fun getPreviewSize(): Size = previewSize

    fun getCameraExposureRange(): LongRange? {
        return try {
            val cameraId = findBackCamera() ?: return null
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val range = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            range?.let { LongRange(it.lower, it.upper) }
        } catch (e: Exception) {
            null
        }
    }

    fun getCameraIsoRange(): IntRange? {
        return try {
            val cameraId = findBackCamera() ?: return null
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val range = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            range?.let { IntRange(it.lower, it.upper) }
        } catch (e: Exception) {
            null
        }
    }

    fun updateCameraSettings() {
        if (isRunning) {
            stop()
            start()
        }
    }

    fun setPreviewSurface(surface: Surface?) {
        val wasRunning = isRunning
        if (wasRunning) {
            stop()
        }
        previewSurface = surface
        if (wasRunning && surface != null) {
            start()
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return

        handlerThread = HandlerThread("CameraThread").apply { start() }
        handler = Handler(handlerThread!!.looper)

        try {
            val cameraId = findBackCamera() ?: return
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val configMap = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            ) ?: return

            previewSize = choosePreviewSize(configMap)

            imageReader = ImageReader.newInstance(
                previewSize.width,
                previewSize.height,
                ImageFormat.YUV_420_888,
                2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        val timestamp = image.timestamp
                        recordTimestamp(timestamp)
                        image.close()
                    }
                }, handler)
            }

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                }
            }, handler)

            isRunning = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun findBackCamera(): String? {
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id
            }
        }
        return cameraManager.cameraIdList.firstOrNull()
    }

    private fun choosePreviewSize(configMap: android.hardware.camera2.params.StreamConfigurationMap): Size {
        val sizes = configMap.getOutputSizes(ImageFormat.YUV_420_888)
        // Choose a reasonable preview size (720p or smaller for efficiency)
        return sizes.filter { it.width <= 1280 && it.height <= 720 }
            .maxByOrNull { it.width * it.height }
            ?: sizes.minByOrNull { it.width * it.height }
            ?: Size(640, 480)
    }

    private fun createCaptureSession() {
        val camera = cameraDevice ?: return
        val reader = imageReader ?: return
        val h = handler ?: return

        try {
            val outputs = mutableListOf(OutputConfiguration(reader.surface))
            previewSurface?.let { surface ->
                outputs.add(OutputConfiguration(surface))
            }

            val executor = Executor { command -> h.post(command) }

            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputs,
                executor,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        startPreview()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                }
            )

            camera.createCaptureSession(sessionConfig)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startPreview() {
        val camera = cameraDevice ?: return
        val session = captureSession ?: return
        val reader = imageReader ?: return

        try {
            val cameraId = findBackCamera() ?: return
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)

            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(reader.surface)
                previewSurface?.let { addTarget(it) }

                // Configure AF mode
                if (autoAF) {
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                } else {
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                    set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f) // 0 = infinity
                }

                // Configure AE mode
                if (autoAE) {
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                } else {
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    
                    val exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                    val exposureTime = exposureRange?.let {
                        exposureTimeNs.coerceIn(it.lower, it.upper)
                    } ?: exposureTimeNs
                    set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime)

                    val isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                    val isoValue = isoRange?.let {
                        iso.coerceIn(it.lower, it.upper)
                    } ?: iso
                    set(CaptureRequest.SENSOR_SENSITIVITY, isoValue)
                }
            }

            session.setRepeatingRequest(requestBuilder.build(), null, handler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun recordTimestamp(timestamp: Long) {
        timestamps.addLast(timestamp)
        frameCount++

        // Trim old timestamps beyond 20 minutes
        val cutoff = timestamp - twentyMinWindowNs
        while (timestamps.peekFirst()?.let { it < cutoff } == true) {
            timestamps.pollFirst()
        }
    }

    fun getStats(): FpsStats? {
        if (timestamps.size < 2) return null

        val now = timestamps.peekLast() ?: return null

        return FpsStats(
            frameCount = frameCount,
            tenSecStats = calculateWindowStats(now - tenSecWindowNs),
            oneMinStats = calculateWindowStats(now - oneMinWindowNs),
            fiveMinStats = calculateWindowStats(now - fiveMinWindowNs),
            twentyMinStats = calculateWindowStats(now - twentyMinWindowNs)
        )
    }

    private fun calculateWindowStats(startTime: Long): WindowStats? {
        val relevantTimestamps = timestamps.filter { it >= startTime }.sorted()
        if (relevantTimestamps.size < 2) return null

        val fpsList = mutableListOf<Double>()
        for (i in 1 until relevantTimestamps.size) {
            val deltaNs = relevantTimestamps[i] - relevantTimestamps[i - 1]
            if (deltaNs > 0) {
                val fps = 1_000_000_000.0 / deltaNs
                fpsList.add(fps)
            }
        }

        if (fpsList.isEmpty()) return null

        return WindowStats(
            min = fpsList.minOrNull() ?: 0.0,
            max = fpsList.maxOrNull() ?: 0.0,
            mean = fpsList.average()
        )
    }

    fun isRunning(): Boolean = isRunning

    fun stop() {
        isRunning = false
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
    }

    fun release() {
        stop()
        imageReader?.close()
        imageReader = null
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
        timestamps.clear()
        frameCount = 0
    }
}
