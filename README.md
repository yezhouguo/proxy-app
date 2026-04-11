# [proxy-app](https://github.com/yezhouguo/proxy-app)

## 项目背景

- 在分析app的时候,偶尔需要抓包,尝试了目前比较常见的代理工具
- `Drony` `Postern` `ProxyDroid` 发现都有一个相同的问题,对于较新的Android系统不太友好,要么app列表显示不正常,或者界面过于复杂,往往设置之后经常会失效,偶然在play上发现一个比较新的代理工具,界面很不错清晰不过对国内用户不友好有些功能需要会员,即使花钱由于不维护或者网络原因,完整的功能无法使用于是在业余时间开发了这个.

## 项目简介

1. 基于 `flutter` 和[tun2socks](https://github.com/xjasonlyu/tun2socks)开发.
2. [proxy-app](https://github.com/yezhouguo/proxy-app) 是一个轻量级的VPN代理工具，支持HTTP, SOCKS5协议.
3. 功能单只做代理,可分app代理, **双击修改配置** 逻辑比较简单, 主打一个能用就行.
4. 出于学习熟悉flutter的目的去做的,分享给大家,顺便帮我测试一下.
5. 加上[MoveCertificate](https://github.com/ys1231/MoveCertificate) 上下游都有了哈哈.
6. 支持 `Android 9` 及以上版本， 低于 `android 9` 推荐使用 `postern`

## 重要的事情说三遍

- **双击修改配置**
- **双击修改配置**
- **双击修改配置**

## 默认配置文件（INI）

应用支持从默认路径读取代理配置：

- 路径：`/sdcard/Download/proxy-app.ini`
- 兼容旧文件名：`/sdcard/Download/appproxy.ini`
- 格式：仅支持 `[proxy]` 段

```ini
[proxy]
type=socks5
host=10.0.0.1
port=7893
username=
password=
autostart=true
```

字段说明：

- `type`：代理类型，当前建议使用 `socks5`
- `host`：代理地址
- `port`：代理端口
- `username`：用户名，可留空
- `password`：密码，可留空
- `autostart`：是否允许自动启动，`true` / `false`

说明：

1. 首次使用仍需用户同意系统 VPN 授权。
2. Android 11 及以上如果要直接读取 `Download` 下的 ini 文件，需要授予文件访问权限。
3. 可参考仓库中的示例文件：`proxy-app.ini.example`
4. 当配置文件与 app 内已有配置同时存在时，优先使用 `/sdcard/Download/proxy-app.ini`。
5. 兼容旧文件名 `/sdcard/Download/appproxy.ini`，但新文件名优先。
6. 只有当 ini 文件不存在、无法读取或格式非法时，才会回退到 app 内缓存配置。

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=yezhouguo/proxy-app&type=Date)](https://star-history.com/#yezhouguo/proxy-app&Date)

## 附上截图

![Screenshot_20240604-205220](./assets/Screenshot_20240604-205220.png)

![Screenshot_20240604-205910](./assets/Screenshot_20240604-205910.png)

![Screenshot_20240604-205229](./assets/Screenshot_20240604-205229.png)

![Screenshot_20240604-205148](./assets/Screenshot_20240604-205148.png)

![Screenshot_20240604-205158](./assets/Screenshot_20240604-205158.png)
![img.png](assets/img.png)

# 依赖项目
- [tun2socks](https://github.com/xjasonlyu/tun2socks)

# 开发

## build tun2socks

- `touch btun2socks.sh`

```shell
#!/bin/zsh
set -x
cd tun2socks
TUN2SOCKS_DIR="${0:a:h}"
cd $TUN2SOCKS_DIR
go get
go install golang.org/x/mobile/cmd/gomobile@latest
go get golang.org/x/mobile/bind
go get
make
gomobile init
# 兼容 Android 9
gomobile bind -o ../android/app/libs/tun2socks.aar -target android -androidapi 28 ./engine
ls ../android/app/libs/tun2socks.aar
```

```shell
# 如果发现Android Studio 调试flutter 自动跳到一个只读的文件,调试的时候无法修改代码,可以恢复上一个版本,是的坑.
# 推荐 "Android Studio Iguana | 2023.2.1 Patch 2" -> "Android Studio Meerkat | 2024.3.1 Patch 1"
# line 设置为 100
tun2socks/build.sh
flutter build apk --release --split-per-abi --build-name=$VERSION --dart-define=BUILD_DATE=$(date +%Y-%m-%d) --obfuscate --split-debug-info ./build/
```

## flutter shorebird 热更新

- [proxy-app](https://github.com/yezhouguo/proxy-app) 已经绑定可强制更新 `app_id`
```shell
# linux && mac
curl --proto '=https' --tlsv1.2 https://raw.githubusercontent.com/shorebirdtech/install/main/install.sh -sSf | bash
# windows
# Set-ExecutionPolicy RemoteSigned -scope CurrentUser
# iwr -UseBasicParsing 'https://raw.githubusercontent.com/shorebirdtech/install/main/install.ps1'|iex

# 官网注册
https://console.shorebird.dev/login
# shell 登录
shorebird login
# 初始化项目 在 flutter 项目下执行即可
shorebird init
# 编译
shorebird release -p android --artifact apk 
# 打补丁
shorebird patch --platforms=android
```

# 免责声明
- 本程序仅用于学习交流, 请勿用于非法用途.
