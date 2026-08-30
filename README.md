<div align="center">

<img src="docs/brand-logos/luzzy.png" width="128" alt="LuzzyRP"/>

# LuzzyRP

**"每次对话，都像一本有你的小说。"**

移动端 AI 角色扮演应用 · 将 LLM 的推理能力与角色扮演（RP）规则/背景提示词深度融合

![Android](https://img.shields.io/badge/Android-Native%20Kotlin-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.05.01-4285F4?logo=jetpackcompose&logoColor=white)
![Material 3 Expressive](https://img.shields.io/badge/Material%203-Expressive-EADDFF)
![License](https://img.shields.io/badge/License-CC%20BY--NC%204.0-F5A623)

</div>

---

## 简介

LuzzyRP 是一款原生 Android AI 角色扮演应用。它不是又一个聊天壳——而是把 **真流式输出**、**Agentic 闭环**、**角色卡生态** 与 **长期记忆** 做成一套完整的 RP 引擎，让每一次对话都像在读一本以你为主角的小说。

- **真流式**：模型出一个字，屏幕上就出一个字。1 字 = 1 次更新，无伪打字机、无节流。
- **Agentic**：多轮思考 + 主动工具调用 + 结果原地回填，推理过程以思考卡片时间线完整呈现。
- **像小说**：角色卡、世界书、正则脚本、三级摘要与长期记忆共同构成连贯的长篇叙事体验。

## 核心能力

| 能力 | 说明 |
|------|------|
| 真流式输出 | `callbackFlow` + OkHttp SSE `EventSources` + `trySend` 逐 event 零节流 + `readTimeout = 10 MINUTES`，1 字 = 1 次更新 |
| Agentic 闭环 | `maxLoops = 3` 两阶段闭环（推理+工具规划 → 基于结果生成最终回复），工具结果原地回填保持 KV 缓存命中率，禁止 `tool_choice = required` |
| 三级摘要 | A 级每轮生成（≤50）/ B 级每 10 轮（≤10）/ C 级每 50 轮（永久） |
| 文本标签兜底 | `<tool_calls>tool_name:args</tool_calls>` 标签解析，为 GLM-5.2 等不支持原生 function calling 的模型兜底 |
| 角色卡生态 | SillyTavern PNG 导入/导出、世界书三策略召回、正则脚本、UI 模板、收藏 |
| 长期记忆 | ACE 三步循环（Execute → Reflect → Update）、向量相似度检索、嵌入去重与评分淘汰 |

## 技术栈

| 层 | 选型 |
|----|------|
| 语言 | Kotlin 2.4.0（JVM 17 字节码 target，jvmToolchain(21)） |
| UI | Jetpack Compose（BOM 2026.05.01）· Material3 1.5.0-alpha21 · MaterialExpressiveTheme |
| 架构 | MVVM + 单一真源 `MutableStateFlow<Conversation>` + `collectAsStateWithLifecycle()` |
| 导航 | Navigation3 1.1.2（`entry<T> { key -> XxxPage() }` 模式） |
| DI | Koin 4.2.1（BOM 模式） |
| 网络 | OkHttp 5.3.2 · okhttp-sse EventSources（真流式 SSE） |
| 数据库 | Room 2.8.4（导出 schemas + AutoMigration 就绪）· DataStore Preferences |
| 向量检索 | sqlite-vec（记忆/世界书/摘要嵌入） |
| 序列化 | kotlinx.serialization |
| 构建 | Gradle 9.4.1 · AGP 9.2.1 · KSP 2.3.9 |
| SDK | compileSdk 37 · minSdk 26 · targetSdk 37 |

## 模块结构

```
LuzzyRP/
├── app/          # 主应用：UI、ViewModel、Room、DataStore、DI、ChatService
├── core/model/   # 纯领域模型（UIMessage / MessageChunk / 角色卡 / 世界书 / 记忆）
├── core/ai/      # AI SDK：Provider、SSE 真流式、三协议、标签兜底解析
├── core/common/  # 工具：HTTP 桥接、JSON 单例、PNG tEXt chunk 读写
└── docs/         # 规划 / 工作日志 / 参考资料
```

## 构建与运行

```bash
# 环境要求：JDK 21 · Android SDK 37 · （首次构建会自动安装缺失的 SDK 组件）
git clone git@github.com:LuzzyMeow/LuzzyRP.git
cd LuzzyRP
./gradlew assembleDebug        # Debug 构建
./gradlew assembleRelease      # Release 签名构建（需 keystore.properties）
./gradlew test                 # JVM 单元测试
```

## 下载

前往 [Releases](https://github.com/LuzzyMeow/LuzzyRP/releases) 页面：稳定版（`x.y.0`）附 APK 安装包。

## 内置供应商

开箱即用的预置档案（均可编辑/删除）：

| 供应商 | 端点 | 模型 |
|--------|------|------|
| DeepSeek | api.deepseek.com | deepseek-v4-pro / deepseek-v4-flash（reasoning_effort=max） |
| 火山方舟 CodingPlan | coding v3 端点 | glm-5.2（1024K 上下文）/ deepseek-v4-pro / doubao-embedding-vision |
| 自定义 | 任意 OpenAI 兼容端点 | 自行配置 |

## 开发者须知

**接手开发前必读**（按顺序）：

1. [`HARD_REQUIREMENTS.md`](HARD_REQUIREMENTS.md) —— 12 条硬性规定 + 真流式 6 不变性 + Agentic 6 不变性，**违反任何一条即为不合格交付**；
2. [`docs/PLAN-v0.1.0.md`](docs/PLAN-v0.1.0.md) —— 架构与实施计划；
3. [`docs/WORKLOG.md`](docs/WORKLOG.md) —— 工作日志（跨会话连续记忆）；
4. [`docs/INVARIANTS-CHECKLIST.md`](docs/INVARIANTS-CHECKLIST.md) —— 发版前逐项自检。

不变性落点的代码文件头部带有 `[INVARIANT-STREAMING]` / `[INVARIANT-AGENTIC]` 注释块，标明禁止修改的范围与原因——**看到该标记即视为红线**。

## 许可证

本项目以 [CC BY-NC 4.0](LICENSE)（署名-非商业性使用 4.0 国际）许可开源。

<div align="center">
<sub>LuzzyRP · Built with Kotlin & Jetpack Compose</sub>
</div>
