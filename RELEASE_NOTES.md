# Release Notes

## v1.1.1 — 视觉资源与构建补丁

本补丁延续 v1.1 功能范围，不新增业务功能、权限、路由、API、DTO、Repository 或 Room 结构。

### 新增与调整

- **品牌资源替换**：Android launcher 图标替换为处理后的 OpenList 图形元素；首页、登录页、添加实例页、我的页接入统一品牌 logo 组件。
- **云端构建选项**：手动 GitHub Actions 构建支持选择 `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64` 和 universal APK；默认推荐只构建 `arm64-v8a`。
- **ABI 拆分参数化**：Gradle release 构建支持 `openlist.abiIncludes` 与 `openlist.universalApk` 参数。
- **versionCode = 8，versionName = "1.1.1"**。

### 验收

`compileDebugKotlin testDebugUnitTest` 与 `assembleDebug` 已通过。图标裁切、圆形图标和小屏品牌露出仍需真机或模拟器人工观感确认，详见 [docs/plans/2026-07-09-logo-replacement-acceptance.md](docs/plans/2026-07-09-logo-replacement-acceptance.md)。

---

## v1.1.0 — 首页工作台化 + 全 App UI 重构

范围定义见 [v1.1_PRD.md](v1.1_PRD.md)，执行过程见 [v1.1_EXECUTION_PLAN.md](v1.1_EXECUTION_PLAN.md)，实现记录见 [v1.1_IMPLEMENTATION_LOG.md](v1.1_IMPLEMENTATION_LOG.md)，验收见 [v1.1_ACCEPTANCE_REPORT.md](v1.1_ACCEPTANCE_REPORT.md)。

### 新增与调整

- **首页工作台化**：启动落点改为首页；首页提供当前实例、搜索入口、快捷入口、任务摘要、最近访问和实例管理区。
- **底部导航**：新增首页 / 文件 / 任务 / 我的四 tab；任务 tab 支持运行中 badge；深层页面保持无底栏。
- **最近访问**：新增 `recent_paths`，Room schema 从 9 升到 10；文件访问写入实例级最近路径，首页可直达。
- **全 App UI 重构**：连接实例、登录、文件、任务中心、搜索、分享、预览、我的、管理台概览按 v1.1 设计映射重排；保留既有业务行为与路由。
- **轻功能补齐**：文件排序、文件行能力 badge、连接测试耗时显示、缓存容量显示、分享列表状态筛选与本地搜索、任务中心分组/失败 Tab/清除已完成/清除失败/全部取消。

### 技术

- **Room Migration 9→10**：新增 `recent_paths` 表，实例删除级联清理；已在 AVD 上完成 v1.0.0 到 v1.1.0 原地升级验证。
- **设计系统扩展**：新增工作台分区、快捷入口、入口行、底栏、状态摘要、分段控件、OTP 分格输入等 Compose 组件。
- **行为约束**：零新增 Android 权限、零新增路由、零 API/DTO 变更、零新增运行时依赖；新增依赖仅限计划内测试依赖。
- **versionCode = 7，versionName = "1.1.0"**。

### 验收

`Medium_Phone` AVD（API 36）完成 H3 起逐阶段安装验证，H10 完成浅色/深色/小屏截图矩阵，H11 完成主流程回归抽样。`clean assembleDebug testDebugUnitTest`、`compileDebugKotlin testDebugUnitTest` 均通过。

### 已知限制

见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)。本轮 late-stage AVD 会话不是真实 `https://nas.siveci.com:18443` 管理员会话，因此真实分享数据、真实预览内容、管理台真实数据密度仍需用户侧确认；小屏任务摘要省略号和“我的”页顶栏视觉差异作为人工观感确认项记录。

---

## v1.0.0 — 原子级对齐稳定版

