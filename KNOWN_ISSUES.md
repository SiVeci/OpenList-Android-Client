# Known Issues (v0.5.0)

v0.1~v0.4 的已知限制见文末历史章节。以下是 v0.5 范围内已知的限制。

## 本轮环境状态（比 v0.4 更强一档：本轮实际跑过构建）

v0.4 全程禁止运行任何构建/测试命令，一次编译都没有做过。**v0.5 本轮环境恢复了可运行 Gradle 的能力**：S0 起点即执行 `JAVA_HOME="C:/Program Files/Java/jdk-20" ./gradlew assembleDebug testDebugUnitTest` 确认 BUILD SUCCESSFUL，此后每个 Sprint 出口均以 `compileDebugKotlin`/`testDebugUnitTest` 实测通过为准（非纯静态审查），S8 版本号提升后（versionCode 4→5、versionName 0.4.0→0.5.0）重新跑过一次完整 `assembleDebug`/`testDebugUnitTest` 并确认 BUILD SUCCESSFUL。**但本轮仍然没有 Android 真机/模拟器**（`adb devices` 为空），因此"编译通过"与"真机运行正确"之间的 gap 仍然存在，性质与 v0.3 的验证口径相同（v0.4 是唯一连编译都没跑过的一轮）。

## 未在真机上验证的项目（发布前待验清单，详见 `v0.5_ACCEPTANCE_REPORT.md`）

- **Room 8→9 真实升级路径**：v0.4.0 APK 已有数据 → v0.5 APK 打开后数据保留且 `admin_cache` 新表可用，仍需真机验证（这一步本轮环境无法做，无 Android 设备/模拟器）。**schema 结构本身已用 KSP 核对并修复**：S8 验收阶段执行 `:core:database:kspDebugKotlin` 强制重新生成 `9.json`，与手工推导的已提交版本逐字段比对**结果完全一致**，仅 `identityHash` 从占位符变为真实值；已将真实值 **`edad8842c1f0268bcb41ace48375b2d7`** 写回并提交至仓库的 `9.json`，不再是占位符。`8.json`（v0.4 遗留）的 `identityHash` 占位符 `PLACEHOLDER_UNVERIFIED_8_00000000000000` **无法在本轮修复**：KSP 只导出「当前」数据库版本（9）的 schema，要重新核对 8 版本需要临时把 `OpenListDatabase` 版本降回 8 重新编译，这个改动侵入性太大、风险不匹配收益，故保留原占位符，留待下次真正修改该版本 schema（不会发生，8 已是历史版本）或有更完整工具链时处理，不阻塞发布。剩下的唯一 gap 是真机上的实际迁移执行（表结构创建、旧数据保留），非 schema 描述本身的正确性。
- **`/@manage` 路径的真实可达性与页面渲染**：V-508 已从源码（`server/static/static.go:220`）确认路径本身存在，但未在真实部署的 OpenList 实例上实际打开验证。
- **外部浏览器 Intent 的真机弹出行为**：分享面板 vs 直接跳转，取决于设备默认浏览器配置，未真机验证。
- **`AdminUserSummary.otpEnabled` 字段仍是 provisional**：`openlist-ref` 源码未找到可确认的 2FA 状态字段，S1~S7 全程保守显示为"未知"而非猜测 `false`，需要真实管理员账号（已开启/未开启 2FA 两种）核对后端实际返回。
- **索引 `updateIndex` 的 `maxDepth=-1` 默认值**：是本轮基于 `internal/fs/walk.go` 递归终止条件（`depth==0` 停止）推导的"尽力而为"默认值，非确认的后端契约；真机验证后如发现后端对负数 `maxDepth` 有钳制或报错行为，需要调整。
- **设置分组的 12 个 Group 枚举中文命名**是否与真实后端返回的常见配置项分布相符（例如"其他"兜底分支覆盖率），未用真实实例核对。
- **深色模式**：管理台新组件（`AdminScaffold`/`AdminTabRow`/`IndexProgressPanel`/`AdminUserDetailSheet`/`AdminStorageDetailSheet` 等）复用既有 designsystem token，但未做系统性人工暗色走查。
- **存储 `status` 字段的健康判定阈值**（"work"字符串、大小写不敏感）为 S3 保守推导，真机验证后如与实际驱动行为不符需要校准（详见 `v0.5_IMPLEMENTATION_LOG.md` S3 决策记录第 4 条）。
- 沿用 v0.4 已知的：Media3/ExoPlayer 实际播放效果、Markdown 渲染排版、图片预览/Coil 缓存等——均未在本轮重新验证（v0.5 未改动这些既有功能）。

