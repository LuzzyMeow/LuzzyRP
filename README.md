<div align="center">

<img src="app/src/main/res/drawable-nodpi/luzzy_logo.png" width="96" alt="LuzzyRP"/>

# LuzzyRP

**"每次对话，都像一本有你的小说。"**

移动端 AI 角色扮演应用 · 将 LLM 的推理能力与角色扮演（RP）规则/背景提示词深度融合

</div>

> [!WARNING]
> ## ⚠️ 本项目仍在积极开发中（Work In Progress）
>
> **当前版本不支持正常游玩。** LuzzyRP 尚处于早期开发阶段：
>
> - ❌ **无法直接开箱游玩**——需要自行配置模型供应商 API Key 才能产生对话；
> - ❌ 核心玩法链路（真流式逐字输出、Agentic 工具调用、记忆/摘要、世界书召回）**尚未经过完整实机验收**；
> - ❌ 部分 UI 仍在重制中，存在已知未修复项（见 CHANGELOG「遗留」）；
> - ❌ 数据格式（Room schema、Settings）**在 v1.0 前仍可能破坏性变更**，不保证旧数据兼容；
> - ❌ **不提供任何安装包支持**——Release 不附带 APK，安装包仅供开发者自行构建（见下文「构建」）。
>
> **想要尝鲜？** 请自行 `git clone` 并用 Android Studio 构建 debug 包，预期遇到 bug 属正常现象。
> 稳定可玩的首个版本会在开发完成后另行发布（届时 Release 将附 APK）。

