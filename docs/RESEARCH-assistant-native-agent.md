# 调研 · 菜单栏「助手」原生页（Kotlin 手机端 Agent）

> **状态：可行性调研完成（2026-09-09），尚未进入实施。** 本文只回答「能不能做、怎么做、
> 代价与红线」，**不含任何 UI 视觉设计**——按硬性规定 9，界面设计阶段开始前必须先完整阅读
> 4 项设计 SKILL 并走「三方向硬门」。
>
> 上游基线：RP-Hub 1.9.2（commit `d2f2625`）· 当前版本：v1.4.0 · 本文所有代码位置均以
> 当前工作树为准（文件 + 行号）。

---

## 0. 结论速览（TL;DR）

| 问题 | 结论 |
|------|------|
| 菜单栏能否新增「助手」入口？ | **能**。侧栏条目由上游 `ui-components.js` 的 `primaryItems/onlineItems/advancedItems` 数组 + 底部簇按钮渲染，新增一个条目只需改这个组件（走登记 patch）或由扩展层 DOM 注入 |
| 能否是原生 Kotlin 页面而非 WebView？ | **能**。当前是单 Activity + WebView 全屏；助手做成**同 Activity 的原生覆盖层**（FrameLayout 里 WebView 之上叠 ComposeView），或退一步做**独立 Activity**。两者都不需要重写现有前端 |
| 能否实现手机端 Agent？ | **能，且不必从零造**。OkHttp SSE 流式 + 工具调用循环 + 工具注册表 + 审批门，约 2500-4000 行 Kotlin 可做出可用第一版；参考实现 rikkahub / rikkahub-agent 已证明这条路在 Android 上成立 |
| 依赖能否离线拿到？ | **能**。本机 Gradle 缓存已含 Compose BOM 2026.08.00 / material3 1.4.0 / Room 2.8.4 / OkHttp 5.3.2 / kotlinx-serialization 1.11.0 / navigation3 1.1.2 / Koin / KSP / Compose 编译器插件 2.4.0（见 §6.1），加依赖无需联网 |
| 最大风险是什么？ | ① 协议与配置**重复实现**（Web 端已有三协议，原生要再写一套）；② rikkahub 是 **AGPL-3.0**，代码**不可复制**进本 CC BY-NC 项目，只能参考思路；③ 屏幕自动化类工具属高危能力，需用户逐项授权且与「仅侧载分发」的合规姿态要讲清 |
| 建议 | **做，但按四阶段渐进**：先交付「可用的原生 Agent 对话页」，屏幕自动化/工作流等重能力默认关闭、后续按需开（§7） |

---

## 1. 现状事实（本项目代码实证）

### 1.1 菜单栏在哪、怎么渲染

| 事实 | 位置 |
|------|------|
| 侧栏组件 `AppSidebar` | `app/src/main/assets/rphub/assets/js/ui-components.js` 第 314 行起（`primaryItems` 295-301、`onlineItems` 302-306、`advancedItems` 307-312） |
| 侧栏挂载 | `app/src/main/assets/rphub/index.html` 第 230-236 行 `<app-sidebar :current-view="currentView" ... @update:current-view="currentView = $event">` |
| 视图状态 | `assets/js/app.js` 第 282 行 `const currentView = ref('chat')` |
| 视图渲染 | `index.html` 内各视图用 `v-if="currentView === 'xxx'"` 条件渲染（如第 1260 行 settings、2020 行 appearance、2084 行 about） |
| 底部簇（二创改过） | `ui-components.js` 第 443-461 行：外观 / 设置 / 关于（patch 014 + 019 登记在案） |

即：**「新增一个菜单条目」在本项目已有先例**（patch 014/019 就是往侧栏底部簇加条目），
技术上不存在未知量。

### 1.2 现有原生层能力边界

