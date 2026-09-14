# HANDOFF · 会话 78 → 79（批 C 余项完成 · 下一轮批 D + 文档收尾）

> **用法**：整段复制下面「下一轮提示词」发给下一个 Agent 即可。
> 本文件只写「下一个 Agent 不知道但必须知道的事」；已沉淀进仓库的只给路径不重复。
> **上一份 `docs/HANDOFF-p5-instrumented.md` 的收尾四项已完成两项**（checkChat 终值 + 提交），
> 真机两项仍欠——设备不在线，本文件不重复它的真机清单。

---

## 给下一轮 Agent 的提示词（整段复制）

```
继续 LuzzyRP 的 P5 批 D。上一轮（会话 78）把批 C 余项五条全部做完并提交
（C3 多候选持久化 / C4 附件 / C5 表格列宽 / C6 撤工作区 / C7 reduced-motion），
门禁 `checkChat` 85 条 / 0 失败、JVM 788 条 / 0 失败；main 领先 origin 4 个提交**未 push**。

接手第一件事（按序，别跳）：
1. 读 `docs/WORKLOG.md` 末尾「会话 78（续）」那一节 + `docs/HANDOFF-p5-batch-d.md`（本文件）。
2. 起模拟器（AVD LuzzyRP_Test：删 *.lock → PowerShell Start-Process → 等 sys.boot_completed=1，
   再等 20 秒）→ `ANDROID_SERIAL=emulator-5554 ./gradlew checkChat` 确认接手基线
   **85 / 0 + JVM 788 / 0**。若模拟器劣化（随机生命周期断言失败、整套耗时翻倍）→ 冷启动再跑，别改代码。

本轮任务（批 D，按序，每项独立提交 + 跑 `:app:testDebugUnitTest`；全部 JVM 可判）：
3. **D1 字号**：`AppSettings.fontScale` + `SettingsStore` 读写（legacy `fontSize` 迁移，
   范围/默认照上游 12–20，见 `LegacySettingsReader` 已读的 fontSize 8..40）；缩放只改两处
   ——`MarkdownTokens`（**注意：全仓目前没有 `LocalMarkdownTokens` 的 provide 点，要新建**，
   建议 `LuzzyThemeEntry` 按读到的 fontScale 提供）+ `LuzzyTypography`；设置页滑杆照
   `WorldInfoPage.kt` L346-390 的 `SettingSlider` 范式。设计门：方向 A 延续豁免三方向
   （会话 78 已完整读过 4 份 SKILL，pro-rules/jetpack-compose 规则 34「不硬编码 sp」是底线），
   在 DESIGN-compose 登记设计自由度。
4. **D2 导入导出（序列化半）**：预设 `presets.json` / 世界书 `world_info.json` =
   `store.records(kind)` 原样 JSON **零映射**（导出 = JsonArray.toString()，导入 =
   replaceRecords）；角色卡 V2 JSON 组装（payload 可能是 `data` 外壳或顶层平铺，
   见 `PromptInputSource.characterViewOf`）；SAF 接线进设置页（运行时验证留真机，如实登记）。
   判据：JVM round-trip（导出→导入→语义相等）。
5. **D3 迁移报告页**：纯函数从 `kv["legacy.migrationCounts"]` 渲染（键的写入点
   `MigrationWriter.markMigrated`，值为 12 个数字字段）；入口照 PLAN 放设置页；
   取数照 `PageDataSource` 模式。判据：JVM 夹具映射 + 仪器化种子显示。
6. **D4 文档反向修正**：PLAN §8 R1-R7 逐条核实（R6 先核实措辞；R7 已由批 A 解决）；
   **DESIGN-compose 回填**（C4 缩略图条 / C7 口径 / C3 候选切换器 / D1 字号——awesome-design-md
   的九节结构，会话 78 探索报告有逐节落点建议）；CHANGELOG v3.0.0 段补批 C/C4 条目；
   跑 `node tools/gen-changelog.mjs`。
7. **收尾**：WORKLOG 会话 79 节点 + 更新本文件（或写新交接）→ `git push origin main`
   （4 + 本轮提交一起推）→ 核对 `git status` 干净、模拟器关闭。

**别做**：P6 切 launcher / 发版（需用户在场）；真机 B 栏（设备不在线，清单在
`HANDOFF-p5-static.md` B 栏）；NAI 生图管线（用户拍板跳过）；设置页写路径的真机验证。
```

---

## 会话 78 干了什么（4 个提交，全部已落 main）

| 提交 | 内容 |
|---|---|
| `8b474e67` | 会话 77 遗留 3 文件补验提交（pinToBottom 测量帧重入 + 仪器化用例修正，checkChat 80/0） |
| `4052005a` | 会话 78 收尾节点文档 + AGENTS §7 三条新坑 |
| `40fbd4cc` | **批 C 前半**：C3 多候选持久化 / C5 表格列宽 / C6 撤工作区 / C7 reduced-motion + `testing/Await.kt` 统一等待纪律 |
| `195b6beb` | **C4 附件真功能**：选图→压缩落盘→payload `imageAttachments`（旧键同构）→三家 wire parts + 待发缩略图条 |

门禁终值：**`checkChat` 85 条 / 0 失败**（426 秒）· **JVM 788 条 / 0 失败**。
设计门：4 份 SKILL 主文档已完整读过（记录在 WORKLOG 会话 78（续）§四），方向 A 延续豁免适用。

## 下一轮要知道的实现事实（省你重新探索的时间）

1. **`LocalMarkdownTokens` 目前没有 provide 点**（全仓零调用）——D1 的两处缩放之一必须先建它。
2. `MarkdownTokens` 定义在 `ui/markdown/MarkdownStyle.kt` L20-43（12 个字段）；Typography 在
   `ui/theme/LuzzyFonts.kt` L83-106（只覆盖 6 个字段，其余用 M3 默认——缩放时默认字段也要处理）。
3. **仪器化测试纪律（会话 78 两条新根因，别再踩）**：
   - 等待一律走 `testing/Await.kt`（推时钟 + `waitForIdle` + 让出真实时间）——
     `compose.waitUntil` 自旋不推进时钟，帧驱动的取数/落盘协程续体恢复不了，表现为「超时」；
   - `setContent` 后**先推一帧再操作**（`mainClock.advanceTimeBy(100)` + `waitForIdle`）——
     否则面板的取数协程不启动，单跑绿整套红的假象；
   - 新用例的落库断言**按内容找**不按行下标（界面下标 ≠ 库行下标，有快照/简报行时错位）。
4. C4 的请求侧：历史带图用户消息走 `rawContent` parts（OpenAI 形态），路径 → data URL 在
   `prepareTurn` 内解析；**三家 wire 已有翻译与对照单测**（`AttachmentWireTest`），别重写。
5. 批 C 后门禁构成：85 仪器化 = 82 + C3 重启 1 + C6 断言 1 + C4 存储 1；788 JVM = 773 + 15。

## 环境事实（与上一份 HANDOFF 一致，重点三条）

- 模拟器 = AVD `LuzzyRP_Test`（emulator-5554）；**长跑会劣化**（随机 `[DESTROYED]` 断言、
  整套耗时翻倍、失败用例飘移）→ 冷启动即恢复，别改代码迎合。
- 结果目录被锁 → 先 `./gradlew --stop` 再删 `app/build/outputs/androidTest-results`。
- 真机 `df97f3c4` 本轮与上轮**均不在线**；真机设置还原（`stay_on_while_plugged_in 0` /
  `screen_off_timeout 600000`）仍是**设备回来第一件事**；A9210 严禁当目标机。
