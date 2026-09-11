# LuzzyRP DESIGN.md · 设计契约

> 本文件是 LuzzyRP 的**唯一设计真源**（open-design 品牌契约模式）。
> 任何 UI/UX 改动必须遵循本文档；修改本文档需要在 CHANGELOG 声明。
> 方法论：huashu-design 工作室多角色流程；交付门控：ui-ux-pro-max pro-rules + open-design 五维 critique。
> 依据硬性规定 9（设计 SKILL 强制条款）：任何涉及 UI/前端设计的工作必须先完整阅读 4 项 SKILL。
> 方向选定：2026-09-01 用户选定「**C · 暖幕手记 × 增强 Claude 风格**」（原话与融合细节见 `docs/design/direction-approved-v2.md`）。

## Overview

LuzzyRP 是移动端 AI 角色扮演应用——「每次对话，都像一本有你的小说」。
基于 RP-Hub 二次开发（WebView 壳）。**v1.2.3 起主题单轨化（patch 028）**：经典主题与主题
切换移除，**恒定 Luzzy「暖幕手记」**（亮/暗双模式）。

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
| 思考卡片 · 记忆召回节点（patch 031，v1.3.0） | 时间线首位 thinking 型节点「记忆召回」，详情=召回摘要（片段数+相似度区间，markdown 渲染）；循通用 step 渲染零新增样式；无卡片回复不显示（D2） |
| 供应商管理器 · 可编辑内置商（patch 029，v1.3.0） | DeepSeek 卡片开放「编辑」按钮（id 锁定态置灰）；循既有编辑器/行卡样式，零新增 token |
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

### 性能档位 · 高频面退实底（v1.3.0，用户拍板 D1，patch 034）

分数 DPR 3.25 合成层失效先验（FAB 案，WORKLOG 会话 20 补充 6）下，高频玻璃面 =
逐帧主线程重绘 + 绘制错位双重风险源。D1 档位：**高频面全部退实底，低频面保留磨砂**——

| 表面 | v1.3.0 档位 |
|------|------------|
| 聊天气泡 / typing 气泡 | 实底 `gray-100/.97`（暗 `gray-200/.97`）+ blur 0——立绘透色效果随实底退场 |
| 输入岛（`.input-island`） | 实底 0.97 + blur 0（键盘邻接面，热区漂移根除配套） |
| 侧栏抽屉（`.app-sidebar`） | 实底 0.97 + blur 0 |
| 思考卡（`.cot-ui.native-thinking-card`） | **保留磨砂**（v1.1.0 三方向选定设计），live 态既有降级不变 |
| 模态面板 / 消息操作工具条 / 弹层 | **保留磨砂**（低频面） |
| `:has(.is-live)` 流式加厚 | 随气泡实底化失效（规则保留但 blur 归零，防流式期复辟） |

- 单点变量 `--luzzy-glass-alpha / --luzzy-glass-blur` 自 v1.3.0 起仅思考卡消费；
- 配套合成层瘦身：`.glass-stabilize` / scroll-reveal 三族 `will-change: auto`
  （渲染窗口常驻 40-60 个候选层 → 按需瞬时创建）；
- 视觉回滚点：patch 034 单 commit，可独立回退。

## 外观独立页 · 关于页 · 供应商编辑器（v1.2.0）

### 外观独立页（外观设置全应用唯一入口）

- **入口收敛**：v1.1.0 的弹窗与设置页入口卡**全部移除**，唯一入口 = 侧边栏「外观」；
  侧栏底部簇顺序：高级组 → **外观 → 设置 → 关于（置底）**（v1.2.1 patch 019 调整，
  关于作为品牌/版本信息页置底收尾），均为视图切换（itemClass 激活态）；
  侧栏品牌字样 **LuzzyRP**（patch 019：「Luzzy」主字 gray-800 + 「RP」品牌色 primary-600
  双色同构开屏字标，下划线条随宽度 w-14）；
