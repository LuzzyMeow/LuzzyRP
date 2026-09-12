import java.util.Properties

// :app —— LuzzyRP WebView 壳工程（v1.0.0 重建）
//
// [HARD-REQ-8] 发布流程：稳定版 versionCode 递增，release 构建走 luzzy 签名 + 单包产出。
// 2026-09-08（v1.4.0 用户指示）：**单 APK 发布**——本应用为纯 WebView 壳，无 native 库，
// ABI 拆分产出的 arm64-v8a / x86_64 / universal 三个包字节完全相同（历史各版实证），
// 拆分为零收益；release 只产出一个 APK（app-release.apk），GitHub Release 只附这一个。
// AGP 9 内置 Kotlin 支持（无需 org.jetbrains.kotlin.android 插件，见 AGP 9 迁移说明）。
// 签名配置：从根目录 keystore.properties 读取（该文件不入库，见 .gitignore）。
//
// [v1.5.0 移除] 原「助手」原生 Agent 的构建接入（Compose 编译器插件 / KSP-Room /
// kotlinx-serialization 插件 + Compose BOM / Room / DataStore / OkHttp / serialization
// 依赖 + ksp schema 导出 + Compose mapping 生产者版本对齐）已按用户指示于 2026-09-11
// 彻底移除；本工程回到「最小依赖 WebView 壳」。
//
// [v3.0 P1 恢复（2026-09-12）] Compose 座自 git 历史 52aab12c 回收（版本组合已验证）：
// kotlin.plugin.compose + Compose BOM（ui/foundation/material3/activity-compose/
// lifecycle-runtime-compose）+ buildFeatures.compose + composeMappingProducerClasspath
// 钉版本补丁。仅服务新的 ui/ 包（v3.0 Compose 界面）；KSP/Room/DataStore 不引（P4 按需）。
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // [v3.0 P4-B spike] 存储选型试验：Room 需要注解处理器
    alias(libs.plugins.ksp)
}

// [LuzzyRP v1.2.3] 资产签名自动解压（根治「改 assets 忘 bump EXTRACT_VERSION」）：
// 构建时对 rphub/ext 资产树计算（文件数+总大小+最新 mtime）签名并注入
// BuildConfig.ASSET_SIGNATURE；AssetExtractor 启动时与设备侧标记比对，
// 资产有任何变更即自动重新解压——无需任何手动版本操作。
fun assetSignature(): String {
    var count = 0L
    var total = 0L
    var latest = 0L
    listOf(file("src/main/assets/rphub"), file("src/main/assets/ext")).forEach { root ->
        root.walkTopDown().filter { it.isFile }.forEach { f ->
            count++
            total += f.length()
            latest = maxOf(latest, f.lastModified())
        }
    }
    return "n$count-s$total-m$latest"
}
val assetSignature = assetSignature()

val keystoreProps: Properties? = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { stream -> load(stream) } }
}

