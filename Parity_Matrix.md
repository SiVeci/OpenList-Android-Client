# Parity Matrix (v1.0)

依据 `v1.0_PRD.md` §5.1~§5.3 定稿：12 域覆盖、12 列结构不自创列。P0 = `v1.0_PRD.md` §14 回归清单直接点名的项；P1 = 已实现但未被 §14 点名的次要能力。Android 状态取值：Done / Partial / Web Fallback / Deferred / Not Applicable。证据引用格式：单测文件名、`vX.Y_IMPLEMENTATION_LOG.md` 对应 Sprint 章节、或本文件内联的真机步骤描述。

本文件由 S6 创建并填充，证据来源三类：(1) 本轮 S6 对 PRD §14 的静态代码审计（`v1.0_IMPLEMENTATION_LOG.md` S6 章节，file:line 级别）；(2) v0.1~v1.0 各 Sprint 已完成的真机演示（各自 `vX.Y_IMPLEMENTATION_LOG.md`）；(3) `KNOWN_ISSUES.md` 已记录的历史限制/Deferred 决议。P0 优先级判定：§14 五个子清单逐项对照。

---

## 1. 实例管理 (Instance)

| Area | Web 操作 | Android 入口 | API/路径 | 权限 | 成功结果 | 失败结果 | 缓存影响 | Android 状态 | 差异说明 | v1.0 处理 | 验收证据 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 添加实例 | 手动配置 site_url | `AddInstanceScreen` | 无独立 API，本地校验 URL 格式 | 无需登录 | 新增 `instances` 行 | URL 非法/重复提示 | 无 | Done | 无 | 接受 | `InstanceListViewModelTest`/S0 真机验证 |
| 测试连接 | N/A（Web 无此步骤） | 表单内"测试连接"按钮 | `GET /api/public/settings`（探测可达性） | 无需登录 | 显示"可访问" | 显示错误原因 | 无 | Done (P1) | Android 独有增强 | 接受 | S0 真机验证 |
| 切换实例 | 无对应概念（Web 单实例） | `InstanceListScreen` 列表点击 | 本地 | 无需登录 | 当前实例标记转移 | N/A | 触发新实例首屏刷新 | Done | 平台差异（多实例是 App 独有能力） | 接受 | v0.1 真机验证 |
| 删除实例并清理缓存 | 无对应概念 | `InstanceListScreen` 删除按钮 | 本地：9 张表级联清理 + 磁盘缓存 | 无需登录 | 该实例所有本地数据清空 | 二次确认取消 | 全清（`file_cache`/`preview_cache`/`admin_cache`/任务/分享/搜索历史） | Done | 无 | 接受 | `InstanceRepositoryImplTest` |

## 2. 登录 (Auth)

| Area | Web 操作 | Android 入口 | API/路径 | 权限 | 成功结果 | 失败结果 | 缓存影响 | Android 状态 | 差异说明 | v1.0 处理 | 验收证据 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 账号密码登录 | 登录表单 | `LoginScreen` PASSWORD Tab | `POST api/auth/login` | 匿名 | 签发 token，写 `sessions` | 401/400 文案 | 建立 session | Done | 无 | 接受 | `AuthRepositoryImplTest`/S1 真机验证 |
| LDAP 登录 | 登录表单 LDAP 选项 | `LoginScreen` LDAP Tab | `POST api/auth/login/ldap` | 匿名，需实例启用 LDAP | 同上 | 403"未启用"/"不允许"/400 凭据错 | 建立 session | Done | 无 OTP 分支（V-601 源码确认） | 接受 | `AuthRepositoryImplTest`（10 项）；真机端到端演示待测试账号 |
| 2FA/OTP 登录 | 登录后二次输入 | 内联第二步（DEC-601） | 同密码登录 + `otpCode` | 匿名 | 同上 | 信封 `code=402`→`OtpInvalid` | 建立 session | Done | HTTP 层恒 200，判定挂点信封 code（V-602 确认） | 接受 | `AuthRepositoryImplTest`；真机演示待测试账号（LDAP/OTP 均需要，见下方"未完成验证项"） |
| 游客访问 | 无需登录直接浏览（若允许） | `LoginScreen` 游客按钮 | `POST api/auth/login`（游客凭据）或本地标记 | 匿名 | isGuest session | 同密码登录 | 建立 session | Done | 无 | 接受 | S0/S1 真机验证（匿名 fs/list 可用） |
| 管理员 Token 登录 | 无对应（Web 用 Cookie） | `LoginScreen` TOKEN Tab | `GET api/me`（校验 token 有效性） | 需有效 Token | 建立 session | Token 无效提示 | 建立 session | Done | Android 独有登录方式 | 接受 | S1 真机验证 |
| Token 失效回登录页 | 浏览器 Cookie 失效重定向 | 全局自动跳转（DEC-608） | 任意 API 401 | 已登录态 | 自动导航回 `Routes.login` | N/A | `sessions` 行删除 | **Done（S6 本轮修复）** | 自 v0.2 起主流程只有内联错误条，`v0.2_ACCEPTANCE_REPORT.md` 曾误判 Accepted；S6 静态审计发现并修复 | 修复 | `SessionExpiryViewModelTest`（3 项）；真机冒烟测试确认无回归；**完整 401 端到端复现未完成**（见 KNOWN_ISSUES.md「v1.0 进行中记录」） |
| SSO/WebAuthn | 若实例启用 | 登录页底部纯文案兜底 | 不适用 | — | 引导改用 Token 登录 | — | 无 | Web Fallback | App 不做 SSO/WebAuthn 流程 | 兜底 | S1 真机验证 |

