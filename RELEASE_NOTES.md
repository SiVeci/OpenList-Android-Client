# Release Notes

## v0.2.0 — 上传与文件操作闭环版

范围定义见 [v0.2_PRD.md](v0.2_PRD.md)，执行过程见 [v0.2_EXECUTION_PLAN.md](v0.2_EXECUTION_PLAN.md)。

### 新增功能

- **文件写操作**：新建目录、重命名文件/目录、删除文件/目录（危险操作二次确认）、移动/复制（通用目标目录选择器）。写操作统一经 `FileOperationRepository`，成功后精确失效受影响目录的本地缓存。
- **批量选择**：长按进入选择模式，支持全选/取消全选，批量删除/移动/复制；批量操作在客户端逐项串行调用后端并聚合为"成功 X 失败 Y"，部分失败时可查看每一项失败原因（后端本身批量请求首错即中止、不返回逐项结果，故采用客户端聚合）。
- **上传**：基于 Storage Access Framework 多选本地文件，经 WorkManager 后台流式上传（不整文件读入内存），支持进度展示、取消、失败重试提示；上传状态落 Room（`UploadTaskEntity`），切页/切后台不丢失；完成后自动失效并刷新目标目录（若用户仍在该目录）。
- **写操作权限控制**：游客态隐藏所有写入口；已登录用户乐观展示写入口，403 时按后端 message 精确区分"无权限"与"同名冲突"。
- **目录选择器**：面包屑导航、进入子目录、返回上级、选择当前目录，供移动/复制/上传共用。
- **权限位持久化**：`Session` 新增 `/api/me` 权限位掩码字段（Migration 4→5），为后续更精细的权限门控预留。

### 技术

- **多模块拆分**：单模块 `:app` 拆分为 `core:{common,model,designsystem,network,database,auth,domain}`、`data:repository`、`feature:{instance,auth,files,settings,upload}` 共 14 个 Gradle 模块，依赖方向单向无环。
- **Room 正式 Migration**：移除 `fallbackToDestructiveMigration`，Migration 4→5（`Session.permission`）、5→6（`upload_tasks` 表）均为手写 SQL，逐条与 Room 自身导出的 schema JSON 核对。
- **WorkManager + Hilt**：`UploadWorker`（`@HiltWorker` + assisted inject）经独立 `OkHttpClient`（无读超时、不复用共享的 `AuthInterceptor`/`InstanceContext`，避免后台任务与前台切实例产生的 token 串扰）流式上传，取消经 `suspendCancellableCoroutine` + `Call.cancel()` 真正中断而非仅阻止未开始的请求。
- **批量单元测试**：`FileOperationRepositoryImpl` 覆盖全成/全败/部分失败/401 提前终止等场景。
- **路径编码扩展**：新增 `OpenListPathCodec.encodePathForHeader`，逐段百分号编码同时保留字面 `/` 分隔符（用于上传 `File-Path` 请求头，区别于对整个路径做 `URLEncoder.encode` 会错误转义 `/` 本身）。

### 已知限制

见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)。

### 下一步

见 [v0.2_PRD.md](v0.2_PRD.md) 与 `v0.2_EXECUTION_PLAN.md` 中记录的 v0.3 分享/任务中心预留。

---

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
