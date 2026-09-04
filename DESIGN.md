# LuzzyRP DESIGN.md · 设计契约

> 本文件是 LuzzyRP 的**唯一设计真源**（open-design 品牌契约模式）。
> 任何 UI/UX 改动必须遵循本文档；修改本文档需要在 CHANGELOG 声明。
> 方法论：huashu-design 工作室多角色流程；交付门控：ui-ux-pro-max pro-rules + open-design 五维 critique。
> 依据硬性规定 9（设计 SKILL 强制条款）：任何涉及 UI/前端设计的工作必须先完整阅读 4 项 SKILL。
> 方向选定：2026-09-01 用户选定「**C · 暖幕手记 × 增强 Claude 风格**」（原话与融合细节见 `docs/design/direction-approved-v2.md`）。

## Overview

LuzzyRP 是移动端 AI 角色扮演应用——「每次对话，都像一本有你的小说」。
基于 RP-Hub 二次开发（WebView 壳），主题系统支持：**经典**（原版 RP-Hub 浅色，老用户默认）与
**Luzzy 新主题「暖幕手记」**（亮/暗双模式，**新用户默认**）。

**设计气质**：暖幕手记 × Claude——一本摊开在暖光下的剧作手账。tinted cream 画布 +
珊瑚陶土 accent + 衬线标题的文学排印声音；克制的「手作记号」（荧光笔划、伞骨线、雨点点阵）
只做情绪注脚，不做装饰堆叠。像 Anthropic 一样 deliberate warm：在满屏冷调 AI 工具里，
我们是纸与墨的那一个。

**关键体验词**：温暖（cream/coral 电压对）、文学（衬线 display × 无衬线正文）、
沉浸（长读不疲劳、夜间暗纸不刺眼）、手作（记号克制，宁少勿滥）。

## Colors

### 经典主题（classic = 原版 RP-Hub 色值）

- gray 50-900：`#f9fafb` → `#111827`（中性冷灰阶）
- primary 50-900：`#eff6ff` → `#1e3a8a`（蓝色系）
- 亮色画布 `#f9fafb`；表面 `#ffffff`；文字 `#111827` 系

### Luzzy「暖幕手记」· 亮色（Claude token 体系）

| Token | 值 | 用途 |
|-------|-----|------|
| canvas | `#FAF9F5` | 画布底（tinted cream，gray-50） |
| surface-soft | `#F5F0E8` | 次级表面（gray-100） |
| surface-card | `#EFE9DE` | 卡片表面（gray-200） |
| hairline | `#E6DFD8` | 发丝分割线（gray-300） |
| hairline-strong | `#BEB6A8` | 强描边/禁用（gray-400） |
| muted-soft | `#8E8B82` | 弱文字（gray-500） |
| muted | `#6C6A64` | 次级文字（gray-600，≥4.5:1） |
| body-strong-mid | `#52504A` | 强调次级（gray-700） |
| body | `#3D3A36` | 正文（gray-800，≈9:1） |
| ink | `#141413` | 主文字（gray-900，≈16:1） |
| accent 图形 | `#CC785C` | primary-500（Claude coral：图形 accent/链接/选中） |
| accent 按钮 | `#A9583E` | primary-600（按钮底，白字 ≈4.7:1） |
| accent 深 | `#8F4732` / `#733626` / `#57281B` | 700/800/900 |
| accent 浅 | `#FAF0EA`→`#DB9273` | 50-400（soft 底/边框/hover） |
| highlight | `#F5D9A8` @ 55% | 荧光笔记号（amber 低饱和版，仅注脚） |
| success / warning / error | `#5DB872` / `#D4A017` / `#C64545` | 语义色 |

完整 10 阶 ramp（gray / primary）见 `luzzy-theme.css`，一一对应上游工具类。

### 数据可视化分类色序（v1.2.3，用量趋势图）

多系列折线图按序取色（全部为本文件既有 token；`primary-*` 用 `rgb(var(--tw-*))` 亮暗自适应）：
`primary-500` → `warning #D4A017` → `primary-600` → `success #5DB872` → `primary-700` →
`error #C64545` → `primary-400` → `gray-500`；第 9 起合并为「其他」（`gray-400`）。
禁新增额外色相（同 Do's & Don'ts 约束）。

