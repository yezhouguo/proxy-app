import 'dart:async';
import 'dart:io';

import 'package:app_settings/app_settings.dart';
import 'package:proxy_app/data/common.dart';
import 'package:proxy_app/data/proxy_config_data.dart';
import 'package:proxy_app/events/app_events.dart';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../generated/l10n.dart';
import 'addproxy.dart';

class ProxyListHome extends StatefulWidget {
  const ProxyListHome({super.key});

  @override
  State<ProxyListHome> createState() => _ProxyListHomeState();
}

class _ProxyListHomeState extends State<ProxyListHome> {
  // 配置文件操作类
  final ProxyConfigData _proxyConfigData = ProxyConfigData();

  // 配置文件数据
  List<Map<String, dynamic>> _dataLists = [];

  // 当前选中代理名称
  String _isSelectedProxyName = "";

  // 方法调用通道
  static const platform = MethodChannel("cn.ys1231/appproxy/vpn");
  static const configPlatform = MethodChannel("cn.ys1231/appproxy");
  static const defaultIniConfigSource = 'defaultIni';

  // 当前需要启动的代理配置
  Map<String, dynamic> _currentProxyData = {};

  @override
  void initState() {
    super.initState();
    debugPrint("---- ProxyListHome initState call ");
    initProxyConfig();

    // 在Flutter端处理来自原生的调用
    platform.setMethodCallHandler((call) async {
      if (call.method == 'stopVpn') {
        // 执行Flutter逻辑
        setState(() {
          _stopProxy();
        });
      }
    });
  }

  Future<void> initProxyConfig() async {
    debugPrint("---- ProxyListHome initProxyConfig call ");
    try {
      final localConfigs = (await _proxyConfigData.readProxyConfig() ?? [])
          .map((item) => Map<String, dynamic>.from(item))
          .toList();
      final defaultConfig = await _loadDefaultProxyConfig();
      if (!mounted) {
        return;
      }
      setState(() {
        _dataLists = _mergeConfigList(localConfigs, defaultConfig);
      });
    } catch (e) {
      debugPrint("---- ProxyListHome initProxyConfig error $e");
    }
  }

  Future<Map<String, dynamic>?> _loadDefaultProxyConfig() async {
    final result = await configPlatform.invokeMethod<dynamic>('getDefaultProxyConfig');
    if (result is Map) {
      return Map<String, dynamic>.from(result);
    }
    return null;
  }

  bool _isDefaultIniConfig(Map<String, dynamic> data) {
    return data['configSource'] == defaultIniConfigSource;
  }

  bool _isSameConfigEntry(Map<String, dynamic> current, Map<String, dynamic> candidate) {
    if (_isDefaultIniConfig(current) || _isDefaultIniConfig(candidate)) {
      return current['configSource'] == candidate['configSource'] &&
          current['configSourcePath'] == candidate['configSourcePath'];
    }
    return current['proxyName'] == candidate['proxyName'];
  }

  List<Map<String, dynamic>> _manualConfigList() {
    return _dataLists
        .where((item) => !_isDefaultIniConfig(item))
        .map((item) => Map<String, dynamic>.from(item))
        .toList();
  }

  List<Map<String, dynamic>> _mergeConfigList(
    List<Map<String, dynamic>> localConfigs,
    Map<String, dynamic>? defaultConfig,
  ) {
    final mergedConfigs = localConfigs
        .where((item) => !_isDefaultIniConfig(item))
        .map((item) => Map<String, dynamic>.from(item))
        .toList();
    if (defaultConfig != null) {
      mergedConfigs.insert(0, Map<String, dynamic>.from(defaultConfig));
    }
    return mergedConfigs;
  }

  String _buildProxySubtitle(Map<String, dynamic> data) {
    final summary = '${data["proxyType"]} ${data["proxyHost"]}:${data["proxyPort"]}';
    if (!_isDefaultIniConfig(data)) {
      return summary;
    }
    final path = data['configSourcePath']?.toString() ?? '';
    if (path.isEmpty) {
      return summary;
    }
    return '$summary\n$path';
  }

  // 在这里处理从 AddProxyButton 返回的数据 添加代理配置到列表
  void handleConfigData(Map<String, dynamic> data, {bool isAdd = false}) {
    final normalizedData = Map<String, dynamic>.from(data);
    if (!isAdd &&
        !_isDefaultIniConfig(normalizedData) &&
        _dataLists.any((item) => !_isDefaultIniConfig(item) && item['proxyName'] == normalizedData['proxyName'])) {
      debugPrint("handleConfigData Data already exists in the list, skipping.");
      return;
    }

    final existingIndex = _dataLists.indexWhere((item) => _isSameConfigEntry(item, normalizedData));
    if (existingIndex >= 0) {
      _dataLists[existingIndex] = normalizedData;
    } else {
      _dataLists.add(normalizedData);
    }

    _proxyConfigData.addProxyConfig(_manualConfigList()).then((value) {});
    setState(() {
      debugPrint('Received data: $_dataLists _dataLists lenth:${_dataLists.length}');
    });
  }

