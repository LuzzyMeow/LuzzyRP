# v1.5.0「助手」工作节点与交接档

> **最近更新**：2026-09-11 00:50（会话 51 · 助手页 UI 断层修复 P0+P1 收口并提交）
> **用途**：**上下文重置后的接手入口**。新 Agent 读完本档即可继续工作，无需回溯对话历史。
> **工作模式**：用户**一点一点提问题**，我一点一点改——**每次只改用户当前指定的那一处**，
>   不顺手改别的；改完立刻装机 + 截图验证 + 提交。
>
> ✅ **工作区已干净**：会话 49 / 50 / 51 全部已提交（会话 51 = 助手页 UI 断层修复 **P0+P1**：
>   契约自洽 + 机械对齐；明细见 §6）。单测 **331 绿**、门禁 **95 PASS / 0 FAIL**、
>   `assembleRelease` 通过（40.94 MB、单包、CN=LuzzyRP 签名一致）。
> ⚠ **真机验收仍未做**（执行机 `adb devices` 为空）——P1 的观感项（消息间距 32dp、用户气泡色差）
>   须装机目测。
> **下一步候选**：① 装 release 包真机走查 P1 观感项；② **P2 顶栏语言统一**——按硬性规定 9
>   **须先出 D1/D2/D3 三方向板给用户选定**（方向板未出，不得直接改）；③ 复测阻塞项
>   （STA1N 空 Key / `requestTools` 复原是否根治「永停生成中」）。

---

## 1. 一句话现状

W1（上游同步 RP-Hub 1.9.3）与 W2 P0–P4（助手原生 Agent 全部功能）**代码已完成**；
当前处于**用户真机逐项验收 + 增量优化**阶段：用户实测反馈 → 我定点修复 → 真机截图确认 → 提交。
**331 项单测 / 0 失败**、门禁 **86 PASS / 0 FAIL**、`assembleRelease` 通过。
**发版被用户明确暂缓**（手动验收通过后再发）。

**2026-09-10 两处口径变更（重要）**：
1. **真机体验包改为 release 签名包**（不再是 debug）——用户此后**先一步体验与用户相同的
   APK**，作为发布前的最后一道人工真机测试；手机上的 debug 包已卸载、其数据已清空。
2. **release 构建已开启 WebView 内容调试**（`WebViewSetup`），真机 CDP 诊断通道保留。

---

## 2. 环境与快速上手

构建 + 装机（设备：小米 `25098PN5AC` / Android 16 / 序列号 `df97f3c4`）：

    cd /d/.NekoTool/LuzzyRP
    ./gradlew :app:assembleRelease                                    # 真机用 release，不再用 debug
    adb -s df97f3c4 install -r app/build/outputs/apk/release/app-release.apk   # 同签名覆盖，数据保留
    apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk  # 期望 CN=LuzzyRP

回归自检（每次改动后必跑）：

    ./gradlew :app:testDebugUnitTest                                            # 期望 331 tests / 0 failed
    powershell -NoProfile -ExecutionPolicy Bypass -File tools/verify-markers.ps1 # 期望 86 PASS / 0 FAIL
    node tools/desktop-smoke.cjs                                                # 上游桌面冒烟（需先起 Chrome 远程调试）

**真机 CDP（帧率/布局/脚本耗时定量排查的唯一通道）**：

    adb -s df97f3c4 shell "cat /proc/net/unix | grep webview_devtools"   # socket 名内含 pid
    adb -s df97f3c4 forward tcp:9222 localabstract:webview_devtools_remote_<pid>
    # 然后 http://127.0.0.1:9222/json 取 webSocketDebuggerUrl
    # 帧率：dumpsys gfxinfo com.luzzymeow.luzzyrp reset → 触发交互 → dumpsys gfxinfo com.luzzymeow.luzzyrp

**真机操作要点（血泪经验）**：
- 通知横幅会**顶掉状态栏高度**，坐标每次可能不同 → **先 screencap 看当前画面，再按截图坐标 tap**；
- 侧栏 y 坐标随「在线/高级/助手」展开状态变化，不要用记忆中的坐标；
- **PowerShell 下不要写 `MSYS_NO_PATHCONV=1 <cmd>`**（那是 Git Bash 语法，会报
  `CommandNotFoundException`）；`$PID` 是 PowerShell 只读变量，不可用作变量名；
- 截图：`adb -s df97f3c4 shell screencap -p /sdcard/x.png` + `adb pull /sdcard/x.png D:/Temp/x.png`
  （`D:\Temp` 需已存在）；
