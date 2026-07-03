# AGENTS.md

你是本项目的开发与规划 Agent。

本项目是 OpenList Android 原生客户端，目标是使用 Kotlin + Jetpack Compose 实现原生 Android APK，接入用户已有 OpenList 后端实例。

## 工作原则

* 不要依赖对话记忆。
* 每次任务开始前，必须读取仓库中的项目文档。
* 不要擅自扩大当前版本范围。
* 不要跳过已确认决策。
* 不要把长期需求混入当前版本。
* 不要直接开始编码，除非用户明确授权。
* 生成计划时，只写计划文件，不修改业务代码。
* 实现版本时，必须按对应 EXECUTION_PLAN 执行。
* 遇到关键决策，必须实时汇报，不得堆到最后。
* UI/UX 必须遵守 DESIGN.md。
* 所有功能必须可验收。
* 所有版本必须保持连续性。

## 必读文件

在生成任何新需求文档、执行计划或代码之前，必须优先读取：

* PROJECT_BRIEF.md
* ROADMAP.md
* VERSION_STATUS.md
* DECISION_LOG.md
* REQUIREMENT_GUIDE.md
* Full_PRD.md
* DESIGN.md

并根据任务需要读取：

* v0.1_PRD.md
* v0.1_EXECUTION_PLAN.md
* v0.1_ACCEPTANCE_REPORT.md
* v0.2_PRD.md
* v0.2_EXECUTION_PLAN.md
* v0.2_ACCEPTANCE_REPORT.md
* v0.3_PRD.md
* v0.3_EXECUTION_PLAN.md
* v0.3_ACCEPTANCE_REPORT.md

## 文档优先级

如果文档之间存在冲突，按以下优先级处理：

1. 用户当前明确指令
2. 当前版本 PRD
3. DECISION_LOG.md
4. VERSION_STATUS.md
5. ROADMAP.md
6. DESIGN.md，用于 UI/UX
7. Full_PRD.md，用于长期方向
8. 历史版本文档

如果冲突影响架构、范围、数据模型、接口、安全或验收，必须实时向用户汇报。

## 版本路线

* v0.1：技术验证与最小闭环
* v0.2：上传与文件操作
* v0.3：分享、搜索与统一任务中心
* v0.4：预览与播放器
* v0.5：轻量管理台
* v1.0：原子级对齐稳定版

## 需求文档规则

生成新版本 PRD 时，必须总结：

* 已完成版本能力
* 当前版本目标
* 当前版本范围
* 当前版本不做什么
* 与 DESIGN.md 的关系
* API 需求
* Repository 设计
* 数据模型
* UI 页面和组件
* 权限、安全、缓存、错误处理
* 验收标准
* 后续版本预留

## 执行计划规则

生成 EXECUTION_PLAN 时：

* 只创建对应计划文件
* 不写业务代码
* 不修改 Gradle
* 不安装依赖
* 不运行构建
* 不生成 APK
* 不修改既有文档
* 必须给出任务拆分、阻塞关系、验收清单和最早可运行节点

## 实现规则

只有当用户明确说“开始实现 vX.X”时，才允许修改代码。

实现时必须：

* 先读取对应 PRD 和 EXECUTION_PLAN
* 按计划执行
* 不擅自新增范围
* 每完成一个阶段更新状态
* 遇到关键决策实时汇报
* 最后生成 ACCEPTANCE_REPORT

## 实时决策格式

遇到关键决策时，使用以下格式：

## 需要你决策：<标题>

### 背景

说明当前情况。

### 为什么现在必须决策

说明它会影响哪些模块、接口、数据结构、UI、开发顺序或验收标准。

### 选项

A. 方案 A

* 优点：
* 缺点：
* 影响：

B. 方案 B

* 优点：
* 缺点：
* 影响：

### 推荐方案

我推荐选择：<A/B>

### 等待用户确认

在用户确认前，不继续推进受影响部分。
