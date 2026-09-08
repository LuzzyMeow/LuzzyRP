# PLAN · LuzzyRP v1.4.0（同步上游 1.9.2 × 剧情面板 × 沉浸模式 × 抗截断协议融合）

> 2026-09-08 会话 24 定稿。版本 v1.3.0 → v1.4.0，versionCode 11→12，上游基线 RP-Hub 1.9.1 → **1.9.2**（末次提交 `d2f2625`）。
> 本计划基于会话 24 上游合并调查（四路分析：公告/nsfw 完整性、diff 碰撞面、patch 锚点存活、结构断言），
> 按 AGENTS.md §4 SOP 展开为可执行阶段；每阶段独立 commit，回滚点明确。

---

## 背景（上游 1.9.2 调查结论）

**规模**：14 个提交（09-07 ~ 09-08），9 文件改动，+2242 / -2268 行——与 1.9.0→1.9.1 同量级的大版本。

**上游新功能**：
1. **UI 实时生成（剧情面板）**：新增 `storyPanels` 预设（`<story_panels>` 协议，随剧情插入手机界面/便签/信件等 HTML 面板）+ 渲染链；
2. **抗截断改 `output_reply` 工具协议**：`replyInTool` 参数 + api-utils 原生 toolCalls 流式解析（`readReplyDelta` 增量解码）；
3. **沉浸模式**（`settings.immersiveMode`，消息居中无头像布局）；
4. **角色卡牌组**（CharacterDeck 组件，ui-components 新增）；
5. **主动工具调用重构**：文本标签解析 → 原生 toolCalls 协议（`parseNativeActiveToolCall` / `syncNativeActiveToolUis`）；
6. 快捷面板样式密度优化（`chat-quick-panel` 重构）；
7. 开屏 entry-transition 改版（书本翻页动画）；
8. 修复：正则嵌套重复渲染 / UI 生成状态正文阻断 / 新手引导高度自适应。

**安全面（✅ 全部通过）**：
- `nsfw` 对象本体与 1.9.1 **逐字节一致**（storyPanels 是其后新增的独立预设，不在 nsfw 块内）；
- vendor/ 零变化；无新增/删除文件；
- **novel/index.html 与 runtime-services.js 零变化**（我方 007/029-novel、032/025-runtime 标记区不受影响）；
- 扩展层 ext/ 上游不触碰。

**碰撞面（⚠️ 需手工合并）**：我方 36 个 patch 中约 30 个需重放/适配，9 枚实体全部以 1.9.2 基线再生成。
高危区：**app.js（-1398 行）与 index.html（1521 行）**；上游重写了 `requestTrackedChatCompletion`、
`getTimelineSteps`、`ensureAssistantMessage`、`fetchModels`、`checkApiStatus`、`requestMemoryEmbeddings`、
`AppSidebar`、`TokenUsageView`、settings/memory 视图结构等全部我方锚点函数。

---

## 决策点（开工前用户拍板，缺一不进入阶段 1）

| # | 决策 | 选项 | 推荐 |
|---|------|------|------|
| D1 | **全屏功能去留**：上游 1.9.2 重新引入完整全屏（`toggleChatFullscreen` + 聊天页全屏按钮），与我方 patch 022（v1.2.3 用户需求下线）正面冲突 | A. 继续下线（重放 022，与 v1.2.3 用户决策一致）<br>B. 恢复上游全屏（022 退役） | **A**（用户 v1.2.3 明确要求下线，上游恢复不改变用户需求） |
| D2 | **抗截断融合策略**：上游抗截断改为 `output_reply` 工具协议（仅 Gemini 模型可见），我方 015 三协议适配器需与之融合 | A. 采纳上游协议：Gemini 抗截断走上游 `replyInTool` 原生路径，我方适配器只做协议分派（推荐，代码最少、随上游演进）<br>B. 保留我方旧文本续写链（与上游新协议并存，维护双路径） | **A** |
| D3 | **开屏保留**：上游 entry-transition 改版（书本动画），我方 027「开卷」开屏整体替换该区块 | A. 保留我方「开卷」（上游新开屏动画不出现）<br>B. 换用上游书本动画 | **A**（027 是 v1.2.3 用户选定方向，品牌资产） |
| D4 | **新功能默认值**：剧情面板/沉浸模式/CharacterDeck 的默认开关 | A. 上游默认值原样（immersiveMode=false、storyPanels 预设默认关闭、CharacterDeck 默认关闭）<br>B. 我方改默认开启 | **A**（新功能先以默认态交付，用户真机体验后再议） |