| 事实 | 位置 |
|------|------|
| 单 Activity + 全屏 WebView | `MainActivity.kt` 第 75-91 行（`FrameLayout` + insets 监听） |
| JSBridge 现有方法 | `web/LuzzyBridge.kt`：剪贴板 / Toast / 版本 / 系统栏 / 设备信息 / 外链 |
| 桥接封装约定 | `assets/ext/luzzy-bridge.js`（存在性检测 + 降级，AGENTS §5.1） |
| 依赖极简 | `app/build.gradle.kts` 仅 androidx core/activity/webkit/lifecycle + coroutines；**当前无 Compose、无 Room、无 OkHttp** |
| minSdk / target | 26 / 37（Android 8.0+，与 rikkahub-agent 的 26/37 一致） |

### 1.3 配置与数据的落点（决定「助手怎么拿到 API Key」）

| 事实 | 位置 |
|------|------|
| Web 端数据仓 | `assets/js/data-services.js` 第 5-6 行：IndexedDB `RPHubDB` → objectStore `store`（键值） |
| 供应商体系 | `app.js`：`settings.apiProviders` / `settings.activeApiProviderId` / `settings.apiProviderOverrides`（patch 012/029 建立） |
| 结论 | 助手的「供应商 + Key + 模型」**应当从 Web 端读**（经桥接取 `settings` 相关字段），避免用户填两遍、避免密钥在设备上出现第二份副本 |

---

## 2. 方案对比：原生页怎么装进这个壳

| 方案 | 做法 | 优点 | 代价 | 判断 |
|------|------|------|------|------|
| **A · 独立 Activity** | 侧栏条目 → 桥接 `openAssistant()` → 启动 `AssistantActivity`（Compose） | 隔离最干净；生命周期简单；WebView 不受影响 | 两套 Activity 生命周期；返回栈与主题切换需各处理一次；「像应用内一个页面」的感觉弱 | 可行，作为**兜底方案** |
| **B · 同 Activity 覆盖层**（推荐） | `MainActivity` 的 `FrameLayout` 里在 WebView 之上加 `ComposeView`（初始 `GONE`）；侧栏点「助手」→ 桥接 → `visibility = VISIBLE` | Web 端状态**原样保留**（返回即回到原视图）；返回键/系统栏/主题统一在 Activity 处理；视觉上是「应用内的一页」 | 两套视图树共存，需处理内存与 `onPause/onResume`；Activity 类要拆出视图容器职责 | **推荐** |
| C · 把助手也做成 WebView 页面 | — | — | 与需求「非 WebView、原生 Kotlin」直接冲突 | 排除 |

**方案 B 的关键工程细节**（实施时必须逐条落实）：

1. **返回键**：`OnBackPressedCallback` 优先级——原生层可见时先关原生层，其次 WebView 回退，最后退出（现有实现见 `MainActivity.kt` 第 94-103 行）。
2. **键盘**：`AndroidManifest.xml` 已设 `adjustResize`，Compose 输入框沿用即可，但要验证 insets 与现有 `ViewCompat.setOnApplyWindowInsetsListener` 不打架。
3. **系统栏**：原生页沿用 `LuzzyBridge.setSystemBarStyle` 的同一策略（亮/暗模式图标色），避免切页时状态栏跳变。
4. **内存**：WebView + Compose 同时常驻，低端机风险最高；原生页隐藏时把 Compose 内容降级为「停止订阅 + 释放列表缓存」，不要 `destroy()`。
5. **主题**：Web 端主题存在 `settings.themeMode`；原生侧通过桥接读同一值，UI 用 Compose 侧色板实现（**不复用 CSS**，但颜色 token 必须对齐 `DESIGN.md`）。

---

## 3. 侧栏入口怎么加（硬性规定 2/3 下的两条路）

### 3.1 路径甲 · 扩展层 DOM 注入（不碰上游）

`assets/ext/` 新增 `luzzy-assistant.js`：找到 `.app-sidebar .sidebar-nav`，按现有
`sidebar-nav-button` class 结构注入一个 `<button>`，点击时调用桥接 `Luzzy.openAssistant()`。

