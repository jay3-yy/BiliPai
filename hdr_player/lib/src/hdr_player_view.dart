import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';

import 'hdr_player_controller.dart';

class HdrPlayerView extends StatelessWidget {
  const HdrPlayerView({
    super.key,
    required this.controller,
    this.fill = Colors.black,
  });

  final HdrPlayerController controller;
  final Color fill;

  @override
  Widget build(BuildContext context) {
    if (!Platform.isAndroid) {
      return ColoredBox(color: fill);
    }

    return PlatformViewLink(
      viewType: 'hdr_player_view',
      surfaceFactory: (context, platformController) {
        return AndroidViewSurface(
          controller: platformController as AndroidViewController,
          gestureRecognizers: const <Factory<OneSequenceGestureRecognizer>>{},
          hitTestBehavior: PlatformViewHitTestBehavior.transparent,
        );
      },
      onCreatePlatformView: (params) {
        final controllerView = PlatformViewsService.initSurfaceAndroidView(
          id: params.id,
          viewType: 'hdr_player_view',
          layoutDirection: TextDirection.ltr,
          creationParams: <String, dynamic>{},
          creationParamsCodec: const StandardMessageCodec(),
        );
        controllerView.addOnPlatformViewCreatedListener((id) {
          params.onPlatformViewCreated(id);
          controller.attach(id);
        });
        controllerView.create();
        return controllerView;
      },
    );
  }
}