---

## 阶段 0 · 准备（会话 24 已部分完成）

1. ✅ 参考克隆已 fetch 至 `d2f2625`（gh-proxy 镜像通道；HTTPS 直连被 reset 的坑已记录）；
2. 确认工作树干净（`git status` 零未登记改动）；
3. 基线锚定纪律：**合并全程只引用 `d2f2625` 工作树**，绝不再引用 7b39385（会话 21 曾踩 pre 哈希错位）；
4. WORKLOG 追加「会话 24 开始」记录；
5. 备份当前 9 枚实体与指纹表（git 历史天然可回滚，无需额外备份）。

## 阶段 1 · 覆盖与字符串块重放（独立 commit）

1. 覆盖 9 文件至 1.9.2：`index.html`、`assets/js/{app,api-utils,built-in-content,core-utils,data-services,ui-components}.js`、`assets/css/styles.css`、`character/index.html`；
   **排除**：vendor/、fonts/、novel/、runtime-services.js（零变化）、presence-server/；
2. 重放字符串块 001-006（apply-patches.ps1）：
   - 001 品牌标题 / 002 禁用更新检查 / 004 CDN 本地化 / 005 扩展层挂载 / 006 Lora 本地字体——预期全 OK；
   - 003 已退役（027 承载）；008 字符串块已退役（v4 色板由 index 实体承载）；
   - 004 探测器 WARN 在实体应用后消除（会话 21 先例）；
3. 核对 `git diff --numstat` 无整文件伪 diff（index.html 混合行尾坑，编辑用字节级脚本按锚点插入）；
4. commit：`chore(upstream): 覆盖 RP-Hub 1.9.2（d2f2625）+ 字符串块 001-006 重放`。

## 阶段 2 · 实体手工合并（每文件独立 commit，按依赖序）

> 总原则：先小文件后大文件；app.js 逐 hunk `--reject` 手工合并，每步桌面冒烟；
> index.html 合并后必须做「关键视图容器边界」结构断言（会话 21 教训：hunks 全命中 ≠ 结构语义正确）。

### 2.1 api-utils.js（015/032/025 三 patch 融合，~347 行新版）

- 上游新增：`replyTool` 定义、`requestChatCompletionOnce` 原生 toolCalls 解析（`readReplyDelta` 增量解码、`toolSnapshot`、`finish` 校验）、`requestChatCompletion` 重试循环；
- 我方重放：
  - **015**：三协议分派入口（`requestChatCompletion` 内按 `options.protocol` 分流）+ anthropic/gemini 适配器移植 + `maxTokens`/`extraBody` 注入——适配器需兼容上游新 `onDelta({ content, reasoning, toolCalls })` 契约（D2-A：Gemini 抗截断走上游 `replyInTool` 原生路径，我方适配器只做协议分派，不再自建续写链）；
  - **032**：流式渲染间隔 60→120ms（重锚上游 `setInterval(flush, 60)` 处）；
  - **025**：`withUsageMetrics` 包装器移植（上游无此函数，适配器统一用量指标）；
- 校验：`node --check` + 三协议请求冒烟。

### 2.2 core-utils.js（009/029 + 015 调用点适配，~1045 行新版）

- 上游变化：`getImageTagRegex` 签名去掉 `requireClosing` 参数（8 行 diff）；
- 我方重放：
  - **009**：fontFamilies 改「经典」系 + 新增 luzzy（上游此区未变，直放）；
  - **029**：`apiProviderOptions` 精简仅留 DeepSeek（editable）+ `defaultApiProviderId: 'deepseek'`（上游仍 4 家内置商，需重放）；
- **015 调用点适配**：app.js 内 3 处 `getImageTagRegex(isTruncationEnabled.value)` → `getImageTagRegex()`（上游新签名），随 2.5 一并处理；
- 校验：`node --check`。

### 2.3 data-services.js（016，~1837 行新版）

- 上游变化：UI 模板校验器重写（`dynamicSamplesFor`/`validateValue` 新形态，我方零碰撞）；上下文查看器 `tool_calls` 序列化；`setUiTemplateValue` 修复；
- 我方重放：**016** 召回块 `_preventContextMerge: true`（锚点区 `finalMessages.splice` 结构变化，重放后断言 `ROLE_MEMORY_VECTOR_RECALL_OPEN_TAG` 注入块完整）；
- 校验：`node --check` + 上下文查看器「角色记忆（向量召回）」标注冒烟。

