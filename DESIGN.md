# LuzzyRP DESIGN.md · 设计契约

> 本文件是 LuzzyRP 的**唯一设计真源**（open-design 品牌契约模式）。
> 任何 UI/UX 改动必须遵循本文档；修改本文档需要在 CHANGELOG 声明。
> 方法论：huashu-design 工作室多角色流程；交付门控：ui-ux-pro-max pro-rules。

## Overview

LuzzyRP 是移动端 AI 角色扮演应用——「每次对话，都像一本有你的小说」。
设计气质：**暖夜书房 × 极光**。像一本摊开在暖光下的小说，偶尔翻过一页极光色的书签。
关键体验词：沉浸（叙事优先）、柔软（无锐利工业感）、有生命（微动效呼吸感）。

## Colors（Aurora Dual · 唯一色源 AuroraColor.kt）

### Brand & Accent
- AuroraPink `#FF6EC7`（主行动/情感强调）
- AuroraViolet `#B57BFF`（次级强调/思考域）
- AuroraTeal `#00B8A9`（点缀/成功语义，克制使用）

### Surface
- 亮色画布 `#FAF7F2`（暖纸）；亮色表面 `#FFF8F3`
- 暗色画布 `#0E1116`（极夜）；暗色表面 `#17130F`
- AMOLED：纯黑 `#000000` + 表面 `#0A0A0A`
- 气泡语义：用户 `#FFF0F8`（暗 `#3D2033`）· AI `#FFFFFF`（暗 `#1C1F26`）
- 思考卡片：紫域 `#F6EFFF`（暗 `#261B38`）；工具卡片：蓝域 `#EFF5FF`（暗 `#18253B`）

### Text
- 亮色正文 `#201B19` / 次级 `#51443F`；暗色正文 `#EBE0DB` / 次级 `#D5C3BD`
- 正文对比度 ≥4.5:1（两套主题独立校验，禁止推断）

### Semantic
- 成功 `#2E9E63`/`#7BD8A5` · 警示 `#C77700`/`#FFC46B` · 错误 `#BA1A1A`/`#FFB4AB`

## Typography

- 字族：中文 AlibabaPuHuiTi-3，拉丁/数字 AlibabaSans（`LuzzyMixedFontFamily` 逐字符分流）
- 层级：display 57/45/36 → headline 32/28/24 → title 22/16/14 → body 16/14/12 → label 14/12/11
- 原则：聊天正文 bodyLarge(16sp/行高28sp) 保证长文阅读；标签一律 Medium；禁止 <11sp

## Layout

- 4pt 栅格（LuzzySpacing：4/8/12/16/24/32/48）；页面水平边距 16
- 图标语义尺寸令牌：iconInline 16 / iconDefault 20 / iconEmphasis 24 / iconHero 32（统一取值，禁随机值）
- 触控目标 ≥48dp（视觉更小则扩命中区）；列表错峰 stagger 20ms

## Elevation & Depth（图层层次）

- 层级 0 画布 → 1 卡片（shadow 1-2dp）→ 2 悬浮按钮/输入栏（6dp）→ 3 弹窗/BottomSheet（8-12dp + scrim 45% 黑）→ 4 Toast
- 禁止 Modifier.blur；毛玻璃观感以「表面色 × 透明度」层叠模拟
- 思考/工具卡片在气泡列**内**（内嵌 CoT 时间线），不与气泡同层

## Shapes

- 圆角令牌：小 8 / 中 12 / 大 16 / 全圆；气泡异形角（朝向说话者一侧收 4dp）
- 启动图标：AuroraPink 底 + 居中 logo（66% 安全区）

## Components

- **AuroraSurface**：统一卡片容器（按压 = 色彩过渡 + 阴影抬升，150ms，不改布局边界）
- **思考卡片**：三态（进行中脉冲/完成可展开/错误警示）；展开 expandVertically 195ms
- **工具卡片**：内嵌 CoT 列；待审批态带 批准/拒绝 双按钮
- **确认弹窗**：正文左对齐；scale 0.92→1 + fade 进入
- **空态**：插画位 + 一句引导文案（禁止裸空白）

## Motion（huashu 动效纪律）

- 令牌：进入 300ms（减速）· 交互 150ms · 退出 195ms（快进慢出不对称）
- 物理感：弹窗/卡片用 spring(0.8, 380)；列表 stagger 20ms 波次；页面转场 slide¼ + fade
- 禁忌（pitfalls）：均匀节奏、fade-only、布局跳动式按压、无 reduced-motion 考量
- 流式正文**永不打字机动画**（真流式不变性优先于一切动效）

## Accessibility

- 图标语义：装饰性图标不进无障碍树；可交互图标必须有 contentDescription
- 对比度 3:1（图标）/4.5:1（文本）；色非唯一信息载体
