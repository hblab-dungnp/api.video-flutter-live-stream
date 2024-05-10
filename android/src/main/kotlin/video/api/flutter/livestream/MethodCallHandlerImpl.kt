package video.api.flutter.livestream

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Size
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.TextureRegistry
import video.api.flutter.livestream.utils.addTrailingSlashIfNeeded
import video.api.flutter.livestream.utils.toFacing
import video.api.flutter.livestream.utils.toKey
import video.api.flutter.livestream.utils.toPreset

class MethodCallHandlerImpl(
    private val context: Context,
    messenger: BinaryMessenger,
    private val textureRegistry: TextureRegistry
) : MethodChannel.MethodCallHandler {
    private val methodChannel = MethodChannel(messenger, METHOD_CHANNEL_NAME)
    private val eventChannel = EventChannel(messenger, EVENT_CHANNEL_NAME)
    private var eventSink: EventChannel.EventSink? = null

    private var flutterView: CameraNativeView? = null

    fun startListening() {
        methodChannel.setMethodCallHandler(this)
        eventChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                eventSink = events
            }

            override fun onCancel(arguments: Any?) {
                eventSink?.endOfStream()
                eventSink = null
            }
        })
    }

    fun stopListening() {
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        flutterView?.dispose()
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "create" -> {
                try {
                    flutterView?.dispose()
                    flutterView =
                        CameraNativeView(
                            context,
                            textureRegistry,
                            preset = CameraNativeView.ResolutionPreset.low,
                            onVideoSizeChanged = { sendVideoSizeChanged(it) },
                            onDisconnected = { sendDisconnected() },
                        )
                    result.success(mapOf("textureId" to flutterView!!.textureId))
                } catch (e: Exception) {
                    result.error("failed_to_create_live_stream", e.message, null)
                }
            }

            "dispose" -> {
                flutterView = null
            }

            "setVideoConfig" -> {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val videoConfig = (call.arguments as Map<String, Any>)
                    val preset = (videoConfig["resolution"] as String).toPreset()
                    val bitrate = videoConfig["bitrate"] as Int
                    flutterView!!.setPreset(preset, bitrate)
                    result.success(null)
                } catch (e: Exception) {
                    result.error("failed_to_set_video_config", e.message, null)
                }
            }

            "setAudioConfig" -> {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val audioConfig = (call.arguments as Map<String, Any>)
                    flutterView!!.setAudioBitrate(audioConfig["bitrate"] as Int)
                    result.success(null)
                } catch (e: Exception) {
                    result.error("failed_to_set_audio_config", e.message, null)
                }
            }

            "startPreview" -> {
                try {
                    flutterView!!.startPreview()
                    result.success(null)
                } catch (e: Exception) {
                    result.error("failed_to_start_preview", e.message, null)
                }
            }

            "stopPreview" -> {
                flutterView?.stopPreview()
                result.success(null)
            }

            "startStreaming" -> {
                val streamKey = call.argument<String>("streamKey")
                val url = call.argument<String>("url")
                when {
                    streamKey == null -> result.error(
                        "missing_stream_key", "Stream key is missing", null
                    )

                    streamKey.isEmpty() -> result.error(
                        "empty_stream_key", "Stream key is empty", null
                    )

                    url == null -> result.error(
                        "missing_rtmp_url",
                        "RTMP URL is missing",
                        null
                    )

                    url.isEmpty() -> result.error("empty_rtmp_url", "RTMP URL is empty", null)

                    else -> flutterView!!.startStream(
                        url.addTrailingSlashIfNeeded() + streamKey,
                        result
                    )

                }
            }

            "stopStreaming" -> {
                flutterView?.stopStream()
                result.success(null)
            }

            "getIsStreaming" -> result.success(mapOf("isStreaming" to flutterView!!.isStreaming()))
            "getCameraPosition" -> {
                try {
                    result.success(mapOf("position" to flutterView!!.getCameraFacing().toKey()))
                } catch (e: Exception) {
                    result.error("failed_to_get_camera_position", e.message, null)
                }
            }

            "setCameraPosition" -> {
                val cameraPosition = try {
                    ((call.arguments as Map<*, *>)["position"] as String)
                } catch (e: Exception) {
                    result.error("invalid_parameter", "Invalid camera position", e)
                    return
                }
                try {
                    flutterView!!.setCameraPosition(cameraPosition.toFacing())
                    result.success(null)
                } catch (e: Exception) {
                    result.error("failed_to_set_camera_position", e.message, null)
                }
            }

            "getIsMuted" -> {
                try {
                    result.success(mapOf("isMuted" to flutterView!!.isMuted()))
                } catch (e: Exception) {
                    result.error("failed_to_get_is_muted", e.message, null)
                }
            }

            "setIsMuted" -> {
                val isMuted = try {
                    ((call.arguments as Map<*, *>)["isMuted"] as Boolean)
                } catch (e: Exception) {
                    result.error("invalid_parameter", "Invalid isMuted", e)
                    return
                }
                try {
                    flutterView!!.setIsMuted(isMuted)
                    result.success(null)
                } catch (e: Exception) {
                    result.error("failed_to_set_is_muted", e.message, null)
                }
            }

            "getVideoSize" -> {
                try {
                    val videoSize = flutterView!!.getVideoSize()
                    result.success(
                        mapOf(
                            "width" to videoSize.width.toDouble(),
                            "height" to videoSize.height.toDouble()
                        )
                    )
                } catch (e: Exception) {
                    result.error("failed_to_get_video_size", e.message, null)
                }
            }

            "setListenToOrientationChange" -> {
                try {
                    when (val listenToOrientationChange = call.argument<Boolean>("listenToOrientationChange")) {
                        null -> result.error(
                            "missing_listen_to_orientation_change_key", "ListenToOrientationChange key is missing", null
                        )
                        else -> {
                            flutterView!!.setListenToOrientationChange(listenToOrientationChange)
                            result.success(null)
                        }
                    }

                } catch (e: Exception) {
                    result.error("failed_to_set_listen_to_orientation_change", e.message, null)
                }
            }

            else -> result.notImplemented()
        }
    }

    private fun sendEvent(type: String) {
        Handler(Looper.getMainLooper()).post {
            eventSink?.success(mapOf("type" to type))
        }
    }

    private fun sendConnected() {
        sendEvent("connected")
    }

    private fun sendDisconnected() {
        sendEvent("disconnected")
    }

    private fun sendConnectionFailed(message: String) {
        Handler(Looper.getMainLooper()).post {
            eventSink?.success(mapOf("type" to "connectionFailed", "message" to message))
        }
    }

    private fun sendError(error: Exception) {
        Handler(Looper.getMainLooper()).post {
            eventSink?.error(error::class.java.name, error.message, error)
        }
    }

    private fun sendVideoSizeChanged(resolution: Size) {
        Handler(Looper.getMainLooper()).post {
            eventSink?.success(
                mapOf(
                    "type" to "videoSizeChanged",
                    "width" to resolution.width.toDouble(),
                    "height" to resolution.height.toDouble() // Dart size fields are in double
                )
            )
        }
    }

    companion object {
        private const val METHOD_CHANNEL_NAME = "video.api.livestream/controller"
        private const val EVENT_CHANNEL_NAME = "video.api.livestream/events"
    }
}
