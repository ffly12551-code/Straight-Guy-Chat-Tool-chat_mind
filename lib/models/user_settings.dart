
class UserSettings {
  String apiKey;
  String model;
  String currentScene;
  double floatOpacity;
  List<String> baseValues;
  Map<String, String> sceneValues;

  UserSettings({
    this.apiKey = '',
    this.model = 'doubao-1.5-vision-pro-32k',
    this.currentScene = '职场',
    this.floatOpacity = 0.85,
    this.baseValues = const [],
    this.sceneValues = const {},
  });
}
