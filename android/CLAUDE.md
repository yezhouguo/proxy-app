# Android

## Scope

`android/` 负责 Flutter 与 Android 原生桥接、VPN Service、AAR 集成、签名与打包。

## Key Files

- `app/src/main/kotlin/cn/ys1231/appproxy/MainActivity.kt`：MethodChannel、权限申请、服务绑定
- `app/src/main/kotlin/cn/ys1231/appproxy/IyueService/IyueVPNService.kt`：`VpnService` 与 `Engine` 启停
- `app/src/main/AndroidManifest.xml`：VPN service、前台服务、网络相关权限
- `app/build.gradle.kts`：`tun2socks.aar`、Gson、SDK、签名配置

## Channel Contracts

- `cn.ys1231/appproxy`：应用列表
- `cn.ys1231/appproxy/vpn`：VPN start/stop
- `cn.ys1231/appproxy/appupdate`：下载更新
- 默认 ini 文件名推荐 `proxy-app.ini`，兼容旧文件名 `appproxy.ini`

Flutter 与 Kotlin 的通道名、方法名、参数键必须严格一致。

## VPN Rules

- 启动前走 `VpnService.prepare(...)`
- VPN 运行时保留前台通知
- 允许/排除应用逻辑在 `IyueVPNService` 内处理
- 代理 URL 由 Kotlin 侧拼接给 `Engine`
- `tun2socks.aar` 缺失时先看构建链路，不要直接绕过依赖

## Build Rules

- `minSdk = 28` 与当前 AAR 保持一致
- release 签名依赖环境变量与 `flutter.keystore`
- `implementation(files("libs/tun2socks.aar"))` 是关键依赖
- 改 Manifest 权限时，同时检查 Android 版本兼容性

## Anti-Patterns

- 不要改通道契约而不改 Flutter 侧
- 不要跳过 VPN 授权流程
- 不要移除前台服务通知
- 不要降低 `minSdk` 却继续复用现有 AAR
- 不要把 Flutter UI 逻辑塞进 Kotlin 层
