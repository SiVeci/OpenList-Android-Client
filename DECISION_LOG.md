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