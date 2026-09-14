# HANDOFF · 会话 80 → 81（P6 静态工作 · B 项在途 · C/D/E 未开始）

> **任务来源（用户指令）**：「先完成 P6 的静态工作，做好完整的工作计划，我们先完成所有代码层的
> 工作，然后再进行 bug 修复与真机检测」——计划已获批准（要点见下）；本轮推进到 B 项暂停。
> **接手第一件事**：读 `docs/WORKLOG.md` 末尾「会话 80」节点 + 本文件，然后按序执行。

---

## 已批准计划要点（不要再重新规划）

1. **A · CoT 流式一致性** ✅ 已提交（`042f131b`）
2. **B · 设置页接真 + 文风过滤开关**（实现完未提交——接手先收尾，见 §一）
3. **C · WebView 路径退役 + launcher 切换**（删除清单见 §二，已获批准即授权）
4. **D · 版本与发版静态件**（§三）
5. **E · 真机验收清单**（§四）
6. **收尾**：WORKLOG 节点 + JVM 3 连跑 + push

**明确不做**（计划已定，别扩面）：变量块模板面板（推迟 v3.0.1）；`filesDir/rphub` 残留清理
（WebView 数据目录绝不可动）；真机回归与 Release 发布（等用户/设备）。

---

## §一 · B 项收尾（第一件事）

1. `./gradlew :app:testDebugUnitTest` 全量（B 改动编译已过，测试未跑——先取基线）；
2. `SettingsStoreTest`（androidTest，**ASCII camelCase 方法名**）补 `styleFilterEnabled`
   往返用例（默认 true / 存取往返 / 关掉再读仍 false）——写前读 `docs/CHAT-REGRESSION.md` §4；
3. 模拟器可用则 `ANDROID_SERIAL=emulator-5554 ./gradlew checkChat`（PowerShell 用
   `$env:ANDROID_SERIAL='emulator-5554'`；Bash 语法前缀在 PowerShell 不生效）；
4. 提交：`feat(v3.0-P6): 设置页接真 + 文风过滤开关接线（撤假开关）`——message 说清
   「此前三处硬编码 true（会话 76 A4 欠的 UI 开关），现三处同源 + 设置页可切」。

## §二 · C 项 · WebView 路径退役 + launcher 切换（大项）

**已核实的事实（勿再侦察）**：
- 迁移通道与 MainActivity/rphub **解耦**：`MigrationRunner` 自建隐藏 WebView +
  `WebViewSetup.configure` + `LuzzyBridge`（`migrationDoneSink` + migrate* 四方法），
  导出页 `files/ext/luzzy-migrate.html` 直接调 `window.LuzzyBridge`（不依赖 luzzy-bridge.js）；
- 旧数据在 WebView IndexedDB（filesDir 下），**不在 rphub 资产里**——删资产不影响迁移；
- v2.x 的 AssetExtractor 也解压 `ext/` → 升级设备上迁移页已就位；
- `assetSignature()` 构建期自动适配资产删除（签名变化 → 设备侧重解压）。

**动作清单**：
1. `AndroidManifest.xml`：launcher intent-filter 从 `.MainActivity` 移到 `.ui.ComposeActivity`；
   删 `.MainActivity` 声明；删权限 `READ_CALENDAR`/`WRITE_CALENDAR`（助手已移除）与
   `WRITE_EXTERNAL_STORAGE`（DownloadHandler 专用）；保留 `INTERNET`/`ACCESS_NETWORK_STATE`。
2. 删文件：`MainActivity.kt`、`web/DownloadHandler.kt`、`web/FileChooserHandler.kt`、
   `assets/rphub/**`（20.5MB）。**保留**：`web/LuzzyBridge.kt`、`web/WebViewSetup.kt`、
   `assets/ext/` 全部（迁移页在其中；v2.x 其余扩展文件不再被执行但无害，不清理）。
3. `AssetExtractor.kt`：`ROOTS` 只留 `"ext" to "ext"`；`ensureExtracted` 返回值改为迁移页
   `File`（原 rphub 入口无意义）或拆成 `ensureExtExtracted()`；**解压触发点**从 MainActivity
   移到 `ComposeActivity.onCreate`（`MigrationCoordinator.ensureMigrated` **之前**——
   全新安装也要先解压出迁移页）。
4. 全局搜 `MainActivity`/`RPHub`/`rphub` 的残留引用（含注释）逐一处理。
5. 门禁：JVM 全绿 + `compileDebugAndroidTestKotlin` 通过 + `assembleRelease` 构建过。
6. 提交：`feat(v3.0-P6): launcher 切换 + WebView 路径退役 + 版本 3.0.0`（与 D 的版本号同批，
   互为依存不可拆）。

## §三 · D 项 · 版本与发版静态件

1. `app/build.gradle.kts`：`versionCode 14` / `versionName "3.0.0"`；
2. `docs/release-notes-v3.0.0.md` 草稿（照 `release-notes-v1.4.0.md` 排版；内容从
   CHANGELOG v3.0.0 段提炼；**Release 发布与附 APK 留到真机回归通过后**）；
3. `node tools/gen-changelog.mjs` 重跑（README 版本行 + 应用内 changelog）；
4. 本地 `./gradlew :app:assembleRelease` → 确认 `app/build/outputs/apk/release/` **只有一个
   `app-release.apk`**；`apksigner verify --print-certs` 指纹 =
   `ed78235d2945d2c075b5b1ce92c3eb8ab23222382007fb87dd7cc3f32a9dffb1`（不变）；体积对比（-20MB）。

## §四 · E 项 · 真机验收清单

新建 `docs/HANDOFF-p6-device.md`：合并 ① `CHAT-REGRESSION.md` 十步走查、
② `HANDOFF-p5-static.md` B 栏（B1 行内高亮目视 / B2 页面亮暗目测 / B3 缓存命中率复核 /
B6 压缩水位线清理——写用户数据先备份复述）、③ P6 专项（launcher 切换后冷启动 → 老数据
自动迁移 → 数据完好 → 设置页/迁移报告 Dialog 目测 → SAF 导入导出走查 → 发版检查单）、
④ 真机设置还原两条命令（`stay_on_while_plugged_in 0` / `screen_off_timeout 600000`）。
逐条带操作步骤、预期、判据。

## 收尾（全部完成后）

WORKLOG 节点收尾 → JVM 3 连跑（`--rerun-tasks`）→ `git push origin main`
（HEAD `042f131b` 起 + 本轮全部提交一起推）→ `git status` 干净 → 临时文件清理。

## 环境事实（踩过的坑，别再踩）

- 模拟器 AVD `LuzzyRP_Test`：删 `*.lock` → PowerShell `Start-Process` 独立进程 →
  `sys.boot_completed=1` + 20s；长跑劣化就冷启动，**别改代码迎合**；GPU 配置不动（`hw.gpu.enabled=no`
  是唯一能启动的组合）；结果目录被锁 → 先 `./gradlew --stop` 再删 `androidTest-results`。
- 仪器化只跑模拟器（`verifyEmulatorDevice` 硬门）；真机 `df97f3c4` 只装 release 包人工目视。
- JVM 反引号测试名**不能含点号**（空格可以）；androidTest 方法名一律 ASCII camelCase。
- PowerShell 下 adb/gradle 的环境变量用 `$env:NAME='v'`，bash 前缀语法无效；
  管道会让 exit code 失真——**以输出 BUILD 行为准**。
- 委托属性（`by mutableStateOf`）不能 smart cast——判空后先落局部变量。
