
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'utils/theme.dart';
import 'services/storage_service.dart';
import 'services/float_window_service.dart';
import 'services/screenshot_service.dart';
import 'pages/privacy_page.dart';
import 'pages/home_page.dart';

class AppState extends ChangeNotifier {
  bool _initialized = false;
  bool _privacyAgreed = false;

  bool get initialized => _initialized;
  bool get privacyAgreed => _privacyAgreed;

  Future<void> init() async {
    await StorageService().init();
    FloatWindowService.init();
    ScreenshotService.init();
    _privacyAgreed = StorageService().privacyAgreed;
    _initialized = true;
    notifyListeners();
  }

  void agreePrivacy() {
    _privacyAgreed = true;
    StorageService().setPrivacyAgreed(true);
    notifyListeners();
  }
}

class ChatMindApp extends StatelessWidget {
  const ChatMindApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (_) => AppState()..init(),
      child: MaterialApp(
        title: 'ChatMind',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.darkTheme,
        home: Consumer<AppState>(
          builder: (context, state, _) {
            if (!state.initialized) {
              return const Scaffold(
                backgroundColor: AppTheme.bgDark,
                body: Center(
                  child: CircularProgressIndicator(color: AppTheme.accentStart),
                ),
              );
            }
            if (!state.privacyAgreed) {
              return const PrivacyPage();
            }
            return const HomePage();
          },
        ),
      ),
    );
  }
}
