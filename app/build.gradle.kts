import java.util.Properties

// :app —— LuzzyRP WebView 壳工程（v1.0.0 重建）
//
// [HARD-REQ-8] 发布流程：稳定版 versionCode 递增，release 构建走 luzzy 签名 + ABI 拆分。
// AGP 9 内置 Kotlin 支持（无需 org.jetbrains.kotlin.android 插件，见 AGP 9 迁移说明）。
// 签名配置：从根目录 keystore.properties 读取（该文件不入库，见 .gitignore）。
plugins {
    alias(libs.plugins.android.application)
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
        versionCode = 9
        versionName = "1.2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // abiSplits：按 ABI 拆分 APK，控制分发体积（沿用旧工程配置）
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

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

    // 测试
    testImplementation(libs.junit)
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
