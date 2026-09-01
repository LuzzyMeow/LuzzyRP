# direction-approved-v3 · 「暖幕手记 × 液态玻璃」方向选定

> huashu-design Fallback Phase 5 Gate 文件。三方向板 + 用户选择原话存档。

## 展示的三方向（2026-09-01）

| 方向 | 板 | 截图 |
|------|-----|------|
| A · 雾纸 Frost-Paper（保守 · Windows Mica/Acrylic 派） | `direction-a-frost-paper.html` | `direction-a-frost-paper.png` |
| B · 琥珀琉璃 Amber-Glass（居中 · 暖 tint 派） | `direction-b-amber-glass.html` | `direction-b-amber-glass.png` |
| C · 晨露 Liquid-Clear（激进 · Apple Liquid Glass 派） | `direction-c-liquid-clear.html` | `direction-c-liquid-clear.png` |

三版共用 spec：`boards-v3/SPEC.md`（Claude token 强制、亮暗双框同场景、配方表格式）。
三版布局骨架互异 = 玻璃的**面积与强度**不同：A 仅固定 chrome 且高不透明；
B 暖 tint 玻璃化到 AI 气泡；C 高透 + saturate 到用户气泡。

## 用户选择原话

AskUserQuestion 回答：**「A · 雾纸 Frost-Paper（推荐）」**

## 选定方向的执行要点

- 玻璃只上**固定 chrome**：悬浮顶栏 / 输入岛 / 抽屉 / 模态弹窗；
- 配方：亮 = `#FAF9F5` cream tint，暗 = `#201E1B` 暖纸 tint；alpha 顶栏 .85 / 输入岛 .88 / 弹窗 .88；
  blur 16px；边 = 1px 发丝线（亮 `#E6DFD8` / 暗 `#3E3A34`）；无 specular；
- **聊天气泡维持纸感（不透）**——`.msg-bubble-glass` 回归纸面并移除 backdrop blur（性能同步受益）；
- backdrop-filter 仅用于固定/悬浮层；`@supports` 不支持时降级为实底；
- 工程落点：全部在 `luzzy-theme.css`（规定 3），目标零新 patch。
