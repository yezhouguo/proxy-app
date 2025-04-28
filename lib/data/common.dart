import 'package:shared_preferences/shared_preferences.dart';

class AppSetings {
  static const String _CheckUpdate = "isUpdate";
  static const String _CheckWifi = "isCheckWifi";

  static Future<bool> getCheckUpdate() async {
    SharedPreferences prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_CheckUpdate) ?? true;
  }

  static Future<bool> setCheckUpdate(bool value) async {
    SharedPreferences prefs = await SharedPreferences.getInstance();
    return prefs.setBool(_CheckUpdate, value);
  }

  static Future<bool> getCheckWifi() async {
    SharedPreferences prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_CheckWifi) ?? true;
  }

  static Future<bool> setCheckWifi(bool value) async {
    SharedPreferences prefs = await SharedPreferences.getInstance();
    return prefs.setBool(_CheckWifi, value);
  }

}