范围定义见 [v1.0_PRD.md](v1.0_PRD.md)，执行过程见 [v1.0_EXECUTION_PLAN.md](v1.0_EXECUTION_PLAN.md)，Sprint 记录见 [v1.0_IMPLEMENTATION_LOG.md](v1.0_IMPLEMENTATION_LOG.md)，验收见 [v1.0_ACCEPTANCE_REPORT.md](v1.0_ACCEPTANCE_REPORT.md)，与 Web 端逐项对照见 [Parity_Matrix.md](Parity_Matrix.md)。

### 新增功能

- **登录补齐**：LDAP 登录（`POST api/auth/login/ldap`，403 区分"未启用"/"不允许"）；2FA/OTP 内联第二步（信封 `code=402` 判定，非 HTTP 状态码）；SSO/WebAuthn 提供浏览器兜底文案 + Token 回退指引。
- **目录级权限门控**：`fs/list.write` 字段 ∧ 会话 `CanWriteContent` 权限位组合判定（`DirectoryCapability`），比单纯依赖 `write` 字段更严格；乐观展示（true/unknown）+ 后端 403 兜底裁决，游客隐藏逻辑不变。
- **上传失败一键重试 + 本地下载取消**：`UploadRepository.retryUpload`（校验 SAF URI 可读性后复位重新入队）；`TransferRepository.cancelDownload`（`DownloadManager.remove` 幂等处理）；任务中心接线。
- **分享链接入站最小闭环**：App 内粘贴/剪贴板检测已配置实例的分享链接，解析展示分享基础信息、密码输入、单文件外部打开分发、复制/浏览器兜底；不做完整分享态目录浏览（范围裁剪，见 Parity Matrix）。
- **Markdown 内嵌图片**：`markwon-image` + 受限 SchemeHandler（仅 `http(s)`/`data:`，无 `file`），同实例相对路径复用既有签名 URL 机制，外链零 Token 注入，失败不阻断正文。
- **管理台补齐**：索引更新路径选择器（复用 `DirectoryPickerSheet`）；管理任务批量轻操作（清理已完成/已成功/重试全部失败三端点）。
- **Token 失效全局跳转登录页**：`SessionExpiryViewModel` 在导航顶层反应式监听会话消失，修复自 v0.2 起主流程（文件/预览/上传等）401 后只有内联错误条、无法自动回到登录页的缺口（此前曾被 v0.2 验收误判为已实现）。
- **HTTP 明文实例风险提示**：添加实例地址为 `http://` 时展示醒目风险提示（登录凭据与文件内容不加密传输），子路径/尾部斜杠地址规范化保持不变。
- **Parity Matrix**：`Parity_Matrix.md` 首次建立，覆盖实例/登录/文件/上传/下载/增删改批量/分享/搜索/任务/预览/管理台/横切面共 12 域，逐项标注 Android 状态（Done/Partial/Web Fallback/Deferred/Not Applicable）与验收证据引用。

### 技术

- **`ErrorMapping` 修复**：`Throwable.toDomainError()` 新增 `HttpException` 分支，反代裸 HTTP 错误响应（400/416/405 等非信封 JSON）不再统一显示"未知错误"。
- **`SafeLogger` 安全加固（S7 审计发现并修复）**：脱敏正则原先要求敏感 key 紧邻分隔符，JSON 引号键形式（`"password":"value"`）完全不匹配——调试日志打开时 OTP/密码等请求体可能明文落 logcat；`otp_code` 字段此前也完全不在脱敏关键字清单内。均已修复并补充单测。
- **`DomainError` 新增 8 个子类**：`AuthMethodUnavailable`/`OtpInvalid`/`UploadRetryUnavailable`/`DownloadCancelUnavailable`/`ShareLinkUnsupported`/`SharePasswordRequired` 等，遵循既有统一错误文案表风格。
- **Room 保持 version=9，零迁移**：本轮全部补齐项通过内存态/组合判定/复用既有列实现，未新增表或字段；S6 real-machine 验证了 Room 8→9 真实升级路径（v0.4 基线数据 → v1.0 原地升级，数据完整保留 + `admin_cache` 新表正确创建）。
- **`:feature:files` 新增单测规避的一处 UX 一致性问题（S7 审计发现并修复）**：批量选择工具栏在多选期间目录能力降级为确认 `false` 时未跟随退出选择模式（后端 403 仍是最终裁决，非安全绕过）。
- **`app` 模块首批单元测试**：`SessionExpiryViewModelTest`（3 项），此前 `testDebugUnitTest` 对该模块恒为 `NO-SOURCE`。
- **versionCode = 6，versionName = "1.0.0"**。

