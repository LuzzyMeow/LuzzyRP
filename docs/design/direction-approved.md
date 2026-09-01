# direction-approved.md · 方向选定落档

> Gate 文件协议（huashu-design）：三方向真实视觉展示 + 用户选择原话记录。
> 文件不在 = 环节没做。本文件是主题方向选定的唯一凭证。

## 选定记录

- **日期**：2026-09-01
- **展示内容**：三方向板（A 暖纸书房 / B 极光暖夜 / C 墨韵朱砂），各含亮/暗截图与可交互 HTML
- **用户选择原话**：「A」
- **选定方向**：**A · 暖纸书房**（Claude 原味：米纸底 + 烤橙 accent，卡片式分层布局）

## 方向板交付物（选定依据）

| 文件 | 说明 |
|------|------|
| `docs/design/direction-a-warm-paper.html` | 方向 A 方向板（900 行单文件，内联 CSS/JS，零外部依赖） |
| `docs/design/direction-a-light.png` | 亮色截图（390×844） |
| `docs/design/direction-a-dark.png` | 暗色截图（390×844） |
| `docs/design/theme-C-moyun-zhusha.html` | 方向 C 方向板（备选存档） |
| `docs/design/theme-C-light.png` / `theme-C-dark.png` | 方向 C 截图（备选存档） |

## 方向 A 设计要点（实施依据）

### 色板（亮）

| Token | 值 | 用途 |
|-------|-----|------|
| --bg | #FAF9F5 | 米纸底（画布） |
| --surface | #F1EDE3 | 表面（次级卡） |
| --surface-2 | #E8E0D2 | 表面强（用户气泡） |
| --card | #FFFFFF | 卡片（AI 气泡/设置卡） |
| --hairline | #E6DFD8 | 描边 |
| --ink | #2A2826 | 主文字 |
| --body | #3D3A36 | 正文 |
| --muted | #6C6A64 | 次级文字（≥4.5:1） |
| --accent | #D97757 | 烤橙（图形 accent：头像/开关/标签） |
| --accent-strong | #B85C3E | 烤橙深（按钮底，白字 4.53:1） |
| --accent-deep | #A8543A | 烤橙最深（accent 文字，4.99:1） |
| --on-accent | #FFFFFF | accent 上文字 |

### 色板（暗）

| Token | 值 | 用途 |
|-------|-----|------|
| --bg | #262624 | 深暖灰底 |
| --surface | #32302D | 表面 |
| --surface-2 | #3A3733 | 表面强（用户气泡） |
| --card | #2E2C29 | 卡片 |
| --hairline | #3F3C37 | 描边 |
| --ink | #F5F1EA | 主文字（暖白） |
| --body | #D8D2C8 | 正文 |
| --muted | #A6A29A | 次级文字 |
| --accent | #E08A5F | 暖橙 accent |
| --accent-strong | #E08A5F | 按钮底（深字 5.75:1） |
| --accent-deep | #E8A37C | accent 文字亮化 |
| --on-accent | #262624 | accent 上文字 |

### 关键决策

1. **烤橙双档策略**：spec 主色 #D97757 仅作图形 accent，文字与按钮用深一档 #B85C3E/#A8543A——保住品牌色同时过 4.5:1 底线（#D97757 直接做文字仅 2.96:1）。
2. **卡片式分层骨架**：消息气泡独立悬浮卡片（AI 白卡/用户米卡，圆角 14px + 单侧 5px 收角），输入区悬浮工具栏（18px 圆角 + 三层阴影），顶栏毛玻璃。
3. **暗色暖调**：#262624 是暖调而非冷黑，与亮色共享同一色相家族，双模式切换无跳变。
4. **动效令牌**：进入 200ms / 退出 140ms / ease-out cubic-bezier(0.23,1,0.32,1) / 按压 100ms scale(.96) / 禁 scale(0) / prefers-reduced-motion 兜底。
5. **对比度验证**：亮色正文 13.9:1、次级 5.1:1、accent 文字 5.0:1、按钮白字 4.5:1；暗色正文 13.5:1、次级 6.0:1、accent 5.8:1——全部 ≥4.5:1。

## 实施范围（选定后）

1. DESIGN.md 设计真源填充（方向 A 定稿）
2. luzzy-theme.css 主题变量填充（--tw-gray-* / --tw-primary-* 映射方向 A 色板）
3. patch 008：tailwind.config 色板 var() 化
4. patch 009：fontFamilies 增加 luzzy 选项
5. patch 010：settings 默认值（theme=luzzy / fontFamily=luzzy）
6. patch 011：设置页 UI（主题选择 + 亮暗切换）
7. 实机验证
