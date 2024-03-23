package video.api.flutter.livestream.utils

import android.util.Size
import com.pedro.encoder.input.video.CameraHelper
import video.api.flutter.livestream.CameraNativeView

fun CameraNativeView.ResolutionPreset.toResolution(): Size {
    return when (this) {
        CameraNativeView.ResolutionPreset.low -> Size(426, 240)
        CameraNativeView.ResolutionPreset.medium -> Size(640, 360)
        CameraNativeView.ResolutionPreset.high -> Size(854, 480)
        CameraNativeView.ResolutionPreset.veryHigh -> Size(1280, 720)
        CameraNativeView.ResolutionPreset.ultraHigh -> Size(1920, 1080)
        else -> throw IllegalArgumentException("Unknown resolution: $this")
    }
}

fun String.toPreset(): CameraNativeView.ResolutionPreset {
    return when (this) {
        "240p" -> CameraNativeView.ResolutionPreset.low
        "360p" -> CameraNativeView.ResolutionPreset.medium
        "480p" -> CameraNativeView.ResolutionPreset.high
        "720p" -> CameraNativeView.ResolutionPreset.veryHigh
        "1080p" -> CameraNativeView.ResolutionPreset.ultraHigh
        else -> throw IllegalArgumentException("Unknown preset: $this")
    }
}

fun CameraHelper.Facing.toKey(): String {
    return when (this) {
        CameraHelper.Facing.FRONT -> "front"
        CameraHelper.Facing.BACK -> "back"
        else -> throw IllegalArgumentException("Invalid camera position for camera $this")
    }
}

fun String.toFacing(): CameraHelper.Facing {
    return when (this) {
         "front" -> CameraHelper.Facing.FRONT
         "back" -> CameraHelper.Facing.BACK
        else -> throw IllegalArgumentException("Invalid camera position for camera $this")
    }
}

/**
 * Add a slash at the end of a [String] only if it is missing.
 *
 * @return the given string with a trailing slash.
 */
fun String.addTrailingSlashIfNeeded(): String {
    return if (this.endsWith("/")) this else "$this/"
}