- **改资产后 APK 重装会触发资产重解压 → 页面回到开屏**：开屏按钮是 `.lsp-dive-btn`
  （`.click()` 可关，但**开屏层在时会遮挡测量**，做帧率实验前务必先关掉）。

**参考克隆**：`rp-hub-reference/`（上游 1.9.3，锚定 `4aef0bb`）——查上游实现用。

---

## 3. 当前数据快照（核实于 2026-09-11 00:50）

| 项 | 值 |
|----|-----|
| 分支 | `main`，领先 `origin/main` **52 个提交**（未 push；本会话 +2：`76c68c6c` 断层修复、`a0fc0868` 坑表） |
| 工作区 | ✅ **干净**（会话 49/50 = `2b85d51d`；会话 51 = `76c68c6c` + `a0fc0868`；仅 `.workbuddy/` 未跟踪，非仓库产物） |
| 门禁 | **95 PASS / 0 FAIL**（2026-09-11 实跑） |
| 单测 | **331 用例 / 0 失败 / 0 错误**（34 个测试类，2026-09-11 实跑） |
| release APK | **40.94 MB**（R8 + 资源压缩；会话 51 重建，release 目录内**只有这一个包**） |
| 签名 | CN=LuzzyRP / SHA-256 `ed78235d…dfb1`（与上一版一致） |
| 设备端版本 | `1.4.0`（release 签名包；版本号在发版时才 bump 到 1.5.0） |
| 助手源码 | ~122 文件 / 15.6k 行 |
| 真机 | ⚠ **执行机 `adb devices` 为空**——本轮改动未装机验收 |


**最近 8 个提交**：

| 提交 | 内容 |
|------|------|
| `bf7e0cf1` | 侧栏「助手」改可折叠组 + 「在线/高级」子项统一为助手子项样式 |
| `6006eb98` | 图标渲染修复（复用原项目 SVG → VectorDrawable，弧线标志位分隔） |
| `c1adf223` | 真机比对两处对齐（参数图标 / 页面级保存按钮形态） |
| `8311eaca` | **P0 修复**：管理页设计一致性重构（ledger 组件库 + 设计契约补全） |
| `d86d39f9` | 记录 P0（助手页面未贯彻同一设计理念） |
| `b297c821` | 工作节点记录（现状快照） |
| `fe3e7d3f` | 助手首页改聊天页版式 + 菜单栏归 LuzzyRP |
| `c752fc43` | 卡顿修复 + DataStore 无限流卡死首轮对话 |

---


## 4. 架构与代码地图（助手部分）

    app/src/main/java/com/luzzymeow/luzzyrp/
    ├─ MainActivity.kt                  宿主：ComposeView 懒创建覆盖层；助手可见时暂停 WebView
    ├─ web/LuzzyBridge.kt               JS ↔ 原生桥（openAssistant / openAssistantAt / openRpSidebar / setAssistantConfig）
    ├─ assistant/
    │  ├─ AssistantController.kt        宿主控制接口（showAssistant(route) / hideAssistant / openRpSidebar / setThemeMode）
    │  ├─ domain/                       纯逻辑（不依赖 Android）
    │  │  ├─ tool/                      Tool/Schema/Ports/ToolRegistry/ApprovalGate/HardlineGuard/SsrfGuard/AuditSink
    │  │  ├─ tool/builtin/              内置工具（WebSearch/Calendar/SendToRpChat/RunCode/Terminal/Memory…）
    │  │  ├─ loop/                      AgentEvent / AgentLoop / BudgetGuard
    │  │  ├─ llm/                       OpenAI / Anthropic / Gemini 三协议 + RoutingTransport + SSE
    │  │  ├─ prompt/ContextBuilder.kt   系统提示词 + 记忆召回 + 技能注入（DataStore 取值必须用 flow.first()）
    │  │  └─ memory/ skill/ mcp/        向量检索 / 技能加载 / MCP（HTTP+stdio）
    │  ├─ data/                         Room 十表 + DAO（CJK bigram 检索）+ DataStore + SecretStore 接口
    │  ├─ runtime/                      接线层：AssistantRuntime / 各 Repository / proot 沙盒 / 加密存储
    │  └─ ui/
    │     ├─ AssistantHost.kt           根组件 + 路由 + 侧栏子项路由映射（fromSidebarRoute）
    │     ├─ AssistantRoute.kt          路由密封类
    │     ├─ component/ledger/          ★「卷宗」组件库（见 §5）——所有管理页必须用它
    │     ├─ component/                 ChatTopBar / ChatIcons / MessageComponents / InputIsland / AssistantAvatarStrip
    │     └─ screen/                    9 个页面：ChatScreen / ConversationsScreen / MemoryScreen / SkillsScreen /
    │                                   McpScreen / WorkspaceScreen / TerminalScreen / SettingsScreen / AssistantManagerScreen
    app/src/main/assets/
    ├─ rphub/                           ★上游 1.9.3 文件（只经 tools/patches 改，禁止直接编辑）
    └─ ext/                             ★扩展层（我们的代码）
       ├─ luzzy-assistant.js            「助手」侧栏折叠组注入 + 配置推送 + 主题联动
       ├─ luzzy-bridge.js               桥接封装（存在性检测 + 降级）
       ├─ luzzy-theme.css               ★Luzzy 主题层（986 行，只覆盖颜色、不改结构——设计真源之一）
       ├─ luzzy-ext.js / luzzy-changelog.js / luzzy-splash.js
       └─ assistant/                    字体 TTF（8 枚 21MB）+ 技能 md + proot 沙盒资产（4.1MB，GPL）

