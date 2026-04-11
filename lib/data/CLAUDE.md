# Data

## Scope

`lib/data/` 负责轻量本地持久化与共享数据结构。
当前主要是 JSON 文件读写，不是数据库层。

## Files

- `proxy_config_data.dart`：代理配置列表，文件名 `proxyConfig.json`
- `app_proxy_config_data.dart`：按应用分流配置，文件名由构造参数决定
- `common.dart`：共享配置/全局数据

## Storage Patterns

- 文件目录来自 `getApplicationDocumentsDirectory()`
- 代理配置保存为 `List<Map<String, dynamic>>`
- 应用开关保存为 `Map<String, bool>`
- 读写格式改动时，reader 与 writer 必须一起改

## Field Contracts

核心代理字段：
- `proxyName`
- `proxyType`
- `proxyHost`
- `proxyPort`
- `proxyUser`
- `proxyPass`

这些字段会被 UI 和 Android 通道共同使用。
字段重命名不是局部改动。

## Change Rules

- 保持 JSON 结构简单、直接、可打印
- 新增字段前先确认 UI 与 Android 侧是否需要同改
- 优先延续现有 Map 结构，不为一次性需求引入复杂模型层
- 修改默认值时，考虑旧文件读取行为

## Anti-Patterns

- 不要在多个文件里复制同一份配置结构
- 不要只改写入逻辑，不改读取逻辑
- 不要把页面临时状态当作持久化格式
- 不要把平台通道参数和本地存储格式悄悄分叉
