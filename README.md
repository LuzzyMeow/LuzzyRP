<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" alt="LuzzyRP 图标" />

# LuzzyRP

**RP-Hub 的 Android WebView 封装**

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-JVM%2017-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Upstream](https://img.shields.io/badge/RP--Hub-2.0.6-4FC08D?logo=vue.js&logoColor=white)](https://github.com/STA1N156/RP-Hub)
[![License](https://img.shields.io/badge/License-AGPL--3.0%20%2B%20CC%20BY--NC%204.0-3E6389)](#许可证与署名)

</div>

> [!IMPORTANT]
> 项目已重启。当前版本不再包含旧版的 `assets/ext` 扩展层、`LuzzyBridge`、独立主题或同步补丁工具链。
> 现在它是一个尽量保持上游原貌的 Android WebView 壳：业务界面和前端逻辑来自 RP-Hub 2.0.6，Kotlin 只负责启动、资源解压、文件选择、下载和系统界面适配。

## 下载

前往 [Releases](https://github.com/LuzzyMeow/LuzzyRP/releases) 下载最新的 Debug APK。

| 项目 | 当前值 |
| --- | --- |
| 应用版本 | `2.0.6.1-debug` |
| versionCode | `21` |
| Debug 包名 | `com.luzzymeow.luzzyrp.debug` |
| Release 包名 | `com.luzzymeow.luzzyrp` |
| 最低系统 | Android 8.0（API 26） |
| targetSdk / compileSdk | 37 / 37 |

Debug 包使用 Android 调试签名，可与 Release 包共存，但不作为正式签名分发版本。

## 项目定位

LuzzyRP 将 RP-Hub 2.0.6 的静态前端打包进 APK，并在应用私有目录中解压后通过 WebView 加载。壳不实现聊天、角色卡、记忆或模型请求逻辑，也不充当聊天后端。

当前实现保留的能力：

- 主对话、角色卡工坊和小说页面；
- 角色卡、世界书、正则、剧情分支、记忆、工具调用等上游前端能力；
- 本地资源解压与版本签名失效机制；
- 系统文件选择器，用于 `input[type=file]`；
- 常规 HTTP/HTTPS 下载，保存到系统下载目录；
- WebView 返回键、系统栏与刘海屏 inset 适配；
- Debug 构建的 WebView 远程调试；
- JavaScript Console 转发到 Logcat，标签为 `JSConsole`。

## 运行要求与限制

- 需要 Android 8.0 或更高版本，并建议保持系统 WebView 为最新版本。
- 应用页面、主要业务脚本和 `vendor` 下的三个运行时库随 APK 打包，但 Vue、Tailwind、字体及部分嵌入页依赖仍从 CDN 加载，因此不是完全离线应用。
- 调用模型服务需要网络，并且服务端必须允许来自 WebView 页面的跨域请求（CORS）。
- 当前下载桥只处理常规 HTTP/HTTPS URL。`blob:` URL 由 WebView 内部生成，系统 `DownloadManager` 无法消费，相关前端导出不会由原生层接管。
- 当前没有 `addJavascriptInterface` 或旧版 `LuzzyBridge`。前端不能调用旧的 Luzzy 原生扩展接口。
- `presence-server` 是可选更新检测与云端备份服务，不是聊天后端，也不是运行 Android 壳的必需组件。

## 构建

### 环境

| 工具 | 版本或位置 |
| --- | --- |
| JDK | 21 |
| Android SDK | API 37 |
| Gradle | 9.4.1，使用仓库 Wrapper |
| Android Gradle Plugin | 9.2.1 |
| Kotlin/JVM 目标 | Java 17 / JVM 17 |

本机参考路径：

- JDK：`D:\.Luzzy\Tool\Java\jdk-21`
- Android SDK：`D:\.Luzzy\Tool\Android\Sdk`
- Gradle 用户目录：`D:\.Luzzy\Tool\Gradle`

### Windows PowerShell

```powershell
cd D:\.Luzzy\LuzzyRP
$env:JAVA_HOME = 'D:\.Luzzy\Tool\Java\jdk-21'
$env:ANDROID_HOME = 'D:\.Luzzy\Tool\Android\Sdk'
$env:ANDROID_SDK_ROOT = 'D:\.Luzzy\Tool\Android\Sdk'
$env:GRADLE_USER_HOME = 'D:\.Luzzy\Tool\Gradle'
.\gradlew.bat :app:assembleDebug --no-daemon
```

如果环境变量已经生效，可以只运行：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
```

### macOS / Linux

```bash
./gradlew :app:assembleDebug --no-daemon
```

默认只构建 Debug。除非明确需要，不要执行 `assembleRelease`。

构建产物：

```text
app/build/outputs/apk/debug/app-debug.apk
```

本机每次生成新的 Debug APK 后，会同步副本到：

```text
C:\Users\Administrator\Desktop\LuzzyRP-2.0.6.1-debug.apk
```

## 架构

```text
MainActivity
├── AssetExtractor
│   └── assets/rphub → filesDir/rphub
├── WebViewSetup
│   └── file://.../rphub/index.html
├── FileChooserHandler
│   └── WebChromeClient.onShowFileChooser → 系统文件选择器
├── DownloadHandler
│   └── DownloadListener → DownloadManager（仅 HTTP/HTTPS）
└── WindowInsets / OnBackPressedDispatcher
    └── 系统栏适配与 WebView 返回导航
```

### 资源更新

`AssetExtractor` 只解压 `app/src/main/assets/rphub`。构建时，`app/build.gradle.kts` 根据文件数量、总大小和最新修改时间生成 `BuildConfig.ASSET_SIGNATURE`；应用私有目录中的签名不一致时重新解压资源。

新增资源根目录时，必须同步调整 `AssetExtractor.kt` 和签名计算。

### Debug 调试

- WebView 远程调试仅在 Debug 构建开启；
- 前端 Console 输出可在 Logcat 中按 `JSConsole` 标签查看；
- 可用 `adb logcat -s JSConsole` 过滤前端日志。

## 目录结构

```text
LuzzyRP/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/rphub/                 # RP-Hub 2.0.6 上游静态资源
│       │   ├── index.html               # 主页面
│       │   ├── character/index.html     # 角色卡工坊
│       │   ├── novel/index.html         # 小说页面
│       │   ├── assets/css/              # 页面样式与主题变量
│       │   ├── assets/js/               # 前端业务逻辑
│       │   ├── presence-server/         # 可选更新检测与云端备份服务
│       │   └── vendor/                   # marked、DOMPurify、Sortable
│       ├── java/com/luzzymeow/luzzyrp/
│       │   ├── MainActivity.kt
│       │   ├── util/AssetExtractor.kt
│       │   └── web/
│       │       ├── WebViewSetup.kt
│       │       ├── FileChooserHandler.kt
│       │       └── DownloadHandler.kt
│       └── res/                          # 图标、颜色与应用名称
├── gradle/
│   ├── libs.versions.toml                # 依赖版本统一入口
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── LICENSE                              # 上游 CC BY-NC 4.0
├── LICENSE-AGPL-3.0                     # AGPL-3.0
└── README.md
```

## 开发约定

- `app/src/main/assets/rphub` 视为上游原始树。升级上游时整棵替换，再补回 `vendor` 下三个本地运行时库。
- 不要恢复已删除的 `assets/ext`、旧 JS bridge 或旧同步工具脚本。
- `vendor` 下只保留 `marked.min.js`、`purify.min.js`、`Sortable.min.js`。
- `built-in-content.js` 中的 `<nsfw_rules>` 规则段不可修改。
- 升级上游后同步修改 `app/build.gradle.kts` 的 `versionCode` / `versionName`、`WebViewSetup.kt` 的 UA 后缀，并重新构建 Debug。
- UA 后缀当前为 `LuzzyRP/2.0.6.1`。
- `local.properties`、`keystore.properties`、构建产物和 `.gradle` 均不提交。
- Release 签名需要根目录 `keystore.properties` 和对应密钥文件；没有签名配置时 Debug 仍可正常构建。
- 当前没有测试源码。最低验证门槛是 `:app:assembleDebug` 成功；不要声称运行了不存在的自动化测试。

## 与上游的关系

- 上游仓库：[STA1N156/RP-Hub](https://github.com/STA1N156/RP-Hub)
- 上游在线页面：[sta1n156.github.io/RP-Hub](https://sta1n156.github.io/RP-Hub/)
- 当前基线：RP-Hub 2.0.6
- LuzzyRP 的改动集中在 Android 壳、资源打包、文件与下载适配，以及重启时对旧扩展层的移除。

上游前端文档分别位于：

- [简体中文说明](app/src/main/assets/rphub/README.md)
- [English README](app/src/main/assets/rphub/README.en.md)

## 许可证与署名

本项目基于 **[RP-Hub](https://github.com/STA1N156/RP-Hub)** 二次封装，上游作者为 **STA1N156**。

- `app/src/main/assets/rphub` 中的上游内容遵循 **Creative Commons Attribution-NonCommercial 4.0 International（CC BY-NC 4.0）**，完整文本见 [`LICENSE`](LICENSE)。
- LuzzyRP 原创 Android 壳及本项目材料按 **GNU Affero General Public License v3.0（AGPL-3.0）** 提供，完整文本见 [`LICENSE-AGPL-3.0`](LICENSE-AGPL-3.0)。
- 使用、修改和分发时必须保留署名、许可文本，并标明修改。上游明确禁止商业用途；本项目同样不用于商业分发、付费集成或广告变现。

## 验证状态

当前仓库没有自动化测试源码，也没有连接可用于安装验证的 Android 设备。发布流程以 Debug 构建、APK 元数据和文件校验为主要验证依据；真机首页、返回键与文件选择验证需在设备可用时执行。

问题与建议请提交到 [Issues](https://github.com/LuzzyMeow/LuzzyRP/issues)。