- ✅ 零上游改动、零 patch；
- ⚠️ 依赖上游 DOM 结构（Vue 重渲染可能吃掉注入节点）→ 必须用 `MutationObserver` 重新注入，
  并在找不到锚点时**静默降级**（AGENTS §5.3：扩展层报错不得影响主流程）；
- ⚠️ 与上游 `v-show`/`collapsed` 折叠态、激活态样式需手动对齐（否则折叠时按钮错位）。

### 3.2 路径乙 · 登记 patch（改 `ui-components.js` + `index.html`）

按现有 patch 014/019 的成熟做法，在 `primaryItems` 或底部簇加条目，并在 `index.html`
挂一个 `v-if="currentView === 'assistant'"` 的空容器（或什么都不挂，由扩展层接管）。

- ✅ 结构与上游同源，折叠/激活态自动正确；同步时可重放（`patches/entities/` 机制已成熟）；
- ⚠️ 需新增 patch 编号 + `[LuzzyRP patch NNN]` 标记 + verify-markers 校验项 + 实体重生成。

### 3.3 推荐

**先用路径甲做原型**（验证「点击 → 原生层出现」这条链路，零风险、可随时删），
**落地时切路径乙**（稳定性优先，且符合项目「上游文件改动必须登记」的既有纪律）。
无论哪条路，`currentView` 里**不要**真的新增一个需要 Vue 渲染的视图——原生页不在 DOM 里，
点了就调桥接。

---

## 4. 原生 Agent 架构设计

### 4.1 分层

```
MainActivity
└─ FrameLayout
   ├─ WebView（现有，保持不动）
   └─ ComposeView ← assistant（初始 GONE，侧栏点「助手」时可见）
      └─ AssistantNavHost
         ├─ 会话列表 / 会话页 / 工具权限页 / 设置页
         ├─ AssistantViewModel（会话状态机：idle/streaming/awaiting-approval）
         ├─ AgentLoop（纯 Kotlin，无 Android 依赖，可单测）
         │   ├─ LlmTransport：OkHttp + SSE，OpenAI / Anthropic / Gemini 三协议分派
         │   ├─ ToolRegistry：工具声明（JSON Schema）+ 执行器 + 权限档
         │   ├─ ApprovalGate：每次「写类」工具调用前挂起等用户确认
         │   └─ BudgetGuard：轮次/时长/token 上限，防跑飞
         └─ 数据层
             ├─ Room：会话 / 消息 / 工具调用记录（本地，与 Web 端 IndexedDB 分离）
             └─ DataStore：助手自己的偏好（模型、温度、工具开关、审批策略）
```

### 4.2 Agent 主循环（伪码）

```kotlin
suspend fun runTurn(userInput: String) {
    history += Message.User(userInput)
    repeat(maxRounds) {                      // 默认 8 轮，可配
        val stream = transport.stream(history, tools = registry.enabledSchemas())
        val text = StringBuilder(); val calls = mutableListOf<ToolCall>()
        stream.collect { delta ->            // 流式：先渲染文本，再收集 toolCalls
            text += delta.content
            calls += delta.toolCalls
            ui.emit(AssistantEvent.Delta(delta))
        }
        history += Message.Assistant(text.toString(), calls)
        if (calls.isEmpty()) return          // 无工具调用 = 本轮结束
        for (call in calls) {
            if (approvalGate.needsApproval(call)) ui.awaitApproval(call)   // 挂起
            val result = registry.execute(call)                            // 超时 + 异常兜底
            history += Message.Tool(call.id, result)
            ui.emit(AssistantEvent.ToolResult(call, result))
        }
    }
}
```

要点：

- **取消**：`Job` 取消要同时关 SSE 连接与工具执行；UI 必须有「停止」按钮；
- **截断**：与 Web 端抗截断（`output_reply` 协议）思路一致，超长输出按轮次继续；
- **超时**：单次请求空闲超时 120s（对齐 Web 端 `api-utils.js` 的既有参数）、工具执行 30s；
- **降级**：模型不支持 tool calling 时退化为「纯对话」并提示。

