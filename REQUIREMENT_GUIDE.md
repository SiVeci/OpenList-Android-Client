# Requirement Guide

## Purpose

本文件用于指导 Codex 后续生成每个版本的需求文档，保证完整性、连续性和范围控制。

## Required Reading Before Generating Any New PRD

Codex 必须先读取：

- PROJECT_BRIEF.md
- ROADMAP.md
- VERSION_STATUS.md
- DECISION_LOG.md
- Full_PRD.md
- DESIGN.md
- 所有已完成版本的 PRD
- 所有已完成版本的 EXECUTION_PLAN
- 所有已完成版本的 ACCEPTANCE_REPORT，如存在

## Priority Rules

如果文档冲突，优先级如下：

1. 用户本轮明确指令
2. 当前版本 PRD 目标
3. DECISION_LOG.md
4. VERSION_STATUS.md
5. ROADMAP.md
6. DESIGN.md，用于 UI/UX
7. Full_PRD.md，用于长期方向
8. 历史版本 PRD 和计划

## PRD Generation Rules

每个新版本 PRD 必须包含：

- 前序要求总结
- 已完成版本能力总结
- 当前版本定位
- 当前版本范围
- 明确不做的内容
- 需要决策的事项
- UI/UX 要求
- API 需求
- Repository 设计
- 数据模型
- 页面和组件
- 权限规则
- 缓存与刷新
- 错误处理
- 安全要求
- 验收标准
- 后续版本预留
- 成功标准
- 推荐结论

## Decision Reporting

遇到影响以下内容的问题，必须实时汇报：

- 架构方向
- 功能范围
- 数据模型
- API 接入方式
- UI 设计系统
- 权限和安全
- 工期和版本拆分
- 后续版本兼容性

不得把关键决策堆到文档最后。