建议在自建 OpenList 实例 + 真机/模拟器上完整走一遍 `v0.5_EXECUTION_PLAN.md` §14 验收清单再发布，重点覆盖上述"真机项"与 Room 8→9 真实升级路径。

## 一般性观察（非 v0.5 引入的缺口，项目级别）

- **项目全局没有配置 `HttpLoggingInterceptor`**：S8 安全审计确认这不是 v0.5 引入的新问题，而是自 v0.1 起就没有的组件（沿用既有的最小化日志面），一并记录供后续版本评估是否需要引入（引入时需同步设计 admin 接口响应体的脱敏策略）。

## v1.0 Parity Matrix 预留材料（PRD §18.1，6 项，供 v1.0 建立完整 Parity Matrix 时直接引用）

1. **用户管理**：v0.5 原生只读（列表+详情），Web 管理台支持完整 CRUD（创建/编辑/删除用户、重置密码、取消 2FA、SSH Key 管理）。
2. **存储管理**：v0.5 原生只做查看、启用、禁用、重新加载全部，Web 管理台支持完整创建/编辑/删除存储与动态驱动配置表单。
3. **设置管理**：v0.5 原生只读（含默认设置对比），Web 管理台支持保存、删除设置项、重置 API Token、各类下载工具账号配置表单。
4. **任务管理**：v0.5 原生覆盖 7 类任务的常用动作（取消/重试/删除记录），不做批量清理接口（`cancel_some`/`delete_some`/`retry_some`/`clear_done`/`clear_succeeded`/`retry_failed`）、完整日志详情、速度曲线。
5. **索引管理**：v0.5 原生覆盖构建/更新/停止/清空常用动作，但路径更新细节（如 `maxDepth` 语义、多路径选择器）可能与 Web 端实际行为存在差异，`maxDepth=-1` 默认值为客户端最佳努力推导，非确认契约。
6. **Web 兜底**：不注入原生 App 的 Token/Cookie（原生会话与 Web 会话完全独立），打开 `/@manage` 后可能需要用户在 Web 端重新登录。

---

## v0.4 待真机核对的后端行为推测（V-401 ~ V-410，历史遗留，v0.5 未重新核实）

以下验证项详见 `v0.4_IMPLEMENTATION_LOG.md` S0 章节，均已尽力对照 `openlist-ref/` 后端参考源码核实到 handler/路由层，但部分内部实现（签名生成/校验、反代 Range 转发细节）不在本地精简 checkout 范围内，标注为 provisional：

