/**
 * LuzzyRP 扩展层 · 应用内更新日志（patch 014，由 tools/gen-changelog.mjs 自动生成）
 * 来源：仓库根 CHANGELOG.md —— 请勿手改本文件，改 CHANGELOG.md 后重新运行生成脚本。
 */
(function () {
    window.LuzzyChangelog = { md: `# 更新日志（CHANGELOG）

> LuzzyRP 遵循语义化版本（\`MAJOR.MINOR.PATCH\`）；\`x.y.0\` 视为稳定版并附 APK。
> 格式：\`### vX.Y.Z — 标题\` + 「新增 / 优化 / 修复 / 注意事项」分类要点 + 构建结果与 versionCode。
> **v1.0.0 起：每条记录注明上游基线版本（RP-Hub）。** 旧 v0.x 记录保留于下方历史区。

### v3.0.0 — 全面转 Jetpack Compose（开发中）（上游基线 RP-Hub 1.9.3 · 基线定格）

> **状态：开发中（2026-09-12 起）。** 主计划 \`docs/PLAN-v3.0-compose.md\`（P0-P6 分期）。
> 设计语言「**织机 Loom**」（用户 2026-09-12 三方向选定），设计真源 \`docs/DESIGN-compose.md\`。
> 上游同步已退役（见 v2.0.0 注意事项），v2.x WebView 版在 P6 切换前保持可发布。

**新增**
- **P1「空壳可跑」完成（2026-09-12）**：
  - **Compose 座接线**：Compose BOM（版本组合自助手时代先例 \`52aab12c\` 回收）+
    \`kotlin.plugin.compose\` + buildFeatures.compose + composeMappingProducerClasspath
    钉版本补丁；新增独立 \`ui.ComposeActivity\`（**launcher 仍为 WebView 版 MainActivity**，
    v2.x 用户体验零影响；ComposeActivity 经 adb 显式启动验证，P6 才切换）；
  - **HCT 动态色板**：vendor material-color-utilities（**Apache-2.0**，\`ui/theme/mcu/\`，
    与 AGPL 并存无冲突）→ seed 珊瑚陶土 \`#CC785C\` → TONAL_SPOT 亮暗双 ColorScheme；
    权威生成值落档 \`DESIGN-compose.md\` §2 并以快照测试钉死
    （亮画布 \`#FFF4F1\` / 暗画布 \`#231917\`；primary 亮 \`#8F4C35\` / 暗 \`#FFB59D\`）；
  - **字体本地打包（硬性规定 4）**：8 枚 TTF（Lora×2 / PuHuiTi×3 / AlibabaSans×3）自
    git 历史恢复落 \`res/font/\`；Lora display 族 + PuHuiTi 正文族 + API 29+ 中文回退链；
  - **假数据聊天页**：顶栏（Luna + Lora 名牌体系）/ 无气泡 AI 消息（叙述/动作斜体/对白）/
    primaryContainer 用户气泡（320dp 上限）/ 思考卡折叠行 / \`‹ 2/3 ›\` 分支 chip /
    输入岛（28dp 圆角 + 38dp 圆形发送键 + 导航栏避让）/ Modal 抽屉 /
    MeshGradient 背景（线性底 + 光斑漂移，无 blur，亮暗两套）+ 动效令牌（200/140ms 贝塞尔）；
  - **验证**：模拟器（LuzzyRP_Test）装机可见、可滚动、亮暗切换正常、抽屉正常
    （截图 \`docs/design/verify-p1-*.png\`）；\`assembleDebug\` + \`assembleRelease\`（R8 + luzzy
    签名，38.3MB，字体 +21MB 为既定接受增量）双过；单测全绿（既有 207 + 新增色板快照）。
- **P2 第三项「接 Kotlin 传输做流式上屏」完成（2026-09-12）——零模拟**
  （**P2 整体的另两项「会话列表」「消息渲染 Markdown→Compose」尚未开始**；
  原属 P3 的输入岛/发送/停止已顺带完成。详见 \`docs/PLAN-v3.0-compose.md\` §7 的更正记录）：
  界面上每一次变化都对应一次真实事件，
  没有 \`delay()\` 造的假打字、没有预置假节点、没有假工具结果：
  - **引擎层（纯 Kotlin，新增 5 文件）**：\`ChatEngine\`（事件流 + **工具循环**：模型请求工具 →
    本地执行 → 结果回填 → **再发一次真实请求**，≤3 轮）/ \`RecallEngine\`（真实会话检索：
    CJK 二元组 + 拉丁词重叠打分，命中注入 \`<memory_recall>\`）/ \`WorldBookTool\`
    （\`world_info_lookup\` 真实执行）/ \`TransportConfig\` + \`TransportStore\`（供应商配置，
    **密钥只存设备本地**、界面打码）/ \`VanioCard\`（演示角色人设 + 世界书）；
  - **真流式**：每个 \`LlmDelta\` 触发一次且仅一次状态追加（不插值、不节流、不合并）。
    真机实测 4 轮：正文 465 个增量中 **267 个是单字符**（reasoning 504 个中 191 个），
    工具参数由模型流式发出（20~37 片）；
  - **思考节点入气泡**（用户指示）：思考卡改为 AI 气泡内的第一个子块（与名牌、正文同处一块
    玻璃面板），生成完成后**随消息常驻**、不再消失；
  - **逐节点时序**：\`activeIndex\` 驱动「**自动展开 → 内容逐字流入 → 完成后自动收起**」；
    用户手动点开的展开态优先于自动态；
  - **子节点溢出**：\`heightIn(max = 200.dp)\` + 卡内滚动——过长内容可滑，不截断也不撑爆消息流；
  - **输入框与跟随**：\`BasicTextField\` 的 \`decorationBox\` 恢复 \`Box\` 包裹（修复输入框点不中、
    拿不到焦点）；发送后列表 \`pinToBottom()\` 并**在内容变化前**判定是否贴底
    （修复「发送后新气泡留在屏幕外」）；
  - **验证**：模拟器 + 真实端点（OpenAI 兼容协议）4 轮真请求，截图存档
    \`docs/design/verify-p2-*.png\`（8 张，含 live 态节点内正文流式、卡内滚动前后对比）。
    验证所用供应商密钥**只写入模拟器内 App 的 SharedPreferences**（经 \`run-as\` 注入），
    **不入库、不进安装包**，日志与界面一律打码。

**新增（开发工具）**
- **仅 debug 源集的文本注入钩子**：模拟器只有英文 IME（\`adb shell input text\` 遇中文直接抛
  NPE），而本应用内容全为中文。\`ui/DevHooks.kt\`（main 源集，两个恒为 null 的挂点，
  **release 下零行为**）+ \`src/debug/\` 的 \`DevInputReceiver\`（adb 广播注入文本并触发发送）
  ——**不进 release 包**（源集隔离），后续 UI 端到端验证可自动化。

- **P2 第一项「剧情分支列表」完成（2026-09-12）**——我们的「会话列表」语义：
  - 侦察实证：上游**没有平铺会话列表**，一次「会话」= 角色 × **剧情分支**
    （\`StoryBranchModal\`，存储键 \`rp_hub_chat_{角色}__branch__{分支}\`）；本项按此语义实现
    （用户 2026-09-12 拍板；「跨角色平铺总览」归 P4 数据层一并设计）。
  - \`chat/BranchModel.kt\`（纯 Kotlin）：\`ChatBranch\` / \`BranchStat\` / \`BranchTree\` —— 主线优先排序、
    主线不可改名不可删除、删除连坐后代、当前分支被删回退主线；**楼数/字数由真实消息算出**。
  - \`ui/pages/chat/BranchListSheet.kt\`：聊天页顶栏入口（与上游同位置）→ \`ModalBottomSheet\`；
    每项 = 分支名 + \`N 楼 · x.x 字\` + 「起点」「当前」+「自主线第 N 楼分出」+ 进入/重命名(≤30 字)/删除；
    **层级缩进 + 树形连接线**替代上游的 SVG 路线图。
  - 生成期间切分支时，结果仍落在**发起的那条分支**（\`turnBranchId\` 快照）。

- **P2 第二项「消息渲染 Markdown→Compose」完成（2026-09-12）**：
  - 解析 \`org.jetbrains:markdown:0.7.3\`（**Apache-2.0**，与 AGPL-3.0 自有代码兼容；rikkahub 同款），
    渲染层自写（\`ui/markdown/\`）：标题 / 段落 / 粗斜体 / 删除线 / 行内代码 / 围栏代码块
    （语言标签 + 超 10 行折叠）/ 引用 / 有序·无序·嵌套列表 / 分隔线 / GFM 表格 / 链接。
  - **修正一处离上游的语义偏差**：\`*动作*\` 现在是标准 emphasis 斜体（此前把星号当字面量画出来）、
    \`「对白」\`是普通文本 —— 与上游 marked v15（GFM + \`breaks:true\`）一致。
  - **流式容错**：未闭合围栏 → 代码块 + 「生成中…」标识；未闭合强调符 → 保留标记符不吞字符；
    半截链接 → 退化为文本；HTML 块 → 原样展示（不做 sandbox 直通，P5 单独立项）。
  - **成本实测**：4090 字平均单次解析 **6.29ms**（单测成本门 <60ms）→ 逐字流式期间全量重解析
    即在预算内，**不需要前缀缓存**（实测代替假设）。解析在 \`Dispatchers.Default\`，以内容为 key
    自动取消上一次，主线程不解析。
  - 未做（如实记录）：LaTeX / Mermaid / HTML 直通 / 图片；表格列宽等分（长表格会挤）；代码块无语法高亮。

- **聊天链路收口（2026-09-12，按用户批准的计划 T1–T8）**：
  - **键盘避让（真缺陷）**：真机取证 \`mInputShown=true\` 时**输入岛被键盘完全盖住** ——
    \`ComposeActivity\` 开了 \`enableEdgeToEdge()\`，该模式下 \`windowSoftInputMode=adjustResize\`
    不再让出键盘高度，必须显式消费 IME inset。修：\`navigationBarsPadding().imePadding()\`。
  - **滚动跟随精确化 + 回到底部**：\`isAtBottom()\`（末项可见且底边到达视口底）替代
    \`!canScrollForward\`；跟随即 \`isAtBottom() && !isScrollInProgress\`；新增回底圆钮
    （脱离底部时出现，进入 200/退出 140ms）与「离开底部期间有新内容」主色圆点。
  - **错误改悬浮卡栈**：错误不再作为一条「消息」插入对话（可复制/单条关闭/多条全部清除），
    **顺带修掉一个真口径缺陷**——错误此前被 \`BranchStat.of\` 算作一条消息，导致分支
    **楼数/字数被算多**；现在类型层面不可能（\`ChatMessage\` 只有 Ai/User，有单测钉死）。
  - **用量/耗时脚注**：传输层早已解析 usage 且 \`include_usage\` 早已开启，但引擎从不把它变成事件、
    UI 从不渲染 → 补 \`Event.Usage\` + \`AiResult(usage, elapsedMs)\` + 消息脚注
    \`输入 742（缓存 512） · 输出 134 · 2.3s · 59 tok/s\`（真机实测值）；缓存字段按供应商差异
    探测（DeepSeek \`prompt_cache_hit_tokens\` / OpenAI \`prompt_tokens_details.cached_tokens\`），
    取不到即 null。
  - **截断可见化**：\`finish_reason ∈ {length, max_tokens}\` → 脚注告警色「已截断（达到输出上限）」
    + 一次 Snackbar 提示提高最大输出（此前该字段存了却完全不可见）。
  - **操作安全**：删除（单条 / 及其后）先弹确认并写明条数；**编辑用户消息后**弹确认
    「按新内容重新生成？（其后楼层会被删除）」，可选「只改内容」（新增 \`regenerateFrom\`）。
  - **正文可选中复制**：静止消息挂 \`SelectionContainer\`，**流式期间不挂**（防并发修改崩溃）。
  - **验收标准写死**：\`DESIGN-compose §19\` 给出「操作 → 期望 → 证据」验收表 + 10 步回归清单。
  - 单测 **294 全绿**（本轮新增 11：用量/截断 9 + 结构不变式 2）。

- **聊天链路回归能力（2026-09-12，Stage 0）**：
  - **状态机单测**：\`ChatTurnStateTest\`（10 例）——\`LiveTurn\` 此前**零覆盖**，而它是「引擎事件 → 界面」
    的唯一状态入口（逐字追加、工具参数累积、\`activeNode\` 时序、用量/耗时落点、失败态）。
  - **仪器化 UI 测试基座**：新 \`src/androidTest\`（\`androidx.compose.ui:ui-test-junit4\` +
    \`androidx.test.ext:junit:1.3.0\`），首批 7 例确定性用例（**假传输驱动，不联网**）：
    未配置→弹配置框 / 发送键状态 / 流式上屏+用量脚注 / 失败→错误卡且不入列表 / 删除先确认 /
    世界书真条目 / 工具开关联动。
  - **一键回归 + 真机门禁**：\`ANDROID_SERIAL=emulator-5554 ./gradlew checkChat\`；
    新增 \`verifyEmulatorDevice\` 强制仪器化测试只跑模拟器（\`connectedDebugAndroidTest\`
    默认会在**所有已连接设备**上安装执行——曾因此把测试件指向真机，AGP 报告里的设备名 \`A9210\`）。
  - 手册与报告：\`docs/CHAT-REGRESSION.md\`（命令 + 十步人工走查 + 写 UI 测试的三个坑）、
    \`docs/design/regression-p3.md\`（十步逐条 E/M 归属）。

- **P4-A 迁移通道：真实数据夹具 + 通道 go/no-go 探针（2026-09-12，会话 70）**
  —— 设计真源新增 \`docs/DESIGN-migration.md\`：
  - **真实旧数据夹具**：\`app/src/test/resources/legacy/webview-db-fixture.json\`
    （29 主库键 + 3 旧库键，139 KB）。**不是手写样本**——模拟器装 release 包后用 CDP
    驱动**前端自己的函数**造数据：\`createNewCharacter\`/\`saveCharacter\`（头像走真实
    \`compressImage\`）/ \`sendMessage\`（**真实调用 DeepSeek**，SSE 流）/ \`createStoryBranch\`
    / \`createWorldInfo\`·\`saveWorldInfo\` / \`startBatchMemoryExtraction\`（经典总结 + **真实
    3072 维嵌入** → 应用自身 \`int8:maxabs:v1\` 量化落盘）/ \`createNewProfile\`。
    含角色 3、会话 2 分支 5+5 条、向量记忆 2+2、经典记忆 2+2、世界书 2+2、预设 19、
    用量记录 9、人设 2、\`last_active_char=1\`（非零下标判据）。
    **密钥已脱敏**（\`<REDACTED:name:lenN>\` 占位符，保留字段与长度，清单入夹具
    \`provenance.redaction\`）；生成脚本随仓库入库 \`tools/mig-fixture/\`（含复现说明）。
  - **通道探针结论 = GO**：实测**另一个 \`file://\` 页面**（\`files/ext/\` 下）能读到
    \`files/rphub/index.html\` 写入的 IndexedDB —— 30 键可见、3 张角色卡按名字读回、
    写入的探针键事后能在主页面读到。故迁移走**轻量页 \`ext/luzzy-migrate.html\`**（不启动 Vue）；
    「打开 index.html 再注入」作为降级保留。成因是 \`WebViewSetup\` 的
    \`setAllowFileAccessFromFileURLs\` + \`setAllowUniversalAccessFromFileURLs\`——已在文档里
    立成纪律：迁移 WebView 必须复用同一套配置，且门禁要断言「键集非空」（否则会**静默失效**）。
  - 文档同时把旧数据的**真实形态**（键命名空间表、作用域拼接规则、消息/记忆/用量字段、
    \`memory_settings.emptyTurns\` 的键是 \`<scope>:<mode>\`）与 **12 条坑的处置表**写死。
  - \`tools/mig-fixture/README.md\` 记下本轮实踩的两个坑：①用不存在的库名「探测」会**创建**
    空库（对象仓库为 0，后续 transaction 直接抛错）；②\`transaction().objectStore().put()\`
    返回的是 \`IDBRequest\`，给它挂 \`oncomplete\` **永不触发**（首版探针因此卡死）。

- **P4-A 迁移通道实现：导出器 + 迁移器**（2026-09-12，会话 70）
  - **导出器** \`ext/luzzy-migrate.html\`：纯 JS、**不启动 Vue**、**只读**（不写不删任何旧记录，
    用户可随时回退 v2.x）；整串序列化后按字符切片，每块 ≤180k 字符（Binder 事务上限 1 MB，
    中文按 UTF-8 占 3 字节 → 最坏约 540 KB）。
    **第一步是通道健康检查**：两库都读不到键就明确失败——\`setAllowFileAccessFromFileURLs\`
    一旦被收紧，症状是「读不到数据但不报错」，必须有断言把它变成可见的失败。
    另留 \`?chunk=8000\` 测试钩子：小样本导出天然只有一块，而块顺序与拼接完整性只有多块才走到。
  - **桥方法**：\`migrateStart / migrateChunk / migrateDone / migrateError\`（+ \`luzzy-bridge.js\` 封装，
    硬性规定 §5.4）；新增 Kotlin \`MigrationInbox\`（分块拼装 + **序号强校验** + sha256 + manifest）。
    序号必须连续是数据完整性判据：缺块拼出来的 JSON 有可能**恰好能解析**，然后静默少掉一段会话。
  - **迁移器**（\`data/legacy/\`，**纯 Kotlin、无 Android 依赖**）：键/作用域解析、两库多来源索引、
    线格式解析、\`LegacyMigrator\`。12 条坑逐条处理。
    设计要点：强类型只覆盖新界面马上要用的部分（角色/分支/消息/作用域），
    记忆/世界书/预设/正则/用量**整条原样搬运**（字段名逐字一致）——消费方字段需求要等 P4-B/P4-C 才定。
    幂等口径为「同一输入跑两次 \`MigratedData\` 逐字段相等」：缺 uuid 的角色按**内容哈希**补 id
    （随机 UUID 会让第二次迁移多出一张卡），顺序全部确定，\`ExtractedAsset\` 按字节内容比较。
  - **设备端实测**（模拟器 release 包）：导出 29 主库键 + 3 旧库键；单块 82,992 字符与
    11 块（\`?chunk=8000\`）拼接结果**内容完全一致**；导出物与测试夹具 **31/32 键逐字节相同**，
    唯一差异是夹具里被脱敏的两个密钥字段（长度保留）。
  - 单测 **+31**（\`LegacyMigratorTest\` 22 + \`MigrationInboxTest\` 9），全绿。

**修复**
- **上游式「整键优先」会丢掉整张角色卡**（迁移器实现时发现并修）：上游 \`dbGetWithLegacy\` 的
  「新键优先」是**整键替换**——新库里只要存在 \`characters\`，旧库（及同库旧前缀）的那一份就整体不看。
  对数组型记录那样做会丢数据。改为：**带稳定身份字段的记录（角色/人设，按 \`uuid\`）逐条合并**
  （同 uuid 取新库版本，旧库独有照常保留）；其余键仍走整键优先并留下「被遮蔽」的可见记录。
  夹具里同一份数据同时有 \`rp_hub_characters\`(3) / 主库 \`silly_tavern_characters\`(1) /
  旧库 \`silly_tavern_characters\`(1)，按整键优先只能留 3 张、丢 2 张——而它们都是用户的真实角色卡。
- **用户消息的「删除」绕过确认框**（由新 UI 测试基座上线即抓到）：\`ChatPage\` 里 AI 消息那一支已改为
  弹确认框，**用户消息那一支仍在直接删除** —— 同一功能两条路径行为不一致，纯逻辑单测查不出、
  人工走查也容易漏。修复后两条路径一致。
- **消息操作行触控目标不合规**：图标热区原为 32dp，低于 Android 48dp 下限
  （ui-ux-pro-max pro-rules）。改为 **48dp 热区 / 18dp 图标**（分支列表动作按钮同样 48dp）。
- **输入岛排版臃肿 + 模型溢出**（2026-09-12 用户反馈「输入框的组件前端排版设计太臃肿」→ **三轮才对**）：
  - 症状：5 个 48dp 图标铺满一行 + 行内实心模型胶囊 → 超出岛宽，胶囊被挤成两行并**压到图标行上**；
  - **第一次修偏**：把「太挤」误判成「组件太多」而**删掉了附件/预设/工作区三个入口**。用户当场纠正
    「我说的臃肿不是叫你删掉组件啊，我让你重新设计一下输入框的各部分内容」——删功能是改需求；
  - **定稿（用户指示「看一下 rikkahub 的实现方式」）**：照 rikkahub
    \`ui/components/ai/ChatInput.kt\`（AGPL-3.0，本项目已整体转 AGPL，形态复用许可兼容；
    **采用其架构与节拍，未逐行复制代码**）：① **输入框满宽独占一行、放在最上**，动作行在其下方；
    ② 动作行左簇 **\`weight(1f) + horizontalScroll\`**——按钮再多也不换行、不挤压固定件，
    **溢出问题从根上消失**；③ 模型改为**图标（新增 \`ic_lz_chip\`，Heroicons v1 \`chip\`）+ 短名（78dp 省略）**；
    ④ 容器内边距 8/4 + 行距 2dp 照用，按钮取 **44dp**（比 rikkahub 的 30dp 大：Android 下限 48dp、
    44dp 为 iOS 下限也是同类常见值；因左簇可滑，宽度放宽不再有代价）；
  - **验证**：模拟器 **150% 系统字号下不破版**（模型名继续省略、动作行与发送键完好、放不下则横滑）。
- **工具调用被写成文本时，原始协议标记漏进回复正文**（2026-09-12 真机实测发现）：DeepSeek 在
  正常 \`tool_calls\` 之外还会用自家 **DSML 文本标记**复述一遍工具调用，那段标记作为 \`content\`
  直接显示在气泡里（\`<| | DSML | | invoke …>\`）。新增 \`chat/ToolMarkupFilter.kt\` 在**引擎层**
  （不动 wire 逐字节转发纪律）过滤：删整块（只删标签会把工具参数留在正文）、普通文本零延迟
  逐字放行（不牺牲「1 字 = 1 次更新」）、未知情况宁可漏噪声也不吞正文。
- **往上滑回看较早气泡时「气泡突然弹出」（2026-09-12 用户实测反馈）**：LazyColumn 会把划出
  视口的气泡销毁、滚回来重建，而 Markdown 解析是**异步**的（\`produceState\` 初始为空）——
  重建那一帧只画出名牌（矮壳），解析完成后内容才出现，叠上外层 \`animateContentSize(200ms)\`
  就是肉眼可见的「弹出」。修法：**静态消息改同步解析 + 记忆化**（\`MarkdownMemo\` LRU 24，
  首次组合那一帧内容即就绪、滚动来回零成本），流式那一路仍走后台解析。
  同时补两处守卫：非空内容必产出块的**不变式测试**、列表条目的**稳定 key**
  （\`分支id#下标\`，防切换分支时条目状态泄漏）。

**新增（P3 部分 · 聊天页图标真功能，2026-09-12）**
- **气泡操作行全部接线**（此前只有占位图标）：
  - **复制**：系统剪贴板 + Snackbar 反馈（Scaffold 新增 \`snackbarHost\`）；
  - **重新生成**：**真实再跑一次请求**（以该消息之前的历史 + 上一条用户消息），结果作为**新候选追加**——
    数据模型改为候选列表 \`ChatMessage.Ai(results, index)\`，多结果切换器 \`‹ n/m ›\` 因此真正可用
    （真机实测：切换器出现 \`‹2/2›\` 且内容确为另一条新回复）；重新生成期间 live 面板**就地渲染**
    在该消息位置（原处重写，不凭空冒新气泡）；
  - **编辑**：就地编辑弹窗（Markdown 源码即所见；未修改时「保存」禁用）；
  - **更多**：菜单提供「复制 Markdown 源码 / 删除此消息 / 删除此消息及之后」；
  - 用户消息不显示「重新生成」（重生成是模型输出的动作）。
- **输入岛功能入口真实现**（并撤掉尚未实现的入口）：
  - **模型 chip** → 模型面板：真实 \`GET {base}/models\`（新增 \`chat/ModelCatalog.kt\`，解析宽容
    OpenAI/\`models\` 键/裸数组三种形态），选中即写回并持久化（真机实测返回供应商真实模型列表）；
  - **工具** → 工具面板：\`world_info_lookup\` 开关，**真实影响请求体**（\`TransportConfig.toolsEnabled\`）
    ——真机 A/B：关→0 工具事件、开→tool_start + 21 个参数分片 + 结果；
  - **世界书** → 只读面板：列出当前生效的真实条目（标题 + 关键词 + 正文），并明说编辑归 P4；
  - **附件 / 预设 / 工作区**：依赖 P5 图片管线、P4 数据层、P5 工作区特性，**入口保留**，
    点击给出如实说明（「附件：需要 P5 的图片管线，届时开放」）。

### v2.0.0 — 架构版：KV/prompt 前缀缓存最大化 × 原生 Kotlin 聊天传输后端（B 方案 · 薄切）（上游基线 RP-Hub 1.9.3）

> **状态：开发中（2026-09-11 起）。** 主计划 \`docs/PLAN-v2.0.md\`。本版是**架构版本**，两条主线：
> ① **KV / prompt 前缀缓存收益最大化**（聊天请求从「每轮整体重建」改为「纯追加」，
> 实测相邻两轮公共前缀 **0.9101 → 1.0000**）；
> ② **B 方案（薄切）**：WebView 保留为视图层与上下文装配层（**样式零改动**），
> 把**传输层**（HTTP + SSE 解帧 + 三协议线格式 + 工具增量拼装 + 取消/超时）搬到原生 Kotlin，
> 对齐 rikkahub / dsh 的架构分工。
> 本版**同时包含 v1.5.0 尚未发布的全部内容**（上游同步 1.9.3 + 「助手」模块移除），
> 故 v1.5.0 不再单独发布。

**新增**
- **原生 Kotlin 聊天传输后端（patch 050，薄切）**：新增 \`chat/\` 包（\`ChatPlan\` / \`ChatJobs\` /
  \`JsCall\` + \`chat/llm/\` 的三协议传输与线格式），复用可回收的历史实现并补齐 JVM 单测
  （含**真 socket** 的 SSE 测试与**线格式保真**测试）。桥接新增三个方法
  \`LuzzyBridge.chatStart / chatAbort / chatCapabilities\`，扩展层新增
  \`ext/luzzy-chat-native.js\`（桥接封装）与 \`ext/luzzy-chat-offload.js\`（把原生通道适配成与上游
  \`requestChatCompletion\` 同形的可卸载接口）。
  **卸载是可选且可降级的**：扩展层未加载 / 桥不可用 / 协议不支持 / 非流式 / 原生首帧即失败
  → 一律回落原有 JS 路径（该路径**保留不删不改**）。
- **KV / prompt 前缀缓存观测层（patch 047 配套）**：新增 \`ext/luzzy-prefix-guard.js\`，
  只读旁路包装 \`window.fetch\`，把「相邻两轮请求的公共前缀」与「服务端返回的缓存命中
  （\`cached_tokens\` / \`cache_read_input_tokens\` / \`cachedContentTokenCount\`）」变成可读指标。
- **Anthropic 显式缓存断点（patch 048）**：Anthropic Messages **没有** OpenAI 那样的自动前缀缓存，
  现在在 system 块末尾与最后一条消息的最后一个文本块上声明 \`cache_control:{type:'ephemeral'}\`。
- **结束原因（finish_reason）可见化（patch 052）**：此前三协议里**只有 OpenAI 路径**捕获了
  \`finish_reason\`，Anthropic（\`message_delta.stop_reason\`）与 Gemini（\`candidates[0].finishReason\`）
  **从未读取**，且该字段**从不落盘**、应用内**完全不可见** —— 于是「回复被截断」这件事**永远无法定性**。
  现在：三协议全部捕获并归一（Gemini 的 \`MAX_TOKENS\` → \`length\`）→ 写进用量记录 →
  新增 \`lastFinishReason\` 状态；一旦是 \`length\`/\`max_tokens\`，应用会**主动提示**
  「本轮因达到输出上限被截断，请检查模型的最大输出设置」。
  **这一条上线后立刻产生了实际价值**：真机上连续三条记录全部是 \`finish_reason = stop\`
  （模型自称正常收尾，非上限截断），从而把「谁截断了对话」这个问题从猜测变成了事实。

**优化**
- **聊天请求改为「纯追加」形态（patch 047）** —— 这是本版 KV 收益的核心。上游每轮会对
  **已经在上下文里的旧消息**做逐轮改写，而服务端的 KV/前缀缓存按最长公共前缀匹配，任何一处旧消息被
  改写都会让其**之后整段**缓存失效：
  - 停用「把检索提醒追加到最新一条 user 消息」：该句与 system 里 \`<active_tools>\` 内的提醒
    **同源同文本**（都取自 \`getActiveToolLatestUserReminder()\`），属纯冗余；而它使那条 user 消息在
    **下一轮**被重建时变形。
  - 把 \`<next_response>\` 从「最新 user 消息的尾巴」移到 **system 的最后一块**：该块内容
    **只由设置决定、与当轮用户输入无关**，语义上属于 system。提示词文本**一字未改**，每轮仍完整可见。
  - 实测：干净配置连续 6 轮，**每轮恰好 1 条已存在消息被改写 → 0 条**，
    相邻两轮公共前缀 **0.9101 → 1.0000**。
  - 新增回归门禁 \`tools/prefix-cache-test.cjs\`：断言「上一轮请求逐字节成为下一轮的前缀」，
    并逐条给出首个被改写消息的下标与**字段级 diff**；自带**负控**（把旧行为放回来必须判红）。

**修复**
- **Anthropic 协议此前完全不可用（patch 051，两个独立缺陷）**：
  - \`extractApiErrorMessage\` 无条件把 \`payload.message\` 当作错误详情读取，而 Anthropic
    \`message_start\` 事件**按规范就带顶层 \`message\` 对象** → **每个 Anthropic 响应的第一帧
    就被判成 \`API Error: 200 {…}\` 抛出**。现按「已知正常流式事件类型」提前放行
    （\`type:"error"\` 不在集合内，真正的错误事件仍照常上抛）。
  - 生成收尾**无条件**读 \`responseResult.toolCalls.length\`，而 Anthropic / Gemini 适配器
    **从不返回该键** → 任何一次成功回复都会抛 \`TypeError\`。已在两个适配器的**唯一公共出口**
    （\`withUsageMetrics\`）补齐返回契约，并在两处调用点加可选链兜底。
  - 验证（打桩端点端到端）：修复前 \`API Error: 200 {"id":"m1","usage":{…}}\`；
    修复后正常产出回复、零 JS 异常。
- **原生传输桥的终态事件会被静默丢弃**（v2.0 开发中自查发现并修复）：
  \`ext/luzzy-chat-native.js\` 的 \`onEvent\` 原先**先摘除处理器、再派发事件**，而派发恰好靠该处理器查表
  → \`done\` / \`error\` 终态事件取不到处理器而被丢弃，调用方的 Promise 永不 settle，
  真机表现就是本仓库已知的「一直生成中」故障类。已改为**先派发、后摘除**，
  并补 Node \`vm\` 沙箱回归门禁 \`app/src/test/js/chat-native.test.cjs\`（14/14 通过）锁死该顺序。
- **AGENTS §5.4 合规补齐**：三个新桥接方法 \`chatStart / chatAbort / chatCapabilities\` 已在
  \`ext/luzzy-bridge.js\` 内同步封装（存在性检测 + 中性值降级，不抛异常）。

**注意事项**
- **升级方式：同包名覆盖安装即可，数据完整保留**（IndexedDB / localStorage 结构**一个字段都没动**，
  仍由前端独占）。本版**不需要**任何重新配置，供应商 / 密钥 / 模型仍从原设置读取。
- **界面样式零改动**（用户明确要求）。
- **原生传输默认关闭**（\`ENABLED_BY_DEFAULT = false\`），但**桥接链路已在 Android 上验证通过**
  （见下「验证」）。开启方式：\`localStorage.setItem('luzzy_native_transport','1')\`；
  若原生出错会**本会话熔断**（后续请求全走 JS）且**首帧失败时静默回落**，用户无感。
  在用户真机（小米 / MIUI / Android 16）验证通过后，把该常量改为 \`true\` 即为默认开启。
- 未验证项（**如实标注**）：**用户真机（小米 25098PN5AC / Android 16）已做端到端测试，但结论未收敛**——
  见下方「验证」最后一条。**Gemini 协议在桌面打桩下不产出回复**（history 只到 user、无错误、
  无 JS 异常），倾向判定为**既有问题**（本版改动只会增加保护、不会抑制输出），但**未做
  revert 对照，故不下结论**，待真实 Gemini key 复核。
- 已知未完成：世界书 / 向量召回的「距尾 depth」插入点（\`at_depth\`）与「纯追加」存在**语义冲突**
  （「距尾 N 轮」本质上不是追加式），需要专门设计，本版**未改**。
- **许可安排变更（2026-09-12，v3.0 立项决定）**：因 v3.0「全面转 Compose」需以
  [rikkahub](https://github.com/rikkahub/rikkahub)（AGPL-3.0）为 Compose 设计参照并源码级复用，
  **自有代码自 v3.0 起转为 AGPL-3.0 分发**（新增 \`LICENSE-AGPL-3.0\`；上游
  \`assets/rphub/**\` 保持 CC BY-NC 4.0 原样署名，根 \`LICENSE\` 未删未改）。并存结构与
  一处已知冲突（CC BY-NC 非商用 vs AGPL 第 10 条）如实记录于 \`docs/LICENSING.md\` §3；
  \`HARD_REQUIREMENTS.md\` 合规红线与 README「许可证与合规」段已同步更新。
  本条**不改变** v2.0.0 的任何构建产物与代码行为（纯许可/文档动作）。
- **上游同步退役（2026-09-12，用户拍板）**：v3.0 转 Compose 后，上游 RP-Hub 同步
  **不再执行**，上游基线定格 **1.9.3**；\`tools/sync-upstream.ps1\` 等同步工具、
  \`tools/patches/\` 与 \`rp-hub-reference/\` **保留不删**（追溯依据），仅停止流程
  （\`AGENTS.md\` §4 头部退役声明 + \`HARD_REQUIREMENTS.md\` 规定 6 标注退役 + README 表述更新）。
  \`nsfw_rules\` 不可触碰约束（硬性规定 1）**永久有效**。本条同样**不改变** v2.0.0 构建产物。

**验证**
- **构建**：\`./gradlew :app:assembleRelease :app:testDebugUnitTest\` → BUILD SUCCESSFUL；
  单测 **14 类 / 207 用例 / 0 失败 / 0 错误**（其中 22 条走**真 socket**）；
  另有 17 条**线格式保真**断言（期望值为手写常量串，钉死键序 / 值 / 转义）。
  **恰好 1 个 APK**：18,495,002 B，\`versionCode 13\` / \`versionName 2.0.0\`，签名 \`CN=LuzzyRP\`。
- **门禁**：\`tools/verify-markers.ps1\` **151 PASS / 0 FAIL**（4 枚实体按上游纯净基线 \`4aef0bb\`
  重生成：前像 blob id 一致 + 逆向逐字节一致 + 端到端重放 9/9 \`[OK]\`）；
  \`tools/prefix-cache-test.cjs\` 8/8 PASS；既有三门禁（stream-render / model-list / page-handoff）全 PASS。
- **原生传输桥接链路（Android 15 / API 35 模拟器，实测）**：
  \`chatCapabilities()\` 返回 \`{available:true, protocols:[openai,anthropic,gemini]}\`；
  \`chatStart\` 返回 jobId 且**事件回传的 jobId 与之逐字节一致**；
  打桩端点下收到 **\`delta\` → \`usage\` → \`delta\` → \`done\`** 四型事件并拼回完整回复
  （服务端确认收到 \`POST /v1/chat/completions\`，\`model\`/\`stream\`/\`messages\` 均正确）；
  不可达端点下收到 \`error\` 终态事件并**静默回落** JS 路径、熔断生效、零 JS 异常。
- **用户真机（小米 25098PN5AC / Android 16，实测）**：
  - **升级数据守恒（逐项比对通过）**：覆盖安装 v2.0.0 前后完全一致 —— 2 个角色（Isryx /
    Aurelion Sol）、会话 3 条、18 预设、1 世界书、1 正则、\`RPHubDB\` 24 键同一批键族、
    44 项设置、供应商 STA1N、主题/模式/字体不变。**零数据迁移、零重配置**得到实证。
  - **桥接链路通过**：\`chatStart/chatAbort/chatCapabilities\` 可用；\`capabilities\` 报三协议；
    jobId 逐字节回显；不可达端点下 \`error\` 终态事件**在真机成功回传**（即「先派发后摘除」
    的修复在真机生效）；零 JS 异常。
  - **原生「成功流」结论未收敛（故默认仍关）**：开启开关后发一条真实请求（STA1N /
    \`[Cloud]DeepSeek-V4-Pro\`），\`prefixGuard.rounds === 0\` 证明请求**确实走了原生路径**
    （JS 路径必被观测层记录），助手消息**已创建但内容为空**（content 0 字 / reasoning 1 字），
    无错误上报、未触发回落、未熔断。**但不能据此归因于原生传输** —— 该用户**自己的历史消息**
    （i=2，由 v1.4.0 的 JS 路径产生）是**同一表征**（content 0 字 / reasoning 1379 字）。
    两条路径都出现该形态 ⇒ 更像既有现象；**未做同提示词的 A/B 对照，故不下结论**。
    该次测试插入的 2 条消息**已按应用自身流程删除并落盘核对复原**（内存 3 条 / IndexedDB 3 条），
    原生开关已清除。

### v1.5.0 — 开发中 · 文档定位澄清 × 发布纪律固化 × 同步上游 1.9.3（上游基线 RP-Hub 1.9.2 → 1.9.3）

> **状态：开发中（2026-09-09 起）。** 本版含三条工作流：① 文档与纪律更新（已完成）；
> ② **上游同步**（上游在 1.9.2 基线后又新增 4 个提交，本版合并，见下「同步」段）；
> ③ **「助手」原生 Agent —— 已于 2026-09-11 按用户指示彻底移除**（见下「移除」段；
> 原实施计划 \`docs/PLAN-v1.5.0-assistant.md\` 与交接档 \`docs/STATUS-v1.5.0-assistant.md\`
> 均转为历史存档）。**尚未发布**，最新可下载版本仍为 **v1.4.0**。

**新增**
- **「助手」原生页可行性调研（\`docs/RESEARCH-assistant-native-agent.md\`）**：调研菜单栏新增
  原生 Kotlin 页面并实现手机端 Agent 的可行性，参考 [rikkahub](https://github.com/rikkahub/rikkahub)
  与 [rikkahub-agent](https://github.com/ExTV/rikkahub-agent)。结论：**可行**，推荐「同 Activity
  原生覆盖层 + 扩展层 DOM 注入侧栏入口 + 桥接复用 Web 端供应商配置」方案；已给出四阶段路线、
  依赖选型（Compose/Room/OkHttp/kotlinx-serialization 本地缓存齐备）、风险与红线清单。
  **调研明确不含 UI 视觉设计**（按硬性规定 9，进入界面设计阶段前须先完整阅读 4 项设计 SKILL
  并走三方向硬门）。
- **「助手」Agent 实施级计划（\`docs/PLAN-v1.5.0-assistant.md\`）**：经用户澄清后定稿的实施方案——
  九项需求（多助手 / 会话历史 / 记忆嵌入 / Skill / MCP / 独立工作区 / 双模式终端 /
  提示词与模型设置 / 渲染）+ 追加工具（澄清提问 / 系统时间 / 程序编译 / 日历读写 / 记忆工具）
  + Room 10 表数据模型 + U/P0-P4 阶段路线与验收标准 + R1-R13 风险清单。**本文只定义信息架构，
  不含视觉设计**。
- **「助手」原生页 P0 骨架（原生 Compose，方向 A · 卷宗）**：
  - **设计门（硬性规定 9 全流程）**：完整阅读 4 项设计 SKILL → 产出 **3 个差异化方向**
    （A 卷宗 / B 工作台 / C 场记，各含 1080×2400 亮暗双截图与 IA·栅格·密度·动效规格，
    见 \`docs/design/assistant-v1/\`）→ **用户选定 A · 卷宗**（记录见
    \`docs/design/direction-approved-assistant.md\`）→ 写入 \`DESIGN.md\`「助手原生页」章。
  - **宿主接线**：\`MainActivity\` 懒创建 \`ComposeView\` 覆盖层（WebView 保持存活）+
    返回键三级优先级（抽屉 → 二级页 → 退出助手）；桥接新增 \`openAssistant\` /
    \`isAssistantVisible\` / \`setAssistantConfig\` / \`getAssistantConfig\` / \`setAssistantThemeMode\`。
  - **侧栏入口**：\`assets/ext/luzzy-assistant.js\` DOM 注入（零上游改动）+ \`luzzy-ext.js\` 动态加载；
    桌面实测按钮位于「外观」之上、点击降级提示正常、零 JS 异常。
  - **界面骨架**：会话列表（家）/ 会话页 / 记忆页 / 全部助手管理页 + 右侧抽屉六项
    （技能·MCP·工作区·终端·设置给「规划中」占位）；组件含头像条、会话行、气泡、思考卡、
    工具卡、步骤组、输入岛、审批弹窗、记忆卡、检索框。
  - **主题与字体**：\`LuzzyAssistantTheme\` 落地 DESIGN.md 全部 token（亮/暗双模式、圆角、动效常量）；
    字体按用户选定「全量 1:1 复刻」——\`tools/assistant-fonts.py\` 把上游 woff2 转为 8 枚 TTF
    （Lora + AlibabaSans + Alibaba PuHuiTi 3.0 三字重，约 21.2MB），正文/UI 用 PuHuiTi、
    display 用 Lora。
  - **契约与安全内核（纯 Kotlin，43 项单测全绿）**：\`Tool\` / \`ToolResult\` / \`ToolContext\` /
    \`AgentEvent\` / \`LlmTransport\` / JSON Schema DSL / 工具调用分片累加器 / 宽松 JSON 解析；
    **HARDLINE 危险命令无条件拦截**（9 类模式）、**SSRF 防护**（DNS 解析层拒绝私网/回环/
    链路本地/保留地址 + IPv4 映射 IPv6）、**三层审批门**（每工具开关 / 逐调用审批 /
    本会话始终允许）。
  - **数据层**：Room 十表（\`assistant\` / \`conversation\` / \`message\` / \`memory\` / \`skill\` /
    \`skill_binding\` / \`mcp_server\` / \`mcp_binding\` / \`tool_audit\` + FTS4 检索镜像）+ DataStore 偏好
    + \`WorkspaceManager\`（每助手独立工作区、路径越界/符号链接穿越拒绝、配额 2GB/64MB）。
  - **构建接入**：AGP 9.2.1 内置 Kotlin + Compose 编译器插件 + KSP/Room + kotlinx-serialization
    全部打通（计划 R2 风险解除），\`assembleDebug\` 通过。
- **「助手」P1 最小可用 Agent（已闭环）**：
  - **领域核心**：\`AgentLoop\`（严格 §5.2 事件顺序：TurnStarted → 流式推理/正文/用量 →
    工具逐个审批执行 → 结果回灌 → 下一轮；工具异常统一转 Error 不中断；\`ask_user\` 暂停本轮；
    取消/超预算给出明确 reason）+ \`BudgetGuard\`（轮次 24 / 工具超时 120s / 总时长 30min /
    token 预算）+ \`ContextBuilder\`（六变量替换 + 技能注入 + 记忆块 + 工具约定 + 超限压缩）
    + \`OpenAiTransport\`（SSE 分帧、delta 解析、重试 2 次指数退避、\`extraBody\` 禁覆盖
    messages/tools/stream/model、密钥只进 Authorization 头且错误体不回显）。
  - **内置工具 18 个**：\`ask_user\` / \`get_time\` / \`get_device_info\` / \`clipboard_read·write\` /
    \`workspace_list·read·write·patch·delete·move·mkdir\` / \`memory_write·search·update·delete·list\` /
    \`web_fetch\` / \`terminal_run\` / \`run_code\`。
  - **记忆引擎**：\`VectorMath\`（float32 小端 + 余弦）、\`Retriever\`（full/embed/hybrid，
    TopK 8 / 阈值 0.35 / 最近 5 去重，失败自动降级全文）、\`EmbeddingClient\`（OpenAI 兼容
    \`/embeddings\`，批 ≤32、重试 1 次、密钥不落日志）、\`RoomMemoryStore\`（含后台补嵌与 LIKE 兜底）。
  - **运行时**：\`AssistantRuntime\` 装配（工具注册 + 配置解析 + 工作区适配）、Android 端口
    （设备信息 / 剪贴板 / 时钟）、\`GlobalShellRunner\`（宿主 sh、双层 HARDLINE、200KB 输出上限
    + 溢出落盘、**非阻塞排空修正超时失效**）。
  - **UI 闭环**：会话页状态机（AgentEvent → 思考卡/工具卡/步骤组；**审批弹窗**「允许一次 /
    本会话始终允许 / 拒绝」；**澄清提问卡**；停止键协作式取消；错误条）——UI ↔ ViewModel ↔
    AgentLoop ↔ 传输/工具全线打通。
  - **验证**：全仓 **213 项单测 / 0 失败**（领域 95 + 安全契约 71 + 数据 42 + 运行时 5），
    \`assembleDebug\` 通过。- **「助手」P2 持久化与真实数据接线（进行中）**：
  - \`AssistantRepository\`：助手/会话/消息用例接口——首启自动建默认助手；会话增删改归档；
    消息一律走 \`MessageDao\` 的 \`*Indexed\` 事务方法（FTS 索引随写）；首条用户消息自动成标题；
    **中文检索双通道**（正文 FTS bigram → 无命中回落 LIKE；标题走 LIKE）；会话导出 MD / JSON；
    索引幂等重建。
  - **重启恢复**：会话页从 Room 恢复历史（P1 验收项「会话重启后完整恢复」达成）；
    用户消息先落库再跑循环，助手消息收尾落库（含思考内容 / 状态 / token 用量）。
  - **真实数据接线**：会话列表（\`AssistantListViewModel\`：日期分组 + 相对时间）与记忆页
    （\`MemoryViewModel\`：类型/时间/相似度 + 模式条取自助手设置）均改为读 Room，不再用示例数据。- **「助手」P2b 技能与 MCP（已完成）**：
  - **技能**：\`SkillLoader\` 解析 YAML 子集 front-matter（name / description / tools，未知键
    向前兼容、CRLF 安全，解析失败**拒绝导入并明示原因**）；内置 3 个技能（周报生成 / 资料整理 /
    代码审查）首启幂等导入；\`SkillRepository\` 支持文件导入（同名「用户导入 > 内置」合并）、
    全局启用 + 助手绑定启用，装配 \`SkillDocument\` 注入系统提示词；技能页双开关 UI。
  - **MCP**：\`McpConfigParser\` 自动识别 \`mcpServers\` 映射 / 单对象 / 数组三种 JSON 形态并推断
    传输（type > url 含 \`/sse\` > command），提取 \`\${ENV_VAR}\` 占位符；\`McpClient\` 走 JSON-RPC 2.0
    over **Streamable HTTP / 旧式 SSE**（initialize → tools/list → tools/call，响应兼容纯 JSON /
    SSE 帧 / 批，错误消息不含请求头）；\`McpToolAdapter\` 命名空间 \`mcp__<serverId>__<toolName>\`、
    分级 **T2**（默认关闭 + 逐调用审批）、\`inputSchema\` 原样透传；\`McpRepository\` 负责导入落库、
    可达性预览、连接并注册工具（幂等注销）、连接状态入库，stdio 明确提示「需沙盒（§9.3）」；
    MCP 页支持粘贴 JSON 导入 / 全局开关（开启即连接）/ 重连 / 删除。
  - **验证**：全仓 **239 项单测 / 0 失败**（新增 26：技能解析 11 + MCP 15）。- **「助手」P3 工作区与终端（UI 部分完成）**：
  - **工作区页**：列 \`files/\` 子树 + 面包屑回上级 + 文件预览（截断 8KB）+ 删除 + 配额用量；
    路径越界异常转可读提示（不崩、不静默）。
  - **终端页**：宿主模式（\`/system/bin/sh -c\`，工作目录 = 该助手工作区 \`files/\`），命令先过
    HARDLINE 无条件拦截、再由执行器二次拦截；200KB 输出上限 + 溢出落盘提示；等宽滚动回看、
    清屏、退出码显示。
  - **未完成**：proot 沙盒（需内置 Alpine rootfs，体积/来源待决策）；沙盒模式未接入时终端页
    明确标注「宿主（App 权限）」，**不伪装沙盒**。  - **proot 沙盒已落地**（用户 2026-09-09 拍板方案 A：随包内置）：\`assets/assistant/sandbox/\`
    内置 proot 5.1.107.92 + 依赖库（libtalloc / libandroid-shmem）+ Alpine 3.20.3 minirootfs
    （共 4.1MB）；首次使用释放到应用私有目录并 \`chmod +x\`，\`terminal_run\` / \`run_code\` 自动
    切到沙盒（可 \`apk add python3\` / \`nodejs\`）；终端页新增**宿主 / 沙盒模式切换**与释放进度提示。
    **GPL 合规**：proot 为 GPL-2.0-or-later，以未修改二进制再分发，\`SOURCES.md\` 记录上游仓库、
    发行包与书面索取三条源码获取途径，\`LICENSE-proot-GPL-2.0.txt\` 随包分发。- **「助手」设置页（完成）**：提示词与模型（模型列表来自 Web 端只读镜像，无配置时退化为手填
  \`providerId::模型名\`）/ 参数（temperature / top_p / max_tokens + 记忆模式三选）/ 请求体扩展
  JSON（保存前校验，非法拒绝）/ **预览最终请求**（密钥脱敏）。至此技能 / MCP / 工作区 / 终端 /
  设置五页全部接入真实实现，占位组件已删除。- **「助手」P4 增强（进行中）**：
  - **三协议齐备**：新增 **Anthropic Messages**（\`system\` 顶层 / \`tool_use\` 与 \`tool_result\` block /
    \`thinking_delta\` / \`x-api-key\` + \`anthropic-version\`）与 **Gemini \`streamGenerateContent\`**
    （\`systemInstruction\` / \`functionCall\` 与 \`functionResponse\` / \`usageMetadata\` / \`x-goog-api-key\`
    头，密钥不入 URL）；\`RoutingTransport\` 按供应商协议分派，未知协议回退 OpenAI 兼容。
  - **日历工具**：\`calendar_read\` / \`calendar_write\`（T2 档：默认关闭 + 逐调用审批）——
    \`CalendarContract\` 查询/插入/更新/删除 + 提醒；时间支持 ISO-8601 / 日期 / 空格分隔，
    **解析失败明确报错不猜测**；权限未授予时提示「请去系统设置授权」。
  - **工具审计**：\`AuditSink\` 端口 + \`tool_audit\` 落库 + 设置页审计面板（最近 50 条 + 清空）；
    **参数只记键名与长度**（不回显值，防隐私/密钥泄漏），结果预览截断 400 字。
  - **验证**：全仓 **278 项单测 / 0 失败**（新增 39：Anthropic 13 + Gemini 11 + 日历 10 + 审计 5）。- **「助手」P3/P4 收尾**：
  - **proot 沙盒**（用户拍板方案 A：随包内置，4.1MB）：proot 5.1.107.92 + 依赖库 +
    Alpine 3.20.3 minirootfs；首次使用释放、\`terminal_run\` / \`run_code\` 自动切沙盒、
    终端页宿主/沙盒切换；GPL 合规文件随包分发（\`sandbox/SOURCES.md\`）。
  - **stdio MCP**：\`StdioTransport\` 抽象 + 进程实现 + \`McpStdioClient\`（换行分隔 JSON-RPC）；
    \`McpToolAdapter\` 改为传输无关（http / stdio 两个工厂）；沙盒未就绪时明确提示。
  - **上下文压缩**：\`LlmSummarizer\` 用同一模型生成旧轮摘要（不带工具 / 温度 0 / 30s 超时 /
    失败退化截断）。
  - **验证**：全仓 **299 项单测 / 0 失败**。- **「助手」P4 补齐 + 发版自检**：
  - **\`web_search\`**：可插拔提供方（DuckDuckGo 无 Key 默认 / SearXNG 自填实例，均走 SSRF 防护）；
    设置页可选提供方；未配置时明确提示。需 API Key 的提供方（Tavily/Brave/Exa）待加密存储接入。
  - **\`send_to_rp_chat\`**（预留项，T2 默认关）：未接线时返回「未启用」，不假装可用。
  - **技能 URL 导入**：协议白名单 + SSRF 防护 + 256KB 上限，技能页新增链接导入弹窗。
  - **release 构建修复**：Compose mapping 生产者类路径版本钉到项目 Kotlin 版本
    （AGP 内置 Kotlin 与项目版本不一致会去解析未缓存版本，离线必失败）。
  - **APK 资产名修复**：AGP 会把 \`rootfs.tar.gz\` 解压成 \`rootfs.tar\`（去掉 \`.gz\`）——
    运行时改为两种名字都试 + magic bytes 判断，否则真机沙盒装不上。
  - **发版自检**：单 APK（\`app-release.apk\` 42.9MB）+ 签名指纹与 keystore 逐位一致
    （CN=LuzzyRP / SHA-256 \`ed78235d…ffb1\`）；全仓 **318 项单测 / 0 失败**。- **「助手」密钥加密存储与 Key 类搜索（完成）**：
  - \`KeystoreSecretStore\`：**AndroidKeyStore AES-256-GCM 主密钥**加密每条密钥，密文存
    应用私有目录（不引第三方依赖）；读取不缓存、值不进日志、写入临时文件防半写；
    满足 PLAN §13.2「密钥不进 DataStore/Room/日志」。
  - 搜索提供方补齐 **Tavily**（Key 在请求体）与 **Brave**（\`X-Subscription-Token\` 头），
    Key 只从加密存储取；设置页新增两个 Key 输入框（保存后不回显）。
  - **验证**：全仓 **323 项单测 / 0 失败**；真机（小米 25098PN5AC / Android 16）debug 包
    \`install -r\` 成功、冷启动「开卷」开屏正常、logcat 无崩溃。- **「助手」真机反馈修复（用户实测：助手页帧率明显低于其他页）**：
  - **阻塞性缺陷**：\`AssistantRuntime\` 取 DataStore 当前值用了 \`flow.collect{}\`——DataStore 是
    **无限流**，collect 永不返回，会**让首轮对话直接挂死**。已改为 \`flow.first()\`。
  - **帧率**：助手覆盖层显示时**暂停 WebView**（\`INVISIBLE\` + \`onPause\` + \`pauseTimers\`），
    不再与 Compose 争抢合成器；关闭时原样恢复（不销毁、状态不丢）。
  - **重组开销**：UI 模型与状态类加 \`@Immutable\`（Compose 可跳过未变项）；主题的
    Typography/Shapes 改为 \`remember\`（原先每次重组新建对象会让整棵子树失效）；
    ViewModel 工厂 \`remember\` 复用。
  - **流式节流**：文本/思考增量按 **100ms** 合并刷新（PLAN §11.2），工具状态跃迁与收尾
    强制刷新——此前每个 token 都重建消息列表并触发全列表重组。
- **「助手」首页版式与菜单栏归属（用户 2026-09-09 两次改稿）**：
  - **首页 = LuzzyRP 聊天页版式**：\`h-28\` 深色渐隐顶栏（汉堡 + 头像 + 名称 + chevron）→ 消息流
    （\`px-2 pt-14 space-y-12\`）→ 底部输入岛；**唯一差异点**是顶栏右上角由「清空聊天」改为
    **助手设置按钮**（用户指定）。
  - **菜单栏归 LuzzyRP**：助手页左上角汉堡 = **退出助手并打开 LuzzyRP 原侧栏**；助手不再有
    自己的抽屉。侧栏「助手」下新增子项组：**会话 / 记忆 / 技能 / MCP / 工作区 / 终端 / 设置**
    （DOM 注入，未改上游文件），点击按路由打开助手对应页面。
  - 会话列表退化为二级页（\`ConversationsScreen\`，含助手切换 + 新建），由 chevron 或侧栏「会话」进入。
  - 桥接新增 \`openAssistantAt(route)\` / \`openRpSidebar()\`（契约见 PLAN §14）。
- **「助手」管理页设计一致性重构（用户 P0 反馈）**：
  - **根因**：\`DESIGN.md\` 此前只规范了聊天页组件，管理页零规范 → 实现层临场发明组件
    （违反硬性规定 9 第 3 步）。
  - **补设计契约**：\`DESIGN.md\` 新增「管理页组件规范」——15 个组件逐项标注上游 Tailwind 类与
    像素值（\`.settings-page-header\` / \`.settings-toggle\` 44×24 / \`.settings-collapse\` 0.36s /
    \`.settings-section-heading\` / \`px-3 py-1.5\` 按钮 / 24dp 线性图标），换算基线 1 CSS px = 1 dp。
  - **新建组件库** \`ui/component/ledger/\`：\`Ledger\`（尺寸/字级 token）、\`LedgerIcons\`（19 枚图标，
    路径逐条取自上游 SVG）、\`LedgerPageHeader\` / \`LedgerCard\` / \`LedgerCollapseCard\` /
    \`LedgerToggle\`（自绘 44×24，替换 Material3 Switch）/ \`LedgerButton\` / \`LedgerIconButton\` /
    \`LedgerTextField\` / \`LedgerSearchField\` / \`LedgerListRow\` / \`LedgerEmptyState\` /
    \`LedgerStatusPill\` / \`LedgerSegmented\`。
  - **六个管理页 + 会话页 + 助手管理页全部改用该库**；删除临场组件（\`PageHeader\` / \`SectionCard\` /
    \`Field\` / \`ToggleRow\` / \`EmptyState\` / \`SearchField\` / \`MemoryCard\` / \`ConversationRow\`）。
  - **新增 token \`card\`**：上游卡片是 \`bg-white\`（暗色 #201E1B），此前误用 \`surface-card\`
    （实为上游边框色 #EFE9DE）当填充，导致卡片整体深一档。
  - **规格锁定测试**：\`LedgerTokensTest\` 8 项断言（开关 44×24 / 按钮 32 / 页面头 48 / 圆角 8·12·16 /
    图标 24 与 stroke 2 / 折叠 360ms），改规格必须先改 DESIGN.md。
  - **验证**：全仓 **331 项单测 / 0 失败**；\`assembleDebug\` 通过。**真机视觉比对待设备重新连接**。  - **图标渲染缺陷修复（用户真机指出「圆形图标都是半圆状」）**：上游 SVG 用紧凑弧线标志位
    （如 \`a3 3 0 11-6 0\`），Compose 的 \`addPathNodes\` 会把 \`11\` 读成一个数 → 圆心被画成半圆、
    齿轮变花形。改为**直接复用原项目图标**：把上游 SVG 的 \`d\` 落成 \`res/drawable/ic_lz_*.xml\`
    （Android VectorDrawable，系统 SVG 解析器），并**显式分隔弧线标志位**，以 \`painterResource\` 渲染。
    20 枚图标全部修正（真机截图确认齿轮/放大镜/信息圆/滑块均正常）。  - **侧栏「助手」改为可折叠组（用户 2026-09-09 指定）**：与「在线」「高级」同款结构
    （\`.advanced-nav\` + \`advanced-nav-trigger\` + chevron 旋转 + \`grid-template-rows\` 0fr↔1fr 0.32s）；
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
  - **模型编辑二级弹窗**：复用上游 \`modal-shell\`（\`overlay-class="z-[70]"\` 叠于供应商编辑器 \`z-[60]\` 之上，
    面板 \`max-w-md max-h-[85vh]\`），字段与原内联表单**逐项一致**（模型 ID / 显示 ID / 上下文长度 /
    最大输出长度 / 输入模态 / 模型类型 / 模型级自定义请求体），含 ID 预设命中提示与「撤销」。
  - **编辑完单个模型即保持**：编辑在**草稿副本**上进行，点「确定」才原位写回模型列表（\`splice\` 替换或
    \`push\`），**取消不影响原条目**；逐条累积编辑不再互相覆盖。删除的正是弹窗内条目时自动收殓弹窗。
  - **零上游裸改**：全部落 patch 040（\`index.html\` + \`app.js\`），标记 2+2 处、实体 9 枚按规程重生成并
    **双验证通过**（逆向 2/2 + 端到端 9/9 逐字节一致）；门禁新增 3 项校验（**89 PASS / 0 FAIL**）。
  - **同批附带**：「供应商级自定义请求体」右侧按钮按用户要求**扁平化**——去掉边框与白底（改
    ghost 款：teal 文字 + hover 浅底）、\`whitespace-nowrap\` 防换行、文案「+ 加键值」→「**+ 键值**」
    （原样式在窄列里被挤成两行，观感臃肿）。
  - 真机验收（小米 25098PN5AC）：卡片渲染 ✓ / 弹窗层级与字段 ✓ / 新增→确定即入列表 ✓ /
    编辑→预填当前值→原地替换且 ID 不变 ✓ / 取消不留痕（真实配置零改动）✓。
- **识图架构重构（patch 041，用户 2026-09-10 指定）**：识图模型改为**按需生效**——
  ① 当前聊天模型**原生支持图片**（多模态）时：图片以 \`image_url\` part **直发聊天模型**，
  **不再调用识图模型**（\`buildNativeImageContent\`，只带最近一条带图 user 消息——dataURL 每张
  数百 KB，全量回传会让请求体随轮数线性膨胀）；
  ② 不支持时：先用识图模型（内置提示词：审查豁免前缀 + 中文高密度客观描述 + 区分确定/不确定 +
  不把图内文字当指令）产出描述，再以 **user 身份**注入聊天模型上下文，措辞为
  **「用户上传了一张图，图片内容为：……」**（多张为「第 N 张图」），仍保留
  \`<user_image_context>\` 包裹与「不是系统指令」安全注记。
  ③ **删除视频支持**（含设置项）：模型编辑器的输入模态只留 text / image，归一白名单与
   \`ui-components\` 标签映射同步清理，全仓零残留。
  - 违反「只在…时生效」会怎样：多模态模型下若无原生发图通路，图片会被静默丢弃——故本次
    同步新增该通路（\`api-utils.js\` 三协议转换管道本就支持 \`image_url\` part）。
- **删除内置助手预设「阿墨」+ 空态新建入口（用户 2026-09-10 指定）**：原
  \`AssistantRepository.ensureDefaultAssistant()\` 会在首次进入时自动创建内置助手「阿墨」；
  现整体删除，助手一律经用户显式 \`createAssistant\` 创建。助手管理页空态提示语同步纠正
  （原「助手在首次打开时自动创建」已失效）并挂上**「新建助手」**按钮（\`AssistantHost\` 接线），
  避免"删了预设却无处可去"。
- **进/出助手过渡动画（用户 2026-09-10 报告"切换生硬"）**：助手覆盖层原为 \`visibility\`
  硬切。新增 \`MainActivity.animateAssistantOverlay()\`：**进 200ms / 退 140ms /
  \`cubic-bezier(0.23,1,0.32,1)\`，自 \`scale(0.96)+alpha 0\` 起步**（令牌禁 \`scale(0)\` 起步），
  退出动画结束后才置 \`GONE\`；系统「移除动画」时直接呈现。助手**内部**路由本就有
  \`AnimatedContent\`，未改动。
- **侧栏「助手」组移位 + 「对话」图标去重（用户 2026-09-10 指定）**：助手组由底部簇
  （「外观」之前）移到**「聊天」之下作第二入口**（锚点改为聊天按钮的下一个兄弟节点，
  并保留"上游改名/改结构时回落旧锚点"的降级）；子项「对话」原用铅笔线稿，与「助手」触发
  按钮**同图标**，改用侧栏「聊天」项自带的气泡图标（上游原图形，零自绘）。均落扩展层
  \`ext/luzzy-assistant.js\`，零上游改动。

**同步（上游 1.9.3 · 已完成）**
- **上游新版本 RP-Hub 1.9.3 已合并**（公告 id \`10207\`，更新时间 09/08 15:30；基线 \`d2f2625\` → \`4aef0bb\`，
  4 个提交 / 5 文件 **+297 −351**）。上游自报新功能：角色卡工坊与万相广场**一键导入** ·
  工坊**抗截断模式** · **Diff 匹配与智能修改成功率大幅优化** · **角色卡管理页全面焕新** ·
  开屏动画优化 · 剧情 UI 面板出现时机优化 · 修复沉浸模式宽度异常。
- **逐文件改动**：\`index.html\` +1（\`add-character-modal\` 新增 \`@generate\` 跳生成器）；
  \`ui-components.js\` +20/−9（\`AddCharacterModal\` 新增「生成角色卡」入口）；\`app.js\` +84/−18
  （**万相广场一键导入**消息桥 \`RPH_FORUM_*\` + \`selectCharacter\`/\`importCharacterData\` 签名改选项对象）；
  \`character/index.html\` +170/−285（**工坊页 Diff 机制重构**：文本块解析 → 原生 \`edit_character_card\`
  工具调用）；\`built-in-content.js\` +40/−39（预设文案调整 + 公告换 1.9.3）。
- **合并方式（三方合并，非覆盖重放）**：以 1.9.3 纯净文件为底、1.9.2 纯净文件为公共祖先、
  二创工作树为另一方做 \`git merge-file\`——\`index.html\` / \`ui-components.js\` / \`character/index.html\`
  **零冲突**；\`app.js\` **1 处冲突**（上游新增 \`squareImportPending\` + \`getSquareFrame()\` 与二创
  注释行重叠）取上游侧解决。**交叉验证**：可重放的 7 枚实体重放结果与三方合并结果 **LF 归一逐字节一致**
  （character/index.html、ui-components.js 实测相等），证明合并无信息丢失。
- **顺带修复的存量缺陷**：1.9.2 合并（会话 25）时误删 \`let workshopImportPending = false;\`
  （退化为隐式全局），本次合并随冲突解决恢复该声明。
- **签名变更核对（U5，运行期才炸的隐患）**：上游 \`importCharacterData(raw, avatar, {askImageGeneration, activate})\`
  与 \`selectCharacter(index, isNewImport, {silent})\` 改选项对象——全局核对 6 处调用点，
  无一处传旧式布尔第三参，**无需改动**。
- **实体 9 枚按修正规程全部重生成**（计划 §18.6）：以「上游 1.9.3 纯净基线 + 合并结果」为对、
  LF 归一生成 → **逆向 9/9**（纯净基线逐枚 \`git apply\` → 与工作树 LF 归一逐字节一致）+
  **端到端 9/9**（纯净基线全量 → \`apply-patches.ps1\` 实跑 → 9 枚全 \`[OK]\` 且结果与工作树一致）。
- **工具缺陷修复**：\`apply-patches.ps1\` 的基线 commit **参数化**（\`-BaselineCommit\` > 指纹表头
  \`(commit <sha>)\` > \`FETCH_HEAD\`，替代硬编码 \`d2f2625\`），并改用 cmd 重定向取原始字节以避免
  尾部空行导致的哈希偏差；\`sync-upstream.ps1\` 指纹表头改为携带 \`(commit <sha>)\` 与上游版本号，
  文件清单补 \`character/\`、\`novel/\`。
- **硬编码基线点更新**：\`LuzzyBridge.UPSTREAM_VERSION\` 1.9.0 → **1.9.3**；README 二创声明基线
  与 Upstream 徽章 → 1.9.3；\`tools/upstream-fingerprints.txt\` 全表 13 项以 \`4aef0bb\` 重算。
- **行尾一致性修正**：\`styles.css\` / \`novel/index.html\` 工作树由混合行尾归一为 CRLF，与
  \`core.autocrlf=true\` 检出态一致（git 视角零差异），使 R1/R2 指纹在 clone / checkout 后仍然成立。
- **验证结果**：\`node --check\` 全 JS PASS（rphub 9 + ext 4）；\`verify-markers.ps1\`
  **82 PASS / 0 FAIL**；未 patch 的 4 文件（\`built-in-content.js\` / \`styles.css\` / \`presence.js\` /
  \`update-check.js\`）与上游 1.9.3 **LF 归一同构**，\`nsfw_rules\` 块（1588 字节）**逐字节一致**；
  桌面冒烟（headless Chrome + CDP）**零 JS 异常**，工坊页正常挂载并含 \`edit_character_card\` 工具；
  \`assembleDebug\` 构建通过。
- **回归专项（计划 §18.8 十项）**：①工坊页 JS 执行 ✓ ②Diff 工具调用（静态核验通过，需带 tool 的
  模型实测）③抗截断（同上）④广场一键导入（iframe 与 \`RPH_FORUM_*\` 桥就位，需联网实测）
  ⑤「生成角色卡」入口 ✓（实测点击跳转生成器视图）⑥角色卡管理页焕新 ✓ ⑦开屏 ✓ / 面板时机与
  沉浸宽度需真机目测 ⑧既有二创回归 ✓（冒烟全过）⑨数据兼容 ✓（1.9.3 未触碰 localStorage /
  IndexedDB 结构，零 \`setItem\`/\`removeItem\` 改动）⑩断网 ✓（无新增 CDN 引用）。
- **执行计划**：\`docs/PLAN-v1.5.0-assistant.md\` §18（U1-U12 清单 + 回归专项十项 + 排期约束）。
  按用户指示：**先同步、再做助手，最后一次性发版**（不拆版）。

**优化**
- **二创定位澄清（README）**：二创声明由「仅优化前端、后端完全未动」改为明确三段立场——
  **遵循上游开源协议**（CC BY-NC 4.0 + 上游 LICENSE 保留）、**保持同步上游更新**（覆盖 +
  登记 patch 重放）、**但本项目有自己的功能路线，会修改前端或后端 / 原生侧代码**；同步策略、
  架构分层表、许可证义务表同步补齐（「原样保留」仅指 \`nsfw_rules\` 一条）。
- **发布纪律固化（AGENTS §3.4/§6.3/§7/§9 + README）**：①**只构建/发布一个 APK**
  （\`app-release.apk\`，ABI 拆分保持关闭、禁止恢复）；②**每次发布必须保持同一应用签名**
  （沿用 \`keystore/luzzy-release.keystore\`，发布前 \`apksigner verify --print-certs\` 核对
  指纹与上一版一致，\`keystore.properties\` 缺失会回退 debug 签名 → 不得发布）。
- **文档地图校正（AGENTS §1.5）**：补 \`docs/PLAN-v1.4.0.md\`、调研文档、\`docs/RELEASE-KEY.md\`
  条目；README「开发者须知」的最近 PLAN 指向同步为 v1.4.0。
- **\`tools/gen-changelog.mjs\` 徽章状态分支（工具层）**：CHANGELOG 顶部章节状态为「开发中」时
  生成琥珀色 \`开发中·未发布\` 徽章，避免开发中版本被无条件标成绿色「正式版·可游玩」。
- **真机体验包改用 release 构建（用户指示，2026-09-10）**：用户日常真机由 debug 包改为
  **release 签名包**（\`com.luzzymeow.luzzyrp\`，与最终分发件同物），此后每个版本由用户
  先体验该 APK 作**最后一道人工真机测试**；原 debug 包已从设备卸载（其数据随之清空）。
  配套：\`WebViewSetup\` **显式开启 WebView 内容调试**（\`setWebContentsDebuggingEnabled(true)\`，
  release 同样生效）——release 不可调试会使 CDP 排查通道整体失效（帧率/布局/脚本耗时无从测量）；
  代价为可连 adb 的电脑可检查页面内容，本应用仅侧载分发，接受该代价。另：\`adb\` 传
  \`MSYS_NO_PATHCONV=1\` 前缀是 Git Bash 用法，**PowerShell 下不需要也不生效**（会话 48 踩坑）。
- **侧栏折叠组动效对齐设计令牌（120Hz 掉帧窗口减半）**：用户报告「侧边菜单栏多级抽屉菜单项
  打开时动画不流畅、帧数没达到手机刷新率」。真机量化（小米 25098PN5AC / 120Hz / CDP 帧事件
  追踪 + \`dumpsys gfxinfo\`）：**主线程不是瓶颈**（每帧 layout 0.27ms + recalc 0.75ms +
  paint 0.6ms，rAF 稳定 8.3ms，\`BeginFrame\` 间隔 P50 8.32ms）；瓶颈是 **GPU 栅格约 5ms/帧
  > 120Hz 的 8.33ms 预算**（\`gfxinfo\` GPU 中位 5ms、95 分位 10~14ms），超额帧落到下一 vsync
  → 观感上的顿挫。修复：\`ext/luzzy-theme.css\` 把 \`.advanced-nav-panel\` 从上游
  \`0.32s cubic-bezier(.22,1,.36,1)\` 收敛到本项目令牌 **进入 200ms / 退出 140ms /
  \`cubic-bezier(0.23,1,0.32,1)\`**（chevron 同拍）——动画窗口 38 帧 → 24 帧，单次展开掉帧
  绝对数由 **~1.4 降至 ~0.8**（同场交替 A/B 两轮：19/20 → 8/8）。**掉帧率仍约 2~4%，本改动
  不消除它**（受 GPU 栅格地板限制）；收益是「暴露在预算外的帧数」与总顿挫时长等比下降，
  且时长本就是本项目令牌规定的值。
  **负面结论（已实测排除，勿重复尝试）**：①\`.advanced-nav-panel-inner\` / \`.advanced-nav-list\`
  加 \`will-change: transform\` 促独立合成层——成对交替测量无收益（28 vs 28），且与 patch 034
  的常驻层教训相悖；②\`.advanced-nav-panel{contain:paint}\` 首测似有 −64%（14→5），**成对交替
  复测反向**（无 17 / 有 26）——判定为噪声，不采纳；③纯淡入（去掉高度动画）虽可再降，但会
  破坏「下拉展开」视觉语义，未采纳。**零上游改动、无新 patch**。
- **开屏冷启动跑满 120Hz（遮挡期渲染抑制）**：用户报告「开屏动画最开始总是卡一下」。
  真机**冷启动**实测（\`dumpsys gfxinfo\` 5s 窗口）：**UI 帧时中位 17ms（≈60fps，没跑满 120Hz）、
  GPU 90 分位 15ms、legacy 掉帧 80.69%**；CDP 层树追踪同时证明**开屏入场动画本身已在合成层上**
  （accelerated transform/opacity），故掉帧不在动画机制，而在**被开屏完全遮挡的应用主体仍在
  渲染**（#app 整棵子树绘制 + 逐帧合成 1220×2656 ≈3.2M px @DPR 3.25，全属白烧）+ 启动期 GPU
  瞬时负载（首次栅格 / 着色器编译 / 系统启动画面退场）。上游启动期主线程成本（Tailwind JIT +
  Vue 执行 109ms + 微任务检查点 83ms）为既有事实，扩展层无法消除。
  修复（\`ext/luzzy-theme.css\` + \`ext/luzzy-splash.js\`，**零上游改动、无新 patch、视觉零差异**）：
  \`body:has(> .luzzy-splash:not(.lsp-dive):not(.lsp-warm)) #app { visibility: hidden }\` ——
  遮挡期不绘制应用主体，且**保留布局**（不选 \`content-visibility\`，避免应用自身的滚动定位 /
  输入岛自适应读到 0 而错乱）；**两段解除**：① 入场 1.2s 加 \`.lsp-warm\`，让应用在开屏仍不透明
  时完成首次绘制（该次绘制实测有 150~350ms 主线程尖峰，留到点击会与转场淡出重叠、露出空白）；
  ② 点击「沉溺」加 \`.lsp-dive\` 立即解除（**CSS 原生兜底**，JS 计时器失效也不会卡住应用）。
  \`:has()\` 不受支持时整条不生效，行为与改前完全一致（零风险降级）。
  **结果（三次冷启动取样）**：UI 帧时中位 17ms → **7ms**、GPU 90 分位 15ms → **6~7ms**、
  legacy 掉帧 80.69% → **4.13% / 4.76% / 3.84%**；转场窗口 95 分位 13ms。
  **另记一条被否决的方案**：单段解除（只在点击时解除）冷启动同样能到 7ms，但把应用首绘推迟到
  点击瞬间，转场窗口 95/99 分位劣化到 **150ms / 350ms**——即把卡顿从入场挪到转场，已弃用。
- **助手页 UI 设计语言断层修复（P0 契约自洽 + P1 机械对齐，2026-09-10）**：用户反馈「助手页相关页面
  是不是不符合其他页面的设计语言，感觉有断层」。落档审查档
  \`docs/design/AUDIT-assistant-ui-parity.md\`（评审结论 + P0–P3 修复计划 + 验收标准 + 风险表）。
  **根因**：助手 = 「上游聊天页皮肤」（压在角色背景图上的 112dp 黑渐隐 + 白字 + 1.6dp 描边）
  **＋**「上游设置页皮肤」（纯白管理台 + 深字 + ledger 组件库 2dp 描边）——两套上游皮肤本属不同场景
  （角色背景图 vs 纯白管理台），搬进同一原生容器后未做身份统一，用户在同一个栈里切换等于换一整套
  视觉语言。本轮完成 **P0 + P1**；**P2（顶栏语言统一）属新视觉决策**，按硬性规定 9 须先出
  D1/D2/D3 三方向板由用户选定，**未动**。
  - **P0 契约自洽（先改真源，再动代码）**：① DESIGN.md §管理页组件规范 #6 折叠时长
    **360ms \`.22,1,.36,1\` → 展开 200ms / 收起 140ms + \`cubic-bezier(.23,1,.32,1)\`**——追认会话 48
    已把 Web 侧栏收敛到的本项目令牌值，消除「Web 侧栏 200ms ／ 助手内 360ms」的节奏分裂
    （上游值仅保留在「上游出处」列作对照，不得再用于实现）；② §字体章**补登记等宽族**：
    终端输出 / 工具名 / 模型 ID 用系统 \`FontFamily.Monospace\`，**不再打包等宽 TTF**——为少量
    标识文本再增 ~1-2MB 不划算，中文在等宽族下回落系统字体属已知且接受的偏差；
    ③ 新增**「聊天页组件像素规格」表**（用户气泡 / AI 气泡 / 气泡间距 / 输入岛 / 审批卡·澄清卡 /
    空态），终结「契约只有色与结构、像素由实现层临场取值」的缺口；④ 明确 **2dp 描边适用于
    全部助手页，含聊天页**。
  - **P1 机械对齐（把实现对齐到已存在的契约；零新增色相、零新组件类型）**：① 聊天页图标
    **从来源上统一**——\`ChatIcons.kt\`（Canvas 手绘 1.6 / 1.5dp）**整文件删除**，顶栏与输入岛
    改用与管理页同一套 \`LedgerIcons\`（VectorDrawable，2dp 描边）：新增 \`Menu\`（上游 \`#icon-menu\`）
    与 \`Send\`（上游聊天页发送纸飞机）两枚，**零自绘**；② 管理页折叠面板改
    \`AnimatedVisibility(展开 200ms / 收起 140ms, LuzzyMotion.EaseOut)\`；
    ③ **用户气泡落回契约** \`#F1E3D9\` + coral-300 边（暗色 \`#2E2119\` / \`#9A6244\`，新增
    \`LuzzyColors.userBubble\` / \`userBubbleEdge\` token）——此前误用 \`surfaceCard\`(\`#EFE9DE\`)，
    与 AI 气泡(\`#F5F0E8\`)几乎同色，身份只剩右对齐与一条 35% 透明边可辨；
    ④ 输入岛 \`+\` / \`↑\` **文字字符 → 24dp 线性图标**（字形不再随字体 / 系统漂移）；
    ⑤ 删除白字版空态 \`ChatEmptyState\`（与 \`ChatScreen\` 深字空态重复，且白字压浅底必错）
    → 空态收敛为**全助手唯一实现**；⑥ 气泡最大宽统一 **320dp**（此前用户 300 / AI 320 无出处）；
    ⑦ 消息间距 48dp → **32dp**（上游 \`space-y-12\` 是为角色头像 / 时间留白，助手气泡无头像故显散）。
  - **验证**：\`testDebugUnitTest\` **331 用例 / 0 失败**（规格锁定测试 \`LedgerTokensTest\` 同步：
    折叠时长改断言 200 / 140、图标集 19 → 22 枚）；\`verify-markers\` **95 PASS / 0 FAIL**
    （未改上游文件、无新 patch）；\`assembleRelease\` 通过。
  - **遗留（诚实记录）**：**真机并排截图未做**（执行机 \`adb\` 无设备接入）——P1-7 的 32dp 间距与
    用户气泡色差属观感项，须真机目测确认。
- **P2 顶栏语言统一 = 纸面页头（D1，用户 2026-09-11 免除三方向门）**：审查档 A1 的**两套顶栏语言**
  至此收敛为一套。聊天页页头原为「上游聊天页皮肤」——**112dp 黑色渐隐覆盖层 + 白字 + Canvas 手绘
  1.6dp 图标**；那套皮肤在上游是给**压在角色背景图上**的聊天页做可读性用的，助手聊天页背后是纯
  \`canvas\`，既无图可压，又与八张管理页（canvas 底 + 深字 20sp Bold + 2dp 图标 + 40dp 方钮）同处
  一个栈——用户切换时等于换一整套视觉语言（用户原话：「感觉有断层」）。
  - **决策**：用户原话「顶栏语言统一，此任务本次免去三方向」→ 属 huashu-design「唯一豁免」
    第 1 条（用户本次会话明说跳过），**不出 D1/D2/D3 方向板**，由执行方依审查档候选裁定
    **D1 纸面页头**（D2 与「暖幕手记/雾纸」气质冲突；D3 = D1 + 一处在本实现下**无触发场景**的
    滚动状态——页头入流后内容不再从其下穿过）。决策与用户原话落档
    \`docs/design/direction-approved-assistant.md\`（Gate 文件），契约写入 \`DESIGN.md\`。
  - **落地**：\`ChatTopBar\` 重写为管理页 \`LedgerPageHeader\` **同骨架**——行高 48dp(\`h-12\`) +
    水平 16dp(\`.management-view p-4\`) + 行下 16dp(\`mb-4\`)，**canvas 底、无渐变、由覆盖层改为入流**
    （消息流不再从半透明栏下穿过）；汉堡 24dp(\`muted\`，40dp 触控区)、头像 36dp 圆
    (\`accentSoft\` 底 + \`accentButton\` 字，替管理页的 24dp 前置图标位)、名称 20sp Bold、会话标题
    12sp \`mutedSoft\`、chevron 16dp、右上角**图标按钮 40dp 方钮**（§#2 规格，\`LedgerIconButton\`）；
    图标一律 \`LedgerIcons\`（VectorDrawable，2dp），与管理页同一来源。**用户此前指定的内容差异
    全部保留**（头像 + 助手名 + 会话标题可点开 + 右上角「助手设置」），取消的只是上游那层皮肤。
  - **工程**：按下反馈 \`pressScale\` 由 \`private\` 改 \`internal\` 供聊天页复用（**单一定义点**）；
    删除随黑渐隐一起失效的死常量（\`HEADER_GRADIENT_HEIGHT\` / \`HEADER_ROW_HEIGHT\` /
    \`CHAT_CONTENT_TOP_PADDING\`）。**零上游改动、无新 patch、零新增色相**。
  - **验证**：\`testDebugUnitTest\` **331 用例 / 0 失败**；\`verify-markers\` **95 PASS / 0 FAIL**；
    \`assembleRelease\` 通过（单包 40.95 MB；\`apksigner\` CN=LuzzyRP / \`ed78235d…dfb1\` 与上一版一致）。
    **真机观感（亮/暗双模式）待用户装机确认**（执行机无设备）。
- **统一「所有页之间」的转场：页面交接编排（用户 2026-09-11 指定）**：用户原话——「呼出左侧侧边
  菜单栏、点击其他页时菜单栏左移，页内容交叉淡化，做好不透明度曲线，当菜单栏完全收起动画执行完毕
  屏幕不可见菜单栏时，淡化效果结束，完全呈现新页」。改前：RP-Hub 各页之间是**硬切**（上游用
  \`v-show\`/\`v-if\` 切 \`currentView\`，全站只有 toast/弹窗/下拉面板有 \`<transition>\`），侧栏收起走的是
  上游 \`0.28s cubic-bezier(.22,.78,.25,1)\`，与新页出现毫无时间关系。
  - **落地方案（扩展层实现，零上游改动、无新 patch）**：两侧时长共用单一变量
    \`--lsp-handoff-ms\` = **200ms** + \`cubic-bezier(.23,1,.32,1)\`（ease-out 令牌），侧栏
    \`translate3d(-104%)\`、\`.mobile-overlay\` 遮罩、旧页淡出、新页淡入**同帧起跑、同时结束**
    （时间线实测：旧页与新页同帧到达终态，侧栏差 1 帧）。
  - **不透明度曲线**：旧页 1→0 与新页 **0.35→1** 同窗交叉（新页不从 0 起——两页同时半透明会往
    底色发灰，即线性交叉淡化的「灰陷」）；两层一律用 **animation 而非 transition**（类加在视图切换
    的同一帧，transition 找不到「前一帧的值」，animation 加类即起跑，才可能与侧栏严格同帧）。
  - **旧页快照层**：用**原位元素**（不是克隆节点）——清掉 \`v-show\` 的行内 \`display:none\`（不写死
    display，保住元素自身的 flex/block 类），把它 \`absolute inset:0\` 浮在新页之上只跑 opacity：
    合成层、零重排、无克隆成本、滚动位置不丢；若旧页已被 \`v-if\` 摘除（如管理页之间互切）则跳过
    快照层，只做新页淡入。
  - **助手入口并入同一编排**：\`MainActivity\` 改为**覆盖层淡入结束才停绘 WebView**（原来淡入伊始
    就置 INVISIBLE，等于把「旧页」瞬间抽走——覆盖层只能淡入到窗口底色上，侧栏左收动画也看不见）；
    同时**取消 \`.app-main\` 的 18px 平移**（统一编排只保留「侧栏左移」一处位移，内容只做淡化）。
  - **修掉的 bug（用户真机实测「切了几次就出bug」）**：首版用「\`.app-main\` 下第一个可见子元素」
    判定页面，而扩展层自己注入的 \`.lsp-fab-row\`（关于页置顶 FAB，恒可见）也满足该条件 → 收尾时
    算错「当前页」，旧页的行内 \`display:none\` 没还原 → **聊天页永久盖在管理页上（两页叠加）**。
    改为**集合差分**：新页 = \`after − before\`，旧页 = \`before − after\`，恒可见 chrome = \`before ∩ after\`
    天然排除——不依赖类名/高度去猜，也不怕上游以后再加常驻元素。
  - **新增回归门禁** \`tools/page-handoff-test.cjs\`（桌面 Chromium 同引擎族、手机视口）：连切 10 次
    页逐次断言「可见页面数 == 1 / 无残留交接类 / chrome 不被误隐藏」，并采样一次完整转场的
    不透明度与位移时间线，断言**各要素同时结束**（差 ≤ 2 帧）与总时长落在 200ms 令牌 ±60ms。
    已做**红证**：故意注释掉收尾还原一步 → 测试立刻复现「两页叠加」（\`pass:false\`，退出码 1）；
    恢复后 \`pass:true\`。
  - **踩坑（已入 AGENTS §7）**：**headless Chrome 默认 \`prefers-reduced-motion: reduce\`**，动画被
    压成 0.01ms——不显式 \`Emulation.setEmulatedMedia\` 关掉，时间线断言等于白测（本测试第一版即此坑，
    曾出现「50ms 内全部到位」的假绿）。
  - **真机复测发现并修掉两处（2026-09-11，用户报「怎么是先切换才有交叉淡化」）**：
    ① **交接与切换不同帧**——Vue 在微任务里换完 DOM，而首版交接等到下一个 rAF 才应用，中间漏出
    一帧「已换页、无快照、侧栏还没动」的硬切（真机逐帧实测 dt=35 帧即此）。改为**微任务链**
    （两个嵌套微任务：排在 Vue flush 之后、同一帧 paint 之前）应用交接，并保留 rAF 兜底异步导航。
    ② **管理页之间互切没有快照层**——旧页被 \`v-if\` 从 DOM 摘除，首版直接放弃淡化（只剩新页淡入）。
    改为把**那棵原样子树**搬进自建覆盖层 \`#lsp-handoff-layer\`（\`z-index:10\`，压内容不盖侧栏
    \`z-50\`）当快照：不克隆、不重建，滚动位置仍在。
  - **回归门禁加强**（\`tools/page-handoff-test.cjs\`）：新增 **A7**（任一切换不得出现「可见页已变
    但前后帧都无交接」的硬切帧）、**A8**（管理页互切必须有 \`#lsp-handoff-layer\` 快照）、
    **A9**（**确定性**判据：交接必须在**微任务阶段**即生效＝与切换同帧，而不是等到下一个 rAF）。
    A9 已做红证：把调度改回 rAF-only → A9 判红（\`hoMicro=false, hoLater=true\`）；
    恢复微任务版 → 绿（\`hoMicro=true\`）。**概率性判据不用**（首版「帧末点击」场景跑两次一红一绿，
    概率门禁比没有更糟，已弃）。

**修复**
- **工具开关链路整条是死的（全面静态审查发现，2026-09-11）**：\`ApprovalGate\` 的 KDoc 与
  \`PLAN §12.1\` 都写着「T2/T3 默认关闭，**需用户在设置里逐项开启**」，但实现侧**三处全缺**：
  ① \`AssistantRuntime.toolSwitches\`（喂给审批门的内存快照）**从未被赋值**；
  ② \`AssistantPrefs.setToolGlobalSwitch\`（写开关）**没有任何调用点**；③ 助手设置页**没有开关 UI**。
  后果：\`globalSwitch\` 恒返回 \`null\` → 永远取 tier 默认值 → **日历读写 / 发到 RP 会话 / 终端 /
  截屏 / 点击 / 短信 / 通讯录共 8 类工具永远开不了**（交互死路）。
  修复：① 运行时构造时挂 \`observeToolSwitches()\`，内存快照跟随 DataStore；
  ② 助手设置页新增「**工具开关**」卡片（\`LedgerCollapseCard\` + \`LedgerToggleRow\`；清单直接来自
  \`registry\`——**UI 不硬编码**，默认关的排前并分组标注「需手动开启（默认关闭）」/「默认开启（可关闭）」）；
  ③ 保存后**直读 DataStore 回读**（\`explicitToolSwitches()\`），避免内存快照异步跟随造成的
  「点了没反应」；④ 新增单测锁定契约「显式开关覆盖 tier 默认值（两个方向都要生效）」。
- **日历工具必然失败（同批发现）**：代码里检查 \`READ_CALENDAR\` / \`WRITE_CALENDAR\`，但**清单里从未
  声明**这两项权限、App 内也没有申请流程——未声明的运行时权限**永远无法授予**，该工具 100% 报错。
  修复：清单补声明 + 助手设置页在开启日历工具时就地给出「**日历权限**」行（状态 + 「授予」按钮，
  走 \`ActivityResultContracts.RequestMultiplePermissions\`）+ 权限异常文案改为**可执行**
  （写明 App 内入口与系统设置路径）。
- **助手设置页「未读取到 Web 端配置」是死路（同批发现）**：只有三个字的裸状态、没有任何出路
  （用户 2026-09-10 实测反馈过这一现象）。修复：状态文字改「未同步」，卡片内补一行可执行说明
  （去哪儿配、返回助手会自动同步、助手不重复存 Key）；配色沿用既有 token（亮色 \`accentDeep\` /
  暗色 \`warning\`——亮色下不用 \`warning\` 是**为了对比度**，amber 压白卡不足 4.5:1）。
- **助手导航扁平化：8 个子项各自独立单页、均从侧栏进（用户 2026-09-11 指定）**：用户原话
  「我希望助手项的每一个子项都是独立单页，均从侧边菜单栏进入，而不是点击后进入二级页面」。
  改前有 6 条「页 → 页」推进边，改后全部消化：
  ① 8 个管理页的**返回箭头（→ 回首页）改为汉堡（→ 打开侧栏）**——它们本就是侧栏一级入口、
  彼此没有上下级，**没有「上一级」可返**（\`LedgerPageHeader\` 新增 \`onMenu\`；\`onBack\` 只留给
  「确实从别处推进来」的场景）；
  ② **「助手管理」独立页并入会话页**（页内折叠卡「助手管理」＝识别信息 + 删除；新建走头像条
  「+」与空态按钮），路由 \`AssistantRoute.AssistantManager\` 与 \`AssistantManagerScreen.kt\` 一并删除；
  ③ **会话页点一条会话 = 切到同为一级入口的「对话」页**并打开它（原为推进出 \`Chat(id)\` 二级页，
  该路由已删除）；新建会话同此。
  扁平化后**导航唯一入口就是侧栏**，与 DESIGN.md §管理页组件规范 #1「左键二选一」条款一致。
- **进助手后第一次点汉堡没反应（真机验证发现，2026-09-11）**：从侧栏点助手子项时，扩展层为了让
  侧栏同步左收，**直接摘掉** \`.mobile-sidebar-open\` 类；但上游侧栏的开合是模块状态
  \`isMobileSidebarOpen\`（\`app.js\` 的 \`setMobileSidebarOpen\` 负责 \`classList.toggle\`）——
  **摘类不改状态**，于是 DOM（已收起）与状态（仍认为开着）脱节。之后点助手页左上角汉堡走
  \`Luzzy.openRpSidebar()\` → \`toggleMobileMenu()\` 把状态翻成 \`false\`、再 toggle 一次
  「本来就不存在的类」→ 视觉零变化：**表现为第一次点没反应、第二次才打开**（真机 3 轮复现 1 次，
  触发条件 = 进入助手时抽屉正开着）。
  - **修复**：不再直接摘类，改为**点上游自己的汉堡**（\`toggleMobileMenu\` 的 DOM 入口，
    与 \`openRpSidebar\` 是同一个按钮），由上游状态机摘类并跑过渡——开合状态与 DOM 永远一致，
    不存在脱节。时长/曲线不变（\`:root[data-theme="luzzy"] .app-sidebar\` 的 \`transition\` 覆盖为
    \`--lsp-handoff-ms\` 200ms + ease-out 令牌），**画面与改前完全一致**。
  - **顺带清理**：\`<html>\` 上的信号类 \`.lsp-handoff\` 更名 **\`lsp-assistant-handoff\`**——
    它原来的职责（强制 \`translate3d(-104%)\`）已由上游闭态规则接管，现在**只是一个信号**：
    告诉页面交接控制器「这次换页由助手覆盖层接管，Web 侧不要交叉淡化」。\`ext/luzzy-theme.css\`
    的 \`html.lsp-handoff .app-sidebar { transform: … !important }\` 规则随之删除（留着会误导）。
- **助手导航三处真机缺陷（用户 2026-09-11 真机复测报「很大的问题」，同批修复）**：
  用户原话「页面之间的切换还是硬切换，然后从助手子项的聊天点击后页面是设置页而不是聊天页，
  并且进入设置页后再点汉堡菜单却无法呼出菜单」，并给出期望口径：**「呼出侧边菜单栏 → 随点击
  切换至其他页面，并且同步全局的页面切换效果」**。
  1. **点「对话」子项停在上一页**：\`AssistantRoot\` 的进入跳转写的是
     \`if (target != ChatList) route = target\`——「对话」的路由名是空串、映射结果正是 \`ChatList\`，
     于是被这句守卫挡掉，页面停在原处（设置）。且 key 用的是 \`initialRoute\` 字符串，**同一路由
     再次进入 key 不变 → \`LaunchedEffect\` 根本不重跑**（点「会话」也可能停在对话页）。修复：
     改用**自增导航序号 \`navSeq\`** 作 key（每次进入必变），并**去掉 ChatList 排除**。
  2. **一次切换里叠了两套动画 → 读作硬切**：侧栏进入时，覆盖层整体做 alpha 0→1 交叉淡化的同时，
     页内 \`AnimatedContent\` 又在做「旧页滑出 + 新页滑入」。结果是新页在覆盖层还半透明时就换完了，
     等覆盖层不透明时新页「已经在那儿」——观感就是硬切。修复：新增 \`entering\` 状态（进入淡化的
     200ms 内为 true），**此期间页内不做任何动画**——「页内容交叉淡化」完全交给覆盖层 alpha 与
     WebView 页承担，与 DESIGN.md 页面交接令牌一致。页内同级跳转（会话 → 对话、对话 → 助手设置）
     改为**纯交叉淡化**（去掉 1/24 位移）——扁平单页制下页与页没有前后关系，位移不再有语义。
  3. **返回侧栏是两段**：原实现「覆盖层淡出 140ms → 盲等 250ms 才点汉堡 → 侧栏再滑入 200ms」，
     中间约 110ms 空档。修复：呼出侧栏改走**页面交接令牌 200ms**（覆盖层淡出与侧栏滑入同帧起跑、
     同时结束），并把前端调用从「盲等固定时长」改为**按返回值退避重试**（\`Luzzy.openRpSidebar()\`
     返回 false = 汉堡没点到，100ms 后补试，最多 3 次）。普通退出（系统返回键 / onExit）仍为 140ms。
  4. **（真机复验时抓到的第四个根因）返回助手时侧栏是「跳」进去而不是滑进去**：助手覆盖层全屏时
     壳会 \`visibility=INVISIBLE\` + \`onPause()\` + \`pauseTimers()\`——**恢复后动画时间线仍是冻的**，
     在同一个任务里翻类，CSS 过渡会直接跳到终态。逐帧证据：\`openRpSidebar()\` 调用后 9ms 的首帧，
     侧栏 \`transform\` 已经是 \`0\`；而 WebView 活跃时同一次调用是 \`0 → -53 → -101 → … → -300\`。
     修复：\`Luzzy.openRpSidebar()\` 把「点上游汉堡」**推迟到渲染帧内**（\`requestAnimationFrame\`），
     时间线已恢复，过渡才真正跑起来（返回值只表示按钮是否找到，供原生侧重试判定）。
     这条同时解释了三处修复上线后仍存在的「硬切」观感——它就是被用户看到的那个跳变。

**真机复验（2026-09-11，小米 25098PN5AC / \`df97f3c4\`，用户在场）**：
- **点汉堡一次即出侧栏** ✓（修复前需点两次）；侧栏滑入逐帧实测 \`-300 → -247 → -156 → … → 0\`，
  约 **176ms** 完成，与覆盖层 200ms 淡出同步。
- **助手「对话」子项落到对话页** ✓（页内文本 = 助手名「阿墨」+「在下方输入消息开始对话」，
  设置页特征「保存」不存在）。
- **记忆页 / 终端页页头均为汉堡**（\`content-desc="打开侧栏"\`）✓；会话页点一条会话 → 落到对话页 ✓
  （页头不显示会话标题属**预期**：\`ChatScreen\` 对默认标题「新会话」故意不渲染副标题）。
- **逐帧录像客观判定**（\`adb screenrecord\` + ffmpeg 逐帧平均亮度 / 帧间差）：三段转场
  ——侧栏呼出（t=5.68–5.93，18 个渐变帧，帧间差 9.65 → 0.75 平滑衰减）、进助手（t=6.71–6.81，
  10 个渐变帧，峰值 20/255）、RP 页间切换（t=5.70–5.86，11 个渐变帧，峰值 35/255 后逐帧衰减）
  ——**全部没有「单帧巨大跳变 + 随后归零」的硬切特征**。
- 门禁回归：单测 **332 / 0 失败**、\`verify-markers\` **95 PASS / 0 FAIL**、
  \`page-handoff-test.cjs\` **pass**；\`assembleRelease\` 单包 40.96 MB、签名 \`ed78235d…dfb1\` 一致，
  已 \`install -r\` 装机。

**移除（2026-09-11 · 用户指示）**
- **「助手」功能（原生 Compose Agent 模块）及全部相关子页面彻底移除**：
  - **为什么改**：用户于 2026-09-11 明确指示移除该功能，本版不再交付「助手」；
    v1.5.0 由「上游同步 1.9.3 + 助手原生 Agent」两条主线收敛为**只保留上游同步**。
  - **代码**：\`app/src/main/java/com/luzzymeow/luzzyrp/assistant/\`**整模块删除（120 个 .kt**，
    data / domain / runtime / ui 全部子包）；\`app/schemas/\`（Room 导出 schema）随之删除。
  - **资产**：\`app/src/main/assets/assistant/\`**整目录删除（20 文件 / 26.5MB**）——含字体 TTF、
    内置技能、以及 **GPL-2.0 的 proot 沙盒二进制与 rootfs**；release APK 内该项压缩前占用
    **约 23.0MB**（改造前 APK 42.97MB → 改造后见下）。
  - **扩展层**：\`ext/luzzy-assistant.js\`（侧栏「助手」折叠组注入 + 配置推送 + 交接信号）整文件删除；
    \`ext/luzzy-bridge.js\` 的 \`Luzzy.openAssistant\` / \`openAssistantAt\` / \`openRpSidebar\` /
    \`isAssistantVisible\` / \`push·getAssistantConfig\` / \`setAssistantThemeMode\` /
    \`onAssistantVisibilityChanged\` 全部移除；\`ext/luzzy-ext.js\` 的助手脚本加载器与交接控制器的
    \`lsp-assistant-handoff\` 让位分支移除；\`ext/luzzy-theme.css\` 的「侧栏 → 助手」入口编排段移除
    （页面之间的交接编排保持不变）。
  - **原生接线**：\`MainActivity.kt\` 去掉 \`AssistantController\` 实现、覆盖层 ComposeView 懒创建、
    返回键三级优先级（回归「WebView 可回退则回退，否则退出」）、覆盖层进/出过渡、
    以及**仅为助手存在**的 WebView 停绘 / \`pauseTimers\` / 恢复逻辑；
    \`web/LuzzyBridge.kt\` 去掉 7 个助手相关 \`@JavascriptInterface\` 与 \`AssistantController\` 构造参数。
  - **测试**：\`app/src/test/java/.../assistant/\` **35 个测试文件整体删除**（它们只测助手自身；
    其余断言一律未动——**没有为了让测试通过而修改任何测试断言**）。
  - **构建配置**：\`app/build.gradle.kts\` 去掉 Compose 编译器 / KSP / kotlinx-serialization 三个插件、
    Compose BOM 与 UI / Material3 / Foundation / activity-compose / lifecycle-compose、
    Room 三件套、DataStore、OkHttp、kotlinx-serialization-json 依赖、\`buildFeatures.compose\`、
    \`ksp { room.schemaLocation }\` 与 Compose mapping 生产者版本对齐块；
    \`gradle/libs.versions.toml\` 同步清理对应 version / library / plugin 条目。
    **主壳不再需要 Compose**（除助手外无任何 Compose 使用点，已逐文件核对），故一并移除。
  - **明确未动**：\`app/src/main/assets/rphub/**\`（上游文件）**零改动**——助手入口原本就是扩展层
    DOM 注入，从未占用 patch 编号，故本次无 patch 新增 / 退役 / 实体重生成。
  - **体积变化（实测）**：release APK **42 969 263 → 18 215 394 字节**
    （**−24 753 869 字节 / −57.6%**，仅余原体积的 42.4%）——减少量与助手资产在包内的
    压缩占用（约 23.0MB）吻合，另含 Compose / Room / OkHttp 等依赖的代码与资源。
  - **验证**：\`assembleRelease\` 成功且 release 目录下**只有一个** \`app-release.apk\`；
    \`testDebugUnitTest\` 成功 0 失败（全仓测试仅助手测试，35 枚随模块删除后已无测试类）；
    \`verify-markers\` **132 PASS / 0 FAIL**；\`stream-render-test.cjs\` / \`model-list-test.cjs\` /
    \`page-handoff-test.cjs\` 均 **pass**；全仓助手标识符扫描在代码与工具层**零命中**（剩余仅历史文档叙述）。

**优化**
- **流式输出改「增量渲染」：长回复不再越写越卡（patch 042，用户 2026-09-11 指定）**：用户报
  「流式输出时性能损耗太严重，不丢任何前端部分，能不能优化？跑满手机帧率」。
  - **实测定位**（桌面 Chromium 同引擎族 + 应用自身函数；方法与原始数据见 \`docs/WORKLOG.md\` 会话 58）：
    流式分支每 tick 把**整段**消息重新 \`parseCot → processMainContent → renderMarkdown → v-html\`，
    内容是全文 → 浏览器每 tick 重新解析整段 HTML、重建整条消息 DOM、重排整条消息：
    **1200 字 11.6ms / 4000 字 24.7ms / 8000 字 44.8ms 每 tick**，而 120Hz 帧预算是 8.33ms ——
    成本 ∝ 消息长度、**与新增字数无关**，所以「越写越卡」。每 tick 调用次数实测：
    \`parseCot\` 42 次 / \`processMainContent\` 22 次 / \`renderMarkdown\` 21 次（整屏可见消息全部重算）。
    已排除的猜测：三协议节流不一致（三处 flush 都是 120ms）、每 tick 落库（只在生成结束等离散点）。
  - **做法**：扩展层新增 \`ext/luzzy-stream.js\`，提供 Vue 自定义指令 \`v-lsp-stream\`，按
    「**稳定前缀 + 活动尾部**」渲染——已定稿的块只追加一次、之后不再触碰；仍在增长的最后一块
    每 tick 只重建自身。模板侧只把流式分支的 \`v-html\` 换成该指令（**登记 patch 042**）。
  - **不丢任何前端部分（可证明的约束）**：增量渲染调用的仍是**应用自己的** \`renderMarkdown\`
    （显示过滤 / 显示正则 / marked / DOMPurify / 面板全部照旧）；且**每次推进前缀都先证明
    \`全文 HTML === 前缀 HTML + 尾部 HTML\`** 才提交，不等价就放弃这次推进、周期性校验不等价则
    **整段回退到全量渲染** —— 渲染结果永远与全量渲染一致，不存在「另一套渲染」。
  - **实测收益（同一模板、同会话配对测量）**：30 条历史下每 tick 主线程耗时
    1200 字 **10.6 → 7.8ms**、4000 字 **24.7 → 12.0ms**、8000 字 **44.8 → 18.3ms**；
    其中 layout 从 3.5 / 10.4 / 20.2ms 降到 **1.6 / 1.9 / 2.6ms**（重排不再外溢整条消息）。
    6.2k 字长回复后段：增量 JS **1.29ms** vs 基线 **3.58ms**（0.36×）、layout **1.38ms** vs **6.32ms**。
  - **新增回归门禁** \`tools/stream-render-test.cjs\`：A1 指令注册／A2 常规形态逐 tick **树等价**／
    A3 前缀确实推进／**A4 硬形态**（围栏内空行、松散列表、引用跨空行、有序列表）逐 tick 树等价
    （实测 118 tick 零失配，且等价证明**拒掉 3 个不安全切点**）／A5 单一大段落不退化为错误切分／
    A6 文本整体替换后仍等价／A7 收益比值 ≤0.75／**A8 负控**（拿掉等价证明的朴素增量必须被判红，
    实测 118 tick 中 46 tick 不等价）。门禁自带负控，证明这条等价性断言**有牙齿**。
  - **Patch 登记（硬性规定 10）**：\`tools/patches/README.md\` 新增 042 登记段；index.html 两处
    \`[LuzzyRP patch 042]\` 标记；实体 \`012-035-index-html.patch\` 按 v1.5.0 规程重生成
    （前像 blob id 仍为 \`52135b42\`；**逆向 1/1 与工作树逐字节一致**；**端到端 9/9 枚实体从
    4aef0bb 纯净基线全量重放后与工作树 9/9 逐字节一致**）；\`verify-markers.ps1\` 新增 5 项
    （含「流式分支不再有 v-html 兜底」的 notcontains 项）→ **100 PASS / 0 FAIL**。
  - **真机复测（2026-09-11，小米 25098PN5AC）——重要结论：渲染器不是真机瓶颈**：
    在设备上做定量测量后确认，\`v-lsp-stream\` 自身一次更新仅 **≈1.4ms**，
    但**任何一次根级响应式状态变更**（改消息内容、甚至改一个无关的检索词）都要
    **230–340ms 主线程时间**——上游是单体根组件，每次变更都会重渲染并 diff 整个界面
    （实测每次变更 diff ≈1040 个 vnode / 1130 个 DOM 节点）。
    把指令换成**空实现**后同样的 tick 循环仍是 233ms，消息 1200 字与 5300 字也无差别
    → 流式每 ~120ms 触发一次，主线程被超额占用约 2.5 倍，所以「跑不满帧率」。
    即 **patch 042 让流式渲染变便宜了（12–45ms → 1.4ms），但它不是限制帧率的那一环** ——
    已定位到真正的瓶颈：**上游单体根组件「每次状态变更重渲染整个界面」**。
  - **跟进修复（patch 044，同日实施）——流式正文「活通道」**：流式期间正文/思考**不再每 tick 写
    响应式状态**，改为先进非响应式缓冲，渲染后交给扩展层 \`Luzzy.streamRender.feed()\` 直接上屏；
    响应式 \`content\`/\`reasoning\` 降到 **1.2s** 提交一次，流结束强制追平（在 \`filterBlockedStyleText\`
    与落库之前）。渲染仍走应用自己的 \`renderMarkdown\`，提交后 \`content\` 与所渲染文本**逐字相等** ——
    **所见正文与改前完全一致、不丢任何前端部分**；流式分支绑定加 \`live: true\` 供指令登记元素，
    扩展层 \`update()\` 增加**不倒退**守卫（Vue 带低频提交的旧文本回灌时以活文本为准，提交追平后
    活通道自动退场）。扩展层不可用时每 tick 立即提交 = 改前行为（降级）。
    代价（显式记录）：流式**中间态**的次要 UI（字数统计 / 思考面板文字 / 时间线字数）刷新降到
    1.2s 一次；正文本身仍按上游节奏（120ms）逐段出现。
    \`tools/stream-render-test.cjs\` 新增 **B1–B5** 守卫（未登记元素不抛错 / 指令登记 /
    活通道与全量渲染逐节点等价 / **旧文本回灌不倒退** / 提交追平后退场）→ 全过。
    完整证据链（三臂对拍 / 最小复现 / 分段计时 / Tracing / 精确覆盖率 / CSS 剥离对照）
    见 \`docs/WORKLOG.md\` 会话 59（续）。
  - **正文阶段真机 A/B（同日，用户指示新建 \`deepseek-v4.1-flash\` 后实测）**：同构建、同模型、
    同提示词，运行时切臂（live = 活通道 / legacy = 临时置空 \`feed\` 使 app.js 退回每 tick 提交），
    顺序 live→legacy→live→legacy 成对交替：平均帧率 **76.1 / 61.1 fps vs 8.6 / 8.1 fps**；
    帧间隔 p50 8.3/8.3 vs 8.4/16.7ms；**帧间隔 p95 8.4/8.4ms vs 241.3/241.4ms**；
    长任务占用 38%/50% vs 95%/95%。该机为 120Hz（帧预算 8.33ms）→ **live 臂 p50/p95 都贴在
    8.3/8.4ms，即满帧**。渲染正确性（正文元素尾部与 \`content\` 一致、思考面板独立、落库完整）
    已逐项核对；测试消息与激活模型已复原。
  - **再优化（patch 045，同日）——思考面板也接活通道**：044 之后残余的长任务占用全部来自
    **思考面板**（\`msg.reasoning\` 每次提交都要重渲染整条思考，实测思考 5K–20K 字时单次
    200–390ms）。参照 rikkahub「思考块与正文块是两个不同 composable」的结构，
    把活通道由单条扩为**两条**：思考文字同样由扩展层按上游节奏直接上屏，响应式 \`reasoning\`
    改用更长的提交间隔（6s），正文要出现前先强制把思考定稿；思考通道**固定 \`skipRegex=true\`
    渲染**（与模板原调用一致，避免显示正则差异）。门禁新增 **C1–C4**（通道独立 / 共存 /
    skipRegex 等价 / 跨通道互不干扰）。真机同提示词实测：**3326 帧 / 35.2s ≈ 94.5 fps，
    帧间隔 p50/p95 = 8.3/8.4ms，长任务占用降到 22%**（对照 legacy：19 fps / p95 241ms / 86%）。

**修复**
- **发送键「要点偏上一点才点得到」（用户长期困扰，2026-09-11 报）**：真机命中测试定位到根因 ——
  关于页的「回到顶部」悬浮按钮（\`.about-top-fab\`，\`fixed bottom-5 right-5\`、44×44、\`z-30\`）
  **隐藏状态下 \`opacity: 0\` 却仍然 \`pointer-events: auto\`**，而它正好压在聊天页发送键的**下半部**
  （重叠 41×25px，实测发送键中心点点到的是它）→ 只有按钮最上面约 20px 能点到。
  **修法（扩展层 CSS，零上游改动）**：\`pointer-events\` 与显隐严格同源 ——
  \`.lsp-fab-row .about-top-fab { pointer-events: none }\`，仅 \`.is-visible\` 时恢复 \`auto\`。
  复验：全页扫描「不可见却吃点击」的元素 → **0 命中**；发送键 **全高可点**（命中测试逐点确认）；
  端到端「打字后立刻点发送键**中心**」→ 发出的正是刚打的字。
- **打字卡顿（用户 2026-09-11 报「打字的时候也很卡顿，呼出键盘之后」）→ patch 046**：
  真机实测**每次按键触发一次根重渲染，每次 ≈260–300ms**（15 键 = 15 个长任务 ≈ 4s 阻塞）。
  消融实验先排除 \`autoResizeInput\` 的强制回流（关掉它 4029→3892ms，无改善），
  确认病根仍是「单体根组件：任何根级响应式变更都重渲染整屏」。
  - **做法**：textarea 的 DOM 本来就是用户输入的内容，v-model 同步只影响「发送键可用状态」这类
    次要 UI，不值得每键重渲染整屏。故输入框改走**非响应式镜像 + 200ms 防抖同步**：
    \`:value\` 绑到一个**永远返回最新文本**的取值函数（因此任何重渲染都不会冲掉正在输入/正在拼字
    的内容，含流式期定时器重渲染）；发送键可用状态改用一个**只在「空 ↔ 非空」翻转时才写**的
    响应式布尔（打字过程最多两次重渲染，且按钮**即时可用**）；\`sendMessage\` 开头与失焦时
    \`flushChatInput()\` 追平，保证发出的就是刚打的字。
  - **实测**：15 键 → **1 次重渲染 / 1 个长任务（359ms）**（改前 15 次 / ≈4.4s）；
    按键到下一帧延迟 p50 **4.7ms / p95 8.1ms**（改前 p95 **302ms**）。
  - **真机端到端**：打字后 **55ms 内**点发送键中心 → 发出的正文 = 刚打的完整文本 ✅
    （\`userInput\` 状态当时只同步到第一个字，证明防抖生效且发送路径正确追平）。
  - 门禁：\`verify-markers\` **136 PASS / 0 FAIL**；index.html / app.js 实体重生成
    （前像不变、逆向逐字节一致、端到端 9/9 一致）。
    （本轮另踩一坑：新增的模板符号漏出 \`setup()\` 返回列表 → 模板解析失败 → **整页白屏**，
    装机后立即复验抓到并修复；已记入 WORKLOG。）
- **手动配置的模型在「选择模型」里看不到 / 被自动检测顶掉（patch 043，用户 2026-09-11 报）**：
  用户原话「在 API 配置里，明明是供应商设置内手动配置模型，可是在选择模型的时候又变成自动检测
  然后选择检测出来的模型了，自己配置的模型反而是看不到」。
  - **复现与根因**（桌面同引擎族驱动应用真实函数，方法同会话 58；证据见 \`docs/WORKLOG.md\`）：
    ① \`fetchModelsForProvider\` 的合并写成 \`manualOnly + 检测结果\` —— **同 id 时检测结果覆盖手动条目**，
    用户填的显示名 / 上下文 / 最大输出 / 模态全被丢弃，选择器里只剩端点返回的裸 id
    （实测：手写 \`shared-model{label:'我的共享模型', ctx:64000}\` + 端点返回同 id → 合并后变成
    \`manual:false, ctx:null\`）；这与**请求路径** \`getProviderModelMeta\`（手动条目优先）自相矛盾。
    ② \`providerModels\` 缓存只由「保存供应商」或 \`/models\` 拉取成功写入 —— **冷启动为空**，
    于是重启后打开选择器，手动配置的模型**根本不在列表里**（拉取失败时更是什么都没有，
    因为 \`rebuildMergedAvailableModels\` 会跳过没有缓存的供应商）。
  - **修复**：① 合并改为**手动条目优先**（检测结果只补用户没配过的 id）；② 新增
    \`seedManualProviderModels()\` 在 \`onMounted\`（\`loadData()\` 后）把各商手动模型种入缓存；
    ③ 新增拉取标记 \`providerModelsFetched\` —— \`ensureProviderModelsLoaded\` 不再以「缓存是否存在」
    判断是否拉取（否则手动模型一进缓存就把自动检测顺带关掉），保存 / 删除供应商时复位；
    ④ 两条保存路径（内置商 override / 用户商）同样改为手动条目优先写回，使编辑立即生效。
  - **实测（修复后）**：手写两条 + 端点返回三条（含一条同 id、一条仅端点有）→ 选择器显示 3 条：
    \`manual-only\`（手动，label/上下文保留）、\`shared\`（**手动优先**，label/上下文保留）、
    \`detected-only\`（检测，仍可选）；**断网 + 重载后**手动两条照常可见（含 label）；
    \`fetchHits = 1\` 证明自动检测没有被「种入缓存」顺带关掉。
  - **新增回归门禁** \`tools/model-list-test.cjs\`（A1 冷启动断网可见 / A2 显示名保留 /
    A3 同 id 手动优先 / A4 检测结果仍可选 / A5 手动专属可见 / A6 无 JS 异常 /
    **A7 渲染层断言**：label 的真实渲染宽度 > 0 且未被省略号截断——「数据层有 label ≠
    用户看得见」，正是本轮漏网的那一类 / **A8 负控**：无 label 的检测行必须仍是单行）→ **全过**；
    A7 已用**负控实证**（暂存两行式改动、跑旧标记）→ 红。
  - **真机复验（2026-09-11，用户已接设备并配好 API）**：数据层修复已生效 —— STA1N 供应商
    手写 3 条模型（\`GLM-5.3-Flash\` / \`DeepSeek-V4.1-Flash\` / \`gemini-embedding-2\`）在 94 条
    合并列表里**全部保留 \`manual:true\` 与显示名/上下文**。但截图显示**新问题**：选择器行只渲染
    裸 id，用户配的条目显示成 \`[Cloud]GLM-5…\`（被 meta chip 挤成省略号），旁边还有 4 条同族
    检测结果、全局 94 条 —— 等于看不见。故同批补三项（仍属 patch 043）：
    ⑤ \`ui-components.js\` 选择器行：手动条目（\`manual === true && label\`）**以 label 为主文本**、
    裸 id 退为 11px 淡色次要信息；**检测条目渲染路径完全不变**（无 label → 走原分支），
    未新增色相/组件/动效（沿用行内既有 mono + 灰阶文字）；
    ⑥ \`app.js\` \`filteredModels\` 检索字段加入 \`label\`（按自己起的名字搜不到 = 配了看不见）；
    ⑦ 同一供应商内**手动条目排最前**（跨供应商仍保持原分组顺序）。
  - **真机复验第二轮（同日）**：⑤ 的单行写法**在真机上等于没修** —— 行内固定件
    「供应商徽标 42px + 裸 ID 131px + meta chip 121px」+ 3×8px 间距 = **318px**，
    已超过行内容宽 **292px**（375px 视口下弹层实测），label 作为唯一的可收缩项被压成
    **clientWidth = 0**；截图里首行仍只剩 \`[Cloud]DeepSeek-V4.1-…\`。
    故 ⑤ 改为**两行式**：第一行「供应商徽标 + 用户显示名」，第二行「裸 ID + meta chip」——
    label 独占首行可用 242px ≥ 自然宽 150px、第二行 283px ≤ 292px，**三者零截断**
    （真机实测 \`labelW == labelScrollW\`，三条手配模型全部完整可见）；检测条目仍是单行
    （手动行 69px / 检测行 47px）。未新增色相、组件与动效，仍为同一行模板内的排布调整。
  - **Patch 登记**：\`tools/patches/README.md\` 043 段（含追加三项）；app.js 10 处 + ui-components.js
    1 处 \`[LuzzyRP patch 043]\` 标记；实体 \`012-036-app-js.patch\`（前像 \`79267c03\`）与
    \`012-035-ui-components-js.patch\`（前像 \`e9a992bc\`）按 v1.5.0 规程重生成（**各逆向与工作树
    逐字节一致**；**端到端 9/9 枚实体纯净基线全量重放 → 与工作树 9/9 一致**）；
    \`verify-markers.ps1\` 新增 10 项 → **109 PASS / 0 FAIL**（另含 R3 应用内 CHANGELOG 同步门禁）。

**注意事项**
- 本轮**未改任何上游文件**（\`assets/rphub/\` 零改动），无新 patch、无指纹表变更；
  verify-markers 仍应全绿。**上游同步已列入本版计划（§「同步」段），执行时按 AGENTS §4 全流程走**。
- 应用内 CHANGELOG 与 README 徽章已随 \`node tools/gen-changelog.mjs\` 同步至 v1.5.0；
  **\`tools/gen-changelog.mjs\` 新增徽章状态分支**：CHANGELOG 顶部章节状态为「开发中」时，
  README Status 徽章生成琥珀色 \`开发中·未发布\`（而非无条件写成绿色「正式版·可游玩」），
  避免开发中版本被误标为已发布——发布时按 §3.4 流程更新状态行即自动转绿。

### v1.4.0 — 同步上游 1.9.2 × 剧情面板 × 沉浸模式 × 抗截断协议融合（上游基线 RP-Hub 1.9.2）

> **状态：正式版已发布（2026-09-08）。** 构建：versionCode 12 · **release 单 APK**
> （\`app-release.apk\`，签名 CN=LuzzyRP；本版起关闭 ABI 拆分，见「注意事项」）·
> 用户真机人工验证通过。上游 1.9.2（commit \`d2f2625\`，14 提交 / 9 文件 +2242 −2268）。

**新增**
- **关于页更新日志关键词高亮（patch 039）**：关键词检索命中的内容里，关键词以 \`<mark>\` 高亮
  （文本节点级遍历、跳过 script/style、大小写不敏感、正则元字符转义；底色取 DESIGN.md
  highlight token \`--luzzy-mark\` = \`#F5D9A8\`，与开屏荧光笔记号同源，暗色下配反转主文字）。
- **同步上游 1.9.2（会话 24-25）**：UI 实时生成（剧情面板 \`story_panels\` 协议）／沉浸模式
  （\`settings.immersiveMode\`）／角色卡牌组（CharacterDeck）／主动工具调用改原生 toolCalls
  协议／快捷面板密度重构／开屏改版（书本动画，**我方 D3-A 保留「开卷」不采纳**）；
  安全面全过：\`nsfw\` 对象与 1.9.1 逐字节一致（storyPanels 为其后独立预设）、vendor/ 零变化、
  novel/runtime-services 零变化、无新增/删除文件。
- **抗截断协议融合（patch 015 × 上游 output_reply，D2-A 拍板）**：采纳上游 \`replyInTool\`
  原生 toolCalls 流式解析（\`readReplyDelta\` 增量解码）——Gemini 抗截断走上游原生路径，
  我方三协议适配器只做协议分派 + \`maxTokens\`/\`extraBody\` 注入，不再自建续写链。
- **上游新功能适配**：记忆分片助手与工具系统双轨混合（记忆系统取我方 v1.3.0 实现 +
  工具系统取上游 1.9.2 原生协议）、多商路由 × 上游原生 toolCalls 融合（主聊天/识图/
  UI 模板/总结四请求点）、\`getImageTagRegex\` 签名去参调用点适配（015）。

**优化**
- **用量页时间筛选去冲突（patch 037）**：右上角「更多」下拉（全部/24小时/7天/30天）与
  折线图「日/周/月」粒度双重筛选语义冲突——按用户指示整链下线（props/emits/模板/状态/
  watch/绑定/click-outside 全部移除），只保留折线图粒度与类型筛选。
- **版本更新公告品牌化（patch 038）**：公告弹窗标题由上游「网站公告」改为品牌名 **LuzzyRP**，
  内容区底部新增同步来源注释「同步更新上游节点：本公告内容随上游 RP-Hub 版本同步，
  由 LuzzyRP 呈现。」
- **index.html 开屏区恢复（会话 25）**：会话 24 三方合并时 C1/C3 冲突块误取 ours 侧，
  开屏区成为「上游 entry-transition 书本动画 + 我方 luzzy-splash 残骸」混合体且
  \`luzzy-splash-page/stack/center/glow/seal/wordmark/slogan\` 七节点与 027 标记丢失——
  按 v1.3.0 完整块整块恢复（\`entry-transition\` 彻底退役）。
- **标记与字体合规修复（会话 25）**：补回 001/004/006 标记注释、删除残留的
  \`fonts.googleapis.com\` preconnect 两行（硬性规定 4/10 门禁 003/004/006/027 五项转 PASS）。
- **实体重放通道加固（会话 25，工具层）**：实体段改为**先于字符串块执行**（实体前像 =
  上游纯净基线）、前像判定改用实体头 \`index pre\` 的 LF 归一 blob id、\`git apply\` 的
  trailing-whitespace 告警不再中断脚本（stderr 隔离）、应用后强制校验标记落盘；
  仓库外逆向 9/9 PASS + 纯净基线端到端重放 9/9 PASS。
- **指纹表更新至 1.9.2 基线**：全表 14 项以 \`d2f2625\` 重算（R1/R2 转 PASS）。

**修复**
- **角色卡工坊页 JS 不执行（patch 007 存量缺陷，v1.3.0 修复的回归保持）**：1.9.2 工坊
  大改版后重放 007 时保持「完整 uiHTML 行 + \`</\`+\`script>\` 拼接」形态。
- **上游修复采纳**：正则渲染嵌套重复渲染／UI 生成状态下正文异常阻断／新手引导界面
  高度自适应异常。

**注意事项**
- **发布打包变更（本版起）**：release **只附单个 APK**（\`app-release.apk\`）——LuzzyRP 是纯
  WebView 壳、不含 native 库，此前 ABI 拆分产出的三件套（arm64-v8a / x86_64 / universal）
  **字节完全相同**（v1.2.2~v1.4.0 release 资产 SHA256 实测一致），拆分为零收益；已关闭
  \`app/build.gradle.kts\` 内 ABI 拆分（恢复方式：取消注释原 \`splits.abi\` 块）。
- **测试包 → 正式包数据不互通（需重填一次，谨致歉意）**：测试包 \`com.luzzymeow.luzzyrp.debug\`
  与正式包 \`com.luzzymeow.luzzyrp\` 是**两个独立应用 ID**，安卓层面各自独立存储，正式包读不到
  测试包内的数据——首次切到正式包需重新填写**用户信息**（昵称/人设等）与**供应商 API 配置**
  （Key/URL/模型）。这不是本版更新丢失数据，而是跨包名切换的系统行为；同包名覆盖安装（升级）
  数据照旧保留。**保留原数据的做法**：继续使用测试包；或装正式包后用应用内导入导出搬移
  角色卡/世界书/预设（聊天记录与记忆不随包迁移）。后续版本沿用同一包名，再次升级不会再遇到。
- **决策记录（用户拍板）**：D1-A 全屏功能继续下线（上游 1.9.2 恢复全屏，重放 patch 022）；
  D2-A 抗截断采纳上游 \`output_reply\` 协议；D3-A 保留「开卷」开屏；D4-A 新功能默认值原样
  （\`immersiveMode=false\`、剧情面板预设默认关闭、CharacterDeck 默认关闭）。
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
  （\`editable\` 标志 + \`settings.apiProviderOverrides\` 持久化 + 注册表 override 合并 +
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
  \`rp_hub_default\`；**上游删除项**：临时指令功能、记忆页导入导出按钮（管理器仍可
  导入导出）、自动获取模型开关（改为启动仅拉激活商+选择器惰性补拉）；我方 016/026/031
  记忆链路依赖上游逐字保留零适配；实体全量以 1.9.1 基线再生成（9 枚逆向全过）。
- **存量缺陷顺带修复（patch 007 残缺 hunk）**：character 工坊页 \`uiHTML\` 行自 v1.0.0
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
  输入岛 / 侧栏 backdrop-filter 归零（暖纸 alpha 0.97），\`:has\` 流式加厚同步失效；
  \`glass-stabilize\` 与 scroll-reveal 三族 will-change 归 auto（渲染窗口常驻 40-60 个
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
  \`built-in-content.js\` 一个文件（+8/-13）——破限预设标记 \`<roleplay_hub_default>\` →
  \`<rphub_default>\`（上游公告「修复标记问题」）、UI 模板分析提示词微调、更新公告刷新。
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
- **版本分类解析修正（patch 024 修订）**：\`v1.0.0-rc2/-rc3\` 不再被截断成重复的
  \`v1.0.0\`（rc 后缀独立成项），下拉按首次出现去重；应用内 CHANGELOG 数据已随本版重新生成。

**修复**
- **向量记忆检索死区（patch 026）**：①手动检索不再继承自动召回的「近期保留楼层」排除窗
  （原行为：新会话分片后立即检索必空，且文案误报「还没有分片」）；②分片所属供应商已删除/
  改名时显式报「嵌入供应商已不存在」（原静默回退默认商、整桶 404 无从自查）；③裸引用
  回退协议跟随激活商（原硬编码 openai，Gemini 嵌入分片回退必失败）。排查结论：入库→
  量化→持久化→管理器链路完好，属前端过滤死区，非存储链路崩溃。
- **STA1N 供应商图标缺失（patch 023）**：上游自建图床 404 所致（1.9.0 基线同款），
  改用官方 CDN favicon；novel 子页面同源问题一并修复。
- **用量记录供应商维度缺失（patch 025 附带）**：\`recordApiUsage\` 补存 provider/protocol
  （patch 012 调用侧已传参但构造器漏存——用量列表 \`[商名]\` 前缀此前从未显示）；
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
- \`nsfw_rules\` 区块上游未触碰（硬性规定 1 复核通过）；vendor/ 离线依赖与本地字体无变化。
- 资产签名自动解压（构建期 assetSignature 注入 BuildConfig 比对）：资产变更即自动
  重新解压，IndexedDB 用户数据不受影响；关于页 CHANGELOG 自动同步（构建期重生成 + 校验门拦截）。
- 同步门与标记门：apply-patches 重放全部幂等 SKIP + verify-markers **57 PASS / 0 FAIL**
  （新增 021-027 共 14 项校验）；7 个实体 diff 再生成（范围式命名 007-023 / 009-023 /
  012-027）并全部通过逆向 \`--check\` 校验；指纹基线 1.9.0（94a0cd9）；patch 003 重放块退役
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
  不透明，源图 \`docs/design/brand-logo-v2-source.png\`）；mipmap 全套密度重采样 +
  legacy round 圆形裁切 + 关于页 \`luzzy-logo.png\` 同步；adaptive icon 由「透明贴纸」
  改为「全图前景 68% 居中 + 同色纯背景 #EDD7BD」（取自新图边缘均值）。
- **上游遗留蓝主题化（patch 008 v4）**：tailwind.config blue/indigo 色板接入
  \`rgb(var(--tw-*) / <alpha-value>)\`——luzzy 主题下全部上游遗留 blue-*/indigo-* 工具类
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
- **上游标记体系（硬性规定 10）**：全部二创改动在上游文件内携带 \`[LuzzyRP patch NNN]\`
  标记注释（001-012 存量补全）；\`tools/patches/entities/\` 新增实体 diff（上游 1.8.9 基线 →
  当前态逐文件，007/009/012-019 全覆盖）并接入 \`apply-patches.ps1\` 自动重放判定；
  新增 \`tools/verify-markers.ps1\` 校验门（标记完整性 + NSFW/styles 敏感文件指纹校验），
  同步全绿才算完成。

**优化**
- **品牌色收编**：开屏加载动画（背景蓝晕/光带/底盘阴影/LUZZY-RP 字标渐变/下划线条/嵌入页
  spinner 共 7 处）与设置页两处渐变横幅（用户设置/高级设置）由蓝色系收编为 Luzzy 品牌
  珊瑚陶土色（\`primary-*\` token，亮暗自适应）；仅 luzzy 主题生效，经典主题保持上游原版；
  DESIGN.md 新增「禁新增裸 blue/indigo/violet 色相类」规则。
- **开屏主题防闪蓝**：head 内联主题快照脚本 + luzzy-theme.css 移入 head +
  扩展层主题快照维护（patch 018）——冷启动首帧即当前主题色，消除「蓝色一闪再变暖」。

**修复**
- **v1.2.1 布局异常（顶部遗漏字段/底部溢出屏幕）**：根因为 patch 018 对 head 的第二段
  注入丢失 \`<script>\` 开标签——裸露的 \`document.write\` 文本被解析器判定为正文起点，
  head 提前关闭、body 提前开始，裸文本渲染到页面顶部且 \`luzzy-theme.css\` 主题底座
  （patch 008 色板依赖的 \`--tw-*\` 变量）加载失败，导致全应用配色/布局崩坏。
  补回开标签即修复（仅 1 行，未触碰上游区块）；以 parse5 浏览器同源解析器做树级对比` };
})();
