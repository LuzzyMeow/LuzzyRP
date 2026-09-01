# LuzzyRP 主题系统 · 技术方案（草案）

> 依据 huashu-design 三方向硬门：本方案是技术底座，视觉方向待用户从 3 个方向板中选定后填入。

## 目标

1. 设置页新增「主题」功能：经典（原版 RP-Hub 浅色）+ 新主题（亮/暗双模式），新用户默认新主题
2. 主题附属「字体设置」：系统内置字体=经典（modern/serif/system），默认字体=PuHuiTi-3 + AlibabaSans（新用户默认）
3. 不破坏上游同步（扩展层 + 登记 patch）

## 技术机制（已侦察确认）

### RP-Hub 现有字体机制（可复刻）

- `core-utils.js` `fontFamilies`：`[{value:'modern',label:'现代通用字体'},{value:'serif',label:'衬线字体'},{value:'system',label:'系统字体'}]`
- `app.js` `applyFontFamily(value)`：`document.documentElement.dataset.appFont = value`
- `styles.css` 用 `[data-app-font="modern"]` 等选择器切换 `--app-font-family` 变量
- 设置默认值：`fontFamily: 'modern'`（app.js:628）
- 迁移逻辑：`fontFamilyVersion: 4` + `normalizeFontFamily()` 兜底

### 主题机制设计（data-theme 驱动）

```
document.documentElement.dataset.theme = 'luzzy' | 'classic'   ← 主题
document.documentElement.dataset.mode  = 'light' | 'dark'      ← 亮暗
```

- `luzzy-theme.css`（扩展层）定义：
  - `[data-theme="classic"]` → 原版色值（默认变量，与上游一致）
  - `[data-theme="luzzy"][data-mode="light"]` → 新主题亮色变量
  - `[data-theme="luzzy"][data-mode="dark"]` → 新主题暗色变量
- 色板变量化：tailwind.config 的 `gray`/`primary` 色板改为 `var(--tw-gray-500)` 等引用（登记 patch 008）
- 切换逻辑：`luzzy-ext.js` 监听设置变化 → 设置 `data-theme`/`data-mode` → 存 localStorage

### 设置项落点

- 主题选择：设置页「高级参数」区块新增「界面主题」custom-select（选项：经典 / Luzzy 新主题）
- 亮暗切换：新主题下显示「亮色 / 暗色 / 跟随系统」三选
- 字体设置：复用现有「界面字体」custom-select，`fontFamilies` 增加 `{value:'luzzy', label:'Luzzy 默认字体'}`（PuHuiTi-3 + AlibabaSans）
- 新用户默认：`settings.theme = 'luzzy'`、`settings.fontFamily = 'luzzy'`（app.js 默认值 patch）

### 字体打包

- PuHuiTi-3 精选 3 字重 woff2：55-Regular（5.0MB）/ 65-Medium（5.2MB）/ 85-Bold（5.3MB）≈ 15.5MB
- AlibabaSans 全 6 字重 woff2 ≈ 0.3MB
- 落点：`assets/rphub/assets/fonts/`（与 Lora 同目录）
- @font-face 定义：`assets/css/local-fonts.css` 追加（扩展层文件，同步保护）

### 上游同步影响

| 改动 | 类型 | 同步处理 |
|------|------|---------|
| tailwind.config 色板 var() 化 | patch 008 | 重放 |
| fontFamilies 增加 luzzy 选项 | patch 009（core-utils.js） | 重放 |
| settings 默认值 theme/fontFamily | patch 010（app.js） | 重放 |
| 设置页 UI 新增主题选择 | patch 011（index.html） | 重放 |
| luzzy-theme.css 主题变量 | 扩展层 | 零冲突 |
| luzzy-ext.js 切换逻辑 | 扩展层 | 零冲突 |
| local-fonts.css 字体 | 扩展层 | 零冲突 |

## 待定（方向板选定后）

- [ ] 新主题色板具体值（3 方向选 1）
- [ ] 动效/转场细节（进入 200ms / 退出 140ms / ease-out）
- [ ] DESIGN.md 设计真源撰写