![Status](https://img.shields.io/badge/Status-WIP%20开发中-red)
![Android](https://img.shields.io/badge/Android-Native%20Kotlin-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.05.01-4285F4?logo=jetpackcompose&logoColor=white)
![Material 3 Expressive](https://img.shields.io/badge/Material%203-Expressive-EADDFF)
![License](https://img.shields.io/badge/License-CC%20BY--NC%204.0-F5A623)

---

## 简介

LuzzyRP 是一款原生 Android AI 角色扮演应用。它不是又一个聊天壳——而是把 **真流式输出**、**Agentic 闭环**、**角色卡生态** 与 **长期记忆** 做成一套完整的 RP 引擎，让每一次对话都像在读一本以你为主角的小说。

- **真流式**：模型出一个字，屏幕上就出一个字。1 字 = 1 次更新，无伪打字机、无节流。
- **Agentic**：多轮思考 + 主动工具调用 + 结果原地回填，推理过程以思考卡片时间线完整呈现。
- **像小说**：角色卡、世界书、正则脚本、三级摘要与长期记忆共同构成连贯的长篇叙事体验。

## 核心能力（开发目标 · 完成度见 CHANGELOG）

| 能力 | 说明 | 状态 |
|------|------|------|
| 真流式输出 | `callbackFlow` + OkHttp SSE `EventSources` + `trySend` 逐 event 零节流 + `readTimeout = 10 MINUTES`，1 字 = 1 次更新 | 架构完成，实机验收中 |
| Agentic 闭环 | `maxLoops = 3` 两阶段闭环，工具结果原地回填，禁 `tool_choice = required` | 架构完成，单测通过 |
| 三级摘要 | A 每轮（≤50）/ B 每 10 轮（≤10）/ C 每 50 轮（永久） | 已实现 |
| 文本标签兜底 | `<tool_calls>` 标签解析（GLM-5.2 等无原生 FC 模型） | 已实现，单测通过 |
| 角色卡生态 | SillyTavern PNG 导入/导出、世界书编辑器、正则脚本、UI 模板、收藏 | 部分（正则/UI 模板待 UI） |
| 长期记忆 | ACE 三步循环、向量检索（sqlite-vec）、嵌入去重与评分淘汰 | 已实现 |
| TRPG 模式 | D&D 5e 规则引擎、GM 工具组、世界卡设计模式 | 规划中（v0.4+） |

## 技术栈

| 层 | 选型 |
|----|------|
| 语言 | Kotlin 2.4.0（JVM 17 字节码 target，jvmToolchain(21)） |
| UI | Jetpack Compose（BOM 2026.05.01）· Material3 1.5.0-alpha21 · MaterialExpressiveTheme |
| 架构 | MVVM + 单一真源 `MutableStateFlow<Conversation>` + `collectAsStateWithLifecycle()` |
| 导航 | Navigation3 1.1.2（`entry<T> { key -> XxxPage() }` 模式） |
| DI | Koin 4.2.1（BOM 模式） |
| 网络 | OkHttp 5.3.2 · okhttp-sse EventSources（真流式 SSE） |
| 数据库 | Room 2.8.4（v2，手写迁移）· DataStore Preferences |
| 向量检索 | sqlite-vec（记忆/世界书/摘要嵌入） |
| 序列化 | kotlinx.serialization |
| 构建 | Gradle 9.4.1 · AGP 9.2.1 · KSP 2.3.9 |
| SDK | compileSdk 37 · minSdk 26 · targetSdk 37 |

## 模块结构

```
LuzzyRP/
├── app/          # 主应用：UI、ViewModel、Room、DataStore、DI、ChatService、AppLogger
├── core/model/   # 纯领域模型（UIMessage / MessageChunk / 角色卡 / 世界书 / 预设）
├── core/ai/      # AI SDK：Provider 三协议、SSE 真流式、标签兜底解析
├── core/common/  # 工具：HTTP 桥接、JSON 单例、PNG tEXt chunk 读写
├── tools/        # 图标资产管线（icon_pipeline.py）与补丁脚本
└── docs/         # 规划 / 工作日志 / 设计技能存档（docs/skills/）
```

## 构建与运行（开发者）

```bash
# 环境要求：JDK 21 · Android SDK 37
git clone git@github.com:LuzzyMeow/LuzzyRP.git
cd LuzzyRP
./gradlew assembleDebug        # Debug 构建（可直接安装尝鲜）
./gradlew test                 # JVM 单元测试
```

内置供应商预置档案（需自行填 API Key）：DeepSeek / 火山方舟 CodingPlan / 自定义 OpenAI 兼容端点。

## 开发者须知

**接手开发前必读**（按顺序）：

1. [`HARD_REQUIREMENTS.md`](HARD_REQUIREMENTS.md) —— 13 条硬性规定（含规定 13：4 项设计 SKILL 强制）+ 真流式 6 不变性 + Agentic 6 不变性，**违反任何一条即为不合格交付**；
2. [`DESIGN.md`](DESIGN.md) —— 设计契约（唯一设计真源）；
3. [`docs/AGENT-GUIDE.md`](docs/AGENT-GUIDE.md) —— 后续 Agent 开发指南；
4. [`docs/PLAN-v0.1.0.md`](docs/PLAN-v0.1.0.md) —— 架构与实施计划；
5. [`docs/WORKLOG.md`](docs/WORKLOG.md) —— 工作日志（跨会话连续记忆）；
6. [`docs/INVARIANTS-CHECKLIST.md`](docs/INVARIANTS-CHECKLIST.md) —— 发版前逐项自检。

不变性落点代码头部带 `[INVARIANT-*]` 注释块——**看到该标记即视为红线**。

## 路线图

- **v0.3.x** 暗色/AMOLED 巡检、组件统一收尾、聊天背景打磨
- **v0.4** 稳定性验收（流式逐字实测、长会话回归）→ 首个可玩版本
- **v0.5+** TRPG 模式专项（D&D 5e 引擎、GM 工具组、世界卡设计模式）

## 许可证

本项目以 [CC BY-NC 4.0](LICENSE)（署名-非商业性使用 4.0 国际）许可开源。

<div align="center">
<sub>LuzzyRP · Built with Kotlin & Jetpack Compose · WIP</sub>
</div>