### 4.3 工具集分级（建议）

| 档 | 工具 | 依赖 | 建议默认 |
|----|------|------|---------|
| **P0 只读/无授权** | `get_time`、`get_device_info`、`clipboard_read/write`、`web_fetch`（含正文提取） | OkHttp / 现有桥接 | 开 |
| **P0 网络** | `web_search`（内置无 Key 引擎 + 可选自带 Key：Tavily/Exa/Brave/SearXNG 等） | 网络 | 开（可关） |
| **P1 需 SAF/用户确认** | `file_pick_read`、`file_export`、`notification_post`、`share_intent` | SAF / NotificationManager | 逐个开 |
| **P1 应用内集成** | `character_list`、`worldinfo_read`、`send_to_chat`（把 Agent 结果回填到 RP 会话） | 桥接读 `RPHubDB` | 开（只读） |
| **P2 高危（默认全关）** | `screen_capture`、`accessibility_tap/swipe/type`、`app_launch`、`sms_send`、`contacts_read`、`shell`（Shizuku/Termux） | 无障碍服务 / 系统权限 / 外部工具 | **关，且需二次确认** |

红线：

- **P2 一律默认关闭**，开关要有明确风险说明；不做「静默授权」；
- 屏幕自动化需要用户在系统设置里手动开启无障碍服务（`AccessibilityService`），OEM 还有额外限制，
  不能由 App 自行开启；
- 本应用**仅侧载分发**（nsfw_rules 年龄条款），高风险工具的存在不改变这一约束。

### 4.4 配置共享与数据边界

| 数据 | 归属 | 说明 |
|------|------|------|
| 供应商 / API Key / 模型列表 | **Web 端**（`RPHubDB`） | 助手经桥接读（只读或只写必要字段），不复制密钥到第二处 |
| 助手会话历史 / 工具记录 | **原生侧**（Room） | 与 RP 聊天记录分离；「发送到聊天」是显式动作，不做隐式同步 |
| 助手偏好（模型槽位、工具开关、审批策略） | **原生侧**（DataStore） | 与 Web 端 `settings` 解耦，避免污染上游数据结构 |
| 主题 / 模式 | Web 端 `settings.themeMode` 为真源 | 原生侧只读跟随，不反向写 |

需要新增的桥接方法（草案，实施时按 AGENTS §5.4 流程登记）：

| 方法 | 方向 | 用途 |
|------|------|------|
| `openAssistant()` | JS → Kotlin | 侧栏入口触发原生层显示 |
| `getAssistantConfig()` | JS → Kotlin → JS 回调 | 取激活供应商 / Key / 模型（**只读**，前端脱敏展示） |
| `onAssistantClosed()` | Kotlin → JS | 原生层关闭后前端恢复焦点与视图态 |
| `sendAssistantResult(json)` | Kotlin → JS | 把 Agent 产出回填到指定会话（显式触发） |

---

## 5. 参考项目分析

### 5.1 rikkahub（上游，[GitHub](https://github.com/rikkahub/rikkahub)）

- **定位**：原生 Android LLM 聊天客户端（7.5k★，2,834 commits，活跃）。
- **技术栈**（官方 README）：Kotlin · Koin（DI）· Jetpack Compose · DataStore · Room · Coil ·
  Material You · Navigation 3 · OkHttp · kotlinx.serialization。
- **与 Agent 相关的官方能力**：MCP 支持 · Workspace（基于 proot 的 Linux agent 环境）·
  Search（Exa/Tavily/Zhipu/LinkUp/Brave/Perplexity 等）· Agent 自定义 · 类 ChatGPT 记忆 ·
  多供应商（OpenAI/Google/Anthropic 兼容 API）· 多模态输入 · Markdown/LaTeX/Mermaid 渲染 ·
  消息分支 · Prompt 变量。
- **许可**：**AGPL-3.0**。