### Luzzy「暖幕手记」· 暗色（Claude 暗表面系，gray 色阶反转；v3 层次重调）

| Token | 值 | 用途 |
|-------|-----|------|
| canvas | `#171614` | 画布底（surface-dark，gray-50 最深） |
| surface-soft | `#201E1B` | 次级表面（gray-100） |
| surface-card | `#2B2824` | 卡片表面（gray-200，elevated，与画布拉开的层次） |
| hairline | `#3E3A34` | 发丝线（gray-300，暗下可见） |
| hairline-strong / 图标 | `#6B675F` | 强描边/弱元素（gray-400，≥3:1） |
| muted-soft | `#8A867D` | 弱文字（gray-500，4.9:1） |
| muted | `#A5A198` | 次级文字（gray-600，6.8:1） |
| body-strong-mid | `#C4BFB5` | 强调次级（gray-700） |
| body | `#DED9CF` | 正文（gray-800，≈12:1） |
| on-dark | `#FAF9F5` | 主文字（gray-900，≈15:1） |
| accent 图形 | `#D97757` | primary-500（暗下提亮的 coral） |
| accent 按钮 | `#B85C3E` | primary-600（按钮底，白字 ≈4.5:1） |
| accent 浅阶 | `#2E211B`→`#9A6244` | 50-400（暗底卡片/边框） |
| accent 高亮 | `#E0946F`→`#F7DCC8` | 700-900（accent 文字亮化） |

暗色 gray 色阶**整体反转**（50 最深=画布 → 900 最浅=主文字），上游全部 gray-* 工具类
自动适配，无需改上游 DOM。v3 层次重调：表面阶梯 1.0%→1.3%→1.9% 亮度拉开 +
发丝线可见化；弱/次级文字对比度 4.9:1 / 6.8:1（v2 为 3.95:1 / 不达标）。

## Typography

| 层 | 字体栈 | 说明 |
|----|--------|------|
| display（角色名/名字标签/区块标题） | `Lora` + PuHuiTi 回退 | 本地打包衬线，对位 Claude Tiempos/Copernicus；文学声音 |
| 正文 UI | `AlibabaSans` + `Alibaba PuHuiTi 3.0` | 拉丁/数字走 AlibabaSans，中文走 PuHuiTi（本地 @font-face） |
| 经典（modern） | 上游 Inter 系原栈 | 经典主题与「经典（原版）」字体选项使用 |
| 衬线正文选项 | `Lora` 本地 | 「经典衬线」字体选项 |

- 正文 ≥14px，行高 1.55-1.7；caption ≥12px；
- **字体设置四选项**：`Luzzy 默认`（PuHuiTi+AlibabaSans，新用户默认）/ `经典（原版）` /
  `经典衬线（Lora）` / `系统`；
- 禁止运行时 Google Fonts CDN（硬性规定 4，Lora 与阿里字体全部本地 woff2）。

## Layout & Elevation & Shapes

- 布局不改变上游 DOM 结构（主题=视觉层，规定 2/3）；
- 分层：画布（cream）→ 卡片（surface-card + 发丝线）→ 浮层（白纸面 + 柔和投影）；
- 圆角：气泡 16px / 卡片 12px / 输入岛 22px / 按钮 10-12px / pill 999px；
- 阴影：亮 `0 2px 8px rgba(20,20,19,0.05)` 级别的纸感轻投影；暗模式以表面色阶+发丝线分层，
  阴影收弱；
- 手作记号规范：全屏同屏 ≤3 处；荧光笔划 = 半透明 amber 斜切块压文字底层；
  伞骨/雨点 = 1.5-2px SVG 线稿；禁止 emoji、禁止左彩边圆角卡。

## Components（上游组件的主题映射）

