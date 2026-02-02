package com.phonestats.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Range
import android.util.Size
import android.graphics.ImageFormat

data class CameraConfig(
    val width: Int,
    val height: Int,
    val fpsRanges: String
)

data class CameraInfo(
    val id: String,
    val facing: String,
    val standardConfigs: List<CameraConfig>,
    val highSpeedConfigs: List<CameraConfig>
)

class CameraCapabilities(context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun getCameras(): List<CameraInfo> {
        val cameras = mutableListOf<CameraInfo>()

        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    CameraCharacteristics.LENS_FACING_BACK -> "Back"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                    else -> "Unknown"
                }

                val configMap = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                ) ?: continue

                val fpsRanges = characteristics.get(
                    CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
                ) ?: emptyArray()

                val standardConfigs = getStandardConfigs(configMap, fpsRanges)
                val highSpeedConfigs = getHighSpeedConfigs(configMap)

                cameras.add(CameraInfo(
                    id = cameraId,
                    facing = facing,
                    standardConfigs = standardConfigs,
                    highSpeedConfigs = highSpeedConfigs
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return cameras
    }

    private fun getStandardConfigs(
        configMap: StreamConfigurationMap,
        fpsRanges: Array<Range<Int>>
    ): List<CameraConfig> {
        val configs = mutableListOf<CameraConfig>()
        val fpsString = fpsRanges.joinToString(", ") { "${it.lower}-${it.upper}" }

        val sizes = configMap.getOutputSizes(ImageFormat.YUV_420_888) ?: return configs

        for (size in sizes.sortedByDescending { it.width * it.height }) {
            configs.add(CameraConfig(
                width = size.width,
                height = size.height,
                fpsRanges = fpsString
            ))
        }

        return configs
    }

    private fun getHighSpeedConfigs(configMap: StreamConfigurationMap): List<CameraConfig> {
        val configs = mutableListOf<CameraConfig>()

        try {
            val highSpeedSizes = configMap.highSpeedVideoSizes ?: return configs

            for (size in highSpeedSizes) {
                val fpsRanges = configMap.getHighSpeedVideoFpsRangesFor(size)
                val fpsString = fpsRanges?.joinToString(", ") { "${it.lower}-${it.upper}" } ?: "N/A"

                configs.add(CameraConfig(
                    width = size.width,
                    height = size.height,
                    fpsRanges = fpsString
                ))
            }
        } catch (e: Exception) {
            // High-speed not supported
        }

        return configs
    }
}
