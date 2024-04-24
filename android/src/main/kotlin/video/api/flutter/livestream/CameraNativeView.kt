package video.api.flutter.livestream

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.util.Log
import android.util.Size
import android.view.Surface
import android.widget.Toast
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.video.CameraHelper.Facing
import com.pedro.library.generic.GenericStream
import com.pedro.library.rtmp.RtmpStream
import com.pedro.library.util.SensorRotationManager
import com.pedro.library.util.sources.audio.MicrophoneSource
import com.pedro.library.util.sources.video.Camera1Source
import com.pedro.library.util.sources.video.Camera2Source
import com.pedro.library.util.sources.video.VideoSource
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.TextureRegistry
import video.api.flutter.livestream.utils.toResolution
import java.io.IOException


class CameraNativeView(
    private val context: Context,
    textureRegistry: TextureRegistry,
    private var listenToOrientationChange: Boolean = false,
    private var preset: ResolutionPreset,
    private var audioBitRate: Int? = null,
    private val onVideoSizeChanged: (Size) -> Unit,
    private val onDisconnected: () -> Unit,
) : ConnectChecker {

    private final val TAG: String = "CameraNativeView"

    /// Mirrors camera.dart
    enum class ResolutionPreset {
        low, medium, high, veryHigh, ultraHigh, max
    }

    private var rtmpCamera: GenericStream? = null
    private var sensorRotationManager: SensorRotationManager? = null
    private var currentOrientation = -1
    private var prepared = false

    init {
        rtmpCamera = GenericStream(context, this,  Camera2Source(context), MicrophoneSource())
        rtmpCamera?.setVideoCodec(VideoCodec.H265)
        sensorRotationManager = SensorRotationManager(context, true) {
            //0 = portrait, 90 = landscape, 180 = reverse portrait, 270 = reverse landscape
            if (currentOrientation != it) {
                rtmpCamera?.setOrientation(it)
                currentOrientation = it
                val size = preset.toResolution()
                rtmpCamera?.getGlInterface()
                    ?.setPreviewResolution(size.width, size.height, it == 0)
            }
        }
        rtmpCamera?.setOrientation(0)
        if (listenToOrientationChange) {
            sensorRotationManager?.start()
        }
    }

    fun dispose() {
        rtmpCamera?.release()
        flutterTexture.release()
    }

    private val flutterTexture = textureRegistry.createSurfaceTexture()
    val textureId: Long
        get() = flutterTexture.id()

    private fun getSurface(): Surface {
        val size = getVideoSize()
        val surfaceTexture = flutterTexture.surfaceTexture().apply {
            setDefaultBufferSize(size.width, size.height)
        }
        return Surface(surfaceTexture)
    }

    fun isStreaming(): Boolean {
        return rtmpCamera?.isStreaming ?: false
    }

    fun setPreset(preset: ResolutionPreset) {
        this.preset = preset
        prepared = false
        stopPreview()
        startPreview()
        onVideoSizeChanged(getVideoSize())
    }

    fun getVideoSize(): Size {
        val streamingSize = preset.toResolution()
        return if (currentOrientation == 90 || currentOrientation == 270)
            Size(streamingSize.height, streamingSize.width)
        else
            Size(streamingSize.width, streamingSize.height)
    }

    fun setAudioBitrate(audioBitRate: Int) {
        this.audioBitRate = audioBitRate
        prepared = false
        stopPreview()
        startPreview()
    }

    private fun prepare(): Boolean {
        if (rtmpCamera == null) return false
        if (!prepared) {
            // use landscape size as base frame
            val streamingSize = preset.toResolution()
            Log.i(TAG, "prepare currentOrientation $currentOrientation")
            Log.i(TAG, "prepare streamingSize ${streamingSize.width} ${streamingSize.height}")
            prepared = rtmpCamera!!.prepareVideo(
                streamingSize.width,
                streamingSize.height,
                1200 * 1000
            ) && rtmpCamera!!.prepareAudio(
                44100,
                true,
                audioBitRate ?: (128 * 1000)
            )
        }

        return prepared
    }

    fun startPreview() {
        Log.i(
            TAG,
            "startPreview: isOnPreview ${rtmpCamera?.isOnPreview} isRecording ${rtmpCamera?.isRecording} isStreaming ${rtmpCamera?.isStreaming}"
        )

        //check if onPreview
        if (rtmpCamera?.isOnPreview != true) {
            Log.i(TAG, "startPreview")
            if (!prepare()) {
                Log.e(TAG, "prepare failed")
                throw RuntimeException("Prepare failed")
            }
            val streamingSize = getVideoSize()
            Log.i(TAG, "currentOrientation $currentOrientation")
            Log.i(TAG, "streamingSize ${streamingSize.width} ${streamingSize.height}")
            rtmpCamera?.startPreview(
                getSurface(),
                streamingSize.width,
                streamingSize.height
            )
        }

    }

    fun stopPreview() {
        rtmpCamera?.stopPreview()
    }

    fun startStream(url: String?, result: MethodChannel.Result) {
        Log.i(
            TAG,
            "startVideoStreaming: isOnPreview ${rtmpCamera?.isOnPreview} isRecording ${rtmpCamera?.isRecording} isStreaming ${rtmpCamera?.isStreaming}"
        )
        if (url == null) {
            result.error("startVideoStreaming", "Must specify a url.", null)
            return
        }

        try {
            if (rtmpCamera?.isStreaming == false) {
                if (prepare()) {
                    // ready to start streaming
                    rtmpCamera?.startStream(url)
                } else {
                    result.error(
                        "videoStreamingFailed",
                        "Error preparing stream, This device cant do it",
                        null
                    )
                    return
                }
            }
            result.success(null)
        } catch (e: CameraAccessException) {
            result.error("videoStreamingFailed", e.message, null)
        } catch (e: IOException) {
            result.error("videoStreamingFailed", e.message, null)
        }
    }

    fun stopStream() {
        rtmpCamera?.apply {
            if (isStreaming) stopStream()
        }
    }

    fun setCameraPosition(facing: Facing) {
        if (getCameraFacing() == facing) return
        when (val source = rtmpCamera?.videoSource) {
            is Camera1Source -> {
                source.switchCamera()
            }

            is Camera2Source -> {
                source.switchCamera()
            }
        }
    }

    fun getCameraFacing(): Facing {
        return when (val source = rtmpCamera?.videoSource) {
            is Camera1Source -> {
                source.getCameraFacing()
            }

            is Camera2Source -> {
                source.getCameraFacing()
            }

            else -> throw RuntimeException("Unknown camera source")
        }
    }

    fun isMuted(): Boolean {
        return when (val source = rtmpCamera?.audioSource) {
            is MicrophoneSource -> source.isMuted()
            else -> false
        }
    }

    fun setIsMuted(isMuted: Boolean) {
        when (val source = rtmpCamera?.audioSource) {
            is MicrophoneSource -> {
                if (isMuted) source.mute()
                else source.unMute()
            }
        }
    }

    fun setListenToOrientationChange(listenToOrientationChange: Boolean) {
        if (listenToOrientationChange) {
            sensorRotationManager?.start()
        } else {
            sensorRotationManager?.stop()
        }
    }

    override fun onAuthError() {
    }

    override fun onAuthSuccess() {
    }

    override fun onConnectionFailed(reason: String) {
        rtmpCamera!!.stopStream()

    }

    override fun onConnectionStarted(url: String) {
    }

    override fun onConnectionSuccess() {
    }

    override fun onDisconnect() {
        onDisconnected()
    }

    override fun onNewBitrate(bitrate: Long) {
    }
}
