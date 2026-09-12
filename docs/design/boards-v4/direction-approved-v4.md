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

---

## 豁免记录 · 聊天页 v2 复刻（2026-09-12，会话 64 续）

**用户原话**：「用一张示例角色卡进行复刻原项目的聊天页，包括输入框的功能icon 玻璃质感半透明
气泡 流式输出 思考卡片节点 全屏角色卡图片背景等，示例角色卡为
"C:\Users\Administrator\Desktop\Vanio.png" 请严格按照设计硬性规定 读完skill 才可进行
本次豁免3方向」。

- 符合 huashu-design「唯一豁免」第 1 条（用户本次会话明说跳过三方向）；
- 复刻基准 = **原项目（WebView 版）聊天页** + 现行 `DESIGN.md` 既有条款的 Compose 翻译
  （黑渐隐顶栏 / 统一雾纸玻璃 / 思考卡 / 输入岛），非新方向；
- 硬性规定 9 合规：4 项 SKILL 主文档本会话已完整阅读 + 本次补读
  `animation-pitfalls.md`（流式/展开动效相关：起始帧完整性 / 跨底色元素对比）与
  `app-prototype.md`（细节签名 120% 原则）。
- 示例角色卡 Vanio.png 为**非标准 PNG**（NovelAI 导出，IHDR 后 chunk 边界错位），
  已手工重建合法 PNG 入 `drawable-nodpi/vanio_card.png`（仅作 P1 演示资产；
  正式链路 P4 角色卡导入才用真卡）。