### 安全审计

S7 阶段两个独立 subagent 审计（安全脱敏面、缓存/多实例隔离 + 权限门控回归面）：审计一发现 `SafeLogger` 的 JSON 请求体脱敏漏洞与 OTP 字段缺失（已修复+补测）；审计二发现批量选择栏的能力降级 UX 一致性问题（已修复，非安全漏洞）。其余全部审计面（分享入站零缓存写入、Markdown 图片零 Token 注入、`deleteInstance` 级联覆盖新表、管理台结构性门控）均确认无问题。详见 [v1.0_ACCEPTANCE_REPORT.md](v1.0_ACCEPTANCE_REPORT.md)。

### 真机/模拟器验收

`Medium_Phone` AVD（API 36）全程验证，含 Room 8→9 真实升级路径（v0.4 完成点基线数据 → 原地升级安装 → 数据完整保留）、HTTP 明文实例真机连接、真实 NAS 实例（`nas.siveci.com:18443`）多层真实目录浏览/预览/播放全流程。API 29 真机/模拟器因本轮环境限制未覆盖，按 DEC-605 记入 Parity Matrix 差异项，不阻塞验收。

### 已知限制

见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)。分享入站不支持完整分享态目录浏览（范围裁剪）；LDAP/2FA 真机端到端演示、成功解析他人分享的完整展示均因缺少对应权限测试账号，由单元测试覆盖代码路径正确性、真机演示留待测试账号到位后补充；`PreviewRepository.refreshPreviewUrl` 是 v0.4 遗留的零调用方死代码，本轮未清理（不影响任何真实功能路径）。

### 下一步

见 [v1.0_PRD.md](v1.0_PRD.md) 中 Full_PRD 记录的长期方向；可选范围（搜索分页/类型筛选、分享列表筛选、管理台搜索增强、最近预览等）见 v1.0_PRD §4.3，留待后续版本评估。

---

## v0.5.0 — 轻量管理台版

范围定义见 [v0.5_PRD.md](v0.5_PRD.md)，执行过程见 [v0.5_EXECUTION_PLAN.md](v0.5_EXECUTION_PLAN.md)，Sprint 记录见 [v0.5_IMPLEMENTATION_LOG.md](v0.5_IMPLEMENTATION_LOG.md)，验收见 [v0.5_ACCEPTANCE_REPORT.md](v0.5_ACCEPTANCE_REPORT.md)。

### 新增功能