  // 删除列表中的代理配置
  void deletetoProxyConfig(Map<String, dynamic> data) {
    if (_isDefaultIniConfig(data)) {
      return;
    }
    _dataLists.removeWhere((item) => item['proxyName'] == data['proxyName']);
    _proxyConfigData.deleteProxyConfig(_manualConfigList());
    setState(() {
      debugPrint('delete data: $_dataLists _dataLists lenth:${_dataLists.length}');
    });
  }

  Future<bool> isAndroidQOrAbove() async {
    if (!Platform.isAndroid) return false;
    DeviceInfoPlugin deviceInfo = DeviceInfoPlugin();
    AndroidDeviceInfo androidInfo = await deviceInfo.androidInfo;
    return androidInfo.version.sdkInt >= 29; // Q = 29
  }

  // 检测网络类型
  Future<bool> _checkWifiState() async {
    final List<ConnectivityResult> connectivityResult = await (Connectivity().checkConnectivity());
    if (connectivityResult.contains(ConnectivityResult.wifi)) {
      debugPrint('Wi-Fi connected');
      return true;
    } else {
      if (await isAndroidQOrAbove()) {
        AppSettings.openAppSettingsPanel(AppSettingsPanelType.wifi);
      } else {
        AppSettings.openAppSettings(type: AppSettingsType.wifi);
      }
      return false;
    }
  }

  // 显示提示是否删除代理
  Future<void> _showDeleteDialog(BuildContext context, Map<String, dynamic> data) async {
    bool isDelete = await showDialog(
        context: context,
        builder: (BuildContext context) {
          return AlertDialog(
            title: Text(S.of(context).text_tips),
            content: Text(S.of(context).text_delete_proxy_tips),
            actions: [
              TextButton(
                child: Text(S.current.text_cancel),
                onPressed: () {
                  Navigator.of(context).pop(false);
                },
              ),
              TextButton(
                child: Text(S.of(context).text_confirm),
                onPressed: () {
                  Navigator.of(context).pop(true);
                },
              ),
            ],
          );
        });
    if (isDelete) {
      deletetoProxyConfig(data);
    }
  }

  // 启动VPN
  void _startProxy(data) async {
    if (await AppSetings.getCheckWifi()){
      bool isWifi = await _checkWifiState();
      if (!isWifi) {
        return;
      }
    }
    _isSelectedProxyName = data["proxyName"];
    _currentProxyData = data;
    _currentProxyData['appProxyPackageList'] = appProxyPackageList.getListString();
    try {
      bool result = await platform.invokeMethod('startVpn', _currentProxyData);
      if (result) {
        debugPrint("---- ProxyListHome startVpn: $_currentProxyData success");
      } else {
        debugPrint("---- ProxyListHome startVpn: $_currentProxyData fail");
        _isSelectedProxyName = "";
      }
    } on PlatformException catch (e) {
      debugPrint("Failed to start proxy: '${e.message}'.");
    }
  }

  // 关闭VPN
  void _stopProxy() async {
    try {
      // 控制关闭VPN
      _isSelectedProxyName = "";
      bool result = await platform.invokeMethod('stopVpn');
      if (result) {
        debugPrint("---- ProxyListHome stopVpn success");
      } else {
        debugPrint("---- ProxyListHome stopVpn fail");
      }
    } on PlatformException catch (e) {
      debugPrint("Failed to stop proxy: '${e.message}'.");
    }
  }

