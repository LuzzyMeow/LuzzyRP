<div align="center">

<img src="app/src/main/res/drawable-nodpi/luzzy_logo.png" width="96" alt="LuzzyRP"/>

# LuzzyRP

**"每次对话，都像一本有你的小说。"**

移动端 AI 角色扮演应用 · 基于 [RP-Hub](https://github.com/STA1N156/RP-Hub)（Vue 3 Web 前端）二次开发的安卓 **WebView 封装**（Kotlin 薄壳 + 独立扩展层）

</div>

> [!IMPORTANT]
> ## 📖 二创声明（Attribution）
>
> 本项目基于开源项目 **[RP-Hub](https://github.com/STA1N156/RP-Hub)（作者：STA1N156）** 二次开发，上游基线版本 **1.9.0**。
>
> - 遵循上游 **CC BY-NC 4.0（署名-非商业性使用 4.0 国际）** 许可协议；
> - 仅对**前端**进行优化与封装，**后端代码（presence-server）完全未动**；
> - 修改范围：安卓壳封装、资源离线化、品牌化、独立扩展层（详见 [CHANGELOG](CHANGELOG.md)）；
> - 上游原 LICENSE 文件保留于仓库内，本项目的修改与新增部分同样以 CC BY-NC 4.0 发布。

![Status](https://img.shields.io/badge/Status-v1.2.3--正式版·可游玩-10B981)
![Android](https://img.shields.io/badge/Android-Native%20WebView-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin&logoColor=white)
![Upstream](https://img.shields.io/badge/Upstream-RP--Hub%201.9.0-4FC08D?logo=vue.js&logoColor=white)
![License](https://img.shields.io/badge/License-CC%20BY--NC%204.0-F5A623)

---

## 目录

- [简介](#简介)
- [核心特性](#核心特性)
- [技术架构](#技术架构)
- [与上游的关系](#与上游的关系)
- [构建与运行](#构建与运行)
- [目录结构](#目录结构)
- [开发者须知](#开发者须知)
- [版本规划](#版本规划)
- [许可证与合规](#许可证与合规)
- [免责声明](#免责声明)

---

## 简介

LuzzyRP 是一款安卓端 AI 角色扮演应用。它不是一个从零开始的工程——而是把成熟、可玩、功能完整的开源项目 **RP-Hub**（Vue 3 Web 前端）通过原生 WebView 完整封装进安卓（Kotlin 单 Activity 薄壳），并在此基础上持续做**前端优化**与**独立功能扩展**。

选择这条路的理由很直接：**开箱即玩**。RP-Hub 本身已经具备角色卡、世界书、正则脚本、剧情分支、双轨记忆、自动生图等完整能力，套壳后直接获得成熟可玩的 RP 体验，不再需要从零验证核心玩法链路。

### 设计理念

- **成熟优先**：站在 RP-Hub 的肩膀上，把精力花在体验优化而非重复造轮子；
- **隔离扩展**：所有二创新功能与上游代码物理隔离，上游更新可无缝同步；
- **离线可用**：全部运行时依赖本地化，断网也能完整使用（除真实 API 请求外）；
- **隐私本地**：数据全部存储在设备本地，无任何遥测与云端收集。

---

## 核心特性

### 来自上游 RP-Hub（原样保留）

| 能力 | 说明 |
|------|------|
| 角色卡生态 | 角色卡、世界书、正则脚本和多用户资料管理 |
| 双轨记忆 | 总结记忆与向量记忆，可按角色和剧情分支独立保存 |
| 剧情分支 | 分支创建、切换、回档、重命名和完整导入导出 |
| UI 模板 | 模板变量分析与对话状态展示 |
| 自动生图 | 自动生图、单张重新生成和多套内置画师风格 |
| 在线工具 | 角色卡生成、万相广场与「墨韵 · 造梦」在线工具 |
| NSFW 协议 | 内置 NSFW 增强预设（`<nsfw_rules>`），**原样保留、不可触碰** |

### 来自 LuzzyRP 壳（二创新增）

| 能力 | 说明 |
|------|------|
| 原生安卓封装 | 单 Activity WebView 壳，本地加载，无网络依赖 |
| 资源离线化 | Vue / Tailwind / marked / DOMPurify / SortableJS / Lora 字体全部本地打包 |
| 文件桥接 | 角色卡 PNG/JSON 导入导出走系统文件选择器（SAF） |
| 系统能力桥 | 剪贴板、Toast、版本信息、系统浏览器打开外链等原生能力通过 JSBridge 暴露给前端 |
| 独立扩展层 | `luzzy-theme.css` / `luzzy-bridge.js` / `luzzy-ext.js` / `luzzy-changelog.js` 独立文件承载二创新功能，与上游零冲突 |
| **主题系统** | 「暖幕手记 × Claude」主题（亮/暗双模式）+ 经典（原版）主题；侧边栏「外观」独立页切换；新用户默认暖幕手记，老用户保留经典 |
| **字体系统** | 「Luzzy 默认」字体（Alibaba Sans 拉丁 + Alibaba PuHuiTi 3 中文，本地打包）；经典（原版）/经典衬线（Lora）/系统可选 |
| **统一雾纸玻璃** | 聊天页全部表面纳入玻璃族（气泡/生成中/思考卡/工具条，立绘透色），流式自动加厚保可读；经典主题零影响（v1.2.0） |
| **多模型商混用** | 供应商管理器 + `[商名]` 来源徽标 + `providerId::bareId` 复合引用；聊天/识图/记忆（总结+向量分桶）全槽位跨商（v1.1.0） |
| **三协议供应商** | 自定义供应商支持 OpenAI / Anthropic / Gemini 接口；二级编辑弹窗管理模型（请求 id/显示 id/上下文长度/最大输出/输入模态/类型/自定义请求体）+ 五组模型 id 热检测预设 + 引用重映射 + 模型列表热更新（v1.2.0） |
| **自定义生图模型** | 生图来源可选官方 STA1N（NAI 代理）或供应商 image 模型（OpenAI 协议，b64 直出）（v1.2.0） |
| **外观 / 关于独立页** | 侧边栏独立入口：外观（主题/模式/字体/字号，全应用唯一入口）+ 关于（版本/署名/**应用内 CHANGELOG**）（v1.2.0） |
| 上游同步机制 | 覆盖 + patch 重放脚本化同步，跟随上游持续迭代 |

---

## 技术架构

```
┌─────────────────────────────────────────────────┐
│  LuzzyRP 安卓应用（Kotlin 壳工程）                │
│                                                 │
│  ┌─────────────────────────────────────────┐  │
│  │  WebView（加载 filesDir/rphub/index.html） │  │
│  │  ┌───────────────────────────────────┐  │  │
│  │  │  RP-Hub 上游文件（仅登记 patch）    │  │  │
│  │  │  index.html · assets/js/*.js      │  │  │
│  │  │  assets/css/styles.css            │  │  │
│  │  └───────────────────────────────────┘  │  │
│  │  ┌───────────────────────────────────┐  │  │
│  │  │  二创扩展层（独立文件，零冲突）       │  │  │
│  │  │  luzzy-ext.js · luzzy-theme.css   │  │  │
│  │  │  luzzy-bridge.js · luzzy-changelog.js │  │  │
│  │  └───────────────────────────────────┘  │  │
│  └──────────────┬──────────────────────────┘  │
│                 │ addJavascriptInterface      │
│  ┌──────────────▼──────────────────────────┐  │
│  │  JSBridge 原生层（LuzzyBridge.kt）         │  │
│  │  文件选择 · 导出 · 剪贴板 · 系统栏 · 外链  │  │
│  └─────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

| 层 | 内容 | 与上游关系 |
|----|------|-----------|
| 上游层 | RP-Hub 6 个 JS + styles.css + index.html（仅登记 patch） | 覆盖式同步 |
| 扩展层 | `luzzy-ext.js` / `luzzy-theme.css` / `luzzy-bridge.js` / `luzzy-changelog.js` | 完全独立，零冲突 |
| 原生层 | WebView 壳 + `LuzzyBridge.kt` + 系统能力 | 完全独立 |

### 技术栈

| 层 | 选型 |
|----|------|
| 壳语言 | Kotlin 2.4.0（JVM 17 字节码 target，jvmToolchain(21)） |
| 宿主 | 单 Activity + WebView（系统 WebView，minSdk 26 起） |
| 前端 | Vue 3（本地打包）· Tailwind CSS（本地打包）· DaisyUI |
| 前端依赖 | marked · DOMPurify 3.0.6 · SortableJS（全部本地化） |
| 字体 | Alibaba Sans + Alibaba PuHuiTi 3（默认）· Lora 衬线 · 上游经典栈，全部本地打包 |
| 构建 | Gradle · AGP · 现有签名（keystore/luzzy-release.keystore） |
| SDK | compileSdk 37 · minSdk 26 · targetSdk 37 |

---

## 与上游的关系

### 同步策略：覆盖 + patch 重放

```
上游发版 → git fetch upstream → 覆盖上游文件 → 重放登记 patch → 回归实测 → 构建发布
```

- 上游文件仅允许通过 `tools/patches/` 内**登记**的 patch 修改（品牌标题、禁用更新检查、入口 logo、扩展层挂载）；
- 二创新功能全部落在 `assets/ext/` 独立文件，同步时零冲突；
- 同步 SOP 详见 [AGENTS.md](AGENTS.md) 与 `tools/sync-upstream.ps1`。

### 版本对应

| LuzzyRP 版本 | 上游基线 | 说明 |
|--------------|---------|------|
| v1.2.3（正式版） | RP-Hub 1.9.0 | 上游同步（破限标记 rphub_default）+ 用量趋势折线图（patch 025）+ 关于页工具化/置顶按钮（patch 024）+ 向量检索修复（patch 026）+ 设置页清理（patch 021）+ 自创开屏「开卷 · 门扉」（patch 027 v3）+ 主题单轨化（patch 028）+ CHANGELOG 自动同步与资产签名解压。真机全量验证，附 APK |
| v1.2.2（正式版） | RP-Hub 1.8.9 | 全新品牌图标（White Fox）+ toggle/叙事视角蓝主题化（patch 008 v4）+ 向量检索失败 toast 外化（patch 020）。真机验证通过，附 APK |
| v1.2.0（正式版） | RP-Hub 1.8.9 | 聊天页统一雾纸玻璃补全 + 外观/关于独立页（应用内 CHANGELOG）+ 三协议供应商（OpenAI/Anthropic/Gemini）+ 模型管理编辑器与热检测预设 + 自定义生图模型（模拟器全量走查，附 APK） |
| v1.2.1（正式版） | RP-Hub 1.8.9 | 侧栏品牌化（LuzzyRP 字样）+ 外观→设置→关于导航调整 + 主题预览交互化（随主题取色/点击切换亮暗）+ 召回块防合并 + 记忆内容管理器 + 品牌色收编 + 上游标记体系（001-019）+ 布局异常修复。模拟器+真机 release 包双端验证通过 |
| v1.1.0（正式版） | RP-Hub 1.8.9 | 多模型商混用（供应商管理器 + `[商名]` 徽标 + 跨商模型列表/请求解析/记忆双模式）+ 思考卡全卡雾纸玻璃 + 外观独立面板（真机验证，附 APK） |
| v1.0.0（正式版） | RP-Hub 1.8.9 | 重建落地：主题「暖幕手记 × Claude」+ 雾纸玻璃层 + 字体系统 + 二创壳全链路（真机验证，附 APK） |
| v1.0.0-rc3 | RP-Hub 1.8.9 | 雾纸玻璃层 Frost-Paper（液态玻璃方向板三选一，chrome 磨砂 + 气泡纸感）+ 上游 ！important 白面暗色收编 |
| v1.0.0-rc2 | RP-Hub 1.8.9 | 主题系统「暖幕手记 × Claude」+ 字体系统 + 暗色修复（模拟器+真机验证） |
| v1.0.0 | RP-Hub 1.8.9 | 重建首版：壳 + 离线化 + 品牌 + 桥接 + 扩展层 + 主题 |

---

## 构建与运行（开发者）

```bash
# 环境要求：JDK 21 · Android SDK 37
git clone git@github.com:LuzzyMeow/LuzzyRP.git
cd LuzzyRP
./gradlew assembleDebug        # Debug 构建（可直接安装尝鲜）
./gradlew assembleRelease      # Release 构建（需 keystore.properties，见下）
```

**签名配置**：仓库根创建 `keystore.properties`（不入库）：

```properties
storeFile=keystore/luzzy-release.keystore
storePassword=***
keyAlias=luzzy
keyPassword=***
```

**产物**：ABI 拆分三件套（arm64-v8a / x86_64 / universal）。

> [!NOTE]
> 应用为**侧载分发**，不上架应用商店。安装前需在系统设置中允许「安装未知来源应用」。

---

## 目录结构

```
LuzzyRP/
├── app/                          # 安卓壳工程（Kotlin）
│   └── src/main/
│       ├── java/com/luzzymeow/luzzyrp/
│       │   ├── MainActivity.kt           # 单 Activity，WebView 宿主
│       │   ├── web/                      # WebView 配置 / JSBridge / 文件桥
│       │   └── util/                     # assets 解压等工具
│       ├── res/                          # 启动图标（mipmap 全套 + luzzy_logo）
│       └── assets/
│           ├── rphub/                    # RP-Hub 上游文件（同步目标）
│           │   ├── index.html
│           │   ├── character/ novel/
│           │   ├── assets/css/ assets/js/
│           │   ├── vendor/               # 离线化 CDN 依赖
│           │   └── fonts/                # Lora 本地字体
│           └── ext/                      # 二创扩展层（独立文件）
│               ├── luzzy-bridge.js
│               ├── luzzy-theme.css
│               ├── luzzy-ext.js
│               ├── luzzy-changelog.js    # 关于页 CHANGELOG（tools/gen-changelog.mjs 生成）
│               └── luzzy-logo.png
├── rp-hub-reference/             # 上游参考克隆（保留 upstream remote，不入库）
├── tools/
│   ├── sync-upstream.ps1         # 上游同步脚本
│   ├── apply-patches.ps1         # patch 重放脚本
│   ├── gen-changelog.mjs         # 关于页 CHANGELOG 生成脚本（发版后运行）
│   ├── patches/                  # 二创登记 patch（001-020，entities/ 为 012+ 实体重放）
│   └── upstream-fingerprints.txt # 上游文件指纹基线
├── docs/                         # 规划 / 日志 / 设计存档 / 归档
├── keystore/                     # 签名（不入库）
├── CHANGELOG.md                  # 更新日志（沿用既有格式）
├── AGENTS.md                     # 后续开发 Agent 指南
├── HARD_REQUIREMENTS.md          # 硬性规定（10 条）
├── LICENSE                        # CC BY-NC 4.0
└── README.md                     # 本文件
```

---

## 开发者须知

**接手开发前必读**（按顺序）：

1. [`HARD_REQUIREMENTS.md`](HARD_REQUIREMENTS.md) —— 10 条硬性规定（NSFW 不可触碰 / 上游最小改动 / 扩展层隔离 / 字体锁定 / CHANGELOG 同步 / 同步纪律 / 工作区整洁 / 发布流程 / **设计 SKILL 强制条款** / **改动标记与上游同步适配**），**违反任何一条即为不合格交付**；
2. [`AGENTS.md`](AGENTS.md) —— 后续开发/更新/维护 Agent 工作指南（文件地图 / 工作流程 / 同步 SOP / 扩展开发规范）；
3. [`docs/PLAN-v1.2.1.md`](docs/PLAN-v1.2.1.md) —— 最近版本（v1.2.1）完整实施计划；
4. [`docs/WORKLOG.md`](docs/WORKLOG.md) —— 工作日志（跨会话连续记忆）；
5. [`CHANGELOG.md`](CHANGELOG.md) —— 版本记录（格式：`### vX.Y.Z — 标题` + 分类要点 + 构建结果）。

---

## 版本规划

| 版本 | 内容 | 附 APK |
|------|------|--------|
| v1.0.0 | 重建首版：壳 + 离线化 + 品牌 + 桥接 + 主题/字体/雾纸玻璃系统 + 同步机制（✅ 已发布，附 APK） | ✓（稳定版） |
| v1.1.0 | 扩展功能第一批：多模型商混用（供应商管理器 / `[商名]` 来源徽标 / 跨商请求解析 / 记忆双模式跨商）+ 思考卡全卡雾纸玻璃 + 外观独立面板（✅ 已发布，附 APK） | ✓（稳定版） |
| v1.2.0 | 玻璃补全 + 独立页 + 三协议：聊天页统一雾纸玻璃（气泡/loading/思考卡/工具条）+ 外观/关于独立页（应用内 CHANGELOG）+ 供应商三协议（OpenAI/Anthropic/Gemini）+ 模型管理编辑器（热检测预设/引用重映射/热更新）+ 自定义生图模型（✅ 已发布，附 APK） | ✓（稳定版） |
| v1.2.1 | 召回块防合并修复 + 记忆内容管理器 + 品牌色收编蓝色（开屏/设置横幅）+ 上游标记体系（硬性规定 10 / 实体重放 / verify-markers）+ 侧栏品牌化/预览交互化 + 应用图标粉底修复（✅ 已发布，附 APK） | ✓（稳定版） |
| v1.2.2 | **全新品牌图标**（White Fox 头像版，纯 1:1 满幅，adaptive 改全图前景+同色背景）+ toggle/叙事视角上游蓝主题化（patch 008 v4 色板收编：luzzy=珊瑚、classic=原值）+ 向量检索失败 toast 外化（patch 020）（✅ 已发布，附 APK） | ✓（稳定版） |
| v1.2.3 | 上游 1.9.0 同步 + 六大功能：开卷门扉开屏（patch 027 v3）/向量检索修复（patch 026）/关于页工具化（patch 024）/用量趋势图（patch 025）/设置页三修（patch 021/023）/全屏按钮移除（patch 022）+ 主题单轨化（patch 028）+ CHANGELOG 自动同步 + 资产签名解压 + 置顶按钮修复（✅ 已发布，附 APK） | ✓（稳定版） |
| v1.3.0 | 性能治理（流式降载 / 高频面退实底 / 合成层瘦身 / 开屏去 blur / 发送键热区根除，patch 032-034）+ 内置供应商精简仅留 DeepSeek 且可编辑（patch 029，含老用户无损迁移）+ 记忆召回思考节点（patch 031）+ 关于页上游版本号固化（patch 030）（🚧 开发中） | 按稳定版 |
| v1.3.0+ | 跟随上游节奏迭代 + 独立功能持续扩展（候选遗留：styles.css 低频硬编码蓝收编、向量阈值滑杆、「荧光笔落笔」招牌动效、深链、自建更新检查、Gemini/Anthropic 图像模型接生图流） | 按稳定版 |

版本号独立于上游（LuzzyRP v1.0.0 基于 RP-Hub 1.8.9），CHANGELOG 每条记录注明上游基线版本。

---

## 许可证与合规

本项目以 [CC BY-NC 4.0](LICENSE)（署名-非商业性使用 4.0 国际）许可开源，与上游 RP-Hub 一致。

| 义务 | 落实 |
|------|------|
| 署名 | 本 README 顶部二创声明 + 保留上游 LICENSE 文件 |
| 非商业 | 禁止任何形式的商业化使用（售卖、付费订阅、广告盈利） |
| 修改声明 | CHANGELOG 与 README 注明上游基线版本与修改范围 |

**合规红线**：上游 LICENSE 文件原样保留；仅侧载分发，不上架应用商店。

---

## 免责声明

- 本项目为个人学习与娱乐用途的二次开发作品，与上游 RP-Hub 无官方关联；
- 应用内 AI 生成内容由用户自行配置的模型 API 产生，本项目不对生成内容负责；
- 请遵守所在地区法律法规，合理使用。

<div align="center">
<sub>LuzzyRP · Based on RP-Hub by STA1N156 · CC BY-NC 4.0</sub>
</div>