### 2.4 ui-components.js（012/014/015/019/024/025/035，~2892 行新版）

- 上游变化：`AppSidebar` 重写（props/emits 结构变化，底部仅剩「设置」按钮）、`TokenUsageView` 无折线图（props 无 chart 系）、新增 `CharacterDeck`；
- 我方重放：
  - **014/019**：侧栏底部簇（外观→设置→关于，关于置底）+ 品牌字样 LuzzyRP——重锚新版 AppSidebar 模板尾部；
  - **012**：供应商管理器组件（新版无，整体移植）；
  - **015**：模型 meta 摘要 chip + 编辑器组件（整体移植）；
  - **024**：关于页 CHANGELOG 版本分类 + 关键词搜索 + 置顶 FAB（整体移植）；
  - **025**：TokenUsageView 折线图（chart 系 props/几何辅助/模板段整体移植，重锚新版组件）；
  - **035**：管理卡两行式 + 模型数徽标 + 模型来源提示（整体移植）；
- 校验：`node --check` + 组件注册表（`window.RPHubComponents` 或等价暴露）冒烟。

### 2.5 app.js（最高危，~9391 行新版，逐 hunk 手工合并）

上游重写/恢复的锚点函数（合并时逐项核对）：

| 上游 1.9.2 形态 | 我方 patch | 合并动作 |
|----------------|-----------|---------|
| `requestTrackedChatCompletion` 改从 `settings.apiUrl` 取 url | 012/015/025 | 保留我方多商路由版（`requestModelResolved.url/apiKey` + provider/protocol 透传 + maxTokens/extraBody） |
| `getTimelineSteps` 工具列表改从 `message.toolCalls` 取 | 031 | 记忆召回节点重排进新 `ensureAssistantMessage`（盖戳）+ 时间线首位渲染（`memoryRecall` 字段） |
| `ensureAssistantMessage` 结构变化 | 031 | 同上 |
| `fetchModels` 恢复无条件调用（onMounted） | 012 | 重放懒拉取：启动仅拉激活商，选择器打开惰性补拉 |
| `checkApiStatus` 恢复 | 015 | 保留我方协议分型版 |
| `requestMemoryEmbeddings` 重写（requestJson 通道） | 026 | 保留我方 gemini `batchEmbedContents` 版 + 死供应商显式报错 + 裸引用回退 |
| 全屏功能恢复（`toggleChatFullscreen` + 监听） | 022 | **D1-A**：重放下线（移除按钮/逻辑/监听/expose） |
| `normalizeFontFamily` 白名单无 luzzy、默认 'modern' | 010 | 重放：白名单 + 默认 'luzzy' |
| 无 `applyTheme/applyThemeMode` | 011 | 重放：主题应用 + 系统栏联动（data-theme/data-mode） |
| 无 `migrateRemovedBuiltinProviders` | 029 | 重放：老用户 STA1N/OpenRouter/SiliconFlow 无损迁移 + override 合并注册表 |
| 无 `memoryManager` 全套 | 017/036 | 重放：管理器 + 036 实时联动 watch |
| 无 `providerIconCrop` 状态机 | 035 | 重放：裁剪状态机 + 保存路径 + 冲突检查修复 |
| 无 `usageChart*` 逻辑 | 025 | 重放：三粒度折线数据 + provider/protocol 补存 |
| 无 `extractMemoryRecallStamp` | 031 | 重放（见上） |
| 无 `scrollToTop` 置顶 FAB | 024 | 重放 |
| 无 021 自动统计 watch | 021 | 重放：进入设置页自动统计 |
| 无 020 检索失败 toast 外化 | 020 | 重放：注入/手动检索两处 catch + 30s 节流 |
| 无 030 关于页固定文案 | 030 | 重放：upstreamVersionLabel 整链移除 |
| `getImageTagRegex` 调用点 3 处 | 015 | 适配新签名（去参数） |
| 上游删除符号清点 | — | 全库清点上游删除符号引用（会话 21 教训：expose 残留引用致空壳） |

- 每完成一个函数区 → `node --check` + 桌面冒烟一次；
- 全部完成后 commit：`feat(upstream): app.js 1.9.2 手工合并（012/015/017/020/021/022/024/025/026/028/029/030/031/035/036）`。

### 2.6 index.html（~2853 行新版，40+ 标记重放 + 结构断言）

