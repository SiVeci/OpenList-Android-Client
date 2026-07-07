# OpenList Android v1.1 UI 重构实施计划（首页工作台化 + 全 App 视觉重构）

生成日期：2026-07-07（同日两次更新：① 用户裁决关闭 DEC-H1~H4；② 用户指令扩围为参考全套 12 张设计图的全 App UI 重构，记为 DEC-H5）
状态：**决策已关闭 — 作为 v1.1 功能版本推进；等待用户明确说"开始实现 v1.1"后方可改动业务代码**
上游依据：`AGENTS.md`、`v1.0_PRD.md`（能力基线）、`DESIGN.md`（设计令牌）、`G:\GitHub\UI_image\` 全套 12 张设计图（§3.6 逐图分析）
本轮产出物：仅本文件。零业务代码改动、零 Gradle 改动、零既有文档改动、未运行构建、未生成 APK。

> **版本状态说明**：`VERSION_STATUS.md` 当前 active version 仍为 **None**——按 DEC-H1 裁决以 **v1.1** 为载体推进；`VERSION_STATUS.md`、`DECISION_LOG.md` 的更新与 `v1.1_PRD.md` 的生成属于实现轮 H0 阶段任务，本轮不改任何既有文档。

> **用户已关闭的决策（2026-07-07）**：
> - **DEC-H1 = A**：作为 v1.1 功能版本推进（versionCode=7 / versionName="1.1.0"）。
> - **DEC-H2 = B**：新增 `RecentPathEntity` + DAO + `RecentPathRepository`，Room 9→10 全流程 + 真机升级验证。
> - **DEC-H3 = B**：启动落点改为首页（Splash → `INSTANCE_LIST`）。
> - **DEC-H4 = B**：引入全局底部导航（首页/文件/任务/我的）。按 DEC-H5 的 12 图证据，底栏显隐规则更新为：四顶级路由 + 搜索页 + 管理台（§4.0）。
> - **DEC-H5（本次更新新增，用户明确指令）**：v1.1 扩围为**全 App UI 重构**，视觉目标 = `G:\GitHub\UI_image\` 全套 12 张设计图。执行铁律：**视觉重构 ≠ 功能扩张**——逐图"采用/映射/裁剪"以 §3.6 清单为准；允许的功能性补充仅限 §4.4"轻功能补齐封顶清单"，清单外一律裁剪或走决策流程。

---

## 1. 背景和目标

### 1.1 背景

v1.0 已发布稳定版（versionCode=6 / 1.0.0，验收 Accepted），功能面完整但视觉停留在"能用"层级：首页是单纯实例列表，各页面为朴素 Material 列表形态。用户提供了全套 12 张设计图（连接实例/首页/文件浏览/操作面板/搜索/任务中心×2/分享/预览/我的/管理台×2），要求 v1.1 以此为视觉目标做全 App UI 重构，其中首页采用"文件优先工作台"信息架构（前两轮已定稿），并引入底部导航与最近访问。

### 1.2 目标（v1.1 范围）

1. **首页工作台化**（前两轮定稿，§4.1）：六区信息架构、实例切换器、任务摘要、快捷入口、实例管理区。
2. **全局底部导航**（DEC-H4=B）：首页/文件/任务/我的四 tab；显隐规则按 §4.0；任务 tab 带当前实例活跃任务数 badge（图 02/03 均有）。
3. **启动落点改为首页**（DEC-H3=B）。
4. **最近访问**（DEC-H2=B）：`recent_paths` 表 + Room 9→10 全流程 + 真机升级验证。
5. **全 App 视觉重构**（DEC-H5）：登录/连接实例、文件浏览、文件操作面板、搜索、任务中心、分享管理、预览、"我的"页、管理台概览，逐图对齐 §3.6 的采用清单。
6. **轻功能补齐（封顶清单 §4.4）**：仅限任务中心分组/失败 Tab/清除记录/全部取消、分享状态筛选、"我的"页退出登录与入口补全、文件页客户端排序等小型可验收项。
7. UI 全程遵守 `DESIGN.md` 令牌与 `core:designsystem`；紫仅主操作/强调、navy 仅适度 header、按钮 8dp/卡片 12dp/pill 仅 badge 与 tab；浅色/深色双模式。
8. **行为与路由不回退**：不新增/不删除/不重定义任何路由；各页面既有功能、回调语义、Repository 行为等价保留；重构=重排与换装，不是重写业务。

### 1.3 非目标（详见 §14）

不新增 Android 权限（扫码入口因此裁剪）、不做传输速度/剩余时间、不做存储容量统计、不做服务端排序、不做分享二维码、不做账号编辑/头像、不做营销构件。

---

## 2. 已读文档摘要

（与前版一致，此处保留结论要点；实现轮各阶段按 §9 要求重读对应文档。）

| 文档 | 与本计划相关的结论 |
| --- | --- |
| `AGENTS.md` | 计划轮只写计划文件；实现须用户明确授权；UI/UX 必须遵守 DESIGN.md；关键决策实时汇报 |
| `PROJECT_BRIEF.md` / `ROADMAP.md` | 原生定位不变；本工作以 v1.1 立项（DEC-H1） |
| `VERSION_STATUS.md` | active=None → 实现轮 H0 置 v1.1 |
| `DECISION_LOG.md` | UI 零 Retrofit、全 API 经 Repository、数据带 instanceId、Token Keystore、路径统一编码；P-505 管理台门控约束；DEC-H1~H5 在 H0 回写 |
| `REQUIREMENT_GUIDE.md` | H0 按其规则生成 `v1.1_PRD.md` |
| `Full_PRD.md` | §10.1 首页长期定义、§8.3 RecentPathEntity 预留、§6.4 文件列表排序/视图切换本就是长期 P0 需求——本轮轻功能补齐与长期方向一致 |
| `DESIGN.md` | 令牌与 Do's/Don'ts；12 张图的视觉语言（淡彩 plate、8dp/12dp 圆角、紫主操作）与其一致，颜色一律取主题 token 不取图中像素色 |
| `v1.0_PRD.md` | §6 UI 统一口径与 §9.1 Room 迁移全流程约束；§4.4 十九项不做清单继续有效（本计划轻功能清单不与之冲突） |
| `v1.0_EXECUTION_PLAN.md` / `v1.0_ACCEPTANCE_REPORT.md` | 执行口径（实测构建+单测+按阶段提交）、真机基线（AVD `Medium_Phone` API 36 + `nas.siveci.com:18443`）、S6-T3 真机升级验证范式 |

---

## 3. 当前代码基线分析

（§3.1~§3.5 为前版逐文件复核结论，HEAD=0b9bf99，仍然有效；§3.6 按 12 张图全套重写。）

### 3.1 InstanceListScreen / InstanceListViewModel（首页现状）

`HeroHeader`(navy) + 实例行（名称/当前 badge/baseUrl/lastUsedAt/登录三态 badge/连接三态/删除确认/进入）+ FAB + 设置入口；ViewModel 三流（instances/sessionsByInstanceId/connectionStatus）+ selectInstance/deleteInstance/testConnection。实例管理能力完整，缺工作台信息架构、任务摘要、最近访问、管理台入口。

### 3.2 导航基线

单 NavHost 纯 route 栈：`SPLASH/INSTANCE_LIST/ADD_INSTANCE/LOGIN/FILE_LIST/FILE_DETAIL/SETTINGS/SHARE_LIST/SHARE_OPEN/SHARE_DETAIL/SEARCH/TASK_CENTER/PREVIEW/MEDIA_PLAYER/ADMIN`。启动链 = Splash → login(当前实例) → FileList。**无任何底部导航基建**。`SessionExpiryViewModel` 顶层按路由 instanceId 订阅会话失效（不受本计划影响）。`SETTINGS` 页已示范"无 instanceId 页面携带当前实例跳实例级路由"先例。

### 3.3 相邻页面基线（本次扩围后的关键补充核对项）

- `FileListScreen`：AppTopBar + Breadcrumb + 搜索/分享/任务/上传/新建目录入口 + 批量选择 + `UploadProgressItem` + `FileActionSheet`。**未见排序 UI 与网格切换**（H0 基线核对确认；`Full_PRD` §6.4 本就要求，v0.1~v1.0 均未列入范围）。
- `TaskCenterScreen`：Tab=全部/上传/下载/远程 + `TaskCard` 平铺列表 + 4s 轮询 + 取消/重试/离线下载。**无**分组视图、失败 Tab、清除已完成/失败记录、批量取消。
- `SettingsScreen`：实例管理/任务中心/管理台(三态门控 `AdminEntryState`)/清理缓存/调试日志/关于(版本/已知限制/开源许可)。**无**我的分享、打开分享链接、测试连接、切换实例独立行、退出登录行（登出能力在会话层存在——v0.1 起支持退出登录、`SessionManager.invalidate` 存在，入口位置 H0 核对）、缓存容量显示。
- `ShareListScreen`：列表+详情+打开分享链接入口；**无**状态筛选 Tab 与分享搜索。
- `PreviewScreen`/`ExternalOpenSheet`：统一分发与外部打开兜底齐全。
- `AdminHostScreen`：7 Tab；概览页已有摘要统计卡结构（P-512）。**注意**：OpenList admin API 无存储容量数据（图 10/12 的"可用 x TB/总容量"无数据源，H0 复核确认后按裁剪处理）。

### 3.4 core:designsystem 组件清单

可复用：`HeroHeader`/`StatusBadge`(7 tone)/`EmptyState`/`ErrorBar`/`LoadingState`/`ListRowItem`/`Breadcrumb`/`TaskCard` 族/`AppTopBar`/`SearchBar`/`ConfirmDialog`/`TextInputDialog`/`PrimaryButton`/`FileTypeIconPlate`+`pastelStyle`/`FileActionSheet`/`BatchSelectionTopBar`/`UploadProgressItem`/`ShareComponents`/`AdminComponents`/`DirectoryPickerSheet`。
**缺**：底部导航栏、分区标题、快捷入口瓦片、实例切换 chip、图标 plate 入口行（"我的"页/管理台行式）、状态摘要条（任务中心）、能力 badge chip 行、OTP 分格输入、分段选择器的统一样式封装。→ §5.1。

### 3.5 数据层基线

- 无 `RecentPathEntity`（DEC-H2=B 新增，Room 9→10）；`Instance.lastUsedAt` 已有。
- 任务摘要零网络可派生（`TaskAggregationRepository.observeAllTasks`）；本地任务记录**无删除方法**（清除已完成/失败需在 Upload/Download DAO+Repository 增删除方法——纯本地、无 API）。
- `Session` 具备 isGuest/isAdmin/permission；管理台入口三态逻辑在 `SettingsViewModel`（提取为纯函数防漂移）。
- `feature:instance` 依赖 core 五模块，新增消费 `TaskAggregationRepository`/`RecentPathRepository` 无需新增模块依赖。
- Room=9，迁移链齐全；v1.1 唯一 schema 改动 = `recent_paths`（9→10）。

### 3.6 全套 12 张设计图逐图分析（DEC-H5 核心；图片路径为实现轮读取路径）

> 铁律：**采用=视觉与布局**；**映射=用现有能力/数据充填**；**裁剪=图中元素无对应能力或触碰红线，不做**。图与图之间冲突时以本表"主参考"列为准。颜色一律用主题 token。

| # | 文件（G:\GitHub\UI_image\…） | 对应页面 | 采用 | 映射（用现有能力充填） | 裁剪（明确不做） |
| --- | --- | --- | --- | --- | --- |
| 01 | openlist-01-connect-instance-login-otp.png | `AddInstanceScreen` + `LoginScreen` | 输入框样式（图标前缀/清除/密码可见性切换）、"测试连接"按钮+结果徽章、HTTP/HTTPS 安全提示文案、登录方式**四段分段选择**（账号密码/LDAP/游客/Token）、OTP 卡片式内联第二步（6 位分格输入）、"Token 与验证码不会写入日志"提示 | 测试连接三态=既有 `ConnectionCheck`；四方式登录=v1.0 已有；OTP=v1.0 DEC-601 内联流程换装；HTTP 风险提示=v1.0 已有 | **三步向导合并页不做**（保持 ADD_INSTANCE 与 LOGIN 两个路由，步骤条作为两页各自的纯视觉元素）；"响应 182ms"耗时显示列为可选（见 §4.4 O-5） |
| 02 | openlist-02-home-file-first-workspace.png | `InstanceListScreen`（首页） | 六区工作台布局的视觉细节：图标 plate 淡彩、当前实例行左缘高亮+对勾、最近访问行实例 badge、快捷入口瓦片、底栏样式 | §4.1 全部映射（前两轮定稿） | 搜索框"全部文件"筛选下拉与扫码图标；"最近/实例/任务"分段 tab（纵向分区替代，底栏已承担全局切换）；实例在线状态常驻点 |
| 03 | openlist-03-file-browser-list-upload-progress.png | `FileListScreen`（**文件页主参考**） | 顶栏重排（返回+实例名+登录态副标题+搜索/分享/任务badge/上传/新建图标）、面包屑卡片化、"刚刚刷新"时间戳、排序下拉、行样式（淡彩 plate+名称+元信息）、"可预览/可播放"能力 badge、底部上传进度横幅（N 个文件·百分比·目标路径·关闭）、底栏任务 badge | 刷新时间戳=Cached/Fresh 缓存时间派生；上传横幅=既有 `UploadProgressItem` 数据换装；任务 badge=活跃任务计数；排序=轻功能 L-4（客户端排序） | 目录行"356 项"子项计数（fs/list 无此数据）；**每行"可写"badge**（写权限是目录级属性，行级展示误导）；"外链文件/仅下载"badge（无对应概念）；网格视图切换列为可选 O-4 |
| 04 | openlist-04-file-actions-bottom-sheet.png | `FileActionSheet` | Sheet 重构：文件头（大图标 plate+名称+路径+大小时间+能力 badge）、主操作按钮行（播放或预览/下载/分享）、分组操作列表（详情/复制链接/重命名/移动到/复制到/外部打开）、删除红色置底+"删除前将再次确认"提示条 | 全部操作既有；主操作按钮按 `PreviewKindResolver` 派生（可播放→播放，可预览→预览，其余→下载） | 背景中的"OpenList 词标+首页式搜索框"顶栏（与图 03 冲突，**文件页顶栏以图 03 为准**）；批量选择视觉沿用图 04 的选中态样式但功能不变 |
| 05 | openlist-05-search-results-index-hint.png | `SearchScreen` | 顶栏（返回+标题+实例名+历史图标）、搜索框+搜索按钮、范围分段（当前目录/全部文件）、当前目录路径行、搜索历史 chips+全部删除、结果计数行、结果行（plate+名称+完整路径+大小时间+目录/可预览 badge）、"索引未建立会提示管理员更新索引"提示卡、**底栏显示（文件 tab 选中态）** | 范围/历史/结果/索引提示全部既有能力换装；索引提示卡点击→既有 admin 索引 Tab 深链（v0.5 已有） | 结果行图片缩略图（搜索结果数据无 thumb 则裁剪，H0 核对）；搜索中间态骨架按既有 LoadingState |
| 06 | openlist-06-task-center-full-status.png | `TaskCenterScreen`（**任务中心主参考**） | 顶栏（返回+标题+实例名+离线下载+刷新）、Tab 改为**上传/下载/远程/失败**、状态摘要条（N 运行中/N 失败/N 已完成）、分组视图（运行中/失败/已完成 + 组头右侧"全部取消/清除失败/清除已完成"）、行样式（进度%/等待中 badge/失败原因红字/重试按钮/已完成+打开按钮）、底部后台运行提示条 | 计数与分组=既有 `UnifiedTask` 流派生；重试/取消/打开=既有；失败 Tab=客户端过滤 | **传输速度与剩余时间**（"12.3 MB/s·剩余 00:01:28"，无采样机制，仅显示百分比）；"查看全部已完成任务"折叠页（组内直接完整列表） |
| 07 | openlist-07-share-manager-open-link.png | `ShareListScreen` + `ShareOpenScreen` | 分享行重构（plate+名称+路径+创建时间+启用中/已停用 badge+有密码/过期/永久有效 chips+复制链接/预览按钮）、状态筛选 Tab（全部/启用/停用）、打开分享链接面板重构（粘贴框+解析按钮+"仅处理已添加实例的同源链接"+解析结果卡+复制链接/浏览器打开+密码提示） | 分享字段/复制/启停=既有；解析流程=v1.0 入站分享换装（解析结果卡展示字段以既有 `getInboundShare` 返回为准）；筛选=轻功能 L-2 | 分享搜索框列为可选 O-3；"筛选"下拉（与状态 Tab 重复，只做 Tab）；SHARE_OPEN 保持独立路由（不改为分享列表内嵌 sheet，避免路由变更） |
| 08 | openlist-08-markdown-preview-external-open.png | `PreviewScreen` + `ExternalOpenSheet` | 预览顶栏（返回+文件名+路径·实例副标题+刷新+溢出）、元信息 chip 行（类型 badge/已缓存/复制路径/下载）、"打开方式"sheet 重构（外部打开/下载/浏览器打开三瓦片+权限刷新提示文案） | 已缓存 badge=预览缓存态；复制路径/下载/外部打开=既有；"浏览器打开"映射到既有外部打开分发（若现无浏览器直开项，H0 核对后按既有能力子集呈现） | Markdown 页上的"字幕：自动"下拉（字幕属播放器，设计图错位）；正文渲染能力不动（不新增语法支持） |
| 09 | openlist-09-profile-settings-admin-entry.png | `SettingsScreen`（**重构为"我的"页**，路由不变） | 用户卡（头像 plate+用户名+管理员/已登录 badge+baseUrl+点击进实例区）、"上次使用+连接状态"行、分组卡：常用（任务中心/我的分享/打开分享链接/清理缓存）、实例（切换实例/添加实例/测试连接）、安全与调试（调试日志开关/退出登录）、管理（轻量管理台/Web 管理台兜底，均按既有门控）、页脚（版本+已知限制+开源许可） | 用户卡=当前实例 Session；上次使用=lastUsedAt；连接状态=手动测试结果（无常驻检测）；调试日志/清理缓存/版本页脚=既有；管理台门控=AdminEntryState；我的分享/打开分享链接/测试连接/切换实例/退出登录=轻功能 L-3（全部复用既有能力接线） | "粘贴或**扫描**分享链接"中的扫码（新增相机权限，红线）；"清理缓存 1.24 GB"容量显示列为可选 O-2；"上次同步"概念改文案为"上次使用"（无同步机制）；头像/账号编辑不做 |
| 10 | openlist-10-admin-console-overview-web-fallback.png | `AdminHostScreen` 概览 Tab | 系统概览三卡重排（存储 N/M+在线 badge、任务 N+运行中/队列/失败、索引状态+最后更新）、存储状态行（plate+名称+挂载+启停按钮+状态 badge）、索引卡（当前路径+构建/更新路径/停止/清空按钮组+上次构建）、设置只读卡（掩码示例+只读 badge）、"完整 CRUD 请在 Web 管理台完成"横幅+浏览器打开按钮 | 全部=v0.5 既有能力换装（概览摘要卡 P-512、存储启停、索引操作、设置只读、Web 兜底） | 存储"可用 1.24TB/2.00TB"与"总容量 12.34TB"（admin API 无容量数据，H0 复核确认）；"管理操作已记录到服务器日志"页脚文案（无法承诺，改用图 12 的"高风险操作会二次确认"） |
| 11 | openlist-11-task-center-compact-status.png | `TaskCenterScreen` 备参考 | 与 06 同构的紧凑变体：行密度、底部提示文案"上传任务在后台继续；可在系统通知查看进度"（更准确，采用此文案） | 同 06 | 不做"紧凑/完整"两种模式切换——采用 06 结构 + 11 的行密度与提示文案，单一形态 |
| 12 | openlist-12-admin-console-overview-bottom-nav.png | `AdminHostScreen` + 底栏 | 与 10 同构；**采用其底栏形态：管理台显示底部导航（"我的"tab 选中态）**；页脚"高风险操作会二次确认" | 底栏显隐规则并入 §4.0 | 同 10 的裁剪项 |

---

## 4. 目标信息架构与重构范围

### 4.0 全局底部导航（DEC-H4=B，显隐规则按 12 图证据更新）

| Tab | 目标路由 | badge |
| --- | --- | --- |
| 首页 | `INSTANCE_LIST` | 无 |
| 文件 | `Routes.fileList(currentInstanceId)`；无会话先转 `Routes.login(...)` | 无 |
| 任务 | `Routes.taskCenter(currentInstanceId)` | 当前实例活跃任务数（RUNNING+PENDING，`observeAllTasks` 派生；图 02/03 均有此 badge） |
| 我的 | `SETTINGS`（重构为"我的"页，路由不变） | 无 |

- **显示范围（更新）**：`INSTANCE_LIST`、`FILE_LIST`、`TASK_CENTER`、`SETTINGS`、`SEARCH`（选中态=文件，图 05）、`ADMIN`（选中态=我的，图 12）。其余（SPLASH/LOGIN/ADD_INSTANCE/FILE_DETAIL/SHARE_LIST/SHARE_DETAIL/SHARE_OPEN/PREVIEW/MEDIA_PLAYER）不显示。
- **切换语义**：`popUpTo(起始) { saveState=true }` + `launchSingleTop` + `restoreState`；**当前实例切换后，文件/任务 tab 首次点击放弃 restoreState 按新实例重导航**。
- **回退语义**：非首页 tab 系统返回→首页；深层页返回不受底栏影响；FILE_LIST 根目录返回既有 `onBackToInstances → INSTANCE_LIST` 语义天然一致。
- **无实例/无当前实例**：文件/任务置灰（点击提示"请先添加实例"）。
- 实现：单 NavHost + 外层 `MainScaffold` bottomBar，**不嵌套 NavHost**，既有路由零改动。

### 4.1 首页六区（前两轮定稿，摘要）

A 适度 navy Header（OpenList + 实例切换 chip + 会话 badge + 设置/添加）→ B 搜索入口 → C 主操作"进入文件"+ 快捷瓦片（我的分享/任务中心/管理台门控/设置）→ D 任务摘要（分组活跃计数 badge + 失败高亮行 + 去处理）→ E 最近访问（跨实例、实例 badge、直达）→ F 我的实例（当前高亮+全部既有管理能力）。功能映射与无会话降级规则见前版定稿（未变更）：管理台入口三态=P-505 展示层预判；游客隐藏我的分享；空实例整页空态；各入口无会话一律先经 `Routes.login`。

### 4.2 其余页面重构目标

以 §3.6 表为唯一依据，每屏原则一致：**布局与视觉按图，数据与行为按现状，行为差异只允许来自 §4.4 清单**。各屏"行为等价自查表"（重构前列出该屏全部既有交互，重构后逐项核对）是每个重构任务的 DoD 组成部分。

### 4.3 轻功能补齐封顶清单（L=纳入强验收；O=可选，不进强验收）

| # | 项 | 层面 | 说明 |
| --- | --- | --- | --- |
| L-1 | 任务中心：失败 Tab、分组视图、清除已完成/清除失败、全部取消 | feature:task + Upload/Transfer Repository 增本地删除方法 | 纯本地记录操作零 API；清除/全部取消需 `ConfirmDialog` 二次确认；Tab 由 全部/上传/下载/远程 改为 上传/下载/远程/失败（默认上传，"全部"视角由摘要条承担——此为 UX 变更，写入 v1.1_PRD） |
| L-2 | 分享列表状态筛选 Tab（全部/启用/停用） | feature:share | 客户端过滤（v1.0_PRD §4.3 可选项兑现） |
| L-3 | "我的"页入口补全：我的分享、打开分享链接、测试连接、切换实例、退出登录 | feature:settings | 全部复用既有能力/路由；退出登录=既有会话失效链路（`SessionManager.invalidate` 系）+ 回登录页 + 二次确认；入口有无与门控随会话态（游客无我的分享等，与首页规则一致） |
| L-4 | 文件列表客户端排序（名称/大小/修改时间 + 升降序） | feature:files ViewModel | 内存排序不动 Repository/缓存；`Full_PRD` §6.4 长期 P0 兑现 |
| L-5 | 底栏任务 badge（活跃任务数） | app | `MainNavViewModel` 订阅当前实例 `observeAllTasks` 派生 |
| O-1 | 文件行"可预览/可播放"能力 badge | feature:files | `PreviewKindResolver` 纯派生展示 |
| O-2 | 清理缓存容量显示 | feature:settings | 计算缓存目录/表体量，异步显示 |
| O-3 | 分享列表本地搜索框 | feature:share | 客户端过滤 |
| O-4 | 文件列表网格视图切换 | feature:files | LazyVerticalGrid；不做则保留列表单形态 |
| O-5 | 测试连接响应耗时 ms 显示 | feature:instance | 计时包装既有 testConnection |

**清单封顶**：实现中发现图上还有本清单外的功能性元素 → 一律按 §3.6 裁剪列处理或停下走决策流程，不得顺手实现。

---

## 5. UI 组件拆分

### 5.1 core:designsystem 新增/扩展（通用）

| 组件 | 用途（图号） | 说明 |
| --- | --- | --- |
| `AppNavigationBar` | 全局底栏（02/03/05/06/09/11/12） | Material3 NavigationBar 包装：token 化选中色、禁用态、badge 槽位（L-5 启用任务计数） |
| `SectionHeader(title, trailing?)` | 分区标题（02/06/09/10） | titleMedium + primary 尾随动作 |
| `QuickActionTile` | 快捷入口瓦片（02） | 淡彩 plate + label + 禁用态 |
| `HeroHeader` 槽位扩展 | 首页 header（02） | 可选 `headerContent`，既有调用零回归 |
| `InstanceSwitcherChip` | 首页/各页顶栏实例名 chip（02/05/06/07） | on-dark 与 on-surface 两种配色变体，8dp 圆角 |
| `EntryRow(icon, tint, title, subtitle?, trailing?, enabled)` | "我的"页/管理台/操作面板行（04/09/10） | 图标 plate 入口行，分组卡内使用；替代现 `SettingsRow` 视觉 |
| `GroupCard` | 分组卡容器（09/10） | 12dp 圆角 surface 卡 + 内部分隔线 |
| `StatusSummaryStrip(running, failed, done)` | 任务中心摘要条（06/11） | 三段计数 + 语义色图标 |
| `CapabilityChips` | 能力 badge 行（03/04/05） | 可预览/可播放/目录 等小 chip 组（复用 StatusBadge tone） |
| `OtpCodeInput(length=6)` | 登录 OTP 分格输入（01） | 纯 UI 组件，值不落日志（配合既有脱敏） |
| `SegmentedSelector` | 登录方式/搜索范围/分享筛选分段（01/05/07） | Material3 SegmentedButton 的 token 化封装，pill 仅用于选中指示不用于按钮 |
| `SheetHeader(icon, title, subtitle, badges)` | 操作面板/打开方式 sheet 头（04/08） | 文件头样式统一 |

不新增：营销构件、图表组件。所有新组件浅深色双模式 + 预览注解。

### 5.2 feature 内部拆分

- **feature:instance（首页）**：`HomeHeaderSection`/`InstanceSwitcherSheet`/`HomeSearchEntry`/`HomeActionsSection`/`HomeTaskSummarySection`/`HomeRecentSection`/`HomeInstancesSection`（前版定稿不变，视觉细节以图 02 校准）。
- **feature:auth + feature:instance（连接/登录，图 01）**：`AddInstanceScreen` 换装（地址/名称输入样式+测试连接徽章+安全提示）；`LoginScreen` 换装（`SegmentedSelector` 四方式 + 凭据输入样式 + `OtpCodeInput` 卡片 + 游客文本按钮 + 安全提示文案）。
- **feature:files（图 03/04）**：顶栏重排、面包屑卡、刷新时间戳行+排序下拉（L-4）、行样式换装（plate/元信息/O-1 chips）、上传横幅重排、`FileActionSheet` 重构（SheetHeader+主操作行+分组列表+危险置底）。
- **feature:task（图 06/11）**：`StatusSummaryStrip`、分组列表（组头动作）、失败 Tab、行样式换装、清除/全部取消确认流（L-1）。
- **feature:search（图 05）**：搜索框+按钮、范围分段、历史 chips、结果行换装、索引提示卡（保留既有深链）。
- **feature:share（图 07）**：分享行换装（badge/chips/行内按钮）、状态筛选 Tab（L-2）、`ShareOpenScreen` 内容按解析面板样式换装（路由不变）。
- **feature:preview（图 08）**：预览顶栏与 chip 行换装、`ExternalOpenSheet` 三瓦片重构。
- **feature:settings（图 09）**：重构为"我的"页——用户卡+四分组卡+页脚（L-3 入口补全；门控/开关行为不变）。
- **feature:admin（图 10/12）**：概览 Tab 卡片重排（系统概览三卡/存储行/索引卡/设置卡/Web 兜底横幅）；其余 6 个 Tab 仅统一行样式（`EntryRow`/badge），不动结构。
- **app**：`MainScaffold`/`MainNavViewModel`/Splash 落点（前版定稿，显隐矩阵按 §4.0 更新）。

### 5.3 视觉规约

紫=主操作/选中态/强调，不做大面积背景；navy 仅首页 header；按钮 8dp、卡片与瓦片 12dp、badge/pill 维持 `PillShape`；全部走 colorScheme + extendedColors，禁止硬编码像素色；行高与既有 `ListRowItem` 密度对齐；底栏系统标准高度。

---

## 6. 状态模型设计

### 6.1 首页 `HomeUiState`（前版定稿，不变）

instances/currentInstance/sessionsByInstanceId/connectionStatus/adminEntry(`AdminEntryVisibility` core:model 纯函数)/taskSummary(`HomeTaskSummary` reducer)/recents(`HomeRecents` sealed)/switcherOpen/pendingDelete。任务摘要零轮询、进入时一次性刷新、失败置 `remoteStale`。

### 6.2 各重构页面新增 UI 态（均为内存派生态，无持久化）

| 页面 | 新增态 | 说明 |
| --- | --- | --- |
| 任务中心 | `selectedTab`（枚举改 UPLOAD/DOWNLOAD/REMOTE/FAILED）、`grouped`（运行中/失败/已完成三组派生）、`summary`（计数条）、清除/全部取消确认态 | 全部由既有 `tasks` 流派生；FAILED=status 过滤 |
| 分享列表 | `statusFilter`（ALL/ENABLED/DISABLED） | 客户端过滤 |
| 文件列表 | `sortOrder`（名称/大小/时间 × 升降）、（O-4 时 `viewMode`） | 内存排序；默认=名称升序（与现状列表顺序的差异在 v1.1_PRD 说明） |
| "我的"页 | 用户卡态（当前实例+Session 派生）、退出登录确认态、（O-2 缓存容量异步态） | 复用 `AdminEntryVisibility` |
| 登录页 | 方式分段选中态、OTP 卡展开态 | 既有 LoginResult/NeedOtp 状态机不变，仅换装 |

### 6.3 导航层状态

底栏选中态=路由 pattern 派生；`MainNavViewModel` 暴露 currentInstanceId/hasSession/hasInstances/activeTaskCount(L-5)；实例变更放弃 restore 规则在 `MainScaffold` 层实现并单测。

### 6.4 状态覆盖矩阵（验收基准，含新增页）

空实例/无会话降级/无最近访问/无任务/加载/错误/连接三态（前版不变）+ 新增：任务中心各组空态（无运行中/无失败/无已完成）、分享筛选空结果、搜索无结果与索引未建立提示卡、"我的"页游客/未登录形态（用户卡降级+入口隐藏）、管理台概览各卡加载/错误独立降级（沿用 P-512 逐卡降级原则）。

---

## 7. 数据来源和 Repository 影响

### 7.1 零改动复用

实例/会话/任务聚合/管理台各 Repository 接口零改动（首页与底栏 badge 只是新增消费方）；预览/搜索/分享读取路径零改动。**零新增 API 端点、零 DTO 改动、UI 零 Retrofit。**

### 7.2 最近访问（DEC-H2=B，唯一 schema 改动；前版定稿不变）

`recent_paths`（PK=instanceId+path，索引 instanceId+visitedAt，每实例 50 条滚动）+ `MIGRATION_9_10` + `10.json` + KSP identityHash 核对 + `RecentPathRepository` + `FileListViewModel` 单点埋点 + `InstanceRepositoryImpl.delete` 级联 + "清理缓存不清最近访问"语义 + 真机升级验证（v1.0 S6-T3 范式）+ 失败降级 file_cache 派生并实时汇报。

### 7.3 轻功能补齐涉及的 Repository 增量（全部纯本地，零 API）

| Repository | 增量 | 依据 |
| --- | --- | --- |
| `UploadRepository` | `clearFinished(instanceId, statuses)`（删除 SUCCESS/FAILED/CANCELLED 本地记录）、批量取消复用既有 `cancelUpload` 循环 | L-1 |
| `TransferRepository` | 同上（下载记录删除；不触碰 DownloadManager 既有取消语义） | L-1 |
| `TaskAggregationRepository` | 聚合层转发 `clearFinished`（按 source 分发）；接口新增最小方法 | L-1 |
| `AuthRepository` | 无新方法：退出登录复用既有会话失效/登出路径（H0 核对具体方法名与 DEC-608 边沿行为——登出后 `SessionExpiryViewModel` 会触发回登录页，属预期链路，验收点） | L-3 |
| `FilesRepository` | 无改动（排序在 ViewModel 内存做；O-2 若做，缓存体量查询加只读方法） | L-4/O-2 |

### 7.4 导航接线（前版定稿 + 本次更新）

首页新回调（onOpenFiles/onOpenSearch/onOpenTaskCenter/onOpenShareList/onOpenAdmin/onOpenRecentPath）→ 全部指向既有路由；`MainScaffold` 底栏容器；Splash 落点改 `INSTANCE_LIST`；底栏显隐矩阵按 §4.0（SEARCH/ADMIN 纳入显示）。既有路由定义与回调、`SessionExpiryViewModel`、各页内层 Scaffold 全部不动。

---

## 8. 决策记录（已全部关闭）

- **DEC-H1 = A**（v1.1 功能版本）：H0 生成 v1.1_PRD/EXECUTION_PLAN、更新 VERSION_STATUS；versionCode=7/"1.1.0"。
- **DEC-H2 = B**（RecentPath + Room 9→10 全流程）：§7.2；失败降级 file_cache 派生（R4）。
- **DEC-H3 = B**（启动落点=首页）：§7.4；写入 RELEASE_NOTES。
- **DEC-H4 = B**（底部导航）：§4.0；按 DEC-H5 图证据将 SEARCH（文件选中）与 ADMIN（我的选中）纳入显示范围；任务 tab badge 启用（L-5）。
- **DEC-H5（2026-07-07 用户指令，本次更新）**：v1.1 扩围为全 App UI 重构，参考 `G:\GitHub\UI_image\` 全套 12 张设计图。执行铁律：①逐图采用/映射/裁剪以 §3.6 为唯一清单；②功能性差异仅限 §4.3 封顶清单（L-1~L-5 强验收、O-1~O-5 可选）；③行为等价自查表进入每个重构任务 DoD；④图间冲突以 §3.6"主参考"标注为准（文件页=03、任务中心=06 结构+11 文案密度、管理台底栏=12）；⑤红线不破：零新增权限（扫码裁剪）、零新增路由、零 API 变更。

四项裁决与 DEC-H5 决议在实现轮 H0 回写 `DECISION_LOG.md`。

---

## 9. 分阶段实施步骤

执行口径（每阶段均适用）：实测 `JAVA_HOME="C:/Program Files/Java/jdk-20" ./gradlew compileDebugKotlin testDebugUnitTest` 通过 + 新增/触碰路径单测全绿 + 行为等价自查表核对 + 按阶段一次 git 提交（`feat(v1.1): Hx - <摘要>`）；H3 起 `assembleDebug` 可装 AVD 演示并记录。含 UI 的阶段实现前必须读取该阶段对应的设计图（§3.6 表列明图号与路径）。禁止纯静态审查口径。

| 阶段 | 内容 | 参考图 | 任务拆分 |
| --- | --- | --- | --- |
| **H0 版本文档 + 基线核对** | v1.1_PRD.md、v1.1_EXECUTION_PLAN.md、DECISION_LOG/VERSION_STATUS 更新；扩围新增基线核对：文件列表排序/网格现状、登出入口与方法名、搜索结果 thumb 字段、AdminStorage 容量字段、预览"浏览器打开"现状 | 全套过目 | H0-T1 PRD；H0-T2 EP；H0-T3 治理文档回写；H0-T4 基线核对清单落 IMPLEMENTATION_LOG（结论直接决定 §3.6 裁剪列中"待核对"项的最终归属） |
| **H1 设计系统底座** | §5.1 全部新组件/扩展 + `AdminEntryVisibility` 纯函数 + 任务摘要 reducer | 02/04/06/09 局部 | H1-T1 SectionHeader/QuickActionTile/GroupCard/EntryRow；H1-T2 HeroHeader 槽位+InstanceSwitcherChip；H1-T3 AppNavigationBar（badge 槽位）+StatusSummaryStrip+CapabilityChips；H1-T4 OtpCodeInput+SegmentedSelector+SheetHeader；H1-T5 AdminEntryVisibility 提取+Settings 切换+4 态单测；H1-T6 摘要 reducer+单测；全组件浅深色预览 |
| **H2 首页 ViewModel 聚合** | 前版定稿（含 feature:instance 测试基建补齐） | — | H2-T1 状态聚合+切流；H2-T2 一次性任务刷新；H2-T3 单测（无/单/多实例、游客/管理员、计数、切流） |
| **H3 首页界面重组（最早可运行节点）** | 六区布局（E 区占位）+ 首页新回调接线 | **02** | H3-T1 A/B/C 区+切换 sheet；H3-T2 D 区；H3-T3 F 区（行为等价自查）；H3-T4 NavHost 回调；H3-T5 AVD 演示记录 |
| **H4 底部导航 + 启动落点** | MainScaffold/AppNavigationBar/MainNavViewModel/Splash；显隐矩阵（§4.0 含 SEARCH/ADMIN）；任务 badge（L-5） | 02/03/05/12 底栏 | H4-T1 容器+显隐矩阵；H4-T2 tab 路由+实例变更放弃 restore+单测；H4-T3 Splash 落点+单测；H4-T4 回退语义与深层页走查；H4-T5 FAB/底栏间距走查（首页 FAB 移除——添加实例已有双入口） |
| **H5 最近访问（Room 9→10）** | 前版定稿全链路 | 02 E 区 | H5-T1 Entity/DAO/迁移/schema/identityHash；H5-T2 Repository+单测；H5-T3 埋点+级联+文案+单测；H5-T4 E 区接线；H5-T5 **真机升级验证（硬门槛）** |
| **H6 连接/登录 + "我的"页重构** | 图 01 两页换装（OTP 分格/四方式分段/测试连接徽章/安全文案）；图 09 "我的"页（用户卡+四分组+L-3 入口补全+退出登录确认流） | **01/09** | H6-T1 AddInstance 换装；H6-T2 Login 换装（LoginResult 状态机不动，OTP 不落日志核查）；H6-T3 "我的"页重构+L-3 接线（游客/未登录降级矩阵）；H6-T4 退出登录链路（复用会话失效+验证 SessionExpiry 跳转为预期行为）+单测；H6-T5 行为等价自查（原 Settings 全部行捡回）+AVD 演示 |
| **H7 文件浏览 + 操作面板重构** | 图 03 文件页换装 + L-4 排序 + O-1（可选）+ 上传横幅；图 04 FileActionSheet 重构 | **03/04** | H7-T1 顶栏/面包屑卡/刷新时间戳；H7-T2 行样式+排序（ViewModel 内存排序+单测）；H7-T3 上传横幅重排；H7-T4 FileActionSheet 重构（主操作派生+危险置底）；H7-T5 行为等价自查（上传/批量/操作全链路）+AVD 演示 |
| **H8 任务中心重构** | 图 06 结构+图 11 密度与文案；L-1 全部（失败 Tab/分组/清除/全部取消） | **06/11** | H8-T1 Tab 改造+摘要条+分组派生+单测；H8-T2 Upload/Transfer/聚合层 clearFinished+单测；H8-T3 组头动作+确认流 UI；H8-T4 行样式换装；H8-T5 行为等价自查（取消/重试/打开/离线下载/轮询不回退）+AVD 演示 |
| **H9 搜索 + 分享 + 预览 + 管理台概览重构** | 图 05/07/08/10/12 换装 + L-2 | **05/07/08/10/12** | H9-T1 搜索页换装（历史/范围/索引提示卡深链保留）；H9-T2 分享列表换装+状态筛选+单测；H9-T3 ShareOpen 面板换装；H9-T4 预览顶栏+打开方式 sheet；H9-T5 管理台概览卡重排（容量类数据按 H0 核对结论裁剪；非管理员零请求不回退自查）；H9-T6 各页行为等价自查+AVD 演示 |
| **H10 状态打磨与全量走查** | §6.4 全矩阵；浅色/深色 × 小屏(~360dp)/常规屏 × 全部重构页；无障碍触达抽查 | 全套对照 | H10-T1 空/错/载态收口；H10-T2 深浅色矩阵走查（页面×模式记录）；H10-T3 小屏走查；H10-T4 与 12 图逐屏对照的偏差清单（接受项/修正项分列） |
| **H11 回归与验收** | 全场景回归 + 版本号 + 验收文档 | — | H11-T1 §12 验收清单逐项证据；H11-T2 既有主流程回归（v1.0 §14 四组清单抽样重跑 + 本轮全部行为等价自查表汇总核验）；H11-T3 versionCode=7/"1.1.0"+完整构建重跑；H11-T4 v1.1_ACCEPTANCE_REPORT + README/RELEASE_NOTES/KNOWN_ISSUES/VERSION_STATUS |

---

## 10. 阻塞关系

```text
（已解除）DEC-H1~H5 关闭 → 唯一闸门 = 用户说"开始实现 v1.1"
H0 ──► 一切实现（PRD/EP 先行；H0-T4 基线核对结论决定 §3.6 待核对项归属）
H1 ──► H2 ──► H3 ──► H4（底栏需首页承载）
H3 ──► H5；H4 ∥ H5 可并行
H1（EntryRow/GroupCard/OtpCodeInput/SegmentedSelector/SheetHeader）──► H6/H7/H8/H9
H6/H7/H8/H9 相互独立，可按序或穿插；均依赖 H1，H7~H9 建议在 H4 之后（底栏显隐已就位再走查）
H5-T1 ──► H5-T2/T3/T4 ──► H5-T5（真机升级验证=H5 出口硬门槛）
H4+H5+H6+H7+H8+H9 ──► H10 ──► H11
```

硬约束：H1-T5 门控函数与 Settings 行为等价后才可被 H3/H6 复用；H5-T5 失败→R4 降级+实时汇报；H4/H7~H9 任何一步发现必须改路由定义/回调签名才能继续→停下实时汇报；轻功能清单（§4.3）封顶，图上清单外功能元素一律裁剪或走决策。

---

## 11. 最早可运行节点

- **最早节点 = H3 出口**：新首页五区（E 占位）+ 全部入口直达，AVD + 真实实例演示（无底栏、旧启动落点）。
- **第二节点 = H4 出口**：底部导航 + 新启动落点 + 任务 badge——图 02 完整骨架。
- **第三节点 = H5 出口**：最近访问上线（含真机升级验证）。
- **此后 H6~H9 每阶段出口都是一个可演示的重构页面组**，可分别交用户确认观感，H10/H11 收口。

---

## 12. 验收清单

**首页与全局（前版定稿项全部保留）**
- [ ] 首页六区功能映射逐项可用；实例管理与 v1.0 行为等价；管理台入口三态一致且非管理员零 admin 请求
- [ ] 任务摘要计数/失败高亮/remoteStale；最近访问写入/跨实例排序/直达/上限裁剪/空态
- [ ] 底栏四 tab 目标正确+任务 badge 计数正确；显隐矩阵（含 SEARCH=文件选中、ADMIN=我的选中）逐路由验证；tab 状态保持与实例切换放弃恢复；回退语义；无实例置灰
- [ ] 启动落点（无实例→添加；有实例→首页）；SessionExpiry 在新导航体系下不回退
- [ ] Room 9→10：迁移 SQL/10.json/identityHash/真机原地升级数据保留；删除实例级联清理；清理缓存不清最近访问

**各重构页面（每页三件套：视觉对照 + 行为等价 + 状态覆盖）**
- [ ] 连接实例页/登录页：四方式分段、OTP 分格可用且不落日志、测试连接徽章、HTTP 提示——LoginResult 状态机行为与 v1.0 等价
- [ ] 文件页：顶栏入口一个不少、面包屑/刷新时间戳/排序（L-4 三键升降序）正确、上传横幅数据同源、操作面板全部操作可达、删除确认不回退；批量选择/能力门控（v1.0 DirectoryCapability）不回退
- [ ] 任务中心：摘要条计数=分组内容=Tab 过滤三者一致；清除已完成/失败与全部取消有确认且只影响本地记录；取消/重试/打开/离线下载/轮询不回退；默认 Tab 变更写入 PRD
- [ ] 搜索页：范围/历史/结果跳转/索引提示卡深链不回退
- [ ] 分享页：状态筛选正确；复制/启停/详情/打开分享链接（含密码流程）不回退
- [ ] 预览页：chip 行数据正确（已缓存态）；打开方式三瓦片映射既有能力；Markdown 渲染无回归
- [ ] "我的"页：原 Settings 全部行可达（行为等价自查表）；L-3 新入口全部生效且按会话态门控；退出登录二次确认+回登录页+不残留会话
- [ ] 管理台概览：卡片重排后 v0.5 全部操作可达（启停/索引四操作/设置只读/Web 兜底）；容量类数字按 H0 结论裁剪；危险操作二次确认不回退

**技术与横切**
- [ ] UI 零 Retrofit；新增 Repository 方法（clearFinished/RecentPath）单测全绿；DTO/Entity 不出仓储层；数据全带 instanceId
- [ ] 全部 ViewModel 新增态单测（首页/任务分组与过滤/分享筛选/文件排序/MainNav/Splash）
- [ ] `compileDebugKotlin`+`testDebugUnitTest` 实测通过（版本号提升后重跑）；敏感信息（OTP/密码/Token）不落日志复查
- [ ] 浅色/深色 × 小屏/常规屏走查矩阵覆盖全部重构页并有记录；12 图逐屏对照偏差清单（接受/修正分列）归档
- [ ] 无新增权限、无新增运行时依赖、无新增路由（diff 审查）

---

## 13. 风险和降级策略

| # | 风险 | 缓解/降级 |
| --- | --- | --- |
| R1 | **全 App 重构回归面大（DEC-H5 首要风险）**：九屏换装，任何一屏漏接既有交互即回归 | 按屏分阶段（H6~H9 各自出口演示）；每屏"行为等价自查表"进 DoD；H11 用 v1.0 §14 清单抽样重跑；重构只动 Composable 层，ViewModel 既有方法与 Repository 行为不动（L 清单除外） |
| R2 | 设计图间不一致（03 与 04 顶栏冲突、06 与 11 变体、10 与 12 底栏） | §3.6 已定主参考（文件=03、任务=06+11 文案、管理台=12）；实现不再自行仲裁，遇表外冲突停下汇报 |
| R3 | 图上功能性元素诱发范围蔓延（速度/ETA、容量统计、扫码、缩略图…） | §4.3 封顶清单 + §3.6 裁剪列双保险；"顺手实现"违反 DEC-H5 铁律③⑤ |
| R4 | Room 9→10 迁移或真机升级失败 | 唯一新表零改旧表；H5-T5 失败→E 区降级 file_cache 派生+回退迁移+实时汇报（唯一允许追加修复迁移的场景） |
| R5 | 底部导航全局回归（跨实例串栈/回退环/深层页误显/popUpTo(0) 链路交互） | 不嵌套 NavHost、路由零改动；放弃 restore 规则单测；H4-T4 专项走查；ADMIN/SEARCH 纳入显示后新增"底栏点击离开管理台/搜索再返回"用例；最终降级=收窄显示范围（需用户确认） |
| R6 | 任务中心 Tab 语义变更（去"全部"）引发使用习惯回归 | 摘要条承担全局概览；变更写入 v1.1_PRD 与 RELEASE_NOTES；若走查负反馈，加回 ALL 的成本=一个枚举值（预留） |
| R7 | 退出登录链路与 SessionExpiry 边沿检测交互（登出→session 置空→全局跳转触发） | 该跳转正是预期行为，H6-T4 以单测+真机确认只跳一次、无循环；异常则登出改为显式导航+抑制一次边沿 |
| R8 | 清除任务记录误删（用户点错） | ConfirmDialog 二次确认+明确影响范围文案（"仅删除本地记录，不影响服务器任务"）；只删终态记录（运行中不可清） |
| R9 | HeroHeader/SettingsRow 等共享组件改动波及未重构页 | HeroHeader 只加默认槽位；SettingsRow 替换为 EntryRow 时逐调用点核对；H1 出口全调用点编译+视觉自查 |
| R10 | 启动落点变化 + 我的页重构 双重改变老用户习惯 | "进入文件"一键直达+底栏文件 tab；原 Settings 行全部保留仅重排；RELEASE_NOTES 说明 |
| R11 | 小屏放不下（首页六区、任务中心摘要条+分组、我的页四分组卡） | 高密度规约；H10-T3 小屏走查；首屏保 A/B/C，其余滚动 |
| R12 | 走查工作量随屏数膨胀导致敷衍 | H10-T4 强制产出"12 图逐屏对照偏差清单"文档，接受项须写理由，不允许空表 |

---

## 14. 明确"不做什么"

1. **零新增 Android 权限**：图 09"扫描分享链接"的扫码入口裁剪（需相机权限），只做粘贴。
2. **零新增路由、零路由语义变更**：图 01 三步向导不合并页面；SHARE_OPEN 保持独立路由；不做嵌套 NavHost。
3. **零 API/DTO 变更**：不做传输速度/剩余时间（06/11）、目录子项计数（03）、存储容量与总容量统计（10/12）、搜索结果缩略图（05，除非 H0 核对确认字段已有）、"管理操作已记录到服务器日志"承诺文案（10）。
4. 不做行级"可写"badge（03，目录级属性行级展示误导）、"外链文件/仅下载"badge（03，无对应概念）、Markdown 页字幕下拉（08，设计错位）。
5. 不做任务中心"紧凑/完整"双模式切换（06/11 融合为单一形态）、"查看全部已完成"独立折叠页。
6. 不做分享二维码、分享访问统计；不做"我的"页头像上传/账号编辑/改密（v1.0 §4.4 继续有效）。
7. 不做服务端排序、目录树面板、README 自动展示等 `Full_PRD` 长期项（客户端排序 L-4 除外）。
8. 不动业务逻辑层：各 Repository 既有方法行为、缓存策略、权限门控、会话机制、SessionExpiry、管理台 checkAccess 全部原样（§7.3 列明的纯本地新增方法除外）。
9. 不采用营销构件；不取设计图像素色（一律主题 token）；底栏不做 navy 皮肤。
10. 管理台除概览 Tab 卡片重排与行样式统一外，不动其余 6 个 Tab 的结构与交互；首页/管理台零 admin 数据越界拉取。
11. O-1~O-5 可选项不进强验收；做不做由对应阶段时间余量决定，不做不算缺口。
12. 本轮（计划轮）不改任何业务代码/Gradle/资源/既有文档——一切实现待用户明确说"开始实现 v1.1"。
