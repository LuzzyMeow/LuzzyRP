# boards-v7 · 设置页重设计 · 三方向速览

> **触发**：用户「设置页的组件摆放形式还是很难看，请你重新设计」（当前线上仍是旧版：
> 48dp 裸渐变色块 + 标题在带外 + 数据卡无带 + 硬编码色相）。
> **硬性规定 9 三方向门**：三版**真实渲染**（headless Chrome，412×1750）如下，选定后才实施。
> **共同约束**：零新色相（只用 DESIGN-compose §2 色板与同色相 T 阶）、Lora 区块标题 + PuHuiTi 正文、
> 触控 ≥44dp、亮暗双套（板为亮色）、行为/接线一律不动。

| 方向 | 文件 / 图 | 骨架 | 一句话 |
|---|---|---|---|
| **A · 织机卡组** | `direction-a-cards.html` / `direction-a-cards.png` | 四张卡各带 **96px 渐变头带**（白色纹理 + 白字 icon/标题内嵌）+ 用户卡**头像叠压** + 内容行发丝线 + 高级卡 `SectionTitle` + 参数块 | **上游 rp-hub 摆放形式的最大保真翻译** |
| **B · 折叠卡组** | `direction-b-cardgroup.html` / `direction-b-cardgroup.png` | **Lora 大标题**页头 + 小节标题 + **CardGroup**（圆角 20 卡组、组内条目压角、图标 chip）+ 内联滑杆/开关 | rikkahub 设置形态：密度更高、最原生 M3、无装饰 |
| **C · 状态枢纽** | `direction-c-hub.html` / `direction-c-hub.png` | 顶部 **primaryContainer 状态摘要卡**（头像 / 角色名 / API 徽标 / 版本·数据量）+ **分组导航行**（点行进子页） | 主页即仪表：一眼看清「是谁、连没连、装了多少」 |

**自检（五维 critique 初判）**：
- 方向：A/B/C 骨架互异（头带卡组 / 大标题卡组 / 摘要+导航），非换皮；
- 品牌：三版都用 Lora 标题 + 珊瑚 primary，A 的 `鹿` 头像叠压是品牌签名位；
- 层级：A = 四卡等权；B = 组标题分段；C = 状态卡最高、导航次之；
- 动效：板为静帧（实施统一 200/140 ease-out）；
- 工程：全部可落 PageKit/EditorKit 既有组件（A 需新增一个「带纹理头带」容器件）。

**选定后**：落 `direction-approved-v7.md`（记选择原话）→ 实施 → `DESIGN-compose §31` →
模拟器亮暗复查截图 + `checkChat` 门禁。
