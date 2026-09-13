# direction-approved · boards-v5（会话总览页）

> **Gate 文件**（`docs/skills/huashu-design/SKILL.md`「Gate文件协议」）：本文件存在 = 三方向门已过。
> 缺本文件即视为未过门、不得开工。本文件在实现开工**之前**创建。

## 1 · 展示过什么

| 方向 | 逻辑 / anchor | 稿子 | 渲染（亮 / 暗） |
|---|---|---|---|
| A · 错落拼贴 | 秒数轮盘：`date +%S`=42 → 42%20+1 = 网页库 **#3 孟菲斯复古拼贴最大化** | `direction-a-collage.html` | `direction-a-collage-light.png` / `-dark.png` |
| B · 分组行 | 现实参照：**Things 3**（粘性组头 + 等高行 + 右对齐元数据） | `direction-b-grouped.html` | `direction-b-grouped-light.png` / `-dark.png` |
| C · 目录页 | 最佳设计师：**iA Writer 的 Library**（排版即界面） | `direction-c-contents.html` | `direction-c-contents-light.png` / `-dark.png` |

共同输入（spec）：[`SPEC.md`](SPEC.md)。速览与差异对照：[`direction-summary.md`](direction-summary.md)。

## 2 · 用户选择（原话）

> **「B · 分组行（推荐）」**

## 3 · 随方向一并拍板的两个细节（用户原话）

| 细节 | 用户选择 | 实现口径 |
|---|---|---|
| 预览取哪一条 | **「最后一条用户发言（推荐）」** | 取该会话**最后一条 `role = user`** 的正文。<br>**一处如实说明的偏离**：若该会话里用户**一句话都还没说过**（例如只有开场白），那就没有「用户发言」可取——此时回落显示**末条正文**，否则那一行会空着、与已批准的版式不符。这不是偷偷改选，而是把「无用户发言」这个真实情形交代清楚。 |
| 入口位置 | **「聊天页顶栏入口」** | 聊天页顶栏加一个「会话」按钮进入本页；**侧栏（抽屉）不加项**（用户未选该选项，故不动侧栏 IA）。 |

## 4 · 未选方向的处置

按 `huashu-design`：未选方向**不删除**，连同渲染一起留在 `boards-v5/` 作为决策留痕
（日后若要换方向，先回到本文件重新走门，而不是直接改代码）。

## 5 · 开工前确认

- [x] 4 项 SKILL 主文档已完整重读（2026-09-13）
- [x] SPEC.md 已写（受众/场景/基调/尺寸/约束/母题/图片策略）
- [x] 三方向均有真实渲染（非文字描述）
- [x] 用户已选定（本文件 §2 原话）
- [x] 细节已拍板（§3）
- [ ] 设计真源已更新（`docs/DESIGN-compose.md` §21 —— 实现后回填「已实现」状态）