---

## 5. 设计契约与「卷宗」组件库（**改 UI 前必读**）

### 5.1 设计语言的三个真源

| 层 | 文件 | 作用 |
|----|------|------|
| 主题层 | assets/ext/luzzy-theme.css | **只覆盖颜色**（文件自述「只覆盖视觉，不改结构」） |
| 组件层 | assets/rphub/assets/css/styles.css | 组件规格（.settings-page-header / .settings-toggle 44×24 / .settings-collapse 0.36s / .advanced-nav 等） |
| 结构层 | assets/rphub/assets/js/ui-components.js + index.html | Vue 模板结构（侧栏、卡片、折叠行…） |

**结论：助手原生页 = 上游结构（Tailwind 类逐项复制）+ Luzzy token 取色。不发明组件。**

### 5.2 契约位置

DESIGN.md →「助手原生页（v1.5.0 · Compose 原生 UI）」章，含：
- 换算基线（**1 CSS px = 1 dp = 1 sp**）；
- **管理页组件规范**：15 个组件逐项标注上游类名与像素（页面头 48dp / 卡片 rounded-2xl + hairline +
  shadow-sm / 折叠行 图标方块 28dp + 状态文字 + chevron / 开关 **44×24dp** / 按钮高 32dp / 圆角 8·12·16 …）；
- **新增组件必须先在此登记再实现**（硬性规定 9 第 3 步）。

### 5.3 组件库 ui/component/ledger/

| 文件 | 内容 |
|------|------|
| LedgerTokens.kt | Ledger（尺寸常量）+ LedgerType（字级）——**全部取自上游 Tailwind 类** |
| LedgerIcons.kt | 20 枚图标 = @DrawableRes Int（指向 res/drawable/ic_lz_*.xml，**复用上游 SVG**） |
| LedgerComponents.kt | LedgerPageHeader / LedgerCard / LedgerCollapseCard / LedgerToggle / LedgerToggleRow / LedgerButton / LedgerIconButton / LedgerTextField / LedgerSearchField / LedgerListRow / LedgerEmptyState / LedgerStatusPill / LedgerSegmented |

**主题新增 token**：LuzzyColors.card（上游 bg-white：亮 #FFFFFF / 暗 #201E1B）——
**卡片填充必须用 card**，surfaceCard（#EFE9DE）是上游的**边框色**，别再用错。

**规格锁定测试**：LedgerTokensTest（8 项）——改规格必须先改 DESIGN.md，否则测试失败。

---


## 6. 最近工作节点（会话 43–46，全部已完成并提交）

### 会话 43 · 【P0】管理页设计一致性重构（8311eaca）

**用户反馈**：「助手页下各个页面的组件设计与摆放方式等均只是与其他页相似，而不是做到同一个设计理念」。
**根因**：① DESIGN.md 只规范了聊天页组件，管理页零规范；② 实现层临场发明组件（违反硬性规定 9 第 3 步）。
**修复**：补设计契约 + 建 ledger 组件库 + 八个页面全部改用 + 删除临场组件（PageHeader/SectionCard/Field/
ToggleRow/EmptyState/SearchField/MemoryCard/ConversationRow/SideDrawer）+ 新增 card token。

### 会话 44 · 真机视觉比对（c1adf223）

装机后与上游「用量统计」「记忆系统」逐项核对：页面头 / 白卡 / 折叠行 / 输入面 / 分段控件 / 空态**均已同构**。
比对后修两处：「参数」卡改用上游调节（sliders）图标；页面级「保存」改为上游白底描边按钮形态
（实心主按钮仅用于弹窗 CTA，与 .modal-primary-button 语义一致）。

