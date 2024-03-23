import 'dart:io';

import 'package:flutter/material.dart';
import 'package:native_device_orientation/native_device_orientation.dart';

import 'apivideo_live_stream_controller.dart';

/// Widget that displays the camera preview of [controller].
///
class ApiVideoCameraPreview extends StatefulWidget {
  /// Creates a new [ApiVideoCameraPreview] instance for [controller] and a [child] overlay.
  const ApiVideoCameraPreview({
    super.key,
    required this.controller,
    this.fit = BoxFit.contain,
    this.child,
  });

  /// The controller for the camera to display the preview for.
  final ApiVideoLiveStreamController controller;

  /// The [BoxFit] for the video. The [child] is scale to the preview box.
  final BoxFit fit;

  /// A widget to overlay on top of the camera preview. It is scaled to the camera preview [FittedBox].
  final Widget? child;

  @override
  State<ApiVideoCameraPreview> createState() => _ApiVideoCameraPreviewState();
}

class _ApiVideoCameraPreviewState extends State<ApiVideoCameraPreview> {
  _ApiVideoCameraPreviewState() {
    _widgetListener = ApiVideoLiveStreamWidgetListener(onTextureReady: () {
      final int newTextureId = widget.controller.textureId;
      if (newTextureId != _textureId) {
        setState(() {
          _textureId = newTextureId;
        });
      }
    });

    _eventsListener =
        ApiVideoLiveStreamEventsListener(onVideoSizeChanged: (size) {
      _updateAspectRatio(size);
    });
  }

  late ApiVideoLiveStreamWidgetListener _widgetListener;
  late ApiVideoLiveStreamEventsListener _eventsListener;
  late int _textureId;

  double _aspectRatio = 1.77;
  Size _size = const Size(1280, 720);

  @override
  void initState() {
    super.initState();
    _textureId = widget.controller.textureId;
    widget.controller.addWidgetListener(_widgetListener);
    widget.controller.addEventsListener(_eventsListener);
    if (widget.controller.isInitialized) {
      widget.controller.videoSize.then((size) {
        if (size != null) {
          _updateAspectRatio(size);
        }
      });
    }
  }

  @override
  void dispose() {
    widget.controller.stopPreview();
    widget.controller.removeWidgetListener(_widgetListener);
    widget.controller.removeEventsListener(_eventsListener);
    super.dispose();
  }

  BoxFit get boxFit {
    if (Platform.isAndroid)
      return BoxFit.fitHeight;
    else
      return BoxFit.contain;
  }

  @override
  Widget build(BuildContext context) {
    return _textureId == ApiVideoLiveStreamController.kUninitializedTextureId
        ? Container()
        : _buildPreview(context);
  }

  Widget _buildPreview(BuildContext context) {
    return NativeDeviceOrientationReader(builder: (context) {
      final orientation = NativeDeviceOrientationReader.orientation(context);
      return LayoutBuilder(
          builder: (BuildContext context, BoxConstraints constraints) {
        return Stack(alignment: Alignment.center, children: [
          _buildFittedPreview(constraints, orientation),
          _buildFittedOverlay(constraints, orientation)
        ]);
      });
    });
  }

  Widget _buildFittedPreview(
    BoxConstraints constraints,
    NativeDeviceOrientation orientation,
  ) {
    final orientedSize = _size.orientate(orientation);
    final contentSize = orientedSize.sizeWithFit(orientation, widget.fit);

    final spacing = (orientedSize.width - contentSize.width) / 2;

    // See https://github.com/flutter/flutter/issues/17287
    return SizedBox(
      width: constraints.maxWidth,
      height: constraints.maxHeight,
      child: FittedBox(
        fit: widget.fit,
        child: SizedBox(
          width: contentSize.width,
          height: orientedSize.height,
          child: Stack(
            alignment: Alignment.center,
            children: [
              Positioned(
                left: -spacing,
                right: -spacing,
                top: 0,
                bottom: 0,
                child: SizedBox(
                  width: orientedSize.width,
                  height: orientedSize.height,
                  child: widget.controller.buildPreview(),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildFittedOverlay(
      BoxConstraints constraints, NativeDeviceOrientation orientation) {
    final orientedSize = _size.orientate(orientation);
    final fittedSize = applyBoxFit(boxFit, orientedSize, constraints.biggest);
    return SizedBox(
      width: _size.width,
      height: fittedSize.destination.height,
      child: widget.child ?? Container(),
    );
  }

  void _updateAspectRatio(Size newSize) async {
    final double newAspectRatio = newSize.aspectRatio;
    if ((newAspectRatio != _aspectRatio) || (newSize != _size)) {
      if (mounted) {
        setState(() {
          _size = newSize;
          _aspectRatio = newAspectRatio;
        });
      }
    }
  }
}

extension OrientationHelper on NativeDeviceOrientation {
  /// Returns true if the orientation is portrait.
  bool isLandscape() {
    return [
      NativeDeviceOrientation.landscapeLeft,
      NativeDeviceOrientation.landscapeRight
    ].contains(this);
  }

  /// Returns the number of clockwise quarter turns the orientation is rotated
  int getQuarterTurns() {
    Map<NativeDeviceOrientation, int> turns = {
      NativeDeviceOrientation.unknown: 0,
      NativeDeviceOrientation.portraitUp: 0,
      NativeDeviceOrientation.landscapeRight: 1,
      NativeDeviceOrientation.portraitDown: 2,
      NativeDeviceOrientation.landscapeLeft: 3,
    };
    return turns[this]!;
  }
}

extension OrientedSize on Size {
  /// Returns the size with width and height swapped if [orientation] is portrait.
  Size orientate(NativeDeviceOrientation orientation) {
    if (Platform.isAndroid) {
      return Size(width, height);
    }
    if (orientation.isLandscape()) {
      return Size(width, height);
    } else {
      return Size(height, width);
    }
  }
}

extension FittedSize on Size {
  /// On the Android platform, the size of the stream cannot be changed according to orientation
  /// The solution here is to always use the size in landscape mode,
  /// so when in portrait mode, it will display the frame fit inside that size.
  /// When displayed on the view, needs to be processed to be able to work with BoxFit.
  /// The [orientation] parameter specifies the target device orientation.
  /// The [fit] parameter specifies the `BoxFit` strategy to apply.
  ///
  /// Returns a new `Size` object that represents the size of the current `Size` object
  /// after applying the specified `BoxFit` strategy to fit it within the specified
  /// [orientation].
  /// If the current platform is not Android or the [orientation] is landscape,
  /// the current `Size` object is returned unchanged.
  Size sizeWithFit(NativeDeviceOrientation orientation, BoxFit fit) {
    if (!Platform.isAndroid) {
      return this;
    }
    if (orientation == NativeDeviceOrientation.landscapeLeft ||
        orientation == NativeDeviceOrientation.landscapeRight) {
      return this;
    }

    switch (fit) {
      case BoxFit.fill:
      case BoxFit.cover:
      case BoxFit.fitWidth:
      case BoxFit.contain:
        final contentSize = Size(height, width);
        final fittedHeight = contentSize.width;
        final fittedWidth = height * contentSize.width / width;
        return Size(fittedWidth, fittedHeight);
      case BoxFit.fitHeight:
      case BoxFit.none:
      case BoxFit.scaleDown:
        return this;
    }
  }
}
