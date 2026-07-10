# OpenList Android Client

OpenList Android Client 是基于 Kotlin 和 Jetpack Compose 实现的 Android 客户端，用于通过 OpenList HTTP API 访问和管理自建 OpenList 实例。

该客户端直接调用 OpenList 接口，不以内嵌 Web 页面作为主要业务实现。应用采用单 Activity、Compose Navigation、多 Gradle 模块、Repository 分层、Room 本地存储和 WorkManager 后台任务结构。

## 项目特点

### 多实例与会话隔离

- 保存和管理多个 OpenList 实例。
- 支持 `http://`、`https://` 和部署在子路径下的实例地址。
- 保存前执行地址规范化和连接检测。
- 连接检测依次使用 `/ping` 和 `/api/public/settings`。
- 实例、会话、目录缓存、任务记录和最近访问记录按实例隔离。
- 支持在首页工作区切换当前实例。
- 会话失效后由顶层导航状态监听并返回对应实例的登录页面。

### 鉴权方式

- 账号密码登录。
- LDAP 登录。
- 2FA/OTP 验证。
- 游客访问。
- 管理员 Token 登录。
- 登录后通过 `/api/me` 获取用户身份和权限信息。
- 管理接口在进入管理模块前执行管理员身份门控。

### 文件浏览与文件操作

- 目录列表、子目录跳转和面包屑导航。
- 下拉刷新和列表排序。
- 文件详情、路径复制和链接复制。
- 最近访问路径记录。
- 目录级写入能力门控。
- 新建目录、重命名、删除、移动和复制。
- 长按进入批量选择模式。
- 批量删除、移动和复制。
- 批量操作结果聚合，分别展示成功项和失败项。
- 网络不可用时读取已缓存的目录数据，并标记缓存状态。

### 上传、下载与任务管理

- 通过 Android 系统文件选择器选择上传文件。
- 支持多文件上传。
- 上传内容使用流式请求体发送。
- 上传任务由 WorkManager 和 `UploadWorker` 执行。
- 支持上传进度查询、取消和失败重试。
- 下载任务接入 Android `DownloadManager`。
- 本地保存上传、下载和远程任务状态。
- 任务中心聚合上传任务、下载任务和 OpenList 远程任务。
- 支持离线下载任务提交。
- 支持任务分组、失败筛选、取消和记录清理。
- 已完成任务可根据目标类型跳转到目录、文件详情或预览页面。

### 文件预览与媒体播放

- 图片在应用内预览。
- 纯文本在应用内预览。
- Markdown 在应用内渲染，并处理 Markdown 内嵌图片。
- 视频和音频使用 Media3 ExoPlayer 播放。
- 支持播放、暂停、进度拖动和横屏显示。
- 支持自动发现同目录字幕候选。
- 支持从当前目录手动选择字幕文件。
- 支持 `srt`、`vtt`、`ass` 和 `ssa` 字幕扩展名。
- PDF、Office 文档和未识别格式通过外部应用打开。
- 外部打开流程包含系统应用打开、下载和浏览器打开分支。
- 媒体地址失效时执行受限次数的地址刷新和重试。

### 分享管理

- 创建文件或目录分享。
- 配置分享名称、密码、过期时间和启用状态。
- 查看、筛选和搜索当前账号创建的分享。
- 修改、启用、禁用和删除分享。
- 复制分享链接、密码和组合文本。
- 调用 Android 系统分享面板。
- 在分享详情中打开已知文件路径的预览。
- 解析粘贴或剪贴板中的 OpenList 分享链接。
- 分享链接解析不依赖当前选中的实例，会在已配置实例范围内匹配。

### 搜索

- 当前目录搜索。
- 全局索引搜索。
- 搜索历史保存和清理。
- 搜索结果接入文件详情和统一预览分发。
- 服务端未建立搜索索引时展示索引状态错误。
- 管理员会话可从索引错误入口进入索引管理页面。

### 管理接口子集

管理模块仅在管理员会话下启用，当前覆盖以下接口范围：

