import 'dart:async';
import 'dart:typed_data';
import 'package:flutter/services.dart';

class ScreenshotData {
  final Uint8List image;
  final String scene;

  ScreenshotData({required this.image, required this.scene});
}

class ScreenshotService {
  static const MethodChannel _channel = MethodChannel(
    'com.chatmind/screenshot',
  );
  static final StreamController<ScreenshotData> _selectionController =
      StreamController<ScreenshotData>.broadcast();

  static Stream<ScreenshotData> get selectionComplete =>
      _selectionController.stream;

  static void init() {
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onSelectionScreenshot') {
        final args = call.arguments as Map<dynamic, dynamic>?;
        if (args != null) {
          final bytes = args['image'] as Uint8List?;
          final scene = args['scene'] as String? ?? '职场';
          if (bytes != null) {
            _selectionController.add(
              ScreenshotData(image: bytes, scene: scene),
            );
          }
        }
      }
    });
  }

  static Future<void> showSelectionOverlay() async {
    try {
      await _channel.invokeMethod('showSelectionOverlay');
    } catch (e) {
      throw Exception('显示框选层失败: $e');
    }
  }

  static Future<void> hideSelectionOverlay() async {
    try {
      await _channel.invokeMethod('hideSelectionOverlay');
    } catch (e) {
      throw Exception('隐藏框选层失败: $e');
    }
  }

  static Future<Uint8List?> captureArea(
    int x,
    int y,
    int width,
    int height,
  ) async {
    try {
      final result = await _channel.invokeMethod('captureArea', {
        'x': x,
        'y': y,
        'width': width,
        'height': height,
      });
      if (result != null) {
        return result as Uint8List;
      }
      return null;
    } catch (e) {
      throw Exception('区域截图失败: $e');
    }
  }
}
