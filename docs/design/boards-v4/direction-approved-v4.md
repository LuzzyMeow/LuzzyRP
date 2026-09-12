# direction-approved-v4 · 「织机 Loom」方向选定

> huashu-design Fallback Phase 5 Gate 文件。三方向板 + 用户选择原话存档。
> 三版展示物：`direction-a-loom.png` / `direction-b-warm-journal.png` / `direction-c-nightferry.png`
> （HTML 同名 .html；对比板 `direction-summary.md`；共享 spec `SPEC.md`）。

## 展示的三方向（2026-09-12 · 会话 63）

| 方向 | 板 | 截图 |
|------|-----|------|
| A · 织机 Loom（最大复用 rikkahub · M3 Expressive 原生派） | `direction-a-loom.html` | `direction-a-loom.png` |
| B · 暖幕手记 · 纸页（品牌延续派） | `direction-b-warm-journal.html` | `direction-b-warm-journal.png` |
| C · 夜航灯 Nightferry（新定观感派 · dark-first） | `direction-c-nightferry.html` | `direction-c-nightferry.png` |

## 用户选择原话

AskUserQuestion 回答：**「A · 织机 Loom」**（2026-09-12，会话 63）。

## 选定方向的执行要点（进 T5 撰写 DESIGN-compose.md）

- **结构照搬 rikkahub**：M3 Expressive 座（TopAppBar/Scaffold/Drawer 双形态）+
  ExtendColors 五色×十阶 + assistant 无气泡默认/气泡可选 + ChainOfThought 分块 +
  HCT 动态色板机制（CustomTheme 等价物）+ MeshGradient 背景手法 + haze 类玻璃开关；
- **数值全部重新推导**：HCT seed = 珊瑚陶土 `#CC785C`（TONAL_SPOT，contrast 0）——
  板上数值为设计期手算近似；**P1 实现后的权威生成值**以 `docs/DESIGN-compose.md` §2 为准
  （亮 canvas `#FFF4F1` / 暗 `#231917`；primary 亮 `#8F4C35` / 暗 `#FFB59D`，快照测试钉死）；
  **零照抄 rikkahub Claude 预设色值**（#C96442/#FAF9F5 等仅作对照不入 token）；
- **品牌不变量**：Lora 角色名牌 + PuHuiTi/AlibabaSans 本地字体（规定 4）；
  动效 200/140ms ease-out `cubic-bezier(0.23,1,0.32,1)`；
- **独立实现清单**（rikkahub 无对应物）：分支树编辑器、世界书、预设体系、记忆管线、
  剧情面板、开屏「开卷」、用量趋势图、RP 特有全部功能（PLAN §4 对账清单）。
