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
# 003-entry-logo.patch
#   - index.html: 入口 logo RP/HUB -> LUZZY/RP
#   - 对应：二创品牌化
#   - 预期冲突点：上游改入口 logo 结构时需重打
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
# 013-appearance-panel.patch（2026-09-02，v1.1.0 外观独立面板）
#   - ui-components.js: AppSidebar 设置按钮下新增「外观」按钮（emit open-appearance）
#   - index.html: app-sidebar 接 @open-appearance；新增外观模态面板
#     （界面主题/模式/字体/对话字号，绑定既有 settings 字段复用 watch+deep-watch 持久化）；
#     设置页原主题卡（patch 011 v2 区）替换为「外观」入口卡
#   - app.js: showAppearancePanel ref + setup return 暴露
#   - 对应：用户需求「主题、字体相关设置独立为侧边菜单栏独立入口」；视觉复用雾纸弹窗层（零新增主题适配）
#   - 预期冲突点：上游改 AppSidebar 模板 / 全局弹窗区 / 设置页高级参数区时需重打
# ------------------------------------------------------------