- 实例和运行状态概览。
- 用户列表和用户只读详情。
- 存储列表、存储详情和驱动信息。
- 存储启用、禁用和重新加载全部存储。
- OpenList 后端 7 类任务的未完成记录和已完成记录。
- 任务详情、取消、重试和删除。
- 按任务类型清理已完成记录、清理成功记录和重试失败任务。
- 搜索索引构建、增量更新、停止和清空。
- 搜索索引进度查询。
- 索引更新路径选择。
- 设置分组只读查看。
- 当前设置与默认设置对比。
- 敏感设置值掩码。
- 外部打开 OpenList Web 管理页面。

### 本地数据

Room 数据库用于保存以下数据：

- 实例配置。
- 加密后的会话数据。
- 文件列表缓存。
- 预览缓存。
- 最近访问路径。
- 搜索历史。
- 分享缓存。
- 上传任务。
- 下载任务。
- 远程任务。
- 管理接口缓存。

DataStore 用于保存应用级偏好设置。

## 技术栈

| 类别 | 实现 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose、Material 3 |
| 导航 | Navigation Compose |
| 依赖注入 | Hilt、KSP |
| 网络 | Retrofit、OkHttp |
| 序列化 | kotlinx.serialization |
| 本地数据库 | Room |
| 偏好设置 | DataStore |
| 后台任务 | WorkManager |
| 图片加载 | Coil |
| 媒体播放 | Media3 ExoPlayer |
| Markdown | Markwon |
| 异步模型 | Kotlin Coroutines、Flow |
| 测试 | JUnit、MockK、kotlinx-coroutines-test、Compose UI Test |

## 系统要求

### Android 运行环境

- Android 10 或更高版本。
- 应用最低 SDK：29。
- 应用目标 SDK：35。
- 网络访问权限。
- Android 13 及以上系统中的通知权限用于后台任务通知。

### OpenList 服务端

- 可通过 HTTP 或 HTTPS 访问的 OpenList 实例。
- 实例地址可包含部署子路径。
- 搜索功能依赖服务端搜索索引。
- 文件写入、分享、离线下载和管理操作受服务端账号权限控制。
- 管理模块依赖 OpenList 管理接口。
- 不同 OpenList 服务端版本、存储驱动和反向代理配置可能产生接口字段或媒体直链行为差异。

### 开发环境

- JDK 17。
- Android SDK 35。
- Android Studio，或可运行 Gradle Wrapper 的等价环境。

## 构建方法

### 获取源码

```bash
git clone https://github.com/SiVeci/OpenList-Android-Client.git
cd OpenList-Android-Client
```

### Debug 构建

Linux 或 macOS：

```bash
./gradlew assembleDebug
```

Windows PowerShell：

```powershell
.\gradlew.bat assembleDebug
```

Debug APK 输出目录：

```text
app/build/outputs/apk/debug/
```

### 单元测试

Linux 或 macOS：

```bash
./gradlew testDebugUnitTest
```

Windows PowerShell：

```powershell
.\gradlew.bat testDebugUnitTest
```

测试范围包括路径编解码、Base URL 规范化、错误映射、文件批量操作聚合、缓存失效、预览分发、媒体重试限制、权限门控、任务映射和 Repository 行为。

### 安装 Debug 构建

已连接 Android 设备或已启动模拟器时：

```bash
./gradlew installDebug
```

设备连接状态可通过以下命令检查：

```bash
adb devices
```

## Release 构建与签名

### 生成签名文件

```bash
keytool -genkeypair \
  -v \
  -keystore release.keystore \
  -alias openlist \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

### 配置本地签名参数

在项目根目录的 `local.properties` 中添加：

```properties
RELEASE_STORE_FILE=../release.keystore
RELEASE_STORE_PASSWORD=<store-password>
RELEASE_KEY_ALIAS=openlist
RELEASE_KEY_PASSWORD=<key-password>
```

`local.properties` 和签名文件不得提交到版本控制系统。

执行 Release 构建：

```bash
./gradlew assembleRelease
```

未提供签名配置时，Gradle 仍会生成未签名 Release APK。

### ABI 拆分

构建脚本支持以下 ABI：

| ABI | 适用范围 |
|---|---|
| `arm64-v8a` | 64 位 ARM 设备 |
| `armeabi-v7a` | 32 位 ARM 设备 |
| `x86` | 32 位 x86 设备或模拟器 |
| `x86_64` | 64 位 x86 设备或模拟器 |
| `universal` | 包含多个 ABI 的通用 APK |

示例：仅构建 `arm64-v8a` APK：

```bash
./gradlew assembleRelease \
  -Popenlist.enableAbiSplits=true \
  -Popenlist.abiIncludes=arm64-v8a \
  -Popenlist.universalApk=false
