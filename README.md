# OpenList Android Client

原生 Android 客户端，用于连接自建的 [OpenList](https://github.com/OpenListTeam/OpenList) 实例：多实例管理、登录/游客/管理员 Token 三种鉴权方式、目录浏览、文件详情、下载。

当前版本：**v0.1（技术验证 / 最小可用闭环版）**。范围与后续规划见 [v0.1_PRD.md](v0.1_PRD.md)、[v0.1_EXECUTION_PLAN.md](v0.1_EXECUTION_PLAN.md)、[Full_PRD.md](Full_PRD.md)。

## 技术栈

Kotlin · Jetpack Compose (Material 3) · Hilt · Retrofit + OkHttp + kotlinx.serialization · Room · DataStore · Navigation Compose

## 工程结构

单 Gradle 模块 `app`，内部按 `core/*`（通用基础设施）、`data/repository`（Repository 实现）、`feature/*`（各功能页面）分包，边界清晰、便于后续按包拆分为多模块。

```
core/common      ApiResult / DomainError / SafeLogger / DispatcherProvider
core/model       领域模型（Instance / Session / FileNode / FileDetail）
core/database    Room（Instance/Session/FileCache/DownloadTask）+ DataStore
core/auth        CryptoManager（Keystore AES-GCM）/ SessionManager
core/network     OpenListApi / OpenListClientFactory / 拦截器 / 路径与 URL 规范化
core/domain      Repository 接口
data/repository  Repository 实现
feature/*        instance / auth / files / settings（含预留空包：upload/preview/share/task/admin/webfallback）
```

## 构建

```bash
./gradlew :app:assembleDebug     # Debug APK
./gradlew testDebugUnitTest      # 本地单元测试（路径编解码 / Base URL 规范化）
./gradlew :app:assembleRelease   # Release APK（见下方签名配置）
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

## 快速上手（v0.1 功能范围）

1. 打开 App，点击「添加实例」，输入 OpenList 实例地址（`http(s)://` 开头，支持部署在子路径）。
2. 「测试连接」确认可达（依次尝试 `/ping`、`/api/public/settings`）。
3. 保存后进入登录页：账号密码登录 / 游客访问 / 管理员 Token 登录，三选一。
4. 登录成功后进入文件列表：目录浏览、子目录跳转、面包屑、下拉刷新；断网时展示本地缓存并提示。
5. 点击文件进入详情页：查看基础信息、复制路径/链接、下载（走系统 `DownloadManager`，落地系统 Downloads 目录，完成状态由系统通知栏承接）。
6. 「设置」页可清理本地缓存、开关调试日志（脱敏后写入 logcat，不含 Token）。

v0.1 **不包含**：上传、分享、搜索、预览（图片/视频/文本/Markdown）、管理台、离线下载、批量操作。这些均已在架构层面预留（见 `feature/*` 下的预留空包与 `core/network/OpenListApi.kt` 中的预留接口注释），具体规划见 [v0.1_EXECUTION_PLAN.md](v0.1_EXECUTION_PLAN.md) §17。

## 其他文档

- [KNOWN_ISSUES.md](KNOWN_ISSUES.md) — 已知问题与限制
- [RELEASE_NOTES.md](RELEASE_NOTES.md) — 版本发布说明
- [v0.2_BACKLOG.md](v0.2_BACKLOG.md) — 下一版本待办