### 5.2 rikkahub-agent（社区 fork，[GitHub](https://github.com/ExTV/rikkahub-agent)）

- **定位**：把 rikkahub 变成真正的 on-device agent（241★，3,418 commits）。
- **能力清单**（官方 README 摘要）：80+ 设备工具（点击/滑动/截图/开应用/音量亮度/通知/短信/
  通讯录/NFC/Keystore/ZIP）· 工作流（19 触发 × 14 条件）· 定时任务（reboot 存活）·
  Telegram Bot（含代理）· 内置浏览器（AI 驱动 + 动作轨迹 + 截图流）· 无 Key 网页搜索
  （DuckDuckGo 内置 + 19 引擎可选）· `web_fetch`/`web_extract`（jsoup readability、30s 上限、
  私网地址 DNS 层拦截）· 文件管理器 · Workspace（proot Linux + 后台任务 id 机制）· SSH ·
  音乐媒体 · Skills（Markdown 技能文件）· Sub-Agents（并行子代理 + 按名字派发）·
  上下文压缩 · Doctor（健康检查）· MCP 服务器 · 通知与外触发器 · Shizuku 提权（默认关）·
  三层安全模型：**每个工具默认关闭 / 逐调用审批 / HARDLINE 不可绕过的危险命令黑名单**。
- **许可**：AGPL-3.0（继承上游）。

### 5.3 对本项目的可用性判断

| 维度 | 判断 |
|------|------|
| **代码能否复制** | ❌ **不能**。AGPL-3.0 与本项目 CC BY-NC 4.0 不兼容，且 AGPL 的网络分发条款会改变本项目的许可姿态；任何「抄代码/抄文件结构」都不可接受 |
| **架构思路能否借鉴** | ✅ 可以（工具分级、逐调用审批、HARDLINE 底线、工具开关默认关、上下文压缩、Doctor 自检——这些是通用工程实践） |
| **依赖能否复用** | ✅ 可以（Koin/Compose/Room/OkHttp 等都是 Apache-2.0/MIT，与本项目许可无冲突） |
| **对本项目最值钱的三点** | ①「工具默认全关 + 逐调用审批 + 危险操作硬底线」三层安全模型；②`web_fetch` 的私网地址 DNS 层拦截（防 SSRF）；③无 Key 内置搜索 + 自带 Key 引擎可插拔 |

---

## 6. 技术可行性验证（本机实测/清单）

### 6.1 依赖与工具链（本机 Gradle 缓存实测，离线可用）

| 依赖 | 缓存版本 | 用途 |
|------|---------|------|
| `androidx.compose:compose-bom` | 2026.05.01 / **2026.08.00** | Compose 版本对齐 |
| `androidx.compose.material3:material3` | 1.4.0（+1.5.0-alpha21） | UI 组件 |
| `androidx.activity:activity-compose` | 已缓存 | Compose 宿主 |
| `androidx.lifecycle:lifecycle-runtime-compose` | 已缓存 | `collectAsStateWithLifecycle` |
| `androidx.navigation3:navigation3-runtime/ui` | 1.1.2 | 页面导航 |
| `androidx.room:room-runtime/ktx/compiler` | 2.8.4 | 本地库（+ KSP） |
| `androidx.datastore:datastore-preferences` | 1.2.1 | 偏好存储 |
| `com.squareup.okhttp3:okhttp` | 5.3.2 / 5.5.0（`okhttp-sse` 5.3.2） | 流式 HTTP |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0 | JSON |
| `io.insert-koin:koin-androidx-compose` | 已缓存 | DI（可选，也可手写） |
| `com.google.devtools.ksp` | 已缓存 | 注解处理 |
| `org.jetbrains.kotlin:compose-compiler-gradle-plugin` | 2.2.10 / **2.4.0** | Compose 编译器（AGP 9 内置 Kotlin 下需按官方方式接入） |

