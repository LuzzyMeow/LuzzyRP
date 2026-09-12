# boards-v4 共享 SPEC · v3.0 Compose 设计语言三方向板

> 本文件是三个方向板的**唯一共同输入**（huashu-design Fallback Phase 3）。
> 前置事实：`docs/RESEARCH-v3-rikkahub-design.md`（rikkahub 侦察）+ 现行 `DESIGN.md`（暖幕手记 token）。
> 决策背景：用户 2026-09-12 拍板「整体转 AGPL-3.0，换取 rikkahub 源码级复用」；
> 三方向 = 「**复用 rikkahub 到什么程度、RP 特有界面如何承担**」的三种取舍（PLAN §1）。

## 任务

为 **LuzzyRP v3.0（Jetpack Compose 原生重写版）** 确定整体设计语言的方向板（direction board）。
前提：**这是重新设计而非移植**（Compose 无 Tailwind/CSS/z-index 对应物）；
Material 3 是技术底座（rikkahub 已证明 M3 Expressive 可承载暖色文学观感）；
**硬性规定 4 仍然生效**：Lora + Alibaba PuHuiTi 3 + AlibabaSans 本地打包，禁 CDN。

## 产品与场景

- LuzzyRP：移动端 AI 角色扮演 App（Android 原生，v3.0 起为 Compose 界面）；
- 场景：手机竖屏沉浸阅读长 RP 对话（角色立绘常作背景）、夜间暗纸模式、中端安卓机；
- 「每次对话，都像一本有你的小说」——文学、温暖、沉浸；
- **RP 特有要素必须出现**：角色头像/名牌（Lora 衬线）、长文叙述段（非短问答）、
  思考过程卡（模型推理可视）、消息分支指示。

## 输出格式（三版必须统一，便于横向对比）

每方向 **1 个单文件 HTML 方向板** + 1 张截图：

- 视口 **1440×900**（`npx playwright screenshot` / headless Chrome 截图）；
- 板面结构：深色中性背景（#141413）上左右并排**两个手机框**（各 ~340×700，圆角 36px），
  左=亮色模式、右=暗色模式，渲染**同一个 RP 聊天场景**（同 boards-v3 先例）；
- 聊天场景必含：
  1. **顶栏**：角色名 + 状态，风格随方向自定（M3 TopAppBar / 纸面页头 / 沉浸渐隐，三方向可不同）；
  2. **长文 AI 回复段**：多段叙述 + 一段 `*动作描写*` 斜体 + 对白引号——RP 的核心阅读体验；
  3. **用户气泡**与 AI 回复的视觉区分（气泡式或开放式由方向定义）；
  4. **思考卡（折叠态）**：一行摘要 + chevron（M3 的 ChainOfThought 对应物）；
  5. **输入岛**：圆角 22px 级输入条 + 圆形发送键；
  6. **消息分支指示**（如 `‹ 2/3 ›` 小徽标）；
  7. 角色名牌「Luna」用 **Lora 衬线**（品牌声音不变量）；
- 板底部：**「复用坐标」表**（本方向对 rikkahub 的结构复用项 / 数值重设计项 / 独立实现项各 2-3 条）
  + **M3 token 映射表**（色板种子 → M3 role 对应，亮暗各一列）+ 一句气质定位 + 参照作品名；
- 色板条：本方向关键色块（含色值标注）。

## 强制约束

- **品牌不变量（三版都必须遵守）**：
  - 角色名牌/区块标题 = **Lora** 衬线（文学声音是 LuzzyRP 的身份，三方向都保留）；
  - 正文 = Alibaba PuHuiTi 3.0 + AlibabaSans（本地字体，`fonts/` 已备，@font-face 相对路径）；
  - 文字对比度 ≥4.5:1（正文）；禁止 emoji 图标、紫渐变、左彩边圆角卡、霓虹 glow（反 slop）；
  - 动效纪律：进入 200ms / 退出 140ms / ease-out `cubic-bezier(0.23,1,0.32,1)`；
- **亮暗双模式都必须给出**（M3 ColorScheme 双套）；
- 三版**布局骨架必须互异**（导航/构图/消息区结构至少一项结构性不同——huashu-design 铁律）；
- 各方向有自己的色值边界（见方向简报），**在边界内自由设计，不跨方向偷色**。

## 三方向定义（互异 = 复用程度与观感来源不同，不是换皮）

### 方向 A「织机 Loom」——最大复用 rikkahub（M3 Expressive 原生派）
- **坐标**：rikkahub 的结构 + 机制几乎全盘接收（M3 Expressive + HCT 动态色板 + ExtendColors
  分阶 + assistant 无气泡默认/气泡可选 + MeshGradient 背景 + haze 玻璃开关），
  **数值层以 Luzzy 珊瑚陶土为种子重新生成**（HCT seed = `#CC785C`）；
- 观感基调：现代 M3 工具感 × 暖色——像 rikkahub 官方默认被「Luzzy 化」；
- 色值边界：**禁止照抄 rikkahub Claude 预设的任何色值**（`#C96442`/`#FAF9F5` 等），
  一切色由 HCT 种子推导（板上标注推导关系）；背景可含 MeshGradient 光斑示意；
- 布局骨架：M3 标准件——TopAppBar + LazyColumn 消息流 + 底部 ChatInput 条 + 抽屉。

### 方向 B「暖幕手记 · 纸页」——品牌延续派（保守）
- **坐标**：把现行 v1.x~v2.x「暖幕手记 × 雾纸」设计语言**翻译成 M3 token**；
  rikkahub 只借结构（drawer/输入岛骨架/ChainOfThought），不借观感；
- 观感基调：与 WebView 版几乎连续——cream 画布 `#FAF9F5`、珊瑚 `#CC785C`、
  发丝线 `#E6DFD8`、纸感轻投影；玻璃只上低频 chrome（v1.3.0 性能档位照搬）；
- 色值边界：**只用现行 DESIGN.md 的 token**（亮暗两套全表见 DESIGN.md Colors 节），
  禁止新增色相；
- 布局骨架：延续上游 RP-Hub 结构——黑渐隐顶栏（压角色背景图）+ 玻璃/纸面气泡 + 输入岛。

### 方向 C「夜航灯 Nightferry」——新定观感派（激进）
- **坐标**：rikkahub 结构骨架 + **全新 RP 向观感**——以「深夜航船的阅读灯」为母题：
  暗色优先（dark-first），暖 amber 光晕做唯一 accent，亮色模式是「白昼甲板」的冷纸白；
  布局引入**新结构**（如居中窄栏书卷式消息流 + 顶栏融入背景的沉浸形态）；
- 观感基调：像一本深夜被台灯照亮的小说——比 A 更暗、比 B 更戏剧，但保持纸墨文学气质；
- 色值边界：暗 canvas 允许脱离 DESIGN.md（新方向新值），但**禁紫/禁青/禁霓虹**，
  accent 仍须是暖色族（amber/ember 系），亮暗双套都要给全；Lora 声音保留。

---

## 工程备注（三版共用，写进板底）

- 本板是**方向初稿**（视觉层），不是 Compose 实现；定稿后落 `docs/DESIGN-compose.md`；
- rikkahub 结构复用的 AGPL 义务已在 `docs/LICENSING.md` 落档（源码复用需保留版权头）；
- Compose 侧无 CSS——板中玻璃/渐变仅为观感示意，实现时以 M3 表面色阶 + haze 类库等效表达。