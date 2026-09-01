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

## Motion（动效令牌）

- 基线：进入 **200ms** / 退出 **140ms** / `cubic-bezier(0.23, 1, 0.32, 1)`（ease-out 系）；
  禁 `scale(0)` 起步（自 `scale(0.96)+opacity:0` 起步）；尊重 `prefers-reduced-motion`；
- **主题切换转场「纸色翻面」**：全屏遮罩以新模式底色淡入 200ms → 变量切换 → 遮罩淡出 140ms；
  reduced-motion 下直接切换；
- **气泡进入**：上移 8px + 淡入 200ms；退出淡出 140ms；
- **招牌动效「荧光笔落笔」（roadmap）**：新 AI 消息落定后关键词上划过 amber 记号
  （reduced-motion 直接显示）——v1 先实现主题转场与气泡动效，落笔动效随正则/markdown
  管线单独迭代。

## Do's & Don'ts

✅ coral 稀缺使用（按钮/选中/链接/头像环）；✅ 记号克制；✅ 亮暗分别过 4.5:1；
✅ 色值只用本文件与 luzzy-theme.css 的 token，不临场发明颜色。
❌ 紫渐变 / emoji 图标 / 左彩边圆角卡 / 均匀深蓝底+霓虹 glow（GitHub-dark 套壳）；
❌ 裸改上游文件（规定 2）；❌ 触碰 built-in-content.js（规定 1）；❌ 字体走 CDN（规定 4）。

## 主题系统技术契约

- 驱动：`data-theme`（classic/luzzy）+ `data-mode`（light/dark）双属性于 `<html>`（app.js
  settings.theme/themeMode watch 设置，immediate）；
- 变量：`luzzy-theme.css` 定义 `--tw-gray-*` / `--tw-primary-*` 为 **RGB 三元组**
  （classic=上游 hex 值三元组，luzzy=上表 token），patch 008v3 将 tailwind.config 色板指向
  `rgb(var(--tw-*) / <alpha-value>)`——**透明度修饰符（bg-gray-50/60 等）由 JIT 自动注入
  alpha**。v2 纯 `var()` 方案的缺陷：带 alpha 的工具类被 JIT 回退成纯白（暗色白块根因，
  jsdom + CDP 双实证），禁止回退；
- 字体：`data-app-font`（luzzy/modern/serif/system）驱动 `--app-font-family`；
- 存储：settings.theme / settings.themeMode / settings.fontFamily（上游 settings 体系，
  IndexedDB 随 saveData 持久化；**不使用**独立 localStorage 键）；
- 系统栏：`applyThemeMode` → `LuzzyBridge.setSystemBarStyle`（见桥接实现）；
- 迁移：老用户（savedSettings 无 theme）→ classic；新用户默认 luzzy/light + luzzy 字体。