## 3. 文件 (Files)

| Area | Web 操作 | Android 入口 | API/路径 | 权限 | 成功结果 | 失败结果 | 缓存影响 | Android 状态 | 差异说明 | v1.0 处理 | 验收证据 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 根目录浏览 | 首页列表 | `FileListScreen` | `POST api/fs/list` | Guest/User/Admin | 列出条目 | 401/403/404 兜底文案 | 写 `file_cache` | Done | 无 | 接受 | v0.1 真机验证 |
| 子目录浏览 | 点击进入 | 同上，路径导航 | 同上 | 同上 | 同上 | 同上 | 同上 | Done | 无 | 接受 | v0.1 真机验证 |
| 中文/空格/特殊字符路径 | 原生支持 | `OpenListPathCodec` | JSON body 原样传，URL 场景 percent-encode | 同上 | 正确显示/下载/上传 | — | — | Done | 无 | 接受 | S6 静态审计（`OpenListPathCodec.kt`）；历次真机验证含中文目录名 |
| 文件详情 | 属性面板 | `FileDetailScreen` | `POST api/fs/get` | 同上 | 展示元信息 | 404/403 兜底 | 读 `file_cache` | Done | 无 | 接受 | v0.1/v0.2 真机验证 |
| 下载/状态刷新/取消 | 浏览器下载管理 | 任务中心 + `DownloadManager` | 直链 `raw_url` | 同上（需可读） | 完成态展示 | 失败原因展示 | `download_tasks` 表 | Done | 取消为 v1.0 S2 新增（此前"暂不支持"） | 修复 | `TransferRepositoryImplTest`；S2 真机验证 |
| 上传/取消/失败重试 | 拖拽/选择文件上传 | `UploadWorker` + 任务中心 | `PUT api/fs/put` | 需 `CanWriteContent` + 目录 write | 完成态展示 | 失败原因 + 可重试 | `upload_tasks` 表 | Done | 重试为 v1.0 S2 新增 | 修复 | `UploadRepositoryImplTest`；S2 真机验证 |
| 新建目录 | 新建文件夹按钮 | 顶栏"新建目录"图标 | `POST api/fs/mkdir` | 需目录 write | 列表刷新 | 403/已存在提示 | 失效当前目录 `file_cache` | Done | 无 | 接受 | v0.2 真机验证 |
| 重命名 | 右键菜单 | 条目菜单"重命名" | `POST api/fs/rename` | 需目录 write + `CanRename` | 列表刷新 | 同上 | 同上 | Done | 无 | 接受 | v0.2 真机验证 |
| 删除 | 右键菜单 | 条目菜单"删除" | `POST api/fs/remove` | 需 `CanRemove` | 列表刷新 | 同上 | 同上 | Done | 无 | 接受 | v0.2 真机验证 |
| 移动 | 剪切+粘贴 | 条目菜单"移动"+目录选择器 | `POST api/fs/move` | 需 `CanMove` | 列表刷新（源+目标） | 同上 | 双目录失效 | Done | 无 | 接受 | v0.2 真机验证 |
| 复制 | 复制+粘贴 | 条目菜单"复制" | `POST api/fs/copy` | 需 `CanCopy` | 目标目录任务/刷新 | 同上 | 目标目录失效 | Done | 无 | 接受 | v0.2 真机验证 |
| 批量删除/移动/复制 | 多选工具栏 | 长按多选 + 批量操作栏 | 逐条调用同上单项 API | 同上 | 汇总成功/失败计数 | 部分失败展示明细 | 同上 | Done | 客户端串行聚合（非后端批量端点），设计选择非缺陷 | 接受 | v0.2 真机验证 |
| 目录级权限展示和 403 兜底 | 隐藏/禁用对应按钮 | `DirectoryCapability` 门控写入口 | `fs/list.write` ∧ `session.canDo(PERM_WRITE)` | 同上 | 写入口按能力显隐 | 写操作 403 仍是最终裁决 | 无 | Done | V-604 确认：`write` 字段本身不反映用户位，App 组合双重判定，比单纯字段判断更严格 | 接受 | `FilesRepositoryImplTest`；S1 真机验证 |

