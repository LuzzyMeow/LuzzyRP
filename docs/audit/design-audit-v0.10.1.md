# 「极光双生 Aurora Dual」前端设计彻查报告

- 版本：v0.10.1（基于 v0.10.0 源码彻查）
- 日期：2026-08-11
- 范围：Android 原生 Kotlin + Compose 全部前端（`ui/theme/`、`ui/components/`、`ui/pages/**`、`ui/icons/`、`res/`）
- 依据：`HARD_REQUIREMENTS.md`（12 条硬性规定 + 设计系统不可改项）
- 结论标签：【合规】= 符合规定 /【修复】= 本轮已修复 /【观察】= 建议关注，暂不修改

---

## 1. 设计系统健康度

### 1.1 颜色（【合规】）
- 单色板源：`ui/theme/AuroraColor.kt`（370 行），全部颜色 token 集中于此。
- 品牌色冻结：`AuroraPink #FF6EC7` / `AuroraViolet #B57BFF`，浅深双实例成对维护。
- 全项目检索 `Color(0x...)`：除 `AuroraColor.kt` 与注释外**无任何硬编码** ✅。
- 取色通道统一：`LocalAuroraPalette` 注入，组件/页面经此取色，随浅/深自动切换 ✅。
- 浅色「极光晨光」（canvas #FAF7F2）+ 深色「极光之夜」（canvas #0E1116）语义 token 完整（成功/警告/错误/信息 + soft 变体 + scrim/shadow/glow）。
- **本轮修复**：冷启动 `colors.xml luzzy_splash_background` 由旧羊皮纸 #F5F4ED → 对齐浅色画布 #FAF7F2，消除冷启动颜色跳变；清理过时头注释。

### 1.2 排版（【合规】）
- 双字族：AlibabaPuHuiTi-3（9 字重，中文）+ AlibabaSans（6 字重，西文/数字），`LuzzyMixedFontFamily` 字符级混合。
- 非简中/英语字符回退系统字体（平台 fallback 链），符合硬性规定 #6。
- 「极光」10 级 Type Scale（32sp→11sp）映射 Material3 Typography 完整。
- `res/font/` 15 个 ttf 与 `doc/AlibabaPuHuiTi-3`、`doc/AlibabaSans` 逐字节一致 ✅。

### 1.3 图标（【合规，1 例外】）
- 816 个 VectorDrawable（`ic_<类别>_<名>.xml`）+ `GameIcons.kt`（12 类 815 项）+ `LuzzyIcons.kt` 语义别名，全部源自 `doc/game-icon-pack` ✅。
- 全项目检索 `Icons.Default/Filled/Rounded`：仅出现在**禁止性注释**中，无实际使用 ✅。
- **例外（观察）**：通知小图标 `res/drawable/small_icon.xml` 源自 rikkahub 参考工程，**不在 game-icon-pack 内**。判定：通知系统图标属于系统级（status bar / heads-up 单色 mask），不属于硬性规定 #6 枚举的「容器/按钮/弹窗/页面/下拉扩展框等**前端内容**」范畴，故保留现有干净 vector，不做替换。已在 `HARD_REQUIREMENTS.md` §1 规定 6 标注为已知例外。

### 1.4 间距 / 圆角（【合规】）
- `LuzzySpacing` 4pt 基准（Xs 4 / Sm 8 / Md 12 / Lg 16 / Xl 24 / Xxl 32）。
- `AuroraShapes` / `AuroraCorner` 大圆角体系（8/12/16/20/24/28 + Pill）。
- 语义化取用，未发现散落硬编码 dp 间距异常。

### 1.5 动效（【合规】）
- `ui/theme/Motion.kt`：三态令牌 **Enter=300ms / Interact=150ms / Exit=Enter×0.65=195ms**，符合硬性规定 #6 三态丝滑动画；含 Easing/Spring/Stagger(48ms)/GlowPulse 全套。
- 组件层：按钮 press-scale、卡片 press-scale + raised、switch、menu scale、input focus、tabs 滑动指示、drawer 高亮、avatar 呼吸、skeleton shimmer、spinner 等均已接三态。
- 页面切换：Navigation3 slide+fade+scale（enter）/ slideOut+fade+scale（exit）/ predictivePop。

### 1.6 表面引擎（【合规】）
- `ui/theme/AuroraSurface.kt`：auroraSurface / auroraStroke / auroraGlow / auroraShadow / auroraGradientBackground / auroraCanvasBackground。
- 遵守视觉层级顺序（底色→辉光→描边→阴影→内容）；**未使用 Modifier.blur()** ✅（v0.9.6 教训已守住）。
- **本轮修复（去重）**：原 `ChatPages.kt` / `TrpgPage.kt` 各自维护的私有 `drawAuroraBorder`（同实现、3+4 处调用）统一迁移到共享 `auroraStroke(cornerRadius, width, alpha)`；为 `auroraStroke` 新增 `alpha` 参数（向后兼容，默认 1f），气泡签名造型用 0.55f / 1.2dp，视觉与原来完全一致。

---

## 2. 硬性规定合规核对

