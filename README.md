# OpenList Android Client

原生 Android 客户端，用于连接自建的 [OpenList](https://github.com/OpenListTeam/OpenList) 实例：多实例管理、登录/游客/管理员 Token 三种鉴权方式、目录浏览、文件详情、下载、文件写操作（新建/重命名/删除/移动/复制）、批量选择、上传。

当前版本：**v0.2（上传与文件操作闭环版）**。范围与后续规划见 [v0.2_PRD.md](v0.2_PRD.md)、[v0.2_EXECUTION_PLAN.md](v0.2_EXECUTION_PLAN.md)、[Full_PRD.md](Full_PRD.md)。v0.1 范围见 [v0.1_PRD.md](v0.1_PRD.md)、[v0.1_EXECUTION_PLAN.md](v0.1_EXECUTION_PLAN.md)。

## 技术栈

Kotlin · Jetpack Compose (Material 3) · Hilt · Retrofit + OkHttp + kotlinx.serialization · Room · DataStore · WorkManager · Navigation Compose

## 工程结构

多 Gradle 模块，依赖方向单向无环：`core:{common,model,designsystem}` 为叶子模块 → `core:{database,auth}` → `core:network` → `core:domain` → `data:repository`（汇聚全部 core 依赖）→ `feature:*` → `app`（DI 汇聚 + 导航）。

```
core/common          ApiResult / DomainError / SafeLogger / DispatcherProvider
core/model           领域模型（Instance / Session / FileNode / FileDetail / UploadTask / BatchOperationResult）
core/designsystem    主题 + 交互组件（Dialog/Sheet/BatchSelectionTopBar/UploadProgressItem 等）
core/database        Room（Instance/Session/FileCache/DownloadTask/UploadTask）+ DataStore + Migrations
core/auth            CryptoManager（Keystore AES-GCM）/ SessionManager / TokenProvider
core/network         OpenListApi / OpenListClientFactory / 拦截器 / 路径与 URL 规范化 / 上传专用 OkHttpClient
core/domain          Repository 接口
data/repository      Repository 实现 + UploadWorker（WorkManager CoroutineWorker）
feature/instance     实例管理
feature/auth         登录
feature/files        文件浏览 / 详情 / 写操作 / 批量选择 / 上传入口与进度面板
feature/settings     设置
feature/upload       预留（v0.2 上传 UI 目前内嵌在 feature/files，此模块留给 v0.3 独立任务中心）
```

reserved 特性（preview/share/task/admin/webfallback）仍以 `:app` 内占位包形式保留，未升级为模块。

## 构建

```bash
./gradlew assembleDebug          # 全部模块 Debug 构建
./gradlew testDebugUnitTest      # 本地单元测试（路径编解码 / Base URL 规范化 / 批量操作聚合）
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

## 快速上手（v0.2 功能范围）

1. 打开 App，点击「添加实例」，输入 OpenList 实例地址（`http(s)://` 开头，支持部署在子路径）。
2. 「测试连接」确认可达（依次尝试 `/ping`、`/api/public/settings`）。
3. 保存后进入登录页：账号密码登录 / 游客访问 / 管理员 Token 登录，三选一。
4. 登录成功后进入文件列表：目录浏览、子目录跳转、面包屑、下拉刷新；断网时展示本地缓存并提示。
5. 已登录用户（非游客）可见写操作入口：
   - 顶部「新建目录」「上传」按钮；上传经系统文件选择器多选，后台流式上传并可在顶部「上传进度」入口查看/取消。
   - 每行「更多」菜单：重命名、移动、复制（弹出目标目录选择器）、删除（危险操作二次确认）。
   - 长按任意行进入批量选择模式，可全选/取消全选，批量删除/移动/复制；部分失败时可点击提示上的「查看」看每一项失败原因。
6. 点击文件进入详情页：查看基础信息、复制路径/链接、下载（走系统 `DownloadManager`，落地系统 Downloads 目录，完成状态由系统通知栏承接）。
7. 「设置」页可清理本地缓存、开关调试日志（脱敏后写入 logcat，不含 Token）。

v0.2 **不包含**：分享、搜索、完整任务中心、离线下载、预览（图片/视频/文本/Markdown/PDF/Office）、管理台、断点续传/分片上传。这些均已在架构层面预留，具体规划见 [v0.2_EXECUTION_PLAN.md](v0.2_EXECUTION_PLAN.md) §27，已知限制见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)。

## 其他文档

- [KNOWN_ISSUES.md](KNOWN_ISSUES.md) — 已知问题与限制
- [RELEASE_NOTES.md](RELEASE_NOTES.md) — 版本发布说明
- [v0.2_BACKLOG.md](v0.2_BACKLOG.md) — 下一版本待办