## 4. 上传 (Upload) — 见"文件"域合并展示；补充任务视角

| Area | Web 操作 | Android 入口 | API/路径 | 权限 | 成功结果 | 失败结果 | 缓存影响 | Android 状态 | 差异说明 | v1.0 处理 | 验收证据 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 上传任务列表 | 任务面板 | 任务中心"上传" Tab | 本地 `upload_tasks` 表 | 需登录 | 状态实时刷新 | 失败原因展示 | 无额外 | Done | 无 | 接受 | v0.2/S2 真机验证 |

## 5. 下载 (Download) — 补充任务视角

| Area | Web 操作 | Android 入口 | API/路径 | 权限 | 成功结果 | 失败结果 | 缓存影响 | Android 状态 | 差异说明 | v1.0 处理 | 验收证据 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 下载任务列表 | 浏览器下载管理器 | 任务中心"下载" Tab | 本地 `download_tasks` 表 + `DownloadManager` 状态回读 | 需登录 | 状态实时刷新 | 失败原因展示 | 无额外 | Done | 无 | 接受 | v0.2/S2 真机验证（77.5MB 完整下载） |

## 6. 分享 (Share)

| Area | Web 操作 | Android 入口 | API/路径 | 权限 | 成功结果 | 失败结果 | 缓存影响 | Android 状态 | 差异说明 | v1.0 处理 | 验收证据 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 创建分享 | 分享按钮+表单 | 文件详情/列表"分享" | `POST api/fs/add_share`（或等价） | 需 `CanShare` | 写 `shares` 表 | 403 无权限展示 | 写分享缓存 | Done | 无 | 接受 | v0.3 真机验证；S3 本轮该会话无 `CanShare` 权限，权限拒绝路径本身已验证 |
| 分享列表 | "我的分享"页 | `ShareListScreen` | `GET api/fs/list_share`（等价） | 需登录 | 列表展示 | 空态/错误态 | 读缓存 | Done | 无 | 接受 | v0.3 真机验证 |
| 分享详情 | 详情页 | `ShareDetailScreen` | `GET`分享详情 | 分享所有者 | 展示配置 | 404/403 | 读缓存 | Done | 无 | 接受 | v0.3 真机验证 |
| 修改/启用/禁用/删除分享 | 编辑/开关/删除按钮 | 详情页操作 | `POST`更新/删除 | 分享所有者 | 刷新 | 错误文案 | 失效分享缓存 | Done | 无 | 接受 | v0.3 真机验证 |
| 复制分享链接和系统分享 | 复制按钮 | 详情页 `Intent.ACTION_SEND` | 本地 | — | 系统分享面板 | — | 无 | Done | 无 | 接受 | v0.3 真机验证 |
| 入站分享链接解析与兜底 | 直接打开链接 | "我的分享"页"打开分享链接"入口 | `POST api/fs/get`（`/@s/{sid}/{path}` 路径分流） | 匿名/需密码 | 展示分享内容基础信息 | 密码错/已过期/不存在均信封透传原文 | 零 Room 写入 | Done | 目录型分享只做单层展示，不支持完整浏览（PRD §3 明确范围裁剪） | 接受 | `ShareUrlParserTest`(9)/`ShareRepositoryImplTest`(10)；S3 真机验证（同源解析成功+跨实例正确拒绝+真实"分享不存在"错误透传） |
| 分享内文件预览分发 | 直接预览/下载 | 详情页"外部应用打开文件" | 复用签名 `raw_url` | 同上 | 外部 App 打开 | 无匹配 App 时系统提示 | 无 | Partial（有意裁剪） | 未接入 App 内预览路由（该路由不带分享密码，会 401），改用外部打开满足预览分发的核心诉求 | 兜底 | S3_IMPLEMENTATION_LOG 记录裁剪理由 |
| Web 兜底（App 内粘贴+剪贴板检测，非 `ACTION_VIEW`） | 系统级链接直接打开 | 剪贴板检测卡片 | — | — | 检测到链接展示"使用/忽略" | — | — | Deferred（DEC-602 方案 A） | 不注册 `ACTION_VIEW`，平台机制限制，非功能缺陷 | 延后 | DECISION_LOG.md DEC-602 |