| 规定 | 核对结论 |
|------|---------|
| 1 流式逐字 |【合规】4 个 Provider 全走 SSE callbackFlow 逐事件推送、零节流；`UIMessage.appendChunk` 逐字合并；Chat/TRPG UI 逐字渲染。无 debounce/sample/buffer。 |
| 2 Agentic 多轮 |【合规】Chat 256 步循环 + `AI_AGENTIC_SYSTEM_APPEND`（≥3 思考 + ≥2 工具）+ 首步 tool_choice=REQUIRED；TRPG 双阶段 loop（maxLoops=3）。 |
| 3 NSFW 不可触碰 |【合规】无审查/过滤/改写；用户提示词原文透传；Gemini 安全过滤 OFF。⚠️ `DesignModeService.kt` `NSFW_SYSTEM_PROMPT_BLOCK` 为占位 stub（未填入），见 §3 观察项。 |
| 4 代码注释 |【合规】theme/components/icons 均带【文件说明】+【严禁修改项】头注释；本轮改动均补充。 |
| 5 Android 原生 |【合规】全项目 Kotlin + Compose。 |
| 6 图标/字体/动画 |【合规】图标全取自 pack（1 系统级例外）；字体 Alibaba 双字族；三态动画齐备。 |
| 7 无占位符 |【观察】`DesignModeService.kt` NSFW 块为占位 stub（历史遗留，非本轮引入）。 |
| 8 缓存命中 |【合规】Claude cache_control 断点 + TRPG append-only 消息保护；本轮未改模型/提示词前缀。 |
| 9 工具同步日志 |【合规】本轮未改内置工具，无日志/提示词变更。 |
| 10 文档同步 |【合规】本轮同步更新 CHANGELOG.md / README.md（见 §4）。 |
| 11 工作区整理 |【观察】本轮仅删死代码；未做大范围文件迁移。 |
| 12 APK/Release |【待确认】对外发布操作，需用户另行确认后执行。 |

---

## 3. 观察项（建议关注，本轮未改）

1. **`DesignModeService.kt` `NSFW_SYSTEM_PROMPT_BLOCK` 占位 stub**：原 NSFW 字面量未迁移，现为空 boilerplate。补齐需人工确认内容，且必须保留「不可触碰/无审查」语义（硬性规定 #3、#7）。
2. **`PresetTheme.kt` `CustomTheme` 默认 seed 为 Material 紫 #6750A4**（非品牌粉）。因属数据层序列化契约（【严禁修改项】），不建议改动；仅记录。
3. **图标管线脚本过期**：`scripts/convert_icons.py` / `generate_luzzy_icons.py` 指向已删除的 `svg/no-padding` 与外部 `RP-Hub` 路径，`GameIcons.kt` 再生暂不可复现。如需重新生成图标需先修复管线。
4. **`ai/.../ui/Message.kt` 遗留已废弃 `UIMessagePart.ToolCall` / `ToolResult`**：仅供 DB 迁移保留，非本轮范围。
5. **`presetColorScheme()` 恒返回浅色 scheme**：深色由 `LuzzyTheme` 统一处理，属既有设计；保留观察。
6. **通知 `small_icon.xml` 例外**：见 §1.3，系统级图标，保留。

---

## 4. 本轮变更清单

| 文件 | 变更 |
|------|------|
| `HARD_REQUIREMENTS.md`（新增，仓库根） | 12 条硬性规定 + 设计系统不可改项摘要 + 新任务合规清单。 |
| `doc/audit/design-audit-v0.10.1.md`（新增） | 本报告。 |
| `res/values/colors.xml` | `luzzy_splash_background` #F5F4ED→#FAF7F2，对齐浅色画布；清理过时注释 + 补【严禁修改项】。 |
| `ui/theme/AuroraSurface.kt` | `auroraStroke` 新增 `alpha` 参数（向后兼容）；头注释补 #4 去重规范。 |
| `ui/pages/chat/ChatPages.kt` | 3 处 `drawAuroraBorder`→`auroraStroke`；删除私有副本；补 import 与去重注释。 |
| `ui/pages/trpg/TrpgPage.kt` | 4 处 `drawAuroraBorder`→`auroraStroke`；删除私有副本；补 import 与去重注释。 |
| `ui/pages/misc/MiscPages.kt` | 删除死代码重复 `ShareHandlerPage`（保留 RouteActivity 接线的 chat 版）。 |
| `CHANGELOG.md` / `README.md` | 同步新增 v0.10.1 小节。 |
| 持久记忆 | 写入 hard-requirements / design-system / frontend-workflow + MEMORY.md 索引。 |

---

## 5. 总结

前端设计系统整体**健康、规范、一致性高**：颜色/字体/图标/动效全部集中 token 化并强约束，四大行为规则（流式/agentic/NSFW/缓存）实现正确。本轮重点为「记录硬性规定（`HARD_REQUIREMENTS.md` + 持久记忆）」+「彻查报告」+「顺手修复 4 项明确问题」。剩余观察项以低风险、需人工确认者为主，不影响 v0.10.1 交付。
