# LICENSING · LuzzyRP 许可安排

> **本文件的用途**：把 LuzzyRP 的许可结构写成可核对的契约，避免"混着写、日后说不清"。
> **它不是法律意见。** 凡标 ⚠️ 的地方都需要你自己确认。

---

## 1. 现状事实（已核实）

| 材料 | 许可 | 核实方式 |
|---|---|---|
| 上游 RP-Hub（仓库根 `LICENSE`，即 `app/src/main/assets/rphub/**` 的来源） | **CC BY-NC 4.0** | 读 `LICENSE` 第 1 行 `Attribution-NonCommercial 4.0 International`；`rp-hub-reference/LICENSE` 同款 |
| LuzzyRP 自有代码 | **AGPL-3.0** | 仓库根 `LICENSE-AGPL-3.0` 全文 |

**注**：自有代码采用 AGPL-3.0 的安排在 v3.0 立项时确定；**该版的原生化路线（原生 Kotlin 传输层 /
Jetpack Compose 界面）已于 2026-09-20 放弃并整体删除**，但**本许可安排本身保持不变**
（见 CHANGELOG v1.5.0 段）。AGPL-3.0 是当前自有代码的生效许可。

---

## 2. 许可安排（现行）

仓库根 `LICENSE`（CC BY-NC 4.0）是**上游署名义务的载体，禁止删除或改写**（AGENTS 硬性规定）。
因此采用**并存（mixed licensing）**，而不是覆盖：

| 范围 | 许可 |
|---|---|
| `app/src/main/java/com/luzzymeow/luzzyrp/**`（Kotlin 壳等**自有代码**） | **AGPL-3.0** |
| `app/src/main/assets/ext/**`（扩展层，自有代码） | **AGPL-3.0** |
| `tools/**`、`docs/**`（自有工具与文档） | **AGPL-3.0** |
| `app/src/main/assets/rphub/**`（上游资产，含我们的 patch 修改） | **保持 CC BY-NC 4.0**，原样署名分发 |

**已落地动作**：
1. `LICENSE-AGPL-3.0` 已新增（AGPL-3.0 全文）——**不替换**根 `LICENSE`；
2. `README.md` 已含「许可」段，说明并存结构与各自适用范围；
3. 上游文件内的改动一律携带 `[LuzzyRP patch NNN]` 标记并登记于 `tools/patches/`。

---

## 3. ⚠️ 一处必须你知道的并存冲突（我不替你下法律结论）

**CC BY-NC 4.0 与 AGPL-3.0 在同一件作品上直接互斥**：

- **CC BY-NC 4.0**：授予的权利**限于非商业目的**（§2(a)(1)(A)；`NonCommercial` 定义见其第 116 行附近：
  *"not primarily intended for or directed towards commercial advantage or monetary compensation"*）。
- **AGPL-3.0 第 10 条**：*"You may not impose any further restrictions on the exercise of the rights
  granted or affirmed under this License."* —— **禁止对其授予的权利附加进一步限制**，且 AGPL
  **明确允许商业使用**。

⇒ 「不得商用」与「不得限制他人商用」**无法同时成立**。

**为什么第 2 节的并存安排仍然可能可行**（两种读法，我不判断哪个成立）：

- **读法 A（聚合）**：AGPL-3.0 第 5 条末段明确允许 *aggregate* ——
  *"Inclusion of a covered work in an aggregate does not cause this License to apply to the other parts
  of the aggregate."* 若「我们的壳与扩展层代码」与「上游 Web 资产」被认定为**各自独立、
  仅聚合分发**，则 AGPL 不波及上游部分。本项目的形态（上游资产是被 WebView 加载的独立 web 应用，
  经 patch 修改后原样分发）**偏向支持这一读法**，但不是定论。
- **读法 B（单一作品）**：若被认定为 *"combined ... to form a larger program"*，则 AGPL 第 5 条 c 款
  要求**整件作品**以 AGPL 分发 —— 而上游资产**我们不持有版权**，无法单方面改许可，于是形成僵局。

**另需注意**：CC BY-NC 4.0 是**内容许可（非软件许可）**，把它用于一个 Android 应用本身就不是常规做法；
上游为何如此选择我们无从得知。**最稳妥的做法是联系上游确认**，或按读法 A 的形态明确隔离
（自有代码与上游资产在目录、构建、许可声明三个层面都清晰分离）。

**我（Agent）不对此作法律判断，也不建议在未确认前做大规模商业分发。** 若你要更稳妥，
可选的缓解动作：① 联系上游 RP-Hub 作者确认；② 保持非商业侧载分发（与现状一致）；
③ 若将来要商业化，先解决这一冲突。

---

## 4. 与既有纪律的关系

- **AGENTS 硬性规定**：上游 `LICENSE` 禁止删除/改写 → 本安排**只新增、不修改**，符合规定。
- **署名义务**：README 的二创署名声明与 `rp-hub-reference/` 均保留。
- **分发方式**：仍为**仅侧载**（不应用商店，12 岁条款合规风险不变）。
- **AGPL 的"提供对应源码"义务**：本项目仓库公开，可满足；若日后转为私有分发，
  则必须随二进制提供 Corresponding Source —— **这是 AGPL 与 GPL 之外的持续义务**。

---

## 5. 外部代码复用的逐笔登记（勿事后补）

**纪律**：复用外部代码必须**逐笔登记**，并区分两种性质：
① **采用架构/形态**（非逐行复制）——登记即可；
② **逐行复制代码**——必须在文件头保留原始版权声明与来源标注。

| 日期 | 来源 | 复用性质 | 落到我们哪里 |
|---|---|---|---|
| 2026-09-12 | rikkahub `ui/components/ai/ChatInput.kt` | ① 采用架构与节拍，未逐行复制代码 | 曾用于 v3.0 Compose 输入岛 `InputIsland`。**该界面已随 v3.0 路线放弃整体删除（2026-09-20）**，此笔保留作历史登记。 |

> 当前在用的第三方资产（Vue 3 / Tailwind / marked / DOMPurify / SortableJS / 字体）
> 已在 `assets/rphub/vendor/` 与 `assets/rphub/assets/fonts/` 本地化，其许可随文件保留。
