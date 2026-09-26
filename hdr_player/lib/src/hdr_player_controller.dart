import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'hdr_support.dart';

class HdrPlayerEvent {
  final Duration? position;
  final Duration? duration;
  final Duration? buffered;
  final bool? playing;
  final bool? buffering;
  final bool? completed;
  final int? width;
  final int? height;
  final double? pixelRatio;
  final int? rotation;
  final String? error;

  const HdrPlayerEvent({
    this.position,
    this.duration,
    this.buffered,
    this.playing,
    this.buffering,
    this.completed,
    this.width,
    this.height,
    this.pixelRatio,
    this.rotation,
    this.error,
  });
}

class ToneMapOptions {
  final double targetPeakNits;
  final double strength;
  final double saturation;
  final double highlightBoost;
  final double preDarken;
  final double highlightProtect;

  const ToneMapOptions({
    this.targetPeakNits = 1000.0,
    this.strength = 1.0,
    this.saturation = 1.0,
    this.highlightBoost = 1.0,
    this.preDarken = 0.0,
    this.highlightProtect = 0.65,
  });

  Map<String, dynamic> toMap() => {
    'targetPeakNits': targetPeakNits,
    'strength': strength,
    'saturation': saturation,
    'highlightBoost': highlightBoost,
    'preDarken': preDarken,
    'highlightProtect': highlightProtect,
  };
}

class HdrPlayerController {
  static const MethodChannel _globalChannel = MethodChannel('hdr_player');

  int? _viewId;
  MethodChannel? _channel;
  EventChannel? _eventChannel;
  StreamSubscription? _eventSub;
  final StreamController<HdrPlayerEvent> _events =
      StreamController<HdrPlayerEvent>.broadcast();

  _PendingPrepare? _pendingPrepare;
  ToneMapOptions _toneMapOptions = const ToneMapOptions();

  Stream<HdrPlayerEvent> get events => _events.stream;

  bool get isAttached => _viewId != null;

  static Future<HdrSupportResult> checkHdrSupport() async {
    if (!Platform.isAndroid) {
      return const HdrSupportResult(
        displaySupported: false,
        eglSupported: false,
        reason: 'platform_not_android',
        hdrTypes: <int>[],
      );
    }
    try {
      final res = await _globalChannel.invokeMethod<Map>('checkHdrSupport');
      if (res == null) {
        return const HdrSupportResult(
          displaySupported: false,
          eglSupported: false,
          reason: 'no_result',
          hdrTypes: <int>[],
        );
      }
      return HdrSupportResult.fromMap(res);
    } on PlatformException catch (e) {
      debugPrint('HdrSupport check failed: ${e.code} ${e.message}');
      return const HdrSupportResult(
        displaySupported: false,
        eglSupported: false,
        reason: 'platform_exception',
        hdrTypes: <int>[],
      );
    } catch (e) {
      debugPrint('HdrSupport check failed: $e');
      return const HdrSupportResult(
        displaySupported: false,
        eglSupported: false,
        reason: 'platform_exception',
        hdrTypes: <int>[],
      );
    }
  }

  Future<void> attach(int viewId) async {
    _viewId = viewId;
    debugPrint('HdrPlayerController.attach viewId=$viewId');
    _channel = MethodChannel('hdr_player/view_$viewId');
    _eventChannel = EventChannel('hdr_player/view_$viewId/events');
    _eventSub?.cancel();
    _eventSub = _eventChannel!.receiveBroadcastStream().listen(
      _handleEvent,
      onError: _handleError,
    );

    // Sync tone map options on attach.
    await _invoke('setToneMapOptions', _toneMapOptions.toMap());

    if (_pendingPrepare != null) {
      final pending = _pendingPrepare!;
      _pendingPrepare = null;
      await prepare(
        videoUrl: pending.videoUrl,
        audioUrl: pending.audioUrl,
        headers: pending.headers,
        autoplay: pending.autoplay,
        isLive: pending.isLive,
        startPositionMs: pending.startPositionMs,
      );
    }
  }

  Future<void> prepare({
    required String videoUrl,
    String? audioUrl,
    Map<String, String>? headers,
    bool autoplay = true,
    bool isLive = false,
    int? startPositionMs,
  }) async {
    if (!isAttached) {
      _pendingPrepare = _PendingPrepare(
        videoUrl: videoUrl,
        audioUrl: audioUrl,
        headers: headers,
        autoplay: autoplay,
        isLive: isLive,
        startPositionMs: startPositionMs,
      );
      return;
    }
    await _invoke('prepare', {
      'videoUrl': videoUrl,
      'audioUrl': audioUrl,
      'headers': headers,
      'autoplay': autoplay,
      'isLive': isLive,
      'startPositionMs': startPositionMs,
    });
  }

  Future<void> play() async => _invoke('play');
  Future<void> pause() async => _invoke('pause');
  Future<void> seekTo(Duration position) async =>
      _invoke('seekTo', {'positionMs': position.inMilliseconds});
  Future<void> setRate(double rate) async => _invoke('setRate', {'rate': rate});
  Future<void> setVolume(double volume) async =>
      _invoke('setVolume', {'volume': volume});

  Future<void> setToneMapOptions(ToneMapOptions options) async {
    _toneMapOptions = options;
    await _invoke('setToneMapOptions', options.toMap());
  }

  Future<void> release() async {
    await _invoke('release');
    await _eventSub?.cancel();
    _eventSub = null;
    _viewId = null;
    _channel = null;
    _eventChannel = null;
    _pendingPrepare = null;
  }

  Future<void> _invoke(String method, [Map<String, dynamic>? args]) async {
    if (_channel == null) return;
    await _channel!.invokeMethod(method, args);
  }

  void _handleEvent(dynamic event) {
    if (event is Map) {
      final positionMs = event['positionMs'] as int?;
      final durationMs = event['durationMs'] as int?;
      final bufferedMs = event['bufferedMs'] as int?;
      final playing = event['playing'] as bool?;
      final buffering = event['buffering'] as bool?;
      final completed = event['completed'] as bool?;
      final width = event['width'] as int?;
      final height = event['height'] as int?;
      final pixelRatio = (event['pixelRatio'] as num?)?.toDouble();
      final rotation = event['rotation'] as int?;
      final error = event['error'] as String?;
      _events.add(
        HdrPlayerEvent(
          position: positionMs != null
              ? Duration(milliseconds: positionMs)
              : null,
          duration: durationMs != null
              ? Duration(milliseconds: durationMs)
              : null,
          buffered: bufferedMs != null
              ? Duration(milliseconds: bufferedMs)
              : null,
          playing: playing,
          buffering: buffering,
          completed: completed,
          width: width,
          height: height,
          pixelRatio: pixelRatio,
          rotation: rotation,
          error: error,
        ),
      );
    }
  }

  void _handleError(Object error) {
    _events.add(HdrPlayerEvent(error: error.toString()));
  }
}

class _PendingPrepare {
  final String videoUrl;
  final String? audioUrl;
  final Map<String, String>? headers;
  final bool autoplay;
  final bool isLive;
  final int? startPositionMs;

  _PendingPrepare({
    required this.videoUrl,
    this.audioUrl,
    this.headers,
    required this.autoplay,
    required this.isLive,
    this.startPositionMs,
  });
}
