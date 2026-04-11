# UI

## Scope

`lib/ui/` 是页面层，不是通用组件库。
当前主导航由 3 个页面组成：
- `ProxyListHome`：代理列表与 VPN 启停
- `AppConfigList`：按应用分流配置
- `AppSettings`：设置与附加功能

## File Map

- `proxy_config_list.dart`：代理列表、删除、启动/停止、VPN MethodChannel
- `addproxy.dart`：新增/编辑代理配置
- `app_config_list.dart`：应用列表与勾选状态
- `settings.dart`：设置页
- `app_update.dart`：更新入口

## Patterns

- 页面以 `StatefulWidget` 为主，沿用现有风格
- 本地化字符串优先走 `S.of(context)` / `S.current`
- 主题、语言状态由 `flutter_bloc` 提供，入口在 `lib/main.dart`
- 代理启动参数在页面层组装后，通过 `cn.ys1231/appproxy/vpn` 下发
- 编辑代理时，先保持表单字段名与存储字段名一致
- 对外展示名称使用 `proxy-app`，平台通道仍保持 `cn.ys1231/appproxy*`

## State Rules

- 页面内短期交互状态可留在 `State`
- 持久化数据仍回写 `lib/data/`
- 平台调用结果要回到 UI 状态上，不要只打印日志
- 列表刷新逻辑保持单一入口，避免多处重复读取文件

## Anti-Patterns

- 不要在 UI 层发明新的代理字段名
- 不要硬编码可本地化文案
- 不要修改 MethodChannel 名称而不同步 Android 侧
- 不要把文件存储逻辑直接散落到多个页面
- 不要把单页拆成大量一次性 helper，除非已复用