```

Windows PowerShell：

```powershell
.\gradlew.bat assembleRelease `
  "-Popenlist.enableAbiSplits=true" `
  "-Popenlist.abiIncludes=arm64-v8a" `
  "-Popenlist.universalApk=false"
```

多个 ABI 使用逗号分隔：

```bash
-Popenlist.abiIncludes=arm64-v8a,armeabi-v7a
```

## GitHub Actions 构建

仓库包含手动触发的 `.github/workflows/android-apk.yml` 工作流。该工作流仅由 `workflow_dispatch` 触发，不响应 push、pull request 或 tag 事件，也不创建 GitHub Release。

需要在仓库的 Actions Secrets 中配置：

```text
RELEASE_KEYSTORE_BASE64
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

`RELEASE_KEYSTORE_BASE64` 为 `release.keystore` 的 Base64 单行内容。

工作流参数包括：

- 是否在构建前执行单元测试。
- 是否构建 `arm64-v8a`。
- 是否构建 `armeabi-v7a`。
- 是否构建 `x86`。
- 是否构建 `x86_64`。
- 是否构建 universal APK。

工作流在上传 Artifact 前使用 Android `apksigner` 验证每个 APK 的签名。

## 使用方法

### 添加实例

1. 启动应用并进入实例管理页面。
2. 选择“添加实例”。
3. 输入以 `http://` 或 `https://` 开头的 OpenList 地址。
4. 对部署在子路径下的实例保留对应路径，例如 `https://example.com/openlist/`。
5. 执行连接测试。
6. 保存实例配置。

HTTP 地址允许保存，但传输内容不受 TLS 保护。账号凭据、Token、文件内容和接口数据可能被链路中的其他节点读取或修改。生产环境应使用由系统信任链验证的 HTTPS 证书。

### 登录

实例保存后进入登录页面，可选择以下方式：

- 账号密码。
- LDAP。
- 游客访问。
- 管理员 Token。

账号启用 2FA 时，密码认证后继续输入 OTP。登录成功后保存加密会话，并进入首页工作区。

### 主导航

底部导航包含以下入口：

- **首页**：实例切换、功能入口、任务摘要、最近访问和实例管理。
- **文件**：当前实例的目录和文件操作。
- **任务**：上传、下载和远程任务聚合。
- **我的**：缓存管理、调试日志、实例管理和管理模块入口。

文件和任务入口依赖当前实例。未选择实例或当前实例不存在有效会话时，对应功能不可用。

### 文件操作

- 点击目录进入下一级路径。
- 点击支持应用内预览的文件进入预览或播放器。
- 点击其他文件进入详情页面。
- 使用文件行菜单执行重命名、移动、复制、删除和分享。
- 长按文件或目录进入批量选择模式。
- 使用顶部操作入口创建目录或选择上传文件。
- 写入操作最终由 OpenList 服务端权限校验决定。

### 搜索

- 当前目录搜索以当前路径为范围。
- 全局搜索依赖服务端索引。
- 搜索结果根据文件类型进入目录、详情、预览或播放器。
- 索引不存在或不可用时，搜索页面显示对应错误状态。

### 任务中心

任务中心聚合以下来源：

- WorkManager 上传任务。
- Android `DownloadManager` 下载任务。
- OpenList 离线下载任务。
- OpenList 复制、移动和转存任务。

任务页面支持状态摘要、任务分组、失败项筛选、取消任务和清理任务记录。OpenList 远程任务状态通过接口轮询更新。

### 分享链接

“打开分享链接”入口用于解析 OpenList 分享 URL。解析流程会检查已配置实例并匹配链接来源。文件类型分享可进入预览或外部打开流程；目录型分享不提供完整的分享态递归浏览。

### 管理模块

管理模块仅对管理员会话开放。非管理员会话不能调用管理页面中的业务接口。

用户、存储和设置模块主要用于查看及有限操作。未实现的管理功能通过 OpenList Web 管理页面处理。

## 工程结构

