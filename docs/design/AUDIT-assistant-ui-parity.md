# 助手页 UI 设计语言断层审查档（2026-09-10）

> **性质**：设计**评审**档（huashu-design `references/critique-guide.md` 输出结构）+ **修复计划**。
> **触发**：用户反馈「关于助手页的相关页面，你觉得有没有不符合其他页面的设计语言，感觉有断层？」
> **范围**：`app/src/main/java/.../assistant/ui/**`（Compose 原生）与 `assets/rphub/**`（上游 Web）的对照。
> **方法**：代码层静态审查 + `DESIGN.md` 契约逐项核对。**本次未做真机截图比对**（设备 `df97f3c4` 未连接），
> 结论中凡涉及观感的部分，实施前须真机复验。
>
> **执行状态（2026-09-10 更新）**：**P0 ✅ · P1 ✅ · P2 ✅（用户免除三方向门，裁定 D1 纸面页头）** ·
> **P3 ⏳ 真机并排截图待设备**。落地明细见 §3 末「执行记录」。

## 0. 设计门执行记录（硬性规定 9）

| SKILL | 读取内容 | 用于本档何处 |
|-------|---------|-------------|
| open-design | `AGENTS.md` §UI animation philosophy（ease-out `cubic-bezier(.23,1,.32,1)`、进 200 / 出 140、`grid-template-rows 0fr→1fr`、禁 `scale(0)`） | A3 动效令牌裁定依据 |
| huashu-design | `SKILL.md` 核心哲学（反 AI slop、系统优先、三方向硬门与**三种豁免**）、`references/critique-guide.md`（六维评分 + Top10 问题） | 评审结构、P2 门控判定 |
| ui-ux-pro-max | `CLAUDE.md`（检索与 stack 用法） | 未触发深入检索（本项目有自有契约，不套通用库） |
| awesome-design-md | DESIGN.md 九段格式（Colors/Typography/Layout/Elevation/Shapes/Components/Motion） | 判定 DESIGN.md 章节缺口 |

---

## 1. 总体结论

**有断层，且不止一处。** 评分（critique-guide 六维，仅评「哲学一致性 / 细节执行」两项，其余依赖真机）：

| 维度 | 分 | 一句话 |
|------|----|--------|
| 哲学一致性 | **5/10** | 方向 A「卷宗 Ledger」选定后，聊天页仍穿着**上游聊天页**的皮、管理页穿着**上游设置页**的皮，两套并置 |
| 细节执行 | **6/10** | 管理页（ledger 组件库）执行到位；聊天页是**逐项临场复制**上游像素，未过组件库 |

**根因（一句话）**：助手 = 「上游聊天页皮肤」+「上游设置页皮肤」的拼装。这两套在上游本属不同场景
（聊天页压在角色背景图上，故黑渐隐 + 白字；管理页是纯白管理台，故白卡片 + 深字），
**搬进同一个原生容器后没有做身份统一**——用户在同一个栈里切换时，等于换了一整套视觉语言。

---

## 2. 断层清单

### A 级 · 切换时肉眼可见

| # | 断层 | 证据 | 契约出处 |
|---|------|------|----------|
| A1 | **两套顶栏语言**：聊天页 = 112dp 黑渐隐 + 白字 + 32/36dp 圆形点击区；管理页 = 白底 + 20sp Bold 深字 + 24dp primary 图标 + 40dp 方钮 | `component/ChatTopBar.kt:45-137` vs `component/ledger/LedgerComponents.kt` `LedgerPageHeader` | §组件映射 vs §管理页组件规范 #1 |
| A2 | **图标描边两套**：聊天页 1.6dp / 1.5dp，管理页 2dp | `component/ChatIcons.kt:28,58,84` | §管理页组件规范 #15 规定全站 2dp |
| A3 | **折叠节奏三套并存**：Web 侧栏 200ms(.23,1,.32,1) ／ 助手管理页 360ms(.22,1,.36,1) ／ 路由·覆盖层 200/140ms | `LedgerTokens.kt:56` `CollapseDurationMs = 360`；会话 48 已把 Web 侧栏收敛到 200ms | **§Motion 与 §管理页组件规范 #6 自相矛盾** |

> A3 是**契约自身的 bug**：会话 48 已把项目令牌定为 200/140 + `.23,1,.32,1`，
> 但管理页组件规范第 6 项仍抄着上游 `.settings-collapse` 的 `0.36s cubic-bezier(.22,1,.36,1)`。

### B 级 · 契约写了没落地 / 临场发明

