# Known Issues (v0.1.0)

这些是 v0.1 范围内已知的限制，多数是刻意的范围裁剪（见 [v0.1_PRD.md](v0.1_PRD.md) §2 "v0.1 不追求的目标"），而非缺陷；按后续可能修复/扩展的方向列出。

## 下载

- 下载任务在应用内只记录到"已入队"（`DownloadTaskEntity.status = ENQUEUED`）。真正的下载失败（404、签名过期、权限不足等）由系统 `DownloadManager` 的通知栏原生承接，应用内不会二次感知或更新任务状态为成功/失败。要做到这一点需要监听 `ACTION_DOWNLOAD_COMPLETE` 广播并查询 `DownloadManager`，属于 v0.2+ 自研下载器范畴。
- 未提供应用内的下载历史/任务列表页面（v0.1_PRD 中标注为可选项）。
- 同名文件冲突依赖 `DownloadManager` 默认的自动加后缀行为，应用未额外处理覆盖确认。

## 鉴权与会话

- 会话有效性只在进入登录页（或应用重新导航到该实例）时通过一次 `/api/me` 校验；没有后台定时刷新或应用切前台时的主动重新校验。
- 不支持 2FA、LDAP 登录、SSO、WebAuthn、Hash 登录的 UI；对应的网络层方法（`loginHash` 等）已预留但未接入界面。
- 不支持自签证书；HTTPS 严格校验系统信任锚，暂无"忽略证书错误"或"添加自定义 CA"的入口。

## 功能范围

- 无搜索、上传、文件操作（新建目录/重命名/移动/复制/删除）、分享、预览（图片/视频/文本/Markdown）、离线下载、归档浏览、Torrent、管理台。均已在包结构和网络层预留，具体见 [v0.1_EXECUTION_PLAN.md](v0.1_EXECUTION_PLAN.md) §17。

## 测试与验收覆盖

- 自动化测试仅覆盖 `core/network` 的纯逻辑部分（路径编解码 `OpenListPathCodec`、Base URL 规范化 `BaseUrlNormalizer`），没有 Repository/ViewModel 单元测试或 Compose UI 测试。
- 多实例隔离、子路径部署、HTTP/HTTPS 混合实例、中文/空格/特殊符号真实服务端路径等场景，需要人工在真实 OpenList 实例上验证（见 v0.1_EXECUTION_PLAN.md §13 验收清单），本轮未在真机上执行。

## 工程/发布

- Room 数据库版本升级目前使用 `fallbackToDestructiveMigration`（发布前迭代的正常做法）。首次正式发布后如果继续升级 schema，必须改为正式 `Migration`，否则会清空用户本地数据。
- 网络请求统一 30 秒超时，无重试/指数退避策略。
- 深色模式色板已定义（`core/designsystem`），但未做系统性的视觉走查。