- **页面结构**：`management-view` 惯例（settings-page-header + max-w-2xl 卡列）——
  **助手 8 页为「扁平单页制」（2026-09-11 用户指定）**：每页都是侧栏一级入口，彼此无上下级；
  页面头左键一律汉堡→侧栏（无返回箭头）；页与页之间无推进关系（会话页点一条会话 = 切到同为
  一级的「对话」页）；原「助手管理」二级页已并入会话页折叠卡。
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
- **侧栏折叠组（在线 / 高级 / 助手，v1.5.0 性能对齐）**：上游 `0.32s cubic-bezier(.22,1,.36,1)`
  → 本项目令牌 **进入 200ms / 退出 140ms / `cubic-bezier(0.23,1,0.32,1)`**，chevron 同拍。
  真机 120Hz 实测（CDP 帧事件追踪 + `dumpsys gfxinfo`）：主线程不是瓶颈（每帧 layout 0.27ms +
  recalc 0.75ms + paint 0.6ms，rAF 稳定 8.3ms）；瓶颈是 **GPU 栅格 ~5ms/帧 > 120Hz 的 8.33ms
  预算**。收敛窗口后动画帧数 38 → 24，单次展开掉帧绝对数 ~1.4 → ~0.8（同场交替 A/B：19/20 → 8/8）；
  **掉帧率仍约 2~4%，本改动不消除**（GPU 地板限制）。落地 `ext/luzzy-theme.css`「动效」段；
  独立合成层 / `contain: paint` 两种试探经成对交替测量**无收益**，勿再引入；
- **开屏「开卷 · 门扉」（patch 027 v3，用户决策）**：v3 起为门扉交互——构图淡入 →
  加载进度条自左向右（coral→amber 笔尖渐变）→ 「沉溺」按钮浮现（呼吸微动）并**等待点击**；
  点击转场 = 轻微眩晕（微摆）+ 泡泡上浮（水下隐喻呼应「沉溺」）+ 中心放大坠入主界面；
  **v1.3.0（patch 034）起转场移除 filter:blur**——全屏层 filter 逐帧重栅格（DPR 3.25
  ≈3510×7800 像素/帧）为掉帧主源，失焦感由 scale+rotate+opacity 表达，泡泡层随之脱离
  filter 父层；原掀封叙事与自动退场移除。行为脚本 `ext/luzzy-splash.js`（状态机 ~40 行，
  animationend + 兜底收殓）；reduced-motion 近零时长直出可点态、转场退化 220ms 淡出。
  设计存档 `docs/design/splash-v1/`；
  **v1.5.0 追加「遮挡期渲染抑制」（用户 2026-09-10 报告「开屏最开始卡一下」）**：
  入场动画本身已在合成层上（LayerTree：accelerated transform/opacity 实证），掉帧不在动画机制，
  而在**被开屏完全遮挡的应用主体仍在渲染** + 启动期 GPU 瞬时负载。对策：遮挡期
  `#app { visibility: hidden }`（**保留布局**，避免应用尺寸测量读到 0），入场 1.2s 先解除
  （应用在开屏仍不透明时完成首绘）、点击时再由 `.lsp-dive` 原生解除。冷启动实测：UI 帧时中位
  **17ms → 7ms**、GPU 90 分位 **15ms → 6~7ms**、legacy 掉帧 **80.69% → 4.1%**。视觉零差异
  （遮挡期本就看不见主体）；`:has()` 不支持时整条不生效（零风险降级）；
- **招牌动效「荧光笔落笔」（roadmap）**：新 AI 消息落定后关键词上划过 amber 记号
  （reduced-motion 直接显示）——v1 先实现主题转场与气泡动效，落笔动效随正则/markdown
  管线单独迭代。

## Do's & Don'ts