## 7. 搜索 (Search)

| Area | Web 操作 | Android 入口 | API/路径 | 权限 | 成功结果 | 失败结果 | 缓存影响 | Android 状态 | 差异说明 | v1.0 处理 | 验收证据 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 当前目录搜索 | 目录内搜索框 | 顶栏搜索图标（带 path） | `POST api/fs/search` | 需登录 | 结果列表 | 空态 | 无持久缓存 | Done | 无 | 接受 | v0.3 真机验证 |
| 全局搜索 | 全局搜索框 | 顶栏搜索（无 path 限定） | 同上 | 同上 | 同上 | 同上 | 同上 | Done | 无 | 接受 | v0.3 真机验证 |
| 搜索不可用状态 | 提示"未启用索引" | 搜索页错误态 | 后端错误码启发式映射 | — | — | 明确"索引未建立"文案 | — | Done | 启发式映射为 provisional（`KNOWN_ISSUES.md` V-04），接线本身完整 | 接受 | `SearchRepositoryImplTest` |
| 搜索历史 | 无对应（Web 无历史） | 搜索页历史列表 | 本地 `search_history` 表 | — | 展示/点击复用 | — | 写/读本地表 | Done (P1) | Android 独有增强 | 接受 | v0.3 真机验证 |

## 8. 任务 (Task Center)

| Area | Web 操作 | Android 入口 | API/路径 | 权限 | 成功结果 | 失败结果 | 缓存影响 | Android 状态 | 差异说明 | v1.0 处理 | 验收证据 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 上传任务 | 任务面板 | 任务中心"上传" | 本地表 | 需登录 | 刷新 | 失败原因 | 无 | Done | 无 | 接受 | 见"上传"域 |
| 下载任务 | 浏览器下载器 | 任务中心"下载" | 本地表+系统 DownloadManager | 需登录 | 刷新 | 失败原因 | 无 | Done | 无 | 接受 | 见"下载"域 |
| 远程任务（复制/移动等后端任务） | 任务面板 | 任务中心"远程" | `GET api/task/{type}/undone|done` | 需登录 | 状态轮询刷新 | 失败原因 | 无 | Done | 无 | 接受 | v0.3 真机验证 |
| 离线下载提交 | 离线下载表单 | 任务中心"+"入口 | `POST api/fs/add_offline_download` | 需 `CanAddOfflineDownloadTasks` | 提交后出现在远程任务 | 403/参数错误 | 无 | Done | 无 | 接受 | v0.3 真机验证 |
| 任务取消/重试/失败原因/完成跳转 | 对应按钮 | 任务卡片操作 | `cancel_some`等/本地状态机 | 需登录 | 状态更新 | 二次确认/错误展示 | 无 | Done | 无 | 接受 | `TaskCenterViewModelTest`；S2 真机验证 |

## 9. 预览与播放器 (Preview)

