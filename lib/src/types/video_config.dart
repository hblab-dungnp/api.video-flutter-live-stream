import 'package:json_annotation/json_annotation.dart';

import 'resolution.dart';

part 'video_config.g.dart';

/// Live streaming video configuration.
@JsonSerializable()
class VideoConfig {
  /// The video bitrate in bps
  int bitrate;

  /// The live streaming video resolution
  Resolution resolution;

  /// The video frame rate in fps
  int fps;

  /// Creates a [VideoConfig] instance
  VideoConfig(
      {required this.bitrate,
      this.resolution = Resolution.RESOLUTION_720,
      this.fps = 30})
      : assert(bitrate > 0),
        assert(fps > 0);

  /// Creates a [VideoConfig] instance where bitrate is set according to the given [resolution].
  VideoConfig.withDefaultBitrate(
      {this.resolution = Resolution.RESOLUTION_720, this.fps = 30})
      : assert(fps > 0),
        bitrate = _getDefaultBitrate(resolution);

  /// Creates a [VideoConfig] from a [json] map.
  factory VideoConfig.fromJson(Map<String, dynamic> json) =>
      _$VideoConfigFromJson(json);

  /// Creates a json map from a [VideoConfig].
  Map<String, dynamic> toJson() => _$VideoConfigToJson(this);

  /// Returns the default bitrate for the given [resolution].
  static int _getDefaultBitrate(Resolution resolution) {
    switch (resolution) {
      case Resolution.RESOLUTION_240:
        return 93750;
      case Resolution.RESOLUTION_360:
        return 187500;
      case Resolution.RESOLUTION_480:
        return 375000;
      case Resolution.RESOLUTION_720:
        return 750000;
      case Resolution.RESOLUTION_1080:
        return 1500000;
    }
  }
}
