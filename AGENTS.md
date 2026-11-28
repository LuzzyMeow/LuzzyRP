# AGENTS.md

## 项目定位

LuzzyRP 是 RP-Hub 的 Android WebView 封装。Kotlin 壳负责启动 WebView、解压静态资源、文件选择/下载和系统栏适配；业务界面全部来自 app/src/main/assets/rphub 中的 RP-Hub 2.0.6 前端。

- applicationId: com.luzzymeow.luzzyrp
- versionCode: 22
- versionName: 2.0.6.2
- compileSdk / targetSdk: 37
- minSdk: 26
- Kotlin，AGP 9.2.1，Gradle 9.4.1，JDK 21

## 目录结构

- app/src/main/java/com/luzzymeow/luzzyrp/MainActivity.kt：单 Activity 宿主；创建 WebView、绑定下载与文件选择、处理返回键和系统栏 insets。
- app/src/main/java/com/luzzymeow/luzzyrp/util/AssetExtractor.kt：把 assets/rphub 解压到应用私有目录，使用 BuildConfig.ASSET_SIGNATURE 判断是否需要重新解压。
- app/src/main/java/com/luzzymeow/luzzyrp/web/WebViewSetup.kt：WebView 设置（JavaScript、DOM Storage、本地文件访问、UA 后缀 LuzzyRP/2.0.6.2）。
- app/src/main/java/com/luzzymeow/luzzyrp/web/DownloadHandler.kt：下载处理。
- app/src/main/java/com/luzzymeow/luzzyrp/web/FileChooserHandler.kt：文件选择器与 JS console 转发（Logcat tag: JSConsole）。
- app/src/main/java/com/luzzymeow/luzzyrp/web/AnysearchBridge.kt：Anysearch 受限原生桥，只允许搜索与网页提取。
- app/src/main/assets/rphub：上游 RP-Hub 2.0.6 静态资源（26 个上游文件 + vendor 下 3 个运行时库）；这是应用界面和业务逻辑的唯一来源。
- app/src/main/assets/rphub/assets/fonts：本地阿里巴巴普惠体 3.0 字体文件，作为全局字体兜底。
- app/src/main/res/mipmap-*：应用图标（普通、圆形、自适应前景）；自适应图标背景色为 #FDEFDC。
- gradle/libs.versions.toml：依赖版本统一入口。
- local.properties：本机 SDK 路径，已被 .gitignore 忽略，不要提交。

## 构建环境（本机参考）

- JDK 21：D:\.Luzzy\Tool\Java\jdk-21
- Android SDK：D:\.Luzzy\Tool\Android\Sdk
  - platform-tools 37.0.3
  - build-tools 36.0.0 / 37.0.0
  - platforms/android-37.0
  - cmdline-tools latest 23.0.0
- Gradle 用户目录：D:\.Luzzy\Tool\Gradle
- 用户级环境变量已设置 JAVA_HOME、ANDROID_HOME、ANDROID_SDK_ROOT、GRADLE_USER_HOME；已打开的终端需要重启才会继承。

如果环境变量不存在，显式指定：

    cd D:\.Luzzy\LuzzyRP
    JAVA_HOME='D:/.Luzzy/Tool/Java/jdk-21' GRADLE_USER_HOME='D:/.Luzzy/Tool/Gradle' ./gradlew :app:assembleDebug --no-daemon

PowerShell：

    cd D:\.Luzzy\LuzzyRP
    $env:JAVA_HOME = 'D:\.Luzzy\Tool\Java\jdk-21'
    $env:GRADLE_USER_HOME = 'D:\.Luzzy\Tool\Gradle'
    .\gradlew.bat :app:assembleDebug --no-daemon

## 构建规则

默认只构建 debug：

    ./gradlew :app:assembleDebug --no-daemon

产物：

    app/build/outputs/apk/debug/app-debug.apk

- 每次生成新的 debug APK 后，将副本同步到桌面：C:\Users\Administrator\Desktop\LuzzyRP-2.0.6.2-debug.apk（文件名随 versionName 更新）。
- 除非用户明确要求，不要执行 assembleRelease。
- release 构建需要根目录 keystore.properties，并且需要用户提供签名信息；没有 keystore 时 release signingConfig 不创建，debug 仍可正常构建。
- 构建产物、.gradle、local.properties、keystore.properties 都不要提交。

## 开发约定

- app/src/main/assets/rphub 视为上游原始树；仅 vendor/ 下保留 marked、DOMPurify、Sortable 三个本地运行时库，避免设备无法访问 jsDelivr。升级上游时整棵目录替换为官方 release 的最新版本，再补回 vendor/ 与 assets/fonts/，不要恢复已删除的 assets/ext、旧 LuzzyBridge 或旧工具脚本。AnysearchBridge 是唯一允许保留的原生 JS 桥。
- 升级 upstream 后同步修改 app/build.gradle.kts 的 versionCode/versionName、WebViewSetup.kt 的 UA 后缀，并重新构建 debug。
- built-in-content.js 中的 <nsfw_rules> 规则段不可修改。
- AssetExtractor 只解压 assets/rphub；新增资源根目录时必须同步修改 AssetExtractor.kt 和 app/build.gradle.kts 的签名计算。
- WebView 远程调试只在 debug 构建开启。
- 没有测试源码；当前以 assembleDebug 成功作为最低验证门槛。不要声称运行了不存在的测试。

## 验证清单

1. 构建成功且退出码为 0。
2. 检查 APK 元数据：包名 com.luzzymeow.luzzyrp.debug，versionName 2.0.6.2-debug，compileSdk 37，minSdk 26，targetSdk 37。
3. 如有设备：adb install -r app/build/outputs/apk/debug/app-debug.apk，启动后确认 WebView 能加载首页、返回键和文件选择可用。
4. 检查 app/src/main/assets/rphub 的 26 个上游文件与上游 release 逐文件一致（vendor/ 下 3 个本地运行时库除外，升级场景）。