```text
.
├── app/                    Android Application、依赖注入汇总、顶层导航
├── core/
│   ├── auth/               会话管理、TokenProvider、Android Keystore 加密
│   ├── common/             ApiResult、DomainError、日志、DispatcherProvider
│   ├── database/           Room、DAO、Entity、DataStore、数据库迁移
│   ├── designsystem/       Compose 主题、通用组件和布局组件
│   ├── domain/             Repository 接口
│   ├── model/              领域模型
│   └── network/            Retrofit API、DTO、拦截器、URL 与路径处理
├── data/
│   └── repository/         Repository 实现、网络与数据库协调、UploadWorker
├── feature/
│   ├── admin/              管理接口子集
│   ├── auth/               登录流程
│   ├── files/              文件列表、详情、写操作和上传入口
│   ├── instance/           实例管理
│   ├── preview/            文件预览、媒体播放和字幕
│   ├── search/             当前目录搜索、全局搜索和搜索历史
│   ├── settings/           应用设置和实例相关入口
│   ├── share/              分享列表、详情、编辑和链接解析
│   ├── task/               任务聚合和离线下载提交
│   └── upload/             上传功能模块占位；主要上传 UI 位于 files 模块
├── gradle/                 Gradle Wrapper 和版本目录
└── .github/workflows/      手动 APK 构建工作流
```

### 模块依赖方向

```text
core:common ───────────────┐
core:model ────────────────┼──> core:domain ──┐
core:database ──> core:auth ──> core:network ─┼──> data:repository
core:designsystem ────────────────────────────┘

core:common / core:model / core:domain / core:designsystem
                         └────────────────────────> feature:*

core:network ────────────────────────────────> 部分 feature 模块
core:database ───────────────────────────────> feature:settings

data:repository + feature:* + core:* ───────> app
```

`core:domain` 仅定义 Repository 契约并依赖 `core:common` 与 `core:model`。`data:repository` 汇总网络、数据库、鉴权和领域接口依赖。功能模块主要依赖领域接口和设计系统，个别模块直接使用网络路径工具或数据库偏好设置。`app` 模块负责组合功能模块、Hilt 绑定和导航图，不承载主要数据访问逻辑。依赖关系以各模块的 `build.gradle.kts` 为准。

### 主要组件

| 组件 | 职责 |
|---|---|
| `OpenListApi` | OpenList REST 接口声明 |
| `OpenListClientFactory` | 按实例创建 Retrofit/OkHttp 客户端 |
| `AuthInterceptor` | 为实例请求附加授权信息 |
| `BaseUrlNormalizer` | 规范化实例基础地址 |
| `OpenListPathCodec` | 处理逻辑路径和 URL 路径编码 |
| `CryptoManager` | 使用 Android Keystore 加密和解密 Token |
| `SessionManager` | 管理会话持久化和失效处理 |
| `OpenListDatabase` | Room 数据库入口 |
| `UploadWorker` | WorkManager 后台上传任务 |
| `PreviewRepository` | 文件预览目标解析和内容加载 |
| `MediaRepository` | 媒体播放地址和刷新逻辑 |
| `TaskAggregationRepository` | 聚合本地任务和服务端任务 |
| `OpenListNavHost` | 顶层 Compose 导航图 |

### 数据流

```text
Compose Screen
    ↓
ViewModel
    ↓
core:domain Repository interface
    ↓
data:repository implementation
    ├── core:network
    ├── core:database
    ├── core:auth
    └── Android system service
```

ViewModel 通过协程和 Flow 暴露界面状态。Repository 实现负责网络请求、本地缓存、错误映射和系统服务调用。

## 网络与安全配置

### Token 存储

- Token 使用 Android Keystore 中生成的 AES-256-GCM 密钥加密。
- 持久化内容为 Base64 编码的 `IV + ciphertext + authentication tag`。
- 加密密钥不写入 Room、DataStore 或普通文件。
- Android 应用备份已通过 `android:allowBackup="false"` 禁用。

### HTTPS 与 HTTP

- HTTPS 使用 Android 系统信任锚。
- 不注入自定义 CA。
- 不绕过主机名验证或证书链验证。
- HTTP 明文连接处于允许状态，用于连接显式配置的 HTTP 实例。
- 应用层对 HTTP 实例显示明文传输风险。
- 当前配置不处理自签名证书导入。

### 请求日志

调试日志功能用于问题定位。日志输出应经过敏感字段脱敏，但调试日志仍可能包含路径、主机、状态码和请求时序等运行信息。包含生产数据的环境中应限制日志采集、传输和保留范围。

