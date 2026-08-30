# LuzzyRP 后续 Agent 开发指南（AGENT-GUIDE）

> 本文件是后续任何开发 Agent 接手 LuzzyRP 时的**上手顺序与作业规范**。
> 与 `HARD_REQUIREMENTS.md` 配套：那边是「什么不能做」，这里是「按什么流程做」。

## 一、接手必读（顺序严格）

1. `HARD_REQUIREMENTS.md` —— 12+1 条硬性规定（含规定 13：4 项设计 SKILL 强制）
2. `DESIGN.md`（仓库根）—— 设计契约，UI 工作的唯一真源
3. `docs/PLAN-v0.1.0.md` —— 架构与实施计划
4. `docs/WORKLOG.md` —— 全部历史决策与遗留（**接手后每次会话必须追加**）
5. `docs/INVARIANTS-CHECKLIST.md` —— 发版自检表
6. 不变性落点代码头部的 `[INVARIANT-*]` 注释块 —— 视为红线

## 二、设计技能使用规范（规定 13 的执行细则）

| 场景 | 必须执行 |
|------|----------|
| 任何 UI 新增/改动前 | 读仓库根 `DESIGN.md`；新页面先对照 `docs/skills/awesome-design-md-main/design-md/` 同类范本 |
| 动效实现 | 读 `docs/skills/huashu-design/references/animation-best-practices.md` + `animation-pitfalls.md`；时长一律取 `MotionTokens` |
| UI 交付前 | 过 `docs/skills/ui-ux-pro-max-skill/.claude/skills/ui-ux-pro-max/references/pro-rules.md` 的 Pre-Delivery Checklist |
| Compose 代码规约 | 对照 `docs/skills/ui-ux-pro-max-skill/.claude/skills/ui-ux-pro-max/data/stacks/jetpack-compose.csv`（无状态组合、单一真源、Lazy key、derivedStateOf 等） |
| 大型 UI 方案评审 | huashu-design 五维 critique：层次/节奏/一致性/细节/克制（`references/critique-guide.md`） |

## 三、架构速查

```
app/            UI(ViewModel+Compose) · Room v2 · DataStore · ChatService · AppLogger
core/model/     UIMessage/MessageChunk/CharacterCard/Worldbook/PromptPreset/UserProfile...
core/ai/        Provider 三协议 SSE 真流式 · TagToolCallParser · ThinkingDepthAdapter
core/common/    JsonInstant(KV稳定) · Call.await · PngTextChunk
tools/          icon_pipeline.py（图标/启动图资产生成，改动后必须重跑）
```

- 生成链路：`ChatService → PromptAssembler(KV三层) → GenerationHandler(256步/原地回填) → Provider.streamText`
- 改动提示词/消息序列化前必读 `[INVARIANT-KV]`；改动生成循环前必读 `[INVARIANT-AGENTIC]`
- 图标只允许 `GameIcons/LobeIcons/LuzzyIcons` 注册表（tools/icon_pipeline.py 生成）；新图标加进 `ALIASES` 后重跑管线

## 四、开发循环

1. 每次 session 开始：读 WORKLOG 最后一节 → 更新本文件不必要
2. 每次 session 结束：WORKLOG 追加「完成/决策/遗留/下一步」+ commit + push
3. 发版：INVARIANTS 自检 → 单测 → assembleRelease → CHANGELOG/README → tag → Release（稳定版附 APK）
4. 模拟器回归：AVD `LuzzyRP_Test`（Android 15），安装 `app-universal-release.apk`

## 五、已知禁区速记

- 禁止破坏流式逐字 / Agentic 轮次 / NSFW 内容 / KV 前缀稳定
- 禁止自绘图标、引入非规定字体、硬编码色值、Modifier.blur
- 禁止占位符交付（NSFW 占位除外，属有意保留）
- 内置卡「鹿溪」readonly；gh token 无 delete_repo（删仓库需用户操作）