- 上游变化：entry-transition 改版（书本动画）、快捷面板重构、settings/memory 视图结构变化、usage 视图 props 变化、全屏按钮恢复、`response-error-text` 新增、`JSON.stringify(currentCharacter.avatar)` 修复；
- 我方重放（按锚点）：
  - **001/002/004/006**（字符串块已重放，阶段 1）；
  - **008 v4**：tailwind.config 色板 var() 化（gray/primary/blue/indigo，实体承载）；
  - **018**：head 内联主题快照脚本 + luzzy-theme.css 移入 head；
  - **027**：entry-transition 区块整体替换为「开卷」开屏（D3-A）；
  - **022**：全屏按钮/class 绑定移除（D1-A）；
  - **032**：流式分支 `{ cache: false }` 旁路（重锚新版流式区，新版 `renderMarkdown` 调用已无第 4 参）；
  - **033**：输入区过渡定向化（重锚新版 input-area/input-island/发送中止按钮）；
  - **021**：设置页残留外观入口移除 + 存储自动统计按钮区；
  - **029**：设置页 API URL 输入框放开（`isCustomApiProvider || isUserApiProvider || selectedApiProvider.editable`）；
  - **015**：自定义生图模型卡 + 模型来源提示；
  - **014**：外观独立页 + 关于独立页（settings 视图闭合后插入）；
  - **024**：关于页 CHANGELOG 分类/搜索/置顶 FAB；
  - **017**：记忆内容管理器卡（**重锚新版 memory 视图 classic 卡尾部，容器边界断言**——会话 21 教训）；
  - **012/015/035**：供应商管理器 + 编辑器 + 图标裁剪覆盖层（model-selector-modal 后插入）；
  - **025**：usage 视图折线图 props（重锚新版 token-usage-view）；
  - **005**：尾部扩展层挂载（`</body>` 前）；
- **结构断言**（parse5/jsdom 树级对比，非正则 div 计数）：memory 视图容器闭合、settings 视图闭合、017 卡在 memory 容器内、012 管理器在视图区外文档尾部、Vue 条件链断链（v-if="true" 显式断链设计）；
- 编辑纪律：字节级脚本按锚点插入（混合行尾坑），编辑后核对 `git diff --numstat`；
- commit：`feat(upstream): index.html 1.9.2 手工合并（008/014/015/017/018/021/022/024/025/027/029/032/033/035）`。

### 2.7 character/index.html（007，~4133 行新版）

- 上游变化：uiHTML 行结构变化（284 行 diff，工坊布局/抽屉改版）；
- 我方重放：**007** CDN 本地化（含 hunk2 完整 uiHTML 行 + `</`+`script>` 拼接防截断——会话 21 修复的存量缺陷形态保持）；
- 专项回归：工坊页首屏 JS 执行（v1.0.0 起曾整体不执行的回归盲区）。

### 2.8 不动项

- novel/index.html（零变化）、runtime-services.js（零变化）、vendor/、fonts/、presence-server/、ext/ 全部文件。

## 阶段 3 · 实体再生成 + 门禁校准（独立 commit）

1. 9 枚实体以 **d2f2625** 为基线再生成（`--ignore-cr-at-eol` 生成、`--ignore-whitespace` 应用）；
2. 逆向验证 9/9 PASS；
3. `tools/upstream-fingerprints.txt` 更新至 d2f2625（+character/index.html）；
4. `tools/verify-markers.ps1` 计数校准（新增/退役项：022 若 D1-A 保留则计数不变；015 调用点适配后标记数核对；032 双项核对）；
5. **verify-markers 全绿**（当前 75 PASS / 0 FAIL 基线，合并后逐项校准）；
6. 全 JS `node --check` PASS；
7. apply-patches 全 SKIP（已应用态）；
8. commit：`chore(upstream): 实体以 1.9.2 基线再生成 + 指纹表更新 + 门禁校准`。

## 阶段 4 · 桌面冒烟 + 专项回归

**挂载与品牌**：开屏「开卷」正常（D3-A）、主题首帧防闪蓝、侧栏品牌字样、外观/关于/设置入口、关于页 CHANGELOG。

**我方功能回归**：
- 供应商管理器/编辑器（012/015/035）：多商路由、三协议、图标裁剪、模型数徽标、冲突检查；
- 记忆内容管理器（017/036）：跨角色查看、编辑重嵌、实时联动；
- 用量趋势图（025）：三粒度 + provider 列；
- 记忆召回节点（031）：时间线首位渲染；
- 向量检索（020/026）：失败 toast、死供应商报错；
- 流式（032）：120ms 间隔 + LRU 旁路；
- 输入区（033）：发送键热区（elementFromPoint 向量）；
- 工坊页首屏（007 修复验证）。