### 会话 45 · 图标渲染修复（6006eb98）

**用户反馈**：「为什么你所有的圆形图标都是半圆状的啊」+「你能不能直接复用原项目已经画过的图标」。
**根因**：上游 SVG 用**紧凑弧线标志位**（a3 3 0 11-6 0），Compose 的 addPathNodes 把 11 当成一个数字
→ 圆心画成半圆、齿轮变花形。
**修复**：上游 SVG 的 d → res/drawable/ic_lz_*.xml（VectorDrawable，系统解析器）+ **显式分隔标志位**
（M 15 12 a 3 3 0 1 1 -6 0 3 3 0 0 1 6 0 z）+ painterResource 渲染。

### 会话 46 · 侧栏「助手」折叠组 + 子项样式统一（bf7e0cf1）

**用户要求**：「完全对齐如「在线」和「高级」这种可展开和收起式的选项，让助手下方的各个功能页可折叠收纳，
并且把「在线」和「高级」所展开的预设 世界书等子项，改 ui 成助手下方的子项的样式和大小」。

**实现**（全在 assets/ext/luzzy-assistant.js，未改上游文件）：
1. 复用上游折叠机制 .advanced-nav + .advanced-nav-trigger（chevron rotate(90deg)）+
   grid-template-rows 0fr↔1fr 0.32s cubic-bezier(.22,1,.36,1)；
2. 「助手」由单按钮改为**同款折叠组**，子项 8 个：**对话**（新增，打开首页）/ 会话 / 记忆 / 技能 /
   MCP / 工作区 / 终端 / 设置（触发按钮现在只负责展开收起，故首页入口下移为「对话」）；
3. 注入 CSS 统一**三组子项**规格：13px / 7px·10px 内边距 / 10px 圆角 / 16px 图标 / 左侧 hairline 竖线
   （亮暗双模式）——「在线」的 3 项与「高级」的 4 项现在与助手子项完全一致；
4. 助手可见时触发按钮高亮（bg-primary-50 text-primary-700，与上游激活组同款）。

**真机截图确认**：三组均可展开/收起，子项样式尺寸完全一致。

### 会话 48 · 真机体验包切 release + 侧栏折叠动效掉帧治理（2026-09-10）— **最新**

**用户两条指令**：① 卸载 debug 版、改装标准 release，**以后用户先一步体验与用户相同的 APK**
作为最后的人工真机测试；② 解决侧栏多级抽屉菜单项展开动画不流畅、跑不满刷新率。

**① 真机体验包切换**：三条事实先澄清（GitHub 最新 Release 是 v1.4.0 **不含助手**；debug/release
是两个应用 ID、删 debug = 真删 ≈51MB 真实数据；release 是 R8 + 不可调试、从未真机跑过）→
用户拍板「用当前 main 构建的 release 包」+「直接卸载 debug（不备份）」+「给 release 开 WebView 调试」。
执行：`WebViewSetup` 加 `setWebContentsDebuggingEnabled(true)` → `assembleRelease`（40.93MB）→
签名 **CN=LuzzyRP / SHA-256 `ed78235d…dfb1`** → 卸载 debug → `install -r` release → 冒烟通过
（**CDP 在 release 上打通**）。纪律写入 AGENTS §6.1/§7。

**② 动效掉帧治理（诊断 → 修复 → 复测）**：
- **诊断**：主线程不是瓶颈（每帧 layout 0.27ms + recalc 0.75ms + paint 0.6ms，rAF 稳定 8.3ms，
  `BeginFrame` P50 8.32ms）；**瓶颈是 GPU 栅格 ~5ms/帧 > 120Hz 的 8.33ms 预算**（gfxinfo GPU 中位 5ms、
  95 分位 10~14ms），超额帧落到下一 vsync。
- **修复**：`ext/luzzy-theme.css` 把 `.advanced-nav-panel` 从上游 `0.32s cubic-bezier(.22,1,.36,1)`
  收敛到本项目令牌 **进入 200ms / 退出 140ms / `cubic-bezier(0.23,1,0.32,1)`**（chevron 同拍）。
- **复测**：动画帧数 38 → 24，单次展开掉帧 ~1.4 → ~0.8（同场交替 A/B 19/20 → 8/8）。
  **掉帧率仍约 2~4%，本改动不消除**（GPU 地板）。
- **被实测排除**：独立合成层（`will-change`）、`contain: paint`（首测 −64% 系噪声，配对复测反向）、
  纯淡入（破坏展开语义）。
