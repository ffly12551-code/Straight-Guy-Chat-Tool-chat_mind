
import 'package:flutter/material.dart';
import '../utils/theme.dart';
import '../utils/toast.dart';
import '../utils/constants.dart';
import '../services/storage_service.dart';
import 'values_editor_page.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  late TextEditingController _apiKeyController;
  late TextEditingController _modelController;
  double _opacity = 0.85;
  bool _obscureKey = true;

  @override
  void initState() {
    super.initState();
    _apiKeyController = TextEditingController(text: StorageService().apiKey);
    _modelController = TextEditingController(text: StorageService().model);
    _opacity = StorageService().floatOpacity;
  }

  void _save() {
    StorageService().setApiKey(_apiKeyController.text.trim());
    StorageService().setModel(_modelController.text.trim());
    StorageService().setOpacity(_opacity);
    AppToast.show(context, '保存成功');
  }

  @override
  void dispose() {
    _apiKeyController.dispose();
    _modelController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bgDark,
      appBar: AppBar(
        title: const Text('设置'),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildSectionTitle('API 配置'),
            const SizedBox(height: 12),
            _buildCard(
              child: Column(
                children: [
                  TextField(
                    controller: _apiKeyController,
                    obscureText: _obscureKey,
                    style: const TextStyle(color: AppTheme.textPrimary),
                    decoration: InputDecoration(
                      labelText: '豆包 API Key',
                      labelStyle: const TextStyle(color: AppTheme.textMuted),
                      suffixIcon: IconButton(
                        icon: Icon(
                          _obscureKey ? Icons.visibility_off : Icons.visibility,
                          color: AppTheme.textMuted,
                        ),
                        onPressed: () => setState(() => _obscureKey = !_obscureKey),
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    controller: _modelController,
                    style: const TextStyle(color: AppTheme.textPrimary),
                    decoration: const InputDecoration(
                      labelText: '模型名称',
                      labelStyle: TextStyle(color: AppTheme.textMuted),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              child: Text(
                '请前往火山引擎控制台获取 API Key',
                style: TextStyle(fontSize: 12, color: AppTheme.textMuted.withOpacity(0.7)),
              ),
            ),
            const SizedBox(height: 28),
            _buildSectionTitle('悬浮窗设置'),
            const SizedBox(height: 12),
            _buildCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text('透明度', style: TextStyle(color: AppTheme.textPrimary)),
                      Text(
                        '${(_opacity * 100).toInt()}%',
                        style: const TextStyle(color: AppTheme.accentStart, fontWeight: FontWeight.w600),
                      ),
                    ],
                  ),
                  Slider(
                    value: _opacity,
                    min: 0.3,
                    max: 1.0,
                    divisions: 14,
                    activeColor: AppTheme.accentStart,
                    inactiveColor: AppTheme.textMuted.withOpacity(0.2),
                    onChanged: (v) => setState(() => _opacity = v),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 28),
            _buildSectionTitle('个人价值观'),
            const SizedBox(height: 12),
            _buildCard(
              child: ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    gradient: AppTheme.accentGradient,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Icon(Icons.favorite_border, color: Colors.white, size: 20),
                ),
                title: const Text('价值观编辑器', style: TextStyle(color: AppTheme.textPrimary)),
                subtitle: const Text('让AI回复更贴合你的处事风格', style: TextStyle(color: AppTheme.textMuted, fontSize: 12)),
                trailing: const Icon(Icons.arrow_forward_ios, size: 16, color: AppTheme.textMuted),
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const ValuesEditorPage()),
                ),
              ),
            ),
            const SizedBox(height: 28),
            _buildSectionTitle('关于'),
            const SizedBox(height: 12),
            _buildCard(
              child: Column(
                children: [
                  _buildInfoRow('应用名称', 'ChatMind'),
                  const Divider(color: AppTheme.textMuted, height: 24),
                  _buildInfoRow('版本号', Constants.appVersion),
                ],
              ),
            ),
            const SizedBox(height: 32),
            SizedBox(
              width: double.infinity,
              child: Container(
                decoration: BoxDecoration(
                  gradient: AppTheme.accentGradient,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: ElevatedButton(
                  onPressed: _save,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.transparent,
                    shadowColor: Colors.transparent,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                  ),
                  child: const Text('保存设置', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
                ),
              ),
            ),
            const SizedBox(height: 40),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionTitle(String text) {
    return Text(
      text,
      style: const TextStyle(
        fontSize: 14,
        fontWeight: FontWeight.w600,
        color: AppTheme.textMuted,
      ),
    );
  }

  Widget _buildCard({required Widget child}) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.bgCard.withOpacity(0.6),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppTheme.textMuted.withOpacity(0.1)),
      ),
      child: child,
    );
  }

  Widget _buildInfoRow(String label, String value) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: const TextStyle(color: AppTheme.textSecondary)),
        Text(value, style: const TextStyle(color: AppTheme.textPrimary, fontWeight: FontWeight.w500)),
      ],
    );
  }
}
