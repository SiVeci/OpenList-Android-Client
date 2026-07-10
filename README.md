# OpenList Android Client

原生 Android 客户端，用于连接自建的 [OpenList](https://github.com/OpenListTeam/OpenList) 实例：多实例管理、首页工作台、底部导航、最近访问、账号密码/LDAP/2FA-OTP/游客/管理员 Token 多种鉴权方式、目录浏览与目录级权限门控、文件详情、下载（含状态刷新/取消）、文件写操作（新建/重命名/删除/移动/复制）、批量选择、上传（含失败重试）、分享（含入站链接解析）、搜索、统一任务中心、基础离线下载、统一文件预览分发（图片/文本/Markdown[含内嵌图片]/视频/音频 App 内预览，PDF/Office/未知格式外部打开兜底）、基础字幕支持、轻量管理台（管理员门控、用户/存储查看、存储启停、7 类任务管理+批量轻操作、索引管理[含路径选择器]、设置查看、Web 管理台兜底）。

当前版本：**v1.3.0（产品级中性配色与统一中文字体体系）**。本版范围与验收见 [v1.3_PRD.md](v1.3_PRD.md)、[v1.3_EXECUTION_PLAN.md](v1.3_EXECUTION_PLAN.md)、[v1.3_ACCEPTANCE_REPORT.md](v1.3_ACCEPTANCE_REPORT.md)；长期规划见 [Full_PRD.md](Full_PRD.md)。与 Web 端逐项对照见 [Parity_Matrix.md](Parity_Matrix.md)。历史范围见 [v1.2_PRD.md](v1.2_PRD.md)、[v1.1_PRD.md](v1.1_PRD.md)、[v1.0_PRD.md](v1.0_PRD.md)、[v0.5_PRD.md](v0.5_PRD.md)、[v0.4_PRD.md](v0.4_PRD.md)、[v0.3_PRD.md](v0.3_PRD.md)、[v0.2_PRD.md](v0.2_PRD.md)、[v0.1_PRD.md](v0.1_PRD.md)。

## 技术栈

Kotlin · Jetpack Compose (Material 3) · Hilt · Retrofit + OkHttp + kotlinx.serialization · Room · DataStore · WorkManager · Navigation Compose · Coil · Media3 (ExoPlayer) · Markwon

## 工程结构

多 Gradle 模块，依赖方向单向无环：`core:{common,model,designsystem}` 为叶子模块 → `core:{database,auth}` → `core:network` → `core:domain` → `data:repository`（汇聚全部 core 依赖）→ `feature:*` → `app`（DI 汇聚 + 导航）。

```
core/common          ApiResult / DomainError / SafeLogger / DispatcherProvider
core/model           领域模型（Instance / FileNode / FileDetail / PreviewTarget / MediaSource / AdminModels 等）
core/designsystem    主题 + 交互组件（Dialog/Sheet/工作台组件/底栏/状态摘要/ExternalOpenSheet/AdminComponents 等）
core/database        Room（Instance/Session/FileCache/.../PreviewCache/AdminCache）+ DataStore + Migrations
core/auth            CryptoManager（Keystore AES-GCM）/ SessionManager / TokenProvider
core/network         OpenListApi / OpenListClientFactory / 拦截器 / 路径与 URL 规范化 / 上传与预览专用 OkHttpClient
core/domain          Repository 接口
data/repository      Repository 实现 + UploadWorker（WorkManager CoroutineWorker）
feature/instance     实例管理
feature/auth         登录
feature/files        文件浏览 / 详情 / 写操作 / 批量选择 / 上传入口与进度面板 / 分享创建入口
feature/settings     设置（含管理台入口行）
feature/upload       预留（上传 UI 内嵌在 feature/files）
feature/share        分享列表 / 详情 / 创建 / 编辑 / 启停 / 删除 / 分享文件预览接线
feature/search       当前目录 / 全局搜索 + 搜索历史
feature/task         统一任务中心（上传/下载/远程）+ 离线下载提交
feature/preview      统一文件打开分发 / 图片/文本/Markdown 预览 / 视频音频播放器 / 字幕
feature/admin        轻量管理台：门控 / 概览 / 用户查看 / 存储查看+启停 / 驱动信息 / 7 类任务管理 / 索引管理 / 设置查看 / Web 兜底
```

reserved 特性（webfallback）仍以 `:app` 内占位包形式保留，供未来 SSO/WebAuthn 使用（预览已在 v0.4、管理台已在 v0.5 graduate 为真实模块）。

## 构建

```bash
./gradlew assembleDebug          # 全部模块 Debug 构建
./gradlew testDebugUnitTest      # 本地单元测试（路径编解码 / Base URL 规范化 / 批量操作聚合 / 预览分发 / 缓存失效 / 播放重试上限 / 管理台门控与各 Repository 等）
./gradlew assembleRelease        # Release APK（见下方签名配置）
```

需要 JDK 17。Windows 下如未设置 `JAVA_HOME`，可指向 Android Studio 自带 JBR，例如：

```bash
export JAVA_HOME="D:/Program Files/Android/Android Studio/jbr"
```

### 配置 Release 签名（可选）

不配置签名时 `assembleRelease` 仍会成功，只是产出未签名 APK。要签名，先自行生成一个 keystore（`keytool` 是本地一次性操作，请自己执行，不要把 keystore 提交到仓库）：

```bash
keytool -genkeypair -v -keystore release.keystore -alias openlist -keyalg RSA -keysize 2048 -validity 10000
```

然后在**项目根目录的 `local.properties`**（已被 `.gitignore` 忽略）追加：

```properties
RELEASE_STORE_FILE=../release.keystore
RELEASE_STORE_PASSWORD=你的store密码
RELEASE_KEY_ALIAS=openlist
RELEASE_KEY_PASSWORD=你的key密码
```

### GitHub Actions 手动云端签名构建

仓库提供手动触发的 GitHub Actions 工作流：`Android Manual APK`。它不会在 push、PR 或 tag 时自动运行，也不会自动创建 GitHub Release。

在 GitHub 仓库 `Settings -> Secrets and variables -> Actions` 配置：

```text
RELEASE_KEYSTORE_BASE64
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

`RELEASE_KEYSTORE_BASE64` 是本地 `release.keystore` 的 Base64 单行内容。不要提交 keystore、Base64 文本或 `local.properties`。

手动构建路径：

1. 打开 GitHub 仓库的 `Actions`。
2. 选择 `Android Manual APK`。
3. 点击 `Run workflow`。
4. 保持 `run_tests=true` 可在构建前运行 `testDebugUnitTest`；临时跳过测试时可改为 `false`。
5. 勾选要构建的 APK 类型。默认只构建 `arm64-v8a`，适合绝大多数现代 Android 手机。
6. 构建完成后，在 workflow run 的 `Artifacts` 下载签名 APK。

云端构建会执行：

```bash
./gradlew assembleRelease \
  -Popenlist.enableAbiSplits=true \
  -Popenlist.abiIncludes=arm64-v8a \
  -Popenlist.universalApk=false \
  --stacktrace
```

Windows PowerShell 本地手动验证时需要给 `-P` 参数加引号：

```powershell
.\gradlew.bat assembleRelease "-Popenlist.enableAbiSplits=true" "-Popenlist.abiIncludes=arm64-v8a" "-Popenlist.universalApk=false" --stacktrace
```

workflow 可选 APK 类型：

```text
arm64-v8a       现代 Android 手机，默认推荐
universal       不确定设备架构时使用，体积更大
armeabi-v7a     老旧 32 位 ARM 设备
x86             32 位模拟器/设备
x86_64          64 位模拟器/设备
```

产物只会包含你勾选的类型，并按版本号和适用平台重命名，例如：

```text
openlist-android-v1.3.0-arm64-v8a.apk
openlist-android-v1.3.0-universal.apk
```

## 快速上手（v1.3 功能范围）

1. 打开 App，点击「添加实例」，输入 OpenList 实例地址（`http(s)://` 开头，支持部署在子路径；`http://` 会显示明文传输风险提示）。
2. 「测试连接」确认可达（依次尝试 `/ping`、`/api/public/settings`）。
3. 保存后进入登录页：账号密码 / LDAP / 游客访问 / 管理员 Token，四选一；开启 2FA 的账号在密码登录后会内联展开验证码输入步骤。
4. 登录成功后进入首页工作台：可切换当前实例，使用搜索、根目录、我的分享、任务中心、管理台等快捷入口，并查看任务摘要、最近访问和实例管理区。
5. 底部导航提供「首页 / 文件 / 任务 / 我的」四个主入口；文件与任务入口会跟随当前实例启用，任务入口显示运行中数量 badge。
6. 进入文件列表后可进行目录浏览、子目录跳转、面包屑、下拉刷新、排序；断网时展示本地缓存并提示，访问过的路径会进入首页最近访问。
7. 已登录用户（非游客）可见写操作入口：
   - 顶部「新建目录」「上传」按钮；上传经系统文件选择器多选，后台流式上传并可在顶部「上传进度」入口查看/取消。
   - 每行「更多」菜单：预览（可预览格式）、重命名、移动、复制（弹出目标目录选择器）、删除（危险操作二次确认）。
   - 长按任意行进入批量选择模式，可全选/取消全选，批量删除/移动/复制；部分失败时可点击提示上的「查看」看每一项失败原因。
8. 点击图片/视频/音频/文本/Markdown 文件直接进入 App 内预览/播放；点击 PDF/Office/未知格式文件进入详情页，主操作为「外部打开」（弹出外部打开/下载/网页打开三选一）。
9. 文件详情页：可预览格式显示对应主操作（查看图片/播放视频/播放音频/查看文档），保留下载、复制路径/链接、分享入口（非游客可见）。
10. 视频播放器：播放/暂停/进度拖动、横屏沉浸式；右上角字幕入口可选自动发现的候选字幕、从当前目录手动选择任意文件、或关闭字幕。
11. 文件菜单/详情页「分享」：路径只读 + 名称 + 密码（可空）+ 过期时间快捷项 + 启用开关，创建成功后可复制链接/密码/完整文案或调用系统分享面板。「我的分享」可查看/筛选/本地搜索/修改/启停/删除已创建分享；分享详情页可直接预览已分享的文件。
12. 搜索页支持当前目录/全局二选一、历史记录、索引提示；可预览的搜索结果直接进入统一预览。
13. 任务中心统一查看上传/下载/远程任务（离线下载/转存/复制/移动），支持摘要、分组、失败 Tab、清除已完成、清除失败、全部取消；已完成任务点击后自动判断目标是文件还是目录，文件直接进入预览、目录跳转浏览；右上角「+」提交新的离线下载任务。
14. 「我的」页可清理本地缓存并显示缓存容量、开关调试日志、快速进入当前实例任务中心；管理员账号额外可见「管理台」入口（非管理员显示为不可用说明，游客不显示）。
15. 管理台（仅管理员可进入）：概览（实例信息+存储/任务/索引摘要卡+Web 兜底入口）、用户查看（列表+只读详情）、存储查看（列表+详情+驱动信息，可启用/禁用/重新加载全部）、任务管理（覆盖 7 类后端任务，可取消/重试/删除记录，支持按类型批量清理已完成/已成功/重试全部失败）、索引管理（进度查看+构建/更新/停止/清空，更新路径可用目录选择器指定）、设置查看（分组只读+默认设置对比，私密值掩码）、高级页（Web 管理台外部打开兜底 + 原生不覆盖能力清单）。
16. 分享入站：「我的分享」页「打开分享链接」入口，粘贴或从剪贴板检测到的 OpenList 分享链接，解析展示分享基础信息，可复制链接/浏览器打开，非目录文件可外部应用打开。

v1.2 **不包含**：完整 Web 管理台 CRUD（用户/存储/设置/Meta/消息/扫描）、分享态完整目录浏览与上传、PDF/Office 内置预览、SSO/WebAuthn 原生化、断点续传/分片上传下载、文件网格视图、扫码/二维码、传输速度/剩余时间、目录子项计数、行级可写 badge、外链文件 badge、Markdown 字幕下拉等既有裁剪项。本版不扩大 v1.1 功能范围；具体范围见 [v1.2_PRD.md](v1.2_PRD.md) 与 [v1.1_PRD.md](v1.1_PRD.md)，逐项 Web/Android 对照见 [Parity_Matrix.md](Parity_Matrix.md)，已知限制见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)。

## 其他文档

- [KNOWN_ISSUES.md](KNOWN_ISSUES.md) — 已知问题与限制
- [RELEASE_NOTES.md](RELEASE_NOTES.md) — 版本发布说明
- [Parity_Matrix.md](Parity_Matrix.md) — 与 Web 端逐项对照
- [v1.1_ACCEPTANCE_REPORT.md](v1.1_ACCEPTANCE_REPORT.md) — v1.1 验收报告
- [v1.0_ACCEPTANCE_REPORT.md](v1.0_ACCEPTANCE_REPORT.md) — v1.0 验收报告
- [v0.5_ACCEPTANCE_REPORT.md](v0.5_ACCEPTANCE_REPORT.md) — v0.5 验收报告
- [v0.4_ACCEPTANCE_REPORT.md](v0.4_ACCEPTANCE_REPORT.md) — v0.4 验收报告
- [v0.2_BACKLOG.md](v0.2_BACKLOG.md) — 历史版本待办
