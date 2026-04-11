# proxy-app

一个轻量级的 Android VPN 代理工具，支持 HTTP、SOCKS5，并可按应用分流。

## Stack

- Flutter/Dart 应用
- Android 原生桥接在 `android/`
- VPN 数据路径基于 `VpnService` + `tun2socks.aar`

## Development

- 包管理/构建：Flutter pub
- Android release 签名依赖 `android/local.properties` 中的 `flutter.keystore`
- `minSdk = 28`，跟随 `tun2socks.aar`
- tun2socks 构建入口：`btun2socks.sh`

## Project Commands

```bash
./btun2socks.sh
flutter build apk --release --split-per-abi --build-name=$VERSION --dart-define=BUILD_DATE=$(date +%Y-%m-%d) --obfuscate --split-debug-info ./build/
shorebird release -p android --artifact apk
shorebird patch --platforms=android
```

## Where to Look

| Task | Location |
|------|----------|
| 调整主入口、主题、语言 | `lib/main.dart`, `lib/events/` |
| 修改代理列表/启动停止逻辑 | `lib/ui/proxy_config_list.dart` |
| 修改新增代理表单 | `lib/ui/addproxy.dart` |
| 修改按应用分流配置 | `lib/ui/app_config_list.dart`, `lib/data/app_proxy_config_data.dart` |
| 修改设置页/更新入口 | `lib/ui/settings.dart`, Android app update channel |
| 修改代理配置持久化 | `lib/data/proxy_config_data.dart` |
| 修改 Android VPN 通道/权限/服务 | `android/app/src/main/` |
| 重建 tun2socks AAR | `tun2socks/`, `btun2socks.sh`, `android/app/libs/` |

## Contracts

- Flutter VPN 通道名：`cn.ys1231/appproxy/vpn`
- Flutter app 列表通道名：`cn.ys1231/appproxy`
- Flutter 更新通道名：`cn.ys1231/appproxy/appupdate`
- 默认 ini 文件名推荐 `proxy-app.ini`，兼容旧文件名 `appproxy.ini`
- 代理配置字段以 `proxyName/proxyType/proxyHost/proxyPort/proxyUser/proxyPass` 为核心
- Android 侧 `MainActivity` 与 Flutter 侧参数结构必须同步修改

## Working Rules

- 先搜索，再修改；不要猜代码位置
- 优先改现有文件，不新建抽象层
- Flutter UI、文件存储、Android 通道三层边界要清晰
- 涉及通道、权限、签名、构建时，同时检查 Flutter 与 Android 两侧
- 涉及 tun2socks 构建时，同时检查脚本、AAR 输出位置、Gradle 依赖

## Agent Workflow

Explore 定位 → 读取相关文件 → 小范围修改 → 验证构建/分析结果

并行化场景：
- Flutter UI 与 Android 原生可并行调研
- 数据模型与页面文案可并行调研
- 同一文件不要拆给多个执行单元

## Guidance

目录内的嵌套 `CLAUDE.md` 提供更细的约束。
读取更深层目录时，离目标文件最近的 `CLAUDE.md` 优先生效。
