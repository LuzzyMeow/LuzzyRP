# PLAN v1.1.0 · 多模型商混用 + 思考卡雾纸玻璃 + 外观独立面板

> 会话 11 实施主文档。版本 v1.0.0 → v1.1.0（versionCode 6），上游基线 RP-Hub 1.8.9 不变。
> 用户已确认决策：①思考卡片 = **全卡雾纸玻璃**；②混用范围 = **含供应商管理器**（任意新增/改名/删除 OpenAI 兼容商）；③外观入口 = **抽屉入口 + 模态弹层**。

---

## 一、多模型商混用（patch 012）

### 1.1 模型引用体系（核心架构）

**格式**：复合引用 `providerId::bareId`。
- 分隔符取**首个** `::`；商 id（内置 4 + 动态 `p_*`）不含冒号；openrouter 模型 id 含单 `:`（如 `deepseek/deepseek-chat-v3:free`）不冲突。
- 裸 id（无 `::`）= legacy 语义：**跟随当前激活商**（与 v1.0.0 行为一致，零迁移）。

**新增 helpers（app.js，patch 012 主体）**：

| helper | 作用 |
|---|---|
| `parseModelRef(ref)` | → `{providerId: string\|null, bareId: string}`；无 `::` 或前缀非注册商 → `{providerId:null, bareId:ref}` |
| `formatModelRef(ref)` | → `{providerLabel, bareId}`；显示 `[商名] bareId`；商已删除 → `[未知]` |
| `resolveModelRequest(ref)` | → `{url, apiKey, model}`；复合引用且商已配置（有 key）→ 该商 endpoint + key；否则回落 `settings.apiUrl/apiKey`（激活商） |

**改存复合引用的字段**：`settings.model / qualityModel / balancedModel / fastModel / visionModel / uiTemplateModel`、`memorySettings.embeddingModel / classicModel`。老数据裸 id 不迁移。

### 1.2 供应商管理器

**数据模型**：
- 新增 `settings.apiProviders: [{id, name, apiUrl}]`（用户自定义 OpenAI 兼容商，任意数量；id = `'p_' + Date.now().toString(36) + 随机`）。
- key 统一存既有 `apiProviderKeys[id]`（内置 4 商不变：STA1N / DeepSeek / OpenRouter / 硅基流动，URL 为常量）。
- 统一注册表 computed `allApiProviders` = 4 内置 + `settings.apiProviders`。
- `normalizeApiProviderSettings` / `getApiProviderById` / `getApiProviderByUrl` 扩展支持动态 id；未知 id 归一回退逻辑保持。

**迁移**：老用户 `custom`/`custom2` 槽位（`settings.customApiUrl/customApiUrl2` 非空）自动导入为用户商（新 id，迁移 `apiProviderKeys` 中的 key），原两字段清空且不再出现在下拉。

**UI（设置页）**：
- 供应商下拉（index.html:1577-1677）改为 v-for `allApiProviders` + 底部「管理供应商…」入口。
- 管理弹窗：列表（内置：key 可改；用户商：名称/URL/key 可改、可删）+「+ 添加供应商」+ 每商「检测」按钮（GET `{url}/models` → 状态点 ✓/✗ + 模型数，结果写入 `providerModels` 缓存）。
- 删除商：扫描 8 个模型字段中引用该商的复合引用 → 确认框列出受影响槽位 → 确认后置回裸 id。

### 1.3 跨商合并模型列表

- `providerModels` ref：`{[providerId]: [{id,...}]}`（按商缓存）。
- `availableModels` 改为**合并视图**（computed）：对每个已配置商（有 url+key）的缓存展平为 `{id: 复合引用, bareId, providerId, providerName}`。
- `fetchModels(providerId)` 改为按商拉取原语；挂载时仅拉激活商（现状）；`openModelSelector` 时惰性补拉未缓存的已配置商；「刷新可用模型列表」= 拉全部。
- `modelTags` 族谱匹配与 `filteredModels` 搜索改用 `bareId`；搜索词同时匹配 `providerName`；嵌入目标仍锁 'embedding' 搜索词（跨商检测嵌入模型）。
- `ModelSelectorModal`（ui-components.js:822-918）：列表项渲染 `[providerName] bareId`；quickModels 三槽位同格式；选中判断按复合 id（= 存储值）。