✅ coral 稀缺使用（按钮/选中/链接/头像环）；✅ 记号克制；✅ 亮暗分别过 4.5:1；
✅ 色值只用本文件与 luzzy-theme.css 的 token，不临场发明颜色。
❌ 紫渐变 / emoji 图标 / 左彩边圆角卡 / 均匀深蓝底+霓虹 glow（GitHub-dark 套壳）；
❌ **高频表面新增 backdrop-filter 或常驻 will-change**（v1.3.0 性能档位）——玻璃只上
  低频 chrome（模态/工具条/思考卡）；滚动/流式路径上的表面一律实底 + `will-change: auto`；
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

## 助手原生页（v1.5.0 · Compose 原生 UI）

> **方向选定：A · 卷宗（Ledger）**（2026-09-09 用户拍板；三方向产出与截图见
> `docs/design/assistant-v1/`，决策记录见 `docs/design/direction-approved-assistant.md`）。
> 本页是**原生 Compose**，不经过 WebView，但**必须复用同一套 token**——色板/字体/圆角/
> 动效一律取自本文档，禁止临场发明（硬性规定 9 第 3 步）。

### 信息架构

- **无一级导航**：会话即家（首屏 = 会话列表）；
- 二级收进**右侧抽屉**（记忆 / 技能 / MCP / 工作区 / 终端 / 设置），抽屉右滑 12dp + 淡入 200ms、返回 140ms；
- 助手切换：列表页头部条「全部助手」→ 切换器；会话页头部条显示当前助手。

### 布局与密度

| 项 | 值 |
|----|-----|
| 内容边距 | 16dp（单列） |
| 会话行 | 68dp；单屏 7 条 |
| 记忆行 | 56dp；单屏 5 条 |
| 气泡圆角 | 16dp |
| 输入岛圆角 | 22dp；发送键 44dp（coral 实心圆） |
| 正文 / 行高 | 14px / 1.65；caption 12px |
| display 尺度 | Lora 17dp（克制） |

### 组件映射

| 组件 | 规格 |
|------|------|
| AI 气泡 | `surface-soft` 底 + hairline 边 |
| 用户气泡 | `#F1E3D9` 底 + coral-300 边 |
| 思考卡 | `surface-card` + hairline；执行中 live coral 描边；默认折叠 |
| 工具卡 | 单行 52dp（等宽工具名 + 状态 pill + 耗时）；默认折叠 |
| 步骤组 | 头部「N 步 · 总耗时」+ 缩进步骤行；默认折叠 |
| 输入岛 | `surface-card` 实底（**禁 backdrop-filter**，v1.3.0 性能档位） |
| 记忆行 | 类型标签 + 正文 + `agent/user · 时间` + 相似度条（`primary-500`） |

#### 聊天页组件像素规格（2026-09-10 补登记）

> 上表只有色与结构，缺少可验收的像素值，导致实现层临场取值（用户气泡偏离契约、输入岛用文字字符
> 当图标、气泡最大宽无出处）。此表为**唯一取值来源**，改值必须先改本表。