### 签名材料

以下内容不应进入版本控制、构建日志或公开 Artifact：

- `release.keystore`。
- Keystore Base64 内容。
- `local.properties` 中的签名密码。
- GitHub Actions 签名 Secrets。
- OpenList 管理员 Token。

## 当前功能边界

以下能力未在原生客户端中实现，或仅提供外部页面处理：

- OpenList Web 管理台的全部用户 CRUD。
- OpenList Web 管理台的全部存储 CRUD 和动态驱动配置。
- 设置写入、删除、重置 Token 等完整设置管理。
- Meta、消息、扫描等 Web 管理模块。
- 分享态多级目录递归浏览。
- 分享态上传。
- PDF 内置渲染。
- Office 文档内置渲染。
- SSO 原生认证。
- WebAuthn 原生认证。
- 自签名证书导入或证书校验绕过。
- 分片上传。
- 断点续传上传。
- 断点续传下载。
- 目录递归上传。
- 文件网格视图。
- 二维码生成和扫描。
- 持续运行的全局后台任务轮询服务。

媒体容器和编码格式的实际支持范围由 Android Media3、设备解码器、OpenList 存储驱动和媒体直链响应共同决定。不支持的媒体文件进入外部打开或下载流程。

## 故障定位

### 实例连接失败

检查以下项目：

- 实例地址是否包含 `http://` 或 `https://`。
- 反向代理是否保留 OpenList 部署子路径。
- `/ping` 或 `/api/public/settings` 是否可访问。
- HTTPS 证书是否在 Android 系统信任链中有效。
- OpenList 是否限制来源地址、请求头或代理路径。

### 登录后接口返回 401

- Token 已失效或被服务端撤销。
- 实例地址对应的服务端发生变更。
- 反向代理未转发 `Authorization` 请求头。
- 管理员 Token 格式不符合服务端要求。

会话失效后应用会删除本地会话并返回登录页面。

### 搜索不可用

全局搜索要求 OpenList 服务端已建立索引。管理员会话可在管理模块中查询索引进度并执行构建或更新。

### 上传任务不执行

检查以下项目：

- Android 后台限制和省电策略。
- 通知权限和前台服务权限。
- WorkManager 状态。
- 上传目标目录的服务端写入权限。
- 文件选择器返回 URI 的读取权限。
- 反向代理请求体大小和超时限制。

### 媒体播放失败

检查以下项目：

- `fs/get` 返回的 `raw_url` 或签名地址是否可访问。
- 存储驱动是否支持 Range 请求。
- 反向代理是否转发 Range 相关请求头。
- 设备解码器是否支持目标编码格式。
- 签名 URL 是否已失效。

## 相关文档

- [已知问题与功能限制](KNOWN_ISSUES.md)
- [MIT License](LICENSE)

## 免责声明

该仓库为独立实现的 OpenList Android 客户端，与 OpenList 项目维护组织不存在隶属、授权、担保或服务支持关系。“OpenList”名称及其相关标识的权利归对应权利主体所有。

该软件通过 OpenList API 执行文件创建、上传、重命名、移动、复制、删除、分享、任务管理、存储启停、索引管理等操作。上述操作可能修改或删除服务端数据。执行写入或管理操作前，应根据实际部署环境建立独立备份、权限隔离和恢复方案。

该软件不提供 OpenList 服务端、存储驱动、反向代理、TLS 证书、账号权限、第三方播放器或外部文档应用的可用性保证。服务端版本差异、接口变更、驱动行为、网络环境、Android 系统限制和设备解码能力均可能影响运行结果。

HTTP 明文连接无法保证凭据、Token、文件内容和接口数据的机密性与完整性。公网或不受信任网络中的实例应使用有效 HTTPS 配置。自签名证书、私有 CA 和证书固定不属于当前实现范围。

用户凭据、管理员 Token、签名文件、签名密码、服务端地址、日志和下载文件的保管责任由部署和使用环境承担。因错误配置、权限设置、误操作、接口兼容性、数据丢失、服务中断或第三方组件行为导致的损失，不由该软件提供额外责任承诺。

软件依据 [MIT License](LICENSE) 以“按原样”方式提供，不包含适销性、特定用途适用性和非侵权性的明示或默示保证。法律责任范围以许可证正文为准。