| 上游组件 | Luzzy 主题处理 |
|----------|----------------|
| 顶栏黑色渐隐 | 保留黑渐隐结构（双向模式都可用的可读性底），参数微调 |
| AI 气泡（玻璃） | `rgba(250,249,245,0.78)` + hairline 边 + backdrop-blur；暗= `rgba(37,35,32,0.82)` |
| 用户气泡 | `#F1E3D9`/85% + coral-300 边；暗= coral-50 底 + coral 边 |
| 名字标签 | Lora 衬线 + muted 色（剧作手记的角色名感） |
| 输入岛 | 白纸面 + hairline + 轻投影；发送键 = coral 圆 |
| 发送/强调按钮 | primary-600 底 + 白字；active = primary-700 |
| 设置页卡片 | surface-soft 底 + hairline 边；选中态 = coral-100 底 |
| 用量趋势图（patch 025） | 白卡 + 发丝线 + 纸感轻投影；系列色按「数据可视化分类色序」；粒度分段与供应商/模型 chips 选中态走 primary-* |
| 关于页 CHANGELOG 工具（patch 024） | 版本下拉 + 关键词搜索框走 primary-* focus 态；置顶 FAB = 白圆钮 + 发丝线 + 纸感投影，primary hover |

## Glass（雾纸玻璃层 Frost-Paper）

> 方向板 A「雾纸 Frost-Paper」，用户选定于 2026-09-01（原话与三方向对比见
> `docs/design/boards-v3/direction-approved-v3.md`；参照 Windows 11 Mica / Arc 侧栏）。

**原则：玻璃只上固定 chrome，内容层维持纸感。** blur 统一 **16px**
（上游 chrome 为 backdrop-blur-xl=24px，收准到 16，中端机 GPU 代价下降）；
无 specular；`@supports` 不支持时降级实底（alpha 本就 ≥.85，视觉连续）。

| 表面 | 亮色 | 暗色 |
|------|------|------|
| chrome 半透白面（`bg-white/50-95`：顶栏 / 输入岛 / 侧栏抽屉 / 徽章） | `rgba(250,249,245,.86)` | `rgba(32,30,27,.86)` |
| 模态面板（modal-shell 直子 `.bg-white`） | `rgba(250,249,245,.88)` + blur 16 | `rgba(32,30,27,.88)` + blur 16 |
| chrome 发丝线（玻璃面自携 `border-gray-100/80`） | `rgba(230,223,216,.8)` | `rgba(62,58,52,.8)` |
| 聊天气泡（`.msg-bubble-glass`）· v1.2.0 起入玻璃族 | `rgba(245,240,232,.74)` + blur 18 + 发丝线 `.7` | `rgba(43,40,36,.74)` + blur 18 |

### 统一雾纸 · 聊天页玻璃补全（v1.2.0，方向选择记录 `direction-approved-v120.md`）

v1.0.0 曾把气泡强制实底（用户反馈「玻璃不完整」根因）。v1.2.0 以**统一雾纸**配方
把聊天页全部表面纳入玻璃族（用户选定方向：最克制、与 chrome 玻璃同族、可读性最稳）：

| 表面 | 亮 | 暗 |
|------|----|----|
| AI/用户/system 气泡 + typing 气泡 + 思考卡外层 | `gray-100/.74` + `blur(18px) saturate(1.2)` | `gray-200/.74` 同 blur |
| 思考卡 is-open | `.80` | `.80` |
| 思考卡 is-live（流式） | `.94` + blur 6 + coral 描边 | `.94` |
| 流式加厚（`:has(.cot-ui.is-live)` 所在行气泡） | `.88` + blur 8 | `.88` |
| 名字 chip | 白 `.82` | `#2B2824` `.82` |
| 消息操作工具条（上游自带 blur 被移动端 kill-switch 打死，v1.2.0 收编） | `gray-50/.6` + blur 14 | `gray-100/.6` |

- **单点调参**：`--luzzy-glass-alpha`（0.74）/ `--luzzy-glass-blur`（18px）两个变量统管全部
  基础面——流式掉帧时整体上调即可；
- 对比度：0.74 alpha 在深色立绘上正文 ≥7:1（CDP 实测亮暗两套 computed 命中
  `docs/design/verify-v120-*.png`）；
- 输入岛维持 chrome 级高不透明（键盘邻接面），不入玻璃族；
- `@supports` 降级扩展至气泡/工具条（实底 `#F5F0E8`/`#2B2824`）。

