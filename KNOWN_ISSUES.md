# Known Issues (v0.4.0)

v0.1/v0.2/v0.3 的已知限制见文末历史章节。以下是 v0.4 范围内已知的限制。

## 本轮开发环境约束（比 v0.3 更严格）

v0.3 当时至少还能跑 `compileDebugKotlin`/`assembleDebug`/`testDebugUnitTest` 做编译期验证。**v0.4 本轮开发环境额外禁止运行任何构建/测试命令**（资源受限的云端环境），也没有连接的 Android 设备/模拟器。因此 v0.4 的正确性验证**完全依赖静态代码审查**（类型/字段名/import 逐一核对、依赖方向核查、Room schema 手工核对、Media3 API 对照官方 GitHub 源码核实），**没有做过任何一次编译**，单元测试也只写未运行。这是 v0.4 验收口径的核心 caveat，比 v0.3 的"编译通过但未真机验证"更弱一档。

## 未在真机/构建环境上验证的项目

- **完全没有做过一次编译**：`:feature:preview` 新模块、Room 7→8 迁移、四个新 Repository、Media3/Markwon/Coil 集成，均未经 `compileDebugKotlin`/`kspDebugKotlin`（Hilt DI 图）验证，理论上仍可能存在本轮静态审查未发现的类型错误或 import 遗漏。
- **Media3/ExoPlayer 实际播放效果**：视频/音频播放、`PlayerView` 默认控制条渲染、横屏沉浸式切换、字幕轨道实际渲染、音频进度条视觉效果——全部未在真机/模拟器上验证，仅代码走查+对照 [androidx/media](https://github.com/androidx/media) 官方 `release`/`1.4.1` tag 源码核实 API 签名（`PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS`、`HttpDataSource.InvalidResponseCodeException`、`MimeTypes` 字幕常量、`@UnstableApi` 标注面等）。
- **Markdown 渲染实际效果**（Markwon 4.6.2）：HTML 忽略行为、外链点击安全性已对照 Markwon 源码核实结论，但实际渲染排版、暗色模式适配未真机验证。
- **图片预览/Coil 缓存**：加载失败态、缓存命中效果未真机验证。
- **v0.4 全部新页面**（预览宿主页、图片/文本/Markdown 预览页、视频/音频播放器页、字幕选择 Sheet、外部打开 Sheet）的实际渲染、交互、暗色模式——DESIGN 验收②未做人工走查。
- **Room 7→8 真实升级路径**（v0.3.0 APK 已有数据 → v0.4 APK 打开后数据保留且 `preview_cache` 新表可用）；`8.json` schema 导出文件的 `identityHash` 是手工填的占位值，需要在可构建环境下重新用 KSP 实际导出核对（详见 `v0.4_IMPLEMENTATION_LOG.md` S1 章节）。
- 沿用 v0.3 已知的：分享全闭环真实往返、搜索实际行为、任务中心轮询真机耗电、下载状态回读真机表现——均未在本轮重新验证（v0.4 未改动这些既有功能的核心逻辑，只新增了预览分发接线）。

建议在自建 OpenList 实例 + 可构建环境 + 真机/模拟器上完整走一遍 `v0.4_EXECUTION_PLAN.md` §14 验收清单再发布，**第一步应该是先跑一次完整构建**（本轮从未做过）。

## v0.4 待真机核对的后端行为推测（V-401 ~ V-410）

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

## v0.4 权限门控

- 预览属读操作，游客态**不隐藏**预览/播放/外部打开入口（PRD 明确要求），403 由后端返回时展示"无权限"提示；预览相关 UI 代码已核查，无任何 `canWrite`/`isGuest`/权限位判断分支来隐藏预览入口。
- 与 v0.2/v0.3 一致：写操作入口仍是"游客隐藏、已登录用户乐观展示，真实权限以后端 403 为准"，v0.4 未改动此策略。

## v0.4 功能范围（刻意裁剪，见 v0.4_PRD.md §5.3 "不做"）

- PDF 内置预览、Office 内置预览（均只做外部打开兜底）。
- 代码高亮、歌词支持、图片画廊模式、README 自动渲染增强。
- 归档文件内部浏览、Torrent 预览/种子内容解析。
- 完整分享态目录浏览、任意分享 URL 入站解析（分享详情仅支持已知路径预览，不支持解析外部分享链接）。
- 视频转码；字幕在线搜索/下载/时间轴编辑（仅做同目录自动发现+手动选择+开关）。
- 自研下载器、断点续传、暂停继续下载；管理台；v1.0 原子级对齐完整验收。
- **Markdown 内嵌图片渲染**：本版本明确裁剪（S3 决策，需要额外的 `markwon-image` 依赖+自研 Coil `AsyncDrawableLoader`，评估后判断超出本版本合理工作量），Markdown 正文本身正常渲染，仅图片不显示，不影响阅读。
- 图片/文本/Markdown/视频/音频预览失败态的"下载"按钮已接入真实下载（S4 起），但未做"外部打开"以外的自动重试策略。

## v0.4 任务中心/字幕相关限制

- 字幕仅支持 `srt`/`vtt`/`ass`/`ssa` 四种扩展名的自动发现（基于 `fs/get` 的 `related` 字段做客户端扩展名过滤）；手动选择不限制扩展名（用户可选任意文件尝试）。
- `ass`/`ssa` 字幕在 Media3 上的实际渲染效果依赖 ExoPlayer 自身对 SSA/ASS 格式的支持程度，未真机验证。
- 任务中心完成任务点击时新增一次 `fs/get` 判断文件/目录（此前一律当目录处理），带来了额外一次网络请求延迟，未做加载态提示（走查代码确认无阻断性问题，但用户点击到实际跳转之间会有短暂网络等待，无真机验证实际耗时体感）。

## 任务中心（v0.3 遗留，v0.4 未处理）

- 本地下载任务的取消操作仍不支持（`TaskAggregationRepository.cancelTask` 对 `LOCAL_DOWNLOAD` 直接返回失败提示）。
- 任务中心"有运行中任务"徽标仍只反映本地上传任务状态，未聚合下载/远程任务。

## 测试覆盖（v0.4）

- v0.4 新增的 4 个 Repository（`PreviewRepository`/`MediaRepository`/`SubtitleRepository`/`ExternalOpenRepository`）、`PreviewKindResolver`、流式读取/截断/BOM 解析、缓存失效、Header 限域、错误重试上限均已补充单元测试（详见各 Sprint 的 `v0.4_IMPLEMENTATION_LOG.md` 记录），但**全部只写未运行**（本轮环境禁止执行任何测试命令），下次进入可构建环境时需要先跑一遍 `testDebugUnitTest` 确认全部通过。
- Media3/Compose UI 集成层面（ExoPlayer/PlayerView/字幕渲染/横竖屏切换）未做也无法做 instrumented test。
- Room Migration 7→8 未做 `MigrationTestHelper` 插桩测试，且这次连 KSP schema 导出本身都未运行过（`8.json` 手工推导，见上文）。

## 工程/发布（v0.4）

- `Parity_Matrix.md` 仍未创建；v0.4 PRD §19.2 要求的 8 项 Web 端行为差异记录见本文件与 `v0.4_ACCEPTANCE_REPORT.md`。
- 深色模式色板复用既有 token，v0.4 新组件（`PreviewScaffold`/`ExternalOpenSheet`/`SubtitleSelector`/播放器控制条等）未做系统性人工暗色走查。

---

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
