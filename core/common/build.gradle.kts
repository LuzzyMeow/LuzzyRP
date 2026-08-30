// :core:common —— 通用工具层（纯 Kotlin JVM，无 Android 依赖）
// 内容：OkHttp Call 桥接、JSON 单例（KV 稳定序列化）、PNG tEXt chunk 读写
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // 项目规范：JVM 17 字节码 target，jvmToolchain(21) 本机折中
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
    }
}

java {
    // 与 Kotlin jvmTarget=17 对齐，保证 Java/Kotlin 编译目标一致（JVM 17 字节码规范）
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    api(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