  @override
  Widget build(BuildContext context) {
    // debugPrint("---- ProxyListHome build call: $_dataLists");
    return Scaffold(
      appBar: AppBar(
        title: Text('Server ${S.of(context).text_server_config}'),
        backgroundColor: Theme.of(context).primaryColor,
      ),
      body: ListView.separated(
        // 创建从边缘反弹的滚动物理效果
        physics: const BouncingScrollPhysics(),
        // 设置底部内边距 解决底部按钮遮挡问题
        padding: const EdgeInsets.only(bottom: 70.0),
        // 配置列表个数
        itemCount: _dataLists.length,
        // 设置分隔符零尺寸
        separatorBuilder: (BuildContext context, int index) {
          return const SizedBox.shrink();
        },
        itemBuilder: (BuildContext context, int c_index) {
          Map<String, dynamic> c_data = _dataLists[c_index];
          return Card(
            // 设置 margin 为水平方向 8.0，垂直方向 4.0
            margin: const EdgeInsets.symmetric(horizontal: 8.0, vertical: 4.0),
            child: GestureDetector(
                child: SwitchListTile(
                  // 设置选中状态
                  value: _isSelectedProxyName == c_data["proxyName"] ? true : false,
                  isThreeLine: _isDefaultIniConfig(c_data) &&
                      (c_data['configSourcePath']?.toString().isNotEmpty ?? false),
                  // 设置标题和副标题
                  title: Text('${c_data["proxyName"]}'),
                  subtitle: Text(
                    _buildProxySubtitle(c_data),
                  ),
                  // 设置switch的onChanged事件
                  onChanged: (bool value) {
                    setState(() {
                      if (value) {
                        _startProxy(c_data);
                      } else {
                        _stopProxy();
                      }
                      debugPrint("current index:$c_index select: $_isSelectedProxyName");
                    });
                  },
                ),
                // 设置长按事件 主要触发删除操作
                onLongPress: _isDefaultIniConfig(c_data)
                    ? null
                    : () {
                        debugPrint("long press delete:${c_data["proxyName"]}");
                        _showDeleteDialog(context, c_data);
                      },
                // 设置双击事件
                onDoubleTap: () {
                  Navigator.push(context, MaterialPageRoute(builder: (BuildContext context) {
                    return AddProxyWidget(onDataFetched: handleConfigData, onData: c_data);
                  }));
                }),
          );
        },
      ),
      floatingActionButton: AddProxyButton(onDataFetched: handleConfigData),
      floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
    );
  }
}

// 添加代理按钮
class AddProxyButton extends StatelessWidget {
  const AddProxyButton({super.key, required this.onDataFetched});

  // 定义一个回调，用于处理读取到的数据
  final Function(Map<String, dynamic>, {bool isAdd}) onDataFetched;

  @override
  Widget build(BuildContext context) {
    // proxyConfigData.readProxyConfig();
    return InkWell(
        // 点击事件处理函数
        onTap: () {
          // 使用Navigator.push 实现页面路由跳转，传入当前上下文context和MaterialPageRoute构建器
          Navigator.push(
            context,
            MaterialPageRoute(
                builder: (context) =>
                    AddProxyWidget(onDataFetched: onDataFetched, onData: const {})),
          );
        },
        child: Container(
          height: 50.0,
          // 构建一个BoxDecoration对象，用于设置容器的装饰效果
          decoration: BoxDecoration(
            // 设置背景颜色为紫色
            color: Colors.purple.withAlpha(230),
            // 设置边框圆角为24.0
            borderRadius: BorderRadius.circular(15.0),
            boxShadow: [
              /// 创建一个紫色的阴影效果
              BoxShadow(
                /// 阴影颜色，这里设置为淡紫色
                color: Colors.purple.withAlpha(50),
                // 阴影的扩展半径，0.0表示没有扩展
                spreadRadius: 0.0,
                // 阴影的模糊半径，1.0表示轻微模糊
                blurRadius: 1.0,
                // 阴影的偏移量，这里设置为水平0像素，垂直2像素的偏移
                offset: const Offset(0, 3),
              ),
            ],
          ),

          // 在UI中创建一个带内边距的子组件，用于显示“添加代理”按钮
          child: Padding(
              // 设置四周的内边距为8.0
              padding: EdgeInsets.all(8.0),
              // 使用IntrinsicWidth组件来确定其子组件的自然宽度
              child: IntrinsicWidth(
                // 添加水平布局组件
                child: Row(
                  // 设置子组件在父容器中的水平排列方式为居中
                  mainAxisAlignment: MainAxisAlignment.center,
                  // 设置交叉轴对齐方式为居中
                  crossAxisAlignment: CrossAxisAlignment.center,
                  // 子组件数组，包括一个图标和一个文本
                  children: [
                    // 添加图标组件
                    const Icon(Icons.add, color: Colors.white),
                    // 在图标和文本之间添加一个宽度为10.0的空白间隔
                    const SizedBox(width: 5.0),
                    // 添加文本组件，显示“添加代理”文本
                    Text(
                      S.of(context).text_add_proxy,
                      style: const TextStyle(fontSize: 16.0, color: Colors.white),
                    ),
                    const SizedBox(width: 5.0),
                  ],
                ),
              )),
        ));
  }
}