- **枚举而非通配**：只接管 `bg-white/50` `-60` `-70` `-90` `-95`；`/20` `/40` 是照片上的
  白 chip（白字语义），保持上游值——通配 `[class*="bg-white/"]` 会打碎图片浮层对比度；
- **上游 kill-switch（关键工程约束）**：styles.css 移动端媒体查询内有
  `* { backdrop-filter: none !important }`（上游为性能全局关闭移动端磨砂，手机上上游自己的
  glass 也因此从未模糊过）。雾纸层以 `:root[data-theme]` 前缀 + `!important` 更高特异性
  **仅对 chrome 表面放行 blur**（backdrop-blur-xl 持有者 / `.app-sidebar` / 模态面板），
  其余表面维持上游省电策略；真机（小米 25098PN5AC / WebView 150）实测磨砂渲染生效
  （条幅探针证照 `docs/design/verify-frost-phone-{light,dark}.png`）；
  ⚠ CDP `captureScreenshot` 在页面有激活 backdrop-filter 时会挂起——真机截图走
  `adb shell screencap`；
- 照片浮层 chip（`bg-white/20` + `backdrop-blur-md` + 白字）不属雾纸体系，不改；
- 暗色实底 `.bg-white`（非模态按钮/白卡）→ `#2B2824`，维持不变；
- 实现全部落 `luzzy-theme.css`（规定 3），零新 patch。

### 思考卡片 · 全卡雾纸玻璃（v1.1.0 扩展，三方向硬门用户选定）

- **定位**：唯一一块进入消息流内部的玻璃面（方向 A 原则「玻璃只上 chrome」在此单点突破，
  经用户三方向选定「全卡雾纸玻璃」授权）；classic 主题零影响（`:root[data-theme="luzzy"]` 作用域）；
- **配方**：整卡 `rgba(var(--tw-gray-100)/.86)`（暗 `gray-200`）+ `blur(16px) saturate(1.15)`；
  边线 gray-300/.8 发丝线；`.is-open` 上游蓝调 → 暖发丝线 + 暖阴影；头部半透暖面
  （hover `gray-200/.5`，open `gray-200/.45`）；卡内步骤详情面板半透（`gray-50/.55`）让玻璃透出；
- **live 态（性能闸）**：`.is-live` 流式期间 alpha 提至 `.96` 近实底 + blur 收窄 6px +
  珊瑚描边（`primary-500/.45`）——思考卡在流式期逐帧重排，全强度 blur 的 GPU 代价不可接受，
  生成完成即恢复全玻璃；
- **降级**：`@supports not (backdrop-filter…)` → `#F5F0E8` / `#2B2824` 实底（同雾纸降级配方）；
- 选择器族：`.cot-ui.native-thinking-card`（+ `.cot-header` / `.is-open` / `.is-live` / 卡内 `.bg-gray-50`）。

## 外观独立页 · 关于页 · 供应商编辑器（v1.2.0）

### 外观独立页（外观设置全应用唯一入口）

- **入口收敛**：v1.1.0 的弹窗与设置页入口卡**全部移除**，唯一入口 = 侧边栏「外观」；
  侧栏底部簇顺序：高级组 → **外观 → 设置 → 关于（置底）**（v1.2.1 patch 019 调整，
  关于作为品牌/版本信息页置底收尾），均为视图切换（itemClass 激活态）；
  侧栏品牌字样 **LuzzyRP**（patch 019：「Luzzy」主字 gray-800 + 「RP」品牌色 primary-600
  双色同构开屏字标，下划线条随宽度 w-14）；
- **页面结构**：`management-view` 惯例（settings-page-header + max-w-2xl 卡列）——
  顶部**主题预览卡（v1.2.1 patch 019 交互化）**：色板随 `data-theme` 取色
  （`--luzzy-prev-*` 定义于 luffy-theme.css，值均为本文件既有 token；classic 展示
  上游蓝灰原色属语义正确，不受「禁新增裸 blue」约束）；luzzy 下亮/暗双卡为
  **可点按钮**（aria-pressed + 选中 ring-2 primary-400，点击直接切换 themeMode，
  200ms ease-out 过渡 + active:scale-[0.98] 按压反馈），classic 仅亮色单卡
  （经典无暗色模式）；下接界面主题 / 模式（仅 luzzy）/
  界面字体 / 对话字号四张设置卡（控件自 v1.1.0 弹窗原样迁入，绑定与持久化机制零变化）。

