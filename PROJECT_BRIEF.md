# Project Brief

本项目是 OpenList Android 原生客户端。

目标是通过 Kotlin + Jetpack Compose 实现一个原生 Android APK，接入用户已有 OpenList 后端实例，实现比移动 Web 更好的浏览、上传、下载、文件管理、分享、搜索、任务中心、预览、播放器和轻量管理体验。

长期目标不是 WebView 包壳，而是原生 REST API 客户端。

核心原则：
- 主流程原生实现；
- REST API 优先；
- UI 遵守 DESIGN.md；
- 功能范围按版本渐进；
- 每个版本必须可执行、可验收；
- 遇到关键决策必须实时向用户汇报；
- 不得擅自扩大当前版本范围。