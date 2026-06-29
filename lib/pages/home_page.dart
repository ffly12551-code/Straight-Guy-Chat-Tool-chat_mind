import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import '../utils/theme.dart';
import '../utils/toast.dart';
import '../utils/constants.dart';
import '../models/analysis_result.dart';
import '../services/storage_service.dart';
import '../services/permission_service.dart';
import '../services/screenshot_service.dart';
import '../services/float_window_service.dart';
import '../services/ai_service.dart';
import 'settings_page.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  String _currentScene = Constants.scenes.first;
  bool _isAnalyzing = false;
  StreamSubscription<ScreenshotData>? _screenshotSub;

  @override
  void initState() {
    super.initState();
    _currentScene = StorageService().currentScene;
    _screenshotSub = ScreenshotService.selectionComplete.listen((data) {
      _analyzeImage(data.image, scene: data.scene);
    });
  }

  @override
  void dispose() {
    _screenshotSub?.cancel();
    super.dispose();
  }

  void _setScene(String scene) {
    setState(() => _currentScene = scene);
    StorageService().setScene(scene);
  }

  Future<void> _pickImage() async {
    final picker = ImagePicker();
    final picked = await picker.pickImage(
      source: ImageSource.gallery,
      imageQuality: 85,
    );
    if (picked == null) return;
    final bytes = await picked.readAsBytes();
    _analyzeImage(bytes);
  }

  Future<void> _startScreenshotSelection() async {
    final hasOverlay = await PermissionService.checkOverlayPermission();
    if (!hasOverlay) {
      final granted = await PermissionService.requestOverlayPermission();
      if (!granted) {
        AppToast.show(context, '需要悬浮窗权限才能截图');
        return;
      }
    }

    try {
      await ScreenshotService.showSelectionOverlay();
      AppToast.show(context, '请在屏幕上框选聊天区域');
    } catch (e) {
      AppToast.show(context, '启动框选失败: $e');
    }
  }

  Future<void> _analyzeImage(Uint8List imageBytes, {String? scene}) async {
    if (_isAnalyzing) return;

    setState(() => _isAnalyzing = true);

    try {
      final baseValues = StorageService().baseValues;
      final sceneValues = StorageService().sceneValues;

      final result = await AIService.analyzeImage(
        imageBytes,
        scene: scene ?? _currentScene,
        baseValues: baseValues,
        sceneValues: sceneValues,
      );

      if (mounted) {
        _showResultDialog(result);
      }

      await FloatWindowService.showAnalysisPanel(
        result,
        scene: scene ?? _currentScene,
      );
    } catch (e) {
      AppToast.show(context, '分析失败: $e');
    } finally {
      if (mounted) {
        setState(() => _isAnalyzing = false);
      }
    }
  }

  void _showResultDialog(AnalysisResult result) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AnalysisResultDialog(result: result),
    );
  }

  Future<void> _openFloatWindow() async {
    final hasOverlay = await PermissionService.checkOverlayPermission();
    if (!hasOverlay) {
      final granted = await PermissionService.requestOverlayPermission();
      if (!granted) {
        AppToast.show(context, '需要悬浮窗权限');
        return;
      }
    }

    final success = await FloatWindowService.showFloatBall();
    if (success) {
      AppToast.show(context, '悬浮窗已开启');
    } else {
      AppToast.show(context, '悬浮窗开启失败');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bgDark,
      appBar: AppBar(
        title: ShaderMask(
          shaderCallback: (bounds) =>
              AppTheme.accentGradient.createShader(bounds),
          child: const Text(
            'Chat Mind',
            style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
          ),
        ),
        actions: [
          IconButton(
            icon: const Icon(
              Icons.settings_outlined,
              color: AppTheme.textSecondary,
            ),
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const SettingsPage()),
            ),
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: 8),
            _buildSceneSelector(),
            const SizedBox(height: 24),
            _buildMainButton(
              icon: Icons.photo_library_outlined,
              title: '从相册选择',
              subtitle: '选择已保存的聊天截图',
              onTap: _pickImage,
            ),
            const SizedBox(height: 16),
            _buildMainButton(
              icon: Icons.layers_outlined,
              title: '打开悬浮窗',
              subtitle: '在其他APP上方显示分析结果',
              onTap: _openFloatWindow,
              isSecondary: true,
            ),
            const Spacer(),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }

  Widget _buildSceneSelector() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          '当前场景',
          style: TextStyle(fontSize: 14, color: AppTheme.textMuted),
        ),
        const SizedBox(height: 10),
        Wrap(
          spacing: 10,
          runSpacing: 10,
          children: Constants.scenes.map((scene) {
            final isSelected = scene == _currentScene;
            return GestureDetector(
              onTap: () => _setScene(scene),
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 8,
                ),
                decoration: BoxDecoration(
                  gradient: isSelected ? AppTheme.accentGradient : null,
                  color: isSelected ? null : AppTheme.bgCard,
                  borderRadius: BorderRadius.circular(20),
                  border: isSelected
                      ? null
                      : Border.all(color: AppTheme.textMuted.withOpacity(0.3)),
                ),
                child: Text(
                  scene,
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: isSelected
                        ? FontWeight.w600
                        : FontWeight.normal,
                    color: isSelected ? Colors.white : AppTheme.textSecondary,
                  ),
                ),
              ),
            );
          }).toList(),
        ),
      ],
    );
  }

  Widget _buildMainButton({
    required IconData icon,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
    bool isSecondary = false,
  }) {
    return GestureDetector(
      onTap: _isAnalyzing ? null : onTap,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: isSecondary
              ? AppTheme.bgCard.withOpacity(0.5)
              : AppTheme.bgCard,
          borderRadius: BorderRadius.circular(16),
          border: isSecondary
              ? Border.all(color: AppTheme.textMuted.withOpacity(0.15))
              : null,
        ),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                gradient: isSecondary ? null : AppTheme.accentGradient,
                color: isSecondary ? AppTheme.bgDark : null,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(
                icon,
                color: isSecondary ? AppTheme.accentStart : Colors.white,
                size: 24,
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                      color: AppTheme.textPrimary,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    subtitle,
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppTheme.textMuted,
                    ),
                  ),
                ],
              ),
            ),
            const Icon(
              Icons.arrow_forward_ios,
              size: 16,
              color: AppTheme.textMuted,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTips() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.bgCard.withOpacity(0.4),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppTheme.textMuted.withOpacity(0.1)),
      ),
      child: const Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '使用说明',
            style: TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
              color: AppTheme.textSecondary,
            ),
          ),
          SizedBox(height: 8),
          Text(
            '1. 在微信/QQ等聊天页面截图或选择已有截图\n'
            '2. AI自动分析对方心理、意图和回复建议\n'
            '3. 使用悬浮窗可在聊天时实时查看分析结果\n'
            '4. 建议先在设置中配置个人价值观，让回复更贴合你',
            style: TextStyle(
              fontSize: 12,
              height: 1.7,
              color: AppTheme.textMuted,
            ),
          ),
        ],
      ),
    );
  }
}

