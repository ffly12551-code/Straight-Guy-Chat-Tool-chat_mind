
import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../utils/constants.dart';

class StorageService {
  static final StorageService _instance = StorageService._internal();
  factory StorageService() => _instance;
  StorageService._internal();

  SharedPreferences? _prefs;

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
  }

  String get apiKey => _prefs?.getString(Constants.prefApiKey) ?? '';
  Future<void> setApiKey(String value) => _prefs?.setString(Constants.prefApiKey, value) ?? Future.value();

  String get model => _prefs?.getString(Constants.prefModel) ?? Constants.defaultModel;
  Future<void> setModel(String value) => _prefs?.setString(Constants.prefModel, value) ?? Future.value();

  String get currentScene => _prefs?.getString(Constants.prefScene) ?? Constants.scenes.first;
  Future<void> setScene(String value) => _prefs?.setString(Constants.prefScene, value) ?? Future.value();

  double get floatOpacity => _prefs?.getDouble(Constants.prefOpacity) ?? 0.85;
  Future<void> setOpacity(double value) => _prefs?.setDouble(Constants.prefOpacity, value) ?? Future.value();

  bool get privacyAgreed => _prefs?.getBool(Constants.prefPrivacyAgreed) ?? false;
  Future<void> setPrivacyAgreed(bool value) => _prefs?.setBool(Constants.prefPrivacyAgreed, value) ?? Future.value();

  bool get isFirstLaunch => _prefs?.getBool(Constants.prefFirstLaunch) ?? true;
  Future<void> setFirstLaunch(bool value) => _prefs?.setBool(Constants.prefFirstLaunch, value) ?? Future.value();

  List<String> get baseValues => _prefs?.getStringList(Constants.prefValuesBase) ?? [];
  Future<void> setBaseValues(List<String> values) => _prefs?.setStringList(Constants.prefValuesBase, values) ?? Future.value();

  Map<String, String> get sceneValues {
    final raw = _prefs?.getString(Constants.prefValuesScene);
    if (raw == null) return {};
    try {
      final map = jsonDecode(raw) as Map<String, dynamic>;
      return map.map((k, v) => MapEntry(k, v.toString()));
    } catch (_) {
      return {};
    }
  }

  Future<void> setSceneValues(Map<String, String> values) =>
      _prefs?.setString(Constants.prefValuesScene, jsonEncode(values)) ?? Future.value();
}