- **踩坑**：小样本掉帧对比不可信，**必须成对交替测量**（同变体三轮测出 14/5/10）。

### 会话 49 · 供应商模型列表改卡片 + 二级弹窗（patch 040）

用户指定「改为弹窗实现、编辑完单个模型后保持、以卡片列表展现」→ 卡片列表（显示名 / 类型徽标 /
模型 ID / 上下文·输出 / 模态 chips + 编辑·删除）+ 二级弹窗（复用上游 `modal-shell`，`z-[70]`）
+ **草稿副本、确定才原位写回**（取消不留痕）。真机全链路验收通过。
详版见 `docs/WORKLOG.md` 会话 49。**代码与日志均未提交**。

### 会话 50 · patch 041 识图重构 ／ 删「阿墨」／ 过渡动画 ／ 侧栏组移位（**未提交**）

| # | 改动 | 落点 |
|---|------|------|
| ① | **识图按需生效**：聊天模型原生支持图片 → `image_url` 直发、不调识图模型；不支持时才识图并以 user 身份注入「用户上传了一张图，图片内容为：…」。**删视频支持**（text/image only，全仓零残留） | patch 041（app.js + ui-components.js + index.html） |
| ② | **真修复**：复原 1.9.3 合并吞掉的 `const requestTools`——丢失会导致 `generateResponse` 抛 ReferenceError、**聊天全挂且永停「生成中」** | app.js（上游原状复原，不打标记） |
| ③ | **删除内置预设助手「阿墨」**：`ensureDefaultAssistant()` 整体删除 + 新增 `deleteAssistant()` + 管理页空态「新建助手」入口 | AssistantRepository / ListViewModel / ManagerScreen / Host |
| ④ | **进出助手过渡**：原生覆盖层 200/140ms + `cubic-bezier(.23,1,.32,1)`、`scale(.96)` 起步；WebView 侧 `.lsp-handoff` 协同（侧栏左收 + `.app-main` 左移 18px） | MainActivity / luzzy-theme.css / luzzy-assistant.js |
| ⑤ | **侧栏「助手」组移到「聊天」之下**；子项「对话」换气泡图标（与触发按钮去重） | ext/luzzy-assistant.js（零上游改动） |

**收口（G1–G3）**：`patches/README.md` 补 041 登记；index.html 补 041 注释 →
**实体 `012-035-index-html.patch` 重生成并逆向验证逐字节一致**（后像 `a853f0a` → `6b2498f`）；
WORKLOG 会话 50 + 本档 §3/§6/§12 同步。
**未做（会话 50 当时）**：单测 / 门禁 / 构建 / 真机 / 提交。

### 会话 51 · 助手页 UI 断层修复 **P0 + P1**（2026-09-11，**已提交**）

**接手背景**：会话 50 的 P0/P1 修复在 21:16–21:22 落地途中被中断，改动全部留在工作区，
且两处**处于不可编译状态**（`ChatTopBar` 误删仍在用的 `Column` import；
`LedgerTokensTest` 仍断言已删除的 `Ledger.CollapseDurationMs`）。

| 阶段 | 内容 | 落点 |
|------|------|------|
| **P0 契约自洽** | ① 折叠时长 360ms `.22,1,.36,1` → **展开 200 / 收起 140ms + `.23,1,.32,1`**（追认会话 48 的令牌，消除节奏分裂）；② §字体章**登记等宽族**（系统 `Monospace`，不再打包 TTF）；③ 新增**「聊天页组件像素规格」表**（用户气泡 / AI 气泡 / 间距 / 输入岛 / 审批卡 / 空态）；④ 明确 **2dp 描边覆盖含聊天页** | `DESIGN.md` |
| **P1 机械对齐** | ① **`ChatIcons.kt` 整文件删除**，聊天页顶栏/输入岛改用与管理页同一套 `LedgerIcons`（VectorDrawable 2dp）——新增 `Menu`（上游 `#icon-menu`）、`Send`（上游聊天纸飞机），零自绘；② 折叠面板改 `AnimatedVisibility(200/140, LuzzyMotion.EaseOut)`；③ 用户气泡 `surfaceCard` → **`#F1E3D9` + coral-300 边**（暗色 `#2E2119`/`#9A6244`，新增 `userBubble`/`userBubbleEdge` token）；④ 输入岛 `+`/`↑` 文字字符 → **24dp 线性图标**；⑤ 删除白字版空态 `ChatEmptyState`（收敛为唯一实现）；⑥ 气泡最大宽统一 **320dp**；⑦ 消息间距 48 → **32dp** | `ui/component/**`、`ui/theme/`、`res/drawable/ic_lz_{menu,send}.xml` |
| **顺手修的在途破绽** | `Column` import、规格测试（360 → 200/140；图标 19 → 22 枚）、KDoc 悬空、未接线常量、`DESIGN.md` 表格被块引用劈开、契约里的幽灵 `LedgerIcons.ArrowUp` | 同上 |
| **验证** | 单测 **331 / 0 失败**；门禁 **95 PASS / 0 FAIL**；`assembleRelease` 通过（40.94 MB，单包，签名一致） | — |

