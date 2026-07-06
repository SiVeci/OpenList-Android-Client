# Decision Log

## Confirmed Decisions

### Architecture
- Android 原生客户端，不做 WebView 包壳。
- Kotlin + Jetpack Compose 为主技术栈。
- REST API 优先，不解析 Web 页面。
- UI 层不得直接调用 Retrofit。
- 所有 API 通过 Repository。
- 所有实例相关数据必须带 instanceId。
- Token 必须安全保存。
- 路径编码必须统一处理。

### Version Strategy
- v0.1：技术验证与最小闭环。
- v0.2：上传与文件操作。
- v0.3：分享、搜索与任务中心。
- v0.4：预览与播放器。
- v0.5：轻量管理台。
- v1.0：原子级对齐稳定版。

### UI/UX
- DESIGN.md 从 v0.2 开始正式引入。
- 后续所有新增页面必须遵守 DESIGN.md。
- v1.0 做最终视觉统一和细节打磨。

### Scope Control
- 每个版本只做该版本范围。
- 后续功能只能做架构预留，不提前实现。
- 遇到关键决策必须实时向用户汇报。

### v0.5 轻量管理台（DEC-501~506，均于 S0 定稿，详见 `v0.5_EXECUTION_PLAN.md` §16.1）
- DEC-501：重新获取 OpenList 后端参考源码（`openlist-ref/`），本次精简 checkout 首次覆盖 admin handlers（`handles/{user,storage,driver,setting,index,task}.go`、`server/router.go`、`model/{user,storage,setting}.go`），V-501~V-510 全部有一手源码依据，非 provisional。
- DEC-502：`:feature:webfallback` 占位包不升级为真实模块，v0.5 Web 兜底能力放在 `:feature:admin` + `AdminWebFallbackRepository` 内；占位包继续保留给未来 SSO/WebAuthn。
- DEC-503：Web 管理台兜底打开方式采用方案 A——外部浏览器（`ACTION_VIEW`），零新增依赖，天然无 JS Bridge/无 Token 注入面。
- DEC-504：索引更新（`admin/index/update`）路径默认 `/`，不强制接目录选择器；目录选择器复用留作可选增强（未实现）。
- DEC-505：管理任务采用方案 A——管理台内独立任务 Tab（`AdminTaskRepository`），与 `:feature:task`/现有任务中心零依赖，不做"任务中心内管理员视角开关"方案。
- DEC-506：沿用 v0.3/v0.4 的"Accepted with manual-verification caveat"验收口径，按用户 S0 指示修正为——本轮构建/单测类验收项已实测通过（非纯静态审查），仅真机项（Room 8→9 真实升级路径、`/@manage` 真实可达性等）列为验收报告 caveat。

### v0.5 推荐决策采纳（P-501~512，均按执行计划原样采纳，详见 `v0.5_EXECUTION_PLAN.md` §16.2）
- P-501：`:feature:admin` graduate，删除 `:app` 内 admin 占位包。
- P-502：单条 `ADMIN = "admin/{instanceId}?tab={tab}"` 宿主路由 + 7 Tab 内部状态；用户/存储/驱动详情用 BottomSheet，不加路由。
- P-503：管理任务独立 `AdminTaskRepository` + 内存态 StateFlow 缓存，不写 `remote_tasks` 表；复用 `TaskInfoDto`/`TaskStateMapper`/`UnifiedTaskStatus`；既有 `TaskRepository` 零改动。
- P-504：`admin_cache` 单表通用缓存（scope+cacheKey+rawJson），只缓存用户/存储/设置三类；任务与索引进度不入库。
- P-505：门控实现为 ADMIN 路由进入即 `checkAccess`（强制 `/api/me` 刷新）；设置页入口仅做展示层预判；DENIED 态零 admin 请求。
- P-506：存储启停缓存联动——enable/disable 按 mountPath 前缀精确失效（文件+预览缓存）；load_all 当前实例全清（保守策略）。
- P-507：`admin_cache.rawJson` 只存领域模型序列化结果，不存后端原始响应，缓存层结构上不可能含敏感字段。
- P-508：设置私密判定 = 后端 `flag` PRIVATE 语义 ∪ key 关键字（token/secret/password/key），双重判定宁可多脱敏。
- P-509：轮询——任务 Tab 4 秒（仅 undone、仅可见+有运行中）；索引 Tab 3~5 秒（仅可见+running）；连续失败即停。
- P-510：`DomainError` 只新增 `AdminAccessDenied`/`AdminApiUnavailable`；操作类失败经 `OpenListError` 透传 message；`WebFallbackUnavailable` 复用 `ExternalOpenUnavailable`+专属文案。
- P-511：versionCode=5、appVersionName="0.5.0"（S8 落地，已确认）。
- P-512：概览统计卡片按可选项处理，S2 只做结构骨架，随 S4/S5/S6 逐卡点亮，任何摘要失败独立降级。

### v1.0 原子级对齐 + 功能补齐（DEC-601~607，S0 定稿，详见 `v1.0_EXECUTION_PLAN.md` §16、`v1.0_IMPLEMENTATION_LOG.md` S0 章节）
- DEC-601：2FA/OTP 用登录页内联第二步（方案 A）；源码复核确认 LDAP 登录路径无 OTP 分支，无需并行状态机。
- DEC-602：分享入站只做 App 内粘贴 + 剪贴板检测，不注册 ACTION_VIEW（方案 A）；Parity 记 Deferred（平台机制限制）。
- DEC-603：管理任务批量只做 `clear_done`/`clear_succeeded`/`retry_failed` 三端点（方案 A）；V-610 确认全部 6 端点均存在，`some` 系响应形态也已明确，但按 PRD 范围裁决不纳入，记 Parity Deferred。
- DEC-604：索引 `maxDepth=-1` 默认值（方案 A）；V-609 源码确认 `-1` 是可靠的"事实无限递归"，非猜测。
- DEC-605：真机/模拟器通过为硬门槛，API 29 磁盘不允许时记 KNOWN_ISSUES + Parity 差异（方案 B）。
- DEC-606：Markdown 内嵌图片用 markwon-image + 自写受限 loader（方案 A，S4 编译时最终确认可行性）；V-608 确认签名 URL 机制通用适用于图片。
- **DEC-607（S0 复核后调整为方案 B）**：raw_url 端口加固——S0 在 `nas.siveci.com:18443` 实测 4 个不同存储的 raw_url 均已正确带端口，未能复现历史观察到的丢端口问题，判断该实例 site_url 配置已在两次会话间被修正；只复现了"直链端点裸文本错误响应"（416/405），已被 S1 `HttpException` 修复覆盖。**用户确认采纳 B：不做端口重写代码，只做 content-type 粗校验**；`KNOWN_ISSUES.md`/`Parity_Matrix.md` 记录该问题为环境配置类、当前实例已自行修复、App 不做静默端口纠正。