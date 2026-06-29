
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../app.dart';
import '../utils/theme.dart';

class PrivacyPage extends StatelessWidget {
  const PrivacyPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.bgDark,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 40),
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  gradient: AppTheme.accentGradient,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: const Icon(Icons.shield_outlined, size: 40, color: Colors.white),
              ),
              const SizedBox(height: 24),
              const Text(
                '隐私声明',
                style: TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.bold,
                  color: AppTheme.textPrimary,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                '在使用 Chat Mind 之前，请了解以下重要信息',
                style: TextStyle(
                  fontSize: 14,
                  color: AppTheme.textSecondary.withOpacity(0.8),
                ),
              ),
              const SizedBox(height: 32),
              Expanded(
                child: SingleChildScrollView(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _buildSection('1. 数据上传', '您选择的聊天内容将被上传至服务器进行心理学、行为学、社会学、进化心理学分析。数据中可能包含您的聊天记录、头像、昵称等信息。'),
                      const SizedBox(height: 20),
                      _buildSection('2. 数据安全', 'Chat Mind 不在服务器端存储您的信息和分析结果。所有数据仅在您的设备和 API 之间传输。'),
                      const SizedBox(height: 20),
                      _buildSection('3. 敏感信息提醒', '请勿上传包含密码、身份证号、银行卡号、地址等敏感隐私信息的数据。'),
                      const SizedBox(height: 20),
                      _buildSection('4. API Key', '您需要自行注册 API Key 并承担相关调用费用。Key 将加密存储在您的设备本地。'),
                      const SizedBox(height: 20),
                      _buildSection('5. 悬浮窗权限', '本应用需要悬浮窗权限才能在聊天界面上方显示分析结果，您可以在系统设置中随时关闭。'),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                child: Container(
                  decoration: BoxDecoration(
                    gradient: AppTheme.accentGradient,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: ElevatedButton(
                    onPressed: () {
                      context.read<AppState>().agreePrivacy();
                    },
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.transparent,
                      shadowColor: Colors.transparent,
                      padding: const EdgeInsets.symmetric(vertical: 16),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    child: const Text(
                      '我已阅读并同意',
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSection(String title, String content) {
    return Column(
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
        const SizedBox(height: 6),
        Text(
          content,
          style: const TextStyle(
            fontSize: 14,
            height: 1.6,
            color: AppTheme.textSecondary,
          ),
        ),
      ],
    );
  }
}