**上游新功能可见性**：
- 剧情面板（storyPanels 预设启用后生成 HTML 面板渲染）；
- 沉浸模式（immersiveMode 开关切换布局）；
- CharacterDeck（角色管理卡牌组）；
- 快捷面板（新样式密度）；
- 抗截断（Gemini 模型 + output_reply 协议，D2-A 融合后）；
- 主动工具原生 toolCalls 协议（工具调用卡片/时间线）。

**数据兼容**：老 localStorage 结构可读（029 迁移幂等、011 主题字段、035 icon 字段）。

## 阶段 5 · 真机回归（小米 df97f3c4 / Android 16）

- debug 包 `install -r` 覆盖日常包（数据保留，勿装 release 包）；
- §6.2 全量：对话/角色卡导入导出/世界书/正则/记忆/生图/断网走查；
- 新功能专项：剧情面板/沉浸模式/CharacterDeck/快捷面板/抗截断；
- 性能专项：流式 10s 录制对比、开屏逐帧、玻璃档位（D1）亮暗双模式走查；
- 用户人工审阅后进入发布。

## 阶段 6 · 发布（用户真机验证通过后）

1. `app/build.gradle.kts`：versionCode 11→12、versionName "1.3.0"→"1.4.0"；
2. CHANGELOG 新增 `### v1.4.0 — 同步上游 1.9.2 × 剧情面板 × 沉浸模式 × 抗截断协议融合（上游基线 RP-Hub 1.9.2）`（新增/优化/修复/注意事项分类 + 构建结果）；
3. `node tools/gen-changelog.mjs`（自动同步 README Status 徽章与「当前版本」行 + 应用内 CHANGELOG）；
4. `./gradlew assembleRelease`（签名 + ABI 三件套）+ `assembleDebug`（真机包）；
5. git push；
6. GitHub Release v1.4.0（按 v1.3.0 排版，附三件套 APK，notes 源文件 `docs/release-notes-v1.4.0.md`）；
7. WORKLOG 收尾（完成/决策/遗留/下一步）+ AGENTS §9 快照更新。

---

## 验证清单（发版前逐项自检）

- [ ] verify-markers 全绿（计数校准后）
- [ ] 9 实体逆向 9/9 PASS
- [ ] 全 JS `node --check` PASS
- [ ] nsfw 对象与上游 d2f2625 逐字节一致（R1 门）
- [ ] styles.css 与上游逐字节一致（R2 门）
- [ ] 未登记上游文件改动 = 0（指纹比对）
- [ ] 桌面冒烟全绿（阶段 4 清单）
- [ ] 真机回归全绿（阶段 5 清单）
- [ ] CHANGELOG/README/AGENTS/WORKLOG 文档链同步

## 风险与回滚

| 风险 | 缓解 |
|------|------|
| app.js 手工合并冲突（最高危） | 逐 hunk `--reject` + 每步冒烟；每函数区独立 commit |
| index.html 容器边界错位（会话 21 教训） | 结构断言（parse5 树级对比）+ 017 卡归属专项检查 |
| 上游删除符号残留引用（会话 21 教训：expose 残留致空壳） | 合并后全库清点上游删除符号引用 |
| 015 与上游 output_reply 协议融合失败 | D2-A 方案代码面最小；失败则回退 D2-B 双路径 |
| 022 全屏决策反复 | D1 拍板后单向执行；git 历史可回滚 |
| 混合行尾整文件伪 diff | 字节级脚本按锚点插入 + `git diff --numstat` 核对 |
| 实体基线错位（会话 21 教训） | 合并全程只引用 d2f2625 工作树 |

**回滚点**：阶段 1-3 每阶段独立 commit；app.js/index.html 合并各为独立 commit；
不满意可按包回滚（commit 粒度独立，v1.3.0 先例）。

## 默认不做（本版）

- 上游新功能默认值改动（D4-A：原样交付）；
- styles.css 低频硬编码蓝收编（v1.5.0 候选，上游 611 行 diff 后清单需重扫）；
- 向量阈值滑杆、「荧光笔落笔」动效、深链、自建更新检查、Gemini/Anthropic 图像模型生图流（README 规划表顺延）；
- 剧情面板/沉浸模式的主题化定制（先以 classic 样式交付，luzzy 适配待用户真机体验后按硬性规定 9 走设计流程）。
