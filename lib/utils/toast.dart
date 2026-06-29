
import 'package:flutter/material.dart';
import 'theme.dart';

class AppToast {
  static OverlayEntry? _currentEntry;

  static void show(BuildContext context, String message) {
    hide();
    final overlay = Overlay.of(context);
    _currentEntry = OverlayEntry(
      builder: (_) => _ToastWidget(message: message),
    );
    overlay.insert(_currentEntry!);

    Future.delayed(const Duration(seconds: 2), () {
      hide();
    });
  }

  static void hide() {
    _currentEntry?.remove();
    _currentEntry = null;
  }
}

class _ToastWidget extends StatelessWidget {
  final String message;
  const _ToastWidget({required this.message});

  @override
  Widget build(BuildContext context) {
    return Positioned(
      bottom: 80,
      left: 0,
      right: 0,
      child: Center(
        child: Container(
          margin: const EdgeInsets.symmetric(horizontal: 40),
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
          decoration: BoxDecoration(
            color: AppTheme.bgCard.withValues(alpha: 0.95),
            borderRadius: BorderRadius.circular(10),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.3),
                blurRadius: 10,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: Text(
            message,
            style: const TextStyle(
              fontSize: 14,
              color: AppTheme.textPrimary,
            ),
            textAlign: TextAlign.center,
          ),
        ),
      ),
    );
  }
}