**未做**：真机装机与并排截图（执行机无设备）；**P2 顶栏语言统一**（A1 断层仍在，须先出
D1/D2/D3 三方向板给用户选，方向板未出）。

**审查档**：`docs/design/AUDIT-assistant-ui-parity.md`（已加执行状态与「执行记录」表）。

---

## 7. 已知问题 / 待办

### 7.1 我方自查的功能缺口（**优先级高**）

| # | 问题 | 状态 |
|---|------|------|
| R1 | **会话导出入口丢失**：原在助手抽屉的「导出」随抽屉删除后无新入口 | **未修** |
| R2 | **日历工具无运行时权限申请流程**：calendar_read/write 只做权限检查，App 内无申请入口 → 必然报「未授予权限」 | **未修** |
| R3 | 首页会话 id 在列表加载后固定一次；若该会话在 Web 端被删，首页行为未验证 | 待验证 |
| R4 | ensureConversation() 失败静默返回 | 待验证 |
| R5 | **侧栏折叠动画存在 GPU 栅格地板**：DPR 3.25 下每帧栅格 ~5ms，逼近 120Hz 的 8.33ms 预算，掉帧率约 2~4%（会话 48 实测）。已把时长收敛到设计令牌把绝对掉帧数减半；**进一步下降需 FLIP 级改造**（按行 transform 位移替代高度动画），工作量大且仍受地板限制 | **待用户决定** |
| **R6** | **助手页 UI 设计语言断层（用户 2026-09-10 反馈「感觉有断层」）**：聊天页穿「上游聊天页」皮（黑渐隐 + 白字 + 1.6dp 描边），管理页穿「上游设置页」皮（白底深字 + 2dp 描边），同一栈内两套语言；**且 DESIGN.md §Motion(200/140) 与 §管理页组件规范 #6(360ms) 自相矛盾**；另有用气泡未按契约、输入岛用文字符号当图标、终端用未登记的系统等宽字体 | **P0 + P1 已完成**（会话 51，2026-09-11）：契约自洽 + 机械对齐（含删除 `ChatIcons.kt` 统一图标来源、用户气泡落回 `#F1E3D9`、输入岛换 24dp 图标、间距 48→32dp）；**P2 顶栏统一（A1）未动**——须先出 D1/D2/D3 三方向板由用户选定；P1 观感项（间距/色差）**待真机目测**。明细：`docs/design/AUDIT-assistant-ui-parity.md`（含执行记录表） |

### 7.2 真机未验证的功能（**问题最可能藏在这里**）

| 项 | 状态 |
|----|------|
| **LLM 对话 / 流式渲染** | ❌ 未验证。**实测现象**：助手设置页显示「尚未读取到 Web 端供应商配置」——需确认 pushConfig 是否生效 |
| **proot 沙盒**（释放 → apk add python3 → 跑脚本） | ❌ 未验证 |
| 工具调用 + 审批弹窗（允许一次/本会话/拒绝） | ❌ 未验证 |
| ask_user 澄清卡 | ❌ 未验证 |
| 记忆写入 / 召回（中文 bigram 检索） | ❌ 未验证 |
| MCP（HTTP / SSE / stdio）连接与调用 | ❌ 未验证 |
| 技能生效（内置 3 个 + URL 导入） | ❌ 未验证 |
| 工作区读写 / 预览 / 配额 | ❌ 未验证 |
| 终端宿主模式 / 危险命令拦截 | ❌ 未验证 |
| 联网搜索（DuckDuckGo / SearXNG / Tavily / Brave） | ❌ 未验证 |
| 重启恢复（杀进程后会话与消息是否还在） | ❌ 未验证 |

### 7.3 脆弱点（改上游结构会失效）

| # | 风险 | 现有降级 |
|---|------|----------|
| T1 | 助手页汉堡依赖上游选择器 button svg use[href="#icon-menu"] | 返回 false 静默（汉堡无反应） |
| T2 | 侧栏「助手」组注入依赖 .sidebar-nav 与「外观」锚点 | 不注入（入口消失，主流程不阻断） |
| T3 | openRpSidebar 用 postDelayed(250ms) 等 WebView 恢复 | 无 |
| T4 | webView.pauseTimers() 是全局开关 | 无（当前单 WebView） |
| T5 | 流式节流固定 100ms | 无 |