- **V-401** `raw_url` 签名有效期：未知真实过期时间，客户端策略是"每次进入预览/播放都重新 `fs/get`，不复用旧签名 URL"，不依赖对过期时间的预测。
- **V-402** `/d/` vs `/p/` 优先级：源码确认 `raw_url` 优先，为空时退回签名 `/d/` URL 构造；两者均不需要 `Authorization` 头（靠 URL 里的 `sign` 参数），`MediaSource.headersRequired` 因此固定为 `false`，配套的 host 限域 Header 注入机制（`buildScopedHttpHeaders`）已实现但当前不会被触发。
- **V-403** Range 请求支持：`/d/` 是 302 重定向（Range 支持取决于目标驱动直链本身）；`/p/` 反代路径下 Range 支持取决于存储的 `ProxyRange` 配置（服务端内部逻辑不在本地 checkout 范围）；客户端不假设 Range 一定可用，播放失败统一走"刷新→外部打开/下载"兜底链。
- **V-404** 鉴权失败响应形态：源码确认统一返回 HTTP 状态码+ JSON body，非重定向或 200+错误体。
- **V-405** 分享路径预览：`Share.files[]`/`paths` 是创建者原始文件系统路径，可直接走普通 `fs/get`（非分享态鉴权路径），已在 v0.4 分享详情文件列表接线中按此实现。
- **V-406** 字幕候选可靠性：`fs/get` 的 `related` 字段（后端自带同前缀相关文件列表）源码确认可靠，客户端只做扩展名过滤，未额外调用 `fs/list`。
- **V-407** 路径编码一致性：逻辑路径不编码、下载/代理 URL 逐段编码，两端约定一致，沿用 `OpenListPathCodec` 无需新增分支。
- **V-408** Media3 容器/编码支持面（mkv/flv/rmvb/ape/wma 等）：客户端能力问题，不依赖后端，实际支持范围需真机测试不同格式文件后才能确认，不支持时走 `MediaUnsupported` 兜底。
- **V-409** Markdown 相对路径图片：本版本未实现内嵌图片渲染（见"功能范围"章节），此项暂不适用。
- **V-410** 签名/Token 失效表现：源码确认 Token 失效统一 401；签名验证中间件具体实现不在本地 checkout 范围，客户端按"任何 4xx 播放/预览错误触发一次刷新，上限 2 次"处理，不区分具体失效原因。

## v0.3 待真机核对的后端字段推测（V-01 ~ V-07，历史遗留，v0.4 未重新核实）

以下字段/行为在参考后端源码（`openlist-ref/`）中不可得或存在保留字段以外的不确定性，代码中已标注为"provisional"，采用了保守默认值（缺省不发送/字段带默认值），且解码使用 `ignoreUnknownKeys`/`coerceInputValues` 保证即使猜错也不会崩溃：

- **V-01** Web 分享页 URL 格式：当前实现为 `{baseUrl}/@s/{sid}`（比照后端 `/sd/:sid` 直链家族推测），未经真实 Web 前端核对。
- **V-02** `tache.State` 数值枚举：`TaskStateMapper` 按推测值映射（0=Pending…7=Failed，8/9=Retry），未知值一律映射为 `UNKNOWN` 而非崩溃或误判。
- **V-03** `SearchNode`/`SearchReq` 确切 JSON 字段名与 `scope` 语义：`SearchNodeResp` 字段为最佳猜测，猜错的字段会静默取默认值而非报错。
- **V-04** 未建索引时 `/api/fs/search` 的错误行为：`SearchRepositoryImpl` 用错误信息里的 `index`/"未建立索引"关键字启发式映射为 `DomainError.SearchNotAvailable`，未核对真实错误文案。
- **V-05** `delete_policy` 枚举与缺省值：客户端默认不传（空字符串），交由后端使用自身默认值。
- **V-06** `expires` 序列化格式：使用 `OffsetDateTime`/UTC 生成 RFC3339 字符串，未核对后端时区处理细节。
- **V-07** 普通用户 `share/list` 是否只返回自己的分享：已从后端源码确认（`op.GetSharingsByCreatorId`），未做真机复核。

---

## v0.4.0（历史）