### 1.4 请求点接入 resolveModelRequest（app.js 行号为 v1.0.0 基线）

| 请求点 | 位置 |
|---|---|
| 主聊天 | APP:5032-5040（sendMessage → requestChatCompletion） |
| 识图 recognizeChatImage | APP:3607-3653 |
| UI 模板副模型 fetch | APP:3992-3997 / 4030-4060 |
| 记忆总结 classic | APP:5403-5438（requestClassicMemoryCompletion） |
| 记忆嵌入 embeddings | APP:5811-5862（requestMemoryEmbeddings） |

### 1.5 联动逻辑防污染（关键回归点）

- `watch([apiUrl, apiKey, model])`（APP:830-847）：fast/balanced 等值比较——复合引用两侧同为存储值，等值成立；不等于时归入 qualityModel。
- `selectChatModelSlot`（APP:890-894）、`modelMode` setter（APP:855-871）：直接赋存储值，天然兼容。
- `usesThinkingCotTag(requestModel)`（APP:4469/4633/4842，含 `/deepseek/i`）：**必须先 `parseModelRef` 取 bareId 再测**，否则 DeepSeek 商名前缀会让该商全部模型误判推理风格。
- CoT watch（APP:9354-9361）同上取 bareId。

### 1.6 UI 显示同步（`[商名]` 前缀）

- 聊天页齿轮弹层三槽位（index.html:955-961）。
- 设置页模型入口按钮：聊天/识图（HTML:1702/1724）、UI 模板（2092）、嵌入（2424）、总结副模型（2457）。
- 全部经 `formatModelRef` 渲染（setup return 暴露）。

### 1.7 记忆系统双模式

- **总结模式（classic）**：classicModel 复合引用 + resolveModelRequest，无结构变更。
- **向量模式（vector）**：
  - 新分片写入时记录 `embeddingProvider`（`createVectorMemoryFromFragment`，APP:5873-5897）。
  - 检索（`selectVectorMemoriesForContext` APP:6195-6232 等）按 `(embeddingProvider, embeddingModel)` 分桶：每桶用该商/该模型**现算查询向量**（桶内自比较，余弦有效，桶间合并排序）。
  - legacy 无商字段分片 → `('legacy', model)` 桶，按激活商现算（= 现状行为）。
  - 某桶商未配置（无 key/已删除）→ 跳过该桶 + console.warn。
  - **不做批量重嵌入**（避免切商触发全量 API 费用）。

### 1.8 未提及点位一并更新（1.5 排查结论）

| 点位 | 处理 |
|---|---|
| token 用量统计（runtime-services.js:342+ recordApiUsage） | 记录增加 provider 维度，用量页模型显示加 `[商名]` 前缀 |
| 角色卡生成 iframe 同步（APP:850-852） | 改发 bareId（iframe 侧只认激活商语境） |
| 小说工坊（novel/index.html） | **不动**：自带 provider 表 + postMessage 协议；激活商为用户商时靠扩展后的 getApiProviderById 正常解析 |
| 生图模型 | 走独立网关 nai.sta1n.cn，**不改造** |

---

## 二、思考卡片全卡雾纸玻璃（扩展层 luzzy-theme.css 追加，零 patch）