---


## 8. 真机验收进度

    入口与版式      ████████░░  ~85%  （侧栏入口/折叠组/首页版式/汉堡回侧栏/子项跳转/三页视觉比对）
    管理页视觉      ████████░░  ~80%  （会话/记忆/设置已比对；MCP/工作区/终端未逐页比对）
    会话与对话      ░░░░░░░░░░    0%  （未验证，且疑似供应商配置未推送）
    工具与审批      ░░░░░░░░░░    0%
    沙盒与终端      ░░░░░░░░░░    0%
    记忆/技能/MCP   ░░░░░░░░░░    0%
    工作区          ░░░░░░░░░░    0%
    重启恢复        ░░░░░░░░░░    0%

---

## 9. 决策记录（累计）

| 编号 | 决策 |
|------|------|
| D1 | 上游同步用三方合并而非「覆盖后重放」（等价性已证明） |
| D2 | 实体补丁 worktree 用 CRLF，blob 用 LF |
| D3/D3b | 中文检索用 bigram + LIKE 兜底；标题单独走 LIKE 通道 |
| D5 | 审批默认拒绝（硬性要求 11） |
| D16 | 审计参数**只记键名与长度**（不回显值） |
| D17 | Gemini 用 x-goog-api-key 头而非 ?key=（密钥不入 URL） |
| D18 | 未知协议回退 OpenAI 兼容并记日志 |
| D19 | proot 以**未修改二进制**再分发 + SOURCES.md 三条源码获取途径（GPL 合规） |
| D20 | 最小 rootfs **不预装** node/python，缺失时提示 apk add |
| D22 | stdio 传输抽象化，协议层用内存假传输单测 |
| D23 | 摘要复用当前助手请求模板（不新增供应商配置） |
| D24 | 搜索默认 DuckDuckGo（无 Key）；Key 类 Key 走加密存储 |
| D26 | 自实现 Keystore AES-GCM（不用已停维护的 security-crypto） |
| D27 | 密钥**只写不读回**（设置页不回显） |
| **D28** | **组件规格以「上游类名 + 像素」为准；Compose 不发明样式，新增组件先在 DESIGN.md 登记** |
| **D29** | **图标语义就近映射上游已有图标，不自行绘制新形状** |

---

## 10. 踩坑表（新增项已入 AGENTS.md §7）

| 坑 | 结论 |
|----|------|
| Android 无 ProcessHandle | 子进程排空必须非阻塞轮询 |
| **AGP 会解压 .gz 资产并去掉后缀** | 读资产要两种名字都试 + magic bytes 判断 |
| **Compose addPathNodes 误读紧凑弧线标志位** | 上游 SVG 不要用 ImageVector 复刻 → 改用 VectorDrawable |
| Compose 编译器 mapping 类路径版本漂移 | resolutionStrategy 钉到项目 Kotlin 版本 |
| **DataStore flow 是无限流** | 取当前值必须 flow.first()，collect{} 会永久挂起 |
| PowerShell 文本管道丢尾换行 | 用 cmd 重定向读原始字节 |
| Kotlin 原始字符串里的 \n 是字面量 | 用 Python 改 Kotlin 源码时反斜杠会被吞，需 chr(92) |
| **shell heredoc > ~170 行会被截断** | 大文件用 write 工具或分块写 |
| **Python 正则 (.*?) 在长文本上灾难性回溯** | 会卡死终端；逐项处理，别整文件正则 |

---

## 11. 工作约定（用户已明确）

1. **一点一点来**：用户提一处，我改一处；不顺手改别的、不擅自扩大范围。
2. **不发版 release**：用户手动验收通过后再发；发版走 AGENTS §3.4。
3. **改 UI = 走设计门**（硬性规定 9）：先读 4 项设计 SKILL；若是「已选定方向后的迭代」
   （用户的定点改稿）按豁免情形 2 落档到 docs/design/direction-approved-assistant.md 即可，
   但**规格必须引上游真源，不得再自行发明**。
