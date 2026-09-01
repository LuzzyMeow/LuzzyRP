import java.util.Properties

// :app —— LuzzyRP WebView 壳工程（v1.0.0 重建）
//
// [HARD-REQ-8] 发布流程：稳定版 versionCode 递增，release 构建走 luzzy 签名 + ABI 拆分。
// AGP 9 内置 Kotlin 支持（无需 org.jetbrains.kotlin.android 插件，见 AGP 9 迁移说明）。
// 签名配置：从根目录 keystore.properties 读取（该文件不入库，见 .gitignore）。
plugins {
    alias(libs.plugins.android.application)
}

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
        versionCode = 1
        versionName = "1.0.0"

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