### 关于页（`currentView === 'about'`）

- 品牌区：logo（ext/luzzy-logo.png）+ versionName（LuzzyBridge.getVersion，降级 fallback）+
  上游 RP-Hub 基线链接 + CC BY-NC 4.0 署名声明；
- **应用内 CHANGELOG**：`ext/luzzy-changelog.js`（`tools/gen-changelog.mjs` 从仓库根
  CHANGELOG.md 生成，勿手改），进入视图时经 `renderMarkdown`（marked+DOMPurify 管线）
  渲染——用户在应用内即可读更新日志。

### 供应商编辑器（二级弹窗，z-[60]）

- 管理器每行加「编辑」；添加供应商**直接进编辑器**（占位条目先行入列，取消即移除）；
- 字段：供应商 ID（引用前缀）/ 显示名称 / 协议（openai·anthropic·gemini 三选一，徽标
  violet 大写 chip）/ API URL（placeholder 随协议联动）/ API Key（即改即存）/
  **供应商级自定义请求体**（键值行，值可空=懒编辑）；
- **模型卡**：模型 ID（请求 id，输入即热检测预设）/ 显示 ID / 上下文长度（1024000·100K·1M
  宽松解析，K=1024 M=1024²）/ 最大输出长度 / 输入模态（text·image·video 多选，teal 选中）/
  模型类型（text·image·embedding 单选，violet 选中）/ 模型级自定义请求体（JSON 或 `键:值` 懒编辑）；
- **热检测预设**：五组 id（glm-5.3 / glm-5.3-flash / deepseek-v4-pro / deepseek-v4-flash /
  deepseek-v4-flash-vision-exp）大小写不敏感、**长词优先**；只填空字段不覆盖已编辑值 +
  「已按预设填充」amber 轻提示 + 一键撤销；
- **保存即热更新**：手动模型并入合并模型列表（聊天/识图槽位立即可选，meta chip
  `1M · 文本+图像`）；改 id 时全槽位引用 `旧id::` 前缀与 key/缓存键自动重映射（确认弹窗列出
  受影响槽位）；不设「最大输入长度」字段（上下文长度即输入+输出总预算，已与用户确认）。

### 模型商徽标（v1.1.0 引入，v1.2.0 沿用）

- **语义**：跨商混用后同一模型 id 在不同商下是不同资源；所有模型展示位以
  `[商名] bareId` 标注来源；存储为 `providerId::bareId` 复合引用（裸 id = 跟随激活商）；
- **chip**：`bg-primary-50` + `primary-700` + `primary-100` 边，10px 粗体，`max-w-[45%] truncate`；
  商已删除显示 `[未知]`；选择器列表项 meta chip（v1.2.0）为 `bg-gray-100` 中性灰，
  次要于商名徽标与模型 id。

## Motion（动效令牌）

- 基线：进入 **200ms** / 退出 **140ms** / `cubic-bezier(0.23, 1, 0.32, 1)`（ease-out 系）；
  禁 `scale(0)` 起步（自 `scale(0.96)+opacity:0` 起步）；尊重 `prefers-reduced-motion`；
- **主题切换转场「纸色翻面」**：全屏遮罩以新模式底色淡入 200ms → 变量切换 → 遮罩淡出 140ms；
  reduced-motion 下直接切换；
- **气泡进入**：上移 8px + 淡入 200ms；退出淡出 140ms；
- **关于页置顶 FAB（patch 024）**：进 200ms / 退 140ms ease-out，scale(0.96)+opacity:0 起步；滚动 >240px 显隐；reduced-motion 直接呈现；
- **开屏「开卷」（patch 027，用户选定方向 B）**：掀封→纸落→界格→钤印→落墨→荧光划线→页码（≈2.3s 定格 + 450ms 淡出交还主界面）；纯 CSS transform/opacity；色值全 token、亮/暗随 data-mode 首帧自适应；reduced-motion 终帧直出 + 200ms 退场；设计存档 `docs/design/splash-v1/`（参照 Aēsop 获奖互动站，多源核实）；
- **招牌动效「荧光笔落笔」（roadmap）**：新 AI 消息落定后关键词上划过 amber 记号
  （reduced-motion 直接显示）——v1 先实现主题转场与气泡动效，落笔动效随正则/markdown
  管线单独迭代。

