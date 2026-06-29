
class Constants {
  static const String appName = 'ChatMind';
  static const String appVersion = '1.0.0';

  static const String prefApiKey = 'api_key';
  static const String prefModel = 'model';
  static const String prefScene = 'scene';
  static const String prefOpacity = 'opacity';
  static const String prefValuesBase = 'values_base';
  static const String prefValuesScene = 'values_scene';
  static const String prefPrivacyAgreed = 'privacy_agreed';
  static const String prefFirstLaunch = 'first_launch';

  static const String defaultModel = 'doubao-1.5-vision-pro-32k';
  static const String doubaoBaseUrl = 'https://ark.cn-beijing.volces.com/api/v3/chat/completions';

  static const List<String> scenes = ['职场', '亲密关系', '家庭', '社交'];

  static const List<Map<String, String>> baseValues = [
    {'id': 'sincere', 'text': '待人真诚，不主动算计、挖坑套路别人'},
    {'id': 'no_sarcasm', 'text': '拒绝阴阳怪气、嘲讽、报复性话术'},
    {'id': 'no_beg', 'text': '不卑不亢，不卑微讨好任何人'},
    {'id': 'no_pushover', 'text': '不做无底线老好人，懂得委婉自保'},
    {'id': 'no_gossip', 'text': '不背后诋毁、挑拨他人关系'},
    {'id': 'gentle', 'text': '沟通优先温和体面，尽量避免正面冲突'},
    {'id': 'no_hurt', 'text': '不使用尖锐、伤人、对立的语言'},
  ];

  static const Map<String, String> sceneValueHints = {
    '职场': '例如：尊重但不盲从，合理拒绝额外压榨',
    '亲密关系': '例如：互相尊重，保持独立空间',
    '家庭': '例如：多包容理解，但也不一味顺从',
    '社交': '例如：和朋友往来平等，拒绝无底线消耗',
  };
}