| 组件 | 规格 |
|------|------|
| **页头** | **管理页 `LedgerPageHeader` 同骨架**（§管理页组件规范 #1）：行高 **48dp**（`h-12`）+ 水平 **16dp**（`.management-view p-4`）+ 行下 **16dp**（`mb-4`）；**canvas 底、无渐变、入流不覆盖消息流**。左＝汉堡（40dp 触控区 + 24dp 图标 `LedgerIcons.Menu`，`muted`）；中＝头像 36dp 圆（`accentSoft` 底 + `accentButton` 字 + `hairline` 边，替管理页的 24dp 前置图标位）+ 名称 **20sp Bold `body`** + 会话标题 **12sp `mutedSoft`** + chevron 16dp `mutedSoft`；右＝**图标按钮 40dp 方钮**（`card` 底 + `hairline` 边 + `rounded-xl`，§#2）。**2026-09-10 P2 顶栏语言统一**：原「112dp 黑渐隐覆盖层 + 白字 + 1.6dp 手绘图标」整体取消（那是上游给压在角色背景图上的聊天页做可读性用的，助手下无图可压） |
| 用户气泡 | 底 **`#F1E3D9`**（暗色 `#3A2E26`）+ 边框 **coral-300**（暗色同色降饱和）1dp；圆角 **16dp**；内边距 14×10dp；**最大宽 320dp**；右对齐 |
| AI 气泡 | 底 `surface-soft`（`#F5F0E8`）+ `hairline` 边 1dp；圆角 16dp；内边距 14×10dp；**最大宽 320dp**；左对齐 |
| 气泡间距 | 同一轮内 8dp；**轮间距 32dp**（原 48dp 系复制上游 `space-y-12`，那是为角色头像留白，助手无头像故收敛）——**待真机目测确认** |
| 输入岛 | `surface-card` 实底（禁 `backdrop-filter`）+ `hairline` 边；圆角 **22dp**；内边距 start 4 / end 6 / v 6dp；左侧附件 **44dp 触控区 + 24dp 图标**（`LedgerIcons.Plus`）；发送键 **44dp coral 实心圆 + 白色 24dp 图标**（`LedgerIcons.Send`，上游聊天页纸飞机 `w-5 h-5`）；**禁止用 Text 字符代替图标** |
| 审批卡 / 澄清卡 | `card` 底 + `rounded-2xl` 16dp + 内边距 16dp；主按钮＝coral 实心（**仅弹窗 CTA**），次级＝`card` 底 + `accentSoft` 边；高 32dp |
| 空态 | **全助手唯一实现**：居中列，间距 8dp；主文 14sp `muted`、副文 12sp `mutedSoft`（**深字**，配 canvas；聊天页顶栏的白字版已删除） |

> **2026-09-10 P2「顶栏语言统一」落地（用户免除三方向门）**：审查档 A1 的两套顶栏语言至此收敛为
> 一套——聊天页改用**纸面页头**（上表首行），112dp 黑渐隐整体取消。用户原话「顶栏语言统一，
> 此任务本次免去三方向」，属 huashu-design「唯一豁免」第 1 条（用户本次会话明说跳过），
> 已落档 `docs/design/direction-approved-assistant.md`；本表即该决策的设计真源。

### 动效

进入 200ms / 退出 140ms / `cubic-bezier(0.23,1,0.32,1)`；禁 `scale(0)` 起步（用 `scale(0.96)` + 透明度）；
尊重系统「减少动态效果」；高频滚动/流式路径上的表面一律实底，玻璃只上低频 chrome。

**页面交接（2026-09-11 用户指定，统一「所有页之间」的转场）**：

| 项 | 规格 |
|----|------|
| 编排 | **侧栏左移 + 页内容交叉淡化**，两者**同帧起跑、同时结束**——用户原话：「当菜单栏完全收起动画执行完毕屏幕不可见菜单栏时，淡化效果结束，完全呈现新页」 |
| 时长 | 单值 `--lsp-handoff-ms`（`ext/luzzy-theme.css`）= **200ms**（进入令牌）：侧栏 `translate3d(-104%)`、`.mobile-overlay` 遮罩、旧页淡出、新页淡入**共用同一个值**，改一处全变 |
| 曲线 | `cubic-bezier(0.23,1,0.32,1)`（ease-out；UI 过渡**禁 ease-in**） |
| 旧页 | **原位快照层**（`.lsp-view-out`，非克隆节点）：`display` 被上游 `v-show` 置 none 时以 `!important` 拉回 + `absolute inset:0` 浮在新页之上，只跑 `opacity`（合成层、零重排） |
| 新页 | 自 **0.35** 淡入到 1（两页同时半透明会往底色发灰的「灰陷」，抬高起点保持亮度连续） |
| 实现约束 | 两层一律用 **animation 而非 transition**（类加在视图切换的同一帧，transition 找不到「前一帧的值」；animation 加类即起跑，才可能与侧栏严格同帧） |
| 助手入口 | 同编排的助手版：WebView 侧侧栏左收 + 原生覆盖层 `alpha 0→1`（同一令牌）；**覆盖层淡入期间保留 WebView 绘制**（`MainActivity` 过渡结束才停绘），被淡化的才是真的「旧页」 |