android {
    namespace = "com.luzzymeow.luzzyrp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.luzzymeow.luzzyrp"
        minSdk = 26
        targetSdk = 37

        // 资产签名（见 assetSignature）：资产变更即触发设备侧重新解压
        buildConfigField("String", "ASSET_SIGNATURE", """"$assetSignature"""")
        versionCode = 13
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // APK 拆分：**关闭**（2026-09-08 v1.4.0 用户指示「以后 release 只给一个 APK 包」）。
    // 纯 WebView 壳无 native 库，abi 拆分三包字节相同（v1.2.2~v1.4.0 release 资产实证），
    // 拆分零收益；关闭后 release 产出单个 app-release.apk，Release 只附该包。
    // 如需重新启用：恢复下方 splits.abi 块即可（历史配置见 git 历史 v1.4.0 之前）。
    // splits {
    //     abi {
    //         isEnable = true
    //         reset()
    //         include("arm64-v8a", "x86_64")
    //         isUniversalApk = true
    //     }
    // }

    // 签名：keystore.properties 存在时创建 luzzy 签名；否则 release 回退 debug 签名保证可编译
    signingConfigs {
        if (keystoreProps != null) {
            create("luzzy") {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (keystoreProps != null) "luzzy" else "debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        // [v3.0 P1] Compose 界面层（ui/ 包）
        compose = true
    }
}

// [v3.0 P1，自 52aab12c 回收] AGP 9.2.1 内置 Kotlin 2.2.10，而项目 Kotlin/Compose 插件为
// 2.4.0：composeMappingProducerClasspath 任务会去解析
// org.jetbrains.kotlin:compose-group-mapping:2.2.10（可能无此版本可用）→ 钉回项目 Kotlin 版本。
configurations.configureEach {
    if (name.contains("composeMappingProducerClasspath")) {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin" && requested.name == "compose-group-mapping") {
                useVersion(libs.versions.kotlin.get())
                because("对齐项目 Kotlin 版本，避免解析不到 AGP 内置 Kotlin 对应版本")
            }
        }
    }
}

// AGP 9 内置 Kotlin：编译选项在顶层 kotlin 块配置（kotlinOptions 已被替代）
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // AndroidX 基础（保持最小依赖）
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // [v2.0.0] 原生聊天传输层（v2.0 薄切：HTTP + SSE + 协议线格式 + 流式装配下沉到 Kotlin）
    // 仅两个运行时依赖；未使用 @Serializable，故不需要 kotlinx-serialization 编译器插件。
    implementation(libs.kotlinx.serialization.json)
    // [v3.0 P4-B spike] 存储选型试验（去留见 docs/DESIGN-migration.md §8）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.okhttp)

    // [v3.0 P1] Compose 座（BOM 统一版本；组合自 52aab12c 先例回收）
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.haze)
    implementation(libs.haze.materials)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // [v3.0 P2] GFM Markdown 解析器（JetBrains，**Apache-2.0**，与 AGPL-3.0 自有代码兼容）。
    // 只引解析器：AST → Compose 的渲染层自写（不引第三方 Compose 渲染库，避免再叠一层版本耦合）。
    implementation(libs.jetbrains.markdown)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // 测试
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    // [v3.0 Stage0] 仪器化 UI 测试（只跑模拟器；真机仍只装 release，不装测试件）
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    // 空的测试宿主 Activity（createComposeRule 用），debug 专用、不进 release
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// [LuzzyRP v1.2.3] 应用内 CHANGELOG 自动同步（硬性规定 5 辅助机制）：
// 每次构建前由 tools/gen-changelog.mjs 从仓库根 CHANGELOG.md 重新生成
// src/main/assets/ext/luzzy-changelog.js（关于页更新日志数据源），
// 杜绝「忘记重新生成」导致应用内日志过期；同步状态另受
// tools/verify-markers.ps1 的 R3-changelog-sync 门禁拦截（双保险）。
// node 不可用时降级为告警并保留现有数据文件（不阻塞构建）。
val genChangelog = tasks.register("genChangelog") {
    val changelogSrc = rootProject.file("CHANGELOG.md")
    val genScript = rootProject.file("tools/gen-changelog.mjs")
    val projectDir = rootProject.projectDir
    val outFile = layout.projectDirectory.file("src/main/assets/ext/luzzy-changelog.js")
    inputs.file(changelogSrc)
    inputs.file(genScript)
    outputs.file(outFile)
    doLast {
        try {
            val process = ProcessBuilder("node", genScript.absolutePath)
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            System.out.println(output.trim())
            if (code != 0) throw GradleException("genChangelog 失败（exit $code）")
        } catch (e: java.io.IOException) {
            System.err.println("[genChangelog] node 不可用，跳过自动同步（保留现有 luzzy-changelog.js）: " + e.message)
        }
    }
}

tasks.named("preBuild") { dependsOn(genChangelog) }

// [v3.0 Stage0] 聊天链路一键回归入口。
//
// **真机防护（血的教训）**：`connectedDebugAndroidTest` 默认会在**所有已连接设备**上安装并执行
// 测试件 —— 首次跑就把测试件指向了真机（AGP 报告里的设备名 A9210）。AGENTS.md 明令「测试一律
// 模拟器、严禁真机 A9210」，故本任务加硬门：ANDROID_SERIAL 必须是 emulator-*，否则直接失败。
// 确需多设备（不含真机）时用 -PallowAllDevices=true 显式放行。
// 配置缓存要求：属性在配置期取成 Provider，执行期只读它（不要在执行期访问 project）
val allowAllDevicesProvider = providers.gradleProperty("allowAllDevices")
val verifyEmulatorDevice = tasks.register("verifyEmulatorDevice") {
    group = "verification"
    description = "门禁：仪器化测试只允许跑模拟器（ANDROID_SERIAL=emulator-*）"
    val allowAllProvider = allowAllDevicesProvider
    doLast {
        val serial = System.getenv("ANDROID_SERIAL")
        if (allowAllProvider.orNull == "true") {
            logger.lifecycle("[verifyEmulatorDevice] 显式放行（-PallowAllDevices=true），会在所有已连接设备上执行")
            return@doLast
        }
        if (serial == null || !serial.startsWith("emulator-")) {
            throw GradleException(
                "仪器化测试只允许跑模拟器：请用 `ANDROID_SERIAL=emulator-5554 ./gradlew checkChat`。" +
                    "（当前 ANDROID_SERIAL=${serial ?: "未设置"}；真机不得安装测试件，见 AGENTS.md §6.1）",
            )
        }
        logger.lifecycle("[verifyEmulatorDevice] OK：目标模拟器 $serial")
    }
}

tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
    dependsOn(verifyEmulatorDevice)
}

val checkChat = tasks.register("checkChat") {
    group = "verification"
    description = "聊天链路回归：纯逻辑单测 + 仪器化 UI 测试（只跑模拟器）"
    dependsOn(verifyEmulatorDevice, "testDebugUnitTest", "connectedDebugAndroidTest")
}
