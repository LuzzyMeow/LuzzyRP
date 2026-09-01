# LuzzyRP DESIGN.md · 设计契约

> 本文件是 LuzzyRP 的**唯一设计真源**（open-design 品牌契约模式）。
> 任何 UI/UX 改动必须遵循本文档；修改本文档需要在 CHANGELOG 声明。
> 方法论：huashu-design 工作室多角色流程；交付门控：ui-ux-pro-max pro-rules + open-design 五维 critique。
> 依据硬性规定 9（设计 SKILL 强制条款）：任何涉及 UI/前端设计的工作必须先完整阅读 4 项 SKILL。
> 方向选定：2026-09-01 用户选定「A · 暖纸书房」（见 docs/design/direction-approved.md）。

## Overview

LuzzyRP 是移动端 AI 角色扮演应用——「每次对话，都像一本有你的小说」。
基于 RP-Hub 二次开发（WebView 壳），主题系统支持：**经典**（原版 RP-Hub 浅色）与 **Luzzy 新主题「暖纸书房」**（亮/暗双模式）。

**设计气质**：暖纸书房——像一本摊开在暖光下的书。米纸底 + 烤橙 accent + 暖灰文字层级，长文本阅读像翻一本暖调的书；沉浸感来自纸感底色与克制的卡片分层，而非高饱和刺激。

**关键体验词**：温暖（暖调色相家族）、沉浸（长读不疲劳）、克制（accent 稀缺使用）、有物理感（卡片分层如纸张堆叠）。

## Colors

### 经典主题（classic = 原版 RP-Hub 色值，默认变量）

- gray 50-900：`#f9fafb` → `#111827`（中性灰阶）
- primary 50-900：`#eff6ff` → `#1e3a8a`（蓝色系）
- 亮色画布 `#f9fafb`；表面 `#ffffff`；文字 `#111827` 系

### Luzzy 新主题「暖纸书房」（亮/暗双模式）

#### 亮色

| Token | 值 | 用途 |
|-------|-----|------|
| bg | `#FAF9F5` | 米纸底（画布） |
| surface | `#F1EDE3` | 表面（次级卡） |
| surface-2 | `#E8E0D2` | 表面强（用户气泡） |
| card | `#FFFFFF` | 卡片（AI 气泡/设置卡） |
| hairline | `#E6DFD8` | 描边 |
| ink | `#2A2826` | 主文字 |
| body | `#3D3A36` | 正文 |
| muted | `#6C6A64` | 次级文字（≥4.5:1） |
| accent | `#D97757` | 烤橙（图形 accent：头像/开关/标签） |
| accent-strong | `#B85C3E` | 烤橙深（按钮底，白字 4.53:1） |
| accent-deep | `#A8543A` | 烤橙最深（accent 文字，4.99:1） |
| on-accent | `#FFFFFF` | accent 上文字 |

#### 暗色

| Token | 值 | 用途 |
|-------|-----|------|
| bg | `#262624` | 深暖灰底 |
| surface | `#32302D` | 表面 |
| surface-2 | `#3A3733` | 表面强（用户气泡） |
| card | `#2E2C29` | 卡片 |
| hairline | `#3F3C37` | 描边 |
| ink | `#F5F1EA` | 主文字（暖白） |
| body | `#D8D2C8` | 正文 |
| muted | `#A6A29A` | 次级文字 |
| accent | `#E08A5F` | 暖橙 accent |
| accent-strong | `#E08A5F` | 按钮底（深字 5.75:1） |
| accent-deep | `#E8A37C` | accent 文字亮化 |
| on-accent | `#262624` | accent 上文字 |

#### 色板纪律

- **烤橙双档策略**：accent `#D97757` 仅作图形 accent（头像/开关/标签），文字与按钮用深一档 `#B85C3E`/`#A8543A`——保住品牌色同时过 4.5:1 底线（#D97757 直接做文字仅 2.96:1）。
- **暗色暖调**：`#262624` 是暖调而非冷黑，与亮色共享同一色相家族，双模式切换无跳变。
- 对比度验证：亮色正文 13.9:1、次级 5.1:1、accent 文字 5.0:1、按钮白字 4.5:1；暗色正文 13.5:1、次级 6.0:1、accent 5.8:1——全部 ≥4.5:1。