- **管理员门控**：设置页新增「管理台」入口行，跟随当前实例与登录身份显示三种状态（隐藏/禁用说明/可进入）；进入 `ADMIN` 路由即强制 `checkAccess`（重新拉取 `/api/me`），非管理员/游客/会话失效均在门控页拦截，零管理接口请求。
- **管理台概览**：实例信息（零请求）+ 存储/任务/索引三张摘要卡（各自独立加载、独立降级）+ Web 兜底入口卡。
- **用户查看**：分页列表 + 只读详情（用户名/角色/禁用状态/基础路径/权限摘要），无编辑入口，密码等敏感字段结构性不存在于领域模型。
- **存储查看与启停**：列表 + 详情（含 `mount_details` 独立加载态与超时降级）；存储启用/禁用（二次确认，禁用为危险样式）；重新加载全部存储（二次确认）；成功后精确/保守失效对应的文件与预览缓存。
- **驱动信息只读**：驱动列表/名称/详情信息展示（存储详情内的二级 Sheet）。
- **管理任务扩展**：覆盖后端全部 7 类任务（`upload`/`copy`/`move`/`offline_download`/`offline_download_transfer`/`decompress`/`decompress_upload`）的 undone/done 查看、详情、取消、重试（限失败）、删除记录（限已完成类），4 秒轮询（仅可见+有运行中任务时，连续失败 3 次自动停止）；与既有任务中心（`:feature:task`）完全独立，普通视角行为与请求量零变化。
- **索引管理**：进度查看（对象数/运行状态/上次完成时间/错误）+ 构建/更新/停止/清空四操作（清空为危险样式），3~5 秒轮询（仅可见+运行中）。
- **设置查看**：按 12 个 Group 分组的只读列表 + 默认设置对比（`FilterChip` 切换）；私密项（`flag=PRIVATE` 或 key 命中 token/secret/password/key 关键字）掩码展示，值在写入缓存前已置空，无保存/删除/重置 Token 入口。
- **Web 管理台兜底**：构造 `{baseUrl}/@manage` 链接，外部浏览器打开（`ACTION_VIEW`），无接收方时可复制链接；不注入 Token，URL 恒与当前实例同源。

### 技术

- **新增 `:feature:admin` 模块**：graduate 自 `:app` 内的占位包，依赖单向指向 `core:{common,model,domain,designsystem}`，不依赖任何其他 feature；`:feature:webfallback` 占位包按 DEC-502 保留，未升级为真实模块。
- **Room Migration 8→9**：新增 `admin_cache` 通用缓存表（`instanceId/scope/cacheKey/rawJson/cachedAt` + 唯一索引），只缓存用户/存储/设置三类慢变数据（TTL 分别为 1 分钟/30 秒/5 分钟），任务与索引进度不入库；`rawJson` 只存领域模型序列化结果，从结构上不可能包含敏感字段；`InstanceRepositoryImpl.delete` 级联清理该表。
- **7 个新 Repository**：`AdminGateRepository`/`AdminUserRepository`/`AdminStorageRepository`/`AdminTaskRepository`/`AdminIndexRepository`/`AdminSettingsRepository`/`AdminWebFallbackRepository`，均遵循既有 instance 查找/401 失效处理模式；管理任务独立内存态 StateFlow，不写 `remote_tasks` 表。
- **`DomainError` 新增** `AdminAccessDenied`/`AdminApiUnavailable` 两个子类；操作类失败（存储启停/任务操作/索引操作）经 `OpenListError` 原样透传后端 message。
- **后端接口对照**：本轮重新获取 `openlist-ref/` 源码并首次覆盖 admin handlers（`handles/{user,storage,driver,setting,index,task}.go`、`server/router.go`），V-501~V-510 全部有一手源码依据，非 provisional；管理员判定沿用现有 `role`/`isAdmin`；`AuthAdmin` 中间件确认为 HTTP 200 + `body.code=403`（非独立 401/403 状态码），与既有 `ErrorMapping` 只看 `body.code` 的实现天然兼容。
- **versionCode = 5，versionName = "0.5.0"**。

### 安全审计

S8 阶段两个独立静态审计（缓存/多实例隔离/敏感字段、权限门控/日志脱敏/回归面）均未发现问题，详见 [v0.5_ACCEPTANCE_REPORT.md](v0.5_ACCEPTANCE_REPORT.md) §8。

### 已知限制

见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)。**本版本延续 DEC-506 的人工验证 caveat**：本轮开发环境无 Android 真机/模拟器，Room 8→9 真实升级路径、`/@manage` 真实可达性、外部浏览器实际弹出行为等均待发布前人工核对；本轮版本号提升后已重新执行 `assembleDebug`/`testDebugUnitTest` 并确认 BUILD SUCCESSFUL（区别于 v0.4 完全没有编译过一次的更弱基线）。

