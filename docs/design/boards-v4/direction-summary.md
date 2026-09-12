# boards-v4 · direction-summary · v3.0 Compose 设计语言三方向对比板

> huashu-design Fallback Phase 5 Gate 文件（待用户选定后补「用户选择原话」并改名 approved）。
> 三版共享 spec：`SPEC.md`；侦察依据：`docs/RESEARCH-v3-rikkahub-design.md`。
> 截图视口 1440×1500（headless Edge）；三版布局骨架互异（huashu-design 铁律）。

## 展示的三方向（2026-09-12）

| 方向 | 一句话 | 板 | 截图 |
|------|--------|-----|------|
| **A · 织机 Loom**（最大复用 rikkahub · M3 Expressive 原生派） | rikkahub 的骨架，Luzzy 的线：M3 座 + HCT 动态色板全盘接收，以珊瑚陶土 `#CC785C` 为种子重新推导全部数值，零照抄 rikkahub Claude 预设 | `direction-a-loom.html` | `direction-a-loom.png` |
| **B · 暖幕手记 · 纸页**（品牌延续派 · 保守） | 老用户的视觉记忆原封延续：现行「暖幕手记 × 雾纸」token 全数直译成 M3 role（cream `#FAF9F5` / coral `#CC785C` / 雾纸 .86+blur16），rikkahub 只借结构不借观感 | `direction-b-warm-journal.html` | `direction-b-warm-journal.png` |
| **C · 夜航灯 Nightferry**（新定观感派 · 激进） | 深夜航船的阅读灯：dark-first，舱室黑 `#100E0C` + amber 灯光 `#D69E5A` 唯一 accent，居中窄栏书卷式消息流 + 首句衬线 drop 为全新品牌签名 | `direction-c-nightferry.html` | `direction-c-nightferry.png` |

## 三方向的差异化轴（不是换皮）

| 轴 | A 织机 | B 纸页 | C 夜航灯 |
|----|--------|--------|----------|
| 观感来源 | rikkahub（M3 Expressive 默认观感）× Luzzy 种子 | 现行 DESIGN.md（100% 自家） | 全新定（夜读母题） |
| 消息形态 | AI 可选气泡/无气泡（rikkahub 语义），16dp 圆角 | 双方都气泡 + 玻璃雾纸 | AI 无气泡开放式书卷 + 用户便签气泡 |
| 顶栏 | M3 TopAppBar（primaryContainer 面） | 黑渐隐压角色背景图（上游延续） | 沉浸融入背景（无独立面） |
| 背景母题 | MeshGradient 光斑（rikkahub 手法） | 角色背景图 + 雾纸玻璃 | 台灯光晕（radial 灯照） |
| accent | 珊瑚陶土（HCT 推导） | 珊瑚陶土（现行值） | amber 灯光（ember 系新值） |
| 暗色 canvas | #191411（HCT T10） | #171614（现行） | #100E0C（更深舱室） |
| 布局骨架 | M3 标准 Scaffold + Drawer | 上游 RP-Hub 结构延续 | 居中窄栏书卷式（新结构） |
| 品牌声音 | Lora 名牌 ✓ | Lora 名牌 ✓ | Lora 名牌 + 衬线 drop 首句 ✓ |

## 共同不变量（三版都遵守，出自 SPEC.md）

Lora 角色名牌（文学声音）· PuHuiTi/AlibabaSans 正文（本地字体，规定 4）· 正文对比 ≥4.5:1 ·
禁 emoji 图标/紫渐变/左彩边圆角卡/霓虹 glow · 动效 200/140ms ease-out · 亮暗双模式 ·
思考卡 + 分支指示 + 输入岛三要素齐备。

## 用户选择

**「A · 织机 Loom」**（2026-09-12，会话 63，AskUserQuestion）。
Gate 文件：`direction-approved-v4.md`。