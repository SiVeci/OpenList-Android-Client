# Release Notes

## v0.1.0 — 技术验证 / 最小可用闭环版

范围定义见 [v0.1_PRD.md](v0.1_PRD.md)，执行过程见 [v0.1_EXECUTION_PLAN.md](v0.1_EXECUTION_PLAN.md)。

### 新增功能

- **实例管理**：添加 / 删除 / 切换实例，Base URL 规范化（协议头校验、去空格、去尾斜杠、子路径部署支持、去重），测试连接（`/ping` 失败回退 `/api/public/settings`）。
- **鉴权与会话**：账号密码登录、游客访问（探测实例是否允许游客）、管理员 Token 登录；Token 使用 Android Keystore（AES-256-GCM）加密落盘；401 / 服务端重启导致的会话失效会自动清理并回到登录页，不影响其他实例。
- **文件浏览**：目录列表（面包屑、子目录跳转、系统返回键回上级）、下拉刷新、目录缓存（5 分钟 TTL，命中期内免网络请求）、断网降级展示缓存并提示。
- **文件详情与下载**：文件基础信息、复制路径/复制链接、下载交给系统 `DownloadManager`（落地系统 Downloads 目录，完成/失败由系统通知栏原生承接）。
- **设置页**：清理全部实例的本地目录缓存、调试日志开关（HTTP 请求日志脱敏后写入 logcat）、开源许可证信息。
- **多实例隔离**：所有本地数据（Session/FileCache/DownloadTask）均以 `instanceId` 维度隔离；删除实例时级联清理其 Session、文件缓存、下载记录。

### 技术

- 单 Activity + Jetpack Compose + Navigation Compose + Hilt + MVVM + Repository。
- Room 数据库（Instance / Session / FileCache / DownloadTask）。
- Retrofit + OkHttp + kotlinx.serialization，自研极简 JSON Converter（未依赖外部 `retrofit2-kotlinx-serialization-converter`）。
- 路径编解码（`OpenListPathCodec`）统一处理中文、空格、特殊符号路径与子路径部署拼接。

### 已知限制

见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)。

### 下一步

见 [v0.2_BACKLOG.md](v0.2_BACKLOG.md)。