### 下一步

见 [v0.5_PRD.md](v0.5_PRD.md) §18 记录的 v1.0（原子级对齐 + Parity Matrix）预留。

---

## v0.4.0 — 预览与播放器版

范围定义见 [v0.4_PRD.md](v0.4_PRD.md)，执行过程见 [v0.4_EXECUTION_PLAN.md](v0.4_EXECUTION_PLAN.md)，Sprint 记录见 [v0.4_IMPLEMENTATION_LOG.md](v0.4_IMPLEMENTATION_LOG.md)。

### 新增功能

- **统一文件打开分发**：文件列表、文件详情、搜索结果、分享详情、任务中心完成项这五个入口，点击可预览文件都会经统一的 `resolvePreview` 分发（按扩展名判定 kind，再决定 App 内预览/播放、外部打开或不支持兜底），不再各自重复实现打开逻辑。
- **图片预览**：Coil 加载，自定义 `instanceId+path+modifiedAt` 缓存 key（避免签名 URL 变化导致缓存全 miss 或跨实例串缓存），失败态可重试/下载。
- **文本预览**：流式限长读取（默认 512KB 截断+提示，超过 20MB 直接提示"文件过大"不发起网络读取），UTF-8+BOM 自动识别，可复制全文、下载。
- **Markdown 预览**：Markwon 原生渲染（无 WebView，不执行脚本），HTML 内容静默忽略，外链走系统浏览器，渲染异常降级为源码文本；本版本暂不支持内嵌图片渲染。
- **PDF / Office / 未知格式外部打开兜底**：外部打开、下载、网页打开三选一，无可处理应用时安全提示不崩溃。
- **视频播放器**：ExoPlayer 播放/暂停/进度拖动/横屏沉浸式播放；签名地址失效时自动刷新重试（上限 2 次）后仍失败给出外部打开/下载兜底。
- **音频播放器**：同一播放内核，简洁播放/暂停/进度条布局，不做歌词。
- **基础字幕支持**：同目录同名/前缀字幕自动发现（`srt`/`vtt`/`ass`/`ssa`）、手动从当前目录选择任意文件、可关闭字幕；字幕加载失败不阻断主视频播放。
- **预览缓存**：文本/Markdown 内容缓存 24 小时 TTL，写操作（重命名/删除/移动/复制/上传覆盖）与删除实例均联动清理对应缓存。

### 技术

- **新增 `:feature:preview` 模块**：graduate 自 `:app` 内的占位包，依赖单向指向 `core:{common,model,domain,designsystem}`，不依赖任何其他 feature。
- **Room Migration 7→8**：新增 `preview_cache` 元数据表（内容本体存 `cacheDir/preview/<instanceId>/`），迁移 SQL 手工核对；`8.json` schema 因本轮无构建环境为手工推导，需下次构建时用 KSP 实际导出重新核对（见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)）。
- **4 个新 Repository**：`PreviewRepository`/`MediaRepository`/`SubtitleRepository`/`ExternalOpenRepository`，均遵循既有的 instance 查找/401 失效处理模式；`FileOperationRepositoryImpl`/`UploadWorker` 新增对 `PreviewRepository` 的缓存失效调用（本仓库首次出现的 Repository 间依赖，仅限缓存失效副作用场景）。
- **新依赖**：androidx.media3（exoplayer + ui，1.4.1）、Markwon（core，4.6.2）；Coil 从 `:app` 下放到 `:feature:preview` 并正式启用；未引入任何 PDF/Office 渲染类依赖。
- **安全**：新增 `sign` 查询参数日志脱敏（`SafeLogger`，注意单词边界避免误伤"designation"等词）；媒体播放 Header 注入按实例 host 限域（`buildScopedHttpHeaders`，当前恒不触发但机制已就位）；外部打开 URI 不含 Token，已有单测断言。
- **`DomainError` 新增** `PreviewTooLarge`/`MediaUnsupported`/`MediaSourceExpired`/`ExternalOpenUnavailable` 四个子类。