| Area | Web 操作 | Android 入口 | API/路径 | 权限 | 成功结果 | 失败结果 | 缓存影响 | Android 状态 | 差异说明 | v1.0 处理 | 验收证据 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 图片预览 | 内嵌查看器 | `PreviewScreen`/`ImagePreviewSurface` | `fs/get` → `raw_url` + Coil | 需读权限 | 渲染图片 | 加载失败占位 | `preview_cache`（元信息）+ Coil 磁盘缓存 | Done | 无 | 接受 | v0.4 真机验证 |
| 文本预览 | 内嵌查看器 | `TextPreviewSurface` | `fs/get` 原始内容 | 同上 | 渲染文本 | 失败提示 | `preview_cache` | Done | 无 | 接受 | v0.4 真机验证 |
| Markdown 预览 | 内嵌渲染 | `MarkdownPreviewSurface` | 同上 | 同上 | Markwon 渲染 | 降级为纯文本 | 同上 | Done | 无 | 接受 | v0.4 真机验证 |
| Markdown 内嵌图片 | 同上 | 同上（`ImagesPlugin`） | 同实例相对路径复用 `fs/get`；外链直连 | 同上 | 图片正确加载 | 失败不阻断正文 | 复用 `preview_cache`/Coil | Done | 仅支持内联 `![alt](url)` 语法，不支持引用式 `![alt][ref]`（已知限制） | 接受 | `PreviewRepositoryImplTest`(7)；S4 真机验证（外链+同目录相对路径+失败降级三种场景） |
| PDF 外部打开 | 内嵌查看器 | `PreviewScreen` 外部打开 | `Intent.ACTION_VIEW` | 需读权限 | 系统选择器 | 无匹配 App 提示 | 无 | Done | 平台差异（外部打开而非内嵌渲染，PRD 范围内认可） | 接受 | v0.4 真机验证 |
| Office 外部打开 | 内嵌查看器 | 同上 | 同上 | 同上 | 同上 | 同上 | 同上 | Done | 同上 | 接受 | v0.4 真机验证 |
| 未知格式兜底 | 提示不支持预览 | `PreviewScreen` 兜底态 | — | — | 提供外部打开/下载 | — | — | Done | 无 | 接受 | v0.4 真机验证 |
| 视频播放 | HTML5 video | `MediaPlayerScreen` (ExoPlayer) | `raw_url` | 需读权限 | 正常播放 | 错误分类展示 | 无额外 | Done | 无 | 接受 | v0.4 真机验证 |
| 音频播放 | HTML5 audio | 同上 | 同上 | 同上 | 同上 | 同上 | 同上 | Done | 无 | 接受 | v0.4 真机验证 |
| 字幕自动发现/手动选择/关闭 | 字幕轨道切换 | `SubtitleSelector` | 同目录同名字幕文件探测 | 同上 | 正确显示/切换/关闭 | 未找到不阻断播放 | 无 | Done | 无 | 接受 | v0.4 真机验证 |
| 签名 URL 失效刷新（媒体播放） | 浏览器重新请求资源 | `MediaPlayerViewModel.refreshAfterHttpError` | 重新 `fs/get` 获取新 `raw_url` | 需读权限 | 断流后自动重连（≤2 次重试） | 超限后展示错误 | 无 | Done | 无 | 接受 | S6 静态审计确认；v0.4 真机验证 |
| 签名 URL 失效刷新（`PreviewRepository.refreshPreviewUrl`） | — | — | — | — | — | 恒定失败（死桩） | — | **Partial（死代码）** | v0.4 遗留死方法，零调用方，实际刷新机制在别处正常工作；本轮不清理（超出回归修复范围） | 延后 | S6 静态审计；`KNOWN_ISSUES.md` |

## 10. 管理台 (Admin)

