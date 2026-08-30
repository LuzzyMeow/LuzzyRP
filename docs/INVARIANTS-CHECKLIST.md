# LuzzyRP 发版前不变性自检表（INVARIANTS-CHECKLIST）

## v0.1.0 自检记录（2026-08-30）

| 区块 | 结果 |
|------|------|
| A 流式输出 | ✅ 架构层通过（callbackFlow+trySend 逐 event / awaitClose cancel / readTimeout 10min / 单一真源直写）；真机逐字验收待用户提供 API Key |
| B Agentic | ✅ 单测通过（GenerationLoopTest：原地回填/审批续跑/两阶段收口/256 步）；无 tool_choice=required；被动工具经系统提示注入 |
| C NSFW | ✅ NsfwBlock 占位，无任何过滤/改写逻辑 |
| D KV 缓存 | ✅ KvPrefixStabilityGoldenTest 绿（同历史两次组装逐字节相等 + 易变字段不进前缀） |
| E 资产与 UI | ✅ 图标全部经 GameIcons/LuzzyIcons 注册表；LuzzyMixedFontFamily 混排；AuroraSurface 三态交互 + 页面三态转场；色值集中于 AuroraColor |
| F 质量底线 | ✅ 无 TODO/FIXME 遗留（NsfwBlock 占位为有意保留） |
| G 测试与构建 | ✅ 24 项 JVM 单测全绿；assembleRelease 签名成功；Room schemas 已导出 |
| H 文档与发布 | ✅ CHANGELOG/README/WORKLOG 同步；tag v0.1.0 + Release（附 APK） |

---


> 规则：每次发版（含 alpha/beta）前逐项打勾。任何一项不通过则**禁止发版**。
> 自检结果追加记录到 `docs/WORKLOG.md`。

## A. 流式输出（HARD_REQUIREMENTS 规定 1）

- [ ] 所有模式（RP 对话/普通对话）均为模型原生流式，无伪打字机实现
- [ ] `streamText` 为 `callbackFlow` + `EventSources.newEventSource`，`onEvent → trySend` 逐 event 零节流
- [ ] `awaitClose { eventSource.cancel() }` 存在且未被移除
- [ ] OkHttp `readTimeout = 10 MINUTES` 未被改动
- [ ] UI 侧 `collectAsStateWithLifecycle()` 直连 `MutableStateFlow<Conversation>`，1 字 = 1 次更新
- [ ] 实测：思考卡片逐字出现、正文气泡逐字出现（DeepSeek + 方舟至少各测一次）

## B. Agentic 模式（规定 2）

- [ ] `GenerationHandler.generateText` 的 `maxSteps = 256` for 循环未被削弱
- [ ] 工具结果原地回填（无新建 TOOL 消息路径）
- [ ] 三 break 条件完整（不调工具 / 等待审批 / 无可执行工具）
- [ ] `ToolApprovalState` 五状态机续跑正常（审批 UI → 状态回写 → 重入循环）
- [ ] 代码中无 `tool_choice = required`（仅 auto / none）
- [ ] 被动工具经系统提示注入，未预执行
- [ ] 内置提示词强化段在位（>2 轮思考、>1 次主动调用约束），实测生效
- [ ] 实测：一次完整会话中模型至少 2 轮思考卡片 + 1 次主动工具调用

## C. NSFW（规定 3）

- [ ] `NsfwBlock.kt` 内容未被任何逻辑审查/过滤/改写
- [ ] 无新增内容过滤器、敏感词拦截、输出改写逻辑

## D. KV 缓存命中（规定 8）

- [ ] `KvPrefixStabilityGoldenTest` 通过（同一历史两次组装逐字节相等）
- [ ] 新功能未向「稳定前缀」层注入易变内容
- [ ] 三层提示组装顺序未被破坏（稳定前缀/半稳定段/动态尾段）

## E. 资产与 UI（规定 6）

- [ ] 新增 UI 的图标全部来自 `GameIcons.kt` / `LuzzyIcons.kt` 注册表（无自绘、无第三方 icon、无 emoji 充当图标）
- [ ] 文本渲染走 `LuzzyMixedFontFamily`（中文 PuHuiTi / 英数 AlibabaSans / 其余系统回退）
- [ ] 新增容器/按钮/弹窗/下拉均具备进入(300ms)/交互(150ms)/退出(195ms)三态动画
- [ ] 全项目无硬编码色值（新增色值已进 `AuroraColor.kt`）
- [ ] 无 `Modifier.blur` 使用

## F. 质量底线（规定 7）

- [ ] 全局搜索 `TODO` / `FIXME` / `placeholder` / 占位 —— 无未处理遗留（NSFW 占位除外，且为有意保留）
- [ ] 新功能完整可用，非半成品

## G. 测试与构建

- [ ] 全部 JVM 单测绿（ChunkMerge / TagToolCallParser / WorldbookRecall / SummaryScheduler / AceDedup / PngTextChunkRoundTrip / KvPrefixGolden / ContextTrimmer / RegexScript）
- [ ] `assembleRelease` 签名包构建成功
- [ ] Room schema 已导出且已提交（`app/schemas/`）

## H. 文档与发布（规定 9/10/12）

- [ ] `CHANGELOG.md` 已追加本版本条目
- [ ] 涉及工具/提示词变更时，agentic 请求阶段内部工具提示词已同步
- [ ] `README.md` 已同步（功能表/技术栈/下载）
- [ ] git 提交并推送（SSH）；tag `vX.Y.Z` 已打
- [ ] Release 已发布（排版符合模板；仅稳定版附 APK）
- [ ] `docs/WORKLOG.md` 已记录本版本工作