4. 每阶段更新 CHANGELOG.md + docs/WORKLOG.md（硬性规定）。
5. **禁止直接编辑 assets/rphub/**：上游文件只能经 tools/patches/ 注册补丁修改；
   我们的东西一律放 assets/ext/ 或 app/src/main/java/.../assistant/。

---

## 12. 用户待报问题登记

| # | 问题（用户原话摘要） | 处理 | 状态 | 提交 |
|---|----------------------|------|------|------|
| 1 | 助手页帧率太低，很卡 | 修 WebView 争抢 + @Immutable + remember + 100ms 节流；并修掉 DataStore 无限流阻塞缺陷 | ✅ | c752fc43 |
| 2 | 助手首页应做成 LuzzyRP 聊天页版式，右上角改设置按钮 | 已实现 | ✅ | fe3e7d3f |
| 3 | 原菜单栏应归 LuzzyRP；助手不再有独立抽屉 | 已实现（方案 A） | ✅ | fe3e7d3f |
| 4 | 助手各页组件只是「相似」而非同一设计理念 | 设计契约补全 + ledger 组件库 + 八页改造 | ✅ | 8311eaca 等 |
| 5 | 圆形图标全是半圆状；应直接复用原项目图标 | 改 VectorDrawable + 弧线标志位分隔 | ✅ | 6006eb98 |
| 6 | 侧栏「助手」应做成「在线/高级」式折叠；在线/高级子项改用助手子项样式 | 已实现 | ✅ | bf7e0cf1 |
| 7 | 侧栏多级抽屉菜单项展开动画不流畅、跑不满刷新率 | 诊断到 GPU 栅格瓶颈；折叠时长收敛到设计令牌（200/140ms），掉帧绝对数减半 | ✅ | 会话 48 |
| 8 | 卸载 debug 版、改装 release；以后用户先一步体验相同 APK 作最后人工真机测试 | 已切换（release 签名包 + WebView 调试开启），纪律写入 AGENTS §6.1 | ✅ | 会话 48 |
| 9 | 开屏动画最开始总是卡一下，要求不牺牲视觉效果下跑满帧率 | 定位到「遮挡期应用主体仍在渲染」；冷启动 UI 帧时中位 17ms→**7ms**、legacy 掉帧 80.69%→**4.1%**，视觉零差异 | ✅ | 会话 48 追记 |
| 10 | 供应商「添加模型」改弹窗、编辑完单个模型保持、卡片列表展现 | patch 040：卡片列表 + 二级弹窗（草稿副本，确定才原位写回）；真机全链路验收通过 | ✅ | 会话 49 |
| 11 | **配置好模型后无法正常聊天（阻塞项）** | **两条线索**：① 激活供应商 STA1N 的 **API Key 长度 = 0**（全局 apiKey 有 51 字符）→ 请求带空 Key 打 `cdn.sta1n.cn/v1` 必然 401/403；② **1.9.3 合并吞掉了 `const requestTools` 声明** → `generateResponse` 必抛 ReferenceError（会话 50 已复原，未实测）。用户指示「第二项我们再测试」→ 待复测 | ⏳ 待用户复测 | 会话 50（复原未提交） |
| 12 | 识图应只在模型不支持图片时生效；删除视频支持 | patch 041：原生多模态 `image_url` 直发 + 不支持时才识图注入；视频模态全仓清理 | ✅ 代码完成 | 会话 50（未提交） |
| 13 | 不要内置预设助手「阿墨」 | `ensureDefaultAssistant()` 删除 + `deleteAssistant()` + 空态「新建助手」入口 | ✅ 代码完成 | 会话 50（未提交） |
| 14 | 进/出助手切换生硬 | 覆盖层 200/140ms 过渡 + WebView 侧 `.lsp-handoff` 协同转场 | ✅ 代码完成 | 会话 50（未提交） |
| 15 | 侧栏「助手」组位置 + 「对话」图标与触发按钮重复 | 组移到「聊天」之下；「对话」改气泡图标 | ✅ 代码完成 | 会话 50（未提交） |
| 16 | **助手页相关页面是不是不符合其他页面的设计语言、感觉有断层** | 审查档 `docs/design/AUDIT-assistant-ui-parity.md`（A1 两套顶栏语言 / A2 图标描边 / A3 折叠节奏三套 / B1 用户气泡 / B2 输入岛文字图标 / B3 未登记等宽字体 / C1-C4）。**P0+P1 已完成**（会话 51）：契约自洽 + 机械对齐（删 `ChatIcons.kt` 统一图标来源、气泡落回契约色、输入岛 24dp 图标、折叠 200/140、空态收敛、间距 48→32dp；单测 331 绿 / 门禁 95 绿 / release 构建通过） | ⏳ **P2 未动**（顶栏统一须先出三方向板给用户选）；P1 观感项待真机目测 | 会话 51 |
| 17 | （待用户提出下一处） | — | — | — |