**结论**：新增依赖**全部命中本地缓存**，无需联网即可编译；唯一需要确认的是 **AGP 9.2.1
内置 Kotlin 与 Compose 编译器插件的接入写法**（见 §8 待验证项）。

### 6.2 需要新增的 Android 能力（清单）

| 能力 | 声明/依赖 | 备注 |
|------|-----------|------|
| 网络 | 已有 `INTERNET` / `ACCESS_NETWORK_STATE` | 无需变更 |
| 通知（后台任务） | `POST_NOTIFICATIONS`（API 33+ 运行时授权） | P1 工具与长任务用 |
| 前台服务（长任务） | `FOREGROUND_SERVICE` + 类型声明（Android 14+） | 需要「长任务跑在后台」时才加，第一版可先不做 |
| 无障碍（P2） | `BIND_ACCESSIBILITY_SERVICE` + 用户手动开启 | 默认不做 |
| 存储（P1） | SAF（已有 FileChooserHandler/DownloadHandler 模式） | 复用现有实现 |
| 截图（P2） | `MediaProjection` + 每次授权弹窗 | 默认不做 |

### 6.3 体积与性能影响（预判，实施时实测）

- Compose + Room + OkHttp 预计增加 **约 2-4 MB** 安装体积（当前 APK 17.33 MB）；
- 冷启动：Compose 首次组合有毫秒级成本，建议**懒加载**（首次点击「助手」时才创建 ComposeView）；
- 运行时：WebView + Compose 同时常驻内存增加，需在低端机实测（真机小米 25098PN5AC 可用）。

---

## 7. 分阶段路线（建议）

| 阶段 | 目标 | 交付物 | 验收 |
|------|------|--------|------|
| **P0 · 打通链路** | 侧栏点「助手」→ 原生层出现 → 返回可回 Web | `luzzy-assistant.js`（入口）+ `AssistantActivity`/覆盖层骨架 + 桥接 `openAssistant()` | 真机：点击出现/返回恢复；Web 端视图状态不丢；无异常日志 |
| **P1 · 最小可用 Agent** | 流式对话 + 工具调用 + 审批门 + 本地历史 | `AgentLoop` + `LlmTransport`（OpenAI 协议先行）+ 工具 4-6 个（time/clipboard/web_fetch/web_search）+ Room + 工具卡片 UI | 真机：多轮工具调用跑通；「停止」可中断；断网/超时有明确报错；密钥不落日志 |
| **P2 · 三协议 + 应用内集成** | Anthropic/Gemini 协议 + 读角色卡/世界书 + 结果回填会话 | 协议适配层 + `getAssistantConfig()` + `sendAssistantResult()` | 桌面/CDP 走查 + 真机：三协议各跑一轮；回填后 RP 会话内容正确 |
| **P3 · 重能力（可选，默认关）** | 搜索多引擎 / 文件管理 / 通知与定时 / 屏幕自动化 | 按 §4.3 分级逐项开 | 每项独立开关 + 风险说明 + 逐调用审批；HARDLINE 底线用例通过 |

**跨阶段纪律**：

- 每阶段都遵守硬性规定 2/3/10（上游零裸改、扩展层隔离、标记与重放）；
- 每阶段结束更新 CHANGELOG + WORKLOG；
- **P1 之前不写任何 UI 视觉**——先按硬性规定 9 阅读 4 项设计 SKILL、出 3 个方向、写进 `DESIGN.md`。

---

## 8. 风险与待验证项