class AnalysisResultDialog extends StatelessWidget {
  final AnalysisResult result;
  const AnalysisResultDialog({super.key, required this.result});

  void _copy(BuildContext ctx, String text) {
    Clipboard.setData(ClipboardData(text: text));
    AppToast.show(ctx, '已复制到剪贴板');
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: Colors.transparent,
      insetPadding: const EdgeInsets.all(16),
      child: Container(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.of(context).size.height * 0.8,
        ),
        decoration: BoxDecoration(
          color: AppTheme.bgCard.withOpacity(0.95),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: AppTheme.textMuted.withOpacity(0.1)),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
              decoration: BoxDecoration(
                gradient: AppTheme.accentGradient,
                borderRadius: const BorderRadius.vertical(
                  top: Radius.circular(20),
                ),
              ),
              child: Row(
                children: [
                  const Icon(Icons.psychology, color: Colors.white),
                  const SizedBox(width: 10),
                  const Expanded(
                    child: Text(
                      'AI 分析结果',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                        color: Colors.white,
                      ),
                    ),
                  ),
                  GestureDetector(
                    onTap: () => Navigator.pop(context),
                    child: const Icon(Icons.close, color: Colors.white70),
                  ),
                ],
              ),
            ),
            Flexible(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (result.riskWarning != null &&
                        result.riskWarning!.isNotEmpty)
                      _buildRiskCard(result.riskWarning!),
                    _buildSectionCard(
                      '💭 对方心理',
                      result.psychology,
                      Icons.sentiment_satisfied_outlined,
                    ),
                    const SizedBox(height: 16),
                    _buildSectionCard(
                      '🎯 对方意图',
                      result.intention,
                      Icons.track_changes_outlined,
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      '💬 回复建议',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                        color: AppTheme.textPrimary,
                      ),
                    ),
                    const SizedBox(height: 12),
                    ...result.replies.map(
                      (reply) => _buildReplyCard(context, reply),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildRiskCard(String text) {
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppTheme.danger.withOpacity(0.15),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppTheme.danger.withOpacity(0.3)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(
            Icons.warning_amber_rounded,
            color: AppTheme.danger,
            size: 20,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              text,
              style: const TextStyle(
                fontSize: 14,
                color: AppTheme.danger,
                height: 1.5,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSectionCard(String title, String content, IconData icon) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.bgDark.withOpacity(0.5),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 18, color: AppTheme.accentStart),
              const SizedBox(width: 8),
              Text(
                title,
                style: const TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w600,
                  color: AppTheme.textPrimary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            content,
            style: const TextStyle(
              fontSize: 14,
              height: 1.6,
              color: AppTheme.textSecondary,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildReplyCard(BuildContext context, ReplyOption reply) {
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppTheme.bgDark.withOpacity(0.4),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppTheme.accentStart.withOpacity(0.2)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              gradient: AppTheme.accentGradient,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Text(
              reply.style,
              style: const TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: Colors.white,
              ),
            ),
          ),
          const SizedBox(height: 10),
          Text(
            reply.content,
            style: const TextStyle(
              fontSize: 14,
              height: 1.6,
              color: AppTheme.textPrimary,
            ),
          ),
          const SizedBox(height: 10),
          Align(
            alignment: Alignment.centerRight,
            child: GestureDetector(
              onTap: () => _copy(context, reply.content),
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 6,
                ),
                decoration: BoxDecoration(
                  color: AppTheme.accentStart.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.copy, size: 14, color: AppTheme.accentStart),
                    SizedBox(width: 4),
                    Text(
                      '复制',
                      style: TextStyle(
                        fontSize: 12,
                        color: AppTheme.accentStart,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