- 作用域全部 `:root[data-theme="luzzy"]`（classic 零影响）；破上游移动端 kill-switch（styles.css:1778-1792）需 `:root[data-theme]` 前缀 + `!important` + `-webkit-`（会话 10 实证手段）。
- 配方（RGB 三元组 token）：
  - 整卡 `.cot-ui.native-thinking-card`：`background: rgba(var(--tw-gray-100) / .86)`（暗 `--tw-gray-200`）+ `backdrop-filter: blur(16px) saturate(1.15) !important`；边线 `rgba(var(--tw-gray-300) / .8)` 发丝线。
  - `.is-open` 蓝系 border/shadow → 暖发丝线 + 暖阴影；`.cot-header` / `:hover` / `.is-open .cot-header` 蓝底 → 半透暖面。
  - 内部 `.timeline-thinking-detail` 与 `bg-gray-50` 面板半透化，让玻璃透出。
  - live 态：珊瑚描边（`--tw-primary-500`）。
- 性能降级：`.is-live`（流式）alpha 提至 ≥.94 近实底 + blur 收窄，生成完恢复全玻璃。
- 兜底：`@supports not (backdrop-filter…)` 实底；`prefers-reduced-motion` 禁动效；动效纪律 200ms/140ms + `cubic-bezier(0.23,1,0.32,1)`、禁 scale(0)。
- 验证：CDP `Page.captureScreenshot` 在有 blur 页面挂起（会话 10 实证）→ **必须 `adb shell screencap`**；模拟器 + 真机亮暗双态。

## 三、外观独立面板（patch 013）

- `ui-components.js` AppSidebar：设置按钮旁新增「外观」按钮 → emit `open-appearance`；index.html 接线 `showAppearancePanel = true`。
- 模态弹层（index.html 内，复用 `.fixed.inset-0 > .bg-white` 雾纸弹窗样式——luzzy-theme.css:195-204 已自动主题化）：界面主题（暖幕手记/经典）+ 模式（仅 luzzy 显示）+ 界面字体 + 对话字号（一并迁入）。
- 全部绑定既有 `settings.theme/themeMode/fontFamily/fontSize` → 复用现有 watch + deep-watch 持久化 + 系统栏联动（`LuzzyBridge.setSystemBarStyle`），零新机制；弹层背后实时预览。
- 设置页原主题卡（index.html:1913-1935，patch 011 注入区）替换为「外观设置」入口卡（点击开同一面板，保持网格与可发现性）。
- app.js：`showAppearancePanel` ref + setup return 暴露。

## 四、流程与发布

1. patch 登记：`tools/patches/README.md` 更新 011（v3 说明）+ 新增 012（多模型商混用）/ 013（外观面板）。
2. `AssetExtractor.EXTRACT_VERSION` 4→5（assets 变更必 bump）。
3. DESIGN.md 追加章节：模型商徽标规范 / 外观面板 / 思考卡玻璃配方（并入 Glass 章）。
4. 模拟器（LuzzyRP_Test）走查：双商 key → 跨商混选三槽位 → 对话；向量模式跨商嵌入检索；管理器增删改+检测；外观面板；思考卡亮暗；杀进程数据保留；断网降级。
5. 五维 critique（方向/品牌/层级/动效/工程）+ ui-ux-pro-max pro-rules 对照。
6. CHANGELOG v1.1.0（新增/优化/修复/注意事项 + versionCode 6 + 基线 1.8.9）；README 版本表 v1.1.0 勾选；WORKLOG 交接。
7. `./gradlew assembleRelease` → commit/push → GitHub Release（附 APK）。

## 五、风险与对策

| 风险 | 对策 |
|---|---|
| app.js 改动面大 | 全部登记于 patch 012，逐函数小步改；裸 id 回落路径保证老数据零迁移可用 |
| 整卡 blur 流式性能 | `.is-live` 自动降级近实底；不达标回退「玻璃顶盖」（CSS 一处切换） |
| 动态商与工坊协议隔离 | 工坊不感知用户商，协议不变 |
| 同名复合引用等值比较 | 存储值即复合 id，等值天然成立；仅正则类判断强制走 bareId |