| # | 问题 | 证据 | 契约 |
|---|------|------|------|
| B1 | **用户气泡未按契约**：契约「`#F1E3D9` 底 + coral-300 边」，实为 `surfaceCard #EFE9DE` + `accentGraphic@35%`。与 AI 气泡 `#F5F0E8` 几乎同色，身份区分只剩右对齐 + 一条 35% 透明边 | `component/MessageComponents.kt:70-80` | §组件映射「用户气泡」 |
| B2 | **输入岛 `+` 与 `↑` 是 Text 字符**，非图标；字形随字体/系统漂移 | `component/InputIsland.kt:62,99` | §管理页组件规范 #15（全站 24dp 线性图标） |
| B3 | 终端/工具名用系统 `FontFamily.Monospace`，**字体章未登记** | `screen/TerminalScreen.kt:128` | §字体（硬性规定 9 第 3 步：新组件先登记） |
| B4 | 聊天页**未使用 ledger 组件库**（气泡/顶栏/输入岛各自手搓），与八张管理页不同源 | `ChatScreen.kt` 整体 | §管理页组件规范总原则「不发明组件」 |

### C 级 · 细节与死代码

| # | 问题 | 证据 |
|---|------|------|
| C1 | **两个空态实现**：`ChatTopBar.ChatEmptyState`（白字，配黑渐隐）与 `ChatScreen` 内联空态（深字，配 canvas）。前者若被调用会出现「白字压浅底」 | `ChatTopBar.kt:156-166` vs `ChatScreen.kt:101-118` |
| C2 | 气泡最大宽：用户 300dp / AI 320dp，**无出处** | `MessageComponents.kt:72,92` |
| C3 | 消息间距 48dp（复制上游 `space-y-12`，那是为角色头像/时间留白）；助手气泡无头像，48dp 显散 | `ChatTopBar.kt:153` |
| C4 | 导航隐喻两套：聊天页汉堡→侧栏（上游聊天页行为），管理页页面头返回箭头→回首页 | 全局 |

---

## 3. 修复计划（分阶段，按依赖顺序）

> **工作模式遵守**：用户一点一点提、我一点一点改。以下为**总计划**，每次只执行用户当前指定的一项，
> 改完立即装机 + 截图验证 + 提交（AGENTS.md §工作约定 1）。

### P0 · 契约自洽（先改真源，再动代码）

**门控判定**：属「修契约 bug + 补登记」，非新视觉设计 → **豁免情形 2**，按 AGENTS §硬性规定 9 落档本文件即可。

| 任务 | 内容 |
|------|------|
| P0-1 | `DESIGN.md` §管理页组件规范 #6 折叠时长 **360ms → 200/140ms + `cubic-bezier(.23,1,.32,1)`**，与 §Motion 统一；注明「会话 48 已把 Web 侧栏收敛到同值，此处为追认」 |
| P0-2 | `DESIGN.md` §字体章**登记等宽族**（终端/工具名用）：明确取 `assets/assistant/fonts/` 内哪一枚或确认用系统等宽（需用户确认是否值得再打包一枚等宽 TTF，体积权衡） |
| P0-3 | `DESIGN.md` **补登记**聊天页三个未登记组件：**用户气泡 / 输入岛图标 / 澄清卡·审批卡**（现只有 §组件映射一句，无像素规格） |
| P0-4 | `DESIGN.md` §管理页组件规范 #15 明确「**图标描边 2dp 适用于全部助手页，含聊天页**」，消灭 A2 的两种解释 |
| P0-5 | 同步 `LedgerTokensTest`（规格锁定测试）：改规格必须改测试，否则失败 |

**验收**：`grep` 契约全文无 360ms 残留；`testDebugUnitTest` 通过（规格测试绿）。

### P1 · 机械对齐（不动视觉决策，风险最低）

**门控判定**：全部是「把实现对齐到**已存在**的契约」，零新增色相/零新组件类型 → **豁免情形 2**。

| 任务 | 内容 | 涉及文件 |
|------|------|----------|
| P1-1 | 聊天页图标描边 1.6/1.5 → **2dp**（与 ledger 一致） | `ChatIcons.kt` |
| P1-2 | 管理页折叠 **360ms → 200ms / 140ms** + `.23,1,.32,1` | `LedgerTokens.kt` + `LedgerComponents.kt` |
| P1-3 | 用户气泡 → **`#F1E3D9` 底 + coral-300 边**（契约值） | `MessageComponents.kt`（需先在主题色里加 `userBubble` 或直接使用契约 hex + 暗色对应值） |
| P1-4 | 输入岛 `+` / `↑` → **24dp 线性图标**（复用 `LedgerIcons` 语义就近图形，不自绘；发送键沿用 coral 实心圆 + 白色箭头图标） | `InputIsland.kt`、`LedgerIcons.kt` |
| P1-5 | 删除 `ChatEmptyState`（白字版）或改为深字，统一为**一个**空态实现 | `ChatTopBar.kt` / `ChatScreen.kt` |
| P1-6 | 气泡最大宽统一（取契约或给一个出处：建议两侧同为 320dp 或按屏宽 0.82） | `MessageComponents.kt` |
| P1-7 | 消息间距 48dp → 与「无头像气泡」匹配的密度（**需真机目测**后定值，先记候选 24/32dp） | `ChatTopBar.kt` 常量 |

