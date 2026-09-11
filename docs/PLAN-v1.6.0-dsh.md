# PLAN-v1.6.0-dsh · 聊天侧 DSH 化（追加式上下文 + KV 前缀缓存最大化）

> **本版唯一主计划**。范围：RP 聊天（上游 Vue 前端 + 扩展层）的**上下文构造**与**工具循环**。
> 立档：2026-09-11（会话 61）。用户拍板口径：
> ① 允许改上游文件（走登记 patch，硬性规定 2/10 不变）；
> ② **不分批交付，一次性全做**；
> ③ 目标是「DSH 架构的 Agent Loop + 最大化 KV 缓存」。
>
> 前置：原生「助手」模块已按用户指示**彻底移除**（见 `CHANGELOG.md` v1.5.0「移除」段）；
> 本计划与助手无关，全部落在 `app/src/main/assets/rphub/**`（patch）与 `ext/**`（自有）。

---

## 0. 目标与验收口径

**目标架构三性质（DSH 式）**

| 性质 | 含义 | 本项目的判定方式 |
|------|------|-----------------|
| **稳定前缀** | 相邻两轮请求的 `messages`/`tools` 前缀**逐字节不变** | 新增门禁：公共前缀 ≥ 上轮长度 × 阈值 |
| **追加式** | 新内容只接到尾部；历史消息不被重写 | 同上 + 代码审查（无按「距尾深度」重算历史的路径） |
| **预算裁剪** | 只在离散点（跨轮）裁剪，且从尾部往前裁 | 记忆压缩触发点收口到「用户新消息」事件 |

**度量指标（必须可观测）**

- 服务端返回的 `cached_tokens / prompt_tokens`（命中率）——扩展层采集 + 用量页展示；
- 客户端侧「相邻两轮公共前缀占比」——扩展层比对（不依赖服务端）。

---

## 1. 现状与差距（调研结论，证据见 `docs/WORKLOG.md` 会话 61）

### 1.1 Agent Loop（聊天）

| 项 | 现状 |
|---|---|
| 入口/循环 | `generateResponse`；工具续写靠**递归自调用**（非 while） |
| 轮数上限 | `maxAutoContinue: 4` **硬编码**（`built-in-content.js`），depth≥4 时 tools 置空 |
| 工具轨迹 | 只在内存 `activeToolMessages`；depth0 清空 → **不落 chatHistory、下一用户轮不回放** |
| 渲染耦合 | 已解耦（patch 044/045 活通道）✅ |

### 1.2 KV / 前缀缓存

- **全仓零缓存设计**：无 `cache_control` / `ephemeral` / `prompt_cache_key` / `cachedContent`。
- `cached_tokens` 等字段**只采集、不使用**（`core-utils.js` 归一 → `recordApiUsage` → 用量页字段）。
- **每轮整体重建 `messages`**（不是尾部追加）。
- **前缀不稳定 7 处**（按消息序）：
  1. `system` 每轮现拼；**depth≥1 时整段 `<active_tools>` 被替换**（第 2 轮起首块即变）← 最致命；
  2. `tools` 字段 depth≥1 直接消失；
  3. 末位 user 的检索提醒只在 depth0 追加；
  4. 世界书/向量召回按「距尾部 depth」插入 + 相邻同角色合并；
  5. UI 模板块插在 system 中间 + 副模型结果带 `new Date().toISOString()`；
  6. 正则按「距尾深度」生效 → **老消息随对话变长被重新正则化**；
  7. 最近 2 条 assistant 的 thinking 回填是滑动窗口。
- **裁剪**：前端**无 token 预算**；唯一「缩短」是记忆压缩，而它**搬移前缀**（向量模式删旧轮 / 经典模式换摘要）。

---

## 2. 阶段划分（一次性全做，按依赖排序）

| 阶段 | 内容 | 产出 | 依赖 |
|---|---|---|---|
| **P0** | 观测层：`ext/luzzy-prefix-guard.js` | 前缀比对 + 命中率可观测 | 无（可先跑） |
| **P1** | 前缀稳定化（A1/A2/A3） | patch **047** | P0（用观测层验收） |
| **P2** | 深度锚 + 压缩时机（A4/A5） | patch **049** | P1 |
| **P3** | Anthropic cache 断点 + 用量页命中率 | patch **048** | P0 |
| **P4** | Agent Loop DSH 化（B1/B2/B3） | patch **050** | P1（前缀先稳） |
| **P5** | 回归门禁 `tools/prefix-cache-test.cjs` | 门禁 | P1 |
| **P6** | 真机实测 + 全门禁 + 构建 + 文档 + 提交 | 验收 | 全部 |

---

## 3. 逐阶段任务卡

### P0 · 观测层（扩展层新文件，零上游改动）

- 新建 `app/src/main/assets/ext/luzzy-prefix-guard.js`：
  - 包装 `window.fetch`（**唯一可用挂钩点**：`app.js` 在加载期就把全局解构进闭包，事后替换内部函数无效）；
  - 对发往 chat/completions 的请求体：提取 `messages`/`tools` 的**规范化序列化**，与上一轮比对 → 算出「公共前缀字符数 / 上轮长度」；
  - 记录服务端响应里的 `usage.prompt_tokens_details.cached_tokens` → 命中率；
  - 暴露 `Luzzy.prefixGuard.stats()` 供门禁/真机探针读取；失败一律静默降级（硬性规定 3）。
- 挂载：`index.html` 尾部扩展层挂载块（**patch 047 的一部分**）。

