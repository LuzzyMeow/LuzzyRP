# 更新日志（CHANGELOG）

> LuzzyRP 遵循语义化版本（`MAJOR.MINOR.PATCH`）；`x.y.0` 视为稳定版并附 APK。
> 格式：`### vX.Y.Z — 标题` + 「新增 / 优化 / 修复 / 注意事项」分类要点 + 构建结果与 versionCode。
> **v1.0.0 起：每条记录注明上游基线版本（RP-Hub）。** 旧 v0.x 记录保留于下方历史区。

### v1.5.0 — 开发中 · 文档定位澄清 × 发布纪律固化 × 「助手」原生 Agent × 同步上游 1.9.3（上游基线 RP-Hub 1.9.2 → 1.9.3）

> **状态：开发中（2026-09-09 起）。** 本版含三条工作流：① 文档与纪律更新（已完成）；
> ② **上游同步**（上游在 1.9.2 基线后又新增 4 个提交，本版合并，见下「同步」段）；
> ③ **「助手」原生 Agent**（原生 Kotlin 页 + 手机端 Agent，实施计划见
> `docs/PLAN-v1.5.0-assistant.md`）。**尚未发布**，最新可下载版本仍为 **v1.4.0**。

**新增**
- **「助手」原生页可行性调研（`docs/RESEARCH-assistant-native-agent.md`）**：调研菜单栏新增
  原生 Kotlin 页面并实现手机端 Agent 的可行性，参考 [rikkahub](https://github.com/rikkahub/rikkahub)
  与 [rikkahub-agent](https://github.com/ExTV/rikkahub-agent)。结论：**可行**，推荐「同 Activity
  原生覆盖层 + 扩展层 DOM 注入侧栏入口 + 桥接复用 Web 端供应商配置」方案；已给出四阶段路线、
  依赖选型（Compose/Room/OkHttp/kotlinx-serialization 本地缓存齐备）、风险与红线清单。
  **调研明确不含 UI 视觉设计**（按硬性规定 9，进入界面设计阶段前须先完整阅读 4 项设计 SKILL
  并走三方向硬门）。
- **「助手」Agent 实施级计划（`docs/PLAN-v1.5.0-assistant.md`）**：经用户澄清后定稿的实施方案——
  九项需求（多助手 / 会话历史 / 记忆嵌入 / Skill / MCP / 独立工作区 / 双模式终端 /
  提示词与模型设置 / 渲染）+ 追加工具（澄清提问 / 系统时间 / 程序编译 / 日历读写 / 记忆工具）
  + Room 10 表数据模型 + U/P0-P4 阶段路线与验收标准 + R1-R13 风险清单。**本文只定义信息架构，
  不含视觉设计**。
- **「助手」原生页 P0 骨架（原生 Compose，方向 A · 卷宗）**：
  - **设计门（硬性规定 9 全流程）**：完整阅读 4 项设计 SKILL → 产出 **3 个差异化方向**
    （A 卷宗 / B 工作台 / C 场记，各含 1080×2400 亮暗双截图与 IA·栅格·密度·动效规格，
    见 `docs/design/assistant-v1/`）→ **用户选定 A · 卷宗**（记录见
    `docs/design/direction-approved-assistant.md`）→ 写入 `DESIGN.md`「助手原生页」章。
  - **宿主接线**：`MainActivity` 懒创建 `ComposeView` 覆盖层（WebView 保持存活）+
    返回键三级优先级（抽屉 → 二级页 → 退出助手）；桥接新增 `openAssistant` /
    `isAssistantVisible` / `setAssistantConfig` / `getAssistantConfig` / `setAssistantThemeMode`。
  - **侧栏入口**：`assets/ext/luzzy-assistant.js` DOM 注入（零上游改动）+ `luzzy-ext.js` 动态加载；
    桌面实测按钮位于「外观」之上、点击降级提示正常、零 JS 异常。
  - **界面骨架**：会话列表（家）/ 会话页 / 记忆页 / 全部助手管理页 + 右侧抽屉六项
    （技能·MCP·工作区·终端·设置给「规划中」占位）；组件含头像条、会话行、气泡、思考卡、
    工具卡、步骤组、输入岛、审批弹窗、记忆卡、检索框。
  - **主题与字体**：`LuzzyAssistantTheme` 落地 DESIGN.md 全部 token（亮/暗双模式、圆角、动效常量）；
    字体按用户选定「全量 1:1 复刻」——`tools/assistant-fonts.py` 把上游 woff2 转为 8 枚 TTF
    （Lora + AlibabaSans + Alibaba PuHuiTi 3.0 三字重，约 21.2MB），正文/UI 用 PuHuiTi、
    display 用 Lora。
  - **契约与安全内核（纯 Kotlin，43 项单测全绿）**：`Tool` / `ToolResult` / `ToolContext` /
    `AgentEvent` / `LlmTransport` / JSON Schema DSL / 工具调用分片累加器 / 宽松 JSON 解析；
    **HARDLINE 危险命令无条件拦截**（9 类模式）、**SSRF 防护**（DNS 解析层拒绝私网/回环/
    链路本地/保留地址 + IPv4 映射 IPv6）、**三层审批门**（每工具开关 / 逐调用审批 /
    本会话始终允许）。
  - **数据层**：Room 十表（`assistant` / `conversation` / `message` / `memory` / `skill` /
    `skill_binding` / `mcp_server` / `mcp_binding` / `tool_audit` + FTS4 检索镜像）+ DataStore 偏好
    + `WorkspaceManager`（每助手独立工作区、路径越界/符号链接穿越拒绝、配额 2GB/64MB）。
  - **构建接入**：AGP 9.2.1 内置 Kotlin + Compose 编译器插件 + KSP/Room + kotlinx-serialization
    全部打通（计划 R2 风险解除），`assembleDebug` 通过。
- **「助手」P1 最小可用 Agent（已闭环）**：
  - **领域核心**：`AgentLoop`（严格 §5.2 事件顺序：TurnStarted → 流式推理/正文/用量 →
    工具逐个审批执行 → 结果回灌 → 下一轮；工具异常统一转 Error 不中断；`ask_user` 暂停本轮；
    取消/超预算给出明确 reason）+ `BudgetGuard`（轮次 24 / 工具超时 120s / 总时长 30min /
    token 预算）+ `ContextBuilder`（六变量替换 + 技能注入 + 记忆块 + 工具约定 + 超限压缩）
    + `OpenAiTransport`（SSE 分帧、delta 解析、重试 2 次指数退避、`extraBody` 禁覆盖
    messages/tools/stream/model、密钥只进 Authorization 头且错误体不回显）。
  - **内置工具 18 个**：`ask_user` / `get_time` / `get_device_info` / `clipboard_read·write` /
    `workspace_list·read·write·patch·delete·move·mkdir` / `memory_write·search·update·delete·list` /
    `web_fetch` / `terminal_run` / `run_code`。
  - **记忆引擎**：`VectorMath`（float32 小端 + 余弦）、`Retriever`（full/embed/hybrid，
    TopK 8 / 阈值 0.35 / 最近 5 去重，失败自动降级全文）、`EmbeddingClient`（OpenAI 兼容
    `/embeddings`，批 ≤32、重试 1 次、密钥不落日志）、`RoomMemoryStore`（含后台补嵌与 LIKE 兜底）。
  - **运行时**：`AssistantRuntime` 装配（工具注册 + 配置解析 + 工作区适配）、Android 端口
    （设备信息 / 剪贴板 / 时钟）、`GlobalShellRunner`（宿主 sh、双层 HARDLINE、200KB 输出上限
    + 溢出落盘、**非阻塞排空修正超时失效**）。
  - **UI 闭环**：会话页状态机（AgentEvent → 思考卡/工具卡/步骤组；**审批弹窗**「允许一次 /
    本会话始终允许 / 拒绝」；**澄清提问卡**；停止键协作式取消；错误条）——UI ↔ ViewModel ↔
    AgentLoop ↔ 传输/工具全线打通。
  - **验证**：全仓 **213 项单测 / 0 失败**（领域 95 + 安全契约 71 + 数据 42 + 运行时 5），
    `assembleDebug` 通过。- **「助手」P2 持久化与真实数据接线（进行中）**：
  - `AssistantRepository`：助手/会话/消息用例接口——首启自动建默认助手；会话增删改归档；
    消息一律走 `MessageDao` 的 `*Indexed` 事务方法（FTS 索引随写）；首条用户消息自动成标题；
    **中文检索双通道**（正文 FTS bigram → 无命中回落 LIKE；标题走 LIKE）；会话导出 MD / JSON；
    索引幂等重建。
  - **重启恢复**：会话页从 Room 恢复历史（P1 验收项「会话重启后完整恢复」达成）；
    用户消息先落库再跑循环，助手消息收尾落库（含思考内容 / 状态 / token 用量）。
  - **真实数据接线**：会话列表（`AssistantListViewModel`：日期分组 + 相对时间）与记忆页
    （`MemoryViewModel`：类型/时间/相似度 + 模式条取自助手设置）均改为读 Room，不再用示例数据。- **「助手」P2b 技能与 MCP（已完成）**：
  - **技能**：`SkillLoader` 解析 YAML 子集 front-matter（name / description / tools，未知键
    向前兼容、CRLF 安全，解析失败**拒绝导入并明示原因**）；内置 3 个技能（周报生成 / 资料整理 /
    代码审查）首启幂等导入；`SkillRepository` 支持文件导入（同名「用户导入 > 内置」合并）、
    全局启用 + 助手绑定启用，装配 `SkillDocument` 注入系统提示词；技能页双开关 UI。
  - **MCP**：`McpConfigParser` 自动识别 `mcpServers` 映射 / 单对象 / 数组三种 JSON 形态并推断
    传输（type > url 含 `/sse` > command），提取 `${ENV_VAR}` 占位符；`McpClient` 走 JSON-RPC 2.0
    over **Streamable HTTP / 旧式 SSE**（initialize → tools/list → tools/call，响应兼容纯 JSON /
    SSE 帧 / 批，错误消息不含请求头）；`McpToolAdapter` 命名空间 `mcp__<serverId>__<toolName>`、
    分级 **T2**（默认关闭 + 逐调用审批）、`inputSchema` 原样透传；`McpRepository` 负责导入落库、
    可达性预览、连接并注册工具（幂等注销）、连接状态入库，stdio 明确提示「需沙盒（§9.3）」；
    MCP 页支持粘贴 JSON 导入 / 全局开关（开启即连接）/ 重连 / 删除。
  - **验证**：全仓 **239 项单测 / 0 失败**（新增 26：技能解析 11 + MCP 15）。- **「助手」P3 工作区与终端（UI 部分完成）**：
  - **工作区页**：列 `files/` 子树 + 面包屑回上级 + 文件预览（截断 8KB）+ 删除 + 配额用量；
    路径越界异常转可读提示（不崩、不静默）。
  - **终端页**：宿主模式（`/system/bin/sh -c`，工作目录 = 该助手工作区 `files/`），命令先过
    HARDLINE 无条件拦截、再由执行器二次拦截；200KB 输出上限 + 溢出落盘提示；等宽滚动回看、
    清屏、退出码显示。
  - **未完成**：proot 沙盒（需内置 Alpine rootfs，体积/来源待决策）；沙盒模式未接入时终端页
    明确标注「宿主（App 权限）」，**不伪装沙盒**。  - **proot 沙盒已落地**（用户 2026-09-09 拍板方案 A：随包内置）：`assets/assistant/sandbox/`
    内置 proot 5.1.107.92 + 依赖库（libtalloc / libandroid-shmem）+ Alpine 3.20.3 minirootfs
    （共 4.1MB）；首次使用释放到应用私有目录并 `chmod +x`，`terminal_run` / `run_code` 自动
    切到沙盒（可 `apk add python3` / `nodejs`）；终端页新增**宿主 / 沙盒模式切换**与释放进度提示。
    **GPL 合规**：proot 为 GPL-2.0-or-later，以未修改二进制再分发，`SOURCES.md` 记录上游仓库、
    发行包与书面索取三条源码获取途径，`LICENSE-proot-GPL-2.0.txt` 随包分发。- **「助手」设置页（完成）**：提示词与模型（模型列表来自 Web 端只读镜像，无配置时退化为手填
  `providerId::模型名`）/ 参数（temperature / top_p / max_tokens + 记忆模式三选）/ 请求体扩展
  JSON（保存前校验，非法拒绝）/ **预览最终请求**（密钥脱敏）。至此技能 / MCP / 工作区 / 终端 /
  设置五页全部接入真实实现，占位组件已删除。- **「助手」P4 增强（进行中）**：
  - **三协议齐备**：新增 **Anthropic Messages**（`system` 顶层 / `tool_use` 与 `tool_result` block /
    `thinking_delta` / `x-api-key` + `anthropic-version`）与 **Gemini `streamGenerateContent`**
    （`systemInstruction` / `functionCall` 与 `functionResponse` / `usageMetadata` / `x-goog-api-key`
    头，密钥不入 URL）；`RoutingTransport` 按供应商协议分派，未知协议回退 OpenAI 兼容。
  - **日历工具**：`calendar_read` / `calendar_write`（T2 档：默认关闭 + 逐调用审批）——
    `CalendarContract` 查询/插入/更新/删除 + 提醒；时间支持 ISO-8601 / 日期 / 空格分隔，
    **解析失败明确报错不猜测**；权限未授予时提示「请去系统设置授权」。
  - **工具审计**：`AuditSink` 端口 + `tool_audit` 落库 + 设置页审计面板（最近 50 条 + 清空）；
    **参数只记键名与长度**（不回显值，防隐私/密钥泄漏），结果预览截断 400 字。
  - **验证**：全仓 **278 项单测 / 0 失败**（新增 39：Anthropic 13 + Gemini 11 + 日历 10 + 审计 5）。- **「助手」P3/P4 收尾**：
  - **proot 沙盒**（用户拍板方案 A：随包内置，4.1MB）：proot 5.1.107.92 + 依赖库 +
    Alpine 3.20.3 minirootfs；首次使用释放、`terminal_run` / `run_code` 自动切沙盒、
    终端页宿主/沙盒切换；GPL 合规文件随包分发（`sandbox/SOURCES.md`）。
  - **stdio MCP**：`StdioTransport` 抽象 + 进程实现 + `McpStdioClient`（换行分隔 JSON-RPC）；
    `McpToolAdapter` 改为传输无关（http / stdio 两个工厂）；沙盒未就绪时明确提示。
  - **上下文压缩**：`LlmSummarizer` 用同一模型生成旧轮摘要（不带工具 / 温度 0 / 30s 超时 /
    失败退化截断）。
  - **验证**：全仓 **299 项单测 / 0 失败**。- **「助手」P4 补齐 + 发版自检**：
  - **`web_search`**：可插拔提供方（DuckDuckGo 无 Key 默认 / SearXNG 自填实例，均走 SSRF 防护）；
    设置页可选提供方；未配置时明确提示。需 API Key 的提供方（Tavily/Brave/Exa）待加密存储接入。
  - **`send_to_rp_chat`**（预留项，T2 默认关）：未接线时返回「未启用」，不假装可用。
  - **技能 URL 导入**：协议白名单 + SSRF 防护 + 256KB 上限，技能页新增链接导入弹窗。
  - **release 构建修复**：Compose mapping 生产者类路径版本钉到项目 Kotlin 版本
    （AGP 内置 Kotlin 与项目版本不一致会去解析未缓存版本，离线必失败）。
  - **APK 资产名修复**：AGP 会把 `rootfs.tar.gz` 解压成 `rootfs.tar`（去掉 `.gz`）——
    运行时改为两种名字都试 + magic bytes 判断，否则真机沙盒装不上。
  - **发版自检**：单 APK（`app-release.apk` 42.9MB）+ 签名指纹与 keystore 逐位一致
    （CN=LuzzyRP / SHA-256 `ed78235d…ffb1`）；全仓 **318 项单测 / 0 失败**。- **「助手」密钥加密存储与 Key 类搜索（完成）**：
  - `KeystoreSecretStore`：**AndroidKeyStore AES-256-GCM 主密钥**加密每条密钥，密文存
    应用私有目录（不引第三方依赖）；读取不缓存、值不进日志、写入临时文件防半写；
    满足 PLAN §13.2「密钥不进 DataStore/Room/日志」。
  - 搜索提供方补齐 **Tavily**（Key 在请求体）与 **Brave**（`X-Subscription-Token` 头），
    Key 只从加密存储取；设置页新增两个 Key 输入框（保存后不回显）。
  - **验证**：全仓 **323 项单测 / 0 失败**；真机（小米 25098PN5AC / Android 16）debug 包
    `install -r` 成功、冷启动「开卷」开屏正常、logcat 无崩溃。- **「助手」真机反馈修复（用户实测：助手页帧率明显低于其他页）**：
  - **阻塞性缺陷**：`AssistantRuntime` 取 DataStore 当前值用了 `flow.collect{}`——DataStore 是
    **无限流**，collect 永不返回，会**让首轮对话直接挂死**。已改为 `flow.first()`。
  - **帧率**：助手覆盖层显示时**暂停 WebView**（`INVISIBLE` + `onPause` + `pauseTimers`），
    不再与 Compose 争抢合成器；关闭时原样恢复（不销毁、状态不丢）。
  - **重组开销**：UI 模型与状态类加 `@Immutable`（Compose 可跳过未变项）；主题的
    Typography/Shapes 改为 `remember`（原先每次重组新建对象会让整棵子树失效）；
    ViewModel 工厂 `remember` 复用。
  - **流式节流**：文本/思考增量按 **100ms** 合并刷新（PLAN §11.2），工具状态跃迁与收尾
    强制刷新——此前每个 token 都重建消息列表并触发全列表重组。
- **「助手」首页版式与菜单栏归属（用户 2026-09-09 两次改稿）**：
  - **首页 = LuzzyRP 聊天页版式**：`h-28` 深色渐隐顶栏（汉堡 + 头像 + 名称 + chevron）→ 消息流
    （`px-2 pt-14 space-y-12`）→ 底部输入岛；**唯一差异点**是顶栏右上角由「清空聊天」改为
    **助手设置按钮**（用户指定）。
  - **菜单栏归 LuzzyRP**：助手页左上角汉堡 = **退出助手并打开 LuzzyRP 原侧栏**；助手不再有
    自己的抽屉。侧栏「助手」下新增子项组：**会话 / 记忆 / 技能 / MCP / 工作区 / 终端 / 设置**
    （DOM 注入，未改上游文件），点击按路由打开助手对应页面。
  - 会话列表退化为二级页（`ConversationsScreen`，含助手切换 + 新建），由 chevron 或侧栏「会话」进入。
  - 桥接新增 `openAssistantAt(route)` / `openRpSidebar()`（契约见 PLAN §14）。
- **「助手」管理页设计一致性重构（用户 P0 反馈）**：
  - **根因**：`DESIGN.md` 此前只规范了聊天页组件，管理页零规范 → 实现层临场发明组件
    （违反硬性规定 9 第 3 步）。
  - **补设计契约**：`DESIGN.md` 新增「管理页组件规范」——15 个组件逐项标注上游 Tailwind 类与
    像素值（`.settings-page-header` / `.settings-toggle` 44×24 / `.settings-collapse` 0.36s /
    `.settings-section-heading` / `px-3 py-1.5` 按钮 / 24dp 线性图标），换算基线 1 CSS px = 1 dp。
  - **新建组件库** `ui/component/ledger/`：`Ledger`（尺寸/字级 token）、`LedgerIcons`（19 枚图标，
    路径逐条取自上游 SVG）、`LedgerPageHeader` / `LedgerCard` / `LedgerCollapseCard` /
    `LedgerToggle`（自绘 44×24，替换 Material3 Switch）/ `LedgerButton` / `LedgerIconButton` /
    `LedgerTextField` / `LedgerSearchField` / `LedgerListRow` / `LedgerEmptyState` /
    `LedgerStatusPill` / `LedgerSegmented`。
  - **六个管理页 + 会话页 + 助手管理页全部改用该库**；删除临场组件（`PageHeader` / `SectionCard` /
    `Field` / `ToggleRow` / `EmptyState` / `SearchField` / `MemoryCard` / `ConversationRow`）。
  - **新增 token `card`**：上游卡片是 `bg-white`（暗色 #201E1B），此前误用 `surface-card`
    （实为上游边框色 #EFE9DE）当填充，导致卡片整体深一档。
  - **规格锁定测试**：`LedgerTokensTest` 8 项断言（开关 44×24 / 按钮 32 / 页面头 48 / 圆角 8·12·16 /
    图标 24 与 stroke 2 / 折叠 360ms），改规格必须先改 DESIGN.md。
  - **验证**：全仓 **331 项单测 / 0 失败**；`assembleDebug` 通过。**真机视觉比对待设备重新连接**。  - **图标渲染缺陷修复（用户真机指出「圆形图标都是半圆状」）**：上游 SVG 用紧凑弧线标志位
    （如 `a3 3 0 11-6 0`），Compose 的 `addPathNodes` 会把 `11` 读成一个数 → 圆心被画成半圆、
    齿轮变花形。改为**直接复用原项目图标**：把上游 SVG 的 `d` 落成 `res/drawable/ic_lz_*.xml`
    （Android VectorDrawable，系统 SVG 解析器），并**显式分隔弧线标志位**，以 `painterResource` 渲染。
    20 枚图标全部修正（真机截图确认齿轮/放大镜/信息圆/滑块均正常）。  - **侧栏「助手」改为可折叠组（用户 2026-09-09 指定）**：与「在线」「高级」同款结构
    （`.advanced-nav` + `advanced-nav-trigger` + chevron 旋转 + `grid-template-rows` 0fr↔1fr 0.32s）；
    子项为 对话 / 会话 / 记忆 / 技能 / MCP / 工作区 / 终端 / 设置（新增「对话」入口，因触发按钮现在只负责展开收起）。
  - **子项样式统一**：上游「在线」「高级」展开的子项（角色卡生成/小说生成/万相广场、预设/世界书/正则/工具）
    改用**助手子项的样式与尺寸**——13px / 7px·10px 内边距 / 10px 圆角 / 16px 图标 + hairline 竖线层级标记
    （亮/暗双模式），三组视觉完全一致。
- **供应商编辑器「模型列表」改卡片列表 + 二级弹窗编辑（patch 040，用户 2026-09-10 指定）**：
  用户原话「优化自定义供应商添加模型时的交互，改为弹窗实现，编辑完单个模型后保持，以卡片列表的形式展现」。
  改前：每个模型在供应商编辑器里**内联全展开**成一张长表单（7 个字段全暴露），多模型时模态内滚动很长、
  编辑面与列表混在一起。改后：
  - **卡片列表**：卡片只承载**识别信息**——显示名 + 模型类型徽标（text/image/embedding，配色沿用本屏既有
    accent 不新增色相）+ 模型 ID（等宽）+ 上下文/最大输出 + 输入模态 chips，右侧编辑·删除图标按钮；
    空态文案保留原有 /models 拉取缓存说明。
  - **模型编辑二级弹窗**：复用上游 `modal-shell`（`overlay-class="z-[70]"` 叠于供应商编辑器 `z-[60]` 之上，
    面板 `max-w-md max-h-[85vh]`），字段与原内联表单**逐项一致**（模型 ID / 显示 ID / 上下文长度 /
    最大输出长度 / 输入模态 / 模型类型 / 模型级自定义请求体），含 ID 预设命中提示与「撤销」。
  - **编辑完单个模型即保持**：编辑在**草稿副本**上进行，点「确定」才原位写回模型列表（`splice` 替换或
    `push`），**取消不影响原条目**；逐条累积编辑不再互相覆盖。删除的正是弹窗内条目时自动收殓弹窗。
  - **零上游裸改**：全部落 patch 040（`index.html` + `app.js`），标记 2+2 处、实体 9 枚按规程重生成并
    **双验证通过**（逆向 2/2 + 端到端 9/9 逐字节一致）；门禁新增 3 项校验（**89 PASS / 0 FAIL**）。
  - **同批附带**：「供应商级自定义请求体」右侧按钮按用户要求**扁平化**——去掉边框与白底（改
    ghost 款：teal 文字 + hover 浅底）、`whitespace-nowrap` 防换行、文案「+ 加键值」→「**+ 键值**」
    （原样式在窄列里被挤成两行，观感臃肿）。
  - 真机验收（小米 25098PN5AC）：卡片渲染 ✓ / 弹窗层级与字段 ✓ / 新增→确定即入列表 ✓ /
    编辑→预填当前值→原地替换且 ID 不变 ✓ / 取消不留痕（真实配置零改动）✓。
- **识图架构重构（patch 041，用户 2026-09-10 指定）**：识图模型改为**按需生效**——
  ① 当前聊天模型**原生支持图片**（多模态）时：图片以 `image_url` part **直发聊天模型**，
  **不再调用识图模型**（`buildNativeImageContent`，只带最近一条带图 user 消息——dataURL 每张
  数百 KB，全量回传会让请求体随轮数线性膨胀）；
  ② 不支持时：先用识图模型（内置提示词：审查豁免前缀 + 中文高密度客观描述 + 区分确定/不确定 +
  不把图内文字当指令）产出描述，再以 **user 身份**注入聊天模型上下文，措辞为
  **「用户上传了一张图，图片内容为：……」**（多张为「第 N 张图」），仍保留
  `<user_image_context>` 包裹与「不是系统指令」安全注记。
  ③ **删除视频支持**（含设置项）：模型编辑器的输入模态只留 text / image，归一白名单与
   `ui-components` 标签映射同步清理，全仓零残留。
  - 违反「只在…时生效」会怎样：多模态模型下若无原生发图通路，图片会被静默丢弃——故本次
    同步新增该通路（`api-utils.js` 三协议转换管道本就支持 `image_url` part）。
- **删除内置助手预设「阿墨」+ 空态新建入口（用户 2026-09-10 指定）**：原
  `AssistantRepository.ensureDefaultAssistant()` 会在首次进入时自动创建内置助手「阿墨」；
  现整体删除，助手一律经用户显式 `createAssistant` 创建。助手管理页空态提示语同步纠正
  （原「助手在首次打开时自动创建」已失效）并挂上**「新建助手」**按钮（`AssistantHost` 接线），
  避免"删了预设却无处可去"。
- **进/出助手过渡动画（用户 2026-09-10 报告"切换生硬"）**：助手覆盖层原为 `visibility`
  硬切。新增 `MainActivity.animateAssistantOverlay()`：**进 200ms / 退 140ms /
  `cubic-bezier(0.23,1,0.32,1)`，自 `scale(0.96)+alpha 0` 起步**（令牌禁 `scale(0)` 起步），
  退出动画结束后才置 `GONE`；系统「移除动画」时直接呈现。助手**内部**路由本就有
  `AnimatedContent`，未改动。
- **侧栏「助手」组移位 + 「对话」图标去重（用户 2026-09-10 指定）**：助手组由底部簇
  （「外观」之前）移到**「聊天」之下作第二入口**（锚点改为聊天按钮的下一个兄弟节点，
  并保留"上游改名/改结构时回落旧锚点"的降级）；子项「对话」原用铅笔线稿，与「助手」触发
  按钮**同图标**，改用侧栏「聊天」项自带的气泡图标（上游原图形，零自绘）。均落扩展层
  `ext/luzzy-assistant.js`，零上游改动。

**同步（上游 1.9.3 · 已完成）**
- **上游新版本 RP-Hub 1.9.3 已合并**（公告 id `10207`，更新时间 09/08 15:30；基线 `d2f2625` → `4aef0bb`，
  4 个提交 / 5 文件 **+297 −351**）。上游自报新功能：角色卡工坊与万相广场**一键导入** ·
  工坊**抗截断模式** · **Diff 匹配与智能修改成功率大幅优化** · **角色卡管理页全面焕新** ·
  开屏动画优化 · 剧情 UI 面板出现时机优化 · 修复沉浸模式宽度异常。
- **逐文件改动**：`index.html` +1（`add-character-modal` 新增 `@generate` 跳生成器）；
  `ui-components.js` +20/−9（`AddCharacterModal` 新增「生成角色卡」入口）；`app.js` +84/−18
  （**万相广场一键导入**消息桥 `RPH_FORUM_*` + `selectCharacter`/`importCharacterData` 签名改选项对象）；
  `character/index.html` +170/−285（**工坊页 Diff 机制重构**：文本块解析 → 原生 `edit_character_card`
  工具调用）；`built-in-content.js` +40/−39（预设文案调整 + 公告换 1.9.3）。
- **合并方式（三方合并，非覆盖重放）**：以 1.9.3 纯净文件为底、1.9.2 纯净文件为公共祖先、
  二创工作树为另一方做 `git merge-file`——`index.html` / `ui-components.js` / `character/index.html`
  **零冲突**；`app.js` **1 处冲突**（上游新增 `squareImportPending` + `getSquareFrame()` 与二创
  注释行重叠）取上游侧解决。**交叉验证**：可重放的 7 枚实体重放结果与三方合并结果 **LF 归一逐字节一致**
  （character/index.html、ui-components.js 实测相等），证明合并无信息丢失。
- **顺带修复的存量缺陷**：1.9.2 合并（会话 25）时误删 `let workshopImportPending = false;`
  （退化为隐式全局），本次合并随冲突解决恢复该声明。
- **签名变更核对（U5，运行期才炸的隐患）**：上游 `importCharacterData(raw, avatar, {askImageGeneration, activate})`
  与 `selectCharacter(index, isNewImport, {silent})` 改选项对象——全局核对 6 处调用点，
  无一处传旧式布尔第三参，**无需改动**。
- **实体 9 枚按修正规程全部重生成**（计划 §18.6）：以「上游 1.9.3 纯净基线 + 合并结果」为对、
  LF 归一生成 → **逆向 9/9**（纯净基线逐枚 `git apply` → 与工作树 LF 归一逐字节一致）+
  **端到端 9/9**（纯净基线全量 → `apply-patches.ps1` 实跑 → 9 枚全 `[OK]` 且结果与工作树一致）。
- **工具缺陷修复**：`apply-patches.ps1` 的基线 commit **参数化**（`-BaselineCommit` > 指纹表头
  `(commit <sha>)` > `FETCH_HEAD`，替代硬编码 `d2f2625`），并改用 cmd 重定向取原始字节以避免
  尾部空行导致的哈希偏差；`sync-upstream.ps1` 指纹表头改为携带 `(commit <sha>)` 与上游版本号，
  文件清单补 `character/`、`novel/`。
- **硬编码基线点更新**：`LuzzyBridge.UPSTREAM_VERSION` 1.9.0 → **1.9.3**；README 二创声明基线
  与 Upstream 徽章 → 1.9.3；`tools/upstream-fingerprints.txt` 全表 13 项以 `4aef0bb` 重算。
- **行尾一致性修正**：`styles.css` / `novel/index.html` 工作树由混合行尾归一为 CRLF，与
  `core.autocrlf=true` 检出态一致（git 视角零差异），使 R1/R2 指纹在 clone / checkout 后仍然成立。
- **验证结果**：`node --check` 全 JS PASS（rphub 9 + ext 4）；`verify-markers.ps1`
  **82 PASS / 0 FAIL**；未 patch 的 4 文件（`built-in-content.js` / `styles.css` / `presence.js` /
  `update-check.js`）与上游 1.9.3 **LF 归一同构**，`nsfw_rules` 块（1588 字节）**逐字节一致**；
  桌面冒烟（headless Chrome + CDP）**零 JS 异常**，工坊页正常挂载并含 `edit_character_card` 工具；
  `assembleDebug` 构建通过。
- **回归专项（计划 §18.8 十项）**：①工坊页 JS 执行 ✓ ②Diff 工具调用（静态核验通过，需带 tool 的
  模型实测）③抗截断（同上）④广场一键导入（iframe 与 `RPH_FORUM_*` 桥就位，需联网实测）
  ⑤「生成角色卡」入口 ✓（实测点击跳转生成器视图）⑥角色卡管理页焕新 ✓ ⑦开屏 ✓ / 面板时机与
  沉浸宽度需真机目测 ⑧既有二创回归 ✓（冒烟全过）⑨数据兼容 ✓（1.9.3 未触碰 localStorage /
  IndexedDB 结构，零 `setItem`/`removeItem` 改动）⑩断网 ✓（无新增 CDN 引用）。
- **执行计划**：`docs/PLAN-v1.5.0-assistant.md` §18（U1-U12 清单 + 回归专项十项 + 排期约束）。
  按用户指示：**先同步、再做助手，最后一次性发版**（不拆版）。

**优化**
- **二创定位澄清（README）**：二创声明由「仅优化前端、后端完全未动」改为明确三段立场——
  **遵循上游开源协议**（CC BY-NC 4.0 + 上游 LICENSE 保留）、**保持同步上游更新**（覆盖 +
  登记 patch 重放）、**但本项目有自己的功能路线，会修改前端或后端 / 原生侧代码**；同步策略、
  架构分层表、许可证义务表同步补齐（「原样保留」仅指 `nsfw_rules` 一条）。
- **发布纪律固化（AGENTS §3.4/§6.3/§7/§9 + README）**：①**只构建/发布一个 APK**
  （`app-release.apk`，ABI 拆分保持关闭、禁止恢复）；②**每次发布必须保持同一应用签名**
  （沿用 `keystore/luzzy-release.keystore`，发布前 `apksigner verify --print-certs` 核对
  指纹与上一版一致，`keystore.properties` 缺失会回退 debug 签名 → 不得发布）。
- **文档地图校正（AGENTS §1.5）**：补 `docs/PLAN-v1.4.0.md`、调研文档、`docs/RELEASE-KEY.md`
  条目；README「开发者须知」的最近 PLAN 指向同步为 v1.4.0。
- **`tools/gen-changelog.mjs` 徽章状态分支（工具层）**：CHANGELOG 顶部章节状态为「开发中」时
  生成琥珀色 `开发中·未发布` 徽章，避免开发中版本被无条件标成绿色「正式版·可游玩」。
- **真机体验包改用 release 构建（用户指示，2026-09-10）**：用户日常真机由 debug 包改为
  **release 签名包**（`com.luzzymeow.luzzyrp`，与最终分发件同物），此后每个版本由用户
  先体验该 APK 作**最后一道人工真机测试**；原 debug 包已从设备卸载（其数据随之清空）。
  配套：`WebViewSetup` **显式开启 WebView 内容调试**（`setWebContentsDebuggingEnabled(true)`，
  release 同样生效）——release 不可调试会使 CDP 排查通道整体失效（帧率/布局/脚本耗时无从测量）；
  代价为可连 adb 的电脑可检查页面内容，本应用仅侧载分发，接受该代价。另：`adb` 传
  `MSYS_NO_PATHCONV=1` 前缀是 Git Bash 用法，**PowerShell 下不需要也不生效**（会话 48 踩坑）。
- **侧栏折叠组动效对齐设计令牌（120Hz 掉帧窗口减半）**：用户报告「侧边菜单栏多级抽屉菜单项
  打开时动画不流畅、帧数没达到手机刷新率」。真机量化（小米 25098PN5AC / 120Hz / CDP 帧事件
  追踪 + `dumpsys gfxinfo`）：**主线程不是瓶颈**（每帧 layout 0.27ms + recalc 0.75ms +
  paint 0.6ms，rAF 稳定 8.3ms，`BeginFrame` 间隔 P50 8.32ms）；瓶颈是 **GPU 栅格约 5ms/帧
  > 120Hz 的 8.33ms 预算**（`gfxinfo` GPU 中位 5ms、95 分位 10~14ms），超额帧落到下一 vsync
  → 观感上的顿挫。修复：`ext/luzzy-theme.css` 把 `.advanced-nav-panel` 从上游
  `0.32s cubic-bezier(.22,1,.36,1)` 收敛到本项目令牌 **进入 200ms / 退出 140ms /
  `cubic-bezier(0.23,1,0.32,1)`**（chevron 同拍）——动画窗口 38 帧 → 24 帧，单次展开掉帧
  绝对数由 **~1.4 降至 ~0.8**（同场交替 A/B 两轮：19/20 → 8/8）。**掉帧率仍约 2~4%，本改动
  不消除它**（受 GPU 栅格地板限制）；收益是「暴露在预算外的帧数」与总顿挫时长等比下降，
  且时长本就是本项目令牌规定的值。
  **负面结论（已实测排除，勿重复尝试）**：①`.advanced-nav-panel-inner` / `.advanced-nav-list`
  加 `will-change: transform` 促独立合成层——成对交替测量无收益（28 vs 28），且与 patch 034
  的常驻层教训相悖；②`.advanced-nav-panel{contain:paint}` 首测似有 −64%（14→5），**成对交替
  复测反向**（无 17 / 有 26）——判定为噪声，不采纳；③纯淡入（去掉高度动画）虽可再降，但会
  破坏「下拉展开」视觉语义，未采纳。**零上游改动、无新 patch**。
- **开屏冷启动跑满 120Hz（遮挡期渲染抑制）**：用户报告「开屏动画最开始总是卡一下」。
  真机**冷启动**实测（`dumpsys gfxinfo` 5s 窗口）：**UI 帧时中位 17ms（≈60fps，没跑满 120Hz）、
  GPU 90 分位 15ms、legacy 掉帧 80.69%**；CDP 层树追踪同时证明**开屏入场动画本身已在合成层上**
  （accelerated transform/opacity），故掉帧不在动画机制，而在**被开屏完全遮挡的应用主体仍在
  渲染**（#app 整棵子树绘制 + 逐帧合成 1220×2656 ≈3.2M px @DPR 3.25，全属白烧）+ 启动期 GPU
  瞬时负载（首次栅格 / 着色器编译 / 系统启动画面退场）。上游启动期主线程成本（Tailwind JIT +
  Vue 执行 109ms + 微任务检查点 83ms）为既有事实，扩展层无法消除。
  修复（`ext/luzzy-theme.css` + `ext/luzzy-splash.js`，**零上游改动、无新 patch、视觉零差异**）：
  `body:has(> .luzzy-splash:not(.lsp-dive):not(.lsp-warm)) #app { visibility: hidden }` ——
  遮挡期不绘制应用主体，且**保留布局**（不选 `content-visibility`，避免应用自身的滚动定位 /
  输入岛自适应读到 0 而错乱）；**两段解除**：① 入场 1.2s 加 `.lsp-warm`，让应用在开屏仍不透明
  时完成首次绘制（该次绘制实测有 150~350ms 主线程尖峰，留到点击会与转场淡出重叠、露出空白）；
  ② 点击「沉溺」加 `.lsp-dive` 立即解除（**CSS 原生兜底**，JS 计时器失效也不会卡住应用）。
  `:has()` 不受支持时整条不生效，行为与改前完全一致（零风险降级）。
  **结果（三次冷启动取样）**：UI 帧时中位 17ms → **7ms**、GPU 90 分位 15ms → **6~7ms**、
  legacy 掉帧 80.69% → **4.13% / 4.76% / 3.84%**；转场窗口 95 分位 13ms。
  **另记一条被否决的方案**：单段解除（只在点击时解除）冷启动同样能到 7ms，但把应用首绘推迟到
  点击瞬间，转场窗口 95/99 分位劣化到 **150ms / 350ms**——即把卡顿从入场挪到转场，已弃用。


**注意事项**
- 本轮**未改任何上游文件**（`assets/rphub/` 零改动），无新 patch、无指纹表变更；
  verify-markers 仍应全绿。**上游同步已列入本版计划（§「同步」段），执行时按 AGENTS §4 全流程走**。
- 应用内 CHANGELOG 与 README 徽章已随 `node tools/gen-changelog.mjs` 同步至 v1.5.0；
  **`tools/gen-changelog.mjs` 新增徽章状态分支**：CHANGELOG 顶部章节状态为「开发中」时，
  README Status 徽章生成琥珀色 `开发中·未发布`（而非无条件写成绿色「正式版·可游玩」），
  避免开发中版本被误标为已发布——发布时按 §3.4 流程更新状态行即自动转绿。

### v1.4.0 — 同步上游 1.9.2 × 剧情面板 × 沉浸模式 × 抗截断协议融合（上游基线 RP-Hub 1.9.2）

> **状态：正式版已发布（2026-09-08）。** 构建：versionCode 12 · **release 单 APK**
> （`app-release.apk`，签名 CN=LuzzyRP；本版起关闭 ABI 拆分，见「注意事项」）·
> 用户真机人工验证通过。上游 1.9.2（commit `d2f2625`，14 提交 / 9 文件 +2242 −2268）。

**新增**
- **关于页更新日志关键词高亮（patch 039）**：关键词检索命中的内容里，关键词以 `<mark>` 高亮
  （文本节点级遍历、跳过 script/style、大小写不敏感、正则元字符转义；底色取 DESIGN.md
  highlight token `--luzzy-mark` = `#F5D9A8`，与开屏荧光笔记号同源，暗色下配反转主文字）。
- **同步上游 1.9.2（会话 24-25）**：UI 实时生成（剧情面板 `story_panels` 协议）／沉浸模式
  （`settings.immersiveMode`）／角色卡牌组（CharacterDeck）／主动工具调用改原生 toolCalls
  协议／快捷面板密度重构／开屏改版（书本动画，**我方 D3-A 保留「开卷」不采纳**）；
  安全面全过：`nsfw` 对象与 1.9.1 逐字节一致（storyPanels 为其后独立预设）、vendor/ 零变化、
  novel/runtime-services 零变化、无新增/删除文件。
- **抗截断协议融合（patch 015 × 上游 output_reply，D2-A 拍板）**：采纳上游 `replyInTool`
  原生 toolCalls 流式解析（`readReplyDelta` 增量解码）——Gemini 抗截断走上游原生路径，
  我方三协议适配器只做协议分派 + `maxTokens`/`extraBody` 注入，不再自建续写链。
- **上游新功能适配**：记忆分片助手与工具系统双轨混合（记忆系统取我方 v1.3.0 实现 +
  工具系统取上游 1.9.2 原生协议）、多商路由 × 上游原生 toolCalls 融合（主聊天/识图/
  UI 模板/总结四请求点）、`getImageTagRegex` 签名去参调用点适配（015）。

**优化**
- **用量页时间筛选去冲突（patch 037）**：右上角「更多」下拉（全部/24小时/7天/30天）与
  折线图「日/周/月」粒度双重筛选语义冲突——按用户指示整链下线（props/emits/模板/状态/
  watch/绑定/click-outside 全部移除），只保留折线图粒度与类型筛选。
- **版本更新公告品牌化（patch 038）**：公告弹窗标题由上游「网站公告」改为品牌名 **LuzzyRP**，
  内容区底部新增同步来源注释「同步更新上游节点：本公告内容随上游 RP-Hub 版本同步，
  由 LuzzyRP 呈现。」
- **index.html 开屏区恢复（会话 25）**：会话 24 三方合并时 C1/C3 冲突块误取 ours 侧，
  开屏区成为「上游 entry-transition 书本动画 + 我方 luzzy-splash 残骸」混合体且
  `luzzy-splash-page/stack/center/glow/seal/wordmark/slogan` 七节点与 027 标记丢失——
  按 v1.3.0 完整块整块恢复（`entry-transition` 彻底退役）。
- **标记与字体合规修复（会话 25）**：补回 001/004/006 标记注释、删除残留的
  `fonts.googleapis.com` preconnect 两行（硬性规定 4/10 门禁 003/004/006/027 五项转 PASS）。
- **实体重放通道加固（会话 25，工具层）**：实体段改为**先于字符串块执行**（实体前像 =
  上游纯净基线）、前像判定改用实体头 `index pre` 的 LF 归一 blob id、`git apply` 的
  trailing-whitespace 告警不再中断脚本（stderr 隔离）、应用后强制校验标记落盘；
  仓库外逆向 9/9 PASS + 纯净基线端到端重放 9/9 PASS。
- **指纹表更新至 1.9.2 基线**：全表 14 项以 `d2f2625` 重算（R1/R2 转 PASS）。

**修复**
- **角色卡工坊页 JS 不执行（patch 007 存量缺陷，v1.3.0 修复的回归保持）**：1.9.2 工坊
  大改版后重放 007 时保持「完整 uiHTML 行 + `</`+`script>` 拼接」形态。
- **上游修复采纳**：正则渲染嵌套重复渲染／UI 生成状态下正文异常阻断／新手引导界面
  高度自适应异常。

**注意事项**
- **发布打包变更（本版起）**：release **只附单个 APK**（`app-release.apk`）——LuzzyRP 是纯
  WebView 壳、不含 native 库，此前 ABI 拆分产出的三件套（arm64-v8a / x86_64 / universal）
  **字节完全相同**（v1.2.2~v1.4.0 release 资产 SHA256 实测一致），拆分为零收益；已关闭
  `app/build.gradle.kts` 内 ABI 拆分（恢复方式：取消注释原 `splits.abi` 块）。
- **测试包 → 正式包数据不互通（需重填一次，谨致歉意）**：测试包 `com.luzzymeow.luzzyrp.debug`
  与正式包 `com.luzzymeow.luzzyrp` 是**两个独立应用 ID**，安卓层面各自独立存储，正式包读不到
  测试包内的数据——首次切到正式包需重新填写**用户信息**（昵称/人设等）与**供应商 API 配置**
  （Key/URL/模型）。这不是本版更新丢失数据，而是跨包名切换的系统行为；同包名覆盖安装（升级）
  数据照旧保留。**保留原数据的做法**：继续使用测试包；或装正式包后用应用内导入导出搬移
  角色卡/世界书/预设（聊天记录与记忆不随包迁移）。后续版本沿用同一包名，再次升级不会再遇到。
- **决策记录（用户拍板）**：D1-A 全屏功能继续下线（上游 1.9.2 恢复全屏，重放 patch 022）；
  D2-A 抗截断采纳上游 `output_reply` 协议；D3-A 保留「开卷」开屏；D4-A 新功能默认值原样
  （`immersiveMode=false`、剧情面板预设默认关闭、CharacterDeck 默认关闭）。
- **上游行为变化（用户可见）**：开屏动画仍为我方「开卷」；抗截断仅对 Gemini 模型可见
  （上游实现即如此）；剧情面板需在预设中手动启用。
- 剧情面板/沉浸模式/CharacterDeck 暂以 classic 样式交付，luzzy 主题化定制待用户真机
  体验后按硬性规定 9 走设计流程（本版默认不做）。

### v1.3.0 — 同步上游 1.9.1 × 性能治理 × 供应商精简 × 记忆召回节点 × 关于页文案固化（上游基线 RP-Hub 1.9.1）

> **状态：正式版已发布（2026-09-06）。** 构建：versionCode 11 · release 三件套
> 用户真机人工审阅通过（debug 包覆盖日常数据验证）。
**新增**
- **「记忆召回」思考节点（patch 031）**：思考卡片时间线新增首位节点——消息创建时从请求
  上下文提取向量召回块摘要（片段数 + 相似度区间），以 thinking 型节点渲染（详情含
  上下文查看器指引）；识别复用 patch 016 结构化标记生态，失败静默降级无节点；
  纯文本回复（无卡片）不强制出卡（D2 拍板）。
- **内置供应商精简（patch 029，D3 拍板）**：内置列表仅保留 **DeepSeek** 且开放编辑
  （`editable` 标志 + `settings.apiProviderOverrides` 持久化 + 注册表 override 合并 +
  编辑器内置分支与 id 锁定 + 设置页 URL 直编写 override）；默认商 sta1n → deepseek；
  **老用户无损迁移**——STA1N API / OpenRouter / SiliconFlow 自动转为等价自定义供应商
  （URL / Key / 模型槽位引用保留，用量与工坊联动不受影响）；patch 023 STA1N 图标修复
  随条目退位（校验门 023 项退役 → 029 项接管）。
- **流式渲染降载（patch 032）**：流式渲染间隔 60→120ms（三协议共用）；流式期渲染
  LRU 旁路（中间串不再灌满 2000 缓存上限）——消除 O(n²) 全文重算与缓存驱逐抖动。
- **同步上游 1.9.0 → 1.9.1（patch 007/015/025/029/032/035 适配，2026-09-05 会话 21）**：
  上游 10 文件 +904/-1373 大版本——**OpenAI 传输层迁往 api-utils.js 重写**（120s 空闲
  超时/多行 SSE 容错/requestJson 通道/传输层记账；我方三协议适配器随迁+入口分派+
  max_tokens/extraBody 注入+032 渲染间隔重锚）、UI 模板行协议改纯 JSON + 编辑器协议
  检查横幅、**抗 Gemini 截断自动续写**（仅 Gemini 模型可见）、破限标记改回
  `rp_hub_default`；**上游删除项**：临时指令功能、记忆页导入导出按钮（管理器仍可
  导入导出）、自动获取模型开关（改为启动仅拉激活商+选择器惰性补拉）；我方 016/026/031
  记忆链路依赖上游逐字保留零适配；实体全量以 1.9.1 基线再生成（9 枚逆向全过）。
- **存量缺陷顺带修复（patch 007 残缺 hunk）**：character 工坊页 `uiHTML` 行自 v1.0.0
  起被残缺替换截断主脚本——**角色卡工坊页 JS 自 v1.0.0 起整体不执行**（回归盲区：
  仅测过主应用导入导出）——已按正确形态重生成并专项回归。
- **供应商自定义图标（patch 035）**：编辑器新增「供应商图标」——从相册选图后进入
  1:1 裁剪覆盖层（方框拖动选取 + 右下角圆点缩放，确认裁为 128×128），保存后以圆角
  显示于选择器触发器与供应商管理卡（与 DeepSeek 图标同规格）；支持清除恢复默认。

**修复**
- **内置供应商编辑冲突误报（patch 035）**：编辑 DeepSeek 时误报「该 id 已被其他供应商
  占用」——根因：override 合并会重建内置商注册表对象，冲突检查按对象身份排除自身失效；
  内置商 id 本就锁定，编辑态直接跳过冲突检查。
- **管理卡名称截断（patch 035）**：自定义供应商名称被同行 4 个操作按钮挤压成单字符
  （如「STA1N」只显示「S」）——管理卡改两行式布局（图标 + 名称/徽标行 + 按钮行）。
- **记忆内容管理器实时联动（patch 036）**：补录成功后管理器列表不实时更新（017 打开时
  一次性快照无联动）——面板展开且作用域为当前会话时，watch 记忆/总结列表变化即时同步
  （分页不重置，管理器自身编辑路径不受影响）。
- **关于页上游版本号固化（patch 030）**：「基于 RP-Hub ~~1.9.0~~ 二次开发」固定文案
  （品牌卡去插值 + upstreamVersionLabel 整链移除 + 注入页脚同改）——上游同步不再有
  基线串遗忘点；LuzzyBridge.UPSTREAM_VERSION 保留仅 logcat 诊断。

**优化**
- **性能治理 · 合成层瘦身（patch 034，D1 拍板）**：高频面退实底——聊天气泡 / typing /
  输入岛 / 侧栏 backdrop-filter 归零（暖纸 alpha 0.97），`:has` 流式加厚同步失效；
  `glass-stabilize` 与 scroll-reveal 三族 will-change 归 auto（渲染窗口常驻 40-60 个
  合成层 → 按需瞬时创建）；思考卡 / 模态等低频面保留磨砂（DESIGN.md 已同步）。
- **开屏转场去 blur（patch 034）**：lspDiveZoom 移除 filter:blur（DPR 3.25 下全屏层
  逐帧重栅格 ≈3510×7800 像素/帧为掉帧主源），失焦感由 scale+rotate+opacity 表达；
  泡泡层随之脱离 filter 父层。
- **发送键热区根除（patch 033）**：输入区 transition-all → bottom 定向过渡、输入岛去
  过渡、发送/中止按钮定向属性（FAB v4 配方——分数 DPR 3.25 合成层绘制错位家族根治）。
- **供应商模型来源澄清（patch 035）**：管理卡新增模型数徽标；编辑器模型列表空态说明
  区分「手动配置」与「/models 在线拉取缓存」（配置 Key 后自动拉取，仅本次运行有效，
  重启后需重新拉取）——消除「到底配没配模型」的困惑。

**注意事项**
- 本版实施前按硬性规定 9 复读 4 项设计 SKILL；豁免判定：性能修复=机械操作豁免、
  开屏=已选定方向 B 的迭代、节点/编辑按钮=组件级新增（循 DESIGN.md token）。
- 玻璃档位为设计真源变更（D1）：雾纸玻璃高频面退场、立绘透色效果随实底退场；
  不满意可按包回滚（commit 粒度独立）。
- apply-patches 011 重放块退役（SKIP 检测失配实证：028 单轨化移除目标 DOM 后，
  字符串锚点重放必崩——会话 21 首次暴露，按 003 先例退役）；实体再生成 5 枚
  （007-029-novel / 009-029-core-utils / 012-031-app / 012-032-runtime / 012-033-index），
  pre 哈希与基线逐一对齐，逆向 --check 8/8 PASS。
- 「跑满刷新率」受 WebView 合成路径与分数 DPR 3.25 设备先验限制，目标为消除全部
  可消除卡顿源；实测帧率以真机录屏为准。

### v1.2.3 — 同步上游 1.9.0 × 用量趋势图 × 开卷门扉 × 主题单轨化 × 置顶按钮修复（上游基线 RP-Hub 1.9.0）

> **状态：正式版已发布（2026-09-05）。** 构建：versionCode 10 · release 三件套
> （arm64-v8a / x86_64 / universal）；真机（小米 Android 16）全量回归通过（debug 包覆盖日常数据验证）。

**新增**
- **上游同步 1.8.9 → 1.9.0**（commit b409ca6 → 94a0cd9，2026-09-04）：上游仅更新
  `built-in-content.js` 一个文件（+8/-13）——破限预设标记 `<roleplay_hub_default>` →
  `<rphub_default>`（上游公告「修复标记问题」）、UI 模板分析提示词微调、更新公告刷新。
- **用量趋势折线图（patch 025）**：用量统计页新增「用量趋势」卡——日（近 24h·小时桶）/
  周（近 7d·天桶）/ 月（近 28d·周桶）三种粒度；不同模型以品牌分类色板多线同图；供应商
  chips 一键聚焦某模型商，模型多选 chips 支持查看某商的全部/部分模型；单系列 Top 8 之外
  自动合并「其他」；空窗零值补齐 + 空态提示；纯 SVG 零依赖，亮暗主题自适应。
- **关于页 CHANGELOG 工具化（patch 024）**：版本分类下拉（按 vX.Y.Z 章节过滤）+ 关键词
  搜索框（标题与正文，150ms 防抖，命中计数与空态）+ 右下角置顶按钮（滚动 >240px 出现，
  平滑回顶，reduced-motion 降级）。
- **自创开屏「开卷 Open the Journal」（patch 027）**：启动动画全面替换为 LuzzyRP 品牌
  开屏（用户三方向选定 B，参照 Aēsop 获奖互动站）——掀封→纸落→界格→钤印→落墨→
  荧光划线→页码，≈2.3s 定格后淡出进主界面；纯 CSS 动画仅 transform/opacity，
  亮/暗随主题首帧自适应（暖纸 cream / 暗纸同构镜像），reduced-motion 直出终帧快速退场；
  上游 entry-transition 开屏退役（patch 003 字标并入新开屏，重放块退役）。
- **开屏 v3「门扉」交互（patch 027 修订）**：掀封叙事移除，改为构图淡入 → 加载进度条
  自左向右 → **「沉溺」按钮浮现并等待点击**；点击转场 = 轻微眩晕 + 水下泡泡上浮 +
  中心放大坠入主界面（行为脚本 ext/luzzy-splash.js）；reduced-motion 直出可点态、
  转场退化 220ms 淡出。
- **关于页 CHANGELOG 自动同步（双保险，用户指令）**：Gradle genChangelog 任务
  （preBuild 挂钩，构建即从仓库根 CHANGELOG.md 重生成应用内数据）+
  gen-changelog.mjs --check 模式 + verify-markers R3-changelog-sync 门禁拦截过期——
  「忘记更新」在机制上不可能。
- **资产签名自动解压（patch 028）**：构建期对 rphub/ext 资产树计算签名注入
  BuildConfig.ASSET_SIGNATURE，AssetExtractor 启动比对，资产变更即自动重解压——
  根治「改 assets 忘 bump EXTRACT_VERSION」坑（本会话三踩）。
- **关于页置顶按钮错位修复（patch 024/028 v4）**：luzzy-ext.js 品牌注入泛匹配
  选择器 [class*="about"] 命中 FAB 按钮类名致品牌卡注入按钮内部（箭头挤偏 + 灰卡
  溢出错位）；锚点改显式 .about-view + 已注入实例迁移；FAB 定位改 fixed + 零动效
  （本机 WebView 分数 DPR 合成层失效规避）；品牌基线串 1.8.9→1.9.0（LuzzyBridge/
  app.js 回退标签）。真机 CDP 实证箭头居中 [0,0]、钉扎右下角标准位。
- **主题单轨化（patch 028）**：**经典（原版）主题与主题切换功能移除**，恒定
  「暖幕手记」（亮/暗模式保留，外观页预览卡简化为模式切换）；老用户 classic 设置
  无条件迁移至 luzzy；界面字体 / 对话字号等外观设置不变。DESIGN.md 契约同步。
- **关于页 CHANGELOG 正文渲染修复（patch 024 修订）**：024 首版编辑事故误删正文渲染
  元素（v-if/v-else 双双丢失 → 「全部更新内容不显示」）——已恢复，桌面端到端审计验证
  正文 30676 字符 / 13 章节渲染、下拉默认「全部版本」。
- **版本分类解析修正（patch 024 修订）**：`v1.0.0-rc2/-rc3` 不再被截断成重复的
  `v1.0.0`（rc 后缀独立成项），下拉按首次出现去重；应用内 CHANGELOG 数据已随本版重新生成。

**修复**
- **向量记忆检索死区（patch 026）**：①手动检索不再继承自动召回的「近期保留楼层」排除窗
  （原行为：新会话分片后立即检索必空，且文案误报「还没有分片」）；②分片所属供应商已删除/
  改名时显式报「嵌入供应商已不存在」（原静默回退默认商、整桶 404 无从自查）；③裸引用
  回退协议跟随激活商（原硬编码 openai，Gemini 嵌入分片回退必失败）。排查结论：入库→
  量化→持久化→管理器链路完好，属前端过滤死区，非存储链路崩溃。
- **STA1N 供应商图标缺失（patch 023）**：上游自建图床 404 所致（1.9.0 基线同款），
  改用官方 CDN favicon；novel 子页面同源问题一并修复。
- **用量记录供应商维度缺失（patch 025 附带）**：`recordApiUsage` 补存 provider/protocol
  （patch 012 调用侧已传参但构造器漏存——用量列表 `[商名]` 前缀此前从未显示）；
  历史记录由图表层按 apiUrl 反查兜底。

**优化**
- **设置页清理（patch 021）**：移除高级设置内残留的「外观」入口卡（外观唯一入口 =
  侧栏「外观」页）；空间管理改自动统计（进入设置页自动测量，每会话首次），移除手动
  统计按钮；「网页存储空间」文案改「存储空间占用」。
- **聊天页全屏按钮移除（patch 022）**：右上角全屏按钮及全部全屏逻辑（原生 fullscreen
  helpers/监听/状态）整体下线。
- **开屏节拍 v2 与层级修正（patch 027 修订）**：掀封翻角加大、opacity 后段保持（翻页
  动作可读化，修复「封面原地消失」）；内容节拍与掀封交叉（消除约 400ms 空场）；封面
  离场改「上滑离屏为主 + 3D 翻页为辅」双保险（透视写法自包含，规避 fixed 层透视链路
  兼容风险）；开屏层级提至公告弹层之上（品牌时刻不被打断）；启动窗口底色暖化
  （冷启动白闪 → 暖纸衔接）。

**注意事项**
- `nsfw_rules` 区块上游未触碰（硬性规定 1 复核通过）；vendor/ 离线依赖与本地字体无变化。
- 资产签名自动解压（构建期 assetSignature 注入 BuildConfig 比对）：资产变更即自动
  重新解压，IndexedDB 用户数据不受影响；关于页 CHANGELOG 自动同步（构建期重生成 + 校验门拦截）。
- 同步门与标记门：apply-patches 重放全部幂等 SKIP + verify-markers **57 PASS / 0 FAIL**
  （新增 021-027 共 14 项校验）；7 个实体 diff 再生成（范围式命名 007-023 / 009-023 /
  012-027）并全部通过逆向 `--check` 校验；指纹基线 1.9.0（94a0cd9）；patch 003 重放块退役
  （标记保留，意图由 012-027 实体承载）。
- 向量相似度阈值仍为硬编码 0.45（app.js），可调阈值滑杆列候选迭代。
- 本版实施前按硬性规定 9 复读 4 项设计 SKILL；开屏动画按三方向硬门产出 A/B/C 设计板
  （docs/design/splash-v1/），用户选定方向 B（原话落档 direction-approved.md）。
- 开屏为品牌级画面：classic 主题用户同样看到「开卷」开屏（亮/暗随主题模式），
  上游蓝色开屏退出历史。

### v1.2.2 — 全新品牌图标 × toggle 蓝主题化（patch 008 v4）× 检索失败外化（patch 020）（上游基线 RP-Hub 1.8.9）

> **状态：已发布（GitHub Release v1.2.2 附 APK，versionCode 9）。**

**新增**
- **全新品牌图标**：应用图标全面替换为 White Fox 头像版（用户 AI 生图，纯 1:1 满幅
  不透明，源图 `docs/design/brand-logo-v2-source.png`）；mipmap 全套密度重采样 +
  legacy round 圆形裁切 + 关于页 `luzzy-logo.png` 同步；adaptive icon 由「透明贴纸」
  改为「全图前景 68% 居中 + 同色纯背景 #EDD7BD」（取自新图边缘均值）。
- **上游遗留蓝主题化（patch 008 v4）**：tailwind.config blue/indigo 色板接入
  `rgb(var(--tw-*) / <alpha-value>)`——luzzy 主题下全部上游遗留 blue-*/indigo-* 工具类
  （toggle 选中态、设置页叙事视角等 41+8 处）随主题收编为品牌珊瑚陶土色（与 primary
  同值）；classic 主题 = Tailwind 原值零影响；violet 徽标保留为协议功能区分色
  （v1.2.0 critique 备案）。DESIGN.md「Do's & Don'ts」收编清单与技术契约同步。
- **向量检索失败外化（patch 020）**：向量分桶检索失败（分片嵌入商/模型与当前配置
  对不上导致整桶跳过等场景）由仅 console.warn 改为 toast 提示（注入检索与手动检索
  两处 catch；30s 全局节流防离线刷屏；showToast 不可用时 try/catch 自动降级）。

**注意事项**
- EXTRACT_VERSION 15→20，安装即自动重新解压资产。
- 本版实施前按硬性规定 9 复读 4 项设计 SKILL；视觉方向=「品牌色收编」（v1.2.1 已选定
  方向）的延续迭代，豁免三方向门（豁免理由落档 WORKLOG 会话 18）。
- 真机（小米 25098PN5AC / Android 16）已验证：luzzy 亮/暗下侧栏激活项、叙事视角
  选中态、settings-toggle 三开关全部珊瑚化，无白块无布局回归；classic 对照未在真机
  执行（自定义下拉不响应 adb 点击），由作用域隔离机制保证（收编规则仅 luzzy 生效），
  建议日常手动切 classic 目测复核。
- 实测发现 styles.css 另有 ~70 处硬编码蓝（不走色板工具类）：本次收编 4 族高频可见项
  （侧栏激活项/segmented 选中态/settings-toggle 家族/弹窗主按钮），其余低频组件列入
  v1.3.0 遗留。

### v1.2.1 — 侧栏品牌化 × 主题预览交互化 × 记忆链路修复与内容管理器 × 品牌色收编 × 上游标记体系（上游基线 RP-Hub 1.8.9）

> **状态：已发布（GitHub Release v1.2.1 附 APK，versionCode 8）。**

**新增**
- **侧栏品牌化与导航调整（patch 019）**：侧边栏顶部品牌字样 RP HUB → **LuzzyRP**
  （Luzzy 主字 + RP 品牌珊瑚，双色同构开屏字标）；底部簇顺序调整为 **外观 → 设置 → 关于（置底）**。
- **主题预览交互化（patch 019）**：外观页预览卡色板随主题取色（classic 显示上游原版蓝灰、
  luzzy 显示暖幕手记色板）；luzzy 主题下亮/暗双卡可直接点击切换模式（选中态 ring 标识 +
  aria-pressed，200ms ease-out 按压反馈），经典主题仅亮色单卡（经典无暗色模式）。
- **记忆内容管理器**（记忆系统页）：角色选择器（多分支角色附分支选择器）跨角色查看指定角色的
  向量分片与总结记忆全量列表（分页、轮次/嵌入模型徽标、两行预览点击展开）；支持
  **编辑 / 删除 / 参与召回开关 / 清空此角色记忆**；当前角色的改动即时联动会话上下文。
- **上游标记体系（硬性规定 10）**：全部二创改动在上游文件内携带 `[LuzzyRP patch NNN]`
  标记注释（001-012 存量补全）；`tools/patches/entities/` 新增实体 diff（上游 1.8.9 基线 →
  当前态逐文件，007/009/012-019 全覆盖）并接入 `apply-patches.ps1` 自动重放判定；
  新增 `tools/verify-markers.ps1` 校验门（标记完整性 + NSFW/styles 敏感文件指纹校验），
  同步全绿才算完成。

**优化**
- **品牌色收编**：开屏加载动画（背景蓝晕/光带/底盘阴影/LUZZY-RP 字标渐变/下划线条/嵌入页
  spinner 共 7 处）与设置页两处渐变横幅（用户设置/高级设置）由蓝色系收编为 Luzzy 品牌
  珊瑚陶土色（`primary-*` token，亮暗自适应）；仅 luzzy 主题生效，经典主题保持上游原版；
  DESIGN.md 新增「禁新增裸 blue/indigo/violet 色相类」规则。
- **开屏主题防闪蓝**：head 内联主题快照脚本 + luzzy-theme.css 移入 head +
  扩展层主题快照维护（patch 018）——冷启动首帧即当前主题色，消除「蓝色一闪再变暖」。

**修复**
- **v1.2.1 布局异常（顶部遗漏字段/底部溢出屏幕）**：根因为 patch 018 对 head 的第二段
  注入丢失 `<script>` 开标签——裸露的 `document.write` 文本被解析器判定为正文起点，
  head 提前关闭、body 提前开始，裸文本渲染到页面顶部且 `luzzy-theme.css` 主题底座
  （patch 008 色板依赖的 `--tw-*` 变量）加载失败，导致全应用配色/布局崩坏。
  补回开标签即修复（仅 1 行，未触碰上游区块）；以 parse5 浏览器同源解析器做树级对比