## Do's & Don'ts

✅ coral 稀缺使用（按钮/选中/链接/头像环）；✅ 记号克制；✅ 亮暗分别过 4.5:1；
✅ 色值只用本文件与 luzzy-theme.css 的 token，不临场发明颜色。
❌ 紫渐变 / emoji 图标 / 左彩边圆角卡 / 均匀深蓝底+霓虹 glow（GitHub-dark 套壳）；
❌ 裸改上游文件（规定 2）；❌ 触碰 built-in-content.js（规定 1）；❌ 字体走 CDN（规定 4）；
❌ **新增 UI 使用裸 `blue-*` / `indigo-*` / `violet-*` 色相工具类**（v1.2.1 起）——一律用
  `primary-*`（luzzy 主题下即品牌珊瑚陶土色，classic 主题下为上游蓝，语义正确）；
  上游存量蓝色由 luzzy-theme.css 在 `:root[data-theme="luzzy"]` 作用域内收编（不改上游
  类名，classic 保持原样），收编清单：styles.css 开屏 `.entry-transition` 家族 7 处
  （背景渐变蓝晕 / sheen / 底盘阴影 / `.entry-logo-hub` 字标与渐变 / 下划线条 /
  `.embedded-loading-spinner`）+ index.html 设置页两处渐变横幅
  （用户设置 `from-blue-500 to-indigo-600`、高级设置 `from-indigo-600 to-violet-700`）
  + **色板级收编（patch 008 v4，v1.2.2）**：tailwind.config blue/indigo 色板接入
  `rgb(var(--tw-*) / <alpha-value>)`，luzzy 主题下 `--tw-blue-*`/`--tw-indigo-*` 与
  primary 同值（toggle 选中态/叙事视角等 41+8 处上游遗留蓝全部随主题变珊瑚）；
  violet-* 保留为协议徽标功能区分色（v1.2.0 critique 备案例外）。
  ⚠ styles.css 另有 ~70 处硬编码蓝字面量（不走色板工具类）：已随 v1.2.2 在
  luzzy-theme.css 组件级收编——侧栏激活项 `.sidebar-nav-button.bg-primary-50` 家族
  （渐变/阴影/::before 竖条）、`segmented-switch__option.is-active`（叙事视角等）、
  `settings-toggle` checked 家族（自动获取模型/流式输出等含 --compact/--indigo/--solid）、
  `modal-primary-button`（弹窗主按钮）；其余低频组件清点入 v1.3.0 遗留（WORKLOG 会话 18）。

## 主题系统技术契约

- 驱动：`data-theme`（classic/luzzy）+ `data-mode`（light/dark）双属性于 `<html>`（app.js
  settings.theme/themeMode watch 设置，immediate）；
- 变量：`luzzy-theme.css` 定义 `--tw-gray-*` / `--tw-primary-*` / `--tw-blue-*` /
  `--tw-indigo-*` 为 **RGB 三元组**（classic=上游 hex 值三元组，luzzy=上表 token），
  patch 008 v4 将 tailwind.config gray/primary/blue/indigo 色板指向
  `rgb(var(--tw-*) / <alpha-value>)`——**透明度修饰符（bg-gray-50/60 等）由 JIT 自动注入
  alpha**。v2 纯 `var()` 方案的缺陷：带 alpha 的工具类被 JIT 回退成纯白（暗色白块根因，
  jsdom + CDP 双实证），禁止回退；
- 字体：`data-app-font`（luzzy/modern/serif/system）驱动 `--app-font-family`；
- 存储：settings.theme / settings.themeMode / settings.fontFamily（上游 settings 体系，
  IndexedDB 随 saveData 持久化；**不使用**独立 localStorage 键）；
- 系统栏：`applyThemeMode` → `LuzzyBridge.setSystemBarStyle`（见桥接实现）；
- 迁移：老用户（savedSettings 无 theme）→ classic；新用户默认 luzzy/light + luzzy 字体。