### P1 · 前缀稳定化（patch 047）

| # | 改动 | 点位 |
|---|---|---|
| A1 | depth≥1 **不再替换** system 首块的 `<active_tools>`，保持同一段文本（或整段移出 system，见 A3） | `app.js` system 拼装处 |
| A2 | depth≥1 **仍传同一份 `tools`**（靠已有 4 轮上限 + 越限报错兜底） | `app.js` 请求构造 + `api-utils.js` 输出 `tools` 的分支 |
| A3 | 把所有**随时间变**的量（`toISOString()`、记忆条数/相似度、动态召回）从 system **外移到尾部动态块** | `app.js` system 拼装 + 副模型结果注入处 |

### P2 · 深度锚 + 压缩时机（patch 049）

| # | 改动 | 点位 |
|---|---|---|
| A4 | 世界书/召回/正则的「距尾 depth」→ **绝对深度锚**（插入后位置不再随对话变长而漂移） | `data-services.js` at_depth 插入 + `app.js` 正则深度判定 |
| A5 | 记忆压缩**只在跨轮**（用户新消息）触发，生成中途不微调前缀 | `app.js` 压缩调用点 |

### P3 · Anthropic 断点 + 可见性（patch 048）

| # | 改动 | 点位 |
|---|---|---|
| C2 | Anthropic 路径加 `cache_control: {type:'ephemeral'}` 断点（system + 稳定历史边界） | `api-utils.js` Anthropic 分支 |
| C3 | 用量页增「缓存命中率」列（`cached_tokens / prompt_tokens`） | `ui-components.js` 用量区 + `runtime-services.js` 统计 |

> OpenAI 兼容路径（DeepSeek/STA1N 等）是**自动前缀缓存**，不需要指令 —— 只靠 P1/P2 把前缀稳住即可。

### P4 · Agent Loop DSH 化（patch 050）

| # | 改动 | 点位 |
|---|---|---|
| B1 | 工具轨迹**落库并回放**（assistant(tool_calls) + tool 结果进 `chatHistory` 或独立持久分片），下一用户轮仍在上下文里 | `app.js` 工具结果处理 + 分支存储 |
| B2 | 递归自调用 → **显式 while 循环**；轮数上限从硬编码 4 改为**可配 + 按 token 预算动态** | `app.js` `generateResponse` 主循环 |
| B3 | 工具定义**稳定序列化**（键序固定、按能力分组），保证 `tools` 字节稳定 | `app.js` 工具定义构造 |

### P5 · 回归门禁

- 新建 `tools/prefix-cache-test.cjs`（桌面 Chromium，用法同现有三个门禁）：
  - 注入合成历史 → 连续跑 3 轮（含一次工具轮）→ 断言**相邻两轮公共前缀 ≥ 上轮 × 阈值**；
  - **自带负控**：把 A1/A2 的修复拿掉（还原「depth≥1 替换 system / 去 tools」）必须被判红；
  - 断言命中率字段能被观测层读到。

### P6 · 验收

1. `./gradlew :app:assembleRelease` + `:app:testDebugUnitTest` 通过，单 APK；
2. `tools/verify-markers.ps1` 全绿（新增 047/048/049/050 标记项）；实体按规程重生成并双验证；
3. 三个既有门禁（page-handoff / stream-render / model-list）+ 新门禁全过；
4. **真机实测**：同一会话连续多轮，读 `cached_tokens / prompt_tokens` **前后对比**（改造前基线由 P0 观测层先采集一轮）；
5. 文档：CHANGELOG / WORKLOG / 本 PLAN 勾选进度 / AGENTS §4.2 登记表补 047-050。

---

## 4. 风险与回滚

| 风险 | 等级 | 缓解 |
|---|---|---|
| A3/A4 改变提示词**位置**（模型对位置敏感） | 中 | 真机抽测 RP 回复质量；保留开关（扩展层可强制旧行为） |
| A2 让 depth≥1 也带 tools → 模型可能再发调用 | 中 | 已有 `maxAutoContinue` 上限 + 越限报错兜底 |
| B1 上下文变长（保留工具轨迹） | 中 | 与 A5 裁剪策略配套：只在跨轮裁、从尾部往前裁 |
| B2 重构核心循环 | **高** | 最后做；先补门禁；小步提交，每步可回滚 |
| 前缀门禁误报（工具结果含时间戳等） | 低 | 门禁只断言「公共前缀占比」，不做逐字节全等 |

**回滚**：每个 patch 独立编号、独立提交；`git revert` 单个 patch 即可回到上一步（实体重放通道保证同步时可复现）。

---

## 5. 明确不做

- 不引入任何第三方 SDK / 新依赖；
- 不改 `built-in-content.js` 内 `nsfw_rules`（硬性规定 1）；
- 不为「缓存」牺牲功能（过滤/正则/世界书/记忆语义不变，只改**注入位置与时机**）；
- 不在本版做「服务端显式缓存 API」（DeepSeek 自动缓存已够，Anthropic 只加断点）。

---

## 6. 进度勾选（执行时更新）

- [ ] P0 观测层 + 挂载
- [ ] P1 patch 047 前缀稳定化
- [ ] P2 patch 049 深度锚 + 压缩时机
- [ ] P3 patch 048 Anthropic 断点 + 用量页命中率
- [ ] P4 patch 050 Agent Loop DSH 化
- [ ] P5 门禁 `tools/prefix-cache-test.cjs`
- [ ] P6 真机实测 + 全门禁 + 构建 + 文档 + 提交