### 已知限制

见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)。**本轮开发环境比以往版本更严格，禁止运行任何构建/测试命令**，因此全部验证均为静态代码审查，未做过一次编译，Media3/Markwon 等第三方库 API 用法均对照官方源码核实但未经编译或真机验证。

### 下一步

见 [v0.4_PRD.md](v0.4_PRD.md) 中记录的 v0.5（轻量管理台）/v1.0（原子级对齐）预留；Markdown 内嵌图片渲染、字幕在线搜索等留待后续版本评估。

---

## v0.3.0 — 分享 / 搜索 / 任务中心版

范围定义见 [v0.3_PRD.md](v0.3_PRD.md)，执行过程见 [v0.3_EXECUTION_PLAN.md](v0.3_EXECUTION_PLAN.md)。

### 新增功能

- **分享**：从文件菜单/文件详情页创建分享（路径只读、名称、密码可空、过期时间快捷项 永久/1天/7天/30天/自定义、启用开关）；分享列表/详情查看；修改、启用、禁用、删除（危险操作二次确认）；复制链接/密码/完整分享文案，调用 Android 系统分享面板。分享密码按后端本身的明文契约存储，日志脱敏。
- **搜索**：文件列表顶栏搜索入口，支持"当前目录"/"全局"范围切换，IME 提交（非自动防抖搜索）；搜索历史按实例隔离，上限 20 条，支持删除/清空；结果可进入目录、打开详情，未建立索引的实例显示专属"搜索不可用"提示。
- **任务中心**：统一入口（文件列表顶栏、设置页）聚合上传/下载/远程任务，"全部/上传/下载/远程"分栏；远程任务（离线下载、离线下载转存、复制、移动）4 秒轮询（仅在有运行中/等待中远程任务时轮询，离开页面即停）；远程任务可取消（二次确认）；完成任务可跳转目标目录。
- **离线下载**：任务中心 FAB 提交（URL 校验、目标目录复用现有目录选择器、下载工具单工具时自动隐藏选择器），提交成功后任务立即出现在任务中心。
- **下载状态回读**：本地下载任务不再只停在"已入队"——任务中心可见时通过 `DownloadManager.query()` 与 `ACTION_DOWNLOAD_COMPLETE` 广播双通道回读真实完成/失败状态。

### 技术

- **再拆 3 个模块**：新增 `:feature:{share,search,task}`，`:app` 内 share/task 的 Reserved 占位包按既定约定完成 graduate。
- **Room Migration 6→7**：新增 `shares`、`search_history`（唯一索引去重历史）、`remote_tasks` 三表，迁移 SQL 与 Room 自身导出的 schema JSON 逐字段核对；实例删除级联清理扩展到三新表。
- **5 个新 Repository**：`ShareRepository`/`SearchRepository`/`TaskRepository`/`OfflineDownloadRepository`/`TaskAggregationRepository`（本地上传/下载/远程任务三源合并为统一 `UnifiedTask` 流，按"运行中 > 等待 > 失败 > 完成"排序）。
- **后端字段以源码为准**：`OpenListApi` 新增的 share/search/task/offline_download DTO 字段直接对照 `openlist-ref` 参考后端源码（`handles/sharing.go`、`handles/search.go`、`handles/task.go`、`handles/offline_download.go`、`router.go`）核对，而非按文档推测；`SearchNode`/`tache.State`/`delete_policy` 等字段在参考源码中不可得的部分，保留为文档标注的待真机核对项（见"已知限制"）。
- **`DomainError` 新增** `SearchNotAvailable`、`ShareNotFound` 两个子类。

### 已知限制

见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)。

### 下一步

见 [v0.3_PRD.md](v0.3_PRD.md) 中记录的 v0.4/v0.5 预留（预览、管理台）。

---

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
