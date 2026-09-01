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
