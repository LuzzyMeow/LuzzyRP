# 方向选定落档 · v2（三方向硬门 Gate 文件）

> 对应 huashu-design Fallback Phase 5。用户选择原话为本文件核心记录。

## 用户选择原话

「**选C，但进一步增强CLAUDE风格**」

## 选定方向

**方向 C「暖幕手记」（Backstage Notes）为基底，融合增强 Claude（Anthropic）设计语言**：

- 基底保留（C · Collins 玩味编辑风）：手账/剧作手记概念、克制的手作记号
  （荧光笔划线、伞骨线、雨点点阵）、象牙纸底、编辑排印能量；
- Claude 增强（awesome-design-md `design-md/claude/DESIGN.md` 真实体系）：
  - 色彩骨架整体迁移至 Claude token：画布 `#FAF9F5`（tinted cream）、暖表面三层
    `#F5F0E8 / #EFE9DE / #E8E0D2`、发丝线 `#E6DFD8`、ink `#141413`、body `#3D3D3A`、
    muted `#6C6A64`；
  - 主强调 = 珊瑚陶土 `#CC785C`（Claude signature coral，按钮/激活/图形 accent），
    按钮深档 `#A9583E`（active，白字 ≥4.5:1）；
  - 排印声音 = 衬线 display（本地 **Lora** 对位 Tiempos/Copernicus）× 无衬线正文
    （**AlibabaSans + PuHuiTi 3.0** 对位 Styrene）——「文学刊物」而非 SaaS；
  - 辅助色：amber `#E8A55A`（荧光记号用其低饱和版，替代 C 原案荧光黄）、
    teal `#5DB8A6`（成功/辅助）；
  - 暗模式 = Claude 暗表面系 `#181715 / #1F1E1B / #252320`，on-dark `#FAF9F5`；
  - 气质：warm、humanist、editorial——「 deliberately warm where others are cool」。

## 主题产品决策（随选择一并生效）

1. 设置页新增「界面主题」：`暖幕手记（Luzzy）`（**新用户默认**）/ `经典（原版）`（老用户迁移保留）；
2. 主题卡附属设置：模式（亮/暗，仅 Luzzy 主题下显示）+ 界面字体（原独立字体设置并入主题卡）；
3. 字体选项：`经典（原版）`（上游 Inter 系，改名）+ `经典衬线（Lora）` + `系统` +
   `Luzzy 默认`（PuHuiTi 3 + AlibabaSans，**新用户默认**）；
4. 老用户迁移：savedSettings 无 `theme` → classic；无 `themeMode` → light；已保存字体保持不变。

## 初稿存档

- 方向板：`docs/design/boards-v2/direction-{a,b,c}.html / .png`（含 A 锐白 / B 午夜场 备选）；
- 共享骨架：`docs/design/boards-v2/skeleton.html`；设计合同：`docs/design/spec-v2.md`；
- Claude token 真源参照：`docs/skills/awesome-design-md-main/design-md/claude/DESIGN.md`。
