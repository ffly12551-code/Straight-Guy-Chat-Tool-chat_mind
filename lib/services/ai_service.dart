import 'dart:convert';
import 'dart:typed_data';
import 'package:http/http.dart' as http;
import '../models/analysis_result.dart';
import '../utils/constants.dart';
import 'storage_service.dart';

class AIService {
  static Future<AnalysisResult> analyzeImage(
    Uint8List imageBytes, {
    required String scene,
    required List<String> baseValues,
    required Map<String, String> sceneValues,
  }) async {
    final apiKey = StorageService().apiKey;
    final model = StorageService().model;

    if (apiKey.isEmpty) {
      throw Exception('请先配置豆包 API Key');
    }

    final base64Image = base64Encode(imageBytes);
    final systemPrompt = _buildSystemPrompt(scene, baseValues, sceneValues);

    final requestBody = {
      'model': model,
      'messages': [
        {'role': 'system', 'content': systemPrompt},
        {
          'role': 'user',
          'content': [
            {
              'type': 'image_url',
              'image_url': {'url': 'data:image/jpeg;base64,$base64Image'},
            },
            {'type': 'text', 'text': '分析这张聊天截图，输出JSON格式结果。'},
          ],
        },
      ],
      'temperature': 0.3,
      'max_tokens': 1000,
    };

    final response = await http.post(
      Uri.parse(Constants.doubaoBaseUrl),
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer $apiKey',
      },
      body: jsonEncode(requestBody),
    );

    if (response.statusCode != 200) {
      throw Exception('AI请求失败: ${response.statusCode} ${response.body}');
    }

    final data = jsonDecode(utf8.decode(response.bodyBytes));
    final content = data['choices']?[0]?['message']?['content'] as String?;

    if (content == null || content.isEmpty) {
      throw Exception('AI返回结果为空');
    }

    return _parseContent(content);
  }

  static String _buildSystemPrompt(
    String scene,
    List<String> baseValues,
    Map<String, String> sceneValues,
  ) {
    final buffer = StringBuffer();
    buffer.writeln('你是一位资深心理咨询师和高情商沟通顾问。');
    buffer.writeln('当前场景：$scene');
    buffer.writeln();

    if (baseValues.isNotEmpty) {
      buffer.writeln('价值观约束：${baseValues.join('; ')}');
      buffer.writeln();
    }

    final sceneValue = sceneValues[scene];
    if (sceneValue != null && sceneValue.isNotEmpty) {
      buffer.writeln('场景约束：$sceneValue');
      buffer.writeln();
    }

    buffer.writeln('分析要求：');
    buffer.writeln('1. 分析对方心理状态、真实意图');
    buffer.writeln('2. 检测风险（PUA、情感操控、职场施压等）');
    buffer.writeln('3. 给出2-3个符合价值观的回复建议');
    buffer.writeln();
    buffer.writeln('输出格式（严格JSON）：');
    buffer.writeln(
      '{"psychology":"心理状态","intention":"意图","risk_warning":"风险","replies":[{"style":"风格","content":"回复"}]}',
    );

    return buffer.toString();
  }

  static AnalysisResult _parseContent(String content) {
    String jsonStr = content.trim();

    if (jsonStr.contains('```json')) {
      jsonStr = jsonStr.split('```json')[1].split('```')[0].trim();
    } else if (jsonStr.contains('```')) {
      jsonStr = jsonStr.split('```')[1].split('```')[0].trim();
    }

    final data = jsonDecode(jsonStr) as Map<String, dynamic>;
    return AnalysisResult.fromJson(data);
  }
}
