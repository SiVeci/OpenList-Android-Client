# Known Issues (v0.3.0)

v0.1/v0.2 的已知限制见文末历史章节。以下是 v0.3 范围内已知的限制。

## 未在真机上验证的项目

本轮开发环境没有连接的 Android 设备/模拟器（`adb devices` 为空列表），以下项目仅完成了编译与单元测试层面的验证，**尚未做真机人工验证**：

- v0.3 全部新增页面（分享列表/详情/创建/编辑、搜索页、任务中心、离线下载表单）的实际渲染、交互与暗色模式（DESIGN 验收②未做人工走查，仅代码层面遵循既有 token）。
- 分享全闭环（创建→查看→复制/系统分享→Web 端可见→修改→启用/禁用→删除）在真实 OpenList 实例上的往返验证。
- 搜索在已建索引/未建索引实例上的实际行为。
- 任务中心 4 秒轮询的真机耗电/协程生命周期验证（离开页面即停已按 `ViewModel` 作用域实现，逻辑正确但未真机确认）。
- 离线下载提交→任务中心可见状态的真实链路。
- 下载状态回读（`DownloadManager.query()` + `ACTION_DOWNLOAD_COMPLETE` 广播）在真机上的实际表现。
- **Room 6→7 真实升级路径**（v0.2.0 APK 已有数据 → v0.3 APK 打开后数据保留且新表可用）。

建议在自建 OpenList 实例上过一遍 [v0.3_EXECUTION_PLAN.md](v0.3_EXECUTION_PLAN.md) §23 验收清单再发布。

## 待真机核对的后端字段推测（V-01 ~ V-07）

以下字段/行为在参考后端源码（`openlist-ref/`）中不可得或存在保留字段以外的不确定性，代码中已标注为"provisional"，采用了保守默认值（缺省不发送/字段带默认值），且解码使用 `ignoreUnknownKeys`/`coerceInputValues` 保证即使猜错也不会崩溃：

- **V-01** Web 分享页 URL 格式：当前实现为 `{baseUrl}/@s/{sid}`（比照后端 `/sd/:sid` 直链家族推测），未经真实 Web 前端核对。
- **V-02** `tache.State` 数值枚举：`TaskStateMapper` 按推测值映射（0=Pending…7=Failed，8/9=Retry），未知值一律映射为 `UNKNOWN` 而非崩溃或误判。
- **V-03** `SearchNode`/`SearchReq` 确切 JSON 字段名与 `scope` 语义：`SearchNodeResp` 字段为最佳猜测，猜错的字段会静默取默认值而非报错。
- **V-04** 未建索引时 `/api/fs/search` 的错误行为：`SearchRepositoryImpl` 用错误信息里的 `index`/"未建立索引"关键字启发式映射为 `DomainError.SearchNotAvailable`，未核对真实错误文案。
- **V-05** `delete_policy` 枚举与缺省值：客户端默认不传（空字符串），交由后端使用自身默认值。
- **V-06** `expires` 序列化格式：使用 `OffsetDateTime`/UTC 生成 RFC3339 字符串，未核对后端时区处理细节。
- **V-07** 普通用户 `share/list` 是否只返回自己的分享：已从后端源码确认（`op.GetSharingsByCreatorId`），未做真机复核。

## 权限门控

- 分享创建入口的显示逻辑沿用 v0.2 既定策略——"游客隐藏、已登录用户乐观展示"，不依据 `CanShare` 权限位隐藏；真正的权限判定以后端 403 响应为准。
- 离线下载入口同理，不依据 `CanAddOfflineDownloadTasks` 权限位做客户端隐藏门控。

## 功能范围（刻意裁剪，见 v0.3_PRD.md §5 "不做"）

- 分享访问统计/审计、分享态完整目录浏览与上传、分享二维码。
- App 内打开 OpenList 分享 URL（降级为仅复制链接+系统分享）。
- 本地全文索引、离线搜索、结果按类型筛选/排序、加载更多分页（结果超过 100 条仅显示前 100 条）。
- 任务日志详情页、速度曲线、任务列表按状态筛选。
- 后台常驻轮询服务（轮询仅在任务中心页面可见时进行）。
- BT 文件选择、Magnet 高级解析、多 URL 批量离线提交、下载限速、断点续传。
- 分享密码复制/完整文案复制以外的分享文案自定义、剪贴板 URL 自动识别、上传重试（`UploadRepository.retryUpload` 仍未实现，沿用 v0.2 状态）。
- 管理台任务视图（`TaskRepository` 目前只轮询 offline_download/offline_download_transfer/copy/move 四类，upload/decompress/decompress_upload 预留至 v0.5）。

## 任务中心

- 本地下载任务的取消操作 v0.3 不支持（`TaskAggregationRepository.cancelTask` 对 `LOCAL_DOWNLOAD` 直接返回失败提示"暂不支持取消下载任务"）。
- 任务中心"有运行中任务"徽标目前只反映本地上传任务状态（复用已有 `hasActiveUploads` 信号），未聚合下载/远程任务——完整的三源徽标需要 `FileListViewModel` 额外订阅 `TaskAggregationRepository`，留待后续优化。

## 测试与验收覆盖

- 自动化测试覆盖范围与 v0.2 相同（`core/network` 路径编解码/Base URL 规范化、`FileOperationRepositoryImplTest`），v0.3 新增的 5 个 Repository、`TaskStateMapper`、聚合排序等均未补充单元测试（计划中的 S2-T4/S4-T4/S5-T1 单测项未执行）。
- Room Migration 6→7 未做 `MigrationTestHelper` 插桩测试（需要设备/模拟器），仅完成了手写 SQL 与 Room 导出 schema JSON 的逐字段核对（与 v0.2 的 4→5/5→6 同等严格程度）。

## 工程/发布

- `Parity_Matrix.md`（记录与 Web 端行为差异）计划中要求 S7 起头，本轮未创建。
- 深色模式色板复用 v0.2 既有 token，新组件（`ShareCard`/`TaskCard`/`SearchBar` 等）未做系统性人工暗色走查。

---

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