| # | 风险/未知 | 影响 | 处置 |
|---|-----------|------|------|
| R1 | **许可不兼容**：rikkahub/rikkahub-agent 是 AGPL-3.0 | 高（法律） | 只借鉴思路，**不复制任何代码/文件结构/文案**；新增依赖只用 Apache-2.0/MIT |
| R2 | AGP 9.2.1 内置 Kotlin 下 Compose 编译器插件接入方式 | 中（构建） | 实施第一步先做「空 Compose 页面编译通过」验证；参考官方 Compose 编译器迁移文档 + JetBrains AGP 9 迁移博客（已查证 AGP 9 内置 Kotlin 为默认路径） |
| R3 | 两套视图共存的内存/生命周期 | 中 | 懒创建 + 隐藏时释放列表；真机低内存场景实测 |
| R4 | 协议与配置重复实现，双份维护成本 | 中 | 配置单向读 Web 端；协议层只做「够用」子集（流式 + toolCalls），不追求覆盖 Web 端全部特性 |
| R5 | 后台长任务受系统限制（Doze/后台限制/厂商杀进程） | 中 | 第一版不做后台常驻；需要时用前台服务 + 通知，并在 UI 明示 |
| R6 | 屏幕自动化权限敏感、OEM 限制 | 中（合规+体验） | 默认关闭 + 明确风险说明 + 不做静默授权；不因此改变「仅侧载」定位 |
| R7 | 网页抓取/搜索的 ToS 与隐私 | 中 | 只用公开搜索 API / 用户自带 Key；`web_fetch` 做私网地址拦截（对齐 rikkahub-agent 的做法）；不绕过反爬 |
| R8 | 与 `nsfw_rules` 的边界 | 高（硬性规定 1） | 助手**不得**引入任何内容审查/过滤/改写逻辑；若未来做内容相关工具，必须先与用户确认边界 |
| R9 | 菜单入口的 DOM 注入被 Vue 重渲染吃掉 | 低 | 原型用 DOM 注入，落地走登记 patch（§3.3） |

---

## 9. 决策点（需用户拍板）

| # | 决策 | 选项 | 建议 |
|---|------|------|------|
| D1 | 原生页形态 | A 独立 Activity / **B 同 Activity 覆盖层** | B（Web 状态保留、体验更「应用内」） |
| D2 | 侧栏入口实现 | 甲 DOM 注入 / **乙 登记 patch** | 甲做原型 → 乙落地 |
| D3 | 助手定位 | 只做「通用 AI 助手」 / **兼顾「RP 助手」**（读角色卡/世界书、回填会话） | 兼顾，但应用内集成放 P2 |
| D4 | 高危工具 | 现在就规划 / **P3 再说，默认全关** | P3 |
| D5 | 是否需要后台/定时任务 | 不需要 / **先不做，按需再加** | 先不做 |
| D6 | 是否引入 Koin | 引入 / **手写依赖注入** | 手写（少一个依赖，模块不大） |

---

## 10. 引用与来源

- [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) —— 上游原生客户端（AGPL-3.0，技术栈与 Agent 相关能力见 README）
- [ExTV/rikkahub-agent](https://github.com/ExTV/rikkahub-agent) —— Agent 化 fork（80+ 设备工具、三层安全模型、工作流/定时/Telegram/内置浏览器等）
- [RikkaHub 官网](https://rikka-ai.com/) / [文档](https://docs.rikka-ai.com/llms.txt)
- [ADK for Android（Google）](https://developer.android.com/ai/adk) —— 官方 Android Agent 框架（备选）
- [JetBrains Koog](https://github.com/JetBrains/koog) / [Koog 文档](https://docs.koog.ai/) —— JVM/Android Kotlin Agent 框架（备选）
- [AGP 9.0 内置 Kotlin 迁移（JetBrains 博客）](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/) —— Compose 编译器插件接入前提
- [Android 无障碍服务](https://developer.android.com/guide/topics/ui/accessibility/service) —— P2 屏幕自动化的官方路径
- [WorkManager 长任务](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running) —— 后台长任务方案（P5 之后才需要）

> 来源分级说明：rikkahub / rikkahub-agent 的能力与许可以**官方仓库 README 原文**为准（一类来源，
> 已直接抓取核对）；ADK/Koog/AGP9/无障碍/WorkManager 以**官方文档**为准（一类来源）。
> 本机依赖版本为 **Gradle 缓存实测**（本地事实，非网络来源）。结论中标注为「建议/预判」的部分
> 属工程判断，需实施时以实测为准。