- 本轮（v0.4）开发环境曾禁止运行任何构建/测试命令，是历史上唯一一次连编译都没跑过的版本；v0.5 已重新执行完整构建并确认通过，此项 caveat 到 v0.5 为止解除。
- V-401~V-410（`raw_url` 签名有效期、`/d/`/`/p/` 优先级、Range 支持、鉴权失败响应形态、分享路径预览、字幕候选可靠性、路径编码一致性、Media3 容器支持面、Markdown 内嵌图片不适用、签名/Token 失效表现）均已对照后端源码核实到 handler/路由层，部分内部实现细节标记 provisional，v0.5 未重新核实（与预览/播放器功能无关联）。
- Media3/ExoPlayer 实际播放效果、Markdown 渲染排版、图片预览/Coil 缓存、v0.4 全部新页面的暗色模式——均未真机验证，v0.5 未新增相关验证（预览模块本身在 v0.5 未改动）。
- Room 7→8 真实升级路径与 `8.json` 手工 `identityHash` 占位值——v0.5 引入 Room 8→9 后，此 caveat 与新的 9.json 占位值一并延续，见本文件顶部"未在真机上验证的项目"。
- 预览相关权限门控（游客不隐藏预览入口，403 由后端裁决）与 v0.5 管理台门控策略（游客/非管理员在管理台内容层面直接拦截而非仅提示）不同——两者是不同层级的功能，不构成矛盾。
- Markdown 内嵌图片渲染明确裁剪，仍未实现；字幕仅支持 `srt`/`vtt`/`ass`/`ssa` 四种扩展名自动发现。
- `Parity_Matrix.md` 当时仍未创建，v0.4 PRD §19.2 的 8 项差异记录见 `v0.4_ACCEPTANCE_REPORT.md`；v0.5 PRD §18.1 的 6 项差异记录见本文件顶部"v1.0 Parity Matrix 预留材料"。
- v0.4 新增 4 个 Repository 的单元测试当时只写未运行，v0.5 起点（S0）首次实际跑通并确认全部通过。

## v0.3.0（历史）

- 分享访问统计/审计、分享态完整目录浏览与上传、分享二维码未实现。
- App 内打开 OpenList 分享 URL 降级为仅复制链接+系统分享（v0.4 起支持从分享详情预览已知文件路径，仍不支持任意分享 URL 入站解析）。
- 本地全文索引、离线搜索、结果按类型筛选/排序、加载更多分页均未实现。
- 任务日志详情页、速度曲线、任务列表按状态筛选未实现。
- 后台常驻轮询服务未实现（轮询仅在任务中心页面可见时进行）。
- BT 文件选择、Magnet 高级解析、多 URL 批量离线提交、下载限速、断点续传均未实现。
- 分享密码复制/完整文案复制以外的分享文案自定义、剪贴板 URL 自动识别、上传重试（`UploadRepository.retryUpload`）均未实现。
- 管理台任务视图（`TaskRepository` 目前只轮询 offline_download/offline_download_transfer/copy/move 四类）未扩展。
- v0.3 新增的 5 个 Repository、`TaskStateMapper`、聚合排序当时未补充单元测试（v0.4 未回头补）。
- Room Migration 6→7 当时未做 `MigrationTestHelper` 插桩测试。

## v0.2.0（历史）

- 写入口的显示逻辑是"游客隐藏、已登录用户乐观展示"，不依据 `Session.permission` 位掩码做隐藏判断——该字段是 v0.2 才新增（Migration 4→5），迁移后老会话的位掩码读数为 0 直到下次 `/api/me` 刷新，若据此隐藏会导致真正有权限的用户在升级后短暂看不到写入口。真正的权限判定以后端 403 响应为准（响应式提示"无权限"）。
- 目录级 `FsListResp.write` 字段未接入门控逻辑。
- 不支持断点续传、分片上传、秒传、多线程上传、目录递归上传；上传失败无一键"重新上传"入口；前台服务通知未实现。
- 批量删除/移动/复制在客户端逐项串行调用后端；不支持批量重命名。
- 不支持 2FA、LDAP、SSO、WebAuthn 登录 UI；不支持自签证书。
- 无搜索、分享、完整任务中心、离线下载、预览、归档浏览、管理台（v0.3 已补齐分享/搜索/任务中心/离线下载，预览与管理台仍留待 v0.4/v0.5）。
- Room 数据库 4→5/5→6 迁移已与 Room 自身导出的 schema JSON 逐字段核对，但当时未在真机上验证真实升级路径。

## v0.1.0（历史）

- 下载任务在应用内只记录到"已入队"，真正的下载完成/失败由系统 `DownloadManager` 通知栏原生承接（v0.3 已通过 P9 状态回读补齐应用内感知）。
- 未提供应用内下载历史/任务列表页面（v0.3 已补齐任务中心）。
- 会话有效性无后台定时刷新；不支持 2FA/LDAP/SSO/WebAuthn/自签证书。
- 网络请求统一 30 秒超时，无重试/指数退避策略（v0.2 的上传 client 例外）。
