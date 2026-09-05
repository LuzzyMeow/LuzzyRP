# ============================================================
# LuzzyRP 二创登记 Patch（AGENTS.md §4.2 Patch 纪律）
# ============================================================
# 规则：
# 1. 上游文件改动必须全部落在本目录，禁止裸改（硬性规定 2）。
# 2. 每个 patch 头部注明：目的 / 对应硬性规定 / 预期冲突点。
# 3. NSFW 相关点位（built-in-content.js 内 nsfw_rules）永远不在 patch 范围内。
#
# 当前登记（全部针对 app/src/main/assets/rphub/ 内 files）：
# ------------------------------------------------------------
# 001-brand-title.patch
#   - index.html <title>: RP Hub -> LuzzyRP
#   - 对应：二创品牌化（无硬性规定对应项，属默认品牌改造）
#   - 预期冲突点：上游改 <title> 时需重打
#
# 002-disable-update-check.patch
#   - index.html: 移除 <meta name="rphub-update-api" ...>
#   - 对应：二创后禁用上游更新检查（PLAN-v1.0.0 决策 8）
#   - 预期冲突点：上游新增同类 meta 时需重打
#
# 003-entry-logo.patch（⚠ v1.2.3 起重放块退役）
#   - index.html: 入口 logo RP/HUB -> LUZZY/RP
#   - 对应：二创品牌化
#   - 预期冲突点：上游改入口 logo 结构时需重打
#   - ⚠ v1.2.3（patch 027）：上游 entry-transition 区块被「开卷」开屏整体替换，003 字标并入
#     新开屏；重放块退役（apply-patches.ps1 内保留退役说明），意图由实体 012-027-index-html.patch 承载
#
# 004-vendor-local.patch
#   - index.html: CDN 引用全部改为本地 vendor/（tailwind/vue/marked/dompurify/sortablejs）
#   - 对应：硬性规定 4（离线化）+ PLAN Phase 2
#   - 预期冲突点：上游新增/换 CDN 依赖时需重打并补充 vendor 下载
#
# 005-ext-mount.patch
#   - index.html: </body> 前挂载扩展层（luzzy-theme.css / luzzy-bridge.js / luzzy-ext.js）
#   - 对应：硬性规定 3（扩展层隔离）
#   - 预期冲突点：上游改尾脚本块时需重打
#
# 006-local-fonts.patch
#   - index.html head: Google Fonts Lora 引用 -> assets/css/local-fonts.css（本地字体）
#   - 对应：硬性规定 4（Lora 本地打包，禁 CDN）
#   - 预期冲突点：上游改字体加载方式时需重打
#
# 007-subpage-vendor.patch
#   - character/index.html + novel/index.html: CDN 引用本地化
#     （daisyui/localforage/marked/vue/tailwind -> ../../vendor/，Google Fonts 移除）
#   - 对应：硬性规定 4（离线化全覆盖，Phase 2 审计发现子页面同样有 CDN 依赖）
#   - 决策：CJK 分片字体（Ma Shan Zheng 100+ 分片、Noto Serif SC）不做本地化，
#     依赖安卓系统自带 Noto 字体回退（novel 页 art 字体降级为衬线）
#   - 预期冲突点：上游改子页面依赖时需重打
#
# ------------------------------------------------------------
# 008-theme-vars.patch（v3，2026-09-01 暗色修复）
#   - index.html tailwind.config: gray/primary 色板 → rgb(var(--tw-*) / <alpha-value>)（RGB 三元组）
#   - 对应：DESIGN.md 主题技术契约；变量定义在 ext/luzzy-theme.css（classic=原版值兜底）
#   - v3 动因：v2 纯 var() 下带透明度修饰符的工具类（bg-gray-50/60 等）被 JIT 回退纯白（暗色白块根因）；三元组 + <alpha-value> 使 alpha 由 JIT 自动注入
#   - 预期冲突点：上游改色板结构/新增色阶时需重打
#   - v4（2026-09-03，v1.2.2）：blue/indigo 色板同机制 var() 化——上游遗留
#     blue-*/indigo-* 工具类（41+8 处，toggle 选中态/叙事视角等）在 luzzy 主题下
#     收编为品牌珊瑚（luzzy-theme.css --tw-blue-*/--tw-indigo-* 与 primary 同值），
#     classic = Tailwind 原值零影响；violet-* 保留为协议徽标功能区分色（v1.2.0 备案）
#
# 009-font-options.patch（v2，2026-09-01 重做）
#   - core-utils.js fontFamilies: 内置三项改「经典」系标签 + 新增 luzzy（Luzzy 默认）
#   - 对应：用户指令（系统内置字体改为经典；默认字体 PuHuiTi+AlibabaSans）
#   - 预期冲突点：上游改 fontFamilies 结构/文案时需重打
#
# 010-defaults.patch（v2，2026-09-01 重做）
#   - app.js: 默认 fontFamily 'modern' → 'luzzy'；normalizeFontFamily 白名单加 luzzy
#   - 对应：新用户默认 Luzzy 字体
#   - 预期冲突点：上游改默认值/白名单时需重打
#
# 011-theme-ui.patch（v3，2026-09-02 外观面板独立）
#   - index.html 设置页: v2 的「界面主题 + 模式 + 界面字体」卡 → 「外观」入口卡
#     （点击打开 patch 013 的外观面板；对话字号卡保留原位）
#   - app.js: settings 加 theme/themeMode（默认 luzzy/light）+ themeOptions/themeModeOptions
#     + applyTheme/applyThemeMode immediate watch（含 LuzzyBridge.setSystemBarStyle 联动）
#     + setup return 暴露 + 老用户迁移（savedSettings 无 theme → classic，仅 savedSettings 存在时）
#   - 对应：设置页主题功能 + 新用户默认新主题 + 老用户保留经典
#   - 预期冲突点：上游改设置页结构 / fontFamily watch 区 / settings 加载块时需重打
#
# ------------------------------------------------------------
# 012-multi-provider-models.patch（2026-09-02，v1.1.0 多模型商混用）
#   - app.js:
#     · 模型引用体系：存储 `providerId::bareId`（首个 :: 分隔；裸 id=跟随激活商，向后兼容零迁移）
#       + parseModelRef / formatModelRef / formatModelRefText / resolveModelRequest helpers
#     · 供应商管理器：settings.apiProviders（任意数量用户商）+ settings.apiProvidersMigrated 迁移标记；
#       allApiProviders/userApiProviders 统一注册表；normalizeApiProviderSettings / getApiProviderById/ByUrl
#       扩展动态 id；老用户 custom/custom2 槽位一次性导入为用户商（原字段保留供小说工坊协议）
#     · 模型列表：providerModels 按商缓存 + rebuildMergedAvailableModels 跨商合并视图
#       （条目含 bareId/providerId/providerName）；fetchModels=刷新全部已配置商；
#       启动仅拉激活商，openModelSelector 惰性补拉
#     · 请求点接入 resolveModelRequest：主聊天 / 识图 / UI模板副模型 / 记忆总结 / 记忆嵌入
#     · 联动防污染：usesThinkingCotTag 先 parse 取 bareId（防商名前缀误判 /deepseek/i 等正则）
#     · 向量记忆：新分片记 embeddingProvider（embeddingModel 存 bareId）；检索按
#       (provider, model) 分桶、每桶现算查询向量；legacy 分片跟随激活商=原行为
#     · 用量记录加 provider 维度；iframe 同步载荷剥离商前缀；工坊激活商为用户商时映射 custom 槽位
#     · 供应商管理弹窗状态：showProviderManager / providerTestStatus / 增删改 / testProviderConnection
#   - ui-components.js: ModelSelectorModal 列表项/快捷槽位渲染 [商名] bareId（formatModelText prop）；
#     TokenUsageView / UiTemplatesView 加 formatModelLabel/formatModelText 可选 prop
#   - index.html: 供应商下拉 v-for userApiProviders + 「管理供应商…」入口；供应商管理弹窗；
#     聊天弹层槽位 / 设置页模型入口 / 记忆模型入口显示 [商名] 前缀；选择器/用量/UiTemplates 传 formatter
#   - 对应：用户需求「多模型商混用模型」（设置页/聊天页/记忆双模式/未提及点位一并更新）
#   - 预期冲突点：上游改 settings 结构 / fetchModels / 请求装配 / 记忆检索管线 / ModelSelectorModal 时需重打
#
# 013-appearance-panel.patch（2026-09-02，v1.1.0 外观独立面板；v1.2.0 起**模态面板部分已被 014 取代**）
#   - ui-components.js: AppSidebar 设置按钮下新增「外观」按钮（emit open-appearance）
#     ⚠ v1.2.0（patch 014）已将该按钮改为 selectView('appearance')，open-appearance emit 已删除
#   - index.html: app-sidebar 接 @open-appearance（⚠ v1.2.0 已移除接线）；新增外观模态面板
#     （⚠ v1.2.0 该弹窗整体移除，设置迁至独立视图）
#   - app.js: showAppearancePanel ref + setup return 暴露（⚠ v1.2.0 已删除）
#   - 保留本登记用于追溯 v1.1.0 形态；同步重放时以 014 为准
#
# 014-appearance-about-views.patch（2026-09-02，v1.2.0 外观/关于独立页 + 侧栏重排）
#   - ui-components.js: AppSidebar 底部簇重排：高级组 → 外观 → 关于 → 设置（设置置底）；
#     外观/关于均为 selectView 独立视图（itemClass 激活态）；删除 open-appearance emit
#   - index.html: app-sidebar 移除 @open-appearance 接线；新增 'appearance' 独立视图区块
#     （主题预览条 + 界面主题/模式/字体/对话字号四控件，自 013 弹窗整体迁入，全应用唯一外观入口；
#     设置页入口卡改为跳转 currentView='appearance'，重复字号下拉删除）；
#     新增 'about' 独立视图区块（logo + 版本（LuzzyBridge.getVersion 降级）+ 上游基线链接 +
#     CC BY-NC 4.0 署名 + GitHub 仓库 + CHANGELOG（v-html renderMarkdown，源 ext/luzzy-changelog.js））；
#     尾部扩展层挂载 luzzy-changelog.js（patch 005 区旁）
#   - app.js: 删 showAppearancePanel；新增 appVersionLabel/upstreamVersionLabel/changelogHtml/
#     openGitHubRepo + about 视图惰性渲染 watch（renderMarkdown 可用后定义处）
#   - ext/: 新增 luzzy-changelog.js（tools/gen-changelog.mjs 从 CHANGELOG.md 生成，勿手改）+ luzzy-logo.png
#   - 对应：用户需求「外观改独立页面、设置置底、外观从原设置剥离；新增关于页含 CHANGELOG」
#   - 预期冲突点：上游改 AppSidebar 模板 / 视图区块结构 / 扩展层挂载区 / setup return 时需重打
#
# 015-provider-protocol-models.patch（2026-09-02，v1.2.0 供应商三协议 + 模型管理 + 自定义生图）
#   - app.js:
#     · 数据模型：settings.apiProviders 条目扩展 protocol（openai|anthropic|gemini）/ models（手动模型条目：
#       id 请求id、label 显示id、contextLength、maxOutput、inputModalities 多选、type 单选、extraBody）/
#       extraBody（供应商级请求体）；normalizeUserApiProviders 字段保全（新字段加入映射白名单）；
#       settings.imageModelSource / customImageModelRef
#     · 长度解析：parseLengthToken / formatLengthToken（1024000|100K|1M|100k|1m → 数字，K=1024 M=1024²）
#     · 供应商编辑器：showProviderEditor/draft（浅拷贝防取消污染）/保存写回；模型增删改 UI 状态；
#       热检测预设五组（glm-5.3 / glm-5.3-flash / deepseek-v4-pro / deepseek-v4-flash /
#       deepseek-v4-flash-vision-exp，大小写不敏感长词优先，只填空字段+轻提示+撤销）；
#       编辑商 id → collectModelRefsByProvider 全槽位扫描 + `旧id::` 前缀与 key/缓存键整体重映射；
#       保存后 rebuildMergedAvailableModels 热更新（聊天/识图槽位立即可见）
#     · fetchModelsForProvider / checkApiStatus 按协议分型拉取（openai GET /v1/models；anthropic GET /v1/models
#       x-api-key 头；gemini GET /v1beta/models key 参数 + name 去前缀）；手动模型并入缓存
#     · 请求点接入三协议字段（protocol/maxTokens/extraBody）：主聊天 / 识图 / UI模板分析（改走适配层）/
#       记忆总结（改走适配层）/ 记忆嵌入（gemini 走 batchEmbedContents；anthropic 显式禁用并提示）
#     · 用量记录加 protocol 维度；工坊 remap 仅对 protocol==='openai' 的激活用户商生效
#   - runtime-services.js: requestChatCompletion 三协议适配——openai 原路径 + max_tokens + extraBody 合并；
#     anthropic Messages API（x-api-key + anthropic-version + anthropic-dangerous-direct-browser-access 头，
#     system 抽出、图片转 base64 source、max_tokens 必填缺省 8192、thinking budget 映射、
#     content_block_delta text_delta/thinking_delta SSE 解析）；gemini GenerateContent API
#     （:streamGenerateContent?alt=sse & :generateContent，contents/systemInstruction/generationConfig，
#     part.thought → reasoning，thinkingConfig.thinkingBudget 映射）
#   - index.html: 供应商管理器行加「编辑」按钮 + 协议徽标 + key 只读回显；新增供应商编辑器二级弹窗
#     （z-[60]：id/名称/协议三选一/URL/Key/供应商级请求体键值行/模型增删改卡）；生图设置加「模型来源」
#     （STA1N 官方 / 自定义模型），自定义时隐藏 NAI 专属字段（版本/风格）显示自定义模型下拉
#   - ui-components.js: ModelSelectorModal 列表项加手动模型 meta 摘要 chip（1M · 文本+图像）
#   - 生图：startCustomImageTask（POST {provider}/v1/images/generations，b64_json→dataURL），
#     data-image-request 伪 URL `luzzy-image://` 分流（loadGeneratedImageCard / renderGeneratedImageJob /
#     aspectRatio / reroll 四处适配）；enforceSpecialRules 按 imageModelSource 生成对应替换 URL
#   - 对应：用户需求「供应商支持自定义新增（OpenAI/Anthropic/Gemini）/编辑/删除 + 二级编辑弹窗 +
#   模型增删改（id/显示id/上下文长度/最大输出长度/输入模态/模型类型/自定义请求体懒编辑/供应商级请求体）+
#   五组热检测预设 + 热更新模型列表 + 自定义生图模型接入生图流」；不设「最大输入长度」字段（用户已确认）
#   - 实施中修正（模拟器走查发现，均已修复）：
#     · addUserApiProvider 占位条目必须先 push 进 settings.apiProviders 再进编辑器
#       （否则保存只改 draft 引用，供应商永不入列）；取消时移除空占位条目
#     · providerEditorIdConflict 以 __source 排除自身（原实现编辑已有商时误报 id 冲突）
#     · anthropic/gemini 非流式响应被服务端以 SSE 返回时逐行兜底解析（原 JSON.parse 直接抛错）
#     · system 抽出启发式收紧为「首条 user 纯文本 且 messages.length > 1」
#       （单消息场景正文被误吞进 system；仅 role==='system' 显式角色无条件抽出）
#   - 全面自检轮修正（会话 12 复查，9 处）：
#     · ★致命★ requestChatCompletion 分发器：anthropic/gemini 先剥离调用方传入的 OpenAI 路径
#       （buildApiEndpoint 产物 /v1/chat/completions），否则 anthropic POST 到错误端点、
#       gemini 拼出 /v1/chat/completions/v1beta/... 完全损坏（罐装测试传裸 base 未暴露，
#       真实调用链 CDP 回归验证修复后 URL 正确）
#     · toAnthropicMessages/toGeminiContents 相邻同角色合并（两家 API 严格交替，上游消息流
#       可能产生连续 user → 400）
#     · anthropic thinking 预算守卫（anthropicThinkingConfig）：budget 必须 < max_tokens 且 ≥1024，
#       max_tokens < 2048 时不启用 thinking（原实现 budget 可能 ≥ max_tokens 被拒）
#     · 自定义生图 reroll 崩溃：loadGeneratedImageCard(card, nextImageUrl.href) 对字符串 URL
#       取 undefined（两分支已统一产出字符串）
#     · 自定义生图 URL 的 $1 编码：prompt 不做 encodeURIComponent（否则正则替换不发生，
#       生图 prompt 恒为字面 "$1"）；parseCustomImageRequest 改子串提取 + 容错解码
#     · 编辑器保存时手动模型并入 providerModels 缓存（原实现未入缓存，无 Key 的商
#       手动模型永不进选择器）；fetchModelsForProvider 的 manual 条目携带完整 meta
#       （原 {id, manual} 丢失 contextLength 等，选择器 meta chip 不显示）
#     · 热检测预设渐进输入：模型 __presetLabel 追踪自动填充的 label，长词预设可覆盖
#       短词预设的自动 label（否则逐字输入 glm-5.3-flash 会锁死 GLM-5.3 标签）
#     · 预设「撤销」目标修正：providerEditorPresetModel 追踪触发行（原实现恒撤销最后一行）
#     · 预设填充 extraBody 时同步 extraBodyText 回显（原实现输入框显示空但对象已填）
#   - 壳工程配套（非上游前端）：LuzzyBridge 新增 openUrl（ACTION_VIEW 系统浏览器）——
#     WebView 无 onCreateWindow/setSupportMultipleWindows，window.open 是 no-op，
#     关于页 GitHub 入口必须走桥；luzzy-bridge.js 封装含降级
#   - 预期冲突点：上游改 settings 结构 / 请求管线（requestChatCompletion）/ fetchModels / 记忆嵌入 /
#     生图正则与任务管线 / 设置页生图区时需重打
#
# ------------------------------------------------------------
# 016-vector-recall-nomerge.patch（2026-09-02，v1.2.1 召回块防合并）
#   - data-services.js injectContextMessages: 向量召回 splice 消息加 _preventContextMerge: true
#   - 对应：会话 13 排查——召回块被并入相邻用户消息后，上下文查看器的
#     「角色记忆（向量召回）」startsWith 标注失效（用户看不到记忆分片标签的主因之一）
#   - 预期冲突点：上游重写 injectContextMessages / postprocessContextMessages 时需重打
#
# 017-memory-content-manager.patch（2026-09-02，v1.2.1 记忆内容管理器）
#   - app.js: memoryManager reactive（visible/selectedCharId/branchId/loading/saving/branches/
#     vectorList/classicList/分页/expandedShardId/editor）+ 角色选择器数据（ensureCharacterUuids）+
#     懒加载（readStoryBranchesForCharacter → scopeId → getScopedStoredValue，当前角色走内存数组）+
#     统一写路径（writeMemoryManagerVector/Classic：当前角色→内存+save*Now；其他角色→
#     setScopedStoredValue+compact/clone）+ CRUD（编辑分片强制重嵌成功才落盘/contentFingerprint 置空/
#     summary 重算；启停 enabled；删除 confirmAction；清空角色 showVueConfirmModal）+
#     分页 computed（LIST_PAGE_SIZE 沿用）+ setup return 暴露
#   - index.html: 记忆系统页新增「记忆内容管理」折叠卡（角色/分支选择器 + 统计 + 分片列表
#     [轮次/嵌入模型徽标/参与召回开关/两行预览展开/编辑/删除] + 总结列表[轮次标签/重试(仅当前角色)/
#     编辑/删除] + 分页）；记忆内容编辑弹窗（z-[60]，分片含重嵌提示）
#   - 对应：用户需求「可选择角色查看指定角色的记忆内容，查看/编辑/删除总结模式与分片模式全部记忆」
#     + 编辑保存策略=强制重嵌成功才保存（用户已确认）
#   - 预期冲突点：上游改记忆视图区块 / 记忆存储管线（compact/prepare）/
#     readStoryBranchesForCharacter / setup return 记忆区时需重打
#
# 018-splash-no-flash.patch（2026-09-02，v1.2.1 开屏主题防闪蓝）
#   - index.html head: ① 内联脚本按 localStorage 主题快照（luzzy_theme_snapshot，
#     由 ext/luzzy-ext.js MutationObserver 维护）同步写入 data-theme/data-mode
#     （无快照默认 luzzy+light）；② document.write 注入 ../ext/luzzy-theme.css
#     （⚠ 本块必须自带 <script> 开标签——曾因缺失致 head 被裸文本截断、body 提前开始、
#     顶部裸文本渲染 + 主题 CSS 加载失败，即 v1.2.1 布局异常根因，2026-09-03 修复）
#   - index.html 尾部: luzzy-theme.css <link> 移除（由 head 注入取代，保首帧品牌色）
#   - 预期冲突点：上游改 head 结构 / 尾部扩展层挂载区时需重打
#
# 019-drawer-brand-preview.patch（2026-09-03，v1.2.1 侧栏品牌化 + 预览交互化）
#   - ui-components.js: ① 侧栏品牌字样 RP HUB → LuzzyRP（Luzzy 主字 gray-800 +
#     RP primary-600，双色同构开屏字标，下划线条 w-11→w-14）；② 侧栏底部簇顺序
#     外观→关于→设置 调整为 外观→设置→关于（关于置底）
#   - index.html: 外观页主题预览交互化——色板随 data-theme 取色
#     （rgb(var(--luzzy-prev-*))，vars 定义于 ext/luzzy-theme.css，值=DESIGN.md 既有
#     token）；luzzy 下亮/暗双卡为可点按钮（aria-pressed + 选中 ring，点击直接切
#     settings.themeMode），classic 仅亮色单卡（经典无暗色模式）+ 可点击提示文案
#   - ext/luzzy-theme.css（扩展层，零 patch）：新增 --luzzy-prev-light/dark-* 色板
#   - app.js: 关于页版本 fallback v1.2.0 → v1.2.1（014 区块内 1 行）
#   - 对应：用户需求「关于置底 / 预览随主题切换并点击切换模式 / 侧栏品牌 LuzzyRP」
#   - 预期冲突点：上游改侧栏组件（app-logo/底部簇按钮）/ 外观页预览条结构时需重打
#
# 020-vector-toast.patch（2026-09-03，v1.2.2 向量检索失败外化）
#   - app.js: 两处向量分桶检索 catch（注入检索 / 手动检索）在 console.warn 外增加
#     节流 toast（window.__luzzyVectorToastAt 30s 全局节流防离线刷屏；showToast
#     不可用时 try/catch 降级为仅 console.warn——扩展层不影响主流程）
#   - 对应：会话 13 排查结论②——分桶检索失败此前仅 console.warn 用户不可见
#     （分片记录的嵌入商/模型与当前配置对不上时整桶静默跳过）
#   - 预期冲突点：上游重构记忆检索管线（buildVectorMemoryBuckets/分桶循环）时需重打
#
# ============================================================
# ------------------------------------------------------------
# 021-settings-cleanup.patch（2026-09-04，v1.2.3 需求 5.2/5.3）
#   - index.html: 移除设置页残留「外观」入口卡（原 patch 014 占位）——所在高级参数区随之
#     清空整段移除（外观唯一入口=侧栏「外观」页）；空间管理「网页存储空间」→「存储空间占用」
#     + 移除手动统计按钮
#   - app.js: 进入设置页自动统计 watch（每会话首次，hasMeasured/loading 守卫；清理后复测沿用上游）
#   - 对应：用户需求 5.2（入口唯一化）/ 5.3（存储统计自动化 + 文案）
#   - 预期冲突点：上游改设置页结构 / storageStats 管线时需重打
#
# 022-chat-fullscreen-removal.patch（2026-09-04，v1.2.3 需求 6）
#   - index.html: 移除聊天页右上角全屏按钮 + app-native-fullscreen class 绑定 + 菜单按钮全屏 v-if
#   - app.js: 全屏状态 ref / 原生 fullscreen helpers / 切换与同步函数 / fullscreenchange 监听
#     （挂载+卸载）/ setup 暴露 全部移除
#   - 对应：用户需求 6（聊天页全屏功能整体下线）
#   - 预期冲突点：上游改聊天页顶栏 / 移动端键盘视口逻辑时需重打
#
# 023-sta1n-icon-fix.patch（2026-09-04，v1.2.3 需求 5.1）
#   - core-utils.js + novel/index.html: STA1N API 图标 URL 原上游自建图床（picui.ogmua.cn）
#     已 404 → 改官方 CDN favicon（https://cdn.sta1n.cn/favicon.ico，curl 200 实测）
#   - 对应：上游 bug（1.9.0 基线同款未修），按 AGENTS §3.3.3 登记 patch 修复
#   - 预期冲突点：上游改内置供应商表时需重打
#
# 024-about-changelog-tools.patch（2026-09-04，v1.2.3 需求 3）
#   - index.html: 关于页 CHANGELOG 卡新增版本分类下拉（custom-select）+ 关键词搜索框 +
#     命中版本计数；右下角置顶 FAB（滚动 >240px 出现，aria-label）
#   - app.js: changelog md 按「### vN.N.N」解析版本章节 + 版本/关键词过滤渲染管线
#     （搜索 150ms 防抖；空选择=全部）+ FAB 滚动置顶（smooth，reduced-motion 降级 auto）
#     + 进入视图回顶 + setup 暴露
#   - ext/luzzy-theme.css（扩展层，零 patch）: FAB 进出场动效（进 200ms/退 140ms
#     cubic-bezier(0.23,1,0.32,1)，scale(0.96) 起步）+ about-view 平滑滚动 + reduced-motion 降级
#   - 对应：用户需求 3（滑动优化/置顶/版本分类/关键词检索）
#   - 预期冲突点：上游或后续 patch 改关于页结构 / renderMarkdown 管线时需重打
#
# 025-usage-chart.patch（2026-09-04，v1.2.3 需求 4）
#   - app.js: 用量趋势数据层——日（近24h·小时桶）/周（近7d·天桶）/月（近28d·周桶）三粒度 +
#     供应商/模型筛选（providerId::model 分序列）+ 单系列 Top8 溢出合并「其他」+
#     品牌 token 分类色板（primary ramp + 语义色）+ 历史记录 provider 缺失按 apiUrl 反查兜底
#   - ui-components.js: TokenUsageView 新增「用量趋势」卡（粒度分段按钮 + 供应商 chips +
#     模型多选 chips 带系列色点 + 纯 SVG 折线图：网格线/万单位 Y 轴/降采样 X 轴标注/空态）
#   - runtime-services.js: recordApiUsage 补存 provider/protocol——patch 012 调用侧已传参
#     但构造器漏存，用量列表 [商名] 前缀因此从未显示（历史记录不回填，图表层兜底）
#   - index.html: token-usage-view 图表 props/事件布线
#   - 对应：用户需求 4（多模型多色折线 + 三粒度 + 供应商/模型维度筛选）
#   - 预期冲突点：上游改用量页结构 / recordApiUsage / 记录字段时需重打
#
# 026-vector-search-fix.patch（2026-09-04，v1.2.3 需求 2）
#   - app.js: ①手动向量检索摘除「近期保留楼层」排除窗——排除窗语义仅适用自动召回（避免与
#     在上下文中的内容重复），原行为使新会话分片后立即手动检索必空且判空文案误导；
#     ②requestMemoryEmbeddings 对指向已删除/改名供应商的分桶引用显式报错（原静默回退
#     默认商、整桶 404 用户无从自查）；③resolveModelRequest 裸引用回退协议跟随激活商
#     （原硬编码 'openai'，Gemini 嵌入分片回退必 404）
#   - 对应：用户需求 2（分片可见但检索为空）——排查结论：前端过滤死区/桶级失败，
#     入库→量化→持久化→管理器链路完好，无存储崩溃
#   - 预期冲突点：上游重构记忆检索管线 / parseModelRef / resolveModelRequest 时需重打
#   - 遗留：MEMORY_VECTOR_SIMILARITY_THRESHOLD=45 仍为硬编码（app.js），可调阈值滑杆列候选迭代
#
# 027-splash-open-journal.patch（2026-09-04，v1.2.3 需求 1）
#   - index.html: 上游 entry-transition 开屏 DOM 整体替换为 LuzzyRP「开卷 Open the Journal」
#     自创开屏（用户三方向选定 B，参照 Aēsop 获奖互动站；Gate 记录
#     docs/design/splash-v1/direction-approved.md）；patch 003 入口字标并入新开屏
#     （003 标记保留于注释，重放块退役）
#   - ext/luzzy-theme.css（扩展层，零 patch）: 开屏动画全量样式——掀封/纸落/界格/钤印/
#     落墨/荧光划线/页码 ≈2.3s 定格 + 2.55s 起 450ms 整体淡出；亮/暗经 data-mode 首帧自适应；
#     唯一缓动 cubic-bezier(0.23,1,0.32,1)；仅 transform/opacity；reduced-motion 终帧直出
#     + 200ms 退场；色值全部 DESIGN.md token（品牌级画面，classic 同样生效）
#   - 对应：用户需求 1（自创开屏启动动画）+ 设计硬性规定 9 全流程
#   - 预期冲突点：上游改 entry-transition 区块 / head 注入结构时需重打
# 028-theme-single-track.patch（2026-09-05，v1.2.3 需求 2 · 补记）
#   - index.html + app.js: 主题单轨化——「界面主题」卡与主题切换移除，恒定「暖幕手记」；
#     老用户 classic 无条件迁移 luzzy（settings.theme 迁移 + expose 清理）
#   - 补记：本登记行于 v1.3.0 会话 21 补齐（会话 20 补充 4 遗漏本表登记）
#   - 预期冲突点：上游改外观页结构时需重打
#
# 029-providers-deepseek-only.patch（2026-09-05，v1.3.0 需求 2，用户拍板 D3）
#   - core-utils.js: 内置供应商精简为仅 DeepSeek（editable: true 开放编辑）；
#     默认商 sta1n→deepseek（defaultApiProviderId/defaultApiConfig 同步切换）
#   - app.js: ①migrateRemovedBuiltinProviders 启动迁移——sta1n/openrouter/siliconflow 无损转
#     等价用户商（URL/Key/模型槽位引用保留；记忆嵌入引用不改写，patch 026 显式报错兜底）
#     ②apiProviderOverrides 持久化 + allApiProviders override 合并 + 编辑器内置分支
#     （id 锁定）+ 设置页 URL 直编写 override
#   - index.html: 管理器编辑按钮/设置页 URL 输入框对 editable 内置商放开；编辑器 id 锁定
#   - novel/index.html: 兜底供应商副本同步精简（patch 023 图标修复随条目退位）
#   - 对应：用户需求 2（仅留 DeepSeek + 支持编辑内置供应商）
#   - 预期冲突点：上游改内置列表/供应商管理器/编辑器结构时需重打
#
# 030-about-no-upstream-version.patch（2026-09-05，v1.3.0 需求 3）
#   - index.html: 品牌卡「基于 RP-Hub {{ upstreamVersionLabel }}」→「基于 RP-Hub」（去插值）
#   - app.js: upstreamVersionLabel 整链移除（ref/填充/硬编码兜底/expose）
#   - ext/luzzy-ext.js: 注入页脚同文案固定；LuzzyBridge.UPSTREAM_VERSION 保留仅诊断
#   - 对应：用户需求 3（防上游同步遗忘基线串）
#   - 预期冲突点：上游改关于页品牌卡结构时需重打
#
# 031-memory-recall-node.patch（2026-09-05，v1.3.0 需求 4）
#   - app.js: extractMemoryRecallStamp（创建时盖戳——检索早于消息懒创建，只能此处绑定；
#     识别 patch 016 结构化召回块，摘要=片段数+相似度区间）+ getTimelineSteps 时间线首位
#     渲染「记忆召回」thinking 型节点（模板通用 step 渲染，零模板改动）
#   - 对应：用户需求 4（记忆系统插入显示独立节点）；纯文本回复不强制出卡（D2 拍板）
#   - 预期冲突点：上游改 getTimelineSteps / ensureAssistantMessage / 上下文查看器结构时需重打
#
# 032-stream-render-degrade.patch（2026-09-05，v1.3.0 需求 1）
#   - runtime-services.js: STREAM_RENDER_INTERVAL 60→120ms（三协议共用）；
#     renderMarkdown options.cache=false 流式 LRU 旁路（防中间串灌满 2000 上限）
#   - index.html: 流式分支调用传入 { cache: false }
#   - 对应：用户需求 1（流式卡顿——O(n²) 全文重算链降频 + 缓存污染消除）
#   - 预期冲突点：上游改流式节流/渲染缓存结构时需重打
#
# 033-input-area-transition.patch（2026-09-05，v1.3.0 需求 1）
#   - index.html: 输入区 transition-all→transition-[bottom] 定向；输入岛去过渡；
#     发送/中止按钮 transition-all→定向属性（FAB v4 配方，根除分数 DPR 合成层热区漂移）
#   - 对应：用户需求 1（发送键点击热区漂移）；配套 ext/luzzy-theme.css 输入岛退实底
#   - 预期冲突点：上游改输入岛/按钮类结构时需重打
#
# 034-perf-theme-governance.patch（2026-09-05，v1.3.0 需求 1，扩展层 ext/luzzy-theme.css）
#   - D1 玻璃档位（用户拍板）：气泡/typing/输入岛/侧栏退实底（blur 归零，alpha 0.97），
#     :has 流式加厚同步失效；思考卡/模态等低频面保留磨砂
#   - 合成层瘦身：glass-stabilize / scroll-reveal 三族 will-change→auto（常驻 40-60 层→按需）
#   - 开屏 lspDiveZoom 去 filter:blur（全屏逐帧重栅格主源），失焦感由 scale+rotate+opacity 表达
#   - 对应：用户需求 1（跑满刷新率/气泡卡顿/开屏掉帧）；DESIGN.md 已同步
#   - 预期冲突点：上游改玻璃组件类名 / scroll-reveal 结构时需重打
#
# 035-provider-icon-and-card.patch（2026-09-05，v1.3.0 用户实测反馈四项）
#   - index.html: ①编辑器新增「供应商图标」行（从相册选择/清除）+ 文档尾部 1:1 裁剪覆盖层
#     （拖动选块/右下角圆点缩放，确认裁 128×128 dataURL）②管理卡两行式重构（图标 +
#     名称/徽标行 + 按钮行，修复名称被按钮挤压截断成单字符）+ 模型数徽标
#     ③编辑器模型列表空态文案澄清（/models 在线拉取缓存语义）
#   - app.js: 裁剪状态机（选图/缩放/拖拽/确认出图）+ 保存路径（内置写 override.icon、
#     用户商写 source.icon）+ normalizeUserApiProviders 保全 icon 字段 + 冲突检查修复
#     （内置商编辑 override 合并致对象身份失真→误报「id 已被占用」，__builtinEditable 短路）
#     + providerModelCount 助手 + expose
#   - 对应：用户真机实测反馈（冲突误报截图/名称截断/模型来源困惑/图标功能需求）
#   - 预期冲突点：上游改编辑器模板/管理卡结构/normalizeUserApiProviders 时需重打
#
## 标记体系与实体重放（2026-09-02，v1.2.1，硬性规定 10）
# ============================================================
# 1. 显式标记：上游文件内全部 patch 区域现携带 [LuzzyRP patch NNN] 注释
#    （2026-09-02 补全审计：001-012 结构性点位 + core-utils/ui-components 补齐；
#    013-017 实施时自带）。verify-markers.ps1 按本登记表逐项校验。
# 2. 实体重放：tools/patches/entities/*.patch 为「上游 1.8.9 基线 → 当前态」
#    的逐文件完整 diff（由 rp-hub-reference 克隆生成，含全部标记），覆盖
#    007/009/012-020；apply-patches.ps1 末段按「指纹基线一致才自动 apply」执行。
#    同步新版上游重放失败时：手工合并 → 用 rp-hub-reference 检出对应新版基线
#    重新生成实体 → 复跑 verify-markers.ps1 全绿。
# 3. 敏感文件基线校验：built-in-content.js / styles.css 必须与上游指纹逐字节
#    一致（verify-markers.ps1 的 R1/R2 项）。
# ------------------------------------------------------------

