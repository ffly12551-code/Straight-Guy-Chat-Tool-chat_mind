import 'dart:async';
import 'package:flutter/services.dart';
import '../models/analysis_result.dart';

class FloatWindowService {
  static const MethodChannel _channel = MethodChannel(
    'com.chatmind/floatwindow',
  );
  static final StreamController<String> _actionController =
      StreamController<String>.broadcast();

  static Stream<String> get actionStream => _actionController.stream;

  static Future<void> init() async {
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onAction') {
        _actionController.add(call.arguments as String);
      }
    });
  }

  static Future<bool> showFloatBall() async {
    try {
      final result = await _channel.invokeMethod('showFloatBall');
      return result == true;
    } catch (e) {
      return false;
    }
  }

  static Future<bool> hideFloatBall() async {
    try {
      final result = await _channel.invokeMethod('hideFloatBall');
      return result == true;
    } catch (e) {
      return false;
    }
  }

  static Future<bool> showAnalysisPanel(
    AnalysisResult result, {
    String? scene,
  }) async {
    try {
      final resultMap = {
        'psychology': result.psychology,
        'intention': result.intention,
        'riskWarning': result.riskWarning,
        'scene': scene,
        'replies': result.replies
            .map((r) => {'style': r.style, 'content': r.content})
            .toList(),
      };
      final res = await _channel.invokeMethod('showAnalysisPanel', resultMap);
      return res == true;
    } catch (e) {
      return false;
    }
  }

  static Future<bool> hideAnalysisPanel() async {
    try {
      final result = await _channel.invokeMethod('hideAnalysisPanel');
      return result == true;
    } catch (e) {
      return false;
    }
  }

  static Future<bool> updateOpacity(double opacity) async {
    try {
      final result = await _channel.invokeMethod('updateOpacity', {
        'opacity': opacity,
      });
      return result == true;
    } catch (e) {
      return false;
    }
  }
}