### 字体（硬性规定 4）

- 正文/UI：**Alibaba PuHuiTi 3.0**（Regular/Medium/Bold，本地 TTF）；
- display：**Lora**（拉丁）+ **PuHuiTi**（中文）经 `Typeface.CustomFallbackBuilder` 串成逐字形回退链
  （API 29+；26-28 退化为 Lora + 系统衬线）；
- **平台偏差**：Compose 的 `FontFamily` 不做逐字形回退（与 CSS `font-family` 栈语义不同），
  故正文主族取 PuHuiTi 而非 AlibabaSans——观感与 Web 端一致，但拉丁字形来自 PuHuiTi；
- 转换工具 `tools/assistant-fonts.py`（woff2 → TTF）；**禁止**运行时 CDN（硬性规定 4）；
- 体积：8 枚 TTF 约 21.2MB（用户 2026-09-09 确认）；
- **等宽（2026-09-10 补登记）**：终端输出、工具名、模型 ID 等「必须逐字符对齐」的场合用
  `FontFamily.Monospace`（系统 Droid Sans Mono 系）。**假设**：不再单独打包等宽 TTF——
  等宽仅出现在终端页与少量标识文本，为它再增 ~1-2MB 不划算；若后续终端成为高频页面再议。
  中文在等宽族下回落系统字体，属**已知且接受**的偏差（终端内容以拉丁命令行为主）。

### 管理页组件规范（v1.5.0 · 与上游同构）

> **起因（用户 2026-09-09 P0 反馈）**：「助手页下各个页面的组件设计与摆放方式等均只是与其他页相似，
> 而不是做到同一个设计理念」。根因：本章此前只规范了**聊天页组件**，管理页零规范，导致实现层
> 临场发明组件（违反硬性规定 9 第 3 步）。
>
> **总原则：不发明组件。** 助手原生页的每个组件 = **上游结构（Tailwind 类名逐项复制）
> + Luzzy token（`ext/luzzy-theme.css` 覆盖色）**。本节每条规格都标注上游出处，实施时**不得改动**。

#### 换算基线

WebView 视口即设备 dp，故 **1 CSS px = 1 dp = 1 sp**。

| 项 | 上游出处 | Compose 规格 |
|----|----------|--------------|
| 页面内边距 | `.management-view` `p-4 md:p-6` | 16dp |
| 页面内容宽 | `max-w-3xl md:max-w-5xl mx-auto` | `fillMaxWidth`（移动端无上限） |
| 卡片间距 | `space-y-4 md:space-y-8` | 16dp |
| 页面头下间距 | `mb-4 md:mb-6` | 16dp |
| 圆角 | `rounded-lg` 8 / `rounded-xl` 12 / `rounded-2xl` 16 | 8 / 12 / 16 dp |
| 阴影 | `shadow-sm` | `0 1dp 2dp rgba(0,0,0,0.05)` |
| 字号 | `text-xs` 12 / `text-sm` 14 / `text-base` 16 / `text-xl` 20 / `text-2xl` 24 | 12 / 14 / 16 / 20 / 24 sp |
| 字重 | `font-medium` 500 / `font-semibold` 600 / `font-bold` 700 | Medium / SemiBold / Bold |
| 按下反馈 | `active:scale-95` | 0.95 缩放（press） |
| 过渡 | `transition-all` 150–200ms | 200ms 进 / 140ms 出，`cubic-bezier(.23,1,.32,1)` |

#### 新增 token：卡片面 `card`

上游卡片是 `bg-white`（`ext/luzzy-theme.css` 仅在暗色覆盖为 gray-100），
**不是** `surface-card`（gray-200 = `border-gray-200`，上游用作**边框**）。

| token | 亮色 | 暗色 | 出处 |
|-------|------|------|------|
| `card`（卡片填充） | `#FFFFFF` | `#201E1B`（gray-100） | `bg-white` / 暗色覆盖 |
| 卡片边框 | `hairline` `#E6DFD8`… 实为 `border-gray-200` `#EFE9DE` | `border-gray-300` `#3E3A34` | `border border-gray-200` |

