# Luzzy v0.8.0 — TRPG 模式设计规划

> 版本：v0.8.0
> 日期：2026-06-24
> 规则基准：D&D 5e SRD 5.2.1 (CC-BY-4.0)
> 技术栈：TypeScript / React 19 / Zustand / IndexedDB / Android WebView
> 图标来源：`doc/game-icon-pack` (12 分类, ~400+ SVG)

---

## 目录

1. [架构设计](#一架构设计)
2. [双模式设计 + 附属功能设计](#二双模式设计--附属功能设计)
3. [记忆总结 + 召回设计](#三记忆总结--召回设计)
4. [规则体系设计](#四规则体系设计)
5. [世界存档体系设计](#五世界存档体系设计)
6. [CoT + ReAct 固定思考模式](#六cot--react-固定思考模式)
7. [系统架构适配 + KV 缓存深度优化](#七系统架构适配--kv-缓存深度优化)
8. [开发批次规划](#八开发批次规划)
9. [图标包可用性](#九图标包可用性)

---

## 一、架构设计

### 1.1 UI 设计

在菜单栏内置 TRPG 模式（测试），并删除 `/trpg` 路由的 iframe 模式。取消、删除火山方舟/其他供应商的跨 CORS 代理（代理仅针对纯前端的 TRPG 网页）。所有 ICON 来源仅限 `doc/game-icon-pack/no-padding` 文件包，颜色通过 `currentColor` 继承。

页面从上到下分为四个区域。

第一个区域是模式切换栏，高度固定。左侧是游戏模式和设计模式的切换按钮，当前选中项高亮。右侧是一个小的设置齿轮图标，点击后以 Sheet 形式弹出独立的设置面板，不影响主页面。

第二个区域是剧情正文，占满剩余的全部空间，可以上下滚动。这里展示 TRPG 管线的完整输出：先是思考链节点（Think-1 意图分析 → Think-2 路径规划 → OOC 审查 → 工具调用规则裁决 → Think-4 评审），最后是 Narrator 的 7 段内容（记忆引用 / 剧情分析 / 判定汇总 / 剧情正文 / 行动选项卡片 / 状态信息 / ReAct 反思），全部包含在思考卡片内。滚动行为和聊天模式完全一致：初始只渲染最近 20 轮对话，向上滚动到顶部触发加载更多（加载 20 轮，500ms 转圈动画），新对话自动跟随到底部。

第三个区域是输入栏，紧贴在剧情正文下方、功能栏上方，高度固定。是一行横向的输入框加发送按钮。用户在此输入自然语言描述自己的行动，回车发送，Shift+回车换行，发送后自动清空。

第四个区域是底部功能栏，高度固定。只有四个图标按钮：存档、背包、角色、地图。点击任意按钮会从屏幕右侧滑出一个半屏宽度的 Sheet 面板，聊天区域同时缩小但不被遮挡。关闭 Sheet 后聊天区域恢复全宽。

**数据只读原则**。底部三个游戏数据面板（背包、角色、地图）均为只读视图（存档面板除外，存档的加载/导出/删除属于元操作），用户不可直接增删改其中任何字段。所有游戏数据的变更——物品获取与消耗、HP 与属性变化、装备更换、NPC 态度改变、地标发现——都必须通过剧情叙事触发。玩家在输入框中用自然语言描述自己的行动，GM（LLM）在 OOC 审查中判断行动的合理性，通过后引擎调用对应的工具（如 `inventory_add`、`d20_check`、`combat_resolve`、`social_resolve`、`map_discover` 等）修改数据，修改结果自动反映到各个面板中。这一原则杜绝了玩家绕过游戏规则直接修改数值的"作弊"行为，保持 TRPG 的沉浸感和规则严肃性。

进入设计模式时，布局框架不变。模式切换栏自动定位到"设计模式"。剧情正文区域展示 LLM 引导的串行对话（一次一个问题，Stage 0→1→2→3）。底部功能栏替换为设计模式专属按钮：导出世界卡、体检审查、应用预览。

#### 1.1.1 TRPG 设置面板

点击模式切换栏右上角的设置齿轮图标，从右侧滑出一个 Sheet 面板，仅放置一个**模型选择器**。

TRPG 模式使用独立的模型选择，不与自由聊天模式共享。下拉列表中列出全局已配置的所有供应商及其模型（如 `deepseek / deepseek-v4-pro`、`deepseek / deepseek-v4-flash`），默认跟随全局默认模型。选中即生效，存入 IndexedDB，下次启动自动加载。

### 1.2 上下文滚动预览

采用和聊天模式相同的 UI 策略：仅查看近 20 轮对话内容（40 条消息），再往上滑动要刷新才可继续查看 20 轮。该策略仅针对前端显示。

**当前实现参考**（`chat.tsx:175-200`）：

```typescript
const PAGE_SIZE = 40;                                  // 20 轮 = 40 条消息
const [displayCount, setDisplayCount] = useState(PAGE_SIZE);
const visibleMessages = messages.slice(-displayCount); // 仅渲染尾部

// 滚动到顶部触发加载: scrollTop < 50 → displayCount += 40 → 500ms 转圈动画
const handleScroll = (e) => {
  if (target.scrollTop < 50 && displayCount < messages.length && !isLoadingMore) {
    setIsLoadingMore(true);
    setTimeout(() => {
      setDisplayCount(prev => Math.min(prev + PAGE_SIZE, messages.length));
      requestAnimationFrame(() => { /* 保持滚动位置 */ });
      setIsLoadingMore(false);
    }, 500);
  }
};
```

TRPG 模式直接复用 `displayCount`/`visibleMessages` 逻辑，不需要额外开发。

### 1.3 API 请求路径设计

独立请求（非聊天模式下的管线），不包含全局的角色卡、预设、工具、提示词，避免上下文污染。使用独立的模型配置（在设置面板中单独选择，不与自由聊天模式共享）。

TRPG 管线的完整请求流程分为三个阶段。

第一阶段是预执行。引擎同时做三件事：将角色卡 JSON 注入上下文、将世界卡全部模块全量注入上下文（追加到共享前缀层）、以及召回历史记忆（包括剧情摘要全量注入和向量记忆 Top-8 检索）。三件事并行执行，不阻塞。

第二阶段是单次 API 请求，使用 TRPG_GM_PRESET_CONTENT 作为系统提示词。请求中模型的 reasoning_content 输出 Think-1 意图分析和 Think-2 路径规划，然后通过 tool_calls 调用本地规则引擎工具（d20_check、roll_damage 等），最终 content 输出 Narrator 节点的 7 段内容（全部包含在 CoT 思考卡片内）。

第三阶段是后处理，在收到模型完整响应后依次执行：写入 A 级摘要、写入向量记忆、更新 GameState 中的 HP/位置/轮次等字段、最后持久化到 IndexedDB。

当前聊天管线和 TRPG 管线共用底层 API Client 的 stream 能力，但上层的 context builder 完全独立，互不感知。当 `session.mode === 'trpg'` 时走 buildTrpgContext 分支，否则走 buildContext 分支。

buildTrpgContext 从零构建 TRPG 专用的系统提示词和消息列表：注入 TRPG_GM_PRESET_CONTENT 作为系统提示词、注入 D&D 角色卡 JSON（六维属性加 18 项技能）、注入世界卡全部模块全量、注入 A/B/C 三级摘要全量、注入向量记忆 Top-8 检索结果、注入近 8 轮对话上下文、以及 buildTrpgToolDescriptions 动态生成的 d20/combat/social 工具描述。

而 buildContext 则走原有的自由聊天路径：注入 LUZZY_PRESET_CONTENT、注入角色人格描述文本、注入世界书 keyword-match 匹配结果、注入记忆召回、注入用户档案、以及 buildToolDescriptions 动态生成的 memory-recall/world-recall/anysearch 工具描述。

### 1.4 d20 规则裁决

d20 规则裁决的核心公式是：d20 + ability_modifier + proficiency_bonus（若该技能熟练）与 DC 比较。裁决流程按固定顺序执行：第一步，对世界规则的条件树求值，获取 DC 修正、优劣势和阻断标记；第二步，TypeScript 本地执行 d20 检定（Math.random，不信任 LLM）；第三步，OOC 七项审查；第四步，根据 Think-1 输出的 category 分发给对应子系统进行裁决（combat/social/explore/inventory/rest/info/meta 之一）；第五步，原子更新角色状态（HP、条件、位置等）并推进游戏内时间、更新 NPC 在场状态。注意 category 是个体行动的分类标签，而 TrpgGameState.phase（explore/combat/social）描述的是游戏整体的运行模式——两者独立但关联：category=combat 的行动会触发 phase 切换到 combat，category=rest 仅当 phase 为 explore 时才被允许。

**关键原则**：bonus（技能加值）永远由引擎计算。LLM 选择 `skill` 和评估 `dc`，但 `bonus` 由 TypeScript 从角色属性计算，覆盖 LLM 给出的任何值。LLM 不得自行计算最终加值、不得编造掷骰结果。

#### OOC 七项审查清单

| 编号 | 审查项 | 说明 | 处理方式 |
|:----:|--------|------|----------|
| 1 | **元游戏/第四面墙破坏** | 玩家引用规则书、骰子、LLM、系统提示等游戏外知识 | soft_warn：在叙事中淡化或转移 |
| 2 | **玩家知识越界** | 玩家使用角色不应知道的信息（如未探索地图、未见面 NPC 的名字） | hard_block：本轮行动不生效，OOC 提示 |
| 3 | **世界一致性破坏** | 行动违反已建立的世界法则或物理/社会规则 | hard_block：拒绝行动并说明原因 |
| 4 | **重复无效行动** | 玩家连续尝试同一已被明确拒绝或无效的行动 | soft_warn：提醒后果已发生，引导前进 |
| 5 | **内容分级越界** | 叙事内容超出世界卡 `content_rating` 设定。当分级为 unrestricted（默认）时，此审查项自动 pass | 非 unrestricted 时 soft_warn；unrestricted 时 pass |
| 6 | **绕过机制** | 试图跳过检定、强制成功、或要求 LLM 直接修改状态 | hard_block：坚持引擎裁决 |
| 7 | **角色扮演崩坏** | 行动严重脱离已建立的角色性格/目标，且无叙事铺垫 | soft_warn：在叙事中以角色内心冲突呈现 |

每项审查返回 `hard_block`、`soft_warn` 或 `pass`。存在任意 `hard_block` 时 `action` 置为 `"blocked"`；仅 `soft_warn` 时 `action` 可为 `"resolved"` 或 `"partial"`。

OOC 审查的执行分工：审查项 1（元游戏）、2（知识越界）和 7（角色扮演崩坏）需要语义理解，由 LLM 在 Think-3 阶段协助判断（模型在 reasoning_content 中评估并返回审查结论）。审查项 3（世界一致性）、4（重复无效行动）和 6（绕过机制）由 TypeScript 代码检查（对比世界规则条件树、检测重复输入模式、验证是否绕过检定流程）。审查项 5（内容分级越界）当 content_rating 为 unrestricted 时自动 pass，无需任何检查。

#### 自我评分（Think-4）

| 维度 | 权重 | 评分逻辑 |
|------|:---:|---------|
| fairness（公平性） | 0.35 | DC 是否匹配角色等级：`max(0, 10 - \|estimated_dc - (10 + prof_bonus)\|)` |
| consistency（一致性） | 0.25 | OOC 审查 + 世界规则合规：`max(0, 10 - hard_blocks×5 - soft_warns×2)` |
| consequence（后果性） | 0.25 | 状态是否有实质变化。`state_changes` 为 10 个布尔字段中为 True 的个数：`min(10, state_changes×2)` |
| coherence（连贯性） | 0.15 | 行动是否符合当前叙事阶段（如战斗中不能长休） |

总分 = `fairness×0.35 + consistency×0.25 + consequence×0.25 + coherence×0.15`

判定：`≥6.0 → pass | ≥3.0 → retry | <3.0 → warn`

---

## 二、双模式设计 + 附属功能设计

### 2.1 游戏模式

游戏模式是核心运行模式。玩家输入自然语言行动描述，引擎完成完整管线后返回格式叙事。

模式切换通过在 Session 类型上增加 mode 字段实现，类型为 `'free' | 'trpg'`。

两种模式在六个维度上有不同的行为。在系统提示词方面，自由模式使用 LUZZY_PRESET_CONTENT，TRPG 模式使用独立的 TRPG_GM_PRESET_CONTENT。在角色数据方面，自由模式使用描述文本加人格加对话示例，TRPG 模式使用结构化的 D&D 角色卡（六维属性、18 项技能、HP、AC、装备等）。在工具列表方面，自由模式使用 memory-recall、world-recall、anysearch，TRPG 模式在这些基础上增加 d20_check、roll_damage 和 combat 系列工具。在上下文注入方面，自由模式注入角色定义加世界书匹配结果加记忆召回，TRPG 模式注入角色卡 JSON 加世界规则模块加 A/B/C 全量摘要加向量记忆 Top-8 加近 8 轮对话上下文。在输出格式方面，自由模式为自由文本，TRPG 模式为包含在 CoT 思考卡片内的 7 段内容。在后处理方面，自由模式调用 extractMemory 写入 IndexedDB，TRPG 模式执行状态更新、A 级摘要写入、向量记忆写入和 GameState 持久化。

### 2.2 设计模式

世界卡交互设计模式支持导入和交互式创建世界卡。采用**串行对话式**交互，一次只问一个问题，由 LLM 引导用户逐步构建世界卡。每完成一个阶段，中间产物写入世界卡元数据的 `stages` 数组，确保设计过程可回溯、可中断续做。

设计模式的最终产物是一张完整的 WorldCard（见 2.2.1 节），通过 `saveWorldCard` 写入 IndexedDB 的 worldCards store。写入成功后用户可选择立即创建存档进入游戏，或返回世界卡库稍后使用。

#### Stage 0：欢迎与方向选择

Stage 0 启动时，引擎首先展示欢迎语和四个起始方向。欢迎语为："欢迎来到设计模式，你想从哪个角度出发？在这里，你可以设计一张属于自己的世界卡。我会一步步引导你——先确立一个大方向，再围绕它逐层展开。" 四个方向分别是：01 扮演一个角色（PERSONA），例如修仙弟子、高考刚结束的少年、末日里的一只猫；02 构建一个世界（WORLD），例如修仙宇宙、雨夜的赛博朋克、停战翌日的边境小镇；03 我有一个画面（SCENE），直接写出脑中画面即可；04 随便来一个（IMPROV），暂无头绪时由引擎起头。

用户也可跳过选择，直接写一段描述（粘贴已有设定、写出脑中的画面、或随便聊聊），引擎会从中提取关键信息进入 Stage 1。

Stage 0 的产出写入 `stages[0]`：用户选择的方向枚举值（PERSONA/WORLD/SCENE/IMPROV）或自由文本原文。

#### Stage 1：五维框架采集

采集完毕后产出五维框架对象，写入 `stages[1]`，并填充世界卡 metadata 的 framework 字段（含 context_world 世界观、context_rules 规则、context_chars 角色阵营、context_timeline 时间线锚点、style_guide 叙事风格）和 worldTerms（货币名、纪年名、历法单位、地标层级名）。

| 步骤 | 采集维度 | 关键问题 | 对应 WorldCard 字段 |
|:----:|---------|---------|-------------------|
| 1 | 基调与类型 | 「基调与类型？」（轻松日常/冒险探索/暗流政治/末世生存/你来决定） | `metadata.framework.context_world` |
| 2 | 核心设定 | 用户自由描述世界核心 | `metadata.framework.context_rules` |
| 3 | 时间锚点 | 「世界卡的此刻定在哪个瞬间？」（梦醒时分/进行时/双世界交替/你来决定） | `metadata.frozenMoment` |
| 4 | 面板字段-状态栏 | 「状态栏想额外追踪什么？」 | `panelFields.panelStatus` 自定义组 |
| 5 | 面板字段-NPC | 「NPC 档案想加哪些追踪字段？」 | `panelFields.panelNpc` 自定义字段 |

**交互规则**：
- 每次只问一个问题，提供 3-4 个选项 + 「✱ 你来决定」选项
- 用户选择「你来决定」时，由 LLM 根据已有上下文自主决策
- 用户可随时直接输入自由文本，覆盖选项
- LLM 在每次用户回答后，先复述理解，再问下一个问题

#### Stage 2：骨架生成与精修

LLM 根据 Stage 1 的五维框架，按顺序批量生成世界卡的各个设定模块。每生成一个模块，将结果写入 WorldCard 对应字段（可在生成过程中随时查看和精修）。全部生成完毕后将骨架快照写入 `stages[2]`。

| 步骤 | 生成内容 | 数量要求 | 对应 WorldCard 字段 |
|:----:|---------|---------|-------------------|
| 1 | 地理实体 | ≥3 个，每个含 6 章节 + ≥3 sites | `worldSetting[]` |
| 2 | Prompt 模块 | 4 个必需模块 + module_meta | `promptModules` |
| 3 | 面板字段 | panel_status + panel_npc | `panelFields` |
| 4 | 开场白 | 150-280 字，in-medias-res | `openingGreeting` |
| 5 | 角色数据库 | 3-15 个角色 | `characterDatabase[]` |
| 6 | 世界时间线 | ≥10 事件，严格按时间升序 | `worldTimeline[]` |
| 7 | v2.1 扩展块 | laws / mods / artifacts / backgrounds | `laws[]` / `mods[]` / `artifacts[]` / `characterBackgrounds[]` |

#### Stage 3：审查与交付

引擎对 Stage 2 生成的世界卡运行世界卡审查（见下方检查清单），输出通过/警告/错误的检查报告。用户审批后，引擎调用 `saveWorldCard` 将完整 WorldCard 写入 IndexedDB 的 worldCards store，世界卡进入"已交付"状态。此时用户可选择"创建新存档"直接开始游戏，或"返回世界卡库"稍后使用。交付信息写入 `stages[3]`（含审查分数、通过时间戳）。

17 项自动检查：

| 检查段 | 编号 | 检查内容 | 级别 |
|:------:|:---:|---------|:----:|
| B | B6 | world_setting 缺少 _summary | warning |
| C | C6 | modules.init 缺少推荐开场标准行 | warning |
| D | D-dialogue | 角色 sms 示例不足 4 条 | warning |
| D | D11 | 实体/势力无对应角色（阵营覆盖失衡） | warning |
| D | D-rels-bidir | 单向关系未补全反向 | warning |
| E | E6 | 事件时间顺序异常 | warning |
| H | H8 | panel_status money._currency 与 currency_name 不一致 | **error** |
| I | I5b | opening_greeting 时间超出时间线末段 | warning |
| K | K3b | 模块内容出现与 calendar_era 不同的纪年名 | warning |
| K | K10 | extra_char_fields 与 panel_npc 字段不一致 | warning |
| ... | ... | （其余 7 项结构/引用/逻辑检查） | warning/error |

完整 17 项包含：B 段 world_setting 结构完整性（B1-B6 共 6 项）、C 段 prompt_modules 合规性（C1-C6 共 6 项）、D 段角色数据库质量（D-dialogue/D11/D-rels-bidir 共 3 项）、E 段时间线合理性（E6 1 项）、H 段面板字段一致性（H8 1 项）、I 段开场白时效性（I5b 1 项）、K 段内容一致性（K3b/K10 共 2 项）。减去上表中已列出的项目，剩余约 7 项分布在 B/C 段的结构和引用检查中。

**通过标准**：
- `fatal = 0` 且 `errors = 0`：体检通过，可交付
- 仅剩 `warnings`：可交付，但建议修复
- 存在 `errors`：必须修复后重新体检

#### 2.2.1 世界卡数据模型

设计模式最终产出的世界卡是一个独立的持久化实体，存储在 IndexedDB 中，与存档解耦。一张世界卡可以承载多个存档（同一世界观下的不同角色、不同起点、不同分支）。

世界卡采用模块化结构，每个模块负责一个维度的世界设定。在预处理阶段，世界卡的全部模块全量注入 prompt，不区分条件或层级。全部模块统一追加到共享前缀层，与 D&D 5e 规则速查并列，作为 GM 的完整世界知识库。世界卡不变则共享前缀层缓存稳定命中。

世界卡的所有字段均为可选项，空白世界卡允许用户从零开始全自由游戏（仅依赖 D&D 5e SRD 基础规则）。当存在对应字段时，引擎根据注入规则将其注入 prompt。

世界卡的数据结构分为必选元数据和可选设定模块两部分。元数据包含 cardId、title（用户命名）、description（一句话概述）、contentRating（内容分级，默认为 unrestricted）、author、createdAt、updatedAt、以及 stages 数组记录设计模式四阶段的中间产物（方向、框架、骨架等）。

设定模块包含八个领域，在预处理阶段全量注入。worldSetting 定义地理实体数组，每个实体包含名称、六个章节（here_now 当前状态、social_fabric 社会结构、order 秩序与法律、world_law 自然法则、rhythm 日常生活节奏、narrative_core 叙事核心），以及若干子地点 sites。characterDatabase 定义世界中的 NPC 数组，每个 NPC 包含名称、种族、身份、态度模板、对话风格 tone 和对话示例 examples、常出没地点、作息 routine。worldTimeline 定义事件数组，按游戏内时间升序排列，每个事件包含时间标签、标题、描述、涉及地点和角色、对世界状态的影响。promptModules 包含 coreWorldMechanics（世界法则）、init（开场引导）、narrativeBase（叙事风格规范）、npcGen（NPC 面板输出规范），四个模块全部注入。panelFields 定义状态面板和 NPC 面板的自定义追踪字段。扩展块包含 laws（世界法则数组，每条含名称、效果描述、触发条件）、mods（机制数组，如特殊掷骰规则、自定义技能）、artifacts（关键道具数组，含名称、描述、获取条件、效果）、characterBackgrounds（预设角色背景数组，供玩家创建角色时选择）。此外还有 metadata 子对象存放 ASG v2 版本号、五维框架摘要、术语表（货币名、纪年名、历法单位、地标层级名）和冰冻时刻（世界卡锚定的"此刻"时间点）。以上全部内容在 buildTrpgContext 预处理阶段拼接为一个完整的世界卡文本块，追加到共享前缀层，全量注入每一轮 API 请求。

#### 2.2.2 世界卡存储

世界卡存储在 IndexedDB 数据库 luzzy_trpg 的 worldCards store 中，以 cardId 为键。所有世界卡在此平铺存储，不嵌套分层。

世界卡的读取流程：应用启动时从 IndexedDB 加载全部世界卡元数据列表（仅 cardId、title、description、updatedAt，不含模块全文）到内存，用于世界卡选择器的列表展示。当用户选中某张世界卡进入游戏或查看详情时，再按 cardId 读取完整数据（含所有模块）。

与世界卡关联的存档读取：存档通过 worldCardId 字段关联世界卡。打开存档列表时，可按 worldCardId 分组展示——先显示世界卡标题作为分组标题，下方列出该世界卡下的所有存档。

世界卡的写入：设计模式完成后调用 saveWorldCard 写入完整数据到 IndexedDB。用户也可以通过导入功能将外部 ASG v2 格式的 JSON 世界卡文件解析后写入。

世界卡的删除：删除世界卡时需级联检查——如果该世界卡下存在存档，弹出确认对话框提示"该世界卡下有 N 个存档的关联将被解除"，用户确认后删除世界卡，同时将所有关联存档的 worldCardId 字段置空（存档本身不删除，变为无世界卡的独立存档，仍可正常游玩只是丢失世界设定上下文）。

世界卡的导出：用户可将世界卡导出为 JSON 文件。导出格式为完整的 WorldCard 对象序列化，包含所有模块。这既用于备份，也支持跨设备迁移（通过 Android share API）。

#### 2.2.3 世界卡选择器

新建存档时，用户首先看到世界卡选择器。界面为一个可滚动的网格列表，每个世界卡显示为一张卡片：世界卡标题、一句话描述、内容分级标签、创建时间。列表顶部有一个"不使用世界卡"选项（即空白世界卡，仅使用 D&D 5e 基础规则）。底部有一个"导入世界卡"按钮，点击后触发文件选择器，选择 JSON 文件解析为 WorldCard 并存入 IndexedDB。用户也可以点击"设计新世界卡"按钮，直接跳转到设计模式的 Stage 0。

当用户选择了一张世界卡后进入角色创建环节（见 2.5 节），角色创建完成后生成开场叙事并正式开始游戏。

### 2.3 存档

#### 数据模型

每个存档隶属于一张世界卡（或为空）。一张世界卡下可以有多个存档，形成一对多关系。

```typescript
interface SaveSlot {
  saveId: string;               // uuid
  title: string;                // 用户可编辑
  worldCardId: string | null;   // 所属世界卡 ID（null = 无世界卡）
  gameState: TrpgGameState;     // 完整游戏状态
  character: TrpgCharacter;     // 主角色
  npcs: GameNpc[];               // 运行时 NPC 状态列表
  messages: TrpgMessage[];      // 对话历史（仅 TRPG 格式）
  aSummaries: ASummaryEntry[];  // A 级记忆
  bSummaries: BSummaryEntry[];  // B 级记忆
  cSummaries: CSummaryEntry[];  // C 级记忆
  createdAt: number;
  updatedAt: number;
  pinned?: boolean;
}
```

存档不再内嵌完整的 WorldCard 对象，改为通过 `worldCardId` 外键引用。运行时需要世界卡数据时从 IndexedDB 的 worldCards store 按 ID 读取。这样一张世界卡被多个存档引用时不产生数据冗余，世界卡更新后所有关联存档自动生效。

**TrpgCharacter 类型**：

```typescript
interface TrpgCharacter {
  charId: string;               // 唯一 ID
  name: string;                 // 角色名
  race: string;                 // 种族
  class: string;                // 职业
  level: number;                // 等级 1-20
  abilities: {                  // 六维属性
    str: number; dex: number; con: number;
    int: number; wis: number; cha: number;
  };
  hp: { current: number; max: number };
  ac: number;                   // 护甲等级
  proficientSkills: string[];   // 熟练技能
  expertiseSkills: string[];    // 专精技能
  conditions: string[];         // 状态
  inventory: InventoryItem[];   // 物品栏
  equipment: {                  // 装备
    weapon?: string;
    armor?: string;
    shield?: string;
  };
  spellSlots?: Record<number, number>; // 法术位
  classFeatures: string[];      // 职业特性
  xp: number;                   // 经验值
  background: string;           // 背景故事
  alignment: string;            // 阵营
}
```

**TrpgGameState 类型**：

```typescript
interface TrpgGameState {
  saveId: string;               // 所属存档 ID
  roundNumber: number;          // 当前游戏轮次
  activeCharacterId: string;    // 当前活跃角色
  currentLocation: string;      // 当前位置
  phase: 'explore' | 'combat' | 'social';
  combat?: CombatState;         // 战斗状态（非战斗时为空）
  world: Record<string, unknown>; // 世界状态
  quests: Quest[];              // 任务列表
  time: {                       // 游戏内时间
    day: number;
    hour: number;
    calendarEra: string;
  };
  factionRelations: Record<string, number>; // 势力关系
  npcs: GameNpc[];              // 运行时 NPC 状态
  locations: GameLocation[];    // 已知地标列表（含 archived 标记）
}

interface GameNpc {
  npcId: string;                // 对应世界卡 characterDatabase 中的 ID
  name: string;
  gender: string;
  age: number;
  presence: 'present' | 'absent'; // GM 动态更新在场/离场
  attitude: 'hostile' | 'unfriendly' | 'neutral' | 'friendly' | 'helpful';
  hp: { current: number; max: number };
  revealedFields: string[];     // 已通过剧情解锁的字段名数组（JSON 可序列化）
  customFields: Record<string, string>; // 世界卡 panelNpc 定义的自定义字段值
}

interface GameLocation {
  locationId: string;           // 对应世界卡 worldSetting 中的地点 ID
  name: string;
  status: 'current' | 'visited' | 'unexplored' | 'hostile' | 'archived';
  archived?: boolean;           // GM 判定不可达时标记为 true
  archiveReason?: string;       // 移除原因
  exploredRatio?: number;       // 探索完成度 0-100
}

interface InventoryItem {
  id: string;
  name: string;
  type: 'weapon' | 'armor' | 'consumable' | 'quest' | 'misc';
  quantity: number;
  description: string;
  damageDice?: string;          // 武器："1d8"
  damageType?: string;          // 武器："slashing"
  acBonus?: number;             // 护甲：+2
  effect?: string;              // 消耗品效果描述
  isQuestItem?: boolean;        // 任务物品不可丢弃
}
```

#### 存档分组展示

打开存档 Sheet 面板时，存档列表按世界卡分组展示。每组有一个可折叠的分组标题，标题为该世界卡的名称，右侧标注该组内的存档数量。"无世界卡"的存档归入一个独立的默认分组。每个存档卡片显示存档标题、轮次、角色 HP 摘要、等级、更新时间，以及加载/导出/删除三个操作按钮。

#### 操作流程

新建存档的完整流程为：点击新建按钮 → 弹出世界卡选择器（见 2.2.3） → 选择世界卡或跳过 → 创建或选择 D&D 角色 → 引擎根据世界卡的 opening_greeting 模块生成开场叙事 → 进入游戏。

加载存档的流程为：点击存档卡片 → 从 IndexedDB 读取完整 SaveSlot → 如果 worldCardId 非空则按 ID 从 worldCards store 读取世界卡数据 → 恢复 GameState、角色、消息历史和记忆 → 进入游戏。

导出存档的流程为：将 SaveSlot 序列化为 JSON。如果 worldCardId 非空，同时将关联的世界卡数据内嵌到导出文件中，确保导出文件自包含、导入到另一设备时不丢失世界设定。

删除存档的流程为：二次确认后删除 saves store 中该条记录，不级联删除世界卡（世界卡独立存在）。

### 2.4 背包

背包 Sheet 面板从右侧滑出，仅供查看角色当前持有的物品，**不可由用户直接增删改**。所有物品的获取、消耗、丢弃、装备变更都必须通过剧情叙事触发——玩家在输入框中描述自己的行动（如"我从地上捡起那把长剑""我喝下一瓶治疗药水""我把破损的盾牌丢进河里"），GM 在 OOC 审查阶段判断该行动的合理性，通过后由引擎调用 `inventory_add`、`inventory_remove`、`inventory_use`、`inventory_equip` 等工具修改角色数据，修改结果自动反映到背包视图中。

面板上半部分是可滚动的物品列表，每件物品显示名称、类型标签（武器/护甲/消耗品/任务物品/杂物）、数量和快捷描述（武器标注伤害骰如"1d8 挥砍"，护甲标注 AC 值，消耗品标注效果简述）。任务物品有特殊标记表示不可丢弃。面板顶部显示四种货币余额：铜币 CP、银币 SP、金币 GP、铂币 PP。货币同样不可由用户直接修改，必须在剧情中通过交易、战利品或报酬等形式由 GM 审核后变更。

背包里的物品数据对应 `TrpgCharacter.inventory` 字段。每件物品是一个 `InventoryItem` 对象，包含名称、类型、数量、描述，以及与类型对应的属性（武器有伤害骰和伤害类型、护甲有 AC 加值和类型、消耗品有使用效果描述）。物品数据由引擎维护，用户仅可查看。

### 2.5 角色

角色 Sheet 面板从右侧滑出，顶部有两个分类切换标签："自己"和"NPC"。用户点击标签切换查看对象。所有字段不可由用户直接编辑——属性提升、HP 变化、装备更换、NPC 态度和在场状态等，都必须通过剧情叙事触发，经 GM 的 OOC 审查和引擎的规则裁决后自动更新。

**自己**分类展示当前玩家角色的 D&D 角色卡（只读）。顶部显示角色名称、种族、职业和等级。紧接着一行是 HP 当前值/最大值、AC 护甲等级、以及熟练加值。第三行显示当前游戏内时间：日期、时刻（黎明/上午/下午/黄昏/前半夜/深夜/后半夜）和纪年名称（如"犬历328年7月15日 08:30 上午"）。再下一行是六维属性的两排展示：第一排力量 STR、敏捷 DEX、体质 CON，第二排智力 INT、感知 WIS、魅力 CHA，每个属性后标注对应的调整值（如 STR 16(+3)）。再下一行列出所有熟练技能名称。

**NPC**分类展示所有已知 NPC 的列表。每个列表条目显示 NPC 名称、性别、年龄、以及在场状态（在场/离场）。在场状态是一个由 GM 动态更新的变量——当 NPC 进入或离开当前场景时，引擎通过工具调用自动切换该状态。用户可点击任意 NPC 条目打开详细的 NPC 卡片。

**NPC 卡片**以弹窗形式展示该 NPC 的完整信息。卡片内的字段由世界卡的 `panelFields.panelNpc` 和 `characterDatabase` 中的角色定义共同决定——不同世界卡可能定义不同的 NPC 字段（如身份、势力归属、战斗能力、对话风格等）。关键设计是**信息渐进解锁**：玩家刚认识该 NPC 时，卡片中仅显示已通过剧情公开的信息（如外貌、姓名、初步态度）。随着剧情推进，当玩家在对话中获知该 NPC 的背景秘密、当 NPC 在战斗中使用特殊能力、当玩家成功通过洞悉或调查检定，GM 判定哪些新信息对玩家可见并解锁对应字段。未解锁的字段在卡片中显示为"???"或灰暗遮罩。此解锁状态存储在 `TrpgGameState` 中每个 NPC 对应的 `revealedFields` 集合里。

### 2.6 地图

v0.8.0 不做可视化地图，采用卡片列表形式呈现地标信息，兼顾美观与实用性。

**整体布局**。地图 Sheet 面板从右侧滑出，顶部固定一行显示当前位置和游戏内时间，作为"你在这里"的简洁锚点。下方是可滚动的地标卡片列表，每张卡片代表一个已知地点。卡片按探索时间倒序排列，最新发现的在最上方。

**地标卡片设计**。每张卡片使用 muted 背景、圆角 8px、左侧有一条 3px 宽的竖线颜色表示状态（绿色=当前所在、蓝色=已探索可返回、黄色=未探索、红色=敌方控制/危险区域）。卡片主体包含三行信息：第一行是地标名称（加粗），右侧标注与当前位置的距离描述（如"东北方向半日路程""就在北门哨塔外"）。第二行是地标的简要特征描述（"针叶林环绕的边境哨站，积雪覆盖"），来自世界卡 worldSetting 的摘要。第三行是一排微型标签：地形类型、已知势力归属、是否有未探索区域。卡片底部有一个可折叠的详情区域，展开后显示该地标的完整描述、已知的子地点列表、和关联 NPC。

**层级连线**。列表中的地标并非孤立排列。每个地标卡片左侧的竖线颜色条同时作为层级指示器——子地点（site）比其父地标（entity）向右缩进 16px，形成视觉上的父子层级关系。同一父地标下的多个子地点共享同一条纵向虚线连接，从视觉上表达"这些地点属于同一个区域"。

**状态颜色语意**。绿色表示角色当前所在位置。蓝色表示角色曾到访且可以随时返回。黄色表示通过 NPC 对话、地图碎片、传闻等方式已知名称但尚未亲自探索。红色表示该地点处于敌对势力控制或存在已知危险。灰色表示该地点已不可返回（见自删除机制）。

**自删除机制**。当剧情推进导致某个地点无法再前往时——例如唯一的桥梁被炸毁截断了通往某区域的道路、角色登船远航离开了大陆、或某地标所在的维度/位面入口永久关闭——GM 在叙事中判定该地点不再可达，引擎将该地标卡片从列表移除。被移除的地标并非删除数据，而是在 `TrpgGameState` 中标记 `archived: true` 并记录移除原因，仅在前端列表视图中不再显示。已存档的地标仍然作为玩家的"已知地理知识"保留，可在需要时通过 GM 工具查看完整历史。如果剧情后续出现新的路径通往该区域（如角色学会了飞行法术、发现了密道），GM 可通过工具调用将该地标的 `archived` 恢复为 `false`，使卡片重新出现在列表中。

---

## 三、记忆总结 + 召回设计

### 3.1 记忆总结系统

记忆系统即后处理阶段进行处理写入。采用 **A/B/C 三级摘要架构**，按时间粒度逐级压缩，分别承担"情节记录—语义聚合—史诗篇章"三种职责。所有摘要均以结构化 JSON 持久化。

每一条 A/B/C 摘要必须满足「盲续测试」：即使模型完全不看历史上下文，仅凭这一条摘要 + 当前角色状态 JSON，也能无缝续写下一段剧情，不出现人物身份断裂、地点跳跃、动机缺失、伏笔遗忘或感官锚点丢失。

#### 续写五要素

1. **场景锚点**：当前地点、时间、天气/光照、关键感官细节（至少 2 种感官）
2. **人物状态**：在场角色的即时情绪、HP/状态、当前目标、对玩家的态度
3. **动机张力**：本轮/本段的核心冲突是什么、谁想要什么、阻碍是什么
4. **未决线索**：尚未回收的伏笔、悬而未决的抉择、即将触发的威胁
5. **衔接钩子**：最后一句必须是一个「可接续的动作或对话节点」，而非封闭式结尾

#### 三级结构

| 层级 | 生成频率 | 文件命名 | 保留策略 |
|------|:-------:|----------|----------|
| **A 级摘要** | 每轮 | `A00001.json` ~ `A99999.json` | 保留最近 **50** 个 |
| **B 级摘要** | 每 **25** 轮压缩一次 | `B001.json` ~ `B999.json` | 保留最近 **10** 个 |
| **C 级摘要** | 每 **200** 轮压缩一次 | `C001.json` ~ `C999.json` | **永不删除** |

#### A 级摘要（情节摘要）

**精度要求**：
- **字数下限 80 字**：低于 80 字的摘要无法容纳续写五要素，视为不合格，触发重写
- **必须包含具体名词**：不得使用「某个 NPC」「某个地点」等模糊指代
- **必须包含数值结果**：若本轮涉及检定，必须写出 `{skill} DC{dc} d20({raw})+{bonus}={total} → {outcome}`
- **衔接钩子非封闭**：最后一句不得是句号结尾的陈述句

**自动化校验**：

| 校验项 | 规则 | 失败处理 |
|--------|------|---------|
| 字数下限 | `len(summary) >= 80` | 重写（调用 LLM 补全续写五要素） |
| 场景锚点非空 | 含地点+时间+≥2 感官词 | 重写 |
| 衔接钩子非封闭 | 不以「。！.」结尾 | 重写 |
| 实体具体性 | 无「某个」「一些」「那个」等模糊指代 | 重写 |
| 数值完整性 | 若 `action` 含检定，必须出现 `DC` 和 `d20` | 重写 |

校验失败时，引擎将 Think-3 结果 + 叙事输出重新交给 LLM，要求按续写五要素重写摘要。同一轮最多重写 2 次；2 次后仍不达标则写入当前版本并标记 `quality_flag = "degraded"`。

**重要性评分维度**：

| 维度 | 权重 | 说明 |
|------|:---:|------|
| 战斗状态 | 0.30 | 进入/退出战斗、HP 归零、关键命中/失败 |
| 关键剧情词 | 0.35 | 出现世界卡关键词、任务节点、伏笔实体 |
| 任务相关度 | 0.25 | 与活跃主线/支线任务的关联程度 |
| Think-4 评分 | 0.10 | 四维评分中的 consequence 维度 |

#### B 级摘要（语义摘要）

每 25 轮触发一次压缩，由 A 级摘要聚合生成。压缩过程**调用 TRPG 设置面板中配置的模型**，异步执行不阻塞主循环。

**B 级条目内容**：
- 关键事件链（按时间顺序，每条事件必须含场景锚点）
- 角色弧线（主要 NPC 与玩家关系变化，含态度转变的具体触发事件）
- 世界变化（势力动态、地点状态）
- 未解决线索（伏笔、待办任务、悬而未决的问题——必须逐条列出，不得合并）
- **段末衔接钩子**：B 级摘要的最后一段必须描述「截至第 N 轮，故事停在哪里、下一个即将发生什么」，供模型盲续

B 级压缩时，将 25 轮 A 级摘要交给 LLM，要求输出满足盲续测试的 B 级语义摘要。核心要求：key_events 中每条事件必须含地点加时间加关键感官细节；character_arcs 必须记录态度转变的具体触发事件，不能只写"从 hostile 变为 friendly"；open_threads 必须逐条列出所有未回收的伏笔，每条标注埋设轮次；continuity_hook 必须描述"截至第 N 轮，故事停在哪里、下一个即将发生什么"；summary_text 末尾必须是衔接钩子，不得以封闭式陈述句结尾。

#### C 级摘要（史诗摘要）

每 200 轮触发一次压缩，由 B 级摘要聚合生成。C 级摘要记录战役级叙事，**永久保留**。

**C 级条目内容**：
- 史诗篇章（本章/本幕的核心叙事弧线）
- 主线剧情进展（玩家核心使命的关键节点）
- 主题（当前故事反复出现的主题与象征）
- 伏笔（已埋下尚未回收的线索——必须逐条列出，含埋设轮次和当前状态）
- 角色发展（玩家角色与核心 NPC 的成长轨迹，含关键转折事件）
- **章末衔接钩子**：必须描述「截至第 N 轮，整个故事停在哪一章/哪一幕、下一章即将开启什么主线」

C 级压缩时，将 8 条 B 级摘要（覆盖 200 轮）交给 LLM，要求输出满足盲续测试的 C 级史诗摘要。核心要求：epic_arc 必须概括本章核心叙事弧线（起因、发展、高潮、当前停顿点）；main_plot 必须列出主线剧情的关键节点，每个节点含轮次和触发事件；foreshadowing 必须逐条列出所有未回收的伏笔；character_development 必须记录每个核心 NPC 的成长轨迹，含关键转折事件；continuity_hook 必须描述故事停在哪一章、下一章即将开启什么主线；summary_text 末尾必须是章末衔接钩子。

压缩流程：每轮对话结束后生成一条 A 级摘要（含续写五要素），每 25 条 A 级摘要被 LLM 压缩为一条 B 级语义摘要（含段末衔接钩子），每 8 条 B 级摘要被 LLM 压缩为一条 C 级史诗摘要（含章末衔接钩子）。C 级摘要永久保留不删除。

### 3.2 上下文注入窗口

每轮 API 请求的动态层中同时注入三类上下文。第一类是压缩后的 A/B/C 全量摘要（A 级 50 条引用化约 2000 tokens、B 级 2 条约 400 tokens、C 级 1 条约 200 tokens）。第二类是向量记忆召回 Top-8（约 800 tokens）。第三类是最近 8 轮对话的完整上下文（约 2000 tokens），包含每轮玩家输入和叙事输出中的剧情正文与判定汇总（不含记忆引用和 ReAct 反思段以节省 token）。注入顺序为：A/B/C 摘要最前、向量记忆中间、近 8 轮上下文最后，三类内容都追加在 prompt 末尾的动态层区域。

### 3.3 向量记忆召回

采用聊天模式相同的策略、模型。每对话一轮则针对该存档新增一条分析后的向量记忆，在该存档下持续对话，每轮将召回 8 条记忆条目作为返回记忆注入。

**向量记忆与 A 级摘要的关系**：两套独立数据结构，均在每轮后处理阶段写入，互不替代。

| | 向量记忆 | A 级摘要 |
|------|------|------|
| 存储 | IndexedDB `vectorMemories` store（复用 memoryService） | IndexedDB `saves` store 内嵌于 SaveSlot.aSummaries |
| 内容 | 用户+AI对话内容 + embedding | 结构化 5 字段 + 续写五要素 |
| 生成 | 每轮调用嵌入模型 | 每轮调用 LLM 生成 |
| 用途 | 语义检索召回 Top-8 | 剧情全量注入 + B/C 压缩源 |

---

## 四、规则体系设计

规则基准：D&D 5e SRD 5.2.1 (CC-BY-4.0)

### 4.1 d20 检定判定

d20 检定判定公式为 d20 + ability_modifier + proficiency_bonus（若该技能熟练）对比 DC 值。

判定结果：

| 条件 | 结果 |
|------|------|
| 自然 20 | 大成功（Critical Success）——自动成功 + 额外效果 |
| 自然 1 | 大失败（Critical Failure）——自动失败 + 负面后果 |
| total ≥ DC | 成功 |
| total < DC | 失败 |

优势/劣势规则：
- 优势：掷 2d20，取较高值
- 劣势：掷 2d20，取较低值
- 优势 + 劣势共存 → 相互抵消，正常掷 1d20

DC 等级参考（平衡修改值，非官方参考值）：

| 难度 | DC | 示例 |
|------|:--:|------|
| 非常简单 | 3 | 在安静的房间听到对话 |
| 简单 | 7 | 攀爬有绳结的绳索 |
| 中等 | 13 | 撬开普通锁 |
| 困难 | 17 | 在暴风雪中追踪足迹 |
| 非常困难 | 23 | 在战斗中解除魔法陷阱 |
| 几乎不可能 | 27 | 说服敌对的国王放下武器 |

### 4.2 属性调整值与技能加值

属性调整值等于 (score - 10) 除以 2 向下取整。

| 属性值 | 调整值 | 属性值 | 调整值 |
|:-----:|:-----:|:-----:|:-----:|
| 1 | -5 | 16-17 | +3 |
| 2-3 | -4 | 18-19 | +4 |
| 4-5 | -3 | 20-21 | +5 |
| 6-7 | -2 | 22-23 | +6 |
| 8-9 | -1 | 24-25 | +7 |
| 10-11 | +0 | 26-27 | +8 |
| 12-13 | +1 | 28-29 | +9 |
| 14-15 | +2 | 30 | +10 |

熟练加值等于 2 加上 (level - 1) 除以 4 向下取整。

| 等级 | 熟练加值 |
|:---:|:------:|
| 1-4 | +2 |
| 5-8 | +3 |
| 9-12 | +4 |
| 13-16 | +5 |
| 17-20 | +6 |

**技能加值计算（引擎核心）**：

```typescript
// 技能 → 关联属性映射
const SKILL_ABILITY_MAP: Record<SkillName, AbilityName> = {
  athletics: 'str',
  acrobatics: 'dex', sleight_of_hand: 'dex', stealth: 'dex',
  arcana: 'int', history: 'int', investigation: 'int', nature: 'int', religion: 'int',
  animal_handling: 'wis', insight: 'wis', medicine: 'wis', perception: 'wis', survival: 'wis',
  deception: 'cha', intimidation: 'cha', performance: 'cha', persuasion: 'cha',
};

function skillBonus(char: TrpgCharacter, skill: SkillName): number {
  const ability = SKILL_ABILITY_MAP[skill];
  let bonus = abilityModifier(char.abilities[ability]);

  if (char.proficientSkills.includes(skill)) {
    bonus += proficiencyBonus(char.level);
  }
  if (char.expertiseSkills.includes(skill)) {
    bonus += proficiencyBonus(char.level); // Expertise = 2x proficiency
  }

  return bonus;
}
```

**关键设计决策**：`skillBonus()` 的返回值**覆盖** LLM 给出的 `bonus` 值。LLM 负责选择技能和评估 DC，但加值的数学运算永远是引擎的职责。

### 4.3 战斗系统

战斗系统是 TRPG 引擎中最复杂的子系统，负责从叙事阶段到回合制战斗的无缝切换、回合循环管理、以及战后结算。

**进入战斗**。当 Think-1 从玩家输入中识别出攻击意图（如"我拔剑刺向守卫""我对巨龙释放火球术"），且 Think-2 确认当前场景中存在可攻击目标，引擎调用 `combat_init` 进入战斗。所有参战角色和 NPC 各自投掷先攻骰（d20 + 敏捷调整值），按先攻值从高到低排定回合顺序。先攻值相同的单位之间再掷 d20 决定先后。先攻顺序确定后，引擎将 `TrpgGameState.phase` 切换为 `"combat"`，创建 `CombatState` 对象，并在叙事中输出"进入战斗"的交待段落。

**回合制循环**。每回合只有一个单位行动，引擎在叙事中明确指出"当前是第 N 轮，轮到 XX 行动"。玩家在自己的回合中有以下行动选项：攻击（对指定目标进行近战或远程攻击 —— d20 + 力量或敏捷调整值 + 熟练加值 vs 目标 AC，命中后掷武器伤害骰加对应属性调整值）、施法（消耗法术位施展已知法术，根据法术描述执行效果和伤害）、使用物品（喝药水、投掷道具、激活魔法物品等）、闪避（本回合攻击方对自身有劣势）、撤离（移动不触发借机攻击）、疾走（本回合移动速度翻倍）、协助（为友方单位的下一次攻击检定提供优势条件）、准备动作（设定触发条件和响应动作，在触发条件满足时以反应执行）。所有行动选项都是 Think-2 路径规划的一环，LLM 根据玩家输入和战斗态势推荐最优路径，Think-3 执行裁决。

**NPC 行为策略**。NPC 按阵营和智力的不同采用三级策略。野兽级 NPC 攻击最近的敌人，不考虑战术。士兵级 NPC 优先攻击威胁最大的目标（伤害输出最高或血量最低的敌人），会使用掩护和夹击。指挥官级 NPC 使用完整战术：优先治疗濒死友方、集中火力消灭单个目标、使用环境和地形优势、在劣势时尝试撤退或谈判。

**攻击与伤害计算**。攻击检定的核心公式为 d20 + ability_mod + prof_bonus vs 目标 AC。当结果为自然 20 时触发大成功：攻击自动命中，且伤害骰数量翻倍（如长剑常规 1d8 翻倍为 2d8），但固定加值（来自属性调整值或魔法武器加值）不翻倍。自然 1 触发大失败：攻击自动未命中，无论加值多高。普通命中时伤害计算为武器骰 + 对应属性调整值（近战用力量、远程用敏捷、带灵巧属性的武器可选敏捷）。伤害类型（挥砍/穿刺/钝击/火焰/寒冰/闪电/雷鸣/毒素/酸液/力场/光耀/黯蚀/心灵）影响某些怪物的抗性和弱点——如骷髅对穿刺伤害有抗性（伤害减半）、巨魔对火焰和酸液伤害暂停再生能力。

**战斗中的特殊状态**。角色和 NPC 在战斗中可以附加多种状态条件：中毒（攻击检定和属性检定劣势）、恐慌（无法主动靠近恐慌来源，攻击检定劣势）、魅惑（无法攻击魅惑者，魅惑者在社交检定中有优势）、麻痹（无法行动，攻击方有优势）、昏迷（HP 归零后状态，每回合投掷死亡豁免）、失能（无法行动）、束缚（速度归零，攻击检定劣势，被攻击时对方有优势）、倒地（近战攻击有优势，远程攻击有劣势）。状态可能来自怪物特殊能力、法术效果、或环境因素，由引擎在 Think-3 中进行条件树判定后附加。

**死亡豁免**。当玩家角色的 HP 降至 0，角色立即陷入昏迷状态。在其后续每个回合开始时，角色必须投掷一次死亡豁免：d20 无加值。结果为 10 或以上计 1 次成功；自然 20 立即恢复 1 HP 并苏醒；自然 1 计 2 次失败。累计 3 次成功则角色稳定（HP 仍为 0 但不再投掷死亡豁免，1d4 小时后恢复 1 HP 苏醒）。累计 3 次失败则角色死亡。战斗中的友方角色可以对昏迷角色使用医药检定（DC 10 感知 + 医药）进行急救，成功后角色立即稳定。任何外部治疗效果（法术、药水）可直接将昏迷角色的 HP 恢复至正值并苏醒。NPC 的 HP 降至 0 时直接移除（死亡或逃跑），不投掷死亡豁免。

**退出战斗**。当所有敌对目标的 HP 均不高于 0 时，引擎调用 `combat_end`，将 `TrpgGameState.phase` 切回 `"explore"`，清空 `CombatState` 对象。战后引擎自动计算经验值：每个敌对 NPC 根据其挑战等级（CR）提供对应 XP，所有参战角色平分。如果战斗以非击杀方式结束（如敌方投降、玩家潜行绕过），GM 仍可判定给予等额或减半的经验值以奖励玩家的创造性解决方式。

**战斗 UI 展示**。TRPG 模式在战斗期间自动在叙事区域顶部增加一个可折叠的战斗状态条：显示当前轮次、先攻顺序列表（当前行动者高亮）、玩家 HP 和 AC、当前敌对目标列表及其可见 HP 状态（完整数值 / "受伤" / "重伤"视 GM 判定决定是否公开具体数字，可选设置开关）。

```typescript
interface CombatState {
  round: number;                          // 当前战斗轮次
  turnOrder: string[];                    // 按先攻降序的角色/NPC ID
  currentTurnIndex: number;               // 当前回合索引
  participants: Record<string, {          // 参战者状态
    id: string;
    name: string;
    hp: { current: number; max: number };
    ac: number;
    initiative: number;                   // 先攻值
    conditions: string[];                 // 状态条件
    isPlayer: boolean;
  }>;
}
```

### 4.4 社交系统

社交系统管理玩家与 NPC 之间的非战斗互动，包括对话、说服、欺瞒、威吓、交易和情报获取。社交互动的核心机制是 NPC 态度五级制和社交技能检定。

**态度五级制**。每个 NPC 在 GameState 中维护一个 `attitude` 字段（见 `GameNpc` 类型）。五种态度的初始值由世界卡的 `characterDatabase` 中该 NPC 的定义决定，例如某势力的 NPC 对玩家阵营可能默认为敌视、中立 NPC 对陌生人默认为中立。五种态度对应的 DC 调整值为：敌视 +5（NPC 不信任玩家，任何说服尝试都变得更困难）、不友好 +0（默认难度，NPC 没有特别的好感或恶感）、中立 -5（NPC 按正常社会规范互动，公事公办）、友善 -10（NPC 对玩家有好感，容易接受请求）、乐意帮助 -15（NPC 主动愿意为玩家做超出预期的事）。

**社交行动类型**。当玩家在输入中表达社交意图（如"我试着说服守卫让我通过""我编了个谎言骗过酒保"），Think-1 将其识别为社交意图，Think-2 从以下三种社交行动中选择最合适的：说服——使用魅力加游说技能对抗目标 NPC 的洞悉 DC（NPC 的被动感知值或 d20 + 感知调整值），成功则 NPC 接受玩家的提议或请求；欺瞒——使用魅力加欺瞒技能对抗目标 NPC 的洞悉 DC，成功则 NPC 相信玩家的谎言（失败则 NPC 识破谎言，态度可能恶化）；威吓——使用魅力或力量（取较高者）加威吓技能对抗目标 NPC 的 DC，成功则 NPC 出于恐惧暂时屈从（但态度可能永久恶化，或在威胁消除后转为敌视）。

**态度变化机制**。每次社交检定的结果不仅决定当前互动的成败，还会影响 NPC 对玩家的长期态度。态度调整量计算公式为 `floor((检定总值 - DC) / 5)`，每次交互的态度变化上限为 ±2 级。例如检定总值 18，DC 13，差值为 5，调整量为 +1，态度从"不友好"提升为"中立"。连续多次成功的正面互动会使 NPC 逐渐对玩家产生信任和好感。反之，失败的欺瞒、粗暴的威吓、或违背承诺的行为会使态度急剧恶化。

**态度变化的叙事表现**。态度变化不是突然跳变，而是通过叙事自然过渡。GM 在 Narrator 中通过 NPC 的措辞、肢体语言、行为举止来暗示态度的渐变："守卫原本紧握长矛的手略微放松了些""酒保收起了之前那种懒洋洋的态度，认真地看了你一眼"。对于犬系/兽人 NPC，世界卡的 `npcGen` 模块要求 GM 通过尾巴动作和耳朵方向来暗示真实情绪，即使对话文字与此表面对立。

**社交系统的边界**。以下情况不适用社交检定：NPC 本身没有自由意志（亡灵仆从、构装体）、玩家的请求直接违反 NPC 的核心信念或生存本能（如说服忠臣背叛国王）、玩家试图跳过已由战斗系统处理的冲突（如威胁已经拔剑的敌人让他投降——此时 GM 判定进入战斗而非社交）。这些边界由 GM 在 OOC 审查中判断。

**交易系统**。交易是社交系统的子模块。玩家可以在安全地点（城镇、营地、贸易站等）与 NPC 进行买卖。交易的基础价格来自物品的 `value` 字段（以 GP 为单位）。售价受交易技能检定的影响：玩家可以进行一次魅力 + 游说检定对抗 NPC 的交易 DC，成功时买入价格降低 10%、卖出价格提高 10%。每额外成功 5 点，价格浮动再增减 5%，上限为 25%。态度也会影响价格——友善 NPC 可能主动给出折扣，敌视 NPC 可能漫天要价或干脆拒绝交易。交易过程由 GM 在叙事中呈现，实际的物品和货币增减由引擎通过 `inventory_add`、`inventory_remove` 和货币调整工具完成。

### 4.5 探索系统

探索系统覆盖战斗和社交之外的自由移动、环境交互、信息获取和生存挑战。

**移动与导航**。当玩家描述移动意图（"我沿着山路向北前进""我在森林里寻找通往遗迹的路径"），引擎根据世界卡的 worldSetting 和当前地形判断移动难度。移动分为三种速率：快速（每日覆盖距离 ×1.33，但感知检定有劣势）、正常（每日覆盖标准距离，无修正）、缓慢（每日覆盖距离 ×0.67，但可以使用隐匿技能同时移动）。地形影响最大速率——山地只能慢速移动、森林正常速率、平原可快速移动。玩家选择移动速率后，GM 通过探索检定或直接叙事描述移动过程：成功抵达目的地则更新 `TrpgGameState.currentLocation` 和 `time`（游戏内时间推进对应小时数），同时根据地形和区域随机性触发环境描述和可能的随机遭遇。

**环境描述生成**。探索系统的核心输出是对玩家所处环境的生动叙事。引擎从世界卡的 worldSetting 中读取当前位置的环境描述模板，结合实时变量（天气、时间段、光照条件、季节）动态生成感官丰富的场景描写。天气由 GM 根据世界卡的季节设定和区域气候随机或剧情需要决定——晴天/阴天/小雨/暴雨/暴风雪/浓雾等，不同天气影响能见度、移动速度、以及某些检定的难度。时间段分为黎明/上午/下午/黄昏/前半夜/深夜/后半夜共七个阶段，每个阶段有不同的光照条件和适用场景（深夜适合潜行、正午适合远距离观察）。探索环境描述每次至少包含 2 种感官细节（视觉 + 听觉/嗅觉/触觉/温度感）。

**三种被动感知**。探索系统维护每个角色的被动感知值，不需要玩家主动声明检定即可自动触发。被动察觉 = 10 + 感知调整值 + 熟练加值（如果熟练察觉技能），用于 GM 自动判断玩家是否注意到隐藏的细节（暗门、陷阱的异常痕迹、远处潜伏的生物）。被动洞悉 = 10 + 感知调整值 + 熟练加值（如果熟练洞悉技能），用于 GM 自动判断玩家是否察觉 NPC 的谎言或反常行为。被动调查 = 10 + 智力调整值 + 熟练加值（如果熟练调查技能），用于自动发现线索间的逻辑关联。这三种被动值让 GM 可以在玩家没有主动声明"我检查门""我观察他表情"的情况下，仍然通过叙事暗示引导玩家发现隐藏内容。

**五种主动探索行动**。主动搜索：玩家声明搜索意图后，引擎投掷 d20 + 感知 + 察觉（如果熟练）对抗隐藏 DC。成功时 GM 在叙事中描述发现的线索或物品，并可能在地图中标记新地标、在背包中添加找到的物品。失败时 GM 描述搜索无果，或给出误导性线索（如果失败幅度大）。解除陷阱：玩家声明后，引擎投掷 d20 + 敏捷 + 巧手对抗陷阱 DC。成功则陷阱安全解除，失败则陷阱触发（造成伤害、触发警报、或封堵通道），严重失败（比 DC 低 10 以上）可能损坏解除工具。导航：玩家在复杂地形或迷宫中尝试保持方向，引擎投掷 d20 + 感知 + 生存对抗导航 DC。成功则维持正确方向并可能发现捷径，失败则迷失方向（消耗额外时间、可能绕回原点、触发随机遭遇）。追踪：玩家尝试跟随目标的踪迹，引擎投掷 d20 + 感知 + 生存对抗追踪 DC。DC 取决于地表类型（软泥地 DC 10、草地 DC 15、岩石地 DC 20）、时间流逝（每过 1 小时 DC + 1）、以及天气影响（雨水冲掉痕迹 DC + 5）。潜行：玩家尝试不被发现地移动，引擎投掷 d20 + 敏捷 + 隐匿对抗场景中所有敌人的被动察觉值。成功则玩家保持隐匿并获得下一轮对未察觉敌人攻击的优势条件。潜行速度减半，且在无掩体的开阔地带无法潜行。

**随机遭遇**。在导航失败、长时间野外旅行、或 GM 判定场景需要增加张力时，引擎触发随机遭遇。遭遇的内容由世界卡的 worldSetting 和当前区域决定——每个区域有一个遭遇表，列出可能遇到的生物、自然现象、或事件（如"森林：1-3 狼群、4-5 友善的旅行商人、6-8 废弃营地含战利品、9-10 无害的野生动物"）。GM 在叙事中灵活调整遭遇的强度和时机，确保推进剧情而不是机械掷表。

**地图数据更新**。探索过程中，当玩家首次进入一个新地点、从高处俯瞰到新区域、或通过 NPC 对话获得地理情报，引擎调用 `map_discover` 工具将新地标添加到 `TrpgGameState` 的已知地标集合中。地标的详细信息（完整描述、子地点、关联 NPC）从世界卡的 worldSetting 中提取，但只有在玩家实际探索或通过其他方式获知后才对用户可见（见 2.6 节的自删除和渐进解锁机制）。

### 4.6 休息系统

休息系统管理角色的 HP 恢复、资源重置和时间推进。所有时间均指 TRPG 游戏内世界时间（角色主观感受到的时间流逝），而非现实真实时间。

**短休**。短休是两次战斗之间的小憩，前提为当前不处于战斗阶段。短休耗时游戏内时间 1 小时——这意味着引擎将 `TrpgGameState.time.hour` 增加 1（跨日时自动进位天数和更新昼夜阶段）。短休期间角色可以：掷生命骰来恢复 HP（每个生命骰掷对应职业的命中骰 + 体质调整值，恢复值为结果的总和，玩家可以选择掷任意数量但不超过已消耗的数量）；重新集中注意力（某些职业特性可在短休后恢复，如战士的"回气"）。生命骰总数等于角色等级，每消耗一个就从可用池中扣除。每次长休后恢复已消耗生命骰的一半（向下取整，最少恢复 1 个）。

**长休**。长休是完整的夜间休息，前提为角色位于安全地点（城镇旅馆、加固营地、友方据点等，由 GM 判定）且不处于战斗阶段。长休耗时游戏内时间 8 小时——引擎将 `TrpgGameState.time.hour` 增加 8（通常跨夜到次日黎明）。长休的效果：HP 恢复至最大值；恢复已消耗生命骰的一半（向下取整，最少 1 个）；恢复所有已消耗的法术位、每日职业特性和种族能力；移除 1 级力竭状态。长休的限制：每 24 个游戏内小时最多只能完成一次长休。如果长休因战斗或突发事件被打断，角色必须重新开始至少 1 小时才能获得短休的收益。

**时间推进的连带影响**。游戏内时间的推进不仅影响体力恢复，还影响整个世界的状态。昼夜交替改变光照条件和 NPC 位置（NPC 按 worldCard 中定义的 `routine` 作息——白天在集市、晚上在家中）。长休过夜后，世界卡 worldTimeline 中的某些时间敏感事件可能触发（如"第 3 日早晨，灰爪帝国的援军抵达边境"）。食物和水消耗：如果启用生存模式（世界卡的 `mods` 中配置），每经过 24 个游戏内小时未进食饮水，角色承受力竭累积。这些连带影响由 GM 在时间推进时根据世界卡数据自动判断和叙事呈现。

**力竭状态**。力竭是独立的负面状态，不同于战斗中的昏迷/死亡豁免。力竭由极端环境（暴风雪中无防护行走数小时）、饥饿、或特殊怪物能力触发，分为 6 级。1 级：所有属性检定和除死亡豁免外的所有豁免检定有劣势。2 级：速度减半。3 级：攻击检定和所有豁免检定（包括死亡豁免）有劣势。4 级：HP 上限减半。5 级：速度降为 0。6 级：死亡。每次长休可移除 1 级力竭。较短的长休如果被中断，不能移除力竭。

### 4.7 升级系统

XP 表：

| 等级 | XP | 等级 | XP | 等级 | XP | 等级 | XP |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | 0 | 6 | 14,000 | 11 | 85,000 | 16 | 195,000 |
| 2 | 300 | 7 | 23,000 | 12 | 100,000 | 17 | 265,000 |
| 3 | 900 | 8 | 34,000 | 13 | 120,000 | 18 | 305,000 |
| 4 | 2,700 | 9 | 48,000 | 14 | 140,000 | 19 | 355,000 |
| 5 | 6,500 | 10 | 64,000 | 15 | 165,000 | 20 | 355,000+ |

升级时更新：
- `proficiency_bonus = 2 + (level - 1) // 4`
- HP += 掷命中骰 + con_modifier（或取均值）
- class_features 解锁（按职业表）
- spell_slots 更新（按职业表）
- ASI (Ability Score Improvement) @ Level 4/8/12/16/19

### 4.8 实施策略

所有规则逻辑在 TypeScript 中实现，**不信任 LLM**：

```typescript
// frontend/app/services/trpg/dice.ts
export function d20Check(bonus: number, dc: number, opt?: {
  advantage?: boolean;
  disadvantage?: boolean;
}): DiceResult {
  let raw: number;
  if (opt?.advantage && !opt?.disadvantage) {
    raw = Math.max(randomInt(1, 20), randomInt(1, 20));
  } else if (opt?.disadvantage && !opt?.advantage) {
    raw = Math.min(randomInt(1, 20), randomInt(1, 20));
  } else {
    raw = randomInt(1, 20);
  }
  const total = raw + bonus;
  const success = total >= dc;
  const critical = raw === 20 ? 'success' : raw === 1 ? 'failure' : 'none';
  return { roll: raw, bonus, total, dc, success, critical };
}
```

---

## 五、世界存档体系设计

### 5.1 持久化方案

TRPG 数据全部存储于 IndexedDB 数据库 luzzy_trpg 中，与自由聊天 Session 完全隔离。包含四个 store。

saves 存储所有存档，以 saveId 为键。每个存档通过 worldCardId 外键关联到一张世界卡（可为 null）。存档内存放消息历史、A/B/C 三级记忆摘要、角色卡和 NPC 列表的副本（冗余存储以保证存档独立可导出）。

worldCards 存储所有世界卡，以 cardId 为键。世界卡独立于存档存储，支持一张世界卡被多个存档引用。worldCards store 仅有三条索引：byUpdatedAt 按更新时间排序、byTitle 按标题排序、和 byContentRating 按内容分级筛选。

characters 存储 D&D 角色卡模板，以 charId 为键。角色卡模板可在多个存档间复用（同一玩家角色在不同存档中使用相同的属性配置）。每个角色卡存储六维属性、技能、装备、法术位等完整数据。

vectorMemories 复用现有 memoryService 的向量记忆 store，每轮游戏后新增一条嵌入向量供语义检索召回。

### 5.2 持久化策略

| 策略 | 触发条件 | 实现 |
|------|---------|------|
| 自动保存 | 每 `auto_save_interval` 轮（默认 10） | `SaveSlot` → JSON → IndexedDB put |
| 手动保存 | 用户触发 | 同上 |
| 会话关闭保存 | `beforeunload` / `visibilitychange` | 触发 `autoSave()` |
| 写入保护 | 每次写入 | 写前备份 → 写入新版本 → 成功则删备份 |

原子写入实现：

```typescript
async function atomicSave(saveId: string, data: SaveSlot): Promise<void> {
  const db = await openTrpgDb();
  const current = await db.get('saves', saveId);
  // 1. 备份当前版本
  if (current) {
    await db.put('saves', { ...current, _backup: true }, `${saveId}_bak`);
  }
  // 2. 写入新版本
  await db.put('saves', data, saveId);
  // 3. 成功后删除备份
  await db.delete('saves', `${saveId}_bak`);
}
```

### 5.3 与 Session 的关系

当前的 `Session` + `ChatMessage[]`（localStorage/IndexedDB）不变。TRPG 存档 `SaveSlot` 是**独立存储**，与自由聊天 Session 互不干扰。TRPG 模式不修改现有 Session 类型，而是在 Session 上新增可选字段 `trpgSaveId?: string`，指向 saves store 中的 saveId。当 session.mode 为 'trpg' 时，引擎通过 trpgSaveId 加载对应的 SaveSlot。

---

## 六、CoT + ReAct 固定思考模式

GM 的每一次裁决都遵循固定的 CoT + ReAct 管线——从理解玩家意图到最终交付叙事，经过六个确定性阶段。每个阶段有明确的输入、输出边界和执行者，不跳跃、不合并、不省略。

### 6.1 管线全貌

一次完整的 GM 裁决包含六个阶段，按固定顺序执行。前两个阶段由 LLM 在 reasoning_content 中完成，中间三个阶段是引擎在收到 LLM 输出后本地执行，最后一个阶段由 LLM 进行叙事渲染。

**Think-1（意图分析，LLM）**。模型阅读玩家输入，结合当前角色状态、场景上下文和世界卡设定，输出结构化的意图 JSON。JSON 中包含八个字段：intent 描述玩家实际想做什么（5-200 字）、motive 推测玩家动机、category 行动分类（combat/social/explore/inventory/rest/info/meta）、skill_required 需要的核心 D&D 技能（若无明确技能则为 null）、attribute 关联的六维属性、estimated_dc 预估难度、constraint_scan 五项约束扫描（是否需要特定物品/法术/地点依赖/时间敏感/元游戏风险）、以及 target_npc 社交或战斗指向的目标 NPC ID（无目标则为 null）。新增的 category 字段让引擎在 Think-3 中直接路由到正确的子系统而无需二次判断。新增的 target_npc 字段让引擎预先定位目标 NPC 数据。

**Think-2（路径规划，LLM）**。模型基于 Think-1 的结果，结合角色能力面板（熟练技能列表、属性值、当前 HP/AC/状态条件），提供两条不同侧重点的行动路径并推荐其中之一。每条路径必须选择不同的技能或不同的风险等级，保证玩家有真实的选择空间。路径中的 bonus 字段由 LLM 留空，引擎在后续步骤中从角色属性重新计算真实值并覆盖。如果 Think-1 判定玩家行动属于无需检定的自由叙事（闲聊、观察环境、内心独白等），Think-2 的路径规划中 skill 和 dc 均可为 null，引擎跳过掷骰直接进入叙事。

**引擎计算（TypeScript）**。在 Think-2 完成后、Think-3 开始前，引擎执行一次关键计算：调用 `skillBonus()` 从角色属性表中获取该技能的真实加值，覆盖 Think-2 中 LLM 给出的 bonus 占位值。同时引擎根据 `estimated_dc`、角色等级和当前条件树求值结果，确定最终 DC、优劣势判定和任何适用的世界规则修正。这一步骤完全不依赖 LLM，确保数值公平可审计。

**Think-3（规则裁决，TypeScript 主导 + LLM 辅助 OOC）**。这是 GM 最复杂的工作阶段，按以下顺序执行七个裁决步骤。

第一步，条件树求值。引擎遍历世界卡的 laws 和 mods 数组，检查本轮行动是否触发任何世界规则。例如"在犬系大陆上，欺骗行为的对象如果是犬族，对方可以通过嗅觉线索获得对欺瞒检定的优势条件"。每条匹配的规则输出一个修正项（dc_modifier / advantage / disadvantage / bonus_modifier / blocked）。

第二步，d20 检定。引擎使用 TypeScript 的 `Math.random()` 本地掷骰，结合引擎计算阶段确定的真实 bonus 和最终 DC 以及优劣势修正，执行 d20_check。返回值包含原始掷骰值、加值、总值和成败结论。如果 Think-2 判定无需检定，跳过此步。

第三步，OOC 审查。七项审查中，审查项 3（世界一致性破坏）、4（重复无效行动）、6（绕过机制）由 TypeScript 代码执行：对照条件树的 blocked 标记、比对玩家最近的输入历史检测重复模式、检查玩家是否试图绕过本应存在检定的行动。审查项 1（元游戏/第四面墙破坏）、2（玩家知识越界）、7（角色扮演崩坏）由 LLM 在 reasoning_content 的末尾输出一段 OOC 审查结论（JSON 格式，包含每项的 pass/soft_warn/hard_block 判定和理由），引擎解析后合并到 OocResult 中。审查项 5（内容分级越界）在 content_rating 为 unrestricted 时自动 pass。任何 hard_block 导致本轮行动被阻断，引擎直接跳过后面的裁决步骤并将 action 置为 blocked。

第四步，子系统裁决。根据 Think-1 输出的 category 将裁决分发给对应子系统。category=combat 时分发给战斗系统执行攻击检定、伤害掷骰、状态附加和 HP 扣减。category=social 时分发给社交系统执行社交技能检定和态度调整计算。category=explore 时分发给探索系统执行搜索/导航/潜行等对应行动的检定和环境生成。category=inventory 时执行物品增删逻辑（前提是 GM 在 OOC 审查中判定行动合理——如"我在尸体上搜刮战利品"→ OOC 通过 → inventory_add）。category=rest 时执行短休或长休的 HP 恢复和资源重置（前提是 OOC 审查确认安全条件满足）。category=info 时处理 NPC 信息渐进解锁——玩家通过洞悉检定或剧情对话获知了 NPC 的某些背景信息，引擎将对应的字段名加入 `revealedFields` 集合。category=meta 时（如"我查看自己的属性面板""我在背包里找东西"），引擎不执行任何裁决，直接返回空操作，Narrator 以简短提示回应。

第五步，状态原子更新。引擎将本轮所有子系统输出的变更汇总为一个 StateDelta 对象。包含十个布尔追踪字段：hp_changed（HP 变化）、xp_changed（经验值变化）、level_changed（等级变化）、inventory_changed（物品变化，含新增/消耗/丢弃）、location_changed（位置变化）、npc_changed（NPC 态度或在场状态变化）、condition_changed（角色状态条件变化）、quest_changed（任务进度变化）、faction_changed（势力关系变化）、map_changed（地图发现或地点归档）。引擎按此顺序原子写入 TrpgGameState，确保变更的完整性和一致性。

第六步，游戏内时间推进。根据行动性质推进 `TrpgGameState.time`。简短行动（一次攻击、一句对话）推进数分钟；中等行动（搜索房间、短途移动）推进数十分钟到数小时；长时间行动（长休、长途旅行）推进数小时到半天。引擎根据行动类别使用预设时间增量表，GM 可以在 Think-2 中覆盖预估时间。

第七步，NPC 在场状态更新。引擎检查当前场景中的 NPC 列表，根据他们的 `routine`（世界卡 characterDatabase 定义）和当前游戏内时间，自动更新每个 NPC 的在场/离场状态。例如商人 NPC 白天在集市、傍晚回家；守夜人 NPC 白天离场、夜晚在场。

**Think-4（评分，TypeScript）**。引擎对 Think-3 的全部裁决结果进行四维自我评分，作为 GM 的质量自检。四项评分和加权公式见 1.4 节自我评分部分。评分结果不直接影响本轮叙事输出（即使低分也照常输出），但影响 A 级摘要的重要性权重和日志记录——连续低分会触发 GM 质量警告。

**Narrator（叙事渲染，LLM）**。模型收到引擎的完整裁决结果后，以文学化、沉浸式的第二人称写出 7 段叙事。叙事中必须忠实引用引擎提供的掷骰数值和裁定结论，不得编造。同时根据 NPC 的 attitude 变化和世界卡的叙事规范（narrativeBase / npcGen），在描述中自然融入态度暗示（如犬系 NPC 通过尾巴动作暴露真实情绪）。叙事末尾必须出具 A 到 E 五个行动选项供玩家选择下一轮行动。

### 6.2 Think-1 意图分析 Prompt

Think-1 的核心任务是理解玩家输入并将其归类到正确的子系统入口，同时完成出 OOC 风险的初步扫描。Prompt 以 [THINK-1: 意图分析] 标签开头，要求模型输出 JSON，包含：intent（玩家行动描述）、motive（推测动机）、category（combat/social/explore/inventory/rest/info/meta）、skill_required（D&D 18 技能之一或 null）、attribute（六维属性之一或 null）、estimated_dc（3-27 整数或 null）、constraint_scan（五项布尔值约束扫描）、以及 target_npc（目标 NPC ID 或 null）。category 的引入使 Think-3 可以直接路由，避免模型和引擎之间的分类歧义。模型必须只输出 JSON。

### 6.3 Think-2 路径规划 Prompt

Think-2 的核心任务是基于 Think-1 的意图评估为玩家提供选择空间。Prompt 以 [THINK-2: 路径规划] 标签开头，要求输出两条不同侧重点的路径。每条路径包含：name（简短路径名）、description（30-100 字路径描述）、skill（使用的技能或 null）、dc（难度或 null）、advantage/disadvantage（布尔值，不可同时为 true）、bonus（留空由引擎覆盖）、risk（safe/moderate/risky/deadly）、以及 potential_outcome（成功后可能发生什么）。此外输出 recommended 推荐哪条路径、reasoning 推荐理由（不超过 50 字）、和 estimated_time_cost（预估消耗的游戏内时间，如"数分钟""1 小时""半天"）。如果 Think-1 的 category 为 info 或 meta（纯叙事无需检定），两条路径可以仅侧重不同的叙事风格或信息获取角度，skill 和 dc 均可为 null。模型必须只输出 JSON。

### 6.4 Think-3 规则裁决的工具调用映射

Think-3 中的每个裁决步骤对应一个 TypeScript 工具函数，模型通过 `tool_calls` 调用这些工具，引擎本地执行并返回结果。所有工具调用的结果都追加到消息列表中作为 `tool` 角色消息，供后续 Narrator 引用。

| 裁决步骤 | 工具名 | 输入 | 输出 |
|---------|--------|------|------|
| 条件树求值 | `eval_world_rules` | category, skill, target_npc | 规则修正数组 |
| d20 检定 | `d20_check` | skill, bonus, dc, advantage?, disadvantage? | 掷骰结果 |
| 伤害掷骰 | `roll_damage` | expression (如 "2d6+3"), crit? | 伤害值 |
| OOC 审查（引擎部分） | `ooc_check_engine` | player_input, recent_inputs, phase | 引擎可判定的 hard_block/soft_warn |
| OOC 审查（LLM 部分） | LLM 在 reasoning_content 末尾输出 OOC JSON → 引擎解析合并 |
| 战斗裁决 | `combat_resolve` | attacker_id, target_id, action_type, weapon? | 命中/伤害/状态附加 |
| 社交裁决 | `social_resolve` | npc_id, action_type (persuade/deceive/intimidate) | 成功/态度变化量 |
| 探索裁决 | `explore_resolve` | action_type (search/disarm/navigate/track/sneak), dc | 成功/发现内容 |
| 物品操作 | `inventory_add` / `inventory_remove` / `inventory_use` / `inventory_equip` | item 描述 | 操作结果 |
| 休息 | `rest_resolve` | rest_type (short/long), location_safety | HP 恢复量/资源重置 |
| NPC 信息解锁 | `npc_reveal` | npc_id, field_keys[] | 已解锁字段更新 |
| 状态更新 | `apply_state_delta` | state_delta JSON | 写入 TrpgGameState |
| 时间推进 | `advance_time` | minutes | 新时间值 |
| NPC 在场更新 | `update_npc_presence` | 无 | 根据 routine 自动更新 |
| 地图更新 | `map_discover` / `map_archive` | location_id, reason? | 地标增删 |

### 6.5 7 段输出（CoT + ReAct 思考卡片内）

7 段输出不是独立的正文格式，而是作为 CoT + ReAct 思考链中 Narrator 节点（思考卡片）的内部结构。模型在 content 中按固定顺序输出 7 段，前端将其渲染在思考链的最后一个展开卡片内。

卡片从上到下依次展示：第一段记忆引用（标注 A/B/C 层级）、第二段剧情分析（一两句话处境分析）、第三段判定汇总（引用引擎掷骰结果）、第四段剧情正文（300-500 字第二人称叙事）、第五段行动选项（A 到 E 五个按钮，见 6.7 节）、第六段状态信息（紧凑一行 HP/AC/位置/游戏内时间/活跃任务）、第七段 ReAct 反思（折叠隐藏，不展示给玩家，仅用于记忆生成）。

### 6.6 思考链前端解析

前端在 `parseCot()`（`markdownService.ts`）中识别 `[THINK-N]` 标签，将 LLM 的 reasoning_content 拆分为结构化步骤。扩展后的解析同时识别 `[OOC-REVIEW]` 标签和工具调用条目，将它们映射为对应的 AgentStep。

```typescript
// markdownService.ts 新增
export function parseThinkSections(text: string): AgentStep[] {
  const thinkRegex = /\[THINK-(\d)\]\s*([\s\S]*?)\[\/THINK-\1\]/g;
  const oocRegex = /\[OOC-REVIEW\]\s*([\s\S]*?)\[\/OOC-REVIEW\]/g;
  // 解析 THINK-1/2/4 和 OOC 审查段落
  // ...
  return steps;
}
```

在思考链 UI 中，不同节点根据类型显示不同的标签和颜色：Think-1（蓝色，"意图分析"）、Think-2（绿色，"路径规划"）、OOC 审查（黄色，"OOC 审查"）、d20_check/combat_resolve/social_resolve/explore_resolve 等工具调用（琥珀色，显示对应工具名和裁决结果）、rest_resolve/inventory_*/npc_reveal/map_* 等数据变更工具（灰色，紧凑一行仅显示操作摘要不展开详情）、Think-4（紫色，"GM 评审"，显示四维分数）、Narrator（白色/默认，"叙事"，内含 7 段子内容）。

### 6.7 行动选项卡片

行动选项是 Narrator 思考卡片中第五段的内容，以五个可交互的卡片形式呈现在剧情正文下方。

**卡片布局**：A 到 D 四个选项各占一行，纵向排列。每个选项是一个圆角卡片，左侧是选项字母标签（A/B/C/D），右侧是选项描述文本。E 选项（自定义行动）是一个独立的输入框，提示文字为"输入自定义行动..."，用户可以直接在此输入任何自然语言描述的行动。

**选中行为**：点击任意 A 到 D 的选项卡片，被选中的卡片边框高亮（主色调描边），其余卡片变灰，同时底部输入栏自动填入该选项的描述文本。用户可在发送前自由修改文本。在 E 选项输入框中直接输入文字则视为自定义行动，无需点击卡片。

**与思考链的关系**：行动选项卡片是 Narrator 节点的子内容。当思考链中的 Narrator 节点展开时，7 段内容从上到下依次展示，第五段的 A 到 E 五个选项作为交互式卡片嵌入其中。

**视觉规范**：未选中卡片使用 muted 背景，圆角 8px，左侧字母标签使用小号粗体加圆角方块底色。选中卡片使用主色调淡背景加主色调描边。卡片 hover 时背景略微加深，点击有轻微缩放弹簧动效。

**与输入栏的关系**：底部输入栏和行动选项卡片同时存在但不冲突。用户既可以通过点击选项卡片来快速填充输入栏，也可以直接忽略卡片、在输入栏中手打任何内容。选项卡片仅作为快捷输入辅助，不替代输入栏的文字能力。

---

## 七、系统架构适配 + KV 缓存深度优化

### 7.1 三层 Prompt 架构

TRPG 的系统提示词采用三层分层架构，以最大化 DeepSeek KV 缓存的命中率。

第一层是共享前缀层，约 4000 tokens（如存在世界卡则增加世界卡文本量，约 3000-5000 tokens），KV 缓存完整命中。包含四项固定不变的内容：TRPG 引擎身份声明（永不修改）、世界卡全量文本（世界卡不变则缓存稳定）、D&D 5e 规则速查（DC 表、技能表、优劣势规则）、以及叙事风格规范（preset 的固定部分）。

第二层是半稳定层，约 1500 tokens。包含三项结构固定但值可变的内容：角色卡 JSON（每轮更新，但通过 key 排序保证 JSON 字节稳定性）、世界状态 JSON（低频更新，仅世界事件触发时变更）、活跃 NPC 风格（仅在 NPC 出入场时更新）。半稳定层的 JSON 序列化使用稳定算法（key 按字母排序、浮点取整、不使用 Unicode 转义），确保大多数轮次中字节序列与前一轮完全一致，从而命中 DeepSeek 的公共前缀缓存。

第三层是动态层，约 6800 tokens，追加在 prompt 最末尾，无法命中缓存。包含 7 项内容：A/B/C 全量摘要（压缩后约 2000 tokens）、向量记忆 Top-8（约 800 tokens）、近 8 轮对话上下文（约 2000 tokens）、本轮玩家输入（约 200 tokens）、以及动态生成的 TRPG 工具描述（约 800 tokens）。动态层中的近 8 轮上下文中，n-2 轮与上一轮请求中的 n-1 轮有文本重叠，DeepSeek 公共前缀检测可部分命中约 30%。

### 7.2 缓存优化策略

#### 策略 1：JSON 序列化稳定性

```typescript
function stableJson(obj: unknown): string {
  // key 排序 + 固定浮点精度 + 无 Unicode 转义
  return JSON.stringify(obj, Object.keys(obj).sort(), 0);
  // 注意: HP/AC/属性值使用整数，不需要浮点小数
}
```

#### 策略 2：A 级摘要引用化

A 级摘要注入上下文时采用引用化压缩，不注入完整内容。原始摘要每条约 150 tokens（如"A0042 玩家在白尾哨与霜爪巡逻兵发生冲突，使用威吓技能 DC13 d20(16)+3=19 成功迫使对方后退..."），引用化后每条仅约 40 tokens（如"[A0042] 白尾哨威吓霜爪巡逻兵成功，19 vs DC13，态度 hostile→unfriendly"），50 条 A 级摘要从约 7500 tokens 压缩至约 2000 tokens。

#### 策略 3：半稳定层延迟更新

```typescript
function shouldUpdateSemiStable(
  prevChar: TrpgCharacter,
  currChar: TrpgCharacter,
  prevGs: TrpgGameState,
  currGs: TrpgGameState
): boolean {
  return (
    prevChar.hp.current !== currChar.hp.current ||
    prevChar.hp.max !== currChar.hp.max ||
    prevChar.conditions.join(',') !== currChar.conditions.join(',') ||
    prevGs.currentLocation !== currGs.currentLocation
  );
}
// 不变的轮次 → DeepSeek 公共前缀检测命中
```

#### 策略 4：与聊天模式隔离

TRPG 和聊天模式使用**完全独立的 preset 前缀**，各自维护独立的 KV 缓存空间。

### 7.3 缓存命中率预估

以 ~15,300-17,300 tokens 总 prompt（含世界卡全量 3000-5000 tokens）为例：

| 层级 | tokens | 命中率 | 命中 tokens |
|------|:---:|:---:|:---:|
| 共享前缀层（含世界卡） | 7,000-9,000 | 100% | 7,000-9,000 |
| 半稳定层 | 1,500 | 85% | 1,275 |
| 动态层 | 6,800 | ~30%* | 2,040 |
| **合计** | **15,300-17,300** | **~67-71%** | **10,315-12,315** |

*动态层中 n-2 轮上下文与上轮 n-1 轮有文本重叠，DeepSeek 公共前缀检测可部分命中。

优化建议：若命中率不达标，可将 `recent_context_window` 从 8 降至 4（动态层减至 ~5000 tokens），或采用增量注入（n-1 完整体，n-2..n-8 仅 summary 一行）。

### 7.4 Android 代理适配

删除火山方舟跨 CORS 代理特殊处理分支（`MainActivity.kt:463-586`）。TRPG 内置模式不需要代理——所有 API 调用走 `apiClient.ts` 直连 LLM。

---

## 八、开发批次规划

| 批次 | 内容 | 依赖 | 工作量 |
|------|------|:---:|:---:|
| **P0** | 条目一 (独立管线 buildTrpgContext + sendTrpgMessage) + 条目四核心 (dice.ts + skillBonus.ts + TrpgCharacter/TrpgGameState 类型) + d20 工具注册到 buildTrpgToolDescriptions | 无 | 2-3 周 |
| **P1** | 条目二 UI (模式切换栏 + 底部功能栏 Sheet + 输入栏) + 设置面板模型选择器 + TRPG_GM_PRESET_CONTENT | P0 | 2-3 周 |
| **P2** | 条目四子系统 (combat.ts + social.ts + explore.ts + rest.ts + leveling.ts) + OOC 审查（TypeScript 部分 + LLM 辅助 Prompt） + Think-4 评分 TypeScript 实现 | P0 | 2 周 |
| **P3** | 条目二设计模式 (Stage 0-3) + 条目五 IndexedDB 持久化 + 原子写入 | P1 | 3-4 周 |
| **P4** | 条目三 (A/B/C 记忆压缩 + 上下文注入窗口) + 条目六 (CoT 思考链标签解析 + 行动选项卡片 UI) | P2 | 2-3 周 |
| **P5** | 条目七 (KV 缓存分层 + JSON 稳定化 + A 级引用化) + 条目二地图/背包/角色 UI Sheet | P1 | 1-2 周 |
| **总计** | — | — | **12-15 周** |

### 里程碑

| 版本 | 内容 | 累计工作量 |
|------|------|:---:|
| **v0.8.0-alpha** | P0: 可掷 d20 + TRPG 模式管线 + 基础 UI | 2-3 周 |
| **v0.8.0-beta** | P1+P2: 完整战斗/社交/探索 + UI 全貌 | 6-8 周 |
| **v0.8.0** | P3+P4+P5: 设计模式 + 记忆压缩 + 思考链 + 缓存优化 | 12-15 周 |

---

## 九、图标包可用性

来源：`doc/game-icon-pack`（12 分类，~400+ SVG，含 `no-padding` 和 `padding` 版本）

### 关键图标映射

| 用途 | 可用图标 |
|------|---------|
| 游戏模式 | `1-game/dice.svg`, `dice-02/03/04/05/06`, `random-dice.svg` |
| 设计模式 | `1-game/puzzle-01..12`, `card.svg`, `cards.svg` |
| 设置 | `8-ui/settings.svg`, `settings-02.svg`, `slider.svg` |
| 存档 | `8-ui/save.svg`, `restore.svg`, `restore-02/03/04` |
| 背包 | `2-items/backpack.svg`, `chest.svg`, `ingot.svg`, `coin.svg` |
| 角色 | `1-game/character.svg`, `male.svg`, `female.svg` |
| 地图 | `2-items/map.svg`, `compass.svg`, `binoculars.svg` |
| HP/生命 | `1-game/health.svg`, `health-points.svg`, `heart.svg`, `heart-break.svg` |
| 护甲/AC | `3-gear/shield*.svg` (需确认具体文件名) |
| 攻击/武器 | `2-items/axe.svg`, `hammer.svg`, `3-gear/sword*.svg` |
| 骰子 | `1-game/six-sided-dice.svg`, `six-sided-dice-02..06` |
| 操作 | `8-ui/arrow-*.svg`, `expand*.svg`, `cross.svg`, `tick.svg`, `refresh.svg` |
| 搜索 | `8-ui/search.svg`, `zoom-in.svg`, `zoom-out.svg` |
| 锁定 | `8-ui/lock.svg`, `unlock.svg`, `prohibited.svg` |
| 死亡 | `1-game/death.svg`, `skull.svg` |
| 升级 | `1-game/level.svg`, `experience-points.svg`, `trophy.svg` |
| 技能 | `1-game/skill-points.svg`, `stamina.svg`, `mana-points.svg` |
| 幽灵/怪物 | `1-game/ghost.svg`, `demon.svg`, `demon-02.svg`, `boss.svg` |
| 金币 | `2-items/star-coin.svg`, `star-coin-02.svg`, `coin.svg`, `sycee.svg` |
| 药水 | `2-items/pill.svg`, `medical-kit.svg` |
| 钥匙 | `2-items/key.svg` |
| 工具 | `2-items/tool-kit.svg`, `wrench.svg`, `screwdriver.svg`, `scissors.svg` |
| 书本 | `2-items/book.svg` |
| 鱼钩 | `2-items/fishhook.svg`, `fishing-rod.svg` |

### 图标使用规范

- 所有图标使用 `no-padding` 版本（由 CSS padding 控制间距，避免 SVG 内边距不一致）
- 图标颜色通过 `currentColor` 继承（使用 Tailwind `text-*` 类控制）
- 图标尺寸统一为 `size-5`（20px）用于工具栏，`size-4`（16px）用于行内
- 所有图标引用路径相对于 `doc/game-icon-pack/svg/no-padding/`

---

> **版本**：v0.8.0
> **基于**：D&D 5e SRD 5.2.1 (CC-BY-4.0)
> **图标来源**：doc/game-icon-pack
> **生成日期**：2026-06-24