| Area | Web 操作 | Android 入口 | API/路径 | 权限 | 成功结果 | 失败结果 | 缓存影响 | Android 状态 | 差异说明 | v1.0 处理 | 验收证据 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 非管理员不可进入且零 admin 请求 | 后端路由鉴权 | 设置页"管理台"入口 | 结构性：`AdminHostScreen` DENIED 分支从不挂载 Tab 组件 | 需 Admin | — | 展示无权限说明 | — | Done | 编译期结构保证，非仅运行时隐藏 | 接受 | v0.5 S2 审计确认 |
| 管理员进入管理台 | 管理后台首页 | 同上 | `GET api/me` 强制刷新 | 需 Admin | 展示 7 Tab | — | — | Done | 无 | 接受 | v0.5 真机验证 |
| 概览页 | 后台首页统计 | Overview Tab | 组合多接口摘要 | 需 Admin | 展示统计卡片 | 单卡片独立降级 | — | Done | 无 | 接受 | v0.5 真机验证 |
| 用户列表和详情只读 | 用户管理页（含 CRUD） | Users Tab | `GET api/admin/user/list|get` | 需 Admin | 列表/详情展示 | 403/空态 | `admin_cache` | Done (范围内) | Web 支持完整 CRUD，Android 只读（PRD 范围裁决） | 兜底（Web 管理台） | v0.5 真机验证；`KNOWN_ISSUES.md` #1 |
| 存储列表/详情/驱动信息 | 存储管理页 | Storages Tab | `GET api/admin/storage/list|get` | 需 Admin | 展示 | 403/空态 | `admin_cache` | Done (范围内) | Web 支持完整 CRUD，Android 只做查看+启停+重载 | 兜底（Web 管理台） | v0.5 真机验证；`KNOWN_ISSUES.md` #2 |
| 存储启用/禁用/重新加载全部 | 对应按钮 | Storages Tab 操作 | `POST api/admin/storage/enable|disable`，`load_all` | 需 Admin | 状态刷新+缓存按 mountPath 前缀失效 | 错误文案 | 精确/全清两档策略（P-506） | Done | 无 | 接受 | v0.5 真机验证 |
| 管理任务 7 类查看/详情/取消/重试/删除记录 | 任务管理页 | Tasks Tab | `GET/POST api/admin/task/{type}/...` | 需 Admin | 状态刷新 | 错误文案 | 内存态，不入库 | Done | 无 | 接受 | v0.5 真机验证 |
| 管理任务批量轻操作 | 批量清理按钮 | Tasks Tab 批量操作行（v1.0 S5） | `clear_done`/`clear_succeeded`/`retry_failed` | 需 Admin | 操作级成功刷新 | 错误文案 | 同上 | Done | `cancel_some`/`delete_some`/`retry_some`（需 tid 数组）不纳入范围 | 接受（Deferred 子集） | `AdminTaskRepositoryImplTest`；S5 真机验证（真实"清理已完成"调用成功） |
| 索引进度/构建/更新/停止/清空 | 索引管理页 | Index Tab | `GET/POST api/admin/index/...` | 需 Admin | 状态刷新 | 错误文案 | — | Done | 无 | 接受 | v0.5 真机验证 |
| 索引更新路径选择 | 路径输入框 | Index Tab"选择路径"（v1.0 S5） | 复用普通 `fs/list` 目录选择器 | 需 Admin | 路径写入更新请求 | — | — | Done | `maxDepth` 固定 `-1`（DEC-604，无 UI 控制） | 接受 | S5 真机验证（根目录→openlist→pikpak→面包屑跳转→workspace→确认全链路） |
| 设置列表和默认设置只读 | 设置管理页（含保存/删除） | Settings Tab | `GET api/admin/setting/list` | 需 Admin | 展示 | 403/空态 | `admin_cache` | Done (范围内) | Web 支持保存/删除/重置 Token，Android 只读 | 兜底（Web 管理台） | v0.5 真机验证；`KNOWN_ISSUES.md` #3 |
| 私密设置掩码 | 后端 flag 控制显隐 | Settings Tab 掩码展示 | 同上 | 需 Admin | 私密值掩码展示 | — | 掩码在 Repository 层，非仅 UI | Done | 双重判定（后端 flag ∪ 关键字），宁可多脱敏 | 接受 | v0.5 真机验证 |
| Web 管理台外部浏览器打开 | N/A（本身即 Web） | Advanced/Overview Tab | `Intent.ACTION_VIEW` 打开 `/@manage` | 需 Admin | 系统浏览器打开 | 无匹配浏览器提示 | 零 Token/Cookie 注入 | Done | 打开后可能需 Web 端重新登录（原生/Web 会话独立） | 兜底 | V-611 源码+真实实例复测（`curl -I` 200） |

## 11. 设置 (Settings)