#### 组件清单（逐项对齐上游）

| # | 组件 | 上游出处与规格 | Compose 规格 |
|---|------|----------------|--------------|
| 1 | **页面头** | `.settings-page-header`：`flex items-center justify-between mb-4`；左＝汉堡（`.mobile-menu-button` `w-6 h-6` `text-gray-600` `mr-3`）+ `h2 text-xl font-bold text-gray-800 flex items-center` + 前置图标 `w-6 h-6 mr-2 text-primary-600`；右＝按钮组 `flex gap-2` | `Row`(高 48dp, mb 16dp)；图标 24dp；标题 20sp Bold `body`；右侧动作区。**左键二选一（2026-09-11 定稿）**：助手 8 个页面一律 `onMenu`＝汉堡 → 侧栏（它们都是侧栏一级入口、彼此无上下级，**没有「上一级」可返**）；`onBack`＝返回箭头只留给「确实从别处推进来」的场景 |
| 2 | **图标按钮** | `p-2.5 bg-white rounded-xl border border-gray-200 shadow-sm active:scale-95`（危险态 `text-red-600`） | 40dp 方钮，`card` 底 + `hairline` 边 + `rounded-xl`(12dp) |
| 3 | **分组标题** | `.settings-section-heading`：12px / 700 / `uppercase` / `letter-spacing .05em` / `#9ca3af`(gray-400) / `mb-4` | 12sp Bold，字距 0.05em，`hairlineStrong`，mb 16dp |
| 4 | **卡片** | `bg-white rounded-2xl border border-gray-200 shadow-sm mb-6`；折叠容器变体 `bg-white/70 backdrop-blur-sm p-1 rounded-2xl border border-gray-200 shadow-sm mb-4 overflow-hidden` | `card` 底 + `hairline` 边 + `rounded-2xl`(16dp) + shadow-sm；折叠容器内边距 4dp |
| 5 | **折叠行** | `w-full flex justify-between items-center px-4 py-3 rounded-xl font-bold`（展开态 `bg-primary-50 text-primary-700`）；左＝图标方块 `p-1.5 rounded-lg mr-3 bg-primary-100 text-primary-600`（内 `w-4 h-4`）；右＝状态文字 `text-xs font-bold text-primary-600` + `w-5 h-5` chevron（展开旋转 180°） | 高 48dp，`rounded-xl`(12dp)，图标方块 28dp `rounded-lg`(8dp) `accentSoft`/`accentButton`，右侧状态 12sp + chevron 20dp |
| 6 | **折叠面板** | 上游 `.settings-collapse`：`grid-template-rows 0fr↔1fr` + `opacity`，`0.36s cubic-bezier(.22,1,.36,1)`（**上游值，本项目不采用**）；内容 `px-4 pb-4 pt-3 border-t border-gray-100` | `AnimatedVisibility`(**展开 200ms / 收起 140ms**，`CubicBezierEasing(.23,1,.32,1)`——§Motion 令牌)；内容顶边 `hairline` |
| 7 | **开关** | `.settings-toggle`：**44×24dp** pill，底 `gray-200`，滑块 20dp 白底 `1px gray-300` 边、位移 2dp；选中底 `primary-600`、滑块 `translateX(100%)`；过渡 `all .2s`；`.settings-toggle--compact` 同形 | 44×24dp 自绘（**不用 Material3 Switch**），滑块 20dp，选中 `accentButton` |
| 8 | **按钮** | 次级 `inline-flex items-center text-xs px-3 py-1.5 bg-white hover:bg-primary-50 text-primary-700 rounded-lg border border-primary-200 font-medium active:scale-95 shadow-sm`；主按钮 `.modal-primary-button` `primary-600` 底白字 | 高 32dp，`px-12dp`，`rounded-lg`(8dp)，12sp Medium；次级＝`card` 底 + `accentSoft` 边；主＝`accentButton` 底白字 |
| 9 | **输入框** | `w-full bg-gray-50/60 border-2 border-gray-100 rounded-xl px-4 py-3 text-gray-800 focus:bg-white focus:border-primary-500` | 高 44dp+，`rounded-xl`(12dp)，`surfaceSoft` 底 + 2dp `hairline` 边，聚焦 `accentGraphic` 边 |
| 10 | **搜索框** | `w-full bg-white/80 border border-gray-200 rounded-2xl pl-10 pr-12 py-2.5 text-sm`（左侧 40dp 图标位） | 高 40dp，`rounded-2xl`(16dp)，前置 16dp 图标 |
| 11 | **列表行** | 卡片内行 `rounded-xl px-4 py-3`，行间 `divide-y`/`gap-0.5`；主文 `text-sm font-semibold`，副文 `text-xs text-gray-500` | 行高 ≥48dp，`rounded-xl`，主 14sp SemiBold / 副 12sp `mutedSoft` |
| 12 | **空态** | 图标（`w-8 h-8` `text-gray-300`）+ 主文 `text-sm text-gray-500` + 副文 `text-xs text-gray-400` + 可选动作按钮 | 居中列，间距 8dp |
| 13 | **状态徽标** | `text-xs px-2 py-0.5 rounded-full bg-primary-50 text-primary-700`（成功 `bg-green-50 text-green-700` 等） | 12sp，`px-8dp`/`py-2dp`，`rounded-full` |
| 14 | **分段选择器** | `.segmented-switch`：外框 `rounded-xl bg-gray-100 p-1`，选中滑块 `bg-white shadow-sm`（Luzzy 覆盖为 primary） | 外框 `surfaceSoft` `rounded-xl` p-4dp；滑块 `card` + shadow |
| 15 | **图标体系** | 全站 24dp 线性 SVG：`stroke-width 2`、`stroke-linecap/linejoin round`、`viewBox 0 0 24 24`；页面图标 `text-primary-600`，次级 `text-gray-400/500` | 24dp 线性图标（**复用上游 SVG `d` 路径 → `res/drawable/ic_lz_*.xml` VectorDrawable**，经 `LedgerIcons` 取用），线宽 2dp，圆头圆角 |