**验收**：单测 331 绿；`verify-markers` 95 绿（若未改上游文件）；**真机并排截图**（聊天页 vs 上游聊天页、助手设置页 vs 上游设置页）。

### P2 · 顶栏语言统一（**唯一需要设计门的方向性决策**）

**门控判定**：这是**新的视觉设计决策**（会在屏幕上产生新形态），按 huashu 三方向硬门 **必须出方向板让用户选**，
不适用豁免。三个候选方向（均基于同一套 token，不新增色相）：

| 方向 | 做法 | 气质 | 代价 |
|------|------|------|------|
| **D1 纸面页头** | 聊天页顶栏改为管理页同源：canvas 底 + 深字 20sp Bold + 24dp primary 图标 + 40dp 方钮，黑渐隐**整体取消** | 工具感、与全 App 同源；**推荐** | 失去上游聊天页的沉浸感；与「助手首页 = 聊天页版式」这条用户指定有出入，需用户重新拍板 |
| **D2 统一深色页头** | 管理页也上 112dp 黑渐隐 + 白字页头 | 保留沉浸感 | 白卡片上压深条，与「雾纸/暖幕手记」的主题气质冲突；暗色模式下更怪 |
| **D3 纸面 + 滚动渐显分隔** | 两侧统一为纸面页头；聊天页滚动超过一屏时页头才浮现 hairline（或极淡渐隐） | 兼顾：静止时干净、滚动时有层次 | 多一处状态逻辑；需真机调参 |

**流程**：先出 D1/D2/D3 三块**方向板**（HTML 或 Compose 预览截图，亮暗双框同一聊天场景）
→ 用户选定 → 写入 `docs/design/direction-approved-assistant.md` → 实施 → 真机验收。

### P3 · 收口与验收

| 任务 | 内容 |
|------|------|
| P3-1 | 真机并排比对：助手聊天页 / 设置页 / 记忆页 **vs** 上游对应页（颜色、间距、圆角、字重、图标重量） |
| P3-2 | 走查九张助手页 + 聊天页的**图标描边与折叠节奏**是否全数统一（P1 的回归面） |
| P3-3 | 单测 + `verify-markers` + `assembleRelease` + 装机 |
| P3-4 | CHANGELOG v1.5.0「优化」段登记；WORKLOG / STATUS 同步；提交 |

### 执行记录（2026-09-10 · 会话 51）