## Typography

- 中文：Alibaba PuHuiTi 3.0（55-Regular / 65-Medium / 85-Bold，本地打包）
- 拉丁/数字：AlibabaSans（全 6 字重，本地打包，tabular-nums）
- 衬线（对话正文可选）：Lora（本地打包）
- 字体设置选项：经典（modern/serif/system）/ Luzzy 默认（PuHuiTi + AlibabaSans）
- 新用户默认：Luzzy 默认字体
- 字重映射：PuHuiTi 55→400 / 65→500 / 85→700

## Layout

- **卡片式分层骨架**（方向 A 结构签名）：
  - 消息气泡 = 独立悬浮卡片（AI 白卡 / 用户米卡，圆角 14px + 单侧 5px 收角，阴影 --shadow-1）
  - 输入区 = 悬浮工具栏（18px 圆角浮卡 + 三层阴影 --shadow-3）
  - 顶栏 = 毛玻璃（--bar-bg 半透明 + backdrop-blur）
- 4pt 栅格（8/12/16/24/32/48）；页面水平边距 16
- 触控目标 ≥48dp（视觉更小则扩命中区）

## Elevation & Depth

- 层级 0 画布（--bg）→ 1 卡片（--shadow-1）→ 2 悬浮工具栏（--shadow-2）→ 3 弹窗/BottomSheet（--shadow-3 + scrim）→ 4 Toast
- 阴影用暖调 rgba(42,40,38,*)（亮）/ rgba(0,0,0,*)（暗），禁冷灰阴影
- 毛玻璃观感以「表面色 × 透明度」层叠模拟

## Shapes

- 圆角令牌：小 8 / 中 12 / 大 14（气泡）/ 全圆（胶囊按钮）
- 气泡异形角：朝向说话者一侧收 5px
- 输入区悬浮工具栏：18px 圆角

## Components

- **消息气泡**：AI 白卡（--card）/ 用户米卡（--surface-2），阴影 --shadow-1，进入动画 200ms ease-out（opacity + translateY 4px）
- **思考卡片**：--surface 底 + --hairline 描边，进行中脉冲点（--thinking-dot）
- **输入区悬浮工具栏**：--card 底 + --shadow-3，工具按钮（发送图片/自动生图/剧情分支/切换模型）accent 色 hover
- **发送按钮**：--accent-strong 底 + --on-accent 字，按压 scale(.96) 100ms
- **设置卡片**：--card 底 + --hairline 描边 + 圆角 16，区块头渐变（--grad-head：accent → accent-strong）
- **开关**：accent 色激活态
- **空态**：插画位 + 一句引导文案（禁止裸空白）

## Motion（huashu 动效纪律 + open-design 动画哲学）

- 令牌：进入 200ms / 退出 140ms / ease-out `cubic-bezier(0.23, 1, 0.32, 1)`
- 按压反馈：100ms scale(.96) + opacity .75
- 禁 `scale(0)` 起步（页面切换用 translateY(8px) 起步）
- 主题切换转场：遮罩淡入 200ms（方案 B，见 docs/design/theme-motion-plan.md）
- 流式正文**永不打字机动画**（RP-Hub 原生流式优先）
- `prefers-reduced-motion` 兜底：transition/animation 时长归零

## Accessibility

- 正文对比度 ≥4.5:1（亮暗两套独立校验，禁止推断）
- 触控目标 ≥48dp（Android）
- 图标语义：装饰性图标不进无障碍树；可交互图标必须有 contentDescription
- 支持 reduced-motion

## 主题系统技术契约（见 docs/design/theme-tech-plan.md）

- `data-theme`（classic / luzzy）+ `data-mode`（light / dark）双属性驱动
- 色板变量：`--tw-gray-*` / `--tw-primary-*`（tailwind.config 改 var() 引用，patch 008）
- 存储：扩展层独立键 `luzzy_theme` / `luzzy_theme_mode`（localStorage）
- 新用户默认：luzzy + light；老用户保留原设置