| Area | Web 操作 | Android 入口 | API/路径 | 权限 | 成功结果 | 失败结果 | 缓存影响 | Android 状态 | 差异说明 | v1.0 处理 | 验收证据 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 实例管理入口 | — | `SettingsScreen` | 本地 | — | 导航 | — | — | Done | 无 | 接受 | v0.1 真机验证 |
| 任务中心入口 | — | 同上 | — | 需登录 | 导航 | — | — | Done | 无 | 接受 | v0.2 真机验证 |
| 管理台入口 | — | 同上 | — | 需 Admin（展示层预判） | 导航 | 非管理员仍会在 Admin 路由内被结构性拒绝 | — | Done | 入口展示层预判 + 路由层强制校验双重保险 | 接受 | v0.5 真机验证 |
| 清理缓存 | — | 同上 | 本地 | — | 清空所有实例目录缓存 | — | 全清 `file_cache` | Done (P1) | Android 独有 | 接受 | v0.1 真机验证 |
| 调试日志开关 | — | 同上 | — | — | 切换 | — | — | Done (P1) | Android 独有 | 接受 | v0.1 真机验证 |

## 12. 缓存、安全、错误、权限、副作用（横切面）

| Area | Web 操作 | Android 入口 | API/路径 | 权限 | 成功结果 | 失败结果 | 缓存影响 | Android 状态 | 差异说明 | v1.0 处理 | 验收证据 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Token/OTP/分享密码/签名 URL 不进日志 | — | 全局 | — | — | — | — | — | Done | 无 | 接受 | S7 安全审计（计划中）+ 历次 Sprint 手动核查（S1/S3） |
| 401 裸响应统一映射 | — | `ErrorMapping.HttpException` 分支 | 任意直链/管理端点 | — | 统一转为 `DomainError` | 不再显示"未知错误" | — | Done | V-612 复现的 416/405 裸文本响应已覆盖 | 修复 | `ErrorMappingTest`（7 项） |
| raw_url 端口环境问题 | — | — | — | — | — | — | — | Not Applicable | 历史观察到的丢端口问题，S0 复测未复现（判断为实例侧 site_url 已修正），App 不做静默端口纠正（DEC-607 方案 B） | 接受（不加固） | DECISION_LOG.md DEC-607 |
| 目录级权限组合判定 | — | 见"文件"域 | — | — | — | — | — | Done | 见文件域 | 接受 | 见上 |
| 分享/图片 loader 零 Token 注入 | — | Markdown 图片/分享入站 | — | — | 外链请求不携带 Authorization | — | — | Done | 结构性保证（`NetworkSchemeHandler` 独立于 `AuthInterceptor`） | 接受 | S4 真机验证 |
| 管理危险操作二次确认 | — | 管理台各清理/删除操作 | — | 需 Admin | 危险样式确认对话框 | 取消不产生请求 | — | Done | 无 | 接受 | 历次 Sprint 真机验证 |
| 实例间数据隔离（`instanceId` 分区） | — | 全局 | — | — | 所有本地表均带 `instanceId` | — | — | Done | 无 | 接受 | 历次 Sprint 单测 + S3 跨实例分享链接正确拒绝验证 |
| Token 失效自动回登录页 | — | 见"登录"域 DEC-608 | — | — | 见上 | — | — | Done（本轮修复） | 见登录域 | 修复 | 见上 |

---

## 未完成真机验证项汇总（等待测试账号或安全可行的触发条件）

1. **LDAP 登录、2FA/OTP 登录、三身份 `/api/me` 权限位对照的真机端到端演示** —— 需要 LDAP/2FA 已配置的测试账号，已向用户请求，单测已覆盖代码路径正确性（S1）。
2. **成功解析已存在分享的完整展示（含目录/文件、有/无密码）** —— 需要具备 `CanShare` 权限的测试账号，当前会话权限不足（S3 已验证权限拒绝路径本身正确）。
3. **Token 失效全局跳转的完整真实 401 端到端复现** —— 触发条件（改真实账号密码/不安全的运行时 SQLite 手术）风险超出本轮授权范围，机制正确性由单测 + 与管理台已验证同源机制的代码同构性佐证（S6，DEC-608）。
4. **API 29 设备的真机验收** —— 本轮仅有 API 36 AVD + 无 API 29 真机，DEC-605 方案 B 已确认按 Parity 差异处理。

以上 4 项均不阻塞 S6 其余工作与后续 Sprint 推进，留待测试账号/合适真机到位后补充。