| 阶段 | 状态 | 落地内容 | 证据 |
|------|------|---------|------|
| **P0** | ✅ 完成 | ① `DESIGN.md` #6 折叠 360→200/140 `.23,1,.32,1`（上游值移入「上游出处」列）；② §字体章登记等宽族（系统 `Monospace`，不新增 TTF）；③ 新增「聊天页组件像素规格」表；④ 明确 2dp 描边覆盖含聊天页；⑤ `LedgerTokensTest` 同步（200/140、图标 22 枚） | 契约全文 `360` 仅存于「上游出处」对照句；单测 331 绿 |
| **P1-1** | ✅ 完成（比原计划彻底） | **`ChatIcons.kt` 整文件删除**，聊天页顶栏/输入岛改用同一套 `LedgerIcons`（VectorDrawable，2dp）；新增 `Menu`（上游 `#icon-menu`）、`Send`（上游聊天纸飞机），零自绘 | 新增 `ic_lz_menu.xml` / `ic_lz_send.xml`；`git grep ChatIcons` 零命中 |
| **P1-2** | ✅ 完成 | `LedgerTokens.CollapseDurationMs(360)` → `CollapseExpandMs(200)` / `CollapseCollapseMs(140)`；`LedgerComponents` 改用 `LuzzyMotion.EaseOut`，**收起也走令牌**（原 `exit` 为无缓动 200ms） | `LedgerTokens.kt` / `LedgerComponents.kt` |
| **P1-3** | ✅ 完成 | 用户气泡 `surfaceCard` + `accentGraphic@35%` → `userBubble` `#F1E3D9` + `userBubbleEdge` coral-300；暗色 `#2E2119` / `#9A6244` | `LuzzyAssistantTheme.kt` / `MessageComponents.kt` |
| **P1-4** | ✅ 完成 | 输入岛 `Text("+")` / `Text("↑")` → `LedgerIcons.Plus` / `LedgerIcons.Send`（24dp，`Ledger.IconSize`） | `InputIsland.kt` |
| **P1-5** | ✅ 完成 | 删除白字版 `ChatEmptyState`，空态收敛为 `ChatScreen` 的深字实现（唯一） | `ChatTopBar.kt`；`git grep ChatEmptyState` 零命中 |
| **P1-6** | ✅ 完成 | 气泡最大宽两侧统一 `CHAT_BUBBLE_MAX_WIDTH = 320.dp`（原 300 / 320 两个字面量） | `MessageComponents.kt` |
| **P1-7** | ⚠ 落地，**待真机目测** | 消息间距 48 → 32dp（新增 `CHAT_MESSAGE_SPACING` 说明与回退候选 24dp） | `ChatTopBar.kt`；执行机 `adb devices` 为空 |
| **P2** | ✅ 完成（**用户免除三方向门**） | 用户原话「顶栏语言统一，此任务本次免去三方向」→ 属 huashu「唯一豁免」第 1 条，**不出方向板**，依本档候选**裁定 D1 纸面页头**（D2 与主题气质冲突；D3 = D1 + 一处在本实现下无触发场景的滚动状态）。落地：`ChatTopBar` 重写为 `LedgerPageHeader` 同骨架（48dp 行 + 水平 16dp + 下方 16dp、canvas 底、**黑渐隐整体取消**、页头由覆盖层改为**入流**、图标全部取 `LedgerIcons` 2dp），保留用户指定的内容差异（头像 + 助手名 + 会话标题 + 右上角设置） | `ChatTopBar.kt` / `ChatScreen.kt` / `DESIGN.md` §聊天页组件像素规格「页头」行；决策落档 `direction-approved-assistant.md` |
| **P3** | ⏳ 部分 | 单测 / 门禁 / `assembleRelease` 已跑；**真机并排截图未做**（无设备）；CHANGELOG 已登记 | CHANGELOG v1.5.0「优化」段 |

**附带修复（在途改动自身的不一致，非新增范围）**：`ChatTopBar` 删组件时误删仍在使用的
`Column` import（**会编译失败**）；`MessageComponents` 的新常量 KDoc 插进了 `MessageItem`
的文件级 KDoc 与函数之间（使原 KDoc 悬空）；`CHAT_CONTENT_BOTTOM_PADDING` 定义后未接线
（`ChatScreen` 仍写字面量 `16.dp`）——三处均已修正。

---

## 4. 验收标准（可判定）

1. **契约自洽**：`DESIGN.md` 全文搜索 `360` / `0.36s` 结果为 0；`cubic-bezier(.22,1,.36,1)` 仅剩 0 处（或仅存于「上游出处」说明句）。
2. **单值可查**：图标描边、折叠时长、页面头高度、气泡色值在代码里各只有**一个**定义点（token 常量），无散落字面量。
3. **并排截图**：助手设置页与上游设置页并排，卡片/折叠行/开关/按钮肉眼无差异；助手聊天页与上游聊天页并排，
   除**经用户拍板的差异点**外无意外差异。
4. **规格测试**：`LedgerTokensTest` 全绿（改规格必须改测试）。

## 5. 风险与依赖

| 风险 | 说明 | 对策 |
|------|------|------|
| ~~P2 方向未定~~ | **已解除**（2026-09-11）：用户免除三方向门并裁定 D1 纸面页头，A1 断层已消除 | — |
| 真机缺失 | 本次审查为代码层结论，观感类（C3 间距、B1 色差、P2 页头观感）需目测 | 实施前请连接设备 `df97f3c4`；本轮 P1-7 / P2 的观感项标「待真机复验」 |
| 未提交改动堆积 | 会话 49+50 共 21 文件在途 | 已解决：会话 49/50 于 `2b85d51d` 提交，P0/P1 于 `76c68c6c` 提交 |
| 暗色模式 | B1 用户气泡、P1-3 的 coral-300 边需暗色对应值 | 已按 DESIGN.md 暗色表补 `userBubble` `#2E2119` / `userBubbleEdge` `#9A6244`；**暗色观感仍待真机** |

## 6. 依据

- `docs/WORKLOG.md` 会话 48（动效令牌收敛）、会话 50（本审查）
- `docs/STATUS-v1.5.0-assistant.md` §5（设计契约与 ledger 组件库）、§12（待报问题表第 16 项）
- `DESIGN.md` §Motion、§字体、§组件映射、§管理页组件规范
- 代码：`ChatTopBar.kt` / `ChatIcons.kt` / `InputIsland.kt` / `MessageComponents.kt` / `LedgerTokens.kt` / `TerminalScreen.kt`
