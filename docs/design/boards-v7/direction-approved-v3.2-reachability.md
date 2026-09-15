# direction-approved · v3.2 功能可达性收口（会话 88）

> 适用：A 世界书条目排序 / B 世界书绑定角色 / C 剧情分支手动新建 / D 死代码处置。

## 豁免记录（huashu-design「唯一豁免」第 2 条：已选定方向后的迭代）

- **依据**：设计方向「A · 织机 Loom」已由用户选定（`docs/design/boards-v4/direction-approved-v4.md`），
  `docs/design/boards-v7/direction-approved-v7.md` 为其最近一次延续。本轮**不产生新视觉设计**：
  - A 把世界书条目行从「自造的三图标行」**收编回共用件** `PageKit.EntryCard` /
    `EntryMenuAction`（与预设页同形，正是 DESIGN-compose §23.2 早已写定的落点）；
  - B 在既有聊天页世界书面板内追加分区，容器沿用 `ModalBottomSheet` + `surfaceContainer*` +
    `BadgeChip`，零新色相；
  - C 在既有 `MessageActionRow` 追加一枚动作（与「复制/重新生成/编辑/更多」同族、同 48dp 热区），
    位置与语义对齐上游 `index.html:668`；
  - D 为删除/接线，不是视觉产出。

- **硬性规定 9 合规**：4 项 SKILL 主文档本会话已完整阅读
  （huashu `SKILL.md` / open-design `AGENTS.md` / ui-ux-pro-max `CLAUDE.md` + `SKILL.md` /
  awesome-design-md `README.md`），并读到操作层清单 `references/pro-rules.md`（Pre-Delivery Checklist）。
  本次查询契约检索：以 `--domain ux`（触控目标 / 层级列表披露）与 `--stack jetpack-compose` 为口径；
  「上移/下移」这一具体交互**无库匹配** → 按该 SKILL 要求显式声明无匹配，回落项目既有落点
  （DESIGN-compose §23.2 已定「不做拖拽，菜单上移/下移」）。
- **不做**：不新增色相、不换字体、不动动效令牌、不改聊天页其余部分。
- **交付门**：pro-rules Pre-Delivery Checklist 逐项 + 五维 critique，记录落 WORKLOG。