> **2026-09-10 修订（A2/A3 两条）**：
> ① **2dp 描边适用于全部助手页，含聊天页**；② **折叠时长以 §Motion 令牌为准**：会话 48 实测后已把
> Web 侧栏折叠从上游 `0.36s cubic-bezier(.22,1,.36,1)` 收敛到 **200/140ms + `.23,1,.32,1`**，
> 管理页折叠面板（第 6 项）为**追认同值**，消除「Web 侧栏 200ms ／ 助手内 360ms」的节奏分裂。
> 上游值仅保留在「上游出处」列作对照，**不得再用于实现**。
>
> 落地方式（比原计划更彻底）：聊天页原 `ChatIcons.kt`（Canvas 手绘 1.6/1.5dp）**整文件删除**，
> 顶栏/输入岛一律改用与管理页同一套 `LedgerIcons`（VectorDrawable）——两套描边从**来源上**消灭，
> 而非逐处改数值。新增图标：`Menu`（上游 `#icon-menu`）、`Send`（上游聊天页发送纸飞机）。

#### 页面骨架（所有管理页统一）

```
LedgerPageHeader(icon, title, actions)      // 20sp Bold + 24dp 前置图标 + 右侧图标按钮
  └ 内容 Column(间距 16dp)
      ├ LedgerSectionHeading("检索")         // 12sp Bold uppercase
      ├ LedgerCard { LedgerCollapseRow(...) { 面板 } }
      ├ LedgerCard { ... }
      └ LedgerEmptyState(...)
```

#### 验收方式

1. 组件尺寸单测（开关 44×24、按钮高 32、页面头 48、圆角 8/12/16）；
2. 真机截图与上游同页并排比对（颜色/间距/圆角/字重）；
3. `DESIGN.md` 本节即实施清单，**新增组件必须先在此登记**。
