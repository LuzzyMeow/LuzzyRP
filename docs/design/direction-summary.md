# LuzzyRP 新主题 · 三方向板对比汇总

> 依据 huashu-design 三方向硬门：三个差异化方向（Claude 语境内诠释）已产出真实视觉初稿，
> 用户选定后才进入执行。选定后写入 `direction-approved.md` 落档。

## 方向总览

| 方向 | 气质定位 | 色板核心 | 布局骨架 | 状态 |
|------|---------|---------|---------|------|
| A · 暖纸书房 | Claude 原味：米纸 + 烤橙，人文优雅 | 亮：米纸 #FAF9F5 / 烤橙 #D97757；暗：深暖灰 #262624 / 暖橙 #E08A5F | 卡片式分层（气泡独立悬浮卡片 + 输入区悬浮工具栏） | ✅ 完成 |
| B · 极光暖夜 | Claude 暖底 × 旧 Aurora 基因（粉紫） | 亮：暖米底 + AuroraPink #FF6EC7 / AuroraViolet #B57BFF；暗：极夜 #0E1116 + 极光渐变 | 沉浸无边（消息全宽无卡片边框 + 极光氛围光） | ⏳ 制作中 |
| C · 墨韵朱砂 | Claude 暖底 × 东方文人（宣纸/朱砂/墨色） | 亮：宣纸米 #F7F3EC + 朱砂 #C0392B；暗：墨色 #1A1815 + 朱砂暖 #D4573A | 分栏杂志（顶栏细线书页眉 + 消息左右分栏剧本排版） | ✅ 完成 |

## 交付物

| 文件 | 说明 |
|------|------|
| `docs/design/direction-a-warm-paper.html` | 方向 A 方向板（880 行，内联 CSS/JS，零外部依赖） |
| `docs/design/theme-A-light.png` / `theme-A-dark.png` | 方向 A 截图（390×844，亮/暗） |
| `docs/design/theme-C-moyun-zhusha.html` | 方向 C 方向板（656 行，内联 CSS/JS，零外部依赖） |
| `docs/design/theme-C-light.png` / `theme-C-dark.png` | 方向 C 截图（390×844，亮/暗） |
| `docs/design/theme-B-*.html/png` | 方向 B（待完成） |

## 共同约束（三方向一致）

- 亮暗双模式（data-theme 驱动，CSS 变量）
- 动效令牌：进入 200ms / 退出 140ms / ease-out `cubic-bezier(0.23,1,0.32,1)` / 按压 80-150ms / 禁 scale(0)
- 对比度 ≥4.5:1（亮暗独立校验）
- 字体：PuHuiTi-3（55/65/85）+ AlibabaSans（方向板用系统字体栈模拟）
- 界面覆盖：聊天页 / 设置页 / 侧边栏 / 色板条 / 字体展示

## 验证结果

| 方向 | 对比度（亮/暗） | 溢出 | JS 错误 | 交互 |
|------|----------------|------|---------|------|
| A | 亮 13.9:1/5.1:1 · 暗 13.5:1/6.0:1 | 无 | 零 | 全通（五页切换/抽屉/亮暗切换） |
| C | 亮 13.6:1/5.5:1 · 暗 14.7:1/7.9:1 | 无 | 零 | 全通（侧边栏/设置/亮暗切换） |

## 选定后流程

1. 用户选定方向 → 写入 `docs/design/direction-approved.md`（用户选择原话落档）
2. 更新 `DESIGN.md` 设计真源（色板/排版/布局/动效定稿）
3. 填充 `luzzy-theme.css` 主题变量（--tw-gray-* / --tw-primary-*）
4. 实施 patch 008-011（tailwind.config 色板 var() 化 / fontFamilies 扩展 / settings 默认值 / 设置页 UI）
5. 实机